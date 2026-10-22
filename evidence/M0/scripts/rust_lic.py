import hashlib
import io
import json
import os
import re
import sys

REPO = 'C:/Users/15942/Desktop/Work/operit'
LOCK = REPO + '/tools/native_ripgrep/Cargo.lock'
CRATE_DIR = REPO + '/tools/native_ripgrep'
SRC = 'C:/Users/15942/dev/rust/cargo/registry/src/index.crates.io-1949cf8c6b5b557f'


def norm(s):
    return re.sub(r'\s+', ' ', s or '').strip()


def fingerprint(path):
    b = io.open(path, 'rb').read()
    h = hashlib.sha256(b).hexdigest()[:16]
    return h, len(b)


def classify(path):
    b = io.open(path, 'rb').read(800).decode('utf-8', 'replace')
    t = norm(b).lower()
    rules = [
        ('MIT', lambda: re.search(r'\bmit license\b', t) or ('permission is hereby granted, free of charge' in t and 'the software is provided' in t)),
        ('Apache-2.0', lambda: 'apache license, version 2.0' in t),
        ('Unlicense', lambda: 'this is free and unencumbered software released into the public domain' in t),
        ('MPL-2.0', lambda: 'mozilla public license version 2.0' in t),
        ('BSD-3-Clause', lambda: 'neither the name' in t and 'redistribution and use in source and binary forms' in t),
        ('BSD-2-Clause', lambda: 'redistribution and use in source and binary forms' in t and ' endorse ' not in t and 'neither the name' not in t),
        ('ISC', lambda: 'permission to use, copy, modify, and/or distribute this software' in t),
        ('LGPL-3.0', lambda: 'gnu lesser general public license' in t),
        ('GPL-3.0', lambda: 'gnu general public license' in t and 'lesser' not in t),
    ]
    for name, fn in rules:
        try:
            if fn():
                return name
        except Exception:
            pass
    return 'UNCLASSIFIED'


def cargo_toml_field(text, key):
    m = re.search(r'^' + key + r'\s*=\s*"([^"]+)"', text, re.M)
    return m.group(1) if m else None


def parse_lock(path):
    pkgs = []
    cur = None
    for line in io.open(path, encoding='utf-8'):
        line = line.rstrip('\n')
        if line.strip() == '[[package]]':
            cur = {}
            pkgs.append(cur)
            continue
        if cur is None:
            continue
        m = re.match(r'^(\w+)\s*=\s*"?(.*?)"?$', line.strip())
        if m:
            cur[m.group(1)] = m.group(2)
    return pkgs


def index_cache_license(name):
    """crates.io sparse index on-disk cache: binary file with \0 separators, JSON records."""
    idx_root = 'C:/Users/15942/dev/rust/cargo/registry/index/index.crates.io-1949cf8c6b5b557f/.cache'
    n = name.lower()
    if len(n) == 1:
        rel = '1/' + n
    elif len(n) == 2:
        rel = '2/' + n
    elif len(n) == 3:
        rel = '3/' + n[0] + '/' + n
    else:
        rel = n[0:2] + '/' + n[2:4] + '/' + n
    p = os.path.join(idx_root, rel)
    if not os.path.isfile(p):
        return None
    data = io.open(p, 'rb').read()
    out = None
    for part in data.split(b'\x00'):
        if not part.startswith(b'{'):
            continue
        try:
            rec = json.loads(part.decode('utf-8', 'replace'))
        except Exception:
            continue
        if rec.get('name', '').lower() != n:
            continue
        blob = part.decode('utf-8', 'replace')
        m = re.findall(r'"(?:licenses?|license)":\s*"([^"]+)"', blob)
        if m:
            out = m
    return out


pkgs = parse_lock(LOCK)
rows = []
for p in pkgs:
    name = p.get('name')
    ver = p.get('version')
    src = p.get('source', '')
    row = {'crate': name, 'version': ver, 'source': src or 'workspace'}
    if not src:
        # workspace member (root crate)
        ct = os.path.join(CRATE_DIR, 'Cargo.toml')
        text = io.open(ct, encoding='utf-8').read() if os.path.isfile(ct) else ''
        row['licence_declared'] = cargo_toml_field(text, 'license')
        row['licence_files'] = []
        for fn in sorted(os.listdir(CRATE_DIR)):
            if re.match(r'^(LICENSE|LICENCE|COPYING)', fn, re.I):
                fp, sz = fingerprint(os.path.join(CRATE_DIR, fn))
                row['licence_files'].append({'path': fn, 'sha256_16': fp, 'bytes': sz, 'text_class': classify(os.path.join(CRATE_DIR, fn))})
        rows.append(row)
        continue
    d = os.path.join(SRC, '%s-%s' % (name, ver))
    if os.path.isdir(d):
        ct = os.path.join(d, 'Cargo.toml')
        text = ''
        if os.path.isfile(ct):
            text = io.open(ct, encoding='utf-8').read()
        row['licence_declared'] = cargo_toml_field(text, 'license') or cargo_toml_field(text, 'licence')
        files = []
        for root, dirs, fs in os.walk(d):
            dirs[:] = [x for x in dirs if x not in ('target', '.git', 'tests', 'examples', 'benches')]
            for fn in fs:
                if re.match(r'^(LICENSE|LICENCE|COPYING|NOTICE)', fn, re.I):
                    full = os.path.join(root, fn)
                    rel = '/'.join(os.path.relpath(full, d).split(os.sep))
                    fp, sz = fingerprint(full)
                    files.append({'path': rel, 'sha256_16': fp, 'bytes': sz, 'text_class': classify(full)})
                    if len(files) > 8:
                        break
            if len(files) > 8:
                break
        row['licence_files'] = files
        row['src_present'] = True
    else:
        row['src_present'] = False
        lic = index_cache_license(name)
        if lic:
            row['licence_declared'] = '/'.join(lic)
            row['licence_from'] = 'registry-index-cache'
    rows.append(row)

missing = [r['crate'] for r in rows if not r.get('src_present') and not r.get('licence_declared')]
nodir = [r['crate'] + '-' + str(r['version']) for r in rows if r.get('source') and not r.get('src_present')]
summary = {}
for r in rows:
    summary[r.get('licence_declared') or 'NONE'] = summary.get(r.get('licence_declared') or 'NONE', 0) + 1

out = {'total_packages': len(rows), 'by_declared_licence': summary, 'no_source_dir': nodir, 'no_licence_at_all': missing, 'packages': rows}
io.open('C:/tmp/rust_licenses.json', 'w', encoding='utf-8').write(json.dumps(out, ensure_ascii=False, indent=1))
print('packages', len(rows))
print(json.dumps(summary, ensure_ascii=False, indent=1))
print('no src dir:', nodir)
print('no licence:', missing)
