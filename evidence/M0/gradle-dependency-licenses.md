M0-A05 依赖许可第一层：Gradle/JVM 依赖树实测

测量日期：2026-09-19
本轮补的是 `license-inventory.md` 自己划出的缺口——那份文件写明确只覆盖"随 APK 分发的二进制载荷"，不含依赖树。本节覆盖 Gradle/JVM 这一层；其余四层另见 `native-dependency-licenses.md`（CMake FetchContent 与 JS 资产）、`rust-dependency-licenses.md`（`liboperit_ripgrep.so` 的 48 个 crate）、`node-dependency-licenses.md`（`assets/bridge/*.js` 的 92 个 npm 包）与 `upstream-license-retrieval.md`（上游取证轮：本节标出的 jitpack 构件的上游 LICENSE 取证）。

## 1. 方法与可复现命令

```bash
export GRADLE_USER_HOME='C:/Users/15942/Desktop/Work/.gradle-m0'   # M0 构建实际使用的 home，非 ~/.gradle
export JAVA_HOME='C:/Users/15942/dev/jdk-21'
export ANDROID_HOME='C:/Users/15942/dev/sdk/android-sdk'
cd C:/Users/15942/Desktop/Work/operit
./gradlew --offline :app:dependencies --configuration cloneRuntimeClasspath \
    > evidence/M0/dependency-tree-clone-runtime.raw.txt
```

只跑报告任务，不编译、不改任何构建脚本、不引插件。`--offline` 成功的前提是上面那个 GRADLE_USER_HOME：用默认 `~/.gradle` 时 AGP 8.13.2 的 plugin marker 不在缓存里，命令会以 "Plugin ... was not found" 失败（实测 BUILD FAILED in 32s），那次失败输出没有保留，不构成证据。

许可取自本机缓存的 POM 原文（`$GRADLE_USER_HOME/caches/modules-2/files-2.1/<group>/<name>/<version>/*/<name>-<version>.pom`），POM 无 `<licenses>` 时向上追 `<parent>`（最多 6 级）；仍无则读该构件 jar/aar 内的 `LICENSE`/`NOTICE`/`COPYING` 文本并按首 800 字节指纹归类。机器可读全表：`gradle-dependency-licenses.json`（含每个条目的 `source` 字段，标明许可是"来自 POM"还是"来自构件内文本"）。

## 2. 计数

| 项 | 数 | 说明 |
|---|---|---|
| 实际解析选定的构件 | **396** | 只有这一层可能进入 APK |
| 被版本冲突消解掉的构件 | 254 | 报告树上出现但从未下载，缓存里连文件都没有；不计入义务 |
| 依赖约束 `(c)` | 106 | 不是构件 |
| 直接声明（build.gradle.kts / libs.versions.toml）落入选定集的构件 | 108 | |
| 许可分类 | 362 permissive、**5 copyleft 系**、24 其他条款、**5 无法确定** | 两处已被后续轮次更正：copyleft 里 1 项是双许可可选 MIT，"无法确定"里 3 项已联网取证落定，见 `upstream-license-retrieval.md` 第 2、3 节 |
| 选定但缓存中查无文件的构件 | 0 | 反证 254 个"未缓存"确实全是被消解掉的版本 |

## 3. 需要处置的条目

copyleft 系（POM 原文照抄，未作解读）：

| 构件 | 直接声明 | POM `<license><name>` | 使用点 |
|---|---|---|---|
| `com.itextpdf:itextg:5.5.10` | 是（`app/build.gradle.kts:744`） | `GNU Affero General Public License v3` | 仅 `util/DocumentConversionUtil.kt` 一个文件 |
| `org.checkerframework:checker-compat-qual:2.5.3` | 否，`com.google.mediapipe:tasks-text → tasks-core → com.google.flogger:flogger` 带入 | `GNU General Public License, version 2 (GPL2), with the classpath exception` **与 `The MIT License` 并列（双许可，可择 MIT）** | 注解包，无运行期调用面 |
| `ru.noties:jlatexmath-android:0.2.0` | 是（`:754`） | `GNU General Public License, version 2` | Markdown/LaTeX 渲染 5 处（`CanvasMarkdownNodeRenderer`、`StreamMarkdownRenderer`、`MarkdownInlineSpannable`、`LatexCache`、`JLatexMathCompatibility`） |
| `ru.noties:jlatexmath-android-font-cyrillic:0.2.0` | 否（上一项带入） | 同上 | 字体数据 |
| `ru.noties:jlatexmath-android-font-greek:0.2.0` | 否（上一项带入） | 同上 | 字体数据 |

