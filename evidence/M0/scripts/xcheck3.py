import io
import json

rows = json.load(io.open('C:/Users/15942/Desktop/Work/operit/evidence/M0/gradle-dependency-licenses.json',
                         encoding='utf-8'))['rows']
by = {}
for r in rows:
    by.setdefault(r['coord'], r)

MAP = {
    'iText (v5)': [('com.itextpdf:itextg', 'MPL/LGPL')],
    'junrar': [('com.github.junrar:junrar', 'The Unlicense')],
    'RenderX': [('com.github.tech-pw:RenderX', 'MIT')],
    'axml': [('com.github.Sable:axml', 'Apache-2.0')],
    'libsu': [('com.github.topjohnwu.libsu:core', 'Apache-2.0')],
    'JLatexMath-Android': [('ru.noties:jlatexmath-android', 'GPL-2.0 with Classpath Exception')],
    'ML Kit': [('com.google.mlkit:text-recognition', 'Apache-2.0'),
               ('com.google.android.gms:play-services-mlkit-text-recognition', 'Apache-2.0')],
    'Bouncy Castle': [('org.bouncycastle:bcprov-jdk18on', 'MIT')],
    'Tasker Plugin Library': [('com.joaomgcd:taskerpluginlibrary', 'Apache-2.0')],
    'Checker Framework (transitive)': [('org.checkerframework:checker-compat-qual', '(not listed in-app)')],
    'apk-parser': [('com.robert:apkanalyzer', '(?)')],
    'Apache PDFBox': [('org.apache.pdfbox:pdfbox', 'Apache-2.0')],
    'ZIP4J': [('net.lingala.zip4j:zip4j', 'Apache-2.0')],
    'ObjectBox': [('io.objectbox:objectbox-android', 'Apache-2.0')],
}
GR = 'C:/Users/15942/Desktop/Work/.gradle-m0/caches/modules-2/files-2.1'
for name, pairs in MAP.items():
    for prefix, inapp in pairs:
        hit = [c for c in by if c.startswith(prefix + ':')]
        for c in sorted(hit):
            r = by[c]
            print('%-30s %-46s in-app=%-34s measured=%-46s kind=%s' % (
                name, c, inapp, (r['licence'] or '(none)').strip()[:46], r['kind']))
        if not hit:
            print('%-30s %-46s NOT IN GRAPH (prefix %s)' % (name, '', prefix))
print()
print('=== every coordinate whose measured licence mentions GPL/AGPL/LGPL/MPL/EPL/UnRar/Tasker/ObjectBox/Binary ===')
pat = ('gpl', 'agpl', 'lgpl', 'mpl', 'eclipse', 'unrar', 'tasker', 'binary licence', 'openldap',
       'terms of service', 'sdk license', 'creative', 'mozilla')
seen = set()
for c, r in sorted(by.items()):
    low = (r['licence'] or '').lower()
    if any(p in low for p in pat):
        g = (c.split(':')[0], (r['licence'] or '').strip())
        print('%-52s %-46s direct=%-5s kind=%s' % (c, (r['licence'] or '').strip()[:44], r['direct'], r['kind']))
        seen.add(g)
