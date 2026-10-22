import io
import os
import re
import json
import glob
import zipfile

GR = 'C:/Users/15942/Desktop/Work/.gradle-m0/caches/modules-2/files-2.1'
aar = glob.glob(os.path.join(GR, 'com.github.tech-pw', 'RenderX', '*', '*', '*.aar'))[0]
z = zipfile.ZipFile(aar)
print(aar)
print('aar entries:', z.namelist())
inner = zipfile.ZipFile(io.BytesIO(z.read('classes.jar')))
cls = [f for f in inner.namelist() if f.endswith('.class')]
print('classes (%d):' % len(cls), cls[:12])

# distinctive strings from a couple of class files
needles = set()
for f in cls[:6]:
    b = inner.read(f)
    for s in re.findall(rb'[\x20-\x7e]{10,40}', b):
        t = s.decode()
        if not re.match(r'^[\w/$;.()\[\]-]+$', t) or ' ' in t:
            needles.add(t)
needles = sorted(needles)[:40]
print('\ncandidate string fragments from class constant pools (first 12):', needles[:12])

APK = 'C:/M0/A/app/build/outputs/apk/clone/app-clone.apk'
az = zipfile.ZipFile(APK)
dexes = [n for n in az.namelist() if n.endswith('.dex')]
datab = {n: az.read(n) for n in dexes}

print('\n--- APK entries matching RenderX payload ---')
for pat in ('MathJax', 'mathjax', 'renderX', 'renderx'):
    hit = [n for n in az.namelist() if pat.lower() in n.lower()]
    print('  name contains %-9s : %d  %s' % (pat, len(hit), hit[:6]))

print('\n--- descriptor prefixes, both needle shapes ---')
for lbl, nd in [(b'Llive/pw/render', 'with L'), (b'llive/pw/render', 'bare'),
                (b'live/pw/renderX', 'no leading l'),
                (b'Lcom/itextpdf/text', 'with L'), (b'com/itextpdf/text', 'bare'),
                (b'Lpxb/android', 'with L'), (b'pxb/android', 'bare'),
                (b'Lcom/topjohnwu/superuser', 'with L'), (b'com/topjohnwu/superuser', 'bare')]:
    tot = sum(d.count(nd) for d in datab.values())
    print('  %-26s %-10s %d' % (lbl.decode(), nd.decode()[:26], tot))

print('\n--- RenderX class-name strings lifted from the AAR, searched in dex ---')
for f in cls[:8]:
    nm = f[:-6].replace('/', '.')
    d0 = f[:-6]
    for probe in (d0.encode(), nm.encode()):
        c = sum(d.count(probe) for d in datab.values())
        if c:
            print('  %-46s hits=%d' % (f, c))
            break
    else:
        print('  %-46s hits=0' % f)
