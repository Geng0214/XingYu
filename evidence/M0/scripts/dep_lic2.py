import os, re, json, zipfile, collections, hashlib

TREE = 'C:/Users/15942/Desktop/Work/operit/evidence/M0/dependency-tree-clone-runtime.raw.txt'
CACHE = 'C:/Users/15942/Desktop/Work/.gradle-m0/caches/modules-2/files-2.1'
OUT_JSON = 'C:/Users/15942/Desktop/Work/operit/evidence/M0/gradle-dependency-licenses.json'
line_re = re.compile(r'[+\\|`\-]{3,}\s*([A-Za-z0-9._\-\[\]]+(?:[A-Za-z0-9._\-\[\]]*)):([A-Za-z0-9._\-]+):([A-Za-z0-9._\-]+)')

coords, top_level = [], set()
with open(TREE, encoding='utf-8', errors='replace') as f:
    for ln in f:
        m = line_re.search(ln)
        if not m or m.group(1) == 'project':
            continue
        c = (m.group(1), m.group(2), m.group(3))
        coords.append(c)
        if ln.startswith('+---') or ln.startswith('\\---'):
            top_level.add(c)
coords = sorted(set(coords))

NS = re.compile(r'\{.*\}')


def clean(t):
    return NS.sub('', t)


def get(el, name):
    for c in el:
        if clean(c.tag) == name:
            return (c.text or '').strip()
    return None


def licenses_of(el):
    for c in el:
        if clean(c.tag) == 'licenses':
            out = []
            for lc in c:
                nm = url = None
                for x in lc:
                    if clean(x.tag) == 'name':
                        nm = (x.text or '').strip()
                    if clean(x.tag) == 'url':
                        url = (x.text or '').strip()
                out.append((nm, url))
            return [x for x in out if x[0]]
    return None


import xml.etree.ElementTree as ET


def pom_dir(g, n, v):
    p = os.path.join(CACHE, g, n, v)
    return p if os.path.isdir(p) else None


def find_file(g, n, v, ext):
    d = pom_dir(g, n, v)
    if not d:
        return None
    for sub in os.listdir(d):
        f = os.path.join(d, sub, '%s-%s.%s' % (n, v, ext))
        if os.path.isfile(f):
            return f
    return None


def pom_licenses(g, n, v):
    chain = []
    d = 0
    while d < 6:
        p = find_file(g, n, v, 'pom')
        if not p:
            return None, chain
        try:
            root = ET.parse(p).getroot()
        except Exception:
            return None, chain
        ls = licenses_of(root)
        if ls:
            return ls, chain
        par = None
        for c in root:
            if clean(c.tag) == 'parent':
                par = c
        if par is None:
            return None, chain
        pg, pn, pv = get(par, 'groupId'), get(par, 'artifactId'), get(par, 'version')
        if not (pn and pv):
            return None, chain
        chain.append('%s:%s:%s' % (pg or g, pn, pv))
        g, n, v = pg or g, pn, pv
        d += 1
    return None, chain


LIC_PAT = re.compile(r'(META-INF/)?(LICENSE|LICENCE|NOTICE|COPYING|EULA)[^/]*$', re.I)
FINGER = [
    (re.compile(r'Apache License\s*(?://|\*)?\s*Version 2\.0', re.I), 'Apache-2.0'),
    (re.compile(r'Permission is hereby granted, free of charge', re.I), 'MIT'),
    (re.compile(r'Redistribution and use in source and binary forms', re.I), 'BSD-family'),
    (re.compile(r'Eclipse Public License', re.I), 'EPL'),
    (re.compile(r'GNU AFFERO GENERAL PUBLIC', re.I), 'AGPL-3.0'),
    (re.compile(r'GNU LESSER GENERAL PUBLIC', re.I), 'LGPL'),
    (re.compile(r'GNU GENERAL PUBLIC LICENSE', re.I), 'GPL'),
    (re.compile(r'Mozilla Public License', re.I), 'MPL'),
    (re.compile(r'Creative Commons', re.I), 'CC'),
    (re.compile(r'unRAR', re.I), 'unRAR'),
    (re.compile(r'SIL OPEN FONT LICENSE', re.I), 'OFL'),
]


def jar_licenses(g, n, v):
    f = find_file(g, n, v, 'jar') or find_file(g, n, v, 'aar')
    if not f:
        return None, [], None
    try:
        z = zipfile.ZipFile(f)
    except Exception:
        return None, [], f
    names = [i for i in z.namelist() if LIC_PAT.search(i)]
    got = []
    for i in sorted(names, key=len)[:3]:
        try:
            txt = z.read(i).decode('utf-8', 'replace')
        except Exception:
            continue
        head = txt[:600]
        fp = None
        for pat, lab in FINGER:
            if pat.search(head):
                fp = lab
                break
        got.append(dict(entry=i, fingerprint=fp, sha256=hashlib.sha256(z.read(i)).hexdigest()[:16],
                        bytes=len(z.read(i)) if False else None))
    return got, names, f


COPyleft = re.compile(r'GPL|LGPL|AGPL|Copyleft|Affero', re.I)
PERM = re.compile(r'Apache|MIT|BSD|ISC|Eclipse|EPL|Public Domain|CC0|Unlicense|SIL Open|Revised BSD', re.I)

rows = []
for (g, n, v) in coords:
    ls, chain = pom_licenses(g, n, v)
    pom_names = '; '.join(x[0] for x in ls) if ls else ''
    urls = '; '.join(x[1] for x in ls if x[1]) if ls else ''
    jarinfo = None
    if not pom_names:
        jarinfo, jnames, _ = jar_licenses(g, n, v)
    src = 'pom'
    lic = pom_names
    if not lic:
        if jarinfo:
            fp = [j['fingerprint'] for j in jarinfo if j['fingerprint']]
            lic = '; '.join(sorted(set(fp))) if fp else ''
            src = 'jar-META-INF' if fp else 'jar-entry-only'
        else:
            src = 'none'
    kind = 'UNKNOWN'
    if lic:
        if COPyleft.search(lic):
            kind = 'COPYLEFT'
        elif PERM.search(lic):
            kind = 'permissive'
        else:
            kind = 'other'
    rows.append(dict(coord='%s:%s:%s' % (g, n, v), group=g, name=n, version=v,
                     top=bool((g, n, v) in top_level), licence=lic, source=src,
                     kind=kind, pom_parent=chain, jar_lic_entries=(jnames if jarinfo is not None else None)))

print('coords %d' % len(rows))
print(collections.Counter(r['kind'] for r in rows))
print(collections.Counter(r['source'] for r in rows))
print()
print('still UNKNOWN:')
for r in rows:
    if r['kind'] == 'UNKNOWN':
        print('   %-60s src=%-14s entries=%s' % (r['coord'], r['source'], (r['jar_lic_entries'] or [])[:2]))
json.dump(dict(generatedAt='2026-09-19', configuration=':app cloneRuntimeClasspath (offline report task)',
               total=len(rows), rows=rows),
          open(OUT_JSON, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
print()
print('wrote', OUT_JSON)
