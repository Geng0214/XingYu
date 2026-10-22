import hashlib
import io
import json
import os
import re

REPO = 'C:/Users/15942/Desktop/Work/operit'
LOCK = REPO + '/tools/native_ripgrep/Cargo.lock'
CRATE_DIR = REPO + '/tools/native_ripgrep'
SRC = 'C:/Users/15942/dev/rust/cargo/registry/src/index.crates.io-1949cf8c6b5b557f'
NAME_RE = re.compile(r'(?i)^(license|licence|copying|copyright|notice|unlicense|ofl|apache|mit)')


def norm(s):
    return re.sub(r'\s+', ' ', s or '').strip()


def head(path, n=1200):
    b = io.open(path, 'rb').read(n)
    return norm(b.decode('utf-8', 'replace')).lower()


def classify(path):
    t = head(path)
    size = os.path.getsize(path)
    if 'dual-licensed under the unlicense and mit' in t or ('unlicense and mit licenses' in t):
        return 'DUAL-POINTER(Unlicense OR MIT)'
    apache = 'apache license' in t and 'version 2.0' in t
    mit = 'permission is hereby granted, free of charge' in t
    unl = ('this is free and unencumbered software released into the public domain' in t) or ('unlicense' in t and 'public domain' in t)
    uni = 'unicode, inc.' in t or 'unicode terms of use' in t or ('permission to use this data' in t and 'unicode' in t)
    mpl = 'mozilla public license' in t
    bsd3 = 'neither the name' in t and 'redistribution and use in source and binary forms' in t
    bsd = 'redistribution and use in source and binary forms' in t
    isc = 'permission to use, copy, modify, and/or distribute this software' in t
    lgpl = 'gnu lesser general public license' in t
    gpl = 'gnu general public license' in t and not lgpl
    ofl = 'sil open font license' in t or 'open font license' in t
    out = []
    for name, ok in (('Apache-2.0', apache), ('MIT', mit), ('Unlicense', unl), ('MPL-2.0', mpl),
                     ('BSD-3-Clause', bsd3), ('BSD-family', bsd and not bsd3), ('ISC', isc),
                     ('LGPL', lgpl), ('GPL', gpl and not lgpl), ('OFL', ofl), ('Unicode', uni)):
        if ok:
            out.append(name)
    if out:
        return ' + '.join(dict.fromkeys(x for x in out if x != 'BSD-family' or bsd3 is False))
    return 'UNCLASSIFIED(%d)' % size


def fingerprint(path):
    b = io.open(path, 'rb').read()
    return hashlib.sha256(b).hexdigest()[:16], len(b)


def parse_lock(path):
    pkgs, cur = [], None
    for line in io.open(path, encoding='utf-8'):
        s = line.strip()
        if s == '[[package]]':
            cur = {}
            pkgs.append(cur)
            continue
        if cur is None:
            continue
        m = re.match(r'^(\w+)\s*=\s*"?(.*?)"?$', s)
        if m:
            cur[m.group(1)] = m.group(2)
    return pkgs


def toml_field(text, key):
    m = re.search(r'^' + key + r'\s*=\s*"([^"]+)"', text, re.M)
    return m.group(1) if m else None


rows = []
for p in parse_lock(LOCK):
    name, ver, srcurl = p.get('name'), p.get('version'), p.get('source', '')
    row = {'crate': name, 'version': ver, 'source': srcurl or 'workspace-local'}
    d = CRATE_DIR if not srcurl else os.path.join(SRC, '%s-%s' % (name, ver))
    row['src_present'] = os.path.isdir(d)
    if row['src_present']:
        ct = os.path.join(d, 'Cargo.toml')
        text = io.open(ct, encoding='utf-8').read() if os.path.isfile(ct) else ''
        row['declared'] = toml_field(text, 'license') or toml_field(text, 'licence')
        files = []
        for root, dirs, fs in os.walk(d):
            dirs[:] = [x for x in dirs if x not in ('target', '.git', 'tests', 'examples', 'benches', 'fixtures', 'test_data')]
            for fn in sorted(fs):
                if NAME_RE.match(fn) and fn.lower().endswith(('.txt', '.md', '.rst', '')) or NAME_RE.match(fn):
                    full = os.path.join(root, fn)
                    fp, sz = fingerprint(full)
                    files.append({'path': '/'.join(os.path.relpath(full, d).split(os.sep)),
                                  'sha256_16': fp, 'bytes': sz, 'text_class': classify(full)})
        files.sort(key=lambda f: f['path'])
        row['licence_files'] = files
    else:
        row['declared'] = None
        row['licence_files'] = []
    rows.append(row)

io.open('C:/tmp/rust_licenses2.json', 'w', encoding='utf-8').write(
    json.dumps({'packages': rows}, ensure_ascii=False, indent=1))

for r in rows:
    fl = ' | '.join('%s:%s(%d)' % (f['path'], f['text_class'], f['bytes']) for f in r['licence_files'])
    print('%-24s %-9s %-38s %s' % (r['crate'], r['version'], str(r.get('declared')), fl if fl else ('NO-FILE src=%s' % r['src_present'])))
