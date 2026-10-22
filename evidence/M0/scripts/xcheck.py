import io
import re
import json

K = 'C:/Users/15942/Desktop/Work/operit/app/src/main/java/com/ai/assistance/operit/ui/features/about/screens/OpenSourceLicenses.kt'
G = 'C:/Users/15942/Desktop/Work/operit/evidence/M0/gradle-dependency-licenses.json'

d = json.load(io.open(G, encoding='utf-8'))
rows = d['rows']
by_coord = {r['coord']: r for r in rows}
print('measured rows:', len(rows))
print('kind counts:', {})
from collections import Counter
print(Counter(r['kind'] for r in rows))
print()
print('=== copyleft / unknown / none measured in the JVM graph ===')
for r in rows:
    if r['kind'] not in ('permissive',):
        print('%-10s %-58s %-38s src=%s' % (r['kind'], r['coord'], (r['licence'] or '')[:38], r['source']))

entries = []
for m in re.finditer(r'OpenSourceLibrary\("([^"]+)",(?:\s*"([^"]*)")?\s*,\s*"([^"]*)"\s*,\s*"([^"]*)"\)',
                     io.open(K, encoding='utf-8').read()):
    entries.append({'name': m.group(1), 'desc': m.group(2), 'lic': m.group(3), 'url': m.group(4),
                    'line': io.open(K, encoding='utf-8').read()[:m.start()].count('\n') + 1})
print()
print('in-app declared entries:', len(entries))
io.open('C:/tmp/inapp_licenses.json', 'w', encoding='utf-8').write(
    json.dumps(entries, ensure_ascii=False, indent=1))
