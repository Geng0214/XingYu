import io
import json
import zipfile
import urllib.request
import urllib.error

PROXY = 'http://127.0.0.1:7897'
op = urllib.request.build_opener(urllib.request.ProxyHandler({'http': PROXY, 'https': PROXY}))
APK = 'C:/M0/A/app/build/outputs/apk/clone/app-clone.apk'

NEEDLES = [
    ('com.itextpdf:itextg:5.5.10', b'Lcom/itextpdf/text/'),
    ('com.itextpdf:itextg:5.5.10 (unanchored)', b'com/itextpdf/text/'),
    ('com.github.junrar:junrar:7.5.5', b'Lcom/github/junrar/'),
    ('org.checkerframework:checker-compat-qual:2.5.3', b'Lorg/checkerframework/'),
    ('ru.noties:jlatexmath-android(+fonts):0.2.0', b'Lru/noties/jlatexmath/'),
    ('com.tom_roush:pdfbox (对照)', b'Lcom/tom_roush/pdfbox/'),
    ('net.lingala.zip4j:zip4j:2.11.5 (对照)', b'Lnet/lingala/zip4j/'),
    ('com.github.topjohnwu.libsu:*:6.0.0', b'Lcom/topjohnwu/'),
    ('com.github.Sable:axml:2.0.0', b'Lpxb/android/'),
    ('com.github.tech-pw:RenderX:1.0.0', b'Llive/pw/render'),
    ('live.pw.renderX.LatexView (manifest 里被 remove 的那个类)', b'Llive/pw/renderX/LatexView;'),
    ('  └ RenderX 的 mathJaxView 子包', b'Llive/pw/renderX/mathJaxView/'),
]

z = zipfile.ZipFile(APK)
dexes = [n for n in z.namelist() if n.endswith('.dex')]
data = [(n, z.read(n)) for n in dexes]
print('apk entries=%d dex files=%d dex bytes=%d' % (
    len(z.namelist()), len(dexes), sum(len(d) for _, d in data)))
res = {}
for label, nd in NEEDLES:
    hits = sum(d.count(nd) for _, d in data)
    files = sorted(n for n, d in data if d.count(nd))
    res[label] = {'needle': nd.decode(), 'hits': hits, 'dex': files}
    print('%-58s %-34s %6d  %s' % (label, nd.decode(), hits, ' '.join(files)))

out = {'dex_needles_anchored': res}
c = None
for u in ['https://api.github.com/rate_limit',
          'https://api.github.com/repos/tech-pw/RenderX/git/trees/1.0.0?recursive=0',
          'https://api.github.com/repos/tech-pw/RenderX/commits?path=LICENSE&per_page=100',
          'https://api.github.com/repos/tech-pw/RenderX/tags?per_page=100',
          'https://api.github.com/repos/Sable/axml/commits?path=LICENSE.txt&per_page=100']:
    try:
        r = op.open(urllib.request.Request(u, headers={'User-Agent': 'licence-audit'}), timeout=60)
        c, b = r.getcode(), r.read()
    except urllib.error.HTTPError as e:
        c, b = e.code, e.read()[:200]
    except Exception as e:
        c, b = None, repr(e).encode()
    key = u.split('github.com/')[1].split('?')[0]
    if c != 200:
        out[key] = {'status': c, 'body': b.decode('utf-8', 'replace')[:180]}
        print('!!', u, c, b[:120])
        continue
    j = json.loads(b)
    if key == 'rate_limit':
        out[key] = j
    elif 'trees' in key:
        out[key] = {'root_entries': sorted(x['path'] for x in j['tree']),
                    'truncated': j.get('truncated'),
                    'licence_named': [x['path'] for x in j['tree']
                                      if 'licen' in x['path'].lower() or 'copying' in x['path'].lower()]}
        print('1.0.0 tree root:', sorted(x['path'] for x in j['tree'] if '/' not in x['path']))
    elif 'commits' in key:
        out[key] = {'commits': len(j),
                    'history': [(x['commit']['author']['date'], x['sha'][:10],
                                 x['commit']['message'].split('\n')[0][:60]) for x in j]}
        print(key, [(x['commit']['author']['date'], x['sha'][:10]) for x in j])
    elif key.endswith('tags'):
        out[key] = [t['name'] for t in j]
        print('RenderX tags:', [t['name'] for t in j])
io.open('C:/tmp/dex_table.json', 'w', encoding='utf-8').write(
    json.dumps(out, ensure_ascii=False, indent=1))
