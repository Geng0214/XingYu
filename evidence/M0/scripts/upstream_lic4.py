import io
import os
import re
import json
import glob
import zipfile
import hashlib
import urllib.request
import urllib.error

PROXY = 'http://127.0.0.1:7897'
op = urllib.request.build_opener(urllib.request.ProxyHandler({'http': PROXY, 'https': PROXY}))


def code(u):
    try:
        r = op.open(urllib.request.Request(u, headers={'User-Agent': 'licence-audit'}), timeout=45)
        return r.getcode(), r.read()
    except urllib.error.HTTPError as e:
        return e.code, b''
    except Exception as e:
        return None, repr(e).encode()


print('--- licence metadata declared at the PINNED commit ---')
for u in ('https://raw.githubusercontent.com/Sable/axml/2.0.0/pom.xml',
          'https://raw.githubusercontent.com/tech-pw/RenderX/1.0.0/readme.md',
          'https://raw.githubusercontent.com/tech-pw/RenderX/1.0.0/renderX/build.gradle',
          'https://raw.githubusercontent.com/tech-pw/RenderX/1.0.0/renderX/build.gradle.kts',
          'https://raw.githubusercontent.com/topjohnwu/libsu/6.0.0/README.md'):
    c, b = code(u)
    tag = u.split('.com/')[1]
    if c != 200:
        print('  %-52s %s' % (tag, c))
        continue
    t = b.decode('utf-8', 'replace')
    lic = re.findall(r'(?i)<licen[cs]e>.{0,160}?</licen[cs]e>|licen[cs]e\s*(?:Id|:)?\s*["\']?[\w.+-]+', t)
    print('  %-52s 200 %dB licence-ish matches: %s' % (tag, len(b), (lic[:4] or 'none') if lic else 'none'))
    for kw in ('GPL', 'Apache', 'MIT', 'License', 'license'):
        n = t.count(kw)
        if n:
            print('        %-8s x%d' % (kw, n))
    m = re.findall(r'(?i)(licensed under[^\n]{0,120}|all rights reserved[^\n]{0,60})', t)
    for x in m[:3]:
        print('        text:', re.sub(r'\s+', ' ', x)[:120])

print()
print('--- cached artifacts, inner package prefixes ---')
GR = 'C:/Users/15942/Desktop/Work/.gradle-m0/caches/modules-2/files-2.1'
prefixes = {}
for grp in ('com.github.tech-pw', 'com.github.Sable', 'com.github.topjohnwu.libsu'):
    for p in sorted(glob.glob(os.path.join(GR, grp, '*', '*', '*', '*'))):
        if not p.endswith(('.aar', '.jar')):
            continue
        try:
            z = zipfile.ZipFile(p)
        except Exception as e:
            print('  unreadable', os.path.basename(p), e)
            continue
        nm = os.path.basename(p)
        files = z.namelist()
        if nm.endswith('.aar') and 'classes.jar' in files:
            inner = zipfile.ZipFile(io.BytesIO(z.read('classes.jar')))
            cf = [f for f in inner.namelist() if f.endswith('.class')]
        else:
            cf = [f for f in files if f.endswith('.class')]
        pre = sorted({'/'.join(f.split('/')[:3]) for f in cf if f.count('/') >= 2})
        lic = [f for f in files if re.search(r'licen|copying|notice', f, re.I)]
        prefixes[nm] = {'classes': len(cf), 'top_prefixes': pre[:8], 'licence_files': lic[:8]}
        print('  %-52s classes=%-5d %s lic=%s' % (nm, len(cf), pre[:4], lic[:4]))
json.dump(prefixes, io.open('C:/tmp/jitpack_artifacts.json', 'w', encoding='utf-8'),
          ensure_ascii=False, indent=1)
