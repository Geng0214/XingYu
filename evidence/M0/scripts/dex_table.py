import io
import zipfile

APK = 'C:/M0/A/app/build/outputs/apk/clone/app-clone.apk'
z = zipfile.ZipFile(APK)
dexes = [n for n in z.namelist() if n.endswith('.dex')]
datab = [(n, z.read(n)) for n in dexes]

NEEDLES = [
    ('RenderX（GPL-3.0，本次新链入证明）', b'Llive/pw/render'),
    ('axml / pxb（许可在钉住提交缺失）', b'Lpxb/android/'),
    ('libsu（上游 Apache-2.0 已证）', b'Lcom/topjohnwu/superuser/'),
    ('itextg（AGPL-3.0）', b'Lcom/itextpdf/text/'),
    ('junrar（UnRar License）', b'Lcom/github/junrar/'),
    ('jlatexmath（GPL-2.0）', b'Lru/noties/jlatexmath/'),
    ('checker-framework（GPL2+classpath）', b'Lorg/checkerframework/'),
    ('（对照）pdfbox', b'Lcom/tom_roush/pdfbox/'),
    ('（对照）zip4j', b'Lnet/lingala/zip4j/'),
]
print('dex files:', len(dexes))
for lbl, nd in NEEDLES:
    tot = sum(d.count(nd) for _, d in datab)
    where = sorted(n for n, d in datab if d.count(nd))
    print('  %-34s %-28s hits=%-6d dex=%-2d %s' % (lbl, nd.decode(), tot, len(where),
                                                   [w.replace('.dex', '') for w in where][:6]))
