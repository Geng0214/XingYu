import io
import json
import re


def parse_tree(path):
    """cargo tree lines look like: '<box chars>name v1.2.3 (location)' with optional '(proc-macro)' / '(*)'."""
    pkgs = {}
    for line in io.open(path, encoding='utf-8'):
        m = re.search(r'([A-Za-z0-9_\-]+) v([0-9][0-9A-Za-z.\-+]*)', line)
        if not m:
            continue
        kind = 'proc-macro' if 'proc-macro' in line else 'lib'
        key = (m.group(1), m.group(2))
        prev = pkgs.get(key)
        if prev is None or (prev == 'lib' and kind == 'proc-macro'):
            pkgs[key] = kind
    return pkgs


allt = parse_tree('C:/tmp/tree_android_all.txt')
norm = parse_tree('C:/tmp/tree_android_normal.txt')
lock = json.load(io.open('C:/tmp/rust_licenses.json', encoding='utf-8'))
locked = set((p['crate'], p['version']) for p in lock['packages'])

print('lock pkgs      ', len(locked))
print('tree all       ', len(allt))
print('tree normal    ', len(norm))
print('in tree-all not in lock:', sorted(set(allt) - locked))
print('in lock not in tree-all:', sorted(locked - set(allt)))
print()
print('--- normal (runtime code for aarch64-linux-android):')
for k in sorted(norm):
    print('   ', k[0], k[1], norm[k])
print()
print('--- resolved but NOT in android runtime set (host build / proc-macro / other-target):')
for k in sorted(set(allt) - set(norm)):
    print('   ', k[0], k[1], allt[k])
for k in sorted(locked - set(norm)):
    if k not in allt:
        print('   ', k[0], k[1], 'lock-only (never resolved for this target)')

json.dump(
    {
        'android_runtime': [{'crate': a, 'version': b, 'kind': norm[(a, b)]} for a, b in sorted(norm)],
        'resolved_not_runtime': [{'crate': a, 'version': b, 'kind': allt[(a, b)]} for a, b in sorted(set(allt) - set(norm))],
        'lock_only_never_resolved': [{'crate': a, 'version': b} for a, b in sorted(locked - set(allt))],
    },
    io.open('C:/tmp/rust_tree_sets.json', 'w', encoding='utf-8'),
    ensure_ascii=False, indent=1)
