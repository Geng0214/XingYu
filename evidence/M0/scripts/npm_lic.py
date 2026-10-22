import io
import os
import re
import json
import ssl
import gzip
import hashlib
import tarfile
import urllib.request
import collections

PROXY = 'http://127.0.0.1:7897'
OUT = 'C:/tmp/npm_licenses.json'
TAR = 'C:/tmp/npm_packages'
LOCKC = json.load(io.open('C:/tmp/npm_mcp_closure.json', encoding='utf-8'))
PKGS = [(r['pkg'], r['version']) for r in LOCKC['mcp_bridge_sdk'] if r.get('in_lock')]
PKGS = sorted(set(PKGS), key=lambda x: x[0].lower())
import sys
if len(sys.argv) > 1:
    n = int(sys.argv[1])
    PKGS = PKGS[:n]
    sys.argv.pop()
    OUT = 'C:/tmp/npm_licenses_smoke.json'
    TAR = 'C:/tmp/npm_packages_smoke'

opener = urllib.request.build_opener(
    urllib.request.ProxyHandler({'http': PROXY, 'https': PROXY}),
    urllib.request.HTTPSHandler(context=ssl.create_default_context()))


def get(url, binary=False, tries=3):
    last = None
    for _ in range(tries):
        try:
            req = urllib.request.Request(url, headers={'User-Agent': 'licence-audit'})
            r = opener.open(req, timeout=45)
            data = r.read()
            return data if binary else data.decode('utf-8', 'replace')
        except Exception as e:
            last = e
    raise last


LIC_NAME = re.compile(r'^(LICENSE|LICENCE|COPYING|COPYRIGHT|UNLICENSE|NOTICE|Open\s*Font\s*License)', re.I)


def norm(s):
    return re.sub(r'\s+', ' ', s or '').strip()


def classify(path):
    b = io.open(path, 'rb').read(4000)
    t = norm(b.decode('utf-8', 'replace')).lower()
    size = os.path.getsize(path)
    apache = 'apache license' in t and ('version 2.0' in t or 'version2.0' in t)
    mit = 'permission is hereby granted, free of charge' in t
    unl = 'this is free and unencumbered software released into the public domain' in t
    uni = 'unicode, inc.' in t or 'unicode terms of use' in t
    mpl = 'mozilla public license' in t
    bsd3 = 'neither the name' in t and 'redistribution and use in source and binary forms' in t
    bsd = 'redistribution and use in source and binary forms' in t
    isc = 'permission to use, copy, modify, and/or distribute this software' in t
    lgpl = 'gnu lesser general public license' in t or 'gnu gnu lesser' in t
    gpl = ('gnu general public license' in t) and not lgpl
    ofl = 'sil open font license' in t or 'open font license' in t
    cc0 = 'creative commons' in t and ('zero' in t or 'cc0' in t)
    out = []
    for nm, ok in (('Apache-2.0', apache), ('MIT', mit), ('Unlicense', unl), ('MPL-2.0', mpl),
                   ('BSD-3-Clause', bsd3), ('BSD-2-or-other', bsd and not bsd3), ('ISC', isc),
                   ('LGPL', lgpl), ('GPL', gpl), ('OFL', ofl), ('Unicode-Terms', uni),
                   ('CC0', cc0)):
        if ok:
            out.append(nm)
    if 'dual-licensed under the unlicense and mit' in t or ('unlicense' in t and 'mit' in t and 'dual' in t):
        out.append('DUAL(MIT OR Unlicense)')
    if not out:
        out.append('UNCLASSIFIED(%dB)' % size)
    return '+'.join(out)


os.makedirs(TAR, exist_ok=True)
rows = []
for name, ver in PKGS:
    esc = name.replace('/', '%2F')
    url = 'https://registry.npmjs.org/%s/%s' % (esc, ver)
    rec = {'pkg': name, 'version': ver, 'registry_url': url}
    try:
        meta = json.loads(get(url))
    except Exception as e:
        rec['error'] = 'metadata: %s' % e
        rows.append(rec)
        continue
    rec['license_field'] = meta.get('license')
    if isinstance(rec['license_field'], dict):
        rec['license_field'] = rec['license_field'].get('type')
    repo = meta.get('repository')
    rec['repository'] = repo.get('url') if isinstance(repo, dict) else repo
    rec['deprecated'] = meta.get('deprecated')
    dist = meta.get('dist') or {}
    rec['shasum'] = dist.get('shasum')
    rec['integrity'] = (dist.get('integrity') or '')[:16]
    tb = dist.get('tarball')
    rec['tarball'] = tb
    files = []
    if tb:
        fn = os.path.join(TAR, (name.replace('/', '_') + '-' + ver + '.tgz'))
        try:
            blob = get(tb, binary=True)
            rec['tarball_bytes'] = len(blob)
            rec['tarball_sha256_16'] = hashlib.sha256(blob).hexdigest()[:16]
            io.open(fn, 'wb').write(blob)
            tf = tarfile.open(fileobj=io.BytesIO(gzip.decompress(blob)))
            allnames = [m.name for m in tf.getmembers() if m.isfile()]
            rec['package_file_count'] = len(allnames)
            cands = [m for m in allnames
                     if LIC_NAME.match(os.path.basename(m.split('/', 1)[1] if '/' in m else m))]
            rec['licence_like_files'] = sorted(os.path.basename(c) for c in cands)
            for m in cands:
                if not m.startswith('package/') or '..' in m.split('/'):
                    continue
                tf.extract(m, os.path.join(TAR, 'x', name.replace('/', '_') + '-' + ver),
                           filter='data')
                p = os.path.join(TAR, 'x', name.replace('/', '_') + '-' + ver, *m.split('/'))
                if not os.path.exists(p):
                    continue
                files.append({'path': m,
                              'bytes': os.path.getsize(p),
                              'sha256_16': hashlib.sha256(io.open(p, 'rb').read()).hexdigest()[:16],
                              'text_class': classify(p)})
        except Exception as e:
            rec['error'] = 'tarball: %s' % e
    rec['licence_files'] = files
    if not files:
        rec['licence_text_present_in_tarball'] = False
    rows.append(rec)
    print('%-38s %-10s %-28s %s' % (
        name, ver, str(rec.get('license_field'))[:28],
        '; '.join(f['text_class'] for f in files) or 'NO-LICENCE-FILE'))

io.open(OUT, 'w', encoding='utf-8').write(json.dumps(
    {'measured_pkgs': len(rows), 'packages': rows}, ensure_ascii=False, indent=1))

print()
lf = collections.Counter(str(r.get('license_field')) for r in rows)
print('license_field histogram:')
for k, v in lf.most_common():
    print('   %-40s %d' % (k[:40], v))
print('no licence file in tarball:', sum(1 for r in rows if not r.get('licence_files')))
print('errors:', [(r['pkg'], r['error']) for r in rows if r.get('error')])
print('UNCLASSIFIED files:', [(r['pkg'], f['path'], f['text_class'])
                              for r in rows for f in r.get('licence_files', [])
                              if 'UNCLASSIFIED' in f['text_class']])
copyleft = [r['pkg'] for r in rows if re.search(r'GPL|LGPL|MPL|AGPL', str(r.get('license_field')), re.I)]
print('copyleft by license_field:', copyleft)
