import io
import os
import re
import json
import glob
import zlib
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


TARGETS = [('topjohnwu/libsu', '6.0.0'), ('topjohnwu/libsu', 'master'),
           ('Sable/axml', '2.0.0'), ('Sable/axml', 'master'),
           ('tech-pw/RenderX', '1.0.0'), ('tech-pw/RenderX', 'main'),
           ('tech-pw/RenderX', 'master')]
NAMES = ['LICENSE', 'LICENSE.txt', 'LICENSE.md', 'COPYING']
res = {}
for repo, ref in TARGETS:
    for nm in NAMES:
        c, b = head('https://raw.githubusercontent.com/%s/%s/%s' % (repo, ref, nm))
        if c == 200 and b:
            t = re.sub(r'\s+', ' ', b.decode('utf-8', 'replace'))
            res['%s@%s/%s' % (repo, ref, nm)] = {
                'bytes': len(b), 'sha256_16': hashlib.sha256(b).hexdigest()[:16],
                'gpl3': 'GNU GENERAL PUBLIC LICENSE Version 3' in t,
                'lgpl3': 'GNU LESSER GENERAL PUBLIC LICENSE' in t,
                'apache2': 'Apache License Version 2.0' in t,
                'exception_clause': bool(re.search(r'linking with this library|GNU Classpath|LGPL Linking|'
                                                   r'As a special exception', t, re.I)),
                'first_160': t[:160]}
            break
print(json.dumps(res, ensure_ascii=False, indent=1)[:2600])

# RenderX package prefix located separately (see npm-style zipfile probe)
json.dump(res, io.open('C:/tmp/upstream_lic_refs.json', 'w', encoding='utf-8'),
          ensure_ascii=False, indent=1)

