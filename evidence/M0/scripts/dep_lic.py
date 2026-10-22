import os, re, json, collections, glob
import xml.etree.ElementTree as ET

TREE = 'C:/Users/15942/Desktop/Work/operit/evidence/M0/dependency-tree-clone-runtime.raw.txt'
CACHE = 'C:/Users/15942/Desktop/Work/.gradle-m0/caches/modules-2/files-2.1'
MANUAL = 'C:/Users/15942/Desktop/Work/operit/manual-deps'

line_re = re.compile(r'[+\\|`\-]{3,}\s*([A-Za-z0-9._\-\[\]]+(?:[A-Za-z0-9._\-\[\]]*)):([A-Za-z0-9._\-]+):([A-Za-z0-9._\-]+)')

coords = collections.OrderedDict()
top_level = set()
with open(TREE, encoding='utf-8', errors='replace') as f:
    for ln in f:
        m = line_re.search(ln)
        if not m:
            continue
        g, n, v = m.group(1), m.group(2), m.group(3)
        if g in ('project',):
            continue
        c = (g, n, v)
        coords.setdefault(c, 0)
        coords[c] += 1
        pref = ln.split(m.group(0))[0] if False else ln[:4]
        if ln.startswith('+---') or ln.startswith('\\---'):
            top_level.add(c)

print('unique coordinates: %d, declared at top level: %d' % (len(coords), len(top_level)))


def pom_path(g, n, v):
    p = os.path.join(CACHE, g, n, v)
    if not os.path.isdir(p):
        return None
    for d in os.listdir(p):
        f = os.path.join(p, d, '%s-%s.pom' % (n, v))
        if os.path.isfile(f):
            return f
    return None


NS = re.compile(r'\{.*\}')


def clean(tag):
    return NS.sub('', tag)


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
            return out
    return None


def resolve(g, n, v, depth=0):
    """returns (licenses list, pom path, parent chain)"""
    p = pom_path(g, n, v)
    chain = []
    while p is not None and depth < 6:
        try:
            root = ET.parse(p).getroot()
        except Exception as e:
            return None, p, chain
        ls = licenses_of(root)
        if ls:
            return ls, p, chain
        par = None
        for c in root:
            if clean(c.tag) == 'parent':
                par = c
        if par is None:
            return None, p, chain
        pg = get(par, 'groupId') or g
        pn = get(par, 'artifactId')
        pv = get(par, 'version')
        if not pn or not pv:
            return None, p, chain
        chain.append('%s:%s:%s' % (pg, pn, pv))
        g, n, v = pg, pn, pv
        p = pom_path(g, n, v)
        depth += 1
    return None, None, chain


COPyleft = re.compile(r'GPL|LGPL|AGPL|Copyleft|Mozilla Public|MPL', re.I)
PERMISSIVE = re.compile(r'Apache|MIT|BSD|ISC|Eclipse|EPL|Public Domain|CC0|Unlicense|SIL Open', re.I)

rows = []
for (g, n, v) in sorted(coords):
    ls, p, chain = resolve(g, n, v)
    if ls:
        names = '; '.join((x[0] or '?') for x in ls if x[0])
        urls = '; '.join((x[1] or '') for x in ls if x[1])
    else:
        names = ''
        urls = ''
    kind = 'UNKNOWN'
    if names:
        if COPyleft.search(names):
            kind = 'COPYLEFT?'
        elif PERMISSIVE.search(names):
            kind = 'permissive'
        else:
            kind = 'other'
    rows.append(dict(group=g, name=n, version=v, top=(g, n, v) in top_level,
                     licenses=names, url=urls, kind=kind, pom=os.path.basename(p or '')))

unclear = [r for r in rows if r['kind'] in ('UNKNOWN', 'COPYLEFT?', 'other')]
print('with licence from POM: %d / %d' % (len(rows) - len(unclear), len(rows)))
print('need attention: %d' % len(unclear))
for r in unclear:
    print('   %-8s %-40s %s' % (r['kind'], r['group'] + ':' + r['name'], r['licenses'][:60]))

cnt = collections.Counter(r['licenses'] for r in rows if r['licenses'])
print()
print('licence strings, by count of coordinates:')
for k, v in cnt.most_common(30):
    print('  %4d  %s' % (v, k[:100]))

json.dump(rows, open('C:/tmp/gradle_licenses.json', 'w', encoding='utf-8'), ensure_ascii=True, indent=1)
print()
print('wrote C:/tmp/gradle_licenses.json')
if os.path.isdir(MANUAL):
    print('manual-deps contents:', os.listdir(MANUAL))
