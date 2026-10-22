import io
import re
import collections

BASE = 'C:/Users/15942/Desktop/Work/operit/app/src/main/assets/bridge/'
for f in ['index.js', 'spawn-helper.js']:
    b = io.open(BASE + f, 'rb').read()
    sm = collections.Counter(x.decode() for x in re.findall(rb'//# sourceMappingURL=([^\n\r"]{0,80})', b))
    print('---', f, len(b))
    print('  sourceMappingURL targets:', dict(sm))
    print('  webpack runtime signatures:')
    for pat in [rb'__webpack_require__', rb'__unused_webpack_module', rb'\.exports\s*=',
                rb'Object\.defineProperty\(exports', rb'node_modules', rb'licenses/webpack']:
        print('    %-38s %d' % (pat.decode(), len(re.findall(pat, b))))
    ids = re.findall(rb'(?m)^\s{0,8}(\d{1,4}):?\s*(?:/\*\s*\d+\s*\*/)?\s*(?:\(|function)', b)
    print('  numeric module-id-like starts:', len(ids))
    print('  "/*! banners:', b.count(b'/*!'))
