import io
import os
import re
import json
import zlib
import hashlib
import collections

REPO = 'C:/Users/15942/Desktop/Work/operit/'
LIC = json.load(io.open('C:/tmp/npm_licenses.json', encoding='utf-8'))
CLO = json.load(io.open('C:/tmp/npm_mcp_closure.json', encoding='utf-8'))
ATT = json.load(io.open('C:/tmp/npm_attribution.json', encoding='utf-8'))

lock_lines = io.open(REPO + 'pnpm-lock.yaml', encoding='utf-8').read().split('\n')
ver = [l for l in lock_lines[:3] if 'lockfileVersion' in l][0].strip()
imp = [k for k, l in enumerate(lock_lines) if l.rstrip() == '  tools/mcp_bridge:'][0]
importer_block = '\n'.join(x for x in lock_lines[imp:imp + 18] if x.strip())

bundles = {}
for f in ('index.js', 'spawn-helper.js'):
    p = REPO + 'app/src/main/assets/bridge/' + f
    b = io.open(p, 'rb').read()
    apk = {}
    for root in ('A', 'B'):
        ap = 'C:/M0/%s/app/build/outputs/apk/clone/app-clone.apk' % root
        if not os.path.exists(ap):
            continue
        import zipfile
        z = zipfile.ZipFile(ap)
        n = 'assets/bridge/' + f
        if n in z.namelist():
            ab = z.read(n)
            apk[root] = {'bytes': len(ab), 'sha256_16': hashlib.sha256(ab).hexdigest()[:16],
                         'identical_to_repo_copy': ab == b}
    bundles[f] = {
        'path': 'app/src/main/assets/' + f,
        'bytes': len(b),
        'sha256_16': hashlib.sha256(b).hexdigest()[:16],
        'crc32': '%08x' % (zlib.crc32(b) & 0xffffffff),
        'in_apk': apk,
        'copyright_banner_count': len(re.findall(rb'Copyright', b, re.I)),
        'bang_comment_banners': len(re.findall(rb'/\*!', b)),
        'mit_word': len(re.findall(rb'\bMIT\b', b)),
        'node_modules_path_tokens': len(re.findall(rb'node_modules[/\\]', b)),
        'webpack_runtime_markers': {k.decode(): b.count(k) for k in
                                    (b'__webpack_modules__', b'__webpack_module_cache__',
                                     b'__webpack_require__')
                                    if b.count(k)},
        'unused_webpack_module_occurrences': len(re.findall(rb'__unused_webpack_module', b)),
        'source_mapping_url_refs': len(re.findall(rb'//# sourceMappingURL=', b)),
        'source_mapping_url_basenames': sorted({x.decode() for x in
                                                re.findall(rb'//# sourceMappingURL=([^\n\r";]+)', b)}),
        'source_map_files_shipped': False,
    }

packages = []
for r in LIC['packages']:
    lf = r.get('licence_files') or []
    packages.append({
        'pkg': r['pkg'], 'version': r['version'],
        'lock_key': next((c['lock_key'] for c in CLO['mcp_bridge_sdk']
                          if c.get('pkg') == r['pkg'] and c.get('version') == r['version']), r['pkg'] + '@' + r['version']),
        'declared_license_field': r.get('license_field'),
        'shipped_licence_file': (lf[0]['path'] if lf else None),
        'licence_text_bytes': (lf[0]['bytes'] if lf else None),
        'licence_text_sha256_16': (lf[0]['sha256_16'] if lf else None),
        'licence_text_class': (lf[0]['text_class'] if lf else None),
        'copyright_lines': (lf[0].get('copyright_lines') if lf else []),
        'field_agrees_with_text': r.get('field_matches_shipped_text'),
        'registry_integrity_matches_lockfile': ((r.get('integrity') or '') ==
                                                next((c.get('integrity_sha512_16') or ''
                                                      for c in CLO['mcp_bridge_sdk']
                                                      if c.get('pkg') == r['pkg']
                                                      and c.get('version') == r['version']), '')),
        'repository': r.get('repository'),
        'tarball_bytes': r.get('tarball_bytes'),
        'tarball_sha256_16': r.get('tarball_sha256_16'),
        'deprecated': r.get('deprecated'),
    })
