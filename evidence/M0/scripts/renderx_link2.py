import io
import os
import re
import glob
import zipfile

APK = 'C:/M0/A/app/build/outputs/apk/clone/app-clone.apk'
z = zipfile.ZipFile(APK)
dexes = [n for n in z.namelist() if n.endswith('.dex')]
datab = [z.read(n) for n in dexes]

PROBES = [b'MathView', b'renderX', b'RenderX', b'BrutalismFontFamily', b'CoilImageGetter',
          b'live/pw', b'live/pw/render', b'Llive/pw/render',
          b'MathJax', b'commonhtml', b'file:///android_asset']
for p in PROBES:
    tot = sum(d.count(p) for d in datab)
    where = [n for n, d in zip(dexes, datab) if d.count(p)]
    print('%-26s hits=%-7d dex=%d %s' % (p.decode(), tot, len(where), where[:5]))

print()
print('--- does the APK carry RenderX resources (fonts/mipmap) beyond MathJax? ---')
names = z.namelist()
for pat in ('assets/MathJax', 'res/font', 'gilroy', 'balsamiq', 'poppins'):
    hit = [n for n in names if pat in n.lower() or pat in n]
    print('  %-16s %d %s' % (pat, len(hit), hit[:5]))

print()
print('--- repo-side sources of these payload names ---')
R = 'C:/Users/15942/Desktop/Work/operit/'
for pat in ('MathJax', 'gilroy', 'balsamiqsans'):
    found = []
    for dp, dn, fn in os.walk(R):
        if any(x in dp for x in ('.git', 'build/', '\\build', '.cxx', 'node_modules')):
            continue
        for f in fn:
            if pat.lower() in f.lower():
                found.append(os.path.join(dp, f).replace(R, ''))
    print('  %-13s files in repo: %d %s' % (pat, len(found), found[:6]))

print()
print('--- where RenderX is used in Kotlin (any reference at all?) ---')
import subprocess
for kw in ('renderX', 'RenderX', 'MathView', 'live.pw'):
    out = subprocess.run(['grep', '-rn', '--include=*.kt', '--include=*.kts', '--include=*.toml',
                          kw, R], capture_output=True, text=True)
    lines = [l for l in out.stdout.split('\n') if l and 'evidence/M0' not in l]
    print('  %-10s refs=%d' % (kw, len(lines)))
    for l in lines[:6]:
        print('      ', l.replace(R, '')[:150])
