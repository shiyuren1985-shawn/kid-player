"""Mirror only the verified current public release for the local launchd service."""
import fcntl
import hashlib
import json
import os
import pathlib
import re
import shutil
import tempfile

SERVICE_HOME = pathlib.Path.home() / 'Library/Application Support/KidPlayer/UpdateService'

def atomic_copy(source, destination):
    destination.parent.mkdir(parents=True, exist_ok=True)
    fd, name = tempfile.mkstemp(prefix='.publish-', dir=destination.parent)
    try:
        with os.fdopen(fd, 'wb') as out, source.open('rb') as src:
            shutil.copyfileobj(src, out)
            out.flush()
            os.fsync(out.fileno())
        os.replace(name, destination)
    finally:
        if os.path.exists(name): os.unlink(name)

def mirror(public):
    # Share the publisher lock so a concurrent release cannot mix APK and manifest.
    with (public.parent / 'publish.lock').open('a') as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        _mirror_locked(public)

def _mirror_locked(public):
    manifest = public / 'kid-player/update.json'
    data = json.loads(manifest.read_text())
    name = data['apkUrl'].rsplit('/', 1)[-1]
    if not re.fullmatch(r'kid-player-[A-Za-z0-9._-]+\.apk', name):
        raise ValueError('Invalid public release filename')
    source = public / 'kid-player/releases' / name
    if source.stat().st_size != data['size'] or hashlib.sha256(source.read_bytes()).hexdigest() != data['sha256']:
        raise ValueError('Public APK verification failed')
    target = SERVICE_HOME / 'public/kid-player/releases' / name
    if target.exists():
        if hashlib.sha256(target.read_bytes()).hexdigest() != data['sha256']:
            raise ValueError('Immutable service APK mismatch')
    else:
        atomic_copy(source, target)
    atomic_copy(manifest, SERVICE_HOME / 'public/kid-player/update.json')
