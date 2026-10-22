import io
import re
import json
import collections

BASE = 'C:/Users/15942/Desktop/Work/operit/app/src/main/assets/bridge/'
FILES = ['index.js', 'spawn-helper.js']

# pinned versions from the lockfile closure (name -> version)
lock = json.load(io.open('C:/tmp/npm_mcp_closure.json', encoding='utf-8'))
pairs = [(r['pkg'], r['version']) for r in lock['mcp_bridge_sdk'] if r.get('in_lock')]
vc = collections.Counter(v for _, v in pairs)
pairs = [(n, v) for n, v in pairs if vc[v] == 1]

out = {}
for f in FILES:
    b = io.open(BASE + f, 'rb').read()
    mods = len(re.findall(rb'/\*{3,}\s', b))
    strict = b.count(b'"use strict"')
    hits = collections.OrderedDict()
    for nm, ver in pairs:
        # version string literal as it would be embedded by the package itself
        c = b.count(('"%s"' % ver).encode())
        if c:
            hits[nm + '@' + ver] = c
    out[f] = {
        'bytes': len(b),
        'webpack_module_banners': mods,
        'use_strict_count': strict,
        'copyright_banners': len(re.findall(rb'Copyright', b, re.I)),
        'license_word': len(re.findall(rb'\bMIT\b', b)),
        'pinned_version_string_literals': hits,
        'source_map_ref': b.count(b'//# sourceMappingURL'),
        'node_modules_path_tokens': len(re.findall(rb'node_modules[/\\]', b)),
    }
    print(f, json.dumps({k: v for k, v in out[f].items() if k != 'pinned_version_string_literals'},
                        ensure_ascii=False))
    print('   version literals:', dict(list(hits.items())[:12]), '… total distinct', len(hits))

io.open('C:/tmp/npm_bundle_probe.json', 'w', encoding='utf-8').write(
    json.dumps(out, ensure_ascii=False, indent=1))
