import io
import os
import re
import json
import glob
import zipfile
import hashlib
import urllib.request
import urllib.error

PROXY = 'http://127.0.0.1:7897'
op = urllib.request.build_opener(urllib.request.ProxyHandler({'http': PROXY, 'https': PROXY}))
REPO = 'C:/Users/15942/Desktop/Work/operit/'
APK = 'C:/M0/A/app/build/outputs/apk/clone/app-clone.apk'
GR = 'C:/Users/15942/Desktop/Work/.gradle-m0/caches/modules-2/files-2.1'


def fetch(u):
    try:
        r = op.open(urllib.request.Request(u, headers={'User-Agent': 'licence-audit'}), timeout=60)
        return r.getcode(), r.read()
    except urllib.error.HTTPError as e:
        return e.code, b''
    except Exception as e:
        return None, repr(e).encode()


def txt(b, n=200):
    return re.sub(r'\s+', ' ', b.decode('utf-8', 'replace'))[:n]


TARGETS = {
    'com.github.topjohnwu.libsu:core/nio/service:6.0.0': {
        'repo': 'topjohnwu/libsu', 'pinned_ref': '6.0.0', 'fallback_refs': ['master'],
        'declared_in': ['app/build.gradle.kts:718', 'app/build.gradle.kts:719', 'app/build.gradle.kts:720'],
        'cache_glob': ('com.github.topjohnwu.libsu', 'core'), 'dex_needle': b'Lcom/topjohnwu/superuser/'},
    'com.github.Sable:axml:2.0.0': {
        'repo': 'Sable/axml', 'pinned_ref': '2.0.0', 'fallback_refs': ['master'],
        'declared_in': ['app/build.gradle.kts:704', 'examples/apktool/runtime_helper/build.gradle.kts:44'],
        'cache_glob': ('com.github.Sable', 'axml'), 'dex_needle': b'Lpxb/android/'},
    'com.github.tech-pw:RenderX:1.0.0': {
        'repo': 'tech-pw/RenderX', 'pinned_ref': '1.0.0', 'fallback_refs': ['main', 'master'],
        'declared_in': ['app/build.gradle.kts:755', 'gradle/libs.versions.toml:167'],
        'cache_glob': ('com.github.tech-pw', 'RenderX'), 'dex_needle': b'Llive/pw/render'},
}
NAMES = ['LICENSE', 'LICENSE.txt', 'LICENSE.md', 'COPYING', 'LICENCE']
out = {}

az = zipfile.ZipFile(APK)
dexes = [n for n in az.namelist() if n.endswith('.dex')]
datab = [(n, az.read(n)) for n in dexes]

