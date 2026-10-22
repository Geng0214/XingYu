# M0-A05 许可审计脚本副本

本目录收录 **M0-A05 第三轮（依赖树四层）、第四轮（上游与自有 LICENSE 取证）与第五轮（声明成文、条款正文取证、字体条目归属）** 用到的脚本原文，也就是 `gradle-dependency-licenses.md` / `native-dependency-licenses.md` / `rust-dependency-licenses.md` / `node-dependency-licenses.md` / `upstream-license-retrieval.md` 与 `license-inventory.md` 后两节里那些数字的来源；末了两支（`ax_refs_and_tree.py`、`bundled_apk_vcs_info.py`）是第四轮之后补做的内嵌 APK 上游取证，对应 `upstream-license-retrieval.md` §8；再末三支（`licence_texts_sources.py`、`font_carrier_scan.py`、`third_party_notices.py`）是第五轮，对应 `license-inventory.md` §"第五轮"与 `THIRD-PARTY-NOTICES.md`。第二轮（内嵌分发物）当时用的是 `sha256sum`、`apksigner verify --print-certs`、`curl` 与 `unzip` 的一次性命令行，没有脚本可存，命令写在对应文档里——本轮起同类动作已有可重跑脚本。

实际执行时工作目录是 `C:/tmp`，中间产物也写在那里；本目录只是**执行过的原文副本**，未做事后整理或重写，因此保留了一批"同一件事的前一版"。登记在此是为了满足计划书对"本机可重跑"的要求，同时明确标出哪些版本已被取代、哪一版是反面记录。

## 重跑前提

```bash
# 构建产物与依赖缓存（只读）
APK:            C:/M0/A/app/build/outputs/apk/clone/app-clone.apk   # dex 检索类脚本
GRADLE_USER_HOME: C:/Users/15942/Desktop/Work/.gradle-m0            # 读 POM / 构件
# 工具链（Rust 侧）
CARGO_HOME=C:/Users/15942/dev/rust/cargo  RUSTUP_HOME=C:/Users/15942/dev/rust/rustup
# 内嵌 APK 取证那两支另需：SDK build-tools 36.0.0（aapt2 / apksigner）、JDK 21（apksigner.bat 读 JAVA_HOME）、
# 以及 PATH 上有 git、curl、unzip
# 网络：脚本内硬编码 http://127.0.0.1:7897，未走该代理时 Drive/GitHub/HF/registry.npmjs.org 会全部失败
```

第五轮那三支的入口条件与前几轮不同，值得单独记：

- 它们用 `os.path.dirname(os.path.abspath(__file__))` 自锚定，**在任何工作目录下都能跑**，不依赖 `C:/tmp`；`third_party_notices.py` 只读 `evidence/M0/` 里的 JSON 与 `licence-texts/`，`font_carrier_scan.py` 只读 `GRADLE_USER_HOME/caches`（未设置时回落到上面那个默认值），`licence_texts_sources.py` **完全不联网**——它就地计算已落盘文件的指纹，当初的取回动作是 curl 命令行。
- 出正式数字的链条要在本机重建，还需要被审计 APK 的条目表。字体条目清单的再生成命令（本轮实测与 `../apk-font-entries.txt` 逐行相等，83 行）：
  ```bash
  python -c 'import zipfile,sys; z=zipfile.ZipFile("C:/M0/A/app/build/outputs/apk/clone/app-clone.apk");\
  print("\n".join(sorted(n for n in z.namelist() if n.lower().endswith((".ttf",".woff",".woff2",".otf")))))' \
    > evidence/M0/apk-font-entries.txt
  ```
  该筛法是"按扩展名收"，不是"按 RenderX/字体目录收"，所以它会一并收进 `res/font/*.ttf` 与 `assets/com/tom_roush/pdfbox/…`；分类由 `third_party_notices.py` 的 `font_group()` 负责，遇到归不了类的条目直接失败。
- 运行 `third_party_notices.py` 前必须先跑 `licence_texts_sources.py` 与 `font_carrier_scan.py`：它逐文件核对 `sources.json` 记录的字节数与 SHA-256 前 16 位，任何一次改动许可正文却没更新出处清单，都会让它拒绝出文而不是静默出一份对不上账的声明。

密钥零依赖：这批脚本不读任何 Key，也不需要凭据（`api.github.com` 走的是未认证请求，因此有配额上限）。

## 出正式数字的那一版

