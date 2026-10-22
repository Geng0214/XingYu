"""Cache the pinned official Windows QuickJS CLI for host adapter tests, never in the APK."""
import hashlib,io,json,pathlib,urllib.request,zipfile

repo=pathlib.Path(__file__).resolve().parents[2]
target=repo/'.local/.gradle-m0/companion-tools/quickjs-2026-06-04'
url='https://bellard.org/quickjs/binary_releases/quickjs-win-x86_64-2026-06-04.zip'
expected='8d10e75796656f49a3797e2c14465bd67c1f085dba505cf0c8d8a14bf5b19cb4'
data=urllib.request.urlopen(url,timeout=40).read(2*1024*1024)
assert hashlib.sha256(data).hexdigest()==expected,'QuickJS binary archive changed'
archive=zipfile.ZipFile(io.BytesIO(data))
assert set(archive.namelist())=={'qjs.exe','libwinpthread-1.dll'}
target.mkdir(parents=True,exist_ok=True)
hashes={}
for name in ('qjs.exe','libwinpthread-1.dll'):
    content=archive.read(name)
    (target/name).write_bytes(content)
    hashes[name]=hashlib.sha256(content).hexdigest()
(target/'source-lock.json').write_text(json.dumps({'url':url,'archiveSha256':expected,'files':hashes},indent=2)+'\n',encoding='utf-8')
print(json.dumps(hashes))
