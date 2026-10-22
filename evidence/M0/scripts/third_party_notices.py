"""Generate THIRD-PARTY-NOTICES.md from the measured licence layers.

Inputs are the M0 measurement records only; nothing here is transcribed from the
in-app licence screen (ui/features/about/screens/OpenSourceLicenses.kt), which is
itself an audited-defect item.  Run:  python evidence/M0/scripts/third_party_notices.py
"""

import hashlib
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
M0 = os.path.dirname(HERE)
REPO = os.path.dirname(os.path.dirname(M0))
TEXTS = os.path.join(M0, "licence-texts")


def load(name):
    with open(os.path.join(M0, name), encoding="utf-8") as fh:
        return json.load(fh)


manifest = load("build-manifest.json")
gradle = load("gradle-dependency-licenses.json")
native = load("native-dependency-licenses.json")
rust = load("rust-crate-licenses.json")
node = load("node-dependency-licenses.json")
retrieval = load("upstream-license-retrieval.json")

# ---------------------------------------------------------------- licence names
# SPDX-ish tokens per distinct POM string actually observed in the 396 rows.
NAME_TOKENS = {
    "Apache-2.0", "MIT", "BSD-2-Clause", "BSD-3-Clause", "Zlib", "Unlicense",
    "Unicode-3.0", "ISC", "EPL-1.0", "EDL-1.0", "BouncyCastle", "OLDAP-2.8",
    "LGPL-3.0-only", "GPL-3.0-only", "GPL-2.0-only", "AGPL-3.0-only", "UnRAR",
}


def tokens_for(name):
    """Map one POM <license> string to its licence tokens, in POM order."""
    out = []
    for part in [p.strip() for p in name.split(";") if p.strip()]:
        low = part.lower()
        if "affero" in low:
            out.append("AGPL-3.0-only")
        elif "general public license, version 2" in low:
            out.append("GPL-2.0-only")
        elif "apache" in low:
            out.append("Apache-2.0")
        elif "mit" in low:
            out.append("MIT")
        elif "simplified bsd" in low:
            out.append("BSD-2-Clause")
        elif "3-clause bsd" in low or "revised bsd" in low or "bsd 3-clause" in low:
            out.append("BSD-3-Clause")
        elif "bsd 2-clause" in low:
            out.append("BSD-2-Clause")
        elif low == "bsd license":
            out.append("BSD-unspecified")
        elif "bouncy castle" in low:
            out.append("BouncyCastle")
        elif "openldap" in low:
            out.append("OLDAP-2.8")
        elif "objectbox binary" in low:
            out.append("ObjectBox-Binary-Licence")
        elif "eclipse distribution" in low:
            out.append("EDL-1.0")
        elif "eclipse public" in low:
            out.append("EPL-1.0")
        elif "classpath exception" in low:
            out.append("GPL-2.0-with-classpath-exception")
        elif "unrar" in low:
            out.append("UnRAR")
        elif "android software development kit" in low:
            out.append("Android-SDK-ToS")
        elif "ml kit" in low:
            out.append("ML-Kit-ToS")
        elif "tasker" in low:
            out.append("Tasker-Plugin-Sample")
        else:
            raise SystemExit(f"unmapped POM licence string: {part!r}")
    return out


def row_tokens(row):
    name = (row["licence"] or "").strip()
    if name:
        return tokens_for(name)
    coord = row["coord"]
    if coord.startswith("com.github.topjohnwu.libsu:"):
        return ["Apache-2.0"]          # upstream-license-retrieval.json: libsu
    if coord == "com.github.Sable:axml:2.0.0":
        return ["Axml-2.0.0-no-text-on-tag"]
    if coord == "com.github.tech-pw:RenderX:1.0.0":
        return ["RenderX-no-grant"]
    raise SystemExit(f"licence-less row without a recorded verdict: {coord}")


# ---------------------------------------------------------------- full texts
TEXT_FILES = {
    "LGPL-3.0-only": ("FSF-LGPL-3.0.txt", "https://www.gnu.org/licenses/lgpl-3.0.txt"),
    "GPL-3.0-only": ("FSF-GPL-3.0.txt", "https://www.gnu.org/licenses/gpl-3.0.txt"),
    "GPL-2.0-only": ("FSF-GPL-2.0.txt", "https://www.gnu.org/licenses/gpl-2.0.txt"),
    "AGPL-3.0-only": ("SPDX-AGPL-3.0-only.txt",
                      "https://raw.githubusercontent.com/spdx/license-list-data/main/text/AGPL-3.0-only.txt"),
    "Apache-2.0": ("ASF-Apache-2.0.txt", "https://www.apache.org/licenses/LICENSE-2.0.txt"),
    "MIT": ("SPDX-MIT.txt", "https://raw.githubusercontent.com/spdx/license-list-data/main/text/MIT.txt"),
    "BSD-2-Clause": ("SPDX-BSD-2-Clause.txt",
                     "https://raw.githubusercontent.com/spdx/license-list-data/main/text/BSD-2-Clause.txt"),
    "BSD-3-Clause": ("SPDX-BSD-3-Clause.txt",
                     "https://raw.githubusercontent.com/spdx/license-list-data/main/text/BSD-3-Clause.txt"),
    "Zlib": ("SPDX-Zlib.txt", "https://raw.githubusercontent.com/spdx/license-list-data/main/text/Zlib.txt"),
    "Unlicense": ("SPDX-Unlicense.txt",
                  "https://raw.githubusercontent.com/spdx/license-list-data/main/text/Unlicense.txt"),
    "Unicode-3.0": ("SPDX-Unicode-3.0.txt",
                    "https://raw.githubusercontent.com/spdx/license-list-data/main/text/Unicode-3.0.txt"),
    "ISC": ("SPDX-ISC.txt", "https://raw.githubusercontent.com/spdx/license-list-data/main/text/ISC.txt"),
    "EPL-1.0": ("SPDX-EPL-1.0.txt",
                "https://raw.githubusercontent.com/spdx/license-list-data/main/text/EPL-1.0.txt"),
    "EDL-1.0": ("OWNER-EDL-1.0.txt", "https://www.eclipse.org/org/documents/edl-v10.php"),
    "BouncyCastle": ("OWNER-BouncyCastle.txt", "https://www.bouncycastle.org/license.html"),
    "OLDAP-2.8": ("SPDX-OLDAP-2.8.txt",
                  "https://raw.githubusercontent.com/spdx/license-list-data/main/text/OLDAP-2.8.txt"),
    "UnRAR": ("UPSTREAM-junrar-LICENSE.txt", "https://raw.githubusercontent.com/junrar/junrar/v7.5.5/LICENSE"),
}