for coord, t in TARGETS.items():
    rec = {'repo': 'https://github.com/%s' % t['repo'], 'pinned_ref': t['pinned_ref'],
           'declared_in': t['declared_in']}
    c, b = fetch('https://api.github.com/repos/%s' % t['repo'])
    if c == 200:
        j = json.loads(b)
        rec['api'] = {'default_branch': j.get('default_branch'),
                      'spdx_detected_on_current_tree': (j.get('license') or {}).get('spdx_id'),
                      'pushed_at': j.get('pushed_at'), 'size_kb': j.get('size')}
    for ref in [t['pinned_ref']] + t['fallback_refs']:
        got = []
        for nm in NAMES:
            c2, b2 = fetch('https://raw.githubusercontent.com/%s/%s/%s' % (t['repo'], ref, nm))
            if c2 == 200 and b2:
                got.append({'file': nm, 'bytes': len(b2),
                            'sha256_16': hashlib.sha256(b2).hexdigest()[:16],
                            'text_head': txt(b2, 120),
                            'is_gpl3': 'GNU GENERAL PUBLIC LICENSE Version 3' in txt(b2, 400),
                            'is_apache2': 'Apache License Version 2.0' in txt(b2, 400)})
        c3, b3 = fetch('https://api.github.com/repos/%s/contents?ref=%s' % (t['repo'], ref))
        root_lic = []
        if c3 == 200:
            root_lic = [x['name'] for x in json.loads(b3)
                        if re.search(r'licen|copying', x['name'], re.I)]
        rec.setdefault('refs', {})[ref] = {'licence_files_at_ref': got,
                                           'licence_named_files_in_root_listing': root_lic}
    grp, art = t['cache_glob']
    paths = sorted(glob.glob(os.path.join(GR, grp, art, '*', '*', '*')))
    arts = []
    for p in paths:
        if not p.endswith(('.aar', '.jar')):
            continue
        z = zipfile.ZipFile(p)
        files = z.namelist()
        if p.endswith('.aar') and 'classes.jar' in files:
            inner = zipfile.ZipFile(io.BytesIO(z.read('classes.jar')))
            cf = sorted(f for f in inner.namelist() if f.endswith('.class'))
        else:
            cf = sorted(f for f in files if f.endswith('.class'))
        arts.append({'artifact': os.path.basename(p), 'sha256_16':
                     hashlib.sha256(io.open(p, 'rb').read()).hexdigest()[:16],
                     'class_count': len(cf),
                     'class_prefixes': sorted({'/'.join(f.split('/')[:2]) for f in cf})[:6],
                     'aar_licence_or_asset_files': [f for f in files
                                                    if re.search(r'licen|assets/|res/font', f, re.I)][:8],
                     'aar_entry_count': len(files)})
    rec['cached_artifacts'] = arts
    nd = t['dex_needle']
    hits = sum(d.count(nd) for _, d in datab)
    rec['dex'] = {'needle': nd.decode(), 'hits': hits,
                  'dex_files': sorted(n for n, d in datab if d.count(nd))}
    out[coord] = rec

# RenderX payload that enters the APK only through the AAR
rx_assets = [n for n in az.namelist() if n.startswith('assets/MathJax')]
rx_fonts = [n for n in az.namelist() if n.startswith('res/font')]
out['_renderx_apk_payload'] = {
    'assets/MathJax_entries': len(rx_assets),
    'assets/MathJax_bytes': sum(az.getinfo(n).file_size for n in rx_assets),
    'res/font_entries': rx_fonts,
    'repo_side_sources_of_these_names': 0,
    'repo_side_sources_note': 'rg 全仓检索 MathJax/gilroy/balsamiqsans 的文件名命中数为 0，'
                              '即这些条目只可能来自 RenderX-1.0.0.aar。',
    'kotlin_or_java_references_to_the_library': 0,
    'reference_note': 'rg 检索 live.pw / MathView / renderX( 在 *.kt/*.java 里 0 命中；'
                      '仓库内唯一提到该库代码位置的是 app/src/main/AndroidManifest.xml:112-114，'
                      '它用 tools:node="remove" 删掉 AAR 自带的 exported activity '
                      'live.pw.renderX.LatexView（该类在 AAR 的 26 个 class 里并不存在）。'}
io.open(REPO + 'evidence/M0/upstream-license-retrieval.json', 'w', encoding='utf-8').write(
    json.dumps({'measuredAt': '2026-09-19', 'case': 'M0-A05 五个"无出处"构件的上游 LICENSE 联网取证',
                'proxy': PROXY, 'apk_inspected': APK, 'dex_files_scanned': len(dexes),
                'results': out}, ensure_ascii=False, indent=1))
print(io.open(REPO + 'evidence/M0/upstream-license-retrieval.json', encoding='utf-8').read()[:40])
for k, v in out.items():
    if k.startswith('_'):
        continue
    print('==', k)
    for ref, d in v['refs'].items():
        print('   ref %-8s root-licence-files=%s fetched=%s' % (
            ref, d['licence_named_files_in_root_listing'],
            [(x['file'], x['bytes'], x['sha256_16'], 'GPL3' if x['is_gpl3'] else ('APACHE2' if x['is_apache2'] else '?'))
             for x in d['licence_files_at_ref']]))
    print('   api', v.get('api'))
    print('   dex', v['dex'])
    print('   artifacts', [(a['artifact'], a['class_count']) for a in v['cached_artifacts']])
