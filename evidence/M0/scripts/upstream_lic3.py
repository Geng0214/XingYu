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


print('--- tag refs ---')
for repo, ref in [('Sable/axml', '2.0.0'), ('tech-pw/RenderX', '1.0.0')]:
    for nm in ['LICENSE', 'LICENSE.txt', 'LICENSE.md', 'COPYING', 'README.md']:
        c, b = code('https://raw.githubusercontent.com/%s/%s/%s' % (repo, ref, nm))
        print('  %-20s %-12s %-11s %s' % (repo, ref, nm,
                                          '%d %dB sha=%s' % (c, len(b), hashlib.sha256(b).hexdigest()[:16])
                                          if c == 200 else c))
    c, b = code('https://api.github.com/repos/%s/contents?ref=%s' % (repo, ref))
    if c == 200:
        names = [x['name'] for x in json.loads(b)]
        print('   root listing at tag %s: %s' % (ref, [n for n in names if 'licen' in n.lower() or 'copy' in n.lower()] or names[:12]))
    else:
        print('   contents api:', c)

print()
print('--- where are these declared in the build? ---')
R = 'C:/Users/15942/Desktop/Work/operit/'
for pat in ('tech-pw', 'RenderX', 'Sable', 'axml', 'topjohnwu', 'libsu'):
    for p in glob.glob(R + '**/*.gradle.kts', recursive=True) + glob.glob(R + '**/*.toml', recursive=True):
        s = io.open(p, encoding='utf-8', errors='replace').read()
        for i, l in enumerate(s.split('\n'), 1):
            if pat in l:
                print('  %-10s %s:%d  %s' % (pat, p.replace(R, ''), i, l.strip()[:110]))

print()
print('--- cached artifacts + package prefixes ---')
GR = 'C:/Users/15942/Desktop/Work/.gradle-m0/caches/modules-2/files-2.1'
for grp in ('com.github.tech-pw', 'com.github.Sable', 'com.github.topjohnwu.libsu'):
    for p in sorted(glob.glob(os.path.join(GR, grp, '*', '*', '*'))):
        if not p.endswith(('.aar', '.jar')):
            continue
        try:
            z = zipfile.ZipFile(p)
        except Exception as e:
            print('  unreadable', p, e)
            continue
        nm = os.path.basename(p)
        prefixes = set()
        files = z.namelist()
        if nm.endswith('.aar') and 'classes.jar' in files:
            inner = zipfile.ZipFile(io.BytesIO(z.read('classes.jar')))
            cf = inner.namelist()
        else:
            cf = [f for f in files if f.endswith('.class')]
        for f in cf:
            if f.endswith('.class') and '/' in f:
                prefixes.add(f.split('/')[0] + '/' + (f.split('/')[1] if f.count('/') > 1 else ''))
        lic = [f for f in files if re.search(r'licen|copying', f, re.I)]
        print('  %-46s entries=%d class-pkg-prefixes=%s licence-ish=%s'
              % (nm, len(files), sorted(prefixes)[:6], lic[:6]))