LINK_ONLY = {
    "Android-SDK-ToS": "developer.android.com/studio/terms（按 POM 给出的 url 字段分发）",
    "ML-Kit-ToS": "Google ML Kit 服务条款（按 POM 给出的 url 字段分发）",
    "Tasker-Plugin-Sample": "Tasker 插件示例许可（按 POM 给出的 url 字段分发）",
    "ObjectBox-Binary-Licence": ("ObjectBox 二进制许可：POM 的 url 只指向站点根，站点当前公开的只有网站使用条款（其 §2 明说产品另有条款）。"
                                  "缺口登记在第 2 节未闭合项"),
    "Axml-2.0.0-no-text-on-tag": "上游当前 master 有 Apache-2.0 文本，但 2.0.0 tag 上没有",
    "RenderX-no-grant": "分发的那个构件没有任何许可授予；main 上后加的 GPL-3.0 不覆盖它",
    "BSD-unspecified": "POM 只写 \"BSD License\"，未指明条款数",
    "GPL-2.0-with-classpath-exception": "双许可，本 fork 取 MIT 一支，故不需附 GPL 正文",
}


def sha16(path):
    return hashlib.sha256(open(path, "rb").read()).hexdigest()[:16]


SECTION = {spdx_id: f"9.{i}" for i, spdx_id in enumerate(sorted(TEXT_FILES), 1)}

# Font payloads, counted from the audited APK's own entry list.
with open(os.path.join(M0, "apk-font-entries.txt"), encoding="utf-8") as fh:
    font_entries = [l.strip() for l in fh if l.strip()]


def font_group(entry):
    if entry.startswith("assets/MathJax/"):
        return "mathjax"
    if "/jlatexmath/fonts/" in entry:
        return "jlatexmath"
    if entry.startswith("res/font/"):
        return "renderx_res_font"
    if entry.startswith("assets/com/tom_roush/pdfbox/"):
        return "pdfbox"
    if entry == "assets/fonts/material_symbols_rounded_static.woff2":
        return "material_symbols"
    raise SystemExit(f"unattributed font entry: {entry}")


fonts = {}
for e in font_entries:
    fonts.setdefault(font_group(e), []).append(e)
# `roboto_medium_numbers.ttf` 与 `jetbrains_mono_nerd_font_regular.ttf` 不在 RenderX AAR 里：
# 前者实测位于 exoplayer-ui 的 AAR，后者是 :terminal 子模块自带的 res/font。
for special, owner in [("res/font/roboto_medium_numbers.ttf", "exoplayer"),
                       ("res/font/jetbrains_mono_nerd_font_regular.ttf", "terminal")]:
    if special in fonts["renderx_res_font"]:
        fonts["renderx_res_font"].remove(special)
        fonts[owner] = [special]
    else:
        raise SystemExit(f"expected font entry missing: {special}")

ROBOTO_ENTRY = "res/font/roboto_medium_numbers.ttf"
JETBRAINS_ENTRY = "res/font/jetbrains_mono_nerd_font_regular.ttf"
with open(os.path.join(M0, "font-carrier-scan.json"), encoding="utf-8") as fh:
    carrier_scan = json.load(fh)
if len(carrier_scan["hits"][ROBOTO_ENTRY]) != 1:
    raise SystemExit("exoplayer font attribution needs exactly one cache hit")
if carrier_scan["hits"][JETBRAINS_ENTRY]:
    raise SystemExit("jetbrains mono is attributed to the :terminal submodule, but the cache scan found it")
_hit = carrier_scan["hits"][ROBOTO_ENTRY][0].replace("\\", "/")
_hit_parts = _hit.split("/")
CARRIER_FILE, CARRIER_SHA = _hit_parts[-1], _hit_parts[-2][:16]
if not CARRIER_FILE.startswith("exoplayer-ui-"):
    raise SystemExit(f"unexpected carrier artifact for {ROBOTO_ENTRY}: {CARRIER_FILE}")


# ---------------------------------------------------------------- build identity
up = manifest["upstream"]
subs = {s["path"]: s for s in manifest["submodules"]}
sample = manifest["artifacts"]["workingTreeSample"]
clean = manifest["artifacts"]["cleanBuildEvidence"]
builds = {b["directory"]: b for b in clean["builds"]}

L = []
def w(s=""):
    L.append(s)


