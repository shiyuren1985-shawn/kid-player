"""Isolated development fixture. Bind only to loopback, never share user folders."""
from pathlib import Path
import logging
import shutil
from impacket import smbserver
from impacket.ntlm import compute_lmhash, compute_nthash

project = Path(__file__).resolve().parent.parent
fixture = project / "output" / "smb-fixture"
children = fixture / "children"
children.mkdir(parents=True, exist_ok=True)
shutil.copyfile(project / "app/src/main/res/raw/demo_space.mp4", children / "space.mp4")
logging.basicConfig(level=logging.ERROR)
server = smbserver.SimpleSMBServer(listenAddress="127.0.0.1", listenPort=1445)
server.addShare("KIDS", str(fixture), "Disposable prototype test media", readOnly="yes")
server.setSMB2Support(True)
server.addCredential("kidtest", 0, compute_lmhash("fixture-only-123"), compute_nthash("fixture-only-123"))
print("Read-only test SMB listening on loopback port 1445", flush=True)
server.start()
