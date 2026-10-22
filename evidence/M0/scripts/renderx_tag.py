import io
import re
import json
import urllib.request
import urllib.error

PROXY = 'http://127.0.0.1:7897'
op = urllib.request.build_opener(urllib.request.ProxyHandler({'http': PROXY, 'https': PROXY}))


def get(u, html=False):
    h = {'User-Agent': 'Mozilla/5.0 (licence-audit)'}
    if not html:
        h['Accept'] = 'text/plain'
    try:
        r = op.open(urllib.request.Request(u, headers=h), timeout=60)
        return r.getcode(), r.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()[:200]
    except Exception as e:
        return None, repr(e).encode()


out = {}
# 1. raw probes at the pinned tag: any README / licence spelling
cands = ['README.md', 'README.MD', 'Readme.md', 'readme.md', 'README.rst', 'README.txt',
         'docs/README.md', 'LICENSE', 'LICENSE.md', 'LICENSE.txt', 'LICENCE', 'LICENCE.md',
         'COPYING', 'COPYING.md', 'UNLICENSE', 'NOTICE', 'LICENSE-APACHE', 'LICENSE-MIT',
         'app/src/main/AndroidManifest.xml', 'build.gradle', 'gradle.properties']
inv = {}
for nm in cands:
    c, b = get('https://raw.githubusercontent.com/tech-pw/RenderX/1.0.0/%s' % nm)
    inv[nm] = {'status': c, 'bytes': len(b) if c == 200 else None,
               'sha16': __import__('hashlib').sha256(b).hexdigest()[:16] if c == 200 else None}
out['tag_1_0_0_raw_probes'] = inv
print('at tag 1.0.0, present files:', [k for k, v in inv.items() if v['status'] == 200])

# 2. GitHub HTML tree listing for the tag (not the rate-limited API)
c, b = get('https://github.com/tech-pw/RenderX/tree/refs/tags/1.0.0', html=True)
out['tree_html'] = {'status': c}
if c == 200:
    t = b.decode('utf-8', 'replace')
    names = sorted(set(re.findall(r'"name":"([^"]{1,60})","path":"([^"]{1,80})","contentType":"(file|directory)"', t)))
    seen = set()
    listing = [{'name': n, 'path': p, 'type': ty} for n, p, ty in names if not (n in seen or seen.add(n))]
    out['tree_html']['entries'] = listing
    print('tag 1.0.0 root listing:', [(x['name'], x['type']) for x in listing][:40])
    blob = re.findall(r'"refInfo"\s*:\s*\{(.{0,200}?)\}', t)
    out['tree_html']['refinfo'] = blob[:3]

# 3. tags page
c, b = get('https://github.com/tech-pw/RenderX/tags', html=True)
out['tags_html'] = {'status': c}
if c == 200:
    t = b.decode('utf-8', 'replace')
    out['tags_html']['tags'] = sorted(set(re.findall(r'/tech-pw/RenderX/releases/tag/([^"/?]+)', t)))
    out['tags_html']['commit_dates'] = sorted(set(re.findall(
        r'"commit":\{"oid":"[0-9a-f]+","date":"([^"]+)"', t)))[:8]
    print('tags seen on /tags:', out['tags_html']['tags'])

# 4. commits touching LICENSE, via HTML atom feed (no API quota)
for path in ['LICENSE', 'README.md']:
    c, b = get('https://github.com/tech-pw/RenderX/commits/main/%s.atom' % path, html=True)
    rec = {'status': c}
    if c == 200:
        t = b.decode('utf-8', 'replace')
        rec['entries'] = [(m.group(2), m.group(1)) for m in re.finditer(
            r'<title>\s*(.*?)\s*</title>\s*<link[^>]*alt="revision"[^>]*/>\s*<updated>([^<]+)</updated>', t)]
        rec['updated'] = re.findall(r'<updated>([^<]+)</updated>', t)
        rec['title'] = re.findall(r'<entry>.*?<title>\s*([^<]*?)\s*</title>', t, re.S)[:5]
    out['commits_atom_%s' % path] = rec
    print(path, 'atom', c, rec.get('updated', [])[:4])

# 5. current README on main, and its licence section
c, b = get('https://raw.githubusercontent.com/tech-pw/RenderX/main/README.md')
if c == 200:
    t = b.decode('utf-8', 'replace')
    out['main_readme'] = {'bytes': len(t), 'sha16': __import__('hashlib').sha256(b).hexdigest()[:16],
                          'licence_terms': sorted(set(re.findall(
                              r'(?i)\b(MIT|Apache[- ]?2\.0|GPL[-\d.]*|LGPL[-\d.]*|BSD[-\d.]*|AGPL[-\d.]*)\b', t))),
                          'context': [re.sub(r'\s+', ' ', m.group(0)) for m in re.finditer(
                              r'(?i).{0,100}(licen[cs]e|copyright|许可|版权).{0,100}', t)][:10]}
    print('main README licence terms:', out['main_readme']['licence_terms'])
io.open('C:/tmp/renderx_tag.json', 'w', encoding='utf-8').write(
    json.dumps(out, ensure_ascii=False, indent=1))