w("# 第三方声明与许可（THIRD-PARTY-NOTICES）")
w()
w("> 本文件由 `evidence/M0/scripts/third_party_notices.py` 从 M0 的实测记录生成，**不要手工编辑**；")
w("> 要改内容请改生成脚本或它读取的证据文件后重跑。")
w(">")
w("> **本文件不构成许可合规结论。** 它记录的是“哪些第三方载荷在分发物里、各自的许可证据走到哪一步”。")
w("> 第 2 节列出的未闭合项解除之前，本 fork 不具备对外分发的许可前提。")
w()
w("## 1. 口径")
w()
w("| 项 | 值 |")
w("|---|---|")
w(f"| 主仓库基线 | `{up['candidateTag']}` = `{up['candidateTagCommit'][:16]}…`（记录时工作树 HEAD `{up['workingHeadAtRecordTime'][:16]}…`） |")
w(f"| `:terminal` 子模块 | `{subs['terminal']['commit'][:16]}…` |")
w(f"| 度量所用产物 | `{sample['path']}`，applicationId `{sample['applicationId']}`，versionName `{sample['versionName']}`，ABI {','.join(sample['abis'])}，条目 {sample['entries']:,}，APK SHA-256 `{sample['sha256'][:16]}…` |")
w(f"| 两次干净构建 | `C:/M0/A`、`C:/M0/B`（条目集合一致；APK SHA-256 不同，成因见 `evidence/M0/native-build-path-embedding.txt`） |")
w(f"| 依赖树快照 | `{gradle['source']}`（选中 {gradle['selected']} 个坐标） |")
w("| 许可界面不作原料 | 应用内 `ui/features/about/screens/OpenSourceLicenses.kt` 经实测有 8 条错误声明且整层缺失，**本文件不引用它**；对账见 `evidence/M0/upstream-license-retrieval.md` §6 |")
w()
w("## 2. 未闭合项（分发前必须处置）")
w()
w("下表是本文件生成时**仍然存在**的缺口。每一行的详细取证在 `evidence/M0/` 下注明。")
w()
w("| # | 载荷 | 缺什么 | 详细证据 | 决策归属 |")
w("|---|---|---|---|---|")
open_items = [
    ("`com.itextpdf:itextg:5.5.10`",
     "AGPL-3.0-only。类已确认链入分发物（锚定描述符 `Lcom/itextpdf/text/` 1,808 处）。AGPL 的网络交互条款与“源码对应提供”义务未履行",
     "`gradle-dependency-licenses.md` §3–4", "负责人（M0-B08）"),
    ("`ru.noties:jlatexmath-android` + 两个字体包 0.2.0",
     "GPL-2.0-only，无弱许可可选；对应源码/书面要约义务未履行（88 处描述符已链入）",
     "同上", "负责人（M0-B08）"),
    ("`com.github.junrar:junrar:7.5.5`",
     "UnRAR 许可（非开源许可）。条款正文附在第 " + SECTION["UnRAR"] + " 节；其第 2 条要求**在文档与源码注释中写明“该代码不得用于开发 RAR 兼容压缩器”**，本 fork 尚未在任何位置写这句",
     "`license-inventory.md` §第五轮（按被分发版本的 tag `v7.5.5` 取，与 master 同一文件逐字节相同）+ `licence-texts/sources.json`",
     "本仓库可自证，待写入"),
    ("`com.github.tech-pw:RenderX:1.0.0`",
     "分发的构件**没有任何许可授予**（完整反证链已建立）。随它进包的字体见第 8.1 节",
     "`upstream-license-retrieval.md` §5，M0-B09", "负责人"),
    ("`com.github.Sable:axml:2.0.0`",
     "2.0.0 tag 上无授权文本，上游也没有更晚的带许可发布版；当前 master 才是 Apache-2.0",
     "`upstream-license-retrieval.md` §4", "负责人"),
    ("`io.objectbox:objectbox-android:5.3.0`",
     "POM 列的三条许可里，**ObjectBox Binary Licence 的条款正文未取得**：POM 的 url 只指向站点根，站点当前公开的只有网站使用条款（其 §2 明说产品另有条款、在下载区提供）。本轮不复制该条款文本",
     "本文件第 9 节末“仅链接”清单 + `licence-texts/sources.json` 反面记录第 3 条 + `license-inventory.md` 第四轮末", "负责人"),
    ("`app/libs/ffmpeg-kit-local.aar`（本地 `files(...)` 依赖，不经 Maven 坐标解析，故不在第 4 节的 396 条里）",
     "构建配置串含 `--enable-version3 --enable-libtwolame --enable-libshine --enable-gnutls` 而无 `--enable-gpl`，随包书面要约却自称 “GPL v3.0”；AAR 无版本号/commit/上游哈希 ⇒ GPL 还是 LGPL 组合无法由我们判定",
     "`license-inventory.md` 第二轮", "负责人"),
    ("`assets/accessibility.apk`（1.5）",
     "上游整棵归档 78 条目零授权文本（README 只有一枚徽章）；且分发物相对可核验来源含未记录的本地改动",
     "`upstream-license-retrieval.md` §8", "负责人（已定随 M1 载荷移除）"),
    ("`assets/desktop.apk`（1.0）",
     "全仓零引用，且其 `version-control-info.textproto` = `NO_SUPPORTED_VCS_FOUND`（打包时不在任何 git 工作树内）",
     "同上", "负责人（建议直接删除）"),
    ("`assets/subpack/android.apk`、`windows.zip`",
     "由无校验的 Google Drive 归档取得，`android.apk` 还是 `CN=Android Debug` 的 debug 构建；内含 Flutter 运行时与微软 WebView2 载荷",
     "`license-inventory.md` 第二轮", "负责人（已定移除）"),
    ("`:terminal` → `ubuntu-noble-aarch64-pd-v4.18.0.tar.xz`",
     "来源已证实与官方资产逐字节一致，但 12,684 个成员对应的源码/书面要约义务与 Canonical 商标政策均未履行",
     "同上", "负责人（已定移除）"),
    ("STT 资产 8 项（sherpa ×7 + silero VAD）",
     "许可侧已闭合（Apache-2.0 / MIT）；训练数据出处不可证明；随包属性文件里的许可注解与事实相反",
     "同上", "负责人（已定移除）"),
    (f"字体 {len(font_entries)} 个条目（来源归属逐条见第 8.1 节）",
     "全部**无随包授权文本**。其中 `gilroy` 一支不属于 OFL/Apache 系那种自带授权的字体，且它随一个无许可授予的构件进来",
     "`apk-font-entries.txt`（审计产物条目清单）", "负责人"),
    ("`liboperit_ripgrep.so`",
     "产物内嵌 106 处他人 Windows 用户目录，且经无校验 Drive 归档进入分发链；本仓库 `tools/native_ripgrep` 的 crate 自身既无 `license` 字段也无 LICENSE 文件",
     "`ripgrep-source-rebuild.txt`、`rust-dependency-licenses.md`", "本仓库可自证"),
    ("`com.github.virtuald:curvesapi:1.07`",
     "POM 的许可名只写 \"BSD License\"，未指明 2 条款还是 3 条款；本文件因此**没有**为它附正文",
     "`gradle-dependency-licenses.json`", "本仓库可自证（取上游 LICENSE 原文）"),
]
for i, (a, b, c, d) in enumerate(open_items, 1):
    w(f"| {i} | {a} | {b} | {c} | {d} |")
