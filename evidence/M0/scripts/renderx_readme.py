import io
import re
import json
import hashlib
import urllib.request
import urllib.error

PROXY = 'http://127.0.0.1:7897'
op = urllib.request.build_opener(urllib.request.ProxyHandler({'http': PROXY, 'https': PROXY}))


def get(u):
    try:
        r = op.open(urllib.request.Request(u, headers={'User-Agent': 'Mozilla/5.0 (licence-audit)'}), timeout=60)
        return r.getcode(), r.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()[:200]
    except Exception as e:
        return None, repr(e).encode()


out = {}
for ref in ['1.0.0', 'main']:
    c, b = get('https://raw.githubusercontent.com/tech-pw/RenderX/%s/readme.md' % ref)
    rec = {'status': c}
    if c == 200:
        t = b.decode('utf-8', 'replace')
        rec.update({'bytes': len(b), 'sha256_16': hashlib.sha256(b).hexdigest()[:16],
                    'licence_terms': sorted(set(re.findall(
                        r'(?i)\b(MIT|Apache[- ]?2\.0|GPL[-\d.]*|LGPL[-\d.]*|AGPL[-\d.]*|BSD[-\d.]*|Unlicense)\b', t))),
                    'licence_context': [re.sub(r'\s+', ' ', m.group(0)) for m in re.finditer(
                        r'(?im)^.{0,120}(licen[cs]e|copyright|许可|版权|GPL|MIT).{0,120}$', t)][:14]})
    out['readme_%s' % ref.replace('.', '_')] = rec
    print('readme.md @', ref, c, rec.get('licence_terms'))
    for x in rec.get('licence_context', []):
        print('     |', x[:170])

c, b = get('https://github.com/tech-pw/RenderX/releases/tag/1.0.0')
print('releases/tag/1.0.0 status', c)
if c == 200:
    t = b.decode('utf-8', 'replace')
    out['tag_page'] = {'dates': sorted(set(re.findall(r'datetime="([^"]+)"', t)))[:6],
                       'oids': sorted(set(re.findall(r'[0-9a-f]{40}', t)))[:5]}
    print('tag dates:', out['tag_page']['dates'])

c, b = get('https://github.com/tech-pw/RenderX/commits/main/LICENSE.atom')
if c == 200:
    t = b.decode('utf-8', 'replace')
    out['licence_commit'] = {'titles': [re.sub(r'\s+', ' ', x) for x in re.findall(r'<title>(.*?)</title>', t, re.S)],
                             'updated': re.findall(r'<updated>([^<]+)</updated>', t),
                             'commit_links': sorted(set(re.findall(r'/tech-pw/RenderX/commit/([0-9a-f]{40})', t)))[:5]}
    print('LICENSE commit:', out['licence_commit']['titles'][:2], out['licence_commit']['updated'][:3],
          out['licence_commit']['commit_links'])

# does the tag 1.0.0 tree carry any licence text at all under the module dirs?
for p in ['renderX/LICENSE', 'renderX/license', 'app/LICENSE', 'LICENSE.md', 'renderX/build.gradle.kts']:
    c, b = get('https://raw.githubusercontent.com/tech-pw/RenderX/1.0.0/%s' % p)
    out.setdefault('subprobe', {})[p] = c
    if c == 200 and 'licen' in p.lower():
        print('PRESENT:', p)
print('subprobe:', out['subprobe'])
io.open('C:/tmp/renderx_readme.json', 'w', encoding='utf-8').write(
    json.dumps(out, ensure_ascii=False, indent=1))
