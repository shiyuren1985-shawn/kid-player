#!/usr/bin/env python3
"""Run regression only on the dedicated AVD, restoring settings in a finally block."""
import argparse, datetime, json, os, pathlib, socket, subprocess, sys, time

ROOT=pathlib.Path(__file__).resolve().parent.parent
parser=argparse.ArgumentParser()
parser.add_argument('--class',dest='classes',default='')
parser.add_argument('--live',action='store_true')
parser.add_argument('--no-fixture',action='store_true')
args=parser.parse_args()
ADB=pathlib.Path(os.environ['ANDROID_HOME'])/'platform-tools/adb'
serial='emulator-5554'
def adb(*parts, **kwargs):
    return subprocess.run([str(ADB),'-s',serial,*parts],check=True,**kwargs)
def text(*parts):
    return adb(*parts,stdout=subprocess.PIPE).stdout.decode().strip()
if text('emu','avd','name').splitlines()[0].strip()!='KidCinema_Tablet_12':
    raise SystemExit('Refusing to test any device other than KidCinema_Tablet_12')
folder=ROOT/'qa'/'kid-player-tests'/('run-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S'))
folder.mkdir(parents=True)
# Preserve the existing delivery install's settings before installing a new test build.
adb('shell','am','force-stop','family.kidcinema',stdout=subprocess.DEVNULL)
backup=adb('exec-out','run-as','family.kidcinema','tar','cf','-','shared_prefs',stdout=subprocess.PIPE).stdout
(folder/'before-prefs.tar').write_bytes(backup);(folder/'before-prefs.tar').chmod(0o600)
settings={k:text('shell','settings','get',group,k) for group,k in [('global','wifi_on'),('global','mobile_data'),('system','font_scale'),('system','user_rotation'),('system','accelerometer_rotation')]}
fixture=None; result=None
try:
    if not args.no_fixture:
        probe=socket.socket();probe.settimeout(.3)
        if probe.connect_ex(('127.0.0.1',1445))==0:raise RuntimeError('Port 1445 is already in use; refusing to replace an existing service')
        probe.close()
        fixture_log=open(folder/'smb-fixture.log','w')
        fixture=subprocess.Popen([str(ROOT/'tools/.venv/bin/python'),str(ROOT/'tools/smb-fixture.py')],stdout=fixture_log,stderr=subprocess.STDOUT,cwd=ROOT)
        for _ in range(30):
            probe=socket.socket();probe.settimeout(.2);ready=probe.connect_ex(('127.0.0.1',1445))==0;probe.close()
            if ready:break
            if fixture.poll() is not None:raise RuntimeError('SMB fixture did not start')
            time.sleep(.2)
        else:raise RuntimeError('SMB fixture was not ready')
    for path in ['app/build/outputs/apk/debug/app-debug.apk','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']:
        adb('install','-r',str(ROOT/path),stdout=subprocess.DEVNULL)
    cmd=[str(ADB),'-s',serial,'shell','am','instrument','-w']
    if args.classes:cmd+=['-e','class',args.classes]
    if args.live:cmd+=['-e','liveBili','true']
    cmd+=['family.kidcinema.test/family.kidcinema.SafeTestRunner']
    result=subprocess.run(cmd,capture_output=True,text=True,timeout=900)
    output=result.stdout+result.stderr;(folder/'instrumentation.txt').write_text(output)
    print(output)
finally:
    adb('shell','am','force-stop','family.kidcinema',stdout=subprocess.DEVNULL)
    adb('shell','am','force-stop','family.kidcinema.test',stdout=subprocess.DEVNULL)
    # Replace only preference files created by this test run, restoring the original archive.
    adb('shell','run-as','family.kidcinema','rm','-rf','shared_prefs',stdout=subprocess.DEVNULL)
    adb('shell','run-as','family.kidcinema','tar','xf','-',input=backup,stdout=subprocess.DEVNULL)
    for k,v in settings.items():
        if k=='wifi_on':adb('shell','svc','wifi','enable' if v=='1' else 'disable',stdout=subprocess.DEVNULL)
        elif k=='mobile_data':adb('shell','svc','data','enable' if v=='1' else 'disable',stdout=subprocess.DEVNULL)
        elif v=='null':adb('shell','settings','delete','system',k,stdout=subprocess.DEVNULL)
        else:adb('shell','settings','put','system',k,v,stdout=subprocess.DEVNULL)
    if fixture is not None:
        fixture.terminate()
        try:fixture.wait(timeout=5)
        except subprocess.TimeoutExpired:fixture.kill();fixture.wait()
    verify=adb('exec-out','run-as','family.kidcinema','tar','cf','-','shared_prefs',stdout=subprocess.PIPE).stdout
    import io,tarfile,hashlib
    def hashes(data):
        with tarfile.open(fileobj=io.BytesIO(data)) as archive:
            return {m.name:hashlib.sha256(archive.extractfile(m).read()).hexdigest() for m in archive.getmembers() if m.isfile()}
    restored=hashes(backup)==hashes(verify)
    (folder/'restore.json').write_text(json.dumps({'preferences_exactly_restored_before_launch':restored,'original_system_settings':settings,'fixture_stopped':fixture is None or fixture.poll() is not None},indent=2)+'\n')
    if not restored:raise RuntimeError('Preferences restore verification failed')
    adb('shell','am','start','-n','family.kidcinema/.MainActivity',stdout=subprocess.DEVNULL)
    print('Evidence:',folder)
if result is None or result.returncode!=0 or 'FAILURES!!!' in result.stdout or 'OK (' not in result.stdout:
    raise SystemExit(1)