AGPL-3.0 与 GPL-2.0 的差别不是程度而是性质：AGPL 第 13 条要求向通过网络访问该程序的用户提供对应源码，而本项目的首版形态正是"发给 5–10 位受邀测试者的安装包"。这三个库（itextg、jlatexmath 及其字体包）是本项目当前许可义务的最重来源，且在计划书 M0-A05 之前从未被登记。

**这一小节记 5 行，但真正无可选弱许可的 copyleft 构件是 4 个**（itextg + jlatexmath 及其两个字体包）；`checker-compat-qual` 是 GPL2-with-CE **OR** MIT 的双许可，可择 MIT，不产生 copyleft 义务。上一版把它的 `<licenses>` 只读了第一条，因而误判——读 POM 许可必须取完整块。上游取证轮又加入一个成员：`RenderX`（见下表与其第 4 节）。

其他条款（24 项，均非开源许可，各有独立分发条款）：

| 条款名 | 项数 | 构件 |
|---|---|---|
| ML Kit Terms of Service | 15 | `com.google.mlkit:*` 与 `com.google.android.gms:play-services-mlkit-text-recognition*`（OCR 语言包） |
| Android Software Development Kit License | 4 | `play-services-base/basement/tasks`、`com.google.android.odml:image` |
| Bouncy Castle Licence | 3 | `bcprov/bcpkix/bcutil-jdk18on:1.78` |
| UnRar License | 1 | `com.github.junrar:junrar:7.5.5`（`:799` 直接声明，用于 `util/ArchiveUtil.kt`） |
| Tasker Plugin Sample License | 1 | `com.joaomgcd:taskerpluginlibrary:0.4.10` |

许可无法确定（5 项，POM 无声明、构件内也无许可文本）——**本轮已联网落定，明细与证据链见 `upstream-license-retrieval.md`**：

| 构件 | 直接声明 | 上游取证轮结论 |
|---|---|---|
| `com.github.topjohnwu.libsu:core/nio/service:6.0.0` | 是（`:718-720`） | **Apache-2.0，在钉住的 tag 上可证**（`libsu/6.0.0/LICENSE` 11,357 字节，该文件自 2018-01-25 起未改动）。本条结案 |
| `com.github.Sable:axml:2.0.0` | 是（`:704`） | 上游现为 Apache-2.0，**但 2.0.0 tag 上没有任何许可文本**，且 2.0.0 是上游唯一 tag——不存在"升级到带许可的发布版"这条路。需向作者取追认或整链移除 |
| `com.github.tech-pw:RenderX:1.0.0` | 是（`:755`） | **升级：分发的构件无任何许可授予**。jitpack 日志把该 `.aar` 定位到提交 `de7d1e33…`（`1.0.0-0-gde7d1e3`，恰在 tag 上），该提交里 13 种许可文件拼写全部 404、构建脚本无 `license` 行、`readme.md` 无任何许可字样；上游唯一的一份 `LICENSE` 是 3 天后在 main 上创建的 **GPL-3.0**（35,149 字节）。应用内界面却写着 "MIT" |

这三组都经 jitpack 取（`com.github.*`），POM 是 jitpack 生成的，本来就不带 `<licenses>`，所以原先的"无声明"只说明**本机不可证**。补充一条取证纪律：判定"某构件无许可"不能只看 POM 缺字段，也不能只看 tag——要把 jitpack 构建日志里的**提交号**取出来，在那个提交上逐一试探许可文件名，否则很容易把"上游后来补了许可"误当成"分发的那份也有许可"。

## 4. 这些条目确实进了 APK

