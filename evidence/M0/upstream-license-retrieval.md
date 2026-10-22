M0-A05 上游取证轮：5 个"本机无出处"构件的联网取证、与仓库自维护许可界面的对账，以及补做的 `accessibility.apk` 上游取证

测量日期：2026-09-19
配合 `gradle-dependency-licenses.md`（第一层，JVM 依赖树）阅读。那一轮把 3 组构件标成"许可无法确定"，理由是 POM 无 `<licenses>` 且构件内无许可文本——那只是**本机不可证**，不是上游无授权。本轮联网补的就是这一段。机器可读全表：`upstream-license-retrieval.json`。

## 1. 方法

```bash
# 走本机代理 127.0.0.1:7897；raw 与 HTML 通道，不用 api.github.com
curl -x http://127.0.0.1:7897 https://raw.githubusercontent.com/topjohnwu/libsu/6.0.0/LICENSE
curl -x http://127.0.0.1:7897 https://github.com/tech-pw/RenderX/tree/refs/tags/1.0.0
curl -x http://127.0.0.1:7897 https://github.com/tech-pw/RenderX/commits/main/LICENSE.atom
curl -x http://127.0.0.1:7897 https://jitpack.io/com/github/tech-pw/RenderX/1.0.0/build.log
# 补做 accessibility 时另加两条通道：ref 真伪用 git 直连，全树文件名用整包归档
git -c http.proxy=http://127.0.0.1:7897 ls-remote --heads --tags https://github.com/AAswordman/OperitAccessbility
curl -x http://127.0.0.1:7897 -L -o main.tar.gz https://codeload.github.com/AAswordman/OperitAccessbility/tar.gz/refs/heads/main
# 构件侧：直接读 $GRADLE_USER_HOME/caches/modules-2/files-2.1/<group>/<name>/<version>/<sha1>/<file>.pom 的 <licenses> 全文
# 第五轮补两条通道：
#   1) 条目归属——在缓存下全部 1,263 个 .aar/.jar 里按条目名做全等匹配（zipfile.namelist，不猜文件名）：
#      res/font/roboto_medium_numbers.ttf → 唯一命中 exoplayer-ui-2.19.1.aar；
#      res/font/jetbrains_mono_nerd_font_regular.ttf → 0 命中（它是 :terminal 子模块自带文件，不经缓存）。
#      可重跑：scripts/font_carrier_scan.py，结果落在 font-carrier-scan.json
#   2) 条款正文取回：curl 到 licence-texts/ 并就地算字节数与 SHA-256（scripts/licence_texts_sources.py → sources.json）。
#      其中 AGPL-3.0 一份改自 SPDX 镜像——gnu.org 那份在本机经代理三次 curl 均状态 000。
#   3) 取条款正文时 ref 一律用被分发版本自己的 tag，不用默认分支；三行判据：
for ref in v7.5.5 7.5.5 master; do printf "%-9s " "$ref"; \
  curl -s -o /dev/null -w '%{http_code} %{size_download}\n' -x http://127.0.0.1:7897 \
  "https://raw.githubusercontent.com/junrar/junrar/$ref/LICENSE"; done   # → 200 1861 / 404 14 / 200 1861
git -c http.proxy=http://127.0.0.1:7897 ls-remote --tags https://github.com/junrar/junrar   # v7.5.5 = dabca284…
curl -s -x http://127.0.0.1:7897 https://github.com/junrar/junrar/commits/master/LICENSE.atom | grep -o '<updated>[^<]*'   # 恰 2 个 = 1 条 feed 级 + 1 条提交级（Grit::Commit/60f17a1f49b91d70…，2020-07-18T08:47:18Z）⇒ 该文件历史只有一次提交
#      三者齐全才能把"上游 LICENSE"记成"对被分发版本有效"；tag 名带不带 v 前缀要试，别按惯例假定（本项目带 v）。
#      注意 curl 只在循环里逐 ref 调用：写成 `-o /dev/null URL{a,b,c}` 时 -o 只作用于第一个 URL，
#      后两个的正文会直接打到 stdout（本轮实测踩过）。
```

