import os, re, hashlib, json

ROOTS = ['C:/M0/A/app/src/main/cpp/.cxx/operit_deps',
         'C:/M0/A/avator/dragonbones/.cxx/operit_deps',
         'C:/M0/A/avator/fbx/.cxx/operit_deps',
         'C:/M0/A/avator/mmd/.cxx/operit_deps',
         'C:/M0/A/llm/llama/.cxx/operit_deps',
         'C:/M0/A/llm/mnn/.cxx/operit_deps',
         'C:/M0/A/quickjs/src/main/cpp/.cxx/operit_deps']
PAT = re.compile(r'^(LICENSE|LICENCE|COPYING|NOTICE|COPYRIGHT|EULA|MIT-LICENSE|Apache.*LICENSE)([.-].*)?$', re.I)
FINGER = [
    (re.compile(r'Apache License\s*\n?\s*Version 2\.0', re.I), 'Apache-2.0'),
    (re.compile(r'Permission is hereby granted, free of charge', re.I), 'MIT'),
    (re.compile(r'Redistribution and use in source and binary forms.*\(including', re.I | re.S), 'BSD-family'),
    (re.compile(r'GNU GENERAL PUBLIC LICENSE', re.I), 'GPL'),
    (re.compile(r'GNU LESSER GENERAL PUBLIC LICENSE', re.I), 'LGPL'),
    (re.compile(r'GNU AFFERO', re.I), 'AGPL'),
    (re.compile(r'Mozilla Public License Version 2\.0', re.I), 'MPL-2.0'),
    (re.compile(r'Creative Commons', re.I), 'CC'),
    (re.compile(r'SIL OPEN FONT LICENSE', re.I), 'OFL'),
    (re.compile(r'zlib license|This software is provided \'as-is\'', re.I), 'zlib'),
    (re.compile(r'UNIVERSITY OF CALIFORNIA|Regents of the University of California', re.I), 'BSD-univ'),
]
out = []
for R in ROOTS:
    for d in sorted(os.listdir(R)):
        p = os.path.join(R, d)
        if not os.path.isdir(p) or not d.endswith('-src'):
            continue
        name, commit = d[:-4].rsplit("-", 1)
        found = []
        for dirpath, dirnames, filenames in os.walk(p):
            rel = os.path.relpath(dirpath, p).replace('\\', '/')
            if rel.count('/') > 2:
                dirnames[:] = []
                continue
            for fn in filenames:
                if PAT.match(fn):
                    full = os.path.join(dirpath, fn)
                    try:
                        b = open(full, 'rb').read()
                    except Exception:
                        continue
                    head = b[:2000].decode('utf-8', 'replace')
                    lab = None
                    for pat, l in FINGER:
                        if pat.search(head):
                            lab = l
                            break
                    found.append(dict(path=(fn if rel == '.' else rel + '/' + fn),
                                      licence=lab, sha256=hashlib.sha256(b).hexdigest()[:16],
                                      bytes=len(b)))
        out.append(dict(dep=name, commit=commit, licence_files=found))

for e in out:
    tops = [f for f in e['licence_files'] if '/' not in f['path']]
    print('== %-16s commit=%s  licence files within depth<=3: %d (top-level: %d)'
          % (e['dep'], e['commit'][:12], len(e['licence_files']), len(tops)))
    for f in (tops or e['licence_files'])[:6]:
        print('     %-46s %-12s %8d B  %s' % (f['path'], f['licence'] or 'UNFINGERPRINTED', f['bytes'], f['sha256']))
    if not tops:
        print('     (no top-level licence file)')
json.dump(out, open('C:/tmp/native_licenses.json', 'w'), ensure_ascii=True, indent=1)
print('\nwrote C:/tmp/native_licenses.json')