| 脚本 | 产出 | 对应证据文件 |
|---|---|---|
| `dep_lic.py` | `gradle_licenses.json`（396 选中坐标 + POM 许可） | `gradle-dependency-licenses.json` 的原料 |
| `native_lic.py` | `native_licenses.json`（11 个 FetchContent + `assets/js`） | `native-dependency-licenses.*` |
| `rust_merge2.py` | 合并后的 Rust 层全表 | `rust-crate-licenses.json` |
| `rust_tree.py` / `rust_so.py` | `cargo tree` 依赖图划分 / `.so` 内嵌源码路径命中 | `rust-dependency-licenses.md` §3–§4 |
| `npm_closure4.py` | `npm_mcp_closure.json`（92 包锁文件闭包） | `node-dependency-licenses.*` |
| `npm_lic.py` → `npm_reclass.py` | `npm_licenses.json`（registry 完整性比对 + 许可文本分类） | 同上 §2–§3 |
| `npm_attrib.py` | `npm_attribution.json`（bundle 内特征串归属） | 同上 §4 |
| `asset_urls.py` | `asset_remote_urls.json`（assets 远端 `<script src>` 扫描） | 同上 §7（jsdelivr ECharts 一条） |
| `dex_table2.py` | `dex_table.json`（11 个锚定 needle 的 dex 命中数） | `gradle-dependency-licenses.md` §4、`upstream-license-retrieval.json` |
| `itextg_ctx.py` | `itextg_ctx.json`（1,814 与 1,808 的 6 处差额逐条定位） | `upstream-license-retrieval.md` §1 |
| `upstream_final.py` / `upstream_extra.py` | 上游 LICENSE 探测（libsu / axml / RenderX） | `upstream-license-retrieval.json` |
| `renderx_tag.py` / `renderx_readme.py` | tag 根目录列表、`readme.md` 许可词检索 | `upstream-license-retrieval.md` §4 第 4–5 步 |
| `merge_upstream.py` | 把上述几份合成本目录的 JSON | `upstream-license-retrieval.json` |
| `xcheck3.py` | 应用内 68 条与 POM 的手工对照表 | `gradle-dependency-licenses.md` §5 |
| `own_license_compare.py` | 两份自有 `LICENSE` 与 FSF 官方 `lgpl-3.0.txt` 的字节级＋词级比对（输出 `own_license_compare.json`） | `license-inventory.md` §"第四轮复核"末 |
| `ax_refs_and_tree.py` | `ax_refs_and_tree.json`：`AAswordman/OperitAccessbility` 的 `git ls-remote`（分支/tag 真伪）、codeload 整包归档 78 条目全树文件名探测、ref 语义（raw 200 vs tree 302 vs 未知分支 404）、atom 及正向对照、源码里的版本字段 | `upstream-license-retrieval.md` §8 |
| `bundled_apk_vcs_info.py` | `bundled_apk_vcs_info.json`：三个内嵌 APK 的 `META-INF/version-control-info.textproto`、`aapt2 dump badging`、`apksigner --print-certs` | 同上；亦支撑 §逐项结论的 accessibility / desktop 两行 |
| `licence_texts_sources.py` | `../licence-texts/sources.json`：17 份许可条款正文的逐文件出处（URL / 通道 / 字节数 / SHA-256 前 16 位 / 行数 / 取回时间）+ 3 条反面结果 | `THIRD-PARTY-NOTICES.md` §9、`license-inventory.md` §"第五轮" |
| `font_carrier_scan.py` | `../font-carrier-scan.json`：在依赖缓存 1,263 个归档里按 `zipfile.namelist()` 条目名全等匹配非 RenderX 字体的载体 | `THIRD-PARTY-NOTICES.md` §8.1 |
| `third_party_notices.py` | `operit/THIRD-PARTY-NOTICES.md`（全文 10 节）：把四层 JSON、`licence-texts/`、`apk-font-entries.txt`、`font-carrier-scan.json` 合成可交付声明 | 该声明自身（§1 口径、§2 处置项、§10 边界） |

`third_party_notices.py` 与其余脚本有一点不同：它是**唯一会写出仓库文件**的一支（写 `operit/THIRD-PARTY-NOTICES.md`），其余只写 JSON 或 stdout。它不重测任何数据，且刻意不留回退——映射不上 POM 许可名、出现无许可声明又无解释的 crate、来源不明的字体条目、`sources.json` 与磁盘指纹不符，一律 `raise SystemExit` 停下而不是填一句"未知"。因此改这份声明的正确方式是改上游 JSON 或改本脚本的分类表，直接改 markdown 正文会在下次运行时被覆盖。**它的字节数与行数由 stdout 打印，不在任何证据文档里复写**——本轮就发生过一次：这组数被抄进六份文档的九个位置，之后改一句措辞就得跟着改九处，没跟上就是假数。

`ax_refs_and_tree.py` 与 `bundled_apk_vcs_info.py` 和其余脚本也有一点不同：输出直接写在本目录（`os.path.dirname(__file__)`），只有 `ax_refs_and_tree.py` 下载的归档落在 `C:/tmp`。第五轮那三支写到别处——`licence_texts_sources.py` 写 `../licence-texts/sources.json`，`font_carrier_scan.py` 与 `third_party_notices.py` 分别写 `../font-carrier-scan.json` 和仓库根的声明。

