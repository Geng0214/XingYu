import os, re, json, zipfile, collections
import xml.etree.ElementTree as ET

TREE = 'C:/Users/15942/Desktop/Work/operit/evidence/M0/dependency-tree-clone-runtime.raw.txt'
CACHE = 'C:/Users/15942/Desktop/Work/.gradle-m0/caches/modules-2/files-2.1'
OUT_JSON = 'C:/Users/15942/Desktop/Work/operit/evidence/M0/gradle-dependency-licenses.json'

body_re = re.compile(r'^(?:[|\\\-\s+]*)(\S.*)$')
marker_re = re.compile(r'\s*(\((\*)|c|n|s|f|u|i|p\)|\*)\)\s*$')


def split_coord(tok, inherit=None):
    p = tok.split(':')
    if len(p) >= 3:
        return (p[0], p[1], p[2])
    if len(p) == 2 and inherit:
        return (p[0], p[1], None)
    if len(p) == 1 and inherit:
        return (inherit[0], inherit[1], p[0])
    return None


def strip_markers(s):
    s = s.strip()
    for pat in (' (*)', ' (c)', ' (n)', ' (s)', ' (f)', ' (u)', ' (i)', ' (p)'):
        if s.endswith(pat):
            s = s[:-len(pat)]
    return s.strip(), None


selected = collections.OrderedDict()
suppressed = set()
constraints = set()
projects = set()

started = False
with open(TREE, encoding='utf-8', errors='replace') as f:
    for ln in f:
        ln = ln.rstrip('\n')
        if ln.startswith('Project '):
            started = True
            continue
        if not started:
            continue
        if ln.startswith('BUILD ') or ln.startswith('--- Finished'):
            break
        m = body_re.match(ln)
        if not m or ln.startswith('w:') or ln.startswith('C:'):
            continue
        body = m.group(1)
        if not re.match(r'^[A-Za-z0-9_.\-]+:', body):
            continue
        if body.startswith('project '):
            projects.add(body)
            continue
        is_constraint = body.rstrip().endswith('(c)')
        body = strip_markers(body)[0]
        if ' -> ' in body:
            left, right = body.split(' -> ', 1)
        else:
            left, right = body, None
        lc = split_coord(left.strip())
        if not lc:
            continue
        g, n, v = lc
        rc = None
        if right:
            rc = split_coord(right.strip(), inherit=(g, n))
        if rc:
            if v:
                suppressed.add((g, n, v))
            g, n, v = rc
        if not v:
            continue
        if is_constraint:
            constraints.add((g, n, v))
            continue
        selected[(g, n, v)] = True

sel = sorted(selected)
sup_only = sorted(suppressed - set(selected))
print('selected (resolved, can reach the APK): %d' % len(sel))
print('suppressed by conflict resolution (never downloaded, never in APK): %d' % len(sup_only))
print('constraints: %d ; project deps: %d' % (len(constraints), len(projects)))

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
                if nm:
                    out.append((nm, url))
            return out
    return None


def find_file(g, n, v, ext):
    d = os.path.join(CACHE, g, n, v)
    if not os.path.isdir(d):
        return None
    for sub in os.listdir(d):
        f = os.path.join(d, sub, '%s-%s.%s' % (n, v, ext))
        if os.path.isfile(f):
            return f
    return None


def pom_licenses(g, n, v):
    chain = []
    for _ in range(6):
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
        par = next((c for c in root if clean(c.tag) == 'parent'), None)
        if par is None:
            return None, chain
        pg, pn, pv = get(par, 'groupId'), get(par, 'artifactId'), get(par, 'version')
        if not (pn and pv):
            return None, chain
        chain.append('%s:%s:%s' % (pg or g, pn, pv))
        g, n, v = pg or g, pn, pv
    return None, chain