过程中的坑，都值得记下来：

- **`api.github.com` 在取证中途耗尽未认证配额**（`403 API rate limit exceeded for 23.172.200.83`，即代理出口 IP）。所以仓库元数据改从 `raw.githubusercontent.com` 与 `github.com` 的 HTML/atom 通道取——tag 的根目录列表用 `/tree/refs/tags/<tag>` 内嵌的 JSON，许可文件的历史用 `/commits/main/LICENSE.atom`。这些通道给出的证据不弱于 API，但**必须先承认 API 那部分失败了**，否则读者会以为 `repo_api` 为空是"仓库不存在"。
- **文件名大小写**：RenderX 的说明文件叫 `readme.md`（全小写），`README.md` 在两个 ref 上都是 404。只按惯例大小写探测会得出"没有 README"的错误结论。
- **按候选文件名探测不足以证明"没有授权文本"**。第 8 节改用完整归档列举（`tar tzf`）与 `git ls-remote`，既发现了 wrapper 里的 Apache 头，也发现 `master` 根本不是分支。
- **POM 要读完整的 `<licenses>` 块，不能取第一条**。第 3 节的更正全部来自这里。

链入证据仍是同一套 dex 类型描述符计数（`clone` 构建 `isMinifyEnabled = false`，描述符不被改名）。本轮把 needle 统一成锚定形式 `L<pkg>/`，所以与上一版的非锚定计数略有差别（`com/itextpdf/text/` 非锚定 1,814 → 锚定 `Lcom/itextpdf/text/` 1,808）；两种都在 `upstream-license-retrieval.json` 的 `dex_needles_anchored` 里。**这 6 处差额本轮逐一定位过**，全部在 `classes39.dex` 的字符串池里、前一个字节是 ULEB128 字符串长度而非 `L`，内容是 itextg 运行时用来加载资源的**路径字面量**：`com/itextpdf/text/l10n/error/`、`com/itextpdf/text/pdf/fonts/`、`com/itextpdf/text/pdf/fonts/cmaps/`、`…/cmaps/cjk_registry.properties`、`…/glyphlist.txt`、`com/itextpdf/text/pdf/hyphenation/hyph/`。因此它不是"漏了 6 个类"，而是"6 处不是类型描述符"，itextg 的真实类链入面以 1,808 为准；复现脚本与逐条偏移见 `scripts/itextg_ctx.py` / `scripts/itextg_ctx.json`。

## 2. 三个构件的结论

| 构件 | 结论 | 关键证据 |
|---|---|---|
| `com.github.topjohnwu.libsu:core/nio/service:6.0.0` | **Apache-2.0，在钉住的 tag 上可证** | `raw/.../libsu/6.0.0/LICENSE` = 11,357 字节，sha256_16 `c71d239df91726fc`，正文 "Apache License Version 2.0"；该文件自 2018-01-25 唯一一次提交后未变 |
| `com.github.Sable:axml:2.0.0` | 上游现为 Apache-2.0，**但分发的构件上没有** | 2.0.0 tag 的探测（`LICENSE`/`LICENSE.txt`/`COPYING` 等）全部 404；`master` 的 `LICENSE.txt` = 11,358 字节 Apache-2.0（`cfc7749b96f63bd3`）晚于该 tag；**上游唯一 tag 就是 2.0.0，没有可升级的带许可发布版** |
| `com.github.tech-pw:RenderX:1.0.0` | **分发的构件没有任何许可授予**；上游唯一一份许可是三天后在 main 上加的 GPL-3.0 | 见第 4 节 |