packages.sort(key=lambda x: (x['pkg'].lower(), x['version']))

hist = collections.Counter((str(p['declared_license_field']), str(p['licence_text_class']))
                           for p in packages)
doc = {
    'measuredAt': '2026-09-19',
    'case': 'M0-A05 依赖来源与许可闭环 —— Node/JS 分发层',
    'scope': '随 APK 分发的 JS（app/src/main/assets/bridge/*）及其 npm 版本闭包与授权文本；'
             '以及未分发的 web-chat 层与远端加载的 ECharts。',
    'method': [
        '分发确认：解压两份 M0-A01 干净构建 APK，按条目名取 assets/bridge/* 并与仓库内副本逐字节比较。',
        '版本闭包：解析仓库根 pnpm-lock.yaml（lockfileVersion 9.0）的 snapshots 段，'
        '从 importers[tools/mcp_bridge].dependencies 的 @modelcontextprotocol/sdk@1.30.0(zod@3.25.76) 起做 BFS，'
        '沿 dependencies/optionalDependencies 展开，得到 92 个包、0 个未解析。',
        '离线限制：本机没有 node_modules，也没有 pnpm store（检查 4 个候选路径均不存在），'
        '所以许可文本无法从本机取得。',
        '联网取证：经本机代理 127.0.0.1:7897 向 registry.npmjs.org 逐个取 92 个版本的元数据与 tarball，'
        '比对 registry dist.integrity 与 pnpm-lock 钉住的 integrity（同截 sha512 前 16 字符），'
        '再从 tarball 根目录取许可文本（仅接受 package/ 前缀、拒绝 .. 成员，filter="data" 解包），'
        '按文本指纹分类并计 SHA-256 前缀。',
        '产物归属：在两个 bundle 内检索各包特有的字符串字面量（不被压缩改名），命中即正向证明该包代码在包内。',
        '源码一致性：取 tools/mcp_bridge/*.ts 的字符串字面量（≥8 字符）在对应 bundle 中检索；'
        '字符串在 tsc+ncc 后原样保留，未命中即为漂移信号。',
    ],
    'lockfile': {
        'path': 'pnpm-lock.yaml',
        'header': ver,
        'sections': {'packages': 190, 'snapshots': 190},
        'importer_tools_mcp_bridge': importer_block.replace('"', "'"),
    },
    'distributed_js': bundles,
    'closure': {'root': '@modelcontextprotocol/sdk@1.30.0(zod@3.25.76)', 'count': len(packages),
                'license_histogram_field_vs_text': {'%s|%s' % k: v for k, v in hist.items()},
                'distinct_licence_texts': len(set(p['licence_text_sha256_16'] for p in packages)),
                'copyleft_packages': [p['pkg'] for p in packages
                                      if re.search(r'GPL|LGPL|AGPL|MPL|CDDL|EPL', str(p['declared_license_field']), re.I)],
                'field_disagreements': [p['pkg'] for p in packages if p['field_agrees_with_text'] is False],
                'integrity_mismatches': [p['pkg'] for p in packages if not p['registry_integrity_matches_lockfile']],
                'packages': packages},
    'attribution': {
        'index.js': {'positive': ATT['index.js']['positive'],
                     'probe_count': 26,
                     'note': 'index.ts 只 import Node 内建模块（net/child_process/crypto/path/os），'
                             '所以 57 KB 的 index.js 里没有任何第三方包特征串，与闭包结论一致。'},
        'spawn-helper.js': {'positive': ATT['spawn-helper.js']['positive'],
                            'probe_count': 26,
                            'negative_probes': ATT['spawn-helper.js']['negative'],
                            'note': 'spawn-helper.ts 才 import @modelcontextprotocol/sdk/{client/index.js,client/stdio.js}，'
                                    '92 包闭包来自这一支。'},
        'unproven': '正向命中 10/26；其余包未命中特征串，"未命中"不等于"未链入"（只有以字符串字面量出现过的才可见）。',
    },
    'source_vs_artifact': {
        'index.ts -> index.js': {'literals': 40, 'present': 40, 'missing': []},
        'spawn-helper.ts -> spawn-helper.js': {
            'literals': 14, 'present': 12,
            'missing': ['@modelcontextprotocol/sdk/client/index.js', '@modelcontextprotocol/sdk/client/stdio.js'],
            'missing_explained': '两个 import 说明符被 ncc 解析掉是预期行为，不构成漂移证据。'},
    },
    'build_tool': {
        'name': '@vercel/ncc', 'version': '0.38.4', 'declared_license': 'MIT',
        'repository': 'git+https://github.com/vercel/ncc.git',
        'registry_runtime_dependencies': 0,
        'evidence_its_runtime_is_embedded': '两个 bundle 里都有 __webpack_modules__ 与 __webpack_module_cache__（各 2/4 次），'
                                            '即 ncc 注入的 webpack 运行时随包分发；'
                                            '但 ncc 的元数据 dependencies 为空（它自带打过包的 dist），'
                                            '所以按 npm 元数据生成的许可清单会漏掉它注入的这段第三方代码。',
    },
    'in_repo_self_declarations': {
        'repo_root_LICENSE': 'GNU LESSER GENERAL PUBLIC LICENSE Version 3（LICENSE 文件实测文本头）',
        'package_json_license': 'ISC',
        'tools_mcp_bridge_package_json_license': 'MIT（且 "author": ""，tools/ 下无任何 LICENSE 文件）',
        'web_chat_package_json_license': None,
        'licence_files_under_tools_or_web_chat': [],
        'finding': '仓库根是 LGPL-3.0-only，三个 JS 清单里两个自述为 ISC/MIT、一个不写，'
                   '且仓内没有任何支撑文本 —— 与仓库自身许可相互矛盾，归属无法从仓内确定。',
    },
    'not_distributed': {
        'web-chat': 'app/src/main/assets/web-chat/ 被 .gitignore 第 74 行忽略；工作树、两份干净构建与 APK（5,550 条目）内均为 0 命中；'
                    'app/build.gradle.kts 里 "web-chat" 出现 0 次，只有手工脚本 npm run build:webchat 会产出它。',
        'source_maps': '两个 bundle 合计引用 65 个 //# sourceMappingURL 目标，但 assets/ 下 .map 条目为 0，'
                       '且 tools/mcp_bridge/.gitignore 忽略 dist、bundle、bundle-helper，'
                       '所以 ncc 写在 bundle/ 里的任何 sidecar（含许可 sidecar）都既不进仓库也不进 APK。',
        'echarts': 'app/src/main/assets/web/echarts_template.html（2,709 B，全 assets 里唯一带远端 <script src> 的文件）'
                   '从 https://cdn.jsdelivr.net/npm/echarts@5.3.3/dist/echarts.min.js 取码，'
                   '无 integrity、无 crossorigin 属性 —— 不构成分发义务，但是未固定完整性的远端代码执行与出网项。',
    },
}
io.open(REPO + 'evidence/M0/node-dependency-licenses.json', 'w', encoding='utf-8').write(
    json.dumps(doc, ensure_ascii=False, indent=1))
print('wrote node-dependency-licenses.json',
      os.path.getsize(REPO + 'evidence/M0/node-dependency-licenses.json'), 'bytes')
print('packages', len(packages), 'distinct texts', doc['closure']['distinct_licence_texts'],
      'copyleft', doc['closure']['copyleft_packages'],
      'integrity mismatches', doc['closure']['integrity_mismatches'],
      'field disagreements', doc['closure']['field_disagreements'])
print('hist', doc['closure']['license_histogram_field_vs_text'])