LIC_PAT = re.compile(r'/(LICENSE|LICENCE|NOTICE|COPYING|EULA|OPENSSL)([^/]*)?$', re.I)
FINGER = [
    (re.compile(r'Apache License.{0,40}Version 2\.0', re.I), 'Apache-2.0'),
    (re.compile(r'Permission is hereby granted, free of charge', re.I), 'MIT'),
    (re.compile(r'Redistribution and use in source and binary forms', re.I), 'BSD-family'),
    (re.compile(r'Eclipse Public License', re.I), 'EPL'),
    (re.compile(r'GNU AFFERO GENERAL PUBLIC', re.I), 'AGPL-3.0'),
    (re.compile(r'GNU LESSER GENERAL PUBLIC', re.I), 'LGPL'),
    (re.compile(r'GNU GENERAL PUBLIC LICENSE', re.I), 'GPL'),
    (re.compile(r'Mozilla Public License', re.I), 'MPL'),
    (re.compile(r'Creative Commons', re.I), 'CC'),
    (re.compile(r'unRAR', re.I), 'unRAR'),
]
TOP = set()
with open(TREE, encoding='utf-8', errors='replace') as f:
    for ln in f:
        if ln.startswith('+---') or ln.startswith('\\---'):
            mm = body_re.match(ln.rstrip('\n'))
            if not mm:
                continue
            b = strip_markers(mm.group(1))[0]
            left = b.split(' -> ')[0].strip()
            if not re.match(r'^[A-Za-z0-9_.\-]+:', left) or left.startswith('project '):
                continue
            pp = left.split(':')
            if len(pp) >= 2:
                TOP.add((pp[0], pp[1]))


def jar_info(g, n, v):
    f = find_file(g, n, v, 'jar') or find_file(g, n, v, 'aar')
    if not f:
        return None, []
    try:
        z = zipfile.ZipFile(f)
    except Exception:
        return None, []
    names = [i for i in z.namelist() if LIC_PAT.search('/' + i)]
    fps = []
    for i in sorted(names, key=len)[:4]:
        try:
            head = z.read(i).decode('utf-8', 'replace')[:800]
        except Exception:
            continue
        for pat, lab in FINGER:
            if pat.search(head):
                fps.append(lab)
                break
    return sorted(set(fps)), names


COP = re.compile(r'GPL|LGPL|AGPL|Affero|Copyleft|General Public License|Lesser GNU', re.I)
PERM = re.compile(r'Apache|MIT|BSD|ISC|Eclipse|EPL|Public Domain|CC0|Unlicense|SIL Open|Revised BSD', re.I)

rows = []
for (g, n, v) in sel:
    ls, chain = pom_licenses(g, n, v)
    lic = '; '.join(x[0] for x in ls) if ls else ''
    urls = '; '.join(x[1] for x in ls if x[1]) if ls else ''
    src = 'pom' if lic else None
    jf, jn = jar_info(g, n, v)
    if not lic:
        if jf:
            lic = '; '.join(jf)
            src = 'jar/aar META-INF text fingerprint'
        elif jn:
            src = 'licence file present in jar, text not fingerprinted'
        else:
            src = 'no pom licence, no in-archive licence file'
    kind = 'UNKNOWN'
    if lic:
        kind = 'COPYLEFT' if COP.search(lic) else ('permissive' if PERM.search(lic) else 'other')
    rows.append(dict(coord='%s:%s:%s' % (g, n, v), direct=(g, n) in TOP, licence=lic,
                     url=urls, source=src, kind=kind, jar_licence_entries=jn[:4],
                     cached=bool(find_file(g, n, v, 'pom') or find_file(g, n, v, 'aar') or find_file(g, n, v, 'jar'))))

print()
print(collections.Counter(r['kind'] for r in rows))
print(collections.Counter(r['source'] for r in rows))
print('direct declarations in tree: %d' % sum(1 for r in rows if r['direct']))
print('selected but nothing cached (suspicious): %d' % sum(1 for r in rows if not r['cached']))
print()
print('=== needs attention (COPYLEFT / other / UNKNOWN) ===')
for r in rows:
    if r['kind'] != 'permissive':
        print('%-9s %-52s direct=%-5s %-52s %s' % (r['kind'], r['coord'], r['direct'], r['licence'][:52], r['url'][:60]))
print()
hist = collections.Counter(r['licence'] for r in rows if r['licence'])
print('=== licence histogram (selected only) ===')
for k, v in hist.most_common(40):
    print('  %4d  %s' % (v, k[:96]))

json.dump(dict(generatedAt='2026-09-19',
               source='gradlew --offline :app:dependencies --configuration cloneRuntimeClasspath (GRADLE_USER_HOME=C:/Users/15942/Desktop/Work/.gradle-m0)',
               selected=len(sel), suppressed=len(sup_only), constraints=len(constraints),
               projectDependencies=sorted(projects),
               suppressedCoordinates=['%s:%s:%s' % c for c in sup_only],
               rows=rows),
          open(OUT_JSON, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
print()
print('wrote', OUT_JSON)
