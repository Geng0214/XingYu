import io
import json
import hashlib

EV = 'C:/Users/15942/Desktop/Work/operit/evidence/M0/'
base = json.load(io.open(EV + 'upstream-license-retrieval.json', encoding='utf-8'))
dex = json.load(io.open('C:/tmp/dex_table.json', encoding='utf-8'))
rd = json.load(io.open('C:/tmp/renderx_readme.json', encoding='utf-8'))
tg = json.load(io.open('C:/tmp/renderx_tag.json', encoding='utf-8'))
ex = json.load(io.open('C:/tmp/upstream_extra.json', encoding='utf-8'))

aar = 'C:/Users/15942/Desktop/Work/.gradle-m0/caches/modules-2/files-2.1/com.github.tech-pw/RenderX/1.0.0/d1d4c451eaf5a7437d430efb8a0dcdaa6dd64464/RenderX-1.0.0.aar'
b = io.open(aar, 'rb').read()

out = {
    'measuredAt': '2026-09-19',
    'case': 'M0-A05 第三轮：把 gradle-dependency-licenses.md 第 3 节标记的 5 个"本机无出处"构件联网取证，'
            '并与仓库自维护的许可界面逐条对账',
    'network': {
        'proxy': base.get('proxy'),
        'egress_ip_seen': '23.172.200.83（jitpack 与 GitHub 日志中记录的来源 IP）',
        'channels_used': ['raw.githubusercontent.com', 'github.com HTML（/tree/<ref>、/tags、/releases/tag/<t>、'
                                                     '/commits/<branch>/<path>.atom）',
                          'api.github.com（中途耗尽配额，见 failed_probes）', 'jitpack.io build.log'],
        'failed_probes': {
            'api.github.com': [k for k, v in ex.items()
                               if isinstance(v, dict) and isinstance(v.get('repo_api'), dict)
                               and v['repo_api'].get('status') == 403],
            'note': 'api.github.com 对 23.172.200.83 返回 403 "API rate limit exceeded"，'
                    '因此仓库元数据（default_branch、spdx、tag 列表、LICENSE 提交历史）改从 raw 与 HTML 通道取得；'
                    '这些通道的证据强度不低于 API，且下面每一项都给了具体 URL 形态。'
        },
    },
    'libsu': {
        'coordinate': 'com.github.topjohnwu.libsu:core/nio/service:6.0.0',
        'verdict': 'Apache-2.0，且**在钉住的 tag 上可证**',
        'evidence': {
            'licence_at_pinned_ref': base['results']['com.github.topjohnwu.libsu:core/nio/service:6.0.0']
            ['refs']['6.0.0']['licence_files_at_ref'],
            'only_commit_touching_LICENSE': '2018-01-25T09:53:04Z (546b0eb2e2)，此后未改动（atom 计数 1）',
            'github_spdx_on_current_tree': 'Apache-2.0',
            'pom_licenses_block': '不存在（jitpack 生成的 POM）',
        },
        'dex': base['results']['com.github.topjohnwu.libsu:core/nio/service:6.0.0']['dex'],
        'in_app_screen_claim': 'Apache-2.0 —— 一致',
        'residual': 'nio/service 两个构件未单独复核（缓存里只有 core-6.0.0.aar 被解析到），'
                    '同仓库同 tag，同一份 LICENSE 覆盖；core 的 AAR 内无许可文本（6 条目）。',
    },
    'axml': {
        'coordinate': 'com.github.Sable:axml:2.0.0',
        'verdict': '上游现为 Apache-2.0，但**钉住的 2.0.0 tag 上没有许可文本**',
        'evidence': {
            'refs': base['results']['com.github.Sable:axml:2.0.0']['refs'],
            'github_spdx_on_current_tree': ex['Sable/axml']['repo_api']['spdx'],
            'tags': ex['Sable/axml']['tags'],
            'note': '上游唯一 tag 就是 2.0.0，因此"升级到带许可的发布版"这条路不存在；'
                    'master 上的 LICENSE.txt（11,358 字节，Apache-2.0，sha256_16 cfc7749b96f63bd3）'
                    '晚于该 tag。jitpack 构件 axml-2.0.0.jar 内 45 条目、34 个 class，无任何许可文本。',
        },
        'dex': base['results']['com.github.Sable:axml:2.0.0']['dex'],
        'in_app_screen_claim': 'Apache-2.0 —— 与上游现状一致，与被分发的构件不对应',
        'options': ['向作者取得 2.0.0 的 Apache-2.0 追认（或改用 master 构建）', '整链移除（但 subpack 重打包依赖它，代价高于 libsu）'],
    },
    'RenderX': {
        'coordinate': 'com.github.tech-pw:RenderX:1.0.0',
        'verdict': '分发的构件**没有任何许可授予**；上游唯一的一份许可是三天后在 main 上加的 GPL-3.0。'
                   '两种解读都要求移除或替换，且应用内界面写着 "MIT"。',
        'provenance_chain': {
            'aar_sha1': hashlib.sha1(b).hexdigest(),
            'aar_sha256_16': hashlib.sha256(b).hexdigest()[:16],
            'aar_bytes': len(b),
            'gradle_cache_checksum_dir_matches_sha1': True,
            'jitpack_build_log': 'https://jitpack.io/com/github/tech-pw/RenderX/1.0.0/build.log',
            'built_from_commit': 'de7d1e33da264f9969a653a4bd6c6eec7cf143d9',
            'git_describe': '1.0.0-0-gde7d1e3（即恰在 tag 上，无额外提交）',
            'commit_message': 'Renamed the Library name to RenderX',
            'commit_author': 'Rahul Jindal',
            'commit_date': '2024-02-23T18:02:26+05:30',
            'build_start': 'Fri Feb 23 12:39:33 UTC 2024',
        },
        'at_that_commit': {
            'readme_probe_leftovers': ex['_renderx_readme_probe'],
            'root_listing_at_tag': tg.get('tree_html'),
            'licence_files_any_spelling': [],
            'probed_names': ['LICENSE', 'LICENSE.md', 'LICENSE.txt', 'LICENCE', 'LICENCE.md', 'COPYING',
                             'COPYING.md', 'UNLICENSE', 'NOTICE', 'LICENSE-APACHE', 'LICENSE-MIT',
                             'renderX/LICENSE', 'renderX/license', 'app/LICENSE'],
            'probe_result': '上述 13 个名字在 raw.githubusercontent.com/tech-pw/RenderX/1.0.0/ 下全部 404；'
                            'GitHub HTML 树列表（/tree/refs/tags/1.0.0）返回的 14 个根条目里也没有任何许可文件名。',
            'readme_md_at_tag': rd['readme_1_0_0'],
            'readme_licence_statement_at_tag': '无：readme.md 里 MIT/Apache/GPL/AGPL/BSD/Unlicense 六个词 0 命中',
        },
        'later_on_main': {
            'only_commit_touching_LICENSE': {'date': '2024-02-26T06:32:08Z',
                                             'sha': '88423982e36cce638340b6c232b1e55679f2c091',
                                             'message': 'Create LICENSE'},
            'file': {'bytes': 35149, 'sha256_16': '3972dc9744f6499f',
                     'fingerprint': '正文首 400 字符含 "GNU GENERAL PUBLIC LICENSE / Version 3"'},
            'gap_days': 3,
            'tags': rd.get('tag_page'),
            'repo_has_only_one_tag': True,
        },
        'what_the_aar_carries': ex['_renderx_aar'],
        'apk_footprint': dict(
            base['results']['_renderx_apk_payload'],
            dex_hits=dex['dex_needles_anchored']['com.github.tech-pw:RenderX:1.0.0']['hits'],
            mathJaxView_class_hits=dex['dex_needles_anchored']['  └ RenderX 的 mathJaxView 子包']['hits'],
            LatexView_class_hits=dex['dex_needles_anchored'][
                'live.pw.renderX.LatexView (manifest 里被 remove 的那个类)']['hits'],
            fonts_from_this_aar=['balsamiqsans_bold.ttf', 'balsamiqsans_regular.ttf', 'gilroy_bold.otf',
                                 'gilroy_medium.otf', 'gilroy_regular.otf', 'gilroy_semi_bold.otf',
                                 'poppins_bold.xml', 'roboto_bold_font.ttf', 'roboto_light.ttf',
                                 'roboto_medium_font.ttf', 'roboto_regular.ttf', 'roboto_thin.ttf'],
            fonts_note='AAR 的 12 个 res/font 条目全部进入 APK；APK 里另有 2 个字体'
                       '（jetbrains_mono_nerd_font_regular.ttf、roboto_medium_numbers.ttf）来自仓库自身。'
                       'AAR 内除 assets/MathJax/LICENSE 外没有任何许可文本，'
                       '即这 12 个字体二进制是随一个无许可构件一起分发的、自身也无授权的素材。',
        ),
        'usage': {
            'kotlin_or_java_references': 0,
            'manifest_mentions': ['app/src/main/AndroidManifest.xml:112-114 用 tools:node="remove" '
                                  '删掉 AAR 自带的 exported MAIN/LAUNCHER activity live.pw.renderX.LatexView'],
            'aar_own_declares_that_activity': '是：AAR 的 AndroidManifest.xml 声明 '
                                              '<activity android:name="live.pw.renderX.LatexView" '
                                              'android:exported="true"> + MAIN/LAUNCHER intent-filter，'
                                              '而 dex 里 `Llive/pw/renderX/LatexView;` 命中 0，'
                                              'AAR 的 26 个 class 里也没有该类（`LatexView` 只作为 '
                                              'ThemeKt$LatexViewTheme$… 的函数名片段出现）',
            'declaration_sites': ['app/build.gradle.kts:755', 'gradle/libs.versions.toml:167'],
            'removal_cost': '删 1 行依赖 + 1 行 version catalog；无源码改动。'
                            '同时消掉一个 GPL-3.0/无许可构件、150 个 MathJax 资产（1,434,800 字节）与 12 个字体。',
        },
        'in_app_screen_claim': 'OpenSourceLicenses.kt:113 写 "MIT" —— 现已可判定为错误声明'
                               '（上游从未出现过 MIT 文本；1.0.0 无任何授予，main 是 GPL-3.0）',
    },
    'dex_needles_anchored': dex['dex_needles_anchored'],
    'pom_license_block_verification': {
        'method': '直接读缓存里的 .pom 原文的 <licenses> 块（不是取第一条），'
                  '用来复核 gradle-dependency-licenses.json 的 kind 分类',
        'corrections': [
            {'coord': 'org.checkerframework:checker-compat-qual:2.5.3',
             'recorded_kind': 'COPYLEFT',
             'actual': '双许可：<license>GPL2 with the classpath exception</license> '
                       '<license>The MIT License</license>',
             'impact': '可选 MIT，不构成 copyleft 义务；原分类是"只取第一条 <license>"的脚本缺陷。'},
            {'coord': 'com.itextpdf:itextg:5.5.10',
             'recorded_kind': 'COPYLEFT',
             'actual': '<licenses> 里只有 GNU Affero General Public License v3 一条',
             'impact': '界面写的 MPL/LGPL 在这个构件的 POM 里不存在；AGPL-3.0 无可选弱许可。'},
            {'coord': 'ru.noties:jlatexmath-android:0.2.0',
             'recorded_kind': 'COPYLEFT',
             'actual': '只有 "GNU General Public License, version 2"，无 classpath exception 字样',
             'impact': '界面写的 "GPL-2.0 with Classpath Exception" 在 POM 里没有依据。'},
            {'coord': 'io.objectbox:objectbox-android:5.3.0',
             'recorded_kind': 'permissive',
             'actual': '三条并列：ObjectBox Binary Licence（含 native runtime）、Apache-2.0（flatbuffers）、'
                       'OpenLDAP Public License 2.8（LMDB）',
             'impact': '界面只写 Apache-2.0 是不完整的；native runtime 走的是非开源的二进制许可。'},
            {'coord': 'com.github.junrar:junrar:7.5.5',
             'recorded_kind': 'other',
             'actual': '单条 UnRar License',
             'impact': '界面写 "The Unlicense" 是把它说成公有领域。'},
            {'coord': 'com.joaomgcd:taskerpluginlibrary:0.4.10',
             'recorded_kind': 'other', 'actual': '单条 Tasker Plugin Sample License',
             'impact': '界面写 Apache-2.0 不符。'},
            {'coord': 'com.google.mlkit:* / com.google.android.gms:play-services-mlkit-*',
             'recorded_kind': 'other', 'actual': 'ML Kit Terms of Service / Android SDK License',
             'impact': '界面写 Apache-2.0 不符（共 15+4 个构件）。'},
        ],
    },
    'in_app_screen': {
        'file': 'app/src/main/java/com/ai/assistance/operit/ui/features/about/screens/OpenSourceLicenses.kt',
        'entries': 68,
        'verified_conflicts': 7,
        'note': '逐条对账用的是 POM <licenses> 原文与联网取证结果。'
                '自动化的名称↔坐标模糊匹配做过一版（C:/tmp/xcheck2.py），但它会把 junrar 的 '
                '"The Unlicense" 与 "UnRar License" 判成一致、也会把 AndroidX 的 token 匹配到任意含 '
                'core/ktx 的构件，因此本表的每一条都是手工核对过的，不采用自动匹配结果。',
    },
}
out['RenderX']['at_that_commit']['module_build_gradle_kts'] = tg.get('subprobe')
io.open(EV + 'upstream-license-retrieval.json', 'w', encoding='utf-8').write(
    json.dumps(out, ensure_ascii=False, indent=1))
print('bytes', len(io.open(EV + 'upstream-license-retrieval.json', encoding='utf-8').read()))
print(json.dumps(out['RenderX']['at_that_commit']['readme_md_at_tag'], ensure_ascii=False)[:300])