许可层面最容易犯的错是"依赖树里有 ≠ 包里有"或反之。对 `C:/M0/A` 那次干净构建的 APK（5,550 条目、43 个 `classes*.dex`、dex 合计 207,404,144 字节）直接搜类型描述符。**needle 已统一为锚定形式 `L<pkg>/`**（描述符以 `L` 起始），因此与上一版的非锚定计数略有差别：

| 构件 / 说明 | needle | 命中数 | 所在 dex |
|---|---|---|---|
| `com.itextpdf:itextg:5.5.10` | `Lcom/itextpdf/text/` | 1,808 | classes25、classes39 |
| （同一条，非锚定形式，供对照） | `com/itextpdf/text/` | 1,814 | 同上 |
| `com.github.junrar:junrar:7.5.5` | `Lcom/github/junrar/` | 235 | classes25、classes37 |
| `org.checkerframework:checker-compat-qual:2.5.3` | `Lorg/checkerframework/` | 716 | classes32、classes38 |
| `ru.noties:jlatexmath-android`（含两个字体包） | `Lru/noties/jlatexmath/` | 88 | classes15、16、29、35、41 |
| `com.github.topjohnwu.libsu:*:6.0.0` | `Lcom/topjohnwu/` | 246 | classes15、21、23、40 |
| `com.github.Sable:axml:2.0.0` | `Lpxb/android/` | 81 | classes16、classes35 |
| `com.github.tech-pw:RenderX:1.0.0` | `Llive/pw/render` | 72 | classes15、classes41 |
| （对照）`com.tom_roush:pdfbox-android` | `Lcom/tom_roush/pdfbox/` | 1,647 | classes15、25、30、40 |
| （对照）`net.lingala.zip4j:zip4j:2.11.5` | `Lnet/lingala/zip4j/` | 268 | classes41 |
| （反证）RenderX AAR 清单声明的那个 activity 类 | `Llive/pw/renderX/LatexView;` | **0** | 无——该 exported MAIN/LAUNCHER 声明在 `app/src/main/AndroidManifest.xml:112-114` 被 `tools:node="remove"` 删掉，且 AAR 的 26 个 class 里也没有这个类 |

即上述 copyleft / 受限条款构件不是"编译期可见但运行期不打包"，而是实打实在分发物里。**方向性提醒**：命中是"确有代码进来"的正证据；未命中不能反推"没链入"（只有以该前缀命名的类才会留下描述符）。

## 5. 随包"开源许可"界面与实测冲突

仓库里有一个自维护的界面 `ui/features/about/screens/OpenSourceLicenses.kt`，硬编码 68 条（`rg -c '^\s+OpenSourceLibrary\("'` 实测），向用户显示每个库的名称、许可与链接。逐条与实测比对（POM `<licenses>` 全文、构件内文本、上游取证轮三种出处），**下列 8 条不一致**：

| 条目 | 界面写的 | 实测 | 偏差方向 |
|---|---|---|---|
| `iText (v5)` | `MPL/LGPL` | `GNU Affero General Public License v3`（POM 里只有这一条） | **把最重的义务写成了弱许可** |
| `RenderX` | `MIT` | 分发的构件无任何许可授予；上游唯一的许可是 3 天后加的 **GPL-3.0**，且从未出现任何 MIT 文本 | **凭空写成 MIT**（上游取证轮由"本机无出处可证"升级为"已证伪"） |
| `junrar` | `The Unlicense` | `UnRar License` | 把带使用限制的许可说成公有领域 |
| `JLatexMath-Android` | `GPL-2.0 with Classpath Exception` | `GNU General Public License, version 2`（POM 无例外条款字样） | 凭空加上不存在的例外条款 |
| `ML Kit` | `Apache-2.0` | `ML Kit Terms of Service` / `Android Software Development Kit License`，共 19 个构件 | 把专有服务条款说成 Apache |
| `Bouncy Castle` | `MIT` | `Bouncy Castle Licence` | 名称不符（该许可含自己的表述） |
| `FFmpegKit` | `LGPL-3.0` | AAR 内 38 份 `res/raw/license_*.txt` 与书面要约写 "FFmpegKit with GPL v3.0 license"，配置串含 GPL-2.0+ 的 twolame/shine 与 `--enable-version3` | 低估（详见 `license-inventory.md`） |
| `ObjectBox` | `Apache-2.0` | 三条并列：`ObjectBox Binary Licence`（含 native runtime）、Apache-2.0（flatbuffers）、OpenLDAP Public License 2.8（LMDB） | 不完整 |

