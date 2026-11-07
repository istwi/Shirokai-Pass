"""Start disposable Fabric servers; run hostile-click/offline/restart/SIGKILL regressions.
Requires JDK21, Node, npm ci in tools/smoke, and ./gradlew integrationModJar.
"""
import json
import os
from pathlib import Path
import secrets
import shutil
import signal
import socket
import subprocess
import time
import sys
import rcon as wire
import hashlib

ROOT = Path(__file__).resolve().parents[2]
RUN = ROOT / 'run-audit'
RUN.mkdir(exist_ok=True)
WORLD = RUN / ('world-' + time.strftime('%Y%m%d-%H%M%S'))
RESULTS = RUN / ('results-' + WORLD.name)
RESULTS.mkdir()
# Operator lists belong to the server directory, not the selected world. Preserve
# the prior fixture and reset it so a previous admin test cannot grant test users OP.
operators=RUN/'ops.json'
if operators.exists():
    shutil.copy2(operators,RESULTS/'ops-before.json')
operators.write_text('[]\n')
PASSWORD = secrets.token_urlsafe(24)
ENV = dict(os.environ, MC_PORT='25585', MC_RCON_PORT='25595', MC_RCON_PASSWORD=PASSWORD, MC_WORLD=str(WORLD), MC_RESULTS=str(RESULTS))
(RUN / 'mods').mkdir(exist_ok=True)
shutil.copy2(ROOT / 'build/integration/shirokai-pass-test-harness.jar', RUN / 'mods/shirokai-pass-test-harness.jar')
# Only the known test provider is managed here; production mods are never touched.
mode=sys.argv[1] if len(sys.argv)>1 else 'dupe'
provider=RUN/'mods/LuckPerms-Fabric-5.4.140.jar'
if mode in ('permissions','permissions_restart'):
    source=Path(os.environ['MC_LUCKPERMS_JAR'])
    if hashlib.sha512(source.read_bytes()).hexdigest()!='386ff4d18981c830d21ddaa599d70c1c5430c1475e2c5960275bad535cbd572c77e38b3841f0a71e3ea4a795c56d45ff8c4a8dba3799dae26bff5a382c6e0a64':raise ValueError('Unexpected LuckPerms artifact')
    shutil.copy2(source,provider)
    database=RUN/'config/luckperms'
    if database.exists():shutil.move(str(database),str(RESULTS/'luckperms-before'))
elif provider.exists():
    shutil.move(str(provider),str(RESULTS/'luckperms-previous-test.jar'))
(RUN / 'eula.txt').write_text('eula=true\n')
(RUN / 'server.properties').write_text('online-mode=false\nserver-ip=127.0.0.1\nserver-port=25585\nenable-rcon=true\nrcon.port=25595\nrcon.password=' + PASSWORD + '\nlevel-name=' + WORLD.name + '\nview-distance=2\nsimulation-distance=2\ndifficulty=peaceful\nspawn-protection=0\nlevel-type=minecraft:flat\ngenerate-structures=false\nmax-tick-time=120000\n')
os.chmod(RUN / 'server.properties', 0o600)
server = None
log = None

def command(text):
    with socket.create_connection(('127.0.0.1',25595),timeout=10) as conn:
        wire.send(conn,1,3,PASSWORD)
        while True:
            request, kind, _ = wire.receive(conn)
            if request == -1: raise PermissionError('Local test RCON rejected')
            if kind == 2: break
        wire.send(conn,2,2,text)
        return wire.receive(conn)[2]

def start(number):
    global server,log
    filename=RUN / ('audit-session-'+str(number)+'.log')
    log=filename.open('w')
    server=subprocess.Popen([str(ROOT/'gradlew'),'runAuditServer','--console=plain','--no-daemon'],cwd=ROOT,stdin=subprocess.PIPE,stdout=log,stderr=subprocess.STDOUT,env=ENV)
    deadline=time.monotonic()+180
    while time.monotonic()<deadline:
        if server.poll() is not None: raise RuntimeError('Server exited; see '+str(filename))
        if 'Shirokai Pass iniciado' in filename.read_text(errors='replace'):
            try: command('list');print('SERVER READY',number,flush=True);return
            except (OSError,ConnectionError): pass
        time.sleep(0.2)
    raise TimeoutError('Server readiness: '+str(filename))

def stop():
    global server
    command('stop')
    server.wait(timeout=45)
    log.close()
    server=None

def phase(name):
    print('PHASE',name,flush=True)
    with (RESULTS/(name+'.log')).open('w') as output:
        completed=subprocess.run(['node',str(ROOT/'tools/smoke/audit.cjs'),name],cwd=ROOT,env=ENV,stdout=output,stderr=subprocess.STDOUT,timeout=100)
    if completed.returncode: raise RuntimeError('Failed phase '+name+'; see '+str(RESULTS/(name+'.log')))
    print((RESULTS/(name+'.json')).read_text(),flush=True)

try:
    mode=sys.argv[1] if len(sys.argv)>1 else 'dupe'
    names = ('permissions','permissions_restart') if mode=='permissions' else ('fallback',) if mode=='fallback' else ('storage','storage_corrupt') if mode=='storage' else ('admin',) if mode=='admin' else ('premium_setup','premium_expire') if mode=='premium' else ('rapid','restart','crash','recovery')
    if mode=='permissions':
        start(1);phase('permissions');stop()
        start(2);phase('permissions_restart');stop()
    elif mode=='fallback':
        start(1);phase('fallback');stop()
    elif mode=='storage':
        start(1);phase('storage');stop()
        data=WORLD/'shirokaipass/state.json'
        shutil.copy2(data,data.with_name('state.json.startup-saved'))
        data.write_text('broken-storage-fixture')
        start(2);phase('storage_corrupt');stop()
    elif mode=='admin':
        start(1);phase('admin');stop()
    elif names[0]=='premium_setup':
        start(1);phase('premium_setup');stop()
        start(2);phase('premium_expire');stop()
    else:
        start(1);phase('rapid');stop()
        start(2);phase('restart');stop()
        start(3);phase('crash')
        checkpoint=json.loads((WORLD/'audit-checkpoint.json').read_text())
        if checkpoint['pid'] <= 1: raise ValueError('Unsafe test PID')
        os.kill(checkpoint['pid'],signal.SIGKILL)
        server.wait(timeout=35);log.close();server=None
        start(4);phase('recovery');stop()
    if mode=='storage':
        for filename in RUN.glob('audit-session-*.log'):
            text=filename.read_text(errors='replace')
            if "Couldn't place player in world" in text or "Invalid player data" in text: raise AssertionError('Storage fault escaped into player placement: '+str(filename))
    if mode in ('permissions','fallback'):
        for filename in [RUN/'audit-session-1.log'] + ([RUN/'audit-session-2.log'] if mode=='permissions' else []):
            logtext=filename.read_text(errors='replace')
            if 'NoSuchMethodError' in logtext or 'Failed to handle packet' in logtext or "Couldn't place player in world" in logtext:raise AssertionError('Permission linkage/login error: '+str(filename))
    reports=[json.loads((RESULTS/(name+'.json')).read_text()) for name in names]
    summary={'passed':sum(x['passed'] for x in reports),'phases':reports,'world':WORLD.name}
    (RESULTS/'summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2)+'\n')
    print('AUDIT SUCCESS:',summary['passed'],'checks;',RESULTS,flush=True)
finally:
    if server and server.poll() is None:
        try: command('stop');server.wait(timeout=40)
        except Exception:
            server.terminate()
