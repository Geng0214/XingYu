import io
import re
import json
import collections

BASE = 'C:/Users/15942/Desktop/Work/operit/app/src/main/assets/bridge/'
lock = json.load(io.open('C:/tmp/npm_mcp_closure.json', encoding='utf-8'))
closure_names = sorted(r['pkg'] for r in lock['mcp_bridge_sdk'] if r.get('in_lock'))
cset = set(closure_names)

b = io.open(BASE + 'spawn-helper.js', 'rb').read()
print('--- node_modules tokens (spawn-helper.js) ---')
for m in re.finditer(rb'.{60}node_modules.{80}', b, re.S):
    print(repr(m.group(0).decode('utf-8', 'replace')))

print()
print('--- sourceMappingURL basename -> candidate owner (from inner file names) ---')
names = sorted({x.decode() for x in re.findall(rb'//# sourceMappingURL=([^\n\r"]+)', b)})
print(len(names), names)

print()
print('--- is `ws` / `express` / other suspects in the pinned closure? ---')
for q in ('ws', 'express', 'zod', 'ajv', 'ajv-formats', 'cross-spawn', 'jose', 'hono'):
    print('  %-14s in closure: %s' % (q, q in cset))

print()
print('--- whole-lockfile package count vs closure ---')
print('  closure:', len(closure_names))
