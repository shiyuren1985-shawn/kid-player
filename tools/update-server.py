#!/usr/bin/env python3
"""Serve only the public update manifest and immutable APKs on loopback."""
import argparse, http.server, pathlib, re, urllib.parse

class Handler(http.server.BaseHTTPRequestHandler):
    def do_HEAD(self): self.serve(False)
    def do_GET(self): self.serve(True)
    def serve(self, body):
        path = urllib.parse.urlsplit(self.path).path
        if path != '/kid-player/update.json' and not re.fullmatch(r'/kid-player/releases/kid-player-[A-Za-z0-9._-]+\.apk', path):
            self.send_error(404); return
        file = (self.server.root / path.lstrip('/')).resolve()
        if not file.is_relative_to(self.server.root) or not file.is_file(): self.send_error(404); return
        try: stream = file.open('rb')
        except OSError: self.send_error(404); return
        with stream:
            size = file.stat().st_size
            self.send_response(200)
            self.send_header('Content-Type', 'application/json; charset=utf-8' if path.endswith('.json') else 'application/vnd.android.package-archive')
            self.send_header('Content-Length', str(size))
            self.send_header('Cache-Control', 'no-store' if path.endswith('.json') else 'public, max-age=31536000, immutable')
            self.send_header('X-Content-Type-Options', 'nosniff'); self.end_headers()
            if body:
                try:
                    while True:
                        chunk=stream.read(65536)
                        if not chunk:break
                        self.wfile.write(chunk)
                except (BrokenPipeError, ConnectionResetError):pass
    def log_message(self, fmt, *args): pass  # Avoid logging query strings or client details.

if __name__ == '__main__':
    p=argparse.ArgumentParser();p.add_argument('--root',required=True);p.add_argument('--port',type=int,default=18897);a=p.parse_args()
    root=pathlib.Path(a.root).resolve();root.mkdir(parents=True,exist_ok=True)
    server=http.server.ThreadingHTTPServer(('127.0.0.1',a.port),Handler);server.root=root
    print('kid player read-only update server on 127.0.0.1:'+str(a.port),flush=True)
    server.serve_forever()
