import io
import json
import collections

L = json.load(io.open('C:/tmp/npm_licenses.json', encoding='utf-8'))
C = json.load(io.open('C:/tmp/npm_mcp_closure.json', encoding='utf-8'))
lockint = {r['pkg']: (r.get('integrity_sha512_16') or '') for r in C['mcp_bridge_sdk'] if r.get('in_lock')}

rows = L['packages']
print('packages measured:', len(rows))

mismatch_int = []
for r in rows:
    a = (r.get('integrity') or '')[:16]
    b = lockint.get(r['pkg'], '')
    if a and b and a != b:
        mismatch_int.append((r['pkg'], a, b))
print('integrity vs lockfile mismatches:', len(mismatch_int), mismatch_int[:6])
print('present in both:', sum(1 for r in rows if r.get('integrity') and lockint.get(r['pkg'])))

field_vs_text = collections.Counter()
disagree = []
for r in rows:
    f = str(r.get('license_field'))
    cls = '+'.join(sorted({x['text_class'] for x in r.get('licence_files', [])}))
    ok = (f in cls) or (f == 'BSD-3-Clause' and 'BSD-3-Clause' in cls) or \
         (f == 'BSD-2-Clause' and ('BSD-2-or-other' in cls or 'BSD-3-Clause' in cls)) or \
         (f == 'MIT' and 'MIT' in cls) or (f == 'ISC' and 'ISC' in cls)
    field_vs_text[(f, cls if ok else 'TEXT-DISAGREES:' + cls)] += 1
    if not ok:
        disagree.append((r['pkg'], r['version'], f, cls, [x['path'] for x in r['licence_files']]))
print()
print('declared field vs shipped licence text:')
for (f, c), n in field_vs_text.most_common():
    print('   %-16s %-34s %d' % (f, c, n))
print('text disagreements:', len(disagree))
for d in disagree:
    print('   ', d)

uniq = collections.Counter(x['sha256_16'] for r in rows for x in r.get('licence_files', []))
print()
print('distinct licence texts:', len(uniq), 'across', sum(uniq.values()), 'files')
print('most shared texts:', uniq.most_common(6))
big = collections.Counter()
for r in rows:
    for x in r.get('licence_files', []):
        big[(x['text_class'], x['bytes'])] += 1

files_per_pkg = collections.Counter(len(r.get('licence_files', [])) for r in rows)
print('licence files per package:', dict(files_per_pkg))
print('licence file basenames:', dict(collections.Counter(
    x['path'].split('/')[-1] for r in rows for x in r.get('licence_files', []))))
print('copyright holders sample:')
import re
for r in rows[:0]:
    pass
holders = []
for r in rows:
    for x in r.get('licence_files', []):
        holders.append((r['pkg'], x['bytes'], x['text_class']))
print('  total licence files:', len(holders))
import sys
sys.stdout.write('deprecated: %s\n' % [r['pkg'] for r in rows if r.get('deprecated')])
repos = [r.get('repository') for r in rows]
print('no repository field:', [r['pkg'] for r in rows if not r.get('repository')])
json.dump({'measured': len(rows),
           'integrity_match_with_lockfile': sum(1 for r in rows if r.get('integrity') and
                                                lockint.get(r['pkg']) == (r.get('integrity') or '')[:16]),
           'distinct_licence_texts': len(uniq),
           'licence_files_total': sum(uniq.values()),
           'text_field_disagreements': disagree},
          io.open('C:/tmp/npm_licenses_summary.json', 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