libsu 一条可以直接结案：界面写的 Apache-2.0 与实测一致，POM 无声明只是 jitpack 的产物形态。axml 一条不能这样收尾——Apache-2.0 是**当前 master** 的状态，而我们发出去的是 2.0.0 的 jar；要么向作者取得对 2.0.0 的追认，要么改用 master 构建，要么整链移除。axml 与 `subpack` 重打包链绑在一起，代价高于 libsu，且它锚定 needle `Lpxb/android/` 在 dex 里有 81 处命中，不是删一行能解决的。

## 3. 对上一轮分类的更正（读完整 `<licenses>` 块之后）

| 构件 | 上一轮 | 实际 POM 原文 | 影响 |
|---|---|---|---|
| `org.checkerframework:checker-compat-qual:2.5.3` | 判为 copyleft | **双许可**：`GNU General Public License, version 2 (GPL2), with the classpath exception` + `The MIT License` | 可选 MIT，**不构成 copyleft 义务**。原判定是"只取第一条 `<license>`"的脚本缺陷 |
| `com.itextpdf:itextg:5.5.10` | copyleft（AGPL） | `<licenses>` 里**只有** AGPL v3 一条 | 确认；界面上写的 MPL/LGPL 在此构件的 POM 里不存在 |
| `ru.noties:jlatexmath-android:0.2.0` | copyleft（GPL-2.0） | 只有 `GNU General Public License, version 2`，**无 classpath exception 字样** | 确认，且界面凭"上游一贯写法"加上的例外条款没有依据 |
| `io.objectbox:objectbox-android:5.3.0` | permissive | 三条并列：`ObjectBox Binary Licence`（含 native runtime）、Apache-2.0（flatbuffers）、OpenLDAP Public License 2.8（LMDB） | 界面只写 Apache-2.0 不完整；随包的 `libobjectbox-jni.so` 走的是非开源的二进制许可 |
| `com.github.junrar:junrar:7.5.5` | other | 单条 `UnRar License` | 界面写 "The Unlicense" = 把带使用限制的许可说成公有领域 |
| `com.joaomgcd:taskerpluginlibrary:0.4.10` | other | 单条 `Tasker Plugin Sample License` | 界面写 Apache-2.0 不符 |
| `com.google.mlkit:*`、`com.google.android.gms:play-services-mlkit-*` | other | `ML Kit Terms of Service` / `Android Software Development Kit License`（15 + 4 个构件） | 界面写 Apache-2.0 不符 |

因此 `gradle-dependency-licenses.md` 第 2 节的"5 copyleft 系"应读作：**4 个无可选弱许可的 copyleft 构件**（itextg + jlatexmath 及其两个字体包）+ 1 个双许可可选 MIT 的 checker-compat-qual；联网取证后再加 **RenderX（无授予，上游现为 GPL-3.0）**。

## 4. RenderX：一条完整可复核的证据链

这一段之所以值得单独写，是因为它是本轮唯一"从依赖声明一路推到分发物字节"的闭环。

