import io
import json
import collections

BASE = 'C:/Users/15942/Desktop/Work/operit/app/src/main/assets/bridge/'

PROBES = collections.OrderedDict([
    ('ajv@8.20.0', [b'raw.githubusercontent.com/ajv-validator/ajv']),
    ('ajv-formats@3.0.1', [b'formatMinimum', b'formatMaximum']),
    ('cross-spawn@7.0.6', [b'isCmdShimRegExp', b'detectShebang', b'parseCmd']),
    ('shebang-command@2.0.0', [b'shebangCommand', b'/shebang-regex/i']),
    ('shebang-regex@3.0.0', [b'shebangRegex']),
    ('which@2.0.2', [b'pathExt', b'nothrow']),
    ('zod@3.25.76', [b'ZodError', b'invalid_type']),
    ('zod-to-json-schema@3.25.2', [b'zodToJsonSchema', b'zod-first-party-guard']),
    ('jose@6.2.5', [b'ERR_JWS_INVALID', b'ERR_JWE_INVALID']),
    ('hono@4.12.32', [b'HTTPException', b'hono/']),
    ('@hono/node-server@2.0.12', [b'getRequestListener']),
    ('express@5.2.1', [b'X-Powered-By', b'x-powered-by']),
    ('express-rate-limit@8.6.1', [b'X-RateLimit-Limit', b'Too many requests']),
    ('router@2.2.0', [b'Router: route']),
    ('path-to-regexp@8.4.2', [b'Missing parameter name']),
    ('qs@6.15.3', [b'strictNullHandling', b'allowPrototypes']),
    ('raw-body@3.0.2', [b'entity.too large', b'request entity too large']),
    ('iconv-lite@0.7.3', [b'windows-1252', b'utf16-le']),
    ('body-parser@2.3.0', [b'cannot-unparsable-utf8']),
    ('cookie@0.7.2', [b'cookies.parse', b'universal decode']),
    ('negotiator@1.0.0', [b'Accept-Language']),
    ('eventsource@3.0.7', [b'lastEventId', b'retryAfter']),
    ('eventsource-parser@3.1.0', [b'retry:', b'lastEventID']),
    ('pkce-challenge@5.0.1', [b'code_verifier', b'code_challenge_method']),
    ('@modelcontextprotocol/sdk@1.30.0', [b'notifications/initialized', b'MethodNotFound',
                                          b'McpError']),
    ('json-schema-typed@8.0.2', [b'Draft_2020_12', b'json-schema.org/draft/2020-12/schema']),
])

NEGATIVE = collections.OrderedDict([
    ('ws (NOT in the pinned 1.30.0 closure)', [b'Sec-WebSocket-Accept', b'WebSocketServer',
                                               b'permessage-deflate']),
    ('react (web-chat only, not shipped)', [b'useState', b'React.createElement']),
    ('echarts (loaded from CDN, not shipped)', [b'echarts.init', b'zrender']),
])

out = {}
for f in ('index.js', 'spawn-helper.js'):
    b = io.open(BASE + f, 'rb').read()
    print('---', f, len(b), 'bytes')
    pos = []
    for pkg, needles in PROBES.items():
        hit = [n.decode('utf-8', 'replace') for n in needles if n in b]
        if hit:
            pos.append({'package': pkg, 'literals': hit})
            print('   HIT  %-34s %s' % (pkg, hit))
    neg = []
    for label, needles in NEGATIVE.items():
        hit = [n.decode('utf-8', 'replace') for n in needles if n in b]
        neg.append({'probe': label, 'literals_present': hit})
        print('   neg  %-44s %s' % (label, hit or 'absent'))
    print('   positive attributions:', len(pos), 'of', len(PROBES))
    out[f] = {'bytes': len(b), 'positive': pos, 'negative': neg,
              'unattributed_probes': [p for p in PROBES
                                      if p not in {x['package'] for x in pos}]}
io.open('C:/tmp/npm_attribution.json', 'w', encoding='utf-8').write(
    json.dumps(out, ensure_ascii=False, indent=1))
