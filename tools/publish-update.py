#!/usr/bin/env python3
"""Validate an APK and atomically publish it, without touching source or signing keys."""
import argparse, datetime, fcntl, hashlib, json, os, pathlib, re, shutil, subprocess, tempfile, urllib.parse, sys

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
        old=None
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
        if old is not None and code==old['versionCode']:
            # A retry must reuse exactly the same release, including its publication time.
            if any(old.get(key)!=value for key,value in document.items() if key!='publishedAt'):
                raise ValueError('Existing release metadata differs; reuse its original notes and origin or increment versionCode')
            return old
        fd,tmp=tempfile.mkstemp(prefix='.manifest-',dir=folder)
        try:
            with os.fdopen(fd,'w') as f:json.dump(document,f,ensure_ascii=False,indent=2);f.write('\n');f.flush();os.fsync(f.fileno())
            os.chmod(tmp,0o644);os.replace(tmp,manifest)
        finally:
            if os.path.exists(tmp):os.unlink(tmp)
    return document

DEFAULT_WEBSITE_SYNC = pathlib.Path(__file__).resolve().parents[3] / 'yuren.shi/scripts/sync-kid-release.py'

def publish_release(apk,root,base,notes,sdk,website_script=DEFAULT_WEBSITE_SYNC):
    """Publish once to the app service and website; failed website delivery is retryable."""
    if website_script is not None:
        website_script=pathlib.Path(website_script).resolve()
        if not website_script.is_file():
            raise FileNotFoundError('Website publisher is missing: '+str(website_script))
    root=pathlib.Path(root).resolve();root.parent.mkdir(parents=True,exist_ok=True)
    # Serialize the entire CLI workflow, not just the local manifest replacement.
    with (root.parent/'release-pipeline.lock').open('a') as lock:
        fcntl.flock(lock,fcntl.LOCK_EX)
        document=publish(pathlib.Path(apk).resolve(),root,base,notes,sdk)
        from update_service_files import SERVICE_HOME,mirror
        if SERVICE_HOME.exists():mirror(root)
        if website_script is not None:
            try:
                subprocess.run([sys.executable,str(website_script),'--source',str(root/'kid-player'),'--deploy'],check=True,stdout=sys.stderr,timeout=600)
            except (OSError,subprocess.SubprocessError) as error:
                raise RuntimeError('App release is saved locally, but website synchronization was not confirmed. Retry the same command with the original APK, notes and origin; do not increment versionCode for this retry.') from error
        else:
            print('Website synchronization explicitly skipped; this is not a complete public release.',file=sys.stderr)
        return document

if __name__=='__main__':
    p=argparse.ArgumentParser()
    p.add_argument('--apk',required=True,type=pathlib.Path)
    p.add_argument('--root',type=pathlib.Path,default=pathlib.Path(__file__).resolve().parents[2]/'update-server/public')
    p.add_argument('--base-url',default='https://kid-player.shiyu.ren')
    p.add_argument('--notes',required=True)
    p.add_argument('--website-sync-script',type=pathlib.Path,default=DEFAULT_WEBSITE_SYNC)
    p.add_argument('--skip-website',action='store_true',help='Local diagnostics only: explicitly skip website delivery')
    a=p.parse_args()
    try:
        document=publish_release(a.apk,a.root,a.base_url,a.notes,os.environ['ANDROID_HOME'],None if a.skip_website else a.website_sync_script)
    except Exception as error:
        p.exit(1,str(error)+'\n')
    print(json.dumps(document,ensure_ascii=False,indent=2))