1. **声明点**：`app/build.gradle.kts:755` `implementation(libs.renderx) // RenderX library for LaTeX rendering`，版本在 `gradle/libs.versions.toml:167`，坐标 `com.github.tech-pw:RenderX:1.0.0`。
2. **构件身份**：本机缓存 `RenderX-1.0.0.aar` 1,746,805 字节，SHA-1 `d1d4c451eaf5a7437d430efb8a0dcdaa6dd64464`（与该缓存目录名一致，即 Gradle 校验和自洽）。
3. **构建溯源**：`https://jitpack.io/com/github/tech-pw/RenderX/1.0.0/build.log` 记录该构件产自提交 `de7d1e33da264f9969a653a4bd6c6eec7cf143d9`，`git describe` = `1.0.0-0-gde7d1e3`（恰在 tag 上、无额外提交），提交信息 "Renamed the Library name to RenderX"，作者 Rahul Jindal，日期 2024-02-23，构建起始 2024-02-23 12:39:33 UTC。
4. **那个提交里没有许可**：14 个根条目（`.gitignore`、`app/`、`build.gradle.kts`、`gradle/`、`gradle.properties`、`gradlew`、`gradlew.bat`、`jitpack.yaml`、`readme.md`、`renderX/`、`screenshots/`、`settings.gradle.kts`、`.idea/`、`gradle/wrapper`）里没有任何许可文件名；`LICENSE`、`LICENSE.md`、`LICENSE.txt`、`LICENCE`、`LICENCE.md`、`COPYING`、`COPYING.md`、`UNLICENSE`、`NOTICE`、`LICENSE-APACHE`、`LICENSE-MIT`、`renderX/LICENSE`、`renderX/license`、`app/LICENSE` 共 13 个拼写在 raw 上全部 404；`renderX/build.gradle.kts`（2,871 字节，`36b5317fb8322ea3`）、根 `build.gradle.kts`（343 字节，`1f8c261bd9e892ac`）、`jitpack.yaml`（21 字节，`e33c722b5eddcaee`）、`gradle.properties`（1,380 字节，`eef51f412da5bd49`）四个文件在 `licen|copyright|scm|url` 这个大小写不敏感模式下**零命中**，所以 jitpack 生成的 POM 里没有 `<licenses>` 不是打包丢字段，而是上游工程从未声明；`readme.md` 里 MIT/Apache/GPL/AGPL/BSD/Unlicense 六个词 0 命中。这四个文件是可能无授权的上游工程源码，本轮只用字节数＋SHA-256 前 16 位＋命中数固定结论，**不把它们抄进本仓库**（取回命令见 `upstream-license-retrieval.json` 的 `build_files_licence_scan.why_sources_not_stored`）。
5. **唯一的许可出现在三天后**：`LICENSE` 文件全历史只有 1 次提交，`88423982e36cce638340b6c232b1e55679f2c091`，"Create LICENSE"，2024-02-26T06:32:08Z，35,149 字节，sha256_16 `3972dc9744f6499f`，正文指纹为 GPL-3.0。仓库**总共只有一个 tag，就是 1.0.0**，所以也不存在"上游后来给 1.0.0 补了许可并重新发布"这条路。
6. **AAR 自己也承认这点**：219 个条目里唯一带许可字样的路径是 `assets/MathJax/LICENSE`——那是它捆绑的 MathJax 的 MIT 文本，不是 RenderX 对自身的授权。

两种解读都要求处置：按"无授予即保留所有权利"，我们没有再分发该 `.aar` 的权利；按"作者的 repo 级 GPL-3.0 覆盖全史"，它是 GPL-3.0，而界面写着 MIT。**无论取哪种解读，`OpenSourceLicenses.kt:113` 的 "MIT" 都是错误声明**——上游从未出现过任何 MIT 文本。

## 5. 删这一行会带走什么