w()
w("另有两类已登记但不在本文件范围内的缺口：`tools/hotbuild/OperitNightlyRelease`（只有 SSH 地址、构建脚本零引用，不产 APK 侧义务）；"
  "`assets/web/echarts_template.html` 里硬编码的 `cdn.jsdelivr.net` 远端脚本（当前零加载者，属出网与完整性项，见 `storage-network-inventory.md`）。")
w()

w("## 3. 本项目自身的代码")
w()
w("- **Operit fork 主代码**：LGPL-3.0-only。仓库根 `LICENSE` 与 FSF 官方 `lgpl-3.0.txt` 逐字节相同（唯一差别是 CRLF 行尾）；"
  "本文件第 9 节直接附 FSF 官方全文，不引用仓库里那两份的排版。")
w("- **`:terminal`**：`terminal/LICENSE` 的类别同为 LGPL-3.0，条款词经词级比对全部按序出现、替换/删除 0 组；"
  "但它在 GPL-3.0 部分用一句编辑性括注把正文截断，**不能作为可交付的条款文本**——本文件改用 FSF 官方 GPL-3.0 全文。")
w("- **本仓库自写但缺授权声明的部分**：`tools/native_ripgrep` 的 crate `operit_ripgrep 0.1.0`（无 `license` 字段、无 LICENSE 文件）、"
  "`tools/mcp_bridge/package.json`（自述 ISC）、仓库根 `package.json`（自述 MIT）。"
  "后两者的自述许可与仓库整体 LGPL-3.0-only 之间的关系无法由仓内确定，登记在 `node-dependency-licenses.md`。")
w("- **对应源码提供方式**：随包提供本仓库完整源码快照与 `m0-remediation.patch`、`terminal-m0-remediation.patch`"
  "（两份补丁与基线的对应关系记录在 `evidence/M0/build-manifest.json`）。")
w()

# ---------------------------------------------------------------- JVM layer
w("## 4. JVM / Gradle 依赖（" + str(gradle["selected"]) + " 个选中坐标）")
w()
groups = {}
for r in gradle["rows"]:
    for t in row_tokens(r):
        groups.setdefault(t, []).append(r)
multi = sum(1 for r in gradle["rows"] if len(row_tokens(r)) > 1)
unhandled = set(groups) - NAME_TOKENS - set(LINK_ONLY)
if unhandled:
    raise SystemExit(f"licence tokens with no disposition: {sorted(unhandled)}")
order = sorted(groups, key=lambda k: (-len(groups[k]), k))
total_tokens = sum(len(v) for v in groups.values())
w("口径：`:app` 的 `cloneRuntimeClasspath` 实测解析结果（原始树 `dependency-tree-clone-runtime.raw.txt`）。"
  f"`POM 许可名` 一律取上游 POM 原文，不做美化。下表按归一后的许可 token 计数，"
  f"合计 {total_tokens} 条 > {gradle['selected']} 个坐标，"
  f"因为有 {multi} 个坐标的 POM 并列声明了多条许可；`—` 表示该条款未能逐字附出（原因见第 9 节末）。")
w()
w("| 许可（POM 原文归一） | 构件数 | 第 9 节正文 |")
w("|---|---:|---|")
for t in order:
    disp = t if t in NAME_TOKENS else t + "（未取得可附正文）"
    w(f"| {disp} | {len(groups[t])} | {TEXT_FILES[t][0] if t in TEXT_FILES else '—'} |")
w()
licence_less = [r["coord"] for r in gradle["rows"] if not (r["licence"] or "").strip()]
w(f"逐构件清单（`直` = 我们在 build 脚本里直接声明；`POM 许可名` 为 `—` 的 {len(licence_less)} 个坐标其 POM 里没有 "
  "`<licenses>`，许可改由第四轮联网取证取得并在附注里写明证据）：")
w()
by_coord = sorted(gradle["rows"], key=lambda r: r["coord"].lower())
w("| 坐标 | 直 | POM 许可名（原文） | 附注 |")
w("|---|---|---|---|")
NOTES = {
    "com.itextpdf:itextg:5.5.10": "**AGPL-3.0**，见第 2 节第 1 条",
    "com.github.junrar:junrar:7.5.5": "**非开源许可**，条款正文见第 9 节",
    "org.checkerframework:checker-compat-qual:2.5.3": "POM 双许可，本 fork 取 MIT 一支",
    "io.objectbox:objectbox-android:5.3.0": "三条并列，其中 ObjectBox Binary Licence 正文未取得",
    "com.github.virtuald:curvesapi:1.07": "条款数未指明，未附正文",
}
CORRECTED = {
    "com.github.topjohnwu.libsu:core:6.0.0": "Apache-2.0（POM 无声明；上游 tag `6.0.0` 的 `LICENSE` 实测 11,357 字节）",
    "com.github.topjohnwu.libsu:nio:6.0.0": "同上",
    "com.github.topjohnwu.libsu:service:6.0.0": "同上",
    "com.github.Sable:axml:2.0.0": "**2.0.0 tag 上无授权文本**（master 才是 Apache-2.0），见第 2 节第 5 条",
    "com.github.tech-pw:RenderX:1.0.0": "**无许可授予**，见第 2 节第 4 条",
}
for r in by_coord:
    note = NOTES.get(r["coord"], "")
    if r["coord"] in CORRECTED:
        note = (note + "；" if note else "") + CORRECTED[r["coord"]]
    elif not (r["licence"] or "").strip():
        raise SystemExit(f"licence-less row without a correction entry: {r['coord']}")
    lic = (r["licence"] or "").replace("|", "\\|")
    w(f"| `{r['coord']}` | {'直' if r['direct'] else ''} | {lic or '—'} | {note} |")
