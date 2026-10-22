import collections
import io
import json
import os

lic = json.load(io.open('C:/tmp/rust_licenses.json', encoding='utf-8'))
SRC = 'C:/Users/15942/dev/rust/cargo/registry/src/index.crates.io-1949cf8c6b5b557f'
want = {'01c266bced4a434d', 'a60eea8175145316', '62c7a1e35f564068', 'c16f8dcf1a368b83', 'c6596eb7be8581c1'}
seen = {}
for p in lic['packages']:
    for f in p.get('licence_files') or []:
        if f['sha256_16'] in want and f['sha256_16'] not in seen:
            path = os.path.join(SRC, '%s-%s' % (p['crate'], p['version']), f['path'].replace('/', os.sep))
            seen[f['sha256_16']] = (p['crate'], p['version'], f['path'], path, f['bytes'], f['licence_files'] if 'licence_files' in f else None)
for fp, info in seen.items():
    print('=== %s  %s %s  %s  (%d bytes)' % (fp, info[0], info[1], info[2], info[3 + 1] if False else info[4]))
    b = io.open(info[3], 'rb').read(400)
    print(b.decode('utf-8', 'replace').encode('unicode_escape').decode().replace('\\n', '\n')[:400])
    print()

print('--- all licence file names per crate (first 6):')
for p in lic['packages']:
    names = [f['path'] for f in (p.get('licence_files') or [])]
    print('%-24s %-9s src=%s files=%s' % (p['crate'], p['version'], p.get('src_present'), names))