| 项 | 实测 |
|---|---|
| Kotlin/Java 侧引用 | **0**（`rg -e 'live\.pw' -e 'RenderX' -e 'MathView'` 在 `*.kt`/`*.java` 里只命中界面里那个字符串常量） |
| 仓库自身的 MathJax / gilroy / balsamiqsans 素材 | 0 个文件名命中，即这些条目只可能来自该 AAR |
| dex 类命中 | `Llive/pw/render` 72（classes15、classes41），其中 `Llive/pw/renderX/mathJaxView/` 6 |
| APK 内 MathJax 资产 | 150 条目、1,434,800 字节（解压后） |
| APK 内字体 | AAR 的 12 个 `res/font` 全部进包：`balsamiqsans_bold/regular.ttf`、`gilroy_bold/medium/regular/semi_bold.otf`、`poppins_bold.xml`、`roboto_bold_font/light/medium_font/regular/thin.ttf`；APK 另有 2 个字体来自仓库自身（**第五轮订正：这一句错。`res/font` 里的 12 个条目中 `poppins_bold.xml` 是 font-family 声明、不是字体本体，即 RenderX 带来的是 11 个字体二进制；APK 里其余 2 个 `res/font` 字体既不来自 RenderX 也不来自仓库自身——`roboto_medium_numbers.ttf` 出自 `com.google.android.exoplayer:exoplayer-ui:2.19.1` 的 AAR（在 Gradle 缓存里按条目名逐个 AAR/JAR 检索，唯一命中），`jetbrains_mono_nerd_font_regular.ttf` 是 `:terminal` 子模块自带的 `terminal/src/main/res/font/`。仓库自身跟踪的那一份在 `assets/fonts/material_symbols_rounded_static.woff2`，不在 `res/font`。APK 全部字体条目实测 83 个，逐组归属见 `THIRD-PARTY-NOTICES.md` §8.1，条目清单 `apk-font-entries.txt`**） |
| AAR 自带的清单声明 | `<activity android:name="live.pw.renderX.LatexView" android:exported="true">` + MAIN/LAUNCHER intent-filter |
| 该 activity 的实现类 | dex 里 `Llive/pw/renderX/LatexView;` 命中 **0**，AAR 的 26 个 class 里也没有（`LatexView` 只以 `ThemeKt$LatexViewTheme$…` 的函数名片段出现） |
| 仓库侧的补救 | `app/src/main/AndroidManifest.xml:112-114` 已用 `tools:node="remove"` 删掉那个 activity |

也就是说：**删 1 行依赖 + 1 行版本目录，零源码改动**，同时消掉一个无许可（或 GPL-3.0）构件、150 个 MathJax 资产和 12 个 `res/font` 条目（其中 11 个是字体本体）。那个 `tools:node="remove"` 反过来说明上游 AAR 会把一个 exported 的 LAUNCHER activity 混进合并清单——现在靠手工压制；这是包卫生问题，不只是许可问题。

需要负责人判断的点：项目已有 Apache/GPL 系的 LaTeX 路径（`ru.noties:jlatexmath-android`，5 处真实调用），所以 RenderX 这条路即使删掉也不等于失去公式渲染能力；但公式渲染是**界面可见行为**，删除前需要确认现网没有依赖 `live.pw.renderX.*` 的 Compose 调用点（本轮实测为 0）。

字体是另一件事：这 11 个字体二进制随一个无许可构件进来，且 AAR 内没有任何字体授权文本；同一个 AAR 的 `assets/MathJax/fonts/HTML-CSS/TeX/` 下还带 22 个 TeX 派生字体（第五轮实测，见 §8.1 的分组）。`gilroy_*` 尤其要单独看待——它不像 OFL/Apache 系字体那样自带授权，而 AAR 也没给出任何说明。删除 RenderX 依赖会一并带走这 33 个条目；若首版仍需要这些字形，必须另行为每个字体取得授权。

## 6. 与自维护许可界面的对账

`app/src/main/java/com/ai/assistance/operit/ui/features/about/screens/OpenSourceLicenses.kt` 硬编码 68 条向用户展示的"名称 / 许可 / 链接"。逐条与 POM 原文（第 3 节）和联网取证（第 2、4 节）对账，**确认 8 条不一致**，明细已写入 `gradle-dependency-licenses.md` 第 5 节；本轮的变化是：`RenderX` 从"界面写了但本机无出处"升级为"界面写了且已被证伪"（计入第 8 条），`libsu` 一条转为一致，`axml` 一条仍与被分发构件不对应。

一条方法上的坦白：我先做过一版名称↔坐标的自动模糊匹配，结果**不能用**——它把 junrar 的 "The Unlicense" 与 "UnRar License" 判成一致，也会把 AndroidX 的 token 匹配到任意含 `core`/`ktx` 的构件。第 3 节与本节的每一条都是手工核对 POM 原文得到的，自动匹配脚本只留在 `scripts/xcheck2.py` 作为反面记录，未采用的报告在 `scripts/` 同名可查。