另有 2 条界面给出了许可、本机 POM 无出处：`libsu` 与 `axml` 都写 `Apache-2.0`。上游取证轮之后二者地位不同——**`libsu` 已在钉住的 tag 上证实**（该条转为一致）；`axml` 的 Apache-2.0 只存在于晚于 2.0.0 tag 的 master 上，与被分发的构件仍不对应。

这一节是本文件里对 Fork 路线最有约束力的部分：计划书 §10.2 要求"不得用措辞掩盖未实现的保护"，而这个界面正在用措辞掩盖未履行的义务；如果首版原样保留，我们等于把自己上游的一处错误声明继续分发给测试者。**注意它还漏了一整层**：Rust 层（ripgrep 及 32 个运行期 crate）、JS 资产层（terser 及其内嵌的 `@jridgewell/source-map`、`acorn`）、Node 层（92 个包）、MathJax 本体与字体（第五轮订正为 33 个条目，非"12 个"）都没有任何条目——上游取证轮实测该文件里 `ripgrep`、`terser`、`MathJax`、`acorn` 四个词各 0 命中。所以 `THIRD-PARTY-NOTICES` 必须从四层实测数据生成，不能以这个界面为原料。

## 6. 本轮未覆盖（不要把这些当成已完成）

- **实际链接面未逐构件证明**。第 4 节用 dex 证实了 7 个高风险条目（itextg、junrar、checker-compat-qual、jlatexmath、libsu、axml、RenderX）在包里；其余 389 条只到"POM 声明了什么"，没有逐条证明它是否真被链入（例如 `examples/`、`tests/`、`data/` 下的嵌套许可文本对应的代码大概率不在 `.so` 里）。
- ~~`THIRD-PARTY-NOTICES` 文本未产出~~ **第五轮已成文**：`operit/THIRD-PARTY-NOTICES.md`（全文 10 节），原料即本节与 `native-dependency-licenses.md` 等四层 JSON，另加 `licence-texts/` 的 17 份条款正文与字体条目归属；由 `scripts/third_party_notices.py` 生成，**不得手改那份 markdown**。仍未做的是它的**随包位置与 UI 呈现**（作为 APK 资产还是分发页、是否在"关于"里加入口），以及本节第 7 节列出的处置。
- ~~`ObjectBox Binary Licence` 与 `UnRar License` 的**条款正文**未逐条读~~ **第五轮分别收口，结果一好一坏**：UnRAR 的正文已从**上游 tag `v7.5.5`（即被分发版本）的 `LICENSE`** 取得并逐字附在声明第 9.14 节——注意这个通道是必需的而非可选：`junrar-7.5.5.jar` 的 118 个条目里没有任何许可文本。该文件与 master 同名文件逐字节相同（SHA-256 前 16 位 `9d90c3649d4eb064`），且在 master 上只有 2020-07-18 一次提交、早于 7.5.5，因此不存在 `axml` 那种"授权只晚于被分发版本"的问题。其**第 2 条要求分发方写明"不得用该代码开发 RAR 兼容压缩器"**——这条我们从未写过，且属于本仓库自己就能做完的动作；ObjectBox 那份则实测**通道不通**：POM 给的 url 只指向站点根，公开可得的 PDF 是网站服务条款（其第 2 节把产品条款外推、第 5 节禁止复制），并非产品级二进制许可，故不能靠"再搜一次"关闭，需向 ObjectBox 索取正式文本；而它恰恰是关系到随包 `libobjectbox-jni.so` 再分发条件的那一份。
- ~~npm 层未展开~~ **已完成**，见 `node-dependency-licenses.md`（92 个包，MIT 82 / ISC 7 / BSD-3 2 / BSD-2 1，copyleft 0）；Rust 层见 `rust-dependency-licenses.md`（48 包，copyleft 0）。仍未展开的是 `subpack` 内 `flutter_assets/NOTICES.Z` 的逐条归属（该载荷已建议整体移除）。
- ~~5 个"无出处"构件未联网取证~~ **已完成**，见 `upstream-license-retrieval.md`：libsu 结案、axml 待作者追认、RenderX 升级为"分发构件无许可授予 + 界面误写 MIT"。**同风格的剩余动作**已一并做完：`accessibility.apk` 与 `desktop.apk` 的上游取证见 `upstream-license-retrieval.md` §8，用的不再只是 raw/HTML 探测，而是 `git ls-remote`（判分支真伪）＋ codeload 整包归档列举（判全树文件名）＋ 产物自带的 `META-INF/version-control-info.textproto`（判构建时来源），两支脚本在 `scripts/`。`api.github.com` 的配额问题仍在，这套通道不依赖它。