w()

# ---------------------------------------------------------------- native layer
w(f"## 5. 原生库（CMake FetchContent，{len(native)} 个依赖）")
w()
w("口径：各 CMake 文件里钉死的 commit，逐个读到该 commit 的顶层许可文件（`native-dependency-licenses.json`）。"
  "嵌套许可按源文件列表收敛的证明未做，本表只到“该 commit 上存在这些许可文件”。")
w()
w("| 依赖 | commit | 顶层许可 | 实测到的许可文件（节选） |")
w("|---|---|---|---|")
for e in native:
    files = e["licence_files"]
    top = files[0]["licence"] if files and files[0]["licence"] else "无顶层许可文件"
    shown = ", ".join(f"`{f['path']}`" for f in files[:4])
    if len(files) > 4:
        shown += f" …（共 {len(files)} 个）"
    if not files:
        shown = ("无顶层 LICENSE 文件；按 REUSE 规范授权（`LICENSES/` 目录 + 全树 `SPDX-License-Identifier` 统计 "
                 "550 个 Apache-2.0 + 1 个 BSD-3-Clause，逐文件头取证见 `native-dependency-licenses.md` 第 2 节）")
    w(f"| {e['dep']} | `{e['commit'][:10]}…` | {top} | {shown} |")
w()
tops = {e["dep"]: (e["licence_files"][0]["licence"] if e["licence_files"] else None) for e in native}
no_top = [d for d, v in tops.items() if v is None]
others = sorted(set(tops.values()) - {None} - {"MIT", "Apache-2.0", "zlib"})
if others:
    raise SystemExit(f"native top-level licence outside the measured permissive set: {others}")
if no_top != ["kleidiai"]:
    raise SystemExit(f"native deps without a top-level licence file: {no_top}")
w(f"这一层的顶层许可只出现 {len(set(tops.values()) - {None})} 种（{', '.join(sorted(set(tops.values()) - {None}))}），copyleft 计数 0；"
  "`kleidiai` 的授权载体是 SPDX 头而非顶层文件。")
w()

# ---------------------------------------------------------------- rust layer
w(f"## 6. Rust 层（`liboperit_ripgrep.so`，Cargo.lock {len(rust['packages'])} 包）")
w()
rc = rust["counts"]
w(f"按 `--target aarch64-linux-android` 的依赖图划分：运行期库 {rc['android_runtime_libs']} / "
  f"构建期 proc-macro {rc['build_time_proc_macro']} / 宿主构建专用 {rc['host_build_only']} / "
  f"本目标从未解析 {rc['lock_only_not_resolved']}。copyleft {rc['copyleft']}。"
  f"其中 {rc['with_embedded_crate_path_in_shipped_so']} 个 crate 在随包 `.so` 里有内嵌源码路径的正证据"
  "（未出现路径不代表未链入，只有出现的是正证据）。")
w()
w("| crate | 版本 | 声明许可 | 该目标角色 | 随包 `.so` 内路径出现次数 |")
w("|---|---|---|---|---:|")
role_rank = {r: i for i, r in enumerate(["android-runtime", "build-time-proc-macro", "host-build-only",
                                         "lock-only-not-resolved"])}
unknown_roles = {p["role_for_aarch64_linux_android"] for p in rust["packages"]} - set(role_rank)
if unknown_roles:
    raise SystemExit(f"unknown rust role strings: {sorted(unknown_roles)}")
OWN_NO_FIELD = {"operit_ripgrep": "—（本仓库自有 crate，`Cargo.toml` 无 `license` 字段、目录内无 LICENSE 文件）"}
WINDOWS_NO_FIELD = "—（上游已发布 manifest 无 `license` 字段；本目标未解析，不进产物）"
for p in rust["packages"]:
    if p["declared_in_published_manifest"]:
        continue
    if p["crate"] in OWN_NO_FIELD:
        continue
    if p["crate"].startswith("windows_") and p["role_for_aarch64_linux_android"] == "lock-only-not-resolved":
        continue
    raise SystemExit(f"licence-less rust crate without a recorded explanation: {p['crate']} {p['version']}")
for p in sorted(rust["packages"], key=lambda p: (role_rank[p["role_for_aarch64_linux_android"]], p["crate"])):
    declared = p["declared_in_published_manifest"]
    declared = declared if declared else OWN_NO_FIELD.get(p["crate"], WINDOWS_NO_FIELD)
    w(f"| {p['crate']} | {p['version']} | {declared} | {p['role_for_aarch64_linux_android']} | "
      f"{p['crate_source_path_occurrences_in_shipped_so']} |")
w()
w("`unicode-ident` 的声明是 `(MIT OR Apache-2.0) AND Unicode-3.0`，`regex-syntax` 另有嵌套 `src/unicode_tables/LICENSE-UNICODE`，"
  "所以本层不能只写 MIT/Apache——Unicode-3.0 正文在第 9 节。")
w()