这个界面还有第二类问题不是"写错"而是"没写"：Rust 层的 ripgrep 与 32 个运行期 crate、JS 资产层的 terser（含其内嵌的 `@jridgewell/source-map` 与 `acorn`）、Node 层的 92 个包、`subpack` 里的 Flutter NOTICES、以及 `assets/MathJax/LICENSE` 背后的 MathJax 本身，全部没有条目。**因此 `THIRD-PARTY-NOTICES` 必须从四层实测数据生成，不能以这个界面为原料。**

## 7. 自有许可文本（同一轮附带实测）

审计别人家的 LICENSE 时，顺手把自己仓库那两份也做到了字节级。脚本 `scripts/own_license_compare.py`，输出 `scripts/own_license_compare.json`，结论也在 `license-inventory.md` §"2026-09-19 第四轮复核"末：

- 参照物是 FSF 官方 `https://www.gnu.org/licenses/lgpl-3.0.txt`：7,652 字节、165 行、SHA-256 前 16 位 `e3a994d82e644b03`。它本身就只到 §6"Revised Versions…"，对 GPL-3.0 是**援引而非内嵌**，所以"文本里没有 GPL 全文"不能算上游缺陷。
- `LICENSE`（主仓库）：7,817 字节 = 7,652 + 165 个 CR，**与官方文本逐字节相同**，词级 diff 0 处改动。只是行尾是 CRLF。
- `terminal/LICENSE`：10,296 字节、211 行、SHA-256 前 16 位 `40c41f5f4ed9e2f2`。官方 1,234 个词**全部按序出现，替换/删除 0 组**，只有 3 处插入：中文标题与"版权所有 (C) 2023-2024 Operit AI 项目团队"、正文末一个多余的 `-` 词、以及 LGPL 正文之后另接的 GPL-3.0 标题＋序言开头——然后用一句 `[The full text of the GNU General Public License v3.0 follows, but is omitted for brevity. …]` 把 GPL 正文截掉。**所以它是 LGPL-3.0 且未被改写，但作为"可交付的条款文本"不完整。**
- 方法教训：先前用行级对齐得出"terminal 只有 17/165 行与官方一致"，据此几乎写下"正文被重新排版过"。那个数字是假的——文件开头一个空行的偏移就会让后文逐行错位。许可文本要么比字节，要么比词序列（`canonical_is_in_order_subsequence`），不能按行比。
- 动作：`THIRD-PARTY-NOTICES` 与随源码的条款正文一律取 FSF 官方 LGPL-3.0 与 GPL-3.0 全文，上游那两个文件只作出处与版权声明保留。

## 8. `accessibility.apk` 的上游取证（第四轮之后补做，方法同 §4）

随包 `assets/accessibility.apk` 是 2,763,980 字节、版本 1.5，源码仓库为 `AAswordman/OperitAccessbility`。第二轮只查到"main/master 无 `LICENSE`、README 挂着 Apache 2.0 徽章"，本轮除了 §4 那套 ref 探测，另加两条通道（`git ls-remote` 与整包归档列举），结论因此更强一处、也更需要订正一处。

**仓库状态（`git ls-remote` 直连，最硬的通道）**：refs 只有一条 `refs/heads/main = 1221cf08c40f48ee5dfa68d3fc2c5aacbbf01b6a`，**无 tag、无 release**。所以第二轮那句"main/master 均无 LICENSE"里的 `master` 是个假分支：`raw.githubusercontent.com/…/master/README.md` 返回 200 且字节数与 `main` 完全相同（1,957），但同一位置的未知分支名（`definitely-not-a-branch-xyz-000`）返回 404，`github.com/…/tree/master` 是 **302 跳到 `tree/main`**，`commits/master/README.md.atom` 是 404。合理的解释是默认分支曾从 `master` 改名到 `main`，raw 端点保留了旧名的读取兼容。**方法订正：判断分支是否存在不能用 raw 端点的 200，要用 `git ls-remote` 或 tree 页面是否被重定向。**