## 7. 交给负责人的决策

1. `RenderX`（**分发构件无许可授予，上游现为 GPL-3.0**）：**建议直接移除，且这是全部决策项里代价最低的一条**——删 `app/build.gradle.kts:755` 与 `gradle/libs.versions.toml:167` 各 1 行，零源码改动（Kotlin/Java 侧引用实测为 0），顺带带走 150 个 MathJax 资产（1,434,800 字节）与 33 个字体条目（该 AAR 的 `res/font` 12 个条目里 11 个是字体本体、第 12 个 `poppins_bold.xml` 只是 font-family 声明；另有 MathJax 目录下 22 个 TeX 派生字体随它进来。第五轮订正，原记"12 个字体"）。公式渲染另有 `jlatexmath` 一条路在，不会因此失去能力。需负责人确认的是：这些字形与 MathJax 是否被别处隐性依赖（本轮检索未发现）。
2. `itextg`（AGPL-3.0）：只有 `DocumentConversionUtil.kt` 一处引用，而 PDF 文本抽取另有 Apache-2.0 的 pdfbox-android 在用。若首版不需要 itextg 的 PDF 写能力，**从依赖中移除**比履行 AGPL 源码义务划算；若需要，必须先取得商业授权或替换实现。
3. `jlatexmath`（GPL-2.0，且 POM 未给 classpath exception）：LaTeX 渲染对陪伴聊天不是必需，首版是否直接关闭该渲染路径？注意它一旦保留，第 1 条的 RenderX 就有替代对象；两条一起关闭才是"公式渲染出首版"。
4. `junrar`（unRAR 限制）：RAR 解压不在首版范围，建议移除。**若不移除**，其条款正文已在声明第 9.14 节逐字附出，第 2 条要求分发方主动写明"不得用该代码开发 RAR 兼容压缩器"——这句是本仓库自己就能补的动作（写进文档与 `junrar` 依赖声明处的注释），不需要外部信息，也不随本条决策作废。
5. `libsu`：许可已结案（Apache-2.0），移除它的理由**不再是许可**，而是首版在执行层禁用 root——建议连依赖一起移除，而不是只隐藏入口。
6. `axml`（Apache-2.0 只存在于晚于 2.0.0 的 master 上）：不能像 RenderX 那样一删了之（`Lpxb/android/` 81 处命中、随 `subpack` 重打包链使用）。选项只有：向作者取对 2.0.0 的追认、改用 master 构建（引入未固定提交的风险）、或随 `subpack` 一起整链移除。
7. `OpenSourceLicenses.kt` 的 8 处错误声明（第 5 节）：改文本还是整块换成 `THIRD-PARTY-NOTICES`？后者更省事也更可辩护——那个界面还整层漏掉了 Rust / JS 资产 / Node 三层的归属。**第五轮之后该选项有了对象**：`operit/THIRD-PARTY-NOTICES.md` 已成文且待负责人复核签字；仍需决策的是把它放在哪里（APK 内资产 + 应用内入口，还是只在分发页给出）。
