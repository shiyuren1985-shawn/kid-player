#!/usr/bin/env python3
"""Install/update the single user launchd job for the loopback update origin."""
import os
import pathlib
import plistlib
import subprocess
from update_service_files import SERVICE_HOME, atomic_copy, mirror

project = pathlib.Path(__file__).resolve().parents[1]
public = project.parent / 'update-server/public'
if not (public / 'kid-player/update.json').is_file():
    raise SystemExit('Publish a valid update before installing the service')
# launchd must open its working directory and logs on the system volume.
mirror(public)
atomic_copy(project / 'tools/update-server.py', SERVICE_HOME / 'update-server.py')
logs = pathlib.Path.home() / 'Library/Logs/KidPlayer'
logs.mkdir(parents=True, exist_ok=True)
label = 'ren.shiyu.kid-player.update-server'
agent = pathlib.Path.home() / 'Library/LaunchAgents' / (label + '.plist')
agent.parent.mkdir(parents=True, exist_ok=True)
job = {
    'Label': label,
    'ProgramArguments': ['/usr/bin/python3', str(SERVICE_HOME / 'update-server.py'), '--root', str(SERVICE_HOME / 'public')],
    'WorkingDirectory': str(pathlib.Path.home()),
    'RunAtLoad': True,
    'KeepAlive': True,
    'ThrottleInterval': 10,
    'StandardOutPath': str(logs / 'stdout.log'),
    'StandardErrorPath': str(logs / 'stderr.log'),
}
staged = agent.with_suffix('.plist.tmp')
staged.write_bytes(plistlib.dumps(job))
staged.chmod(0o644)
subprocess.run(['plutil', '-lint', str(staged)], check=True)
os.replace(staged, agent)
domain = 'gui/' + str(os.getuid())
service = domain + '/' + label
loaded = subprocess.run(['launchctl', 'print', service], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
if loaded.returncode == 0:
    subprocess.run(['launchctl', 'bootout', service], check=True)
subprocess.run(['launchctl', 'bootstrap', domain, str(agent)], check=True)
print('Installed ' + service)
print('LaunchAgent: ' + str(agent))
print('Logs: ' + str(logs))