**授权文本：整棵树为零。** 取 `codeload.github.com/…/tar.gz/refs/heads/main` 的**完整归档**（79,038 字节、SHA-256 前 16 位 `51b326fc767f42b8`、78 个条目）逐条看目录，而不是按文件名猜：根目录只有 `.gitignore`、`README.md`、`build.gradle.kts`、`gradle.properties`、`gradlew`、`gradlew.bat`、`settings.gradle.kts` 和 `app/`、`docs/`、`gradle/`、`.idea/` 四个目录。全树对 `licen[sc]e|copying|notice|许可` 的名字匹配 **0 命中**。文件历史侧也一致：`commits/main/LICENSE.atom` = 404，而正向对照 `commits/main/README.md.atom` = 200 且 3 条 entry，说明该端点工作正常、`LICENSE` 从未在 `main` 上被提交过（不是被删）。

**全树唯一的 Apache-2.0 文本是 Gradle wrapper 自己的**：`gradlew:4-16` 与 `gradlew.bat:2-14` 是 Apache 标准的 `Copyright 2015 the original author or authors` 头，那是 wrapper 脚本的许可，**不覆盖那 6 个 `.kt` 文件（5 个主源码 + 1 个测试）、`AndroidManifest.xml` 或 AIDL**——这些文件里没有任何 `Copyright`/`SPDX` 头（rg 全树确认）。

**关于项目本身的唯一许可信号**是 README 第 5 行的一枚徽章图片：`![许可证](https://img.shields.io/badge/许可证-Apache%202.0-蓝色.svg)`。徽章是一张 SVG 的 URL，没有任何条款正文、没有版权声明、没有贡献者授权。**徽章不构成可核验授权**这条判断维持不变，但现在有了整棵树的支撑，而不只是"六个候选文件名都 404"。

**版本对不上，但差在哪个方向本轮实测清楚了。** 先记下这条通道：AGP 会把构建时的 git 元数据写进产物 `META-INF/version-control-info.textproto`，`unzip -p <apk> META-INF/version-control-info.textproto` 就能读到，不需要任何联网。随包 `accessibility.apk` 里那 120 字节写的是 `system: GIT`、`local_root_path: "$PROJECT_DIR"`、`revision: "1221cf08c40f48ee5dfa68d3fc2c5aacbbf01b6a"`——**与上面 `git ls-remote` 得到的 `main` 头一字不差**。所以第二轮那句"公开分支版本落后于随包的 1.5"要说得更准：随包二进制**就是**构建自那个公开提交的工作树，落后的只是版本号字段本身。

但同一提交里 `app/build.gradle.kts` 写的是 `versionCode = 3` / `versionName = "1.4"`（`minSdk 26`、`targetSdk 34` 与随包一致），而随包 `aapt2 dump badging` 给出 `versionCode='4' versionName='1.5'`。textproto 能读到 revision 就证明构建时 `.git` 在位且 HEAD 就是它，而版本号却不符——**唯一的解释是那次构建的工作树被改过且没提交**（AGP 只记录 commit，不证明树干净）。正确的表述因此不是"无源码可对应"，而是"可对应到唯一公开提交，但随包 1.5 相对该提交含未记录的本地改动，至少包括版本字段"。仓库无 tag、无 release，也没有第二个分支，所以没有任何公开发布点标注过 1.5。这条残差比原判断小，但性质更糟：它指向的是**未署名的工作树改动进了分发物**，与我本轮在自有许可文本一节记下的 `terminal/LICENSE` 截断、以及 RenderX 那条"界面凭空写 MIT"，同属"分发物与可核验来源之间有未记录改动"这一类。

顺带用同一条通道查了另两个内嵌包：`desktop.apk` 的 textproto 只有一句 `generate_error_reason: NO_SUPPORTED_VCS_FOUND`，即**打包时根本不在任何 git 工作树里**——这是"无来源"的正向证据，比"搜不到仓库"硬得多；`shizuku.apk` 无此文件（其来源已由 v13.6.0 官方资产逐字节 + `CN=Rikka` 签名闭环，不需要这条通道）。两个自签包的证书 SHA-256 相同（`d7ccd870…`，DN `O=dec, OU=sw, CN=aa`），且都没有 JAR 签名的 `META-INF/*.RSA`，只有 APK 签名方案。

