import io
import os
import re
import json
import hashlib
import collections

L = json.load(io.open('C:/tmp/npm_licenses.json', encoding='utf-8'))
C = json.load(io.open('C:/tmp/npm_mcp_closure.json', encoding='utf-8'))
lockint = {}
for r in C['mcp_bridge_sdk']:
    if r.get('in_lock'):
        lockint[(r['pkg'], r['version'])] = r.get('integrity_sha512_16') or ''


def norm(s):
    return re.sub(r'\s+', ' ', s or '').strip()


def classify(path):
    raw = io.open(path, 'rb').read()
    t = norm(raw.decode('utf-8', 'replace')).lower()
    size = len(raw)
    apache = 'apache license' in t and 'version 2.0' in t
    mit = 'permission is hereby granted, free of charge' in t
    unl = 'this is free and unencumbered software released into the public domain' in t
    uni = 'unicode, inc.' in t or 'unicode terms of use' in t
    mpl = 'mozilla public license' in t
    endorse = ('may not be used to endorse or promote' in t
               or 'neither the name' in t)
    bsd3 = 'redistribution and use in source and binary forms' in t and endorse
    bsd = 'redistribution and use in source and binary forms' in t
    isc = 'permission to use, copy, modify, and/or distribute this software' in t
    lgpl = 'gnu lesser general public license' in t
    gpl = ('gnu general public license' in t) and not lgpl
    ofl = 'sil open font license' in t or 'open font license' in t
    cc0 = 'creative commons' in t and ('zero' in t or 'cc0' in t)
    out = []
    for nm, ok in (('Apache-2.0', apache), ('MIT', mit), ('Unlicense', unl), ('MPL-2.0', mpl),
                   ('BSD-3-Clause', bsd3), ('BSD-2-or-other', bsd and not bsd3), ('ISC', isc),
                   ('LGPL', lgpl), ('GPL', gpl), ('OFL', ofl), ('Unicode-Terms', uni), ('CC0', cc0)):
        if ok:
            out.append(nm)
    if not out:
        out.append('UNCLASSIFIED(%dB)' % size)
    holders = re.findall(r'(?m)^.{0,8}Copyright(?: \(c\)| ©| Copyright)?\s*([^\n]{0,120})$',
                         raw.decode('utf-8', 'replace'))
    return '+'.join(out), [norm(h) for h in holders[:4]], size, hashlib.sha256(raw).hexdigest()[:16]


mismatch = []
int_mismatch = []
text_variants = collections.Counter()
for r in L['packages']:
    p = os.path.join('C:/tmp/npm_packages/x', r['pkg'].replace('/', '_') + '-' + r['version'],
                     *r['licence_files'][0]['path'].split('/')) if r.get('licence_files') else None
    cls, holders, size, sha = classify(p)
    old = r['licence_files'][0]['text_class']
    f = str(r.get('license_field'))
    agree = ((f == 'MIT' and cls == 'MIT') or (f == 'ISC' and cls == 'ISC')
             or (f == 'BSD-3-Clause' and cls == 'BSD-3-Clause')
             or (f == 'BSD-2-Clause' and cls in ('BSD-2-or-other', 'BSD-3-Clause'))
             or (f in cls))
    r['licence_files'][0].update({'text_class': cls, 'bytes': size, 'sha256_16': sha,
                                 'copyright_lines': holders})
    r['field_matches_shipped_text'] = agree
    if not agree:
        mismatch.append((r['pkg'], r['version'], f, cls))
    li = lockint.get((r['pkg'], r['version']), '')
    ri = (r.get('integrity') or '')
    if li and ri and li != ri:
        int_mismatch.append((r['pkg'], r['version'], li, ri))
    text_variants[sha] += 1
    if old != cls:
        print('reclassified:', r['pkg'], r['version'], old, '->', cls)

print()
print('registry integrity == lockfile integrity for',
      sum(1 for r in L['packages'] if lockint.get((r['pkg'], r['version'])) == (r.get('integrity') or '')),
      '/', len(L['packages']))
print('integrity mismatches:', int_mismatch)
print('declared field vs shipped text disagreements:', mismatch)
print('distinct licence texts:', len(text_variants), 'max sharing:', text_variants.most_common(3))
hist = collections.Counter((str(r.get('license_field')), r['licence_files'][0]['text_class'])
                           for r in L['packages'])
for (f, c), n in hist.most_common():
    print('   field=%-16s text=%-24s %d' % (f, c, n))
nonstd = [(r['pkg'], r['version'], r['licence_files'][0]['text_class'])
          for r in L['packages']
          if 'BSD-3-Clause' in r['licence_files'][0]['text_class']
          or 'BSD-2' in r['licence_files'][0]['text_class']]
print('BSD family detail:', nonstd)
json.dump(L, io.open('C:/tmp/npm_licenses.json', 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
