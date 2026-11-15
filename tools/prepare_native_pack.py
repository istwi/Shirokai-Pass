#!/usr/bin/env python3
"""Download pinned official test artifacts with TLS+SHA256; extract unchanged nested dependencies for Loom dev runtime."""
import hashlib
import io
import json
from pathlib import Path
import re
import urllib.request
import zipfile

root = Path(__file__).resolve().parent.parent
mods = root / 'build/integration-mods'
nested = root / 'build/integration-nested-mods'
libraries = root / 'build/integration-libraries'
for directory in (mods, nested, libraries): directory.mkdir(parents=True, exist_ok=True)
manifest = json.loads((root / 'tools/native-pack.json').read_text())
root_ids = set()
def digest(data): return hashlib.sha256(data).hexdigest()
def save_verified(path, data):
    if path.exists():
        if digest(path.read_bytes()) != digest(data): raise RuntimeError(f'Existing artifact differs: {path}; preserve and remove it explicitly before retrying')
    else: path.write_bytes(data)
for entry in manifest:
    target = mods / entry['filename']
    if target.exists(): data = target.read_bytes()
    else:
        request = urllib.request.Request(entry['url'], headers={'Accept': 'application/octet-stream'} if entry['url'].startswith('https://api.github.com/repos/') else {})
        with urllib.request.urlopen(request, timeout=120) as response: data = response.read()
    if digest(data) != entry['sha256']: raise RuntimeError(f"SHA256 mismatch: {entry['filename']}")
    save_verified(target, data)
    with zipfile.ZipFile(io.BytesIO(data)) as archive: root_ids.add(json.loads(archive.read('fabric.mod.json'))['id'])
seen = set()
records = []
def extract(data, parent):
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        metadata = json.loads(archive.read('fabric.mod.json')) if 'fabric.mod.json' in archive.namelist() else {}
        for entry in metadata.get('jars', []):
            name = entry['file']; child = archive.read(name); sha = digest(child)
            if sha in seen: continue
            seen.add(sha)
            with zipfile.ZipFile(io.BytesIO(child)) as child_archive:
                child_metadata = json.loads(child_archive.read('fabric.mod.json')) if 'fabric.mod.json' in child_archive.namelist() else None
            if child_metadata and child_metadata['id'] in root_ids: continue
            filename = Path(name).name
            directory = nested if child_metadata else libraries
            save_verified(directory / filename, child)
            records.append({'file': filename, 'parent': parent, 'sha256': sha, 'mod_id': child_metadata['id'] if child_metadata else None, 'version': child_metadata.get('version') if child_metadata else None, 'directory': directory.name})
            if child_metadata: extract(child, filename)
for entry in manifest: extract((mods / entry['filename']).read_bytes(), entry['filename'])
# Select the newest duplicate ID within this pinned, tested set; Fabric still validates version constraints at launch.
# Only the pinned artifacts in this manifest are considered; keep all originals unchanged on disk.
by_id = {}
for record in records:
    record['selected'] = True
    if record['mod_id'] is None: continue
    previous = by_id.get(record['mod_id'])
    if previous:
        def version(item): return tuple(int(n) for n in re.findall(r'\d+', item['version'] or ''))
        older, newer = (previous, record) if version(previous) <= version(record) else (record, previous)
        older['selected'] = False
        by_id[record['mod_id']] = newer
    else: by_id[record['mod_id']] = record
(root / 'build/integration-dependencies.json').write_text(json.dumps(records, indent=2) + '\n')
print(f'Verified {len(manifest)} official mods and {len(records)} unchanged nested dependencies')