本节全部数字由两支脚本产出：`scripts/ax_refs_and_tree.py`（ls-remote、整包归档列举、ref 语义、atom 与正向对照、源码版本字段）与 `scripts/bundled_apk_vcs_info.py`（textproto、`aapt2 dump badging`、`apksigner --print-certs`，对三个内嵌包各跑一遍），输出同名 `.json`。

对首版的意义：`accessibility.apk` 在源码里是被真实使用的（`UIHierarchyManager.kt:47` 解包到 `cacheDir` 再交给系统安装器），但 M1 已把这条安装/代点链路整体移出分发矩阵并在执行层禁用（见 `capability-gap.md`、`execution-entrypoints.md` §3.9、真机行 E15）。**在载荷已计划移除的前提下，这个缺口不需要靠授权来救**——正确动作是按计划删除分发物，而不是向作者索文。这一条从"待办取证"改为"随 M1 载荷移除而关闭"，登记位置见 `license-inventory.md` §逐项结论与 `decision.md` M0-B06。

## 9. 本轮未覆盖

- `desktop.apk` 的上游仓库归属——本轮从它的 `version-control-info.textproto` 读到 `NO_SUPPORTED_VCS_FOUND`，"构建时不在 git 工作树内"已是正向事实；剩下的问题是它对应的公开仓库（若存在）是哪一个，而该包全仓零引用、已建议直接删除，故未继续投入。
- `accessibility.apk` 相对 `1221cf08…` 的那处未记录改动到底是什么：textproto 只给 commit、不给 diff，仓库也没有 1.5 的提交，唯一能闭合的办法是让作者出示 1.5 的工作树或在 `main` 上把版本字段补提交。
- `subpack/android.apk` 内 `flutter_assets/NOTICES.Z` 的逐条归属——该载荷已按负责人裁决建议整体移除，未重复投入。
- ObjectBox Binary Licence 与 UnRAR License 的**条款正文**未逐条读（只确认了 POM 给出的名称与链接）；前者关系到随包 `.so` 的再分发条件，后者关系到"不得用于商业优势"那类限制条款。**（第五轮：UnRAR 已逐字取得并按原样附在 `THIRD-PARTY-NOTICES.md` §9.14，实测的关键限制不是"商业优势"而是其第 2 条——要求在文档与源码注释中写明"该代码不得用于开发 RAR 兼容压缩器"，本 fork 尚未在任何位置写这句；ObjectBox 的正文仍未取得，且理由变了：POM 的 `url` 只指向站点根，站点公开的那份 PDF 是其《Terms Of Use》而非 Binary Licence（该文件 §2 自己声明产品条款另处提供），且其 §5 禁止再制文档内容。取用与反面记录见 `licence-texts/sources.json`。）**
- 字体授权：`gilroy`、`balsamiqsans`、`poppins`、`roboto`、`jetbrains_mono_nerd_font` 五个家族均无随包授权文本，本轮只登记缺口，未逐一取证。**（第五轮订正：条目面与归属都变了——APK 实测 83 个字体条目分属 7 个来源；`poppins` 没有字体本体进包，只有一个 `poppins_bold.xml` 的 font-family 声明；`jetbrains_mono_nerd_font` 不随 RenderX 而是 `:terminal` 子模块自带；`roboto_medium_numbers.ttf` 出自 exoplayer-ui 的 AAR。逐组归属与"删哪个会带走哪些"记在 `THIRD-PARTY-NOTICES.md` §8.1。仍未取证的是各字体家族自身的上游授权文本。）**
- ML Kit / play-services 的条款文本未与首版实际网络行为对照（是否触发"必须回传使用数据"这类条件）。