# ---------------------------------------------------------------- node layer
w("## 7. Node / 随包 JS")
w()
cl = node["closure"]
dj = node["distributed_js"]
w(f"口径：两支 ncc bundle —— `{dj['index.js']['path']}`（{dj['index.js']['bytes']:,} 字节）与 "
  f"`{dj['spawn-helper.js']['path']}`（{dj['spawn-helper.js']['bytes']:,} 字节），"
  f"以及 `pnpm-lock.yaml` 的 {cl['count']} 包闭包（`node-dependency-licenses.json`）。本层 copyleft 计数 "
  f"{len(cl['copyleft_packages'])}；声明字段与上游 LICENSE 正文不一致的包 {len(cl['field_disagreements'])} 个；"
  f"tarball 完整性与 lockfile 不符的 {len(cl['integrity_mismatches'])} 个。")
w()
w("下表里的版权行是从各包上游 `package/LICENSE` 原文抽取的，不是杜撰；若要逐包附完整正文，"
  "须按本文件所依据的 `node-dependency-licenses.json` 里各包的 `tarball_sha256_16` 重新取回原 tarball。")
w()
w("| 包 | 版本 | 许可（声明 = 正文） | 版权行（上游 LICENSE 原文） |")
w("|---|---|---|---|")
for p in sorted(cl["packages"], key=lambda x: x["pkg"].lower()):
    field = p["declared_license_field"]
    text = p["licence_text_class"]
    agree = field if field == text else f"{field} / 正文为 {text}"
    cr = "; ".join(p["copyright_lines"]) or "—"
    w(f"| `{p['pkg']}` | {p['version']} | {agree} | {cr} |")
w()
at = node["attribution"]
w("bundle 的归属取证（`node-dependency-licenses.md`）："
  f"`spawn-helper.js` 有 {len(at['spawn-helper.js']['positive'])} 个包留下字符串字面量正证据"
  f"（{'、'.join('`' + p['package'] + '`' for p in at['spawn-helper.js']['positive'])}），"
  f"`index.js` 探了 {at['index.js']['probe_count']} 个包全部 0 命中——它只 import Node 内建模块，"
  f"因此那 {dj['index.js']['bytes']:,} 字节无法按包归属。"
  "这是 ncc 扁平化的固有盲区，登记在 `node-dependency-licenses.md`，不在此处假装已解决。")
w()
w("随包但**当前不进分发**的远端加载：`assets/web/echarts_template.html` 本身在 APK 里（2,709 字节），"
  "其硬编码的 `cdn.jsdelivr.net/npm/echarts@5.3.3/…` 无 `integrity`、无 `crossorigin`，且当前没有任何代码加载该模板。"
  "ECharts 本体（Apache-2.0）不随包，故不产生声明义务，但它是完整性风险项。")
w()

# ---------------------------------------------------------------- shipped binaries
w("## 8. 随 APK 分发的二进制与资产")
w()
w("这一层是前几轮的工作面（`license-inventory.md` 第二轮、第三轮、第四轮）。"
  "`来源` 一栏只写实测到的证明方式，没写就是没测。")
w()
assets = [
    ("`assets/shizuku.apk` 2,571,773 B（13.6.0）",
     "与 `RikkaApps/Shizuku` v13.6.0 官方资产 SHA-256 逐字节相同；签名 `CN=Rikka`",
     "Apache-2.0（第 " + SECTION["Apache-2.0"] + " 节正文），附带明确商标保留：不得改名、不得重签名、不得复用其图标",
     "可保留（首版仍在执行层禁用）"),
    ("`assets/accessibility.apk` 2,763,980 B（versionCode 4 / 1.5）",
     "随包 APK 的 `META-INF/version-control-info.textproto` 记录 revision = 上游 `main` 头；但该提交自带 `1.4/3` ⇒ 工作树被改过且未提交",
     "**整棵归档 78 条目零授权文本**（唯一 Apache 文字是 Gradle wrapper 自带），项目级许可信号只有 README 一枚徽章",
     "随 M1 载荷移除一并关闭"),
    ("`assets/desktop.apk` 6,489,542 B（1.0）",
     "`textproto` = `NO_SUPPORTED_VCS_FOUND`；全仓零引用；与上一条共用自签证书 `O=dec, OU=sw, CN=aa`",
     "无授权依据",
     "建议删除"),
    ("`assets/subpack/android.apk` 48,139,093 B、`windows.zip` 11,412,717 B",
     "无校验的 Google Drive 归档；`android.apk` 签名 `CN=Android Debug`",
     "Flutter BSD-3 + `flutter_assets/NOTICES.Z`（143 段 BSD-3 / 278 段 Apache-2.0 / 83 段 MIT）；`WebView2Loader.dll` 受微软条款约束",
     "移除"),
    ("`app/libs/ffmpeg-kit-local.aar` 15,264,401 B",
     "`libs.zip` 解包，无版本号/commit/上游哈希",
     "配置串 `--enable-version3 --enable-libtwolame --enable-libshine --enable-gnutls` 无 `--enable-gpl`；随包 38 份 `res/raw/license_*.txt`；`res/raw/source.txt` 自称 “GPL v3.0”",
     "归属待定，见第 2 节第 7 条"),
    ("`lib/arm64-v8a/liboperit_ripgrep.so` 3,599,272 B",
     "源码在本仓库 `tools/native_ripgrep`；本轮用同源码 + 同 `Cargo.lock` 自建并逐字节比对：功能导入面等价"
     "（54 个 `.dynsym` 条目、同一枚 JNI 导出、crate 源码字符串字面量覆盖集完全相同），字节不等价——差异由工具链版本解释"
     "（`ripgrep-source-rebuild.txt` §1–3）。随包产物内嵌 106 处他人用户目录 `C:\\Users\\12809\\…`（同文 §4）",
     "MIT OR Unlicense（上游 grep 系）+ LGPL-3.0（自有代码）；本仓库那个 crate 自身无 `license` 字段",
     "替换的技术前提已证；先加 `--remap-path-prefix` 并把 rustc/NDK 版本写进构建断言（同文 §5）"),
    ("`assets/operit_shell_exec` 21,496 B",
     "本仓库 `tools/shell_identity_launcher/` 自带源码",
     "LGPL-3.0-only（第 " + SECTION["LGPL-3.0-only"] + " 节正文）",
     "随 LGPL 一般义务"),
    ("`:terminal` → `ubuntu-noble-aarch64-pd-v4.18.0.tar.xz` 64,133,552 B（12,684 成员）",
     "与 `termux/proot-distro` v4.18.0 官方资产逐字节相同（两侧 SHA-256 `91acaa78…`）",
     "完整 Ubuntu 用户态混合许可（140 个包带 `debian/copyright`）+ Canonical 商标材料",
     "移除"),
    ("STT 资产 8 项（sherpa-ncnn ×7 + silero VAD）",
     "HF URL 钉死 commit + 字节数 + SHA-256 校验",
     "Apache-2.0（sherpa 7 项，卡片 `cardData.license` 实测）/ MIT（silero VAD）；属性文件里“许可未声明”的注解是错的",
     "移除（首版无语音）"),
    ("`assets/templates/**/aapt2-arm64-v8a` 4,706,040 B ×2",
     "仓库直接跟踪的 Android SDK 工具",
     "AOSP Apache-2.0，需随附 AOSP NOTICE",
     "首版禁用面"),
    ("`shower-server.jar` 1,133,303 B、`main.jar`、`setup_android_env.sh` ×2、`:terminal/setup_fake_sysdata.sh`",
     "上游仓库直接跟踪（自建）",
     "无独立授权文本，随仓库 LGPL-3.0",
     "首版禁用面"),
    ("字体：见第 8.1 节逐条归属",
     "审计产物条目清单 `evidence/M0/apk-font-entries.txt`；三个非 RenderX 来源本轮已单独定位（`:terminal` 子模块自带的 `res/font/`、缓存条目名全等检索命中的 exoplayer-ui AAR、仓库自身的 `assets/fonts/`），逐组见第 8.1 节",
     "**均无随包授权文本**。`roboto`/`poppins`/`jetbrains_mono` 通常分别属 Apache-2.0 / OFL / OFL，但那是**惯例而非本轮证据**，未取得文本前不写入声明",
     "逐家族取证或移除"),
]
w("| 载荷 | 来源证明 | 许可 | 状态 |")
w("|---|---|---|---|")
for a in assets:
    w("| " + " | ".join(a) + " |")
