import io
import re
import json
import zipfile
import hashlib
import glob
import urllib.request
import urllib.error

PROXY = 'http://127.0.0.1:7897'
op = urllib.request.build_opener(urllib.request.ProxyHandler({'http': PROXY, 'https': PROXY}))
GR = 'C:/Users/15942/Desktop/Work/.gradle-m0/caches/modules-2/files-2.1'


def fetch(u):
    try:
        r = op.open(urllib.request.Request(u, headers={'User-Agent': 'licence-audit',
                                                       'Accept': 'application/vnd.github+json'}), timeout=60)
        return r.getcode(), r.read()
    except urllib.error.HTTPError as e:
        return e.code, e.read()[:300]
    except Exception as e:
        return None, repr(e).encode()


out = {}
for repo in ['topjohnwu/libsu', 'Sable/axml', 'tech-pw/RenderX']:
    rec = {}
    c, b = fetch('https://api.github.com/repos/%s' % repo)
    if c == 200:
        j = json.loads(b)
        rec['repo_api'] = {'default_branch': j.get('default_branch'),
                           'spdx': (j.get('license') or {}).get('spdx_id'),
                           'archived': j.get('archived'), 'pushed_at': j.get('pushed_at'),
                           'homepage': j.get('homepage'), 'description': j.get('description')}
    else:
        rec['repo_api'] = {'status': c, 'body': b.decode('utf-8', 'replace')[:200]}
    c, b = fetch('https://api.github.com/repos/%s/tags?per_page=100' % repo)
    rec['tags'] = [t['name'] for t in json.loads(b)][:30] if c == 200 else {'status': c}
    for path in ['LICENSE', 'LICENSE.txt', 'LICENSE.md', 'README.md', 'README-CN.md']:
        c1, b1 = fetch('https://api.github.com/repos/%s/commits?path=%s&per_page=100' % (repo, path))
        if c1 == 200:
            js = json.loads(b1)
            rec.setdefault('commit_history', {})[path] = {
                'commits': len(js),
                'oldest': (js[-1]['commit']['author']['date'], js[-1]['sha'][:10]) if js else None,
                'newest': (js[0]['commit']['author']['date'], js[0]['sha'][:10]) if js else None}
        else:
            rec.setdefault('commit_history', {})[path] = {'status': c1}
    out[repo] = rec

# RenderX README at the pinned tag: does it claim a licence?
c, b = fetch('https://raw.githubusercontent.com/tech-pw/RenderX/1.0.0/README.md')
rx = {'readme_1_0_0_status': c, 'readme_1_0_0_bytes': len(b) if c == 200 else None}
if c == 200:
    t = b.decode('utf-8', 'replace')
    rx['licence_words_in_readme_1_0_0'] = sorted(set(re.findall(
        r'(?i)\b(MIT|Apache[- ]?2\.0|GPL[- \d.]*|LGPL[- \d.]*|BSD[-\d.]*|CC[- \w.]+|Mozilla Public[\w \d.-]*)\b', t)))
    rx['licence_context'] = [m.group(0).strip() for m in re.finditer(
        r'(?i).{0,90}(licen[cs]e|copyright|版权|授权|许可).{0,90}', t)][:12]
    rx['sha256_16'] = hashlib.sha256(b).hexdigest()[:16]
c, b = fetch('https://raw.githubusercontent.com/tech-pw/RenderX/main/README.md')
if c == 200:
    t = b.decode('utf-8', 'replace')
    rx['readme_main_sha256_16'] = hashlib.sha256(b).hexdigest()[:16]
    rx['readme_main_licence_words'] = sorted(set(re.findall(
        r'(?i)\b(MIT|Apache[- ]?2\.0|GPL[- \d.]*|LGPL[- \d.]*|BSD[-\d.]*|CC[- \w.]+|Mozilla Public[\w \d.-]*)\b', t)))
out['_renderx_readme_probe'] = rx

# the cached AAR: full class list + every non-class entry, to test the hardcoded claims
p = sorted(glob.glob(GR + '/com.github.tech-pw/RenderX/*/*/*.aar'))[0]
z = zipfile.ZipFile(p)
inner = zipfile.ZipFile(io.BytesIO(z.read('classes.jar')))
classes = sorted(f for f in inner.namelist() if f.endswith('.class'))
entries = z.namelist()
out['_renderx_aar'] = {
    'aar': p.rsplit('/', 1)[-1], 'entry_count': len(entries), 'class_count': len(classes),
    'classes': classes,
    'has_LatexView_class': any('LatexView' in c for c in classes),
    'non_class_entries_sample': [e for e in entries if not e.endswith(('.class',))][:10],
    'assets_count': sum(1 for e in entries if e.startswith('assets/')),
    'res_font_count': sum(1 for e in entries if e.startswith('res/font')),
    'licence_named_entries': [e for e in entries if re.search(r'licen|copying|notice', e, re.I)],
}
io.open('C:/tmp/upstream_extra.json', 'w', encoding='utf-8').write(
    json.dumps(out, ensure_ascii=False, indent=1))
print(json.dumps(out, ensure_ascii=False)[:1500])
