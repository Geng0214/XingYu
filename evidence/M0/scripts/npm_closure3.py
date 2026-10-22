import io
import json
import re
import collections

LOCK = 'C:/Users/15942/Desktop/Work/operit/pnpm-lock.yaml'
lines = io.open(LOCK, encoding='utf-8').read().split('\n')
KEY = re.compile(r"^  '?([^':]+@[^':]+?)'?:\s*(?:\{\})?\s*$")


def unq(s):
    return s.strip().strip("'\"")


def base_key(k):
    return k.split('(')[0]


def parse_section(name):
    start = None
    for k, l in enumerate(lines):
        if l.rstrip() == name + ':':
            start = k + 1
            break
    assert start is not None, name
    out = collections.OrderedDict()
    i = start
    while i < len(lines):
        l = lines[i]
        if l and not l.startswith('  '):
            break
        m = KEY.match(l)
        if not m:
            i += 1
            continue
        key = unq(m.group(1))
        cur = {'key': key, 'dependencies': {}, 'optionalDependencies': {},
               'integrity': None, 'dev': False}
        out[key] = cur
        j = i + 1
        mode = None
        while j < len(lines):
            raw = lines[j]
            if raw.strip() and not raw.startswith('    '):
                break
            s = raw.strip()
            if s.startswith('resolution:'):
                mm = re.search(r"integrity: (sha\d+-[A-Za-z0-9+/=]+)", s)
                if mm:
                    cur['integrity'] = mm.group(1)
            elif s == 'dev: true':
                cur['dev'] = True
            elif re.match(r'^\w+:[\s]*$', s):
                mode = s.rstrip(':')
            elif mode in ('dependencies', 'optionalDependencies') and ':' in s:
                kk, vv = s.split(':', 1)
                cur[mode][unq(kk)] = unq(vv)
            j += 1
        i = j
    return out


packages_raw = parse_section('packages')
packages = collections.OrderedDict()
for _k, _v in packages_raw.items():
    packages.setdefault(base_key(_k), _v)
snapshots = parse_section('snapshots')
print('packages:', len(packages), ' snapshots:', len(snapshots))


def base_of(key):
    return key.split('(')[0]


def name_of(key):
    k = base_of(key)
    at = k.rfind('@')
    return k[:at] if at > 0 else k


def version_of(key):
    k = base_of(key)
    at = k.rfind('@')
    return k[at + 1:] if at > 0 else k


snap_by_base = collections.defaultdict(list)
for k in snapshots:
    snap_by_base[base_of(k)].append(k)


def pick(base):
    cands = snap_by_base.get(base)
    if not cands:
        return None
    for x in cands:
        e = snapshots[x]
        if e['dependencies'] or e['optionalDependencies']:
            return x
    return cands[0]


ROOT = '@modelcontextprotocol/sdk@1.30.0'
assert ROOT in snap_by_base, [x for x in snap_by_base if 'modelcontext' in x]

seen = collections.OrderedDict()
stack = [ROOT]
while stack:
    b = stack.pop()
    if b in seen:
        continue
    full = pick(b)
    if full is None:
        seen[b] = None
        continue
    seen[b] = full
    p = snapshots[full]
    for dm in ('dependencies', 'optionalDependencies'):
        for dn, dv in p[dm].items():
            stack.append('%s@%s' % (dn, dv))

rows = []
missing = []
for b, full in seen.items():
    r = {'pkg': name_of(b), 'version': version_of(b)}
    if full is None:
        r['in_lock'] = False
        missing.append(b)
    else:
        pe = packages.get(full) or packages.get(base_of(full)) or {}
        r.update({'in_lock': True, 'lock_key': full,
                  'integrity_sha512_16': (pe.get('integrity') or '')[:16],
                  'dev_flag': pe.get('dev', False),
                  'direct_deps': len(snapshots[full]['dependencies'])})
        if base_of(full) != full:
            r['peer_suffix'] = full[len(base_of(full)):]
    rows.append(r)
rows.sort(key=lambda r: r['pkg'].lower())

print('runtime closure from', ROOT, '=', len(rows), ' unresolved:', len(missing))
for r in rows:
    print('%-40s %-10s %s%s' % (r['pkg'], r['version'],
                                'DEV' if r.get('dev_flag') else '   ',
                                '  <-' + r.get('peer_suffix', '')))
io.open('C:/tmp/npm_mcp_closure.json', 'w', encoding='utf-8').write(
    json.dumps({'root': ROOT, 'closure_count': len(rows), 'unresolved': missing,
                'lockfile_sections': {'packages': len(packages), 'snapshots': len(snapshots)},
                'packages': rows}, ensure_ascii=False, indent=1))