## 已被取代的版本与反面记录

- `dep_lic2.py`、`dep_lic3.py`：只改输出格式，不改结论；保留是因为它们的 stdout 是初版表格的原文。
- `rust_lic.py`/`rust_lic2.py`/`rust_merge.py`/`rust_probe.py`：`rust_merge2.py` 之前的中间态。
- `npm_closure.py`/`2`/`3`、`npm_probe.py`/`2`/`3`、`npm_analyze.py`、`npm_doc.py`、`npm_stale.py`、`npm_licenses_smoke*`（未复制，仅 4 包冒烟）：Node 层的迭代过程，最终版见上表。
- `upstream_lic.py`/`2`/`3`/`4`、`jitpack_link.py`、`renderx_link.py`/`2`：联网取证的前几版（含被配额打断的那次）。
- **`xcheck2.py` 是反面记录，不是工具**：名称↔坐标的自动模糊匹配，会把 junrar 的 "The Unlicense" 与 "UnRar License" 判成一致，也会把 AndroidX 的 token 匹配到任意含 `core`/`ktx` 的构件。它的输出 `xcheck_report.json` 未被任何证据文件采用。保留它是为了说明"为什么第 5 节的 68 条是手工核对的"。
- `xcheck.py`：`xcheck3.py` 的前一版（条目数按 `OpenSourceLibrary(` 数，得 69；正确计法见 `gradle-dependency-licenses.md` §5）。

## 已知不严谨处（不要把这些脚本当模板）

1. 路径与代理地址硬编码，换机器即失效——与 `docs/env/build-env.md` §6 记的那类"换机器即踩"是同一性质。
2. 中间产物写 `C:/tmp`，重跑会覆盖，且 `/tmp` 不是仓库内容；本目录的副本才是持久记录，`npm_bundle_probe.json` 是被误一起复制进来的中间产物，保留以对应 `node-dependency-licenses.md` §4 的引用。
3. `dex_table2.py` 里同时跑锚定与非锚定 needle，两者的差额要靠 `itextg_ctx.py` 单独归因——第一版没有这一步，所以留下过"1,814 与 1,808 到底哪个算数"的悬置表述。
4. `npm_reclass.py` 的许可文本分类器第一版把 `fast-uri` 判成 BSD-2 系（其第三条是非标准改写），修正后才有 92/92 一致；任何复用这个分类器的新表都要连同判据一起读。
5. **许可文本不要做行级比对**。`own_license_compare.py` 同时留了行级对齐（`canonical_lines_aligned`）和词级 diff 两套结果，行级那套给出过"17/165 行一致"这种数字，而它只反映"文件开头多一个空行导致后文整体错位"。真正的判据是 `token_diff.canonical_is_in_order_subsequence` 与 `replaced_or_deleted_groups`。本目录的 `own_license_compare.json` 是修正后的输出。
6. **只探候选文件名的上游脚本不能单独定案**。`upstream_final.py`/`upstream_extra.py`/`renderx_*.py` 都是"按常见拼写试一遍"，`ax_refs_and_tree.py` 换成 `git ls-remote` ＋ codeload 整包列举之后，同一件事立刻翻出两个假信号：raw 端点对已改名的 `master` 仍回 200（会被读成"存在这个分支"），以及全树唯一命中的 Apache 头其实在 `gradlew` 里（若只 grep 文件内容而不看是哪个文件，会误判项目已获 Apache-2.0 授权）。前一类脚本保留是为可追溯，但**新做上游取证请从 `ax_refs_and_tree.py` 的通道起手**。
7. **`sources.json` 的 `retrieved_at_utc` 是文件 mtime，不是取回日志**。`licence_texts_sources.py` 不联网，它用落盘时间冒充取回时间，因此重新 clone 或复制目录会把这 17 个时间戳全部改写，而字节数与 SHA-256 不变——即该字段是这清单里唯一不可信的一项，凭证只在 `url` + `sha256_16` 两栏。
8. **`font_carrier_scan.py` 的匹配是"条目名全等"，只能证有不能证无**。它在 1,263 个归档里给 `roboto_medium_numbers.ttf` 找到唯一载体（exoplayer-ui AAR），给 `jetbrains_mono_nerd_font_regular.ttf` 找到 0 个。0 命中本身不构成结论，所以那一格另做了正向核对：`find` 实测该文件就在子模块工作树 `terminal/src/main/res/font/` 下。剩下的盲区是"资源合并管道是否可能从别处产出同名条目"——未验证，因为 `:terminal` 的 `res/font` 只有这一个条目，重叠概率低但没被排除。
