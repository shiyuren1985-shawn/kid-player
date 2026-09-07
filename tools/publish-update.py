#!/usr/bin/env python3
"""Validate an APK and atomically publish it, without touching source or signing keys."""
import argparse, datetime, fcntl, hashlib, json, os, pathlib, re, shutil, subprocess, tempfile, urllib.parse

def publish(apk, root, base, notes, sdk):
    parsed=urllib.parse.urlsplit(base)
    if parsed.scheme!='https' or not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment or parsed.path not in ('','/') or parsed.port not in (None,443):raise ValueError('base-url must be an HTTPS origin')
    if not notes.strip() or len(notes)>4000:raise ValueError('Provide concise release notes')
    tools=pathlib.Path(sdk)/'build-tools/35.0.0'
    info=subprocess.check_output([str(tools/'aapt'),'dump','badging',str(apk)],text=True)
    package=re.search(r"package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'",info)
    if not package or package[1]!='family.kidcinema':raise ValueError('Unexpected package identity')
    code=int(package[2]);name=package[3]
    if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]{0,79}',name):raise ValueError('Version name must be filename safe')
    min_sdk=int(re.search(r"sdkVersion:'(\d+)'",info)[1])
    cert=subprocess.check_output([str(tools/'apksigner'),'verify','--print-certs',str(apk)],text=True)
    signers=sorted(re.findall(r'Signer #\d+ certificate SHA-256 digest: ([a-f0-9]{64})',cert))
    if not signers:raise ValueError('No verified signer')
    size=apk.stat().st_size
    if not 0<size<=256*1024*1024:raise ValueError('Invalid APK size')
    digest=hashlib.sha256(apk.read_bytes()).hexdigest()
    folder=root/'kid-player';releases=folder/'releases';releases.mkdir(parents=True,exist_ok=True)
    with (root.parent/'publish.lock').open('a') as lock:
        fcntl.flock(lock,fcntl.LOCK_EX)
        manifest=folder/'update.json'
        if manifest.exists():
            old=json.loads(manifest.read_text())
            if old.get('signerSha256')!=signers:raise ValueError('Signing identity changed; refusing incompatible update')
            if code<old['versionCode'] or (code==old['versionCode'] and old['sha256']!=digest):raise ValueError('Increment versionCode; an existing version cannot change')
        dest=releases/f'kid-player-{name}-{code}.apk'
        if dest.exists() and hashlib.sha256(dest.read_bytes()).hexdigest()!=digest:raise ValueError('Immutable APK already exists with different bytes')
        if not dest.exists():
            fd,tmp=tempfile.mkstemp(prefix='.apk-',dir=releases)
            try:
                with os.fdopen(fd,'wb') as out,apk.open('rb') as src:shutil.copyfileobj(src,out);out.flush();os.fsync(out.fileno())
                if hashlib.sha256(pathlib.Path(tmp).read_bytes()).hexdigest()!=digest:raise ValueError('Copy hash mismatch')
                os.chmod(tmp,0o644);os.replace(tmp,dest)
            finally:
                if os.path.exists(tmp):os.unlink(tmp)
        document={'schema':1,'packageName':'family.kidcinema','versionCode':code,'versionName':name,'minSdk':min_sdk,'apkUrl':base.rstrip('/')+'/kid-player/releases/'+dest.name,'size':size,'sha256':digest,'signerSha256':signers,'notes':notes,'publishedAt':datetime.datetime.now(datetime.timezone.utc).isoformat()}
        fd,tmp=tempfile.mkstemp(prefix='.manifest-',dir=folder)
        try:
            with os.fdopen(fd,'w') as f:json.dump(document,f,ensure_ascii=False,indent=2);f.write('\n');f.flush();os.fsync(f.fileno())
            os.chmod(tmp,0o644);os.replace(tmp,manifest)
        finally:
            if os.path.exists(tmp):os.unlink(tmp)
    return document

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--apk',required=True,type=pathlib.Path);p.add_argument('--root',type=pathlib.Path,default=pathlib.Path(__file__).resolve().parents[2]/'update-server/public');p.add_argument('--base-url',default='https://kid-player.shiyu.ren');p.add_argument('--notes',required=True);a=p.parse_args()
    print(json.dumps(publish(a.apk.resolve(),a.root.resolve(),a.base_url,a.notes,os.environ['ANDROID_HOME']),ensure_ascii=False,indent=2))
