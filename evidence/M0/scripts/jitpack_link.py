import io
import os
import re
import json
import zipfile
import urllib.request

PROXY = 'http://127.0.0.1:7897'
op = urllib.request.build_opener(urllib.request.ProxyHandler({'http': PROXY, 'https': PROXY}))
u = 'https://raw.githubusercontent.com/tech-pw/RenderX/1.0.0/renderX/build.gradle.kts'
b = op.open(urllib.request.Request(u, headers={'User-Agent': 'licence-audit'}), timeout=45).read()
t = b.decode('utf-8', 'replace')
print('renderX/build.gradle.kts at tag 1.0.0:', len(b), 'bytes')
for i, l in enumerate(t.split('\n'), 1):
    if re.search(r'(?i)gpl|licen|author|copyright|group|version', l):
        print('  %3d| %s' % (i, l.strip()[:140]))
u2 = 'https://raw.githubusercontent.com/tech-pw/RenderX/1.0.0/build.gradle.kts'
try:
    b2 = op.open(urllib.request.Request(u2, headers={'User-Agent': 'licence-audit'}), timeout=45).read()
    print('\nroot build.gradle.kts at 1.0.0 (licence/group lines):')
    for i, l in enumerate(b2.decode('utf-8', 'replace').split('\n'), 1):
        if re.search(r'(?i)gpl|licen|groupId|artifactId|publish', l):
            print('  %3d| %s' % (i, l.strip()[:140]))
except Exception as e:
    print('root build script:', e)

print()
print('--- dex presence in the distributed APK ---')
APK = 'C:/M0/A/app/build/outputs/apk/clone/app-clone.apk'
z = zipfile.ZipFile(APK)
dexes = [n for n in z.namelist() if n.endswith('.dex')]
needles = {
    'RenderX  Llive/pw/render (prefix from AAR: live/pw/renderX)': b'llive/pw/render',
    'axml     Lpxb/android/...      (prefix pxb/android)': b'Lpxb/android/',
    'libsu    Lcom/topjohnwu/superuser/': b'Lcom/topjohnwu/superuser/',
    '(control) Lcom/itextpdf/text/': b'Lcom/itextpdf/text/',
    '(control) Lru/noties/jlatexmath/': b'Lru/noties/jlatexmath/',
}
tot = {k: 0 for k in needles}
where = {k: set() for k in needles}
for n in dexes:
    d = z.read(n)
    for k, nd in needles.items():
        c = d.count(nd)
        if c:
            tot[k] += c
            where[k].add(n)
print('dex files scanned:', len(dexes))
for k in needles:
    print('  %-58s hits=%-7d dex=%d %s' % (k, tot[k], len(where[k]), sorted(where[k])[:4]))
