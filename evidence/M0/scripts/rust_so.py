import collections
import io
import json
import re

SO = 'C:/Users/15942/Desktop/Work/operit/app/src/main/jniLibs/arm64-v8a/liboperit_ripgrep.so'
OWN = 'C:/tmp/rgbuild/aarch64-linux-android/release/liboperit_ripgrep.so'

b = io.open(SO, 'rb').read()
print('shipped bytes', len(b))

pat = re.compile(rb'[/\\]registry[/\\]src[/\\]([^/\\]{5,80})[/\\]([^/\\]+)[/\\]')
hits = collections.Counter()
for m in pat.finditer(b):
    hits[(m.group(1).decode('latin-1'), m.group(2).decode('latin-1'))] += 1
print('registry/src path hits', sum(hits.values()))

pat2 = re.compile(rb'([A-Za-z0-9_\-]{2,40})-([0-9]+\.[0-9]+\.[0-9]+[0-9A-Za-z.\-]*)')
crate_tokens = collections.Counter()
for m in pat2.finditer(b):
    crate_tokens[m.group(1).decode('latin-1')] += 1

lock = json.load(io.open('C:/tmp/rust_licenses.json', encoding='utf-8'))
names = sorted({p['crate'] for p in lock['packages']})
present = [n for n in names if n in crate_tokens]
absent = [n for n in names if n not in crate_tokens]
print('lock crate names appearing as <name>-<semver> token in shipped .so:', len(present))
for n in present:
    print('   ', n, crate_tokens[n])
print('not appearing:', absent)
io.open('C:/tmp/rust_so_tokens.json', 'w', encoding='utf-8').write(
    json.dumps({'path_hits': {'%s|%s' % k: v for k, v in hits.items()},
                'lock_names_present': present,
                'lock_names_absent': absent,
                'token_counts': {n: crate_tokens[n] for n in present}}, ensure_ascii=False, indent=1))
