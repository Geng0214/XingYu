import io
import json
import re
import collections

LOCK = 'C:/Users/15942/Desktop/Work/operit/pnpm-lock.yaml'
lines = io.open(LOCK, encoding='utf-8').read().split('\n')
KEY = re.compile(r"^  '?([^':]+@[^':]+?)'?:\s*(?:\{\})?\s*$")
DPP = re.compile(rb'node_modules[/\\]+((?:@[\w.-]+[/\\])?[\w.-]+)')


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
                mm = re.search(r'integrity: (sha\d+-[A-Za-z0-9+/=]+)', s)
                if mm:
                    cur['integrity'] = mm.group(1)
            elif s == 'dev: true':
                cur['dev'] = True
            elif re.match(r'^\w+:$', s):
                mode = s[:-1]
            elif mode in ('dependencies', 'optionalDependencies') and ':' in s:
                kk, vv = s.split(':', 1)
                cur[mode][unq(kk)] = unq(vv)
            j += 1
        i = j
    return out


packages_raw = parse_section('packages')
packages = collections.OrderedDict()
for k, v in packages_raw.items():
    packages.setdefault(base_key(k), v)
snapshots = parse_section('snapshots')
snap_by_base = collections.OrderedDict()
for k in snapshots:
    snap_by_base.setdefault(base_key(k), []).append(k)
print('packages:', len(packages_raw), 'snapshots:', len(snapshots))


def name_of(key):
    k = base_key(key)
    at = k.rfind('@')
    return k[:at] if at > 0 else k


def version_of(key):
    k = base_key(key)
    at = k.rfind('@')
    return k[at + 1:] if at > 0 else k


def pick(base):
    cands = snap_by_base.get(base)
    if not cands:
        return None
    for x in cands:
        e = snapshots[x]
        if e['dependencies'] or e['optionalDependencies']:
            return x
    return cands[0]


def closure(root):
    root = base_key(root)
    assert root in snap_by_base, 'unknown root ' + root
    seen = collections.OrderedDict()
    stack = [root]
    while stack:
        b = base_key(stack.pop())
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
    for b, full in seen.items():
        r = {'pkg': name_of(b), 'version': version_of(b)}
        if full is None:
            r['in_lock'] = False
        else:
            pe = packages.get(b, {})
            r.update({'in_lock': True, 'lock_key': full,
                      'integrity_sha512_16': (pe.get('integrity') or '')[:16],
                      'dev_flag': pe.get('dev', False),
                      'direct_deps': len(snapshots[full]['dependencies'])})
            if full != b:
                r['peer_suffix'] = full[len(b):]
        rows.append(r)
    rows.sort(key=lambda r: (r['pkg'].lower(), r['version']))
    return rows


def bundle_mods(path):
    b = io.open(path, 'rb').read()
    names = collections.Counter()
    for m in DPP.finditer(b):
        nm = m.group(1).decode('utf-8', 'replace').replace('\\', '/')
        names[nm.rstrip('/')] += 1
    return len(b), names


RESULT = {}
for label, root in (('mcp_bridge_sdk', '@modelcontextprotocol/sdk@1.30.0'),
                    ('root_ts', 'typescript@5.9.3')):
    rows = closure(root)
    RESULT[label] = rows
    miss = [r['pkg'] for r in rows if not r.get('in_lock')]
    print('[%s] closure=%d unresolved=%d' % (label, len(rows), len(miss)), miss)

info = {}
for f in ('index.js', 'spawn-helper.js'):
    p = 'C:/Users/15942/Desktop/Work/operit/app/src/main/assets/bridge/' + f
    size, names = bundle_mods(p)
    info[f] = {'bytes': size, 'distinct_node_modules_names': len(names),
               'path_occurrences': sum(names.values()),
               'names': dict(sorted(names.items(), key=lambda kv: (-kv[1], kv[0])))}
    print('[%s] bytes=%d distinct nm paths=%d occurrences=%d'
          % (f, size, len(names), sum(names.values())))
RESULT['bundles'] = info

sdk = {r['pkg'] for r in RESULT['mcp_bridge_sdk']}
for f in ('index.js', 'spawn-helper.js'):
    hit = sorted(x for x in info[f]['names'] if x in sdk)
    miss = sorted(x for x in sdk if x not in info[f]['names'])
    print('%s bundled-from-closure=%d ; in closure but no path token=%d' % (f, len(hit), len(miss)))
    RESULT['bundles'][f]['names_in_sdk_closure'] = hit
    RESULT['bundles'][f]['closure_without_path_token'] = miss

io.open('C:/tmp/npm_mcp_closure.json', 'w', encoding='utf-8').write(
    json.dumps(RESULT, ensure_ascii=False, indent=1))
