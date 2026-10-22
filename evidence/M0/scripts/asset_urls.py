import io
import os
import re
import json
import collections

R = 'C:/Users/15942/Desktop/Work/operit/app/src/main/assets'
URL = re.compile(rb'(?:src|href)\s*=\s*["\']((?:https?:)?//[^"\']{4,200})["\']', re.I)
FETCH = re.compile(rb'(?:"|\'|`)((?:https?:)//[^"\'`]{6,200})(?:"|\'|`)', re.I)

SKIP_EXT = ('.png', '.jpg', '.jpeg', '.webp', '.gif', '.so', '.ttf', '.woff', '.woff2', '.zip',
            '.bin', '.tflite', '.onnx', '.mp3', '.wav', '.ico', '.model', '.gguf')

rows = []
tot = 0
for dp, dn, fn in os.walk(R):
    for f in fn:
        p = os.path.join(dp, f)
        if f.lower().endswith(SKIP_EXT):
            continue
        b = io.open(p, 'rb').read()
        if len(b) > 4_000_000:
            continue
        hits = set(x.decode('utf-8', 'replace') for x in URL.findall(b))
        if not hits:
            continue
        tot += len(b)
        rows.append({'file': p.replace(R, 'assets'), 'bytes': len(b),
                     'tag_attr_urls': sorted(hits)})
rows.sort(key=lambda r: r['file'])
print('asset files with remote <script>/<link> URLs:', len(rows))
for r in rows:
    print('  %s (%d B)' % (r['file'], r['bytes']))
    for u in r['tag_attr_urls']:
        print('      ', u)

js = collections.Counter()
for dp, dn, fn in os.walk(R):
    for f in fn:
        if not f.lower().endswith(('.js', '.html', '.htm', '.json', '.mjs')):
            continue
        b = io.open(os.path.join(dp, f), 'rb').read()
        for m in FETCH.finditer(b):
            js[m.group(1).decode('utf-8', 'replace')] += 1
print()
print('all cdn-ish URL literals in JS/HTML/JSON assets (any context):')
for u, c in js.most_common(30):
    print('   %-3d %s' % (c, u[:120]))
json.dump({'tag_attr': rows, 'url_literals': dict(js)},
          io.open('C:/tmp/asset_remote_urls.json', 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
