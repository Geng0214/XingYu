import io
import re
import json
import zipfile
import glob
import os

K = 'C:/Users/15942/Desktop/Work/operit/app/src/main/java/com/ai/assistance/operit/ui/features/about/screens/OpenSourceLicenses.kt'
G = 'C:/Users/15942/Desktop/Work/operit/evidence/M0/gradle-dependency-licenses.json'
GR = 'C:/Users/15942/Desktop/Work/.gradle-m0/caches/modules-2/files-2.1'

src = io.open(K, encoding='utf-8').read()
entries = []
for m in re.finditer(r'OpenSourceLibrary\(\s*"([^"]+)",\s*"([^"]*)",\s*"([^"]*)",\s*"([^"]*)"\s*\)', src):
    entries.append({'name': m.group(1), 'lic': m.group(3), 'url': m.group(4),
                    'line': src[:m.start()].count('\n') + 1})
rows = json.load(io.open(G, encoding='utf-8'))['rows']

STOP = {'library', 'lib', 'for', 'and', 'the', 'android', 'java', 'kotlin', 'apache', 'tools',
        'support', 'kit', 'sdk', 'a', 'of', 'in'}


def toks(s):
    s = re.sub(r'(?<=[a-z0-9])(?=[A-Z])', ' ', s.lower())
    return {t for t in re.split(r'[^a-z0-9]+', s) if len(t) > 2 and t not in STOP}


report = []
for e in entries:
    et = toks(e['name'])
    hits = []
    for r in rows:
        art = r['coord'].split(':')[1]
        rt = toks(art) | toks(r['coord'].split(':')[0])
        if et & rt:
            hits.append(r)
    report.append({'entry': e, 'matched': [{'coord': h['coord'], 'lic': h['licence'], 'kind': h['kind'],
                                            'source': h['source']} for h in hits]})

def norm(s):
    s = s.lower()
    s = re.sub(r'the |license|licence|version|-only|\+', '', s)
    s = s.replace('software', '').replace('general public', 'gpl').replace('public, ', 'public ')
    return re.sub(r'[\s.,/()-]+', '', s)


print('%-28s %-34s %s' % ('IN-APP NAME', 'IN-APP LICENCE', 'MEASURED FROM POM/ARTIFACT'))
conflict, nomatch = [], []
for x in report:
    e, h = x['entry'], x['matched']
    if not h:
        nomatch.append(e['name'])
        continue
    meas = sorted({'%s [%s/%s]' % ((c['lic'] or '(none)').strip(), c['kind'], c['source'])
                   for c in h})
    same = all(norm(e['lic']) in norm(c['lic']) or norm(c['lic']) in norm(e['lic'])
               for c in h if c['lic'])
    print('%-28s %-34s %s %s' % (e['name'], e['lic'], 'OK ' if same else 'XX ', '; '.join(meas))[:200])


def norm(s):
    s = s.lower()
    s = re.sub(r'the |license|licence|version|-only|\+', '', s)
    s = s.replace('software', '').replace('general public', 'gpl').replace('public, ', 'public ')
    return re.sub(r'[\s.,/()-]+', '', s)


print()
print('=== in-app entries with NO coordinate matched in the 396-row JVM graph (%d) ===' % len(nomatch))
print(', '.join(nomatch))
io.open('C:/tmp/xcheck_report.json', 'w', encoding='utf-8').write(
    json.dumps(report, ensure_ascii=False, indent=1))
