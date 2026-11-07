"""Download the unmodified LuckPerms Fabric 5.4.140 release from its Modrinth project CDN."""
from pathlib import Path
import hashlib
import subprocess
import sys

URL='https://cdn.modrinth.com/data/Vebnzrzj/versions/l47d4ZWk/LuckPerms-Fabric-5.4.140.jar'
SHA512='386ff4d18981c830d21ddaa599d70c1c5430c1475e2c5960275bad535cbd572c77e38b3841f0a71e3ea4a795c56d45ff8c4a8dba3799dae26bff5a382c6e0a64'
target=Path(sys.argv[1]) if len(sys.argv)>1 else Path(__file__).resolve().parents[2]/'run-audit/fixture-libs/LuckPerms-Fabric-5.4.140.jar'
target.parent.mkdir(parents=True,exist_ok=True)
if not target.exists():
    subprocess.run(['curl','--fail','--location','--silent','--show-error',URL,'--output',str(target)],check=True)
if hashlib.sha512(target.read_bytes()).hexdigest()!=SHA512:
    raise SystemExit('Unexpected LuckPerms artifact checksum; refusing to execute it')
print(target)
