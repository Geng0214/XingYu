import io
import json
import re
import collections

LOCK = 'C:/Users/15942/Desktop/Work/operit/pnpm-lock.yaml'
lines = io.open(LOCK, encoding='utf-8').read().split('\n')

start = None
for k, l in enumerate(lines):
    if l.rstrip() == 'packages:':
        start = k + 1
        break
assert start is not None

def unq(s):
    return s.strip().strip("'\"")

pkgs = {}
i = start
n = len(lines)
while i < n:
    l = lines[i]
    if l and not l.startswith('    ') and not l.startswith('  '):
        break
    m = re.match(r"^  '?([^':]+@[^':]+?)'?:\s*$", l)
    if m:
        key = unq(m.group(1))
        cur = {'key': key, 'dependencies': {}, 'optionalDependencies': {},
               'integrity': None, 'dev': False, 'engines': None}
        pkgs[key] = cur
        j = i + 1
        mode = None
        while j < n:
            s = lines[j]
            if s.strip() and not lines[j].startswith('    '):
                break
            s = s.strip()
            if s.startswith('resolution:'):
                mm = re.search(r"integrity: (sha\d+-[A-Za-z0-9+/=]+)", s)
                if mm:
                    cur['integrity'] = mm.group(1)
            elif s == 'dev: true':
                cur['dev'] = True
            elif re.match(r'^\w+:\s*$', s):
                mode = s[:-1].strip()
            elif mode in ('dependencies', 'optionalDependencies') and ':' in s:
                kk, vv = s.split(':', 1)
                cur[mode][unq(kk)] = unq(vv)
            j += 1
        i = j
        continue
    i += 1

by_base = collections.defaultdict(list)
for k in pkgs:
    by_base[k.split('(')[0]].append(k)

def name_of(key):
    k = key.split('(')[0]
    at = k.rfind('@')
    return k[:at] if at > 0 else k

def version_of(key):
    k = key.split('(')[0]
    at = k.rfind('@')
    return k[at + 1:] if at > 0 else k

def pick(base):
    """Choose the lock entry that actually carries dependencies for a base key."""
    cands = by_base.get(base)
    if not cands:
        return None
    for c in cands:
        if pkgs[c]['dependencies'] or pkgs[c]['optionalDependencies']:
            return c
    return cands[0]

ROOT = '@modelcontextprotocol/sdk@1.30.0'
assert ROOT in by_base, sorted(x for x in by_base if 'modelcontext' in x)

seen = {}
stack = [ROOT]
unresolved = []
while stack:
    b = stack.pop()
    if b in seen:
        continue
    full = pick(b)
    if full is None:
        seen[b] = None
        unresolved.append(b)
        continue
    seen[b] = full
    p = pkgs[full]
    for dm in ('dependencies', 'optionalDependencies'):
        for dn, dv in p[dm].items():
            stack.append('%s@%s' % (dn, dv))

rows = []
for b, full in seen.items():
    r = {'pkg': name_of(b), 'version': version_of(b)}
    if full is None:
        r['in_lock'] = False
    else:
        p = pkgs[full]
        r.update({'in_lock': True, 'lock_key': full,
                  'integrity_sha512_16': (p['integrity'] or '')[:16],
                  'dev_flag': p['dev'], 'deps': len(p['dependencies'])})
        r['licence_declared'] = None
    rows.append(r)
rows.sort(key=lambda r: r['pkg'].lower())

dev_only = [r['pkg'] for r in rows if r.get('dev_flag')]
print('packages in lockfile:', len(pkgs))
print('runtime closure from', ROOT, '=', len(rows), 'unresolved:', len(unresolved))
for r in rows:
    print('%-42s %-10s %s' % (r['pkg'], r['version'], 'DEV' if r.get('dev_flag') else ''))
io.open('C:/tmp/npm_mcp_closure.json', 'w', encoding='utf-8').write(
    json.dumps({'root': ROOT, 'closure_count': len(rows), 'unresolved': unresolved,
                'lockfile_total_packages': len(pkgs), 'packages': rows},
               ensure_ascii=False, indent=1))
