import io
import json
import re

LOCK = 'C:/Users/15942/Desktop/Work/operit/pnpm-lock.yaml'
lines = io.open(LOCK, encoding='utf-8').read().split('\n')


def unq(s):
    return s.strip().strip("'\"")


pkgs = {}
i = 0
n = len(lines)
start = None
for k, l in enumerate(lines):
    if l.rstrip() == 'packages:':
        start = k + 1
        break
assert start is not None, 'no packages section'

cur = None
mode = None
while True:
    if i >= n:
        break
    l = lines[i]
    if start and i < start:
        i += 1
        continue
    m = re.match(r"^  ('?[^:']+'?@\d[^:]*):\s*$", l)
    if m:
        key = unq(m.group(1))
        cur = {'key': key, 'dependencies': {}, 'optionalDependencies': {}, 'peerDependencies': {},
               'integrity': None, 'dev': 'dev: true' in ''.join(lines[i + 1:i + 5])}
        base = len(l) - len(l.lstrip())
        pkgs[key] = cur
        mode = None
        i += 1
        continue
    if cur is not None:
        s = l.strip()
        if s.startswith('resolution:'):
            mm = re.search(r"integrity: (sha\d+-[A-Za-z0-9+/=]+)", s)
            if mm:
                cur['integrity'] = mm.group(1)
            cur['deprecated'] = 'deprecated' if 'deprecated:' in s else cur.get('deprecated')
        elif s.endswith(':') and re.match(r'^\w+:$', s):
            mode = s[:-1]
        elif mode in ('dependencies', 'optionalDependencies') and re.match(r"^\S.*:\s*\S", s):
            kk, vv = s.split(':', 1)
            cur[mode][unq(kk)] = unq(vv)
    i += 1
    if i > n:
        break


def norm_key(name, ver):
    """lockfile keys look like 'ajv@8.17.1' or 'zod@3.25.76' possibly with peer suffix in parens."""
    for k in pkgs:
        base = k.split('(')[0]
        if base == '%s@%s' % (name, ver):
            return k
    return None


def name_of(key):
    k = key.split('(')[0]
    at = k.rfind('@')
    return k[:at] if at > 0 else k


def version_of(key):
    k = key.split('(')[0]
    at = k.rfind('@')
    return k[at + 1:] if at > 0 else k


roots = ['@modelcontextprotocol/sdk@1.30.0(zod@3.25.76)', '@modelcontextprotocol/sdk@1.30.0']
root = None
for r in roots:
    if r in pkgs:
        root = r
        break
if root is None:
    cand = [k for k in pkgs if k.startswith('@modelcontextprotocol/sdk@')]
    root = cand[0] if cand else None
print('sdk root key:', root)

seen = {}
stack = [root]
while stack:
    k = stack.pop()
    kk = k.split('(')[0]
    if kk in seen:
        continue
    p = pkgs.get(kk)
    if p is None:
        nm, ver = name_of(kk), version_of(kk)
        alt = None
        for x in pkgs:
            if x.split('(')[0] == '%s@%s' % (nm, ver):
                alt = x
                break
        if alt is None:
            seen[kk] = None
            continue
        p = pkgs[alt]
        kk = alt
    seen[kk] = p
    for dep_map in ('dependencies', 'optionalDependencies'):
        for dn, dv in p[dep_map].items():
            stack.append('%s@%s' % (dn, dv.split('(')[0] if False else dv))

rows = []
for k, p in seen.items():
    if p is None:
        rows.append({'pkg': name_of(k), 'version': version_of(k), 'in_lock': False})
        continue
    rows.append({'pkg': name_of(k), 'version': version_of(k), 'in_lock': True,
                 'integrity': p['integrity'], 'dev_only': p['dev'],
                 'direct_deps': len(p['dependencies'])})
rows.sort(key=lambda r: r['pkg'].lower())
print('runtime closure size:', len(rows), ' resolved:', sum(1 for r in rows if r['in_lock']))
for r in rows:
    print('%-40s %-12s %s' % (r['pkg'], r['version'], 'dev' if r.get('dev_only') else ''))
io.open('C:/tmp/npm_mcp_closure.json', 'w', encoding='utf-8').write(
    json.dumps({'root': root, 'count': len(rows), 'packages': rows}, ensure_ascii=False, indent=1))
