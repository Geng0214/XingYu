import io
import re
import collections

R = 'C:/Users/15942/Desktop/Work/operit/'
TS = re.compile(r"'((?:[^'\\\n]|\\.){8,80})'|\"((?:[^\"\\\n]|\\.){8,80})\"|`((?:[^`\\\n]|\\.){8,80})`")


def lits(src):
    out = set()
    for t in TS.findall(src):
        for x in t:
            if x and not x.startswith('http') and '${' not in x:
                out.add(x)
    return out


PAIRS = [('index.ts', 'index.js'), ('spawn-helper.ts', 'spawn-helper.js')]
for ts, js in PAIRS:
    src = io.open(R + 'tools/mcp_bridge/' + ts, encoding='utf-8', errors='replace').read()
    b = io.open(R + 'app/src/main/assets/bridge/' + js, 'rb').read()
    L = sorted(lits(src))
    present = [x for x in L if x.encode() in b]
    missing = [x for x in L if x.encode() not in b]
    print('--- %s -> %s : literals=%d present=%d missing=%d' % (ts, js, len(L), len(present), len(missing)))
    for m in missing:
        print('     MISSING %r' % m[:78])
    # literals inside the bundle that the .ts does not contain (only for the own-code part)
    own = sorted({x.decode('utf-8', 'replace') for x in re.findall(rb'"([ -~]{10,60})"', b)}
                 - {x for x in L})
    print('     bundle-only quoted strings (sample of %d): %s' % (len(own), own[:8]))
