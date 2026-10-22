import io
import os
import re
import json
import hashlib
import urllib.request
import urllib.error

PROXY = 'http://127.0.0.1:7897'
op = urllib.request.build_opener(urllib.request.ProxyHandler({'http': PROXY, 'https': PROXY}))


def head(u, tries=2):
    for _ in range(tries):
        try:
            r = op.open(urllib.request.Request(u, headers={'User-Agent': 'licence-audit'}), timeout=45)
            return r.getcode(), r.read()
        except urllib.error.HTTPError as e:
            return e.code, b''
        except Exception as e:
            last = e
    return None, repr(last).encode()


CAND = {
 'com.github.topjohnwu.libsu:core/nio/service:6.0.0': ('Topjohnwu/LibSU', ['6.0.0', 'master']),
 'com.github.Sable:axml:2.0.0': ('Sable/axml', ['2.0.0', 'master']),
 'com.github.tech-pw:RenderX:1.0.0': ('tech-pw/RenderX', ['1.0.0', 'master']),
}
NAMES = ['LICENSE', 'LICENSE.md', 'LICENSE.txt', 'COPYING', 'LICENCE', 'license', 'LICENSE-APACHE']

out = {}
for coord, (repo, refs) in CAND.items():
    st, blob = head('https://api.github.com/repos/%s' % repo)
    rec = {'repo': repo, 'api_status': st}
    if st == 200:
        j = json.loads(blob)
        rec['full_name'] = j.get('full_name')
        rec['default_branch'] = j.get('default_branch')
        rec['api_license_field'] = (j.get('license') or {}).get('spdx_id')
        rec['html_url'] = j.get('html_url')
        rec['pushed_at'] = j.get('pushed_at')
    else:
        rec['raw_error'] = blob[:120].decode('utf-8', 'replace')
    found = []
    for ref in list({rec.get('default_branch'), *refs} - {None}):
        for nm in NAMES:
            u = 'https://raw.githubusercontent.com/%s/%s/%s' % (repo, ref, nm)
            c, b = head(u)
            if c == 200 and b:
                found.append({'ref': ref, 'file': nm, 'bytes': len(b),
                              'sha256_16': hashlib.sha256(b).hexdigest()[:16],
                              'head': re.sub(r'\s+', ' ', b[:220].decode('utf-8', 'replace'))})
        if found:
            break
    rec['licence_files_fetched'] = found
    tags, tb = head('https://api.github.com/repos/%s/tags' % repo)
    rec['tags_status'] = tags
    if tb[:1] == b'[':
        rec['tags'] = [x['name'] for x in json.loads(tb)][:12]
    out[coord] = rec
    print('===', coord)
    for k, v in rec.items():
        print('   %-24s %s' % (k, str(v)[:300]))

json.dump(out, io.open('C:/tmp/upstream_lic.json', 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