w()
w("### 8.1 字体条目归属")
w()
w(f"审计产物 `{sample['path']}` 的 `unzip -Z1` 实测共 {len(font_entries)} 个字体文件条目"
  "（`.ttf`/`.otf`/`.woff`/`.woff2`，条目清单 `apk-font-entries.txt`）。来源按下列方式逐组定位，不是推测："
  "RenderX 一支直接列举该 AAR 的 `res/font`；exoplayer 一支在 Gradle 缓存下全部 "
  f"{carrier_scan['archivesScanned']:,} 个 AAR/JAR 里按条目名做全等匹配，命中 "
  f"{len(carrier_scan['hits'][ROBOTO_ENTRY])} 处（`font-carrier-scan.json`）；"
  "`:terminal` 一支是子模块自带文件——同一条检索对它 0 命中，因为该字体不经构件缓存，只能按仓库路径归属；"
  "其余按 APK 条目路径前缀归属到对应构件的 jar assets。")
w()
w("| 组 | 条目数 | 进包路径前缀 | 载体 | 随包授权文本 |")
w("|---|---:|---|---|---|")
FONT_ORIGIN = {
    "renderx_res_font": ("`res/font/`", "`com.github.tech-pw:RenderX:1.0.0` 的 AAR 内 `res/font`（另含 `poppins_bold.xml`，它是 font-family 声明不是字体本体，故不在条目数里）",
                         "无——该构件本身无任何许可授予"),
    "mathjax": ("`assets/MathJax/fonts/HTML-CSS/TeX/`", "同一 RenderX AAR 的 `assets/MathJax/`（AAR 内唯一带许可字样的路径就是 `assets/MathJax/LICENSE`，那是 MathJax 自己的 MIT，不是 RenderX 对自己的授权）",
                "有 MathJax 的 MIT（第 " + SECTION["MIT"] + " 节正文），但**不覆盖字体本身**：这批是 TeX 字体派生物"),
    "jlatexmath": ("`assets/org/scilab/forge/jlatexmath/fonts/`", "`ru.noties:jlatexmath-android` 及两个字体包的 jar assets",
                   "随包构件声明 GPL-2.0-only（第 " + SECTION["GPL-2.0-only"] + " 节）；字体本身的上游授权（Computer Modern / Euler / TeX Gyre 系）未逐一取证"),
    "pdfbox": ("`assets/com/tom_roush/pdfbox/resources/ttf/`", "`com.tom-roush:pdfbox-android:2.0.27.0`",
               "POM 声明 Apache-2.0（第 " + SECTION["Apache-2.0"] + " 节）；Liberation 字体另有其上游授权惯例，未取证"),
    "material_symbols": ("`assets/fonts/`", "仓库自身跟踪的 `app/src/main/assets/fonts/material_symbols_rounded_static.woff2`",
                         "无——仓内该文件无授权文本，Google 素材惯例为 Apache-2.0/OFL，未取证"),
    "exoplayer": ("`res/font/roboto_medium_numbers.ttf`", "`com.google.android.exoplayer:exoplayer-ui:2.19.1` 的 AAR —— 缓存条目名全等检索的唯一命中（`" + CARRIER_FILE + "`，缓存路径段 SHA-1 前 16 位 `" + CARRIER_SHA + "`）",
                  "POM 声明 Apache-2.0（第 " + SECTION["Apache-2.0"] + " 节）；AAR 本体 526 条目里无 `LICENSE`/`NOTICE`/`COPYING`，字体条目也没有随身的授权文本"),
    "terminal": ("`res/font/jetbrains_mono_nerd_font_regular.ttf`", "`:terminal` 子模块自带 `terminal/src/main/res/font/`",
                 "无——子模块未附该字体授权文本；JetBrains Mono 上游惯例为 OFL-1.1，未取证"),
}
for grp in ["renderx_res_font", "mathjax", "jlatexmath", "pdfbox", "material_symbols", "exoplayer", "terminal"]:
    path, carrier, lic = FONT_ORIGIN[grp]
    w(f"| {grp} | {len(fonts[grp])} | {path} | {carrier} | {lic} |")
