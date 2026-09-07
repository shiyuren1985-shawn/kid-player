#!/usr/bin/env python3
"""Local regression: path isolation, HTTP headers, atomic immutable publishing."""
import importlib.util, pathlib, tempfile, unittest, http.server, threading, urllib.request, urllib.error, os, json

ROOT=pathlib.Path(__file__).resolve().parent.parent
(ROOT/'qa').mkdir(exist_ok=True)

def load(name,file):
    spec=importlib.util.spec_from_file_location(name,ROOT/'tools'/file);m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m);return m
server=load('update_server','update-server.py');publisher=load('publish_update','publish-update.py')

class ServerTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(dir=ROOT/'qa');self.root=pathlib.Path(self.temp.name)
        folder=self.root/'kid-player/releases';folder.mkdir(parents=True)
        (folder.parent/'update.json').write_text('{"schema":1}');(folder/'kid-player-test-1.apk').write_bytes(b'apk')
        (self.root/'secret.txt').write_text('not public')
        self.http=http.server.ThreadingHTTPServer(('127.0.0.1',0),server.Handler);self.http.root=self.root.resolve()
        self.thread=threading.Thread(target=self.http.serve_forever,daemon=True);self.thread.start();self.base='http://127.0.0.1:'+str(self.http.server_port)
    def tearDown(self):self.http.shutdown();self.http.server_close();self.thread.join();self.temp.cleanup()
    def test_only_allowed_paths(self):
        for path in ['/','/kid-player/','/secret.txt','/kid-player/../secret.txt','/kid-player/releases/../../secret.txt','/kid-player/releases/%2e%2e/secret.txt','/kid-player/creators.json']:
            with self.assertRaises(urllib.error.HTTPError) as e:urllib.request.urlopen(self.base+path)
            self.assertEqual(e.exception.code,404)
    def test_cache_and_content_headers(self):
        for path,cache in [('/kid-player/update.json','no-store'),('/kid-player/releases/kid-player-test-1.apk','public, max-age=31536000, immutable')]:
            with urllib.request.urlopen(self.base+path) as response:self.assertEqual(response.headers['Cache-Control'],cache);self.assertEqual(response.headers['X-Content-Type-Options'],'nosniff');self.assertEqual(len(response.read()),int(response.headers['Content-Length']))
    def test_head_has_no_body(self):
        with urllib.request.urlopen(urllib.request.Request(self.base+'/kid-player/update.json',method='HEAD')) as response:self.assertEqual(response.read(),b'');self.assertGreater(int(response.headers['Content-Length']),0)
    def test_symlink_cannot_escape(self):
        outside=self.root.parent/'outside-private-update-test.txt';outside.write_text('not public')
        try:
            (self.root/'kid-player/releases/kid-player-escape.apk').symlink_to(outside)
            with self.assertRaises(urllib.error.HTTPError) as e:urllib.request.urlopen(self.base+'/kid-player/releases/kid-player-escape.apk')
            self.assertEqual(e.exception.code,404)
        finally:outside.unlink()

class PublishTest(unittest.TestCase):
    def test_atomic_idempotent_and_rejects_downgrade(self):
        apk=ROOT/'app/build/outputs/apk/debug/app-debug.apk'
        with tempfile.TemporaryDirectory(dir=ROOT/'qa') as directory:
            root=pathlib.Path(directory)/'public';args=(apk,root,'https://example.com','test',os.environ['ANDROID_HOME'])
            first=publisher.publish(*args);second=publisher.publish(*args)
            self.assertEqual(first['sha256'],second['sha256'])
            newer=dict(second);newer['versionCode']+=1
            (root/'kid-player/update.json').write_text(json.dumps(newer))
            before=(root/'kid-player/update.json').read_bytes()
            with self.assertRaises(ValueError):publisher.publish(apk,root,'https://example.com','older',os.environ['ANDROID_HOME'])
            self.assertEqual(before,(root/'kid-player/update.json').read_bytes())
            current=json.loads(before);self.assertTrue((root/'kid-player/releases'/current['apkUrl'].rsplit('/',1)[-1]).is_file())
    def test_rejects_non_https_origin(self):
        with self.assertRaises(ValueError):publisher.publish(pathlib.Path('absent'),pathlib.Path('absent'),'http://example.com','notes',os.environ['ANDROID_HOME'])

class ServiceMirrorTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(dir=ROOT/'qa')
        self.base=pathlib.Path(self.temp.name)
        self.module=load('service_files','update_service_files.py')
        self.module.SERVICE_HOME=self.base/'service'
        self.public=self.base/'public'
        self.folder=self.public/'kid-player/releases';self.folder.mkdir(parents=True)
        self.apk=self.folder/'kid-player-test-14.apk';self.apk.write_bytes(b'verified-apk')
        import hashlib
        self.manifest=self.folder.parent/'update.json'
        self.manifest.write_text(json.dumps({'apkUrl':'https://example.com/kid-player-test-14.apk','size':self.apk.stat().st_size,'sha256':hashlib.sha256(self.apk.read_bytes()).hexdigest()}))
    def tearDown(self):self.temp.cleanup()
    def test_idempotent_and_only_public_release_is_mirrored(self):
        (self.public/'secret.txt').write_text('private')
        self.module.mirror(self.public);self.module.mirror(self.public)
        dest=self.module.SERVICE_HOME/'public'
        self.assertEqual((dest/'kid-player/update.json').read_bytes(),self.manifest.read_bytes())
        self.assertEqual((dest/'kid-player/releases'/self.apk.name).read_bytes(),b'verified-apk')
        self.assertFalse((dest/'secret.txt').exists())
    def test_bad_apk_does_not_replace_good_manifest(self):
        self.module.mirror(self.public)
        dest=self.module.SERVICE_HOME/'public/kid-player/update.json';before=dest.read_bytes()
        self.apk.write_bytes(b'corrupt')
        with self.assertRaises(ValueError):self.module.mirror(self.public)
        self.assertEqual(dest.read_bytes(),before)

if __name__=='__main__':unittest.main()