w()
w(f"合计 {sum(len(v) for v in fonts.values())} 条，与实测条目数一致。"
  "处置上分三档：随 RenderX 一并消失的两组（`renderx_res_font`、`mathjax`）随 M0-B09 的移除决策自动解决；"
  "随已定移除载荷消失的（`jlatexmath`、`pdfbox` 若 pdf 能力整体移除）；剩下 `material_symbols` 与 `terminal` 两支要在首版前各自补齐文本或换字体。")
w()

# ---------------------------------------------------------------- licence texts
w("## 9. 许可正文")
w()
w("以下正文按“去重后一份”附出。取用来源与指纹：")
w()
w("| 编号 | SPDX 标识 | 文件 | 字节 | SHA-256（前 16） | 取得来源 |")
w("|---:|---|---|---:|---|---|")
SRC = json.load(open(os.path.join(TEXTS, "sources.json"), encoding="utf-8"))
src_by_file = {e["file"]: e for e in SRC["texts"]}
for i, (spdx_id, (fname, url)) in enumerate(sorted(TEXT_FILES.items()), 1):
    path = os.path.join(TEXTS, fname)
    if not os.path.exists(path):
        raise SystemExit(f"missing licence text: {path}")
    rec = src_by_file.get(fname)
    if rec is None:
        raise SystemExit(f"licence text not listed in sources.json: {fname}")
    if rec["url"] != url or rec["bytes"] != os.path.getsize(path) or rec["sha256_16"] != sha16(path) \
            or rec["spdx_id"] != spdx_id:
        raise SystemExit(f"sources.json disagrees with this script for {fname}: {rec}")
    w(f"| {i} | {spdx_id} | `{fname}` | {os.path.getsize(path):,} | `{sha16(path)}` | {url} |")
agpl_lines = src_by_file["SPDX-AGPL-3.0-only.txt"]["lines"]
w()
w("取用说明两条（与 `licence-texts/sources.json` 对账，本表由该清单逐条校验后生成）：")
w()
w("1. `AGPL-3.0-only` 的正文取自 SPDX 的镜像（`gnu.org/licenses/agpl-3.0.txt` 在本机经代理三次连接失败，"
  f"curl 返回状态 000）。该镜像包含 `Preamble` 与 `How to Apply These Terms` 全部段落，共 {agpl_lines} 行；"
  "若负责人要求以 FSF 原始页面为准，需换网络重取并逐字节比对。")
w("2. `EDL-1.0` 与 `BouncyCastle` 不在 SPDX 许可列表内（`licenses.json` 740 项实测无这两个 id），"
  "故取自权利方页面并把 HTML 还原为纯文本；Eclipse 官方在该页把 EDL-1.0 的 SPDX 标识标为 `BSD-3-Clause`。")
w()
w("仅链接、不逐字附出的条款：")
w()
for k, v in LINK_ONLY.items():
    w(f"- **{k}** — {v}")
w()
for i, (spdx_id, (fname, url)) in enumerate(sorted(TEXT_FILES.items()), 1):
    body = open(os.path.join(TEXTS, fname), encoding="utf-8").read().rstrip("\n")
    w()
    w(f"### 9.{i} {spdx_id}")
    w()
    w(f"来源：{url}")
    w()
    w("```text")
    w(body)
    w("```")
w()

# ---------------------------------------------------------------- footer
w("## 10. 生成信息与边界")
w()
w(f"- 生成脚本：`evidence/M0/scripts/third_party_notices.py`（本文件由它整体重写）。")
w(f"- 原料：`build-manifest.json`、`gradle-dependency-licenses.json`、`native-dependency-licenses.json`、"
  f"`rust-crate-licenses.json`、`node-dependency-licenses.json`、`upstream-license-retrieval.json`、"
  f"`apk-font-entries.txt`、`font-carrier-scan.json`、`licence-texts/sources.json`。")
w(f"- 覆盖条目数：JVM {gradle['selected']} + 原生 {len(native)} + Rust {rc['lock_packages']} + Node {cl['count']} + 二进制/资产 {len(assets)} = "
  f"{gradle['selected'] + len(native) + rc['lock_packages'] + cl['count'] + len(assets)}。"
  f"第 8.1 节是第 8 节“字体”那一行的条目级展开（{sum(len(v) for v in fonts.values())} 个 APK 条目），不重复计入该求和。")
w("- 边界：本文件不主张任何构件“确实被链入产物”，除第 4 节标注了 dex 描述符命中数的少数高风险条目；"
  "dex 检索的覆盖面与方向性限制记录在 `gradle-dependency-licenses.md` §4 与 `upstream-license-retrieval.md` §1。")
w("- 边界：本文件不主张许可合规。第 2 节为空之前，任何“随包合规”表述都与实测不符。")
w()

out_path = sys.argv[1] if len(sys.argv) > 1 else os.path.join(REPO, "THIRD-PARTY-NOTICES.md")
text = "\n".join(L) + "\n"
with open(out_path, "w", encoding="utf-8", newline="\n") as fh:
    fh.write(text)
print(f"wrote {out_path}: {len(text.encode()):,} bytes, {text.count(chr(10))} lines, "
      f"{len(open(out_path,encoding='utf-8').read().splitlines())} read-back lines")
