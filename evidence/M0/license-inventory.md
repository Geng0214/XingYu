# M0-T06 依赖与许可审查初表

状态：内嵌分发物已完成两轮实测（见 §"2026-09-19 第二轮复核"，来源类缺口大部分闭合，义务类与授权文本缺口仍在）；Gradle/Maven 依赖树、CMake FetchContent 原生库、Rust 与 Node 四层的许可证已完成实测（见 §"2026-09-19 第三轮复核"）；上一轮标为"本机不可证"的 5 个构件已完成联网取证（见 §"2026-09-19 第四轮复核"，其中 RenderX 升级为分发权缺口 M0-B09），同节末补做了 `accessibility.apk`/`desktop.apk` 的上游取证。**可交付的 `THIRD-PARTY-NOTICES.md` 已成文**（仓库根，由 `scripts/third_party_notices.py` 从本轮四个层的实测 JSON 与 `licence-texts/` 生成，第 2 节即本文件各轮缺口的当前状态汇总）；未完成的仍是分发法律复核，以及该文件第 2 节列出的 15 项处置。

## 已确认

| 组件/范围 | 来源/版本线索 | 许可/风险 | 状态 |
|---|---|---|---|
| Operit 主代码 | 仓库 `LICENSE` | LGPL-3.0。**本轮实测到字节级**：与 FSF 官方 `gnu.org/licenses/lgpl-3.0.txt`（7,652 字节、165 行、SHA-256 前 16 位 `e3a994d82e644b03`）逐字节相同，唯一差别是行尾 LF→CRLF（故本文件 7,817 字节、SHA-256 前 16 位 `7d3a95e5e0697806`） | 已闭合到"类别＋完整性"两级：根 LICENSE 是规范全文，未改写；仍需按修改/分发方式复核义务 |
| `terminal` 子模块 | `.gitmodules` → `github.com/AAswordman/OperitTerminalCore`，钉在 `045868988e4091d6…`（已写进 `build-manifest.json` 的 `baselineDefinition`） | `terminal/LICENSE` 10,296 字节、211 行、SHA-256 前 16 位 `40c41f5f4ed9e2f2`：**类别是 LGPL-3.0，且条款词与官方全文逐词一致**——词级 diff 显示官方 1,234 个词全部按序出现、替换/删除组 **0**，差异只有 3 处**插入**：中文标题"Operit AI 项目许可证（基于GNU宽通用公共许可证v3.0）"＋"版权所有 (C) 2023-2024 Operit AI 项目团队"、正文末一个多余的 `-` 词、以及在 LGPL 正文之后另接 GPL-3.0 标题与序言开头，再用一句非规范括注"[The full text of the GNU General Public License v3.0 follows, but is omitted for brevity. …]"把它截断 | 已实测（本轮）：许可类别与主仓库一致，条款文本未被改写，Fork 路线的义务判定不变；但**它作为"可交付的条款文本"是不完整的**，动作见 §"2026-09-19 第四轮复核"末 |
| `tools/hotbuild/OperitNightlyRelease` | `.gitmodules` 的 **SSH** 地址 `git@github.com:AAswordman/OperitNightlyRelease.git`，gitlink `350165237e89b370e45213dcd7ab02ee7e7a6361` | 未测 | 本轮判定为**不进分发、不进构建**：子模块未检出（工作树为空目录），`*.kts`/`*.gradle`/`libs.versions.toml` 全文对 `hotbuild` 0 引用，故不产生 APK 侧义务；但 HTTPS 取不到、SSH 需授权，**基线复现若依赖它则仍不可复现**，保留为发布流水线侧待办 |
| Gradle/Maven 依赖 | `gradle/libs.versions.toml`、各模块 build 文件 | 已实测：396 个选中坐标全部取得许可证，见 §"2026-09-19 第三轮复核" 与 `gradle-dependency-licenses.md`；其中 5 个"本机不可证"构件已联网取证，见 §"2026-09-19 第四轮复核" | 来源已闭合；**发现 AGPL/GPL 载荷与应用内错误声明，见 M0-B08；发现一个无许可授予的分发构件，见 M0-B09** |
| CMake FetchContent 原生库 | `app`、`llm`、`avator` 等 CMake 文件 | 11 个依赖已钉 commit 并逐个读到顶层 LICENSE，见 `native-dependency-licenses.md` | 已实测；嵌套许可证按源文件列表的证明未做 |
| 外部 ZIP/AAR | `subpack.zip`、`jniLibs.zip`、`libs.zip`、FFmpeg AAR 等 | 来源 URL（Drive 固定 ID）、HTTP 状态、SHA-256 与成员清单已在 §"2026-09-19 实测补充"逐项实测；FFmpeg AAR 的 GPL/LGPL 归属仍未定 | 指纹已闭合，分发权未闭合：见 M0-B06 |
| STT 模型资源 | `app/config/stt-model-assets.properties`，URL 钉到 HF commit + 字节数/SHA-256 校验 | 8 项许可实测为 Apache-2.0（sherpa ×7）与 MIT（silero VAD）；属性文件里"许可未声明"那条注解是错的 | 许可侧闭合；随首版无语音而从分发中移除 |
| 仓库 assets | 历史 `app/src/main/assets/jks.jks`、`pkcs12.keystore` 已从工作树移除 | 密钥材料不得进入发布源码或 APK | 构建侧已闭合（门禁 `verifyNoEmbeddedSigningMaterial` 两次探针命中 + APK 5,550 条目扫描干净）；**运行期未验证**，见 M0-B02 |

## 2026-09-19 实测补充

### 外部归档与产物指纹

完整 SHA-256 记在 `evidence/M0/build-manifest.json`，此处仅列坐标与状态。

| 组件 | 来源 | 许可 | 状态 |
|---|---|---|---|
| `libs.zip` | CI 固定 Google Drive ID，实测 HTTP 200 | 内容含 FFmpeg 相关 AAR，需逐项 | 已取得，待复核 |
| `subpack.zip` → `assets/subpack/android.apk`、`windows.zip` | 同上 | 内含 Flutter 运行时、`assistance_subpack.exe`、`flutter_windows.dll`、WebView2 DLL | 阻塞：可执行产物的来源、版本与再获取途径未证明 |
| `jniLibs.zip` → `arm64-v8a/liboperit_ripgrep.so` | 同上 | 本仓库自带的 cdylib crate `operit_ripgrep`（不是上游 ripgrep CLI），许可为 MIT/Unlicense 双许可 ＋ 自有代码 LGPL-3.0；详见 §"2026-09-19 第二轮复核"的订正表与 `rust-dependency-licenses.md` | 源码级重建**已执行**（结论见第二轮），逐字节不等的根因是工具链版本，非污染；残留动作是加 `--remap-path-prefix` 后替换随包产物，见 M0-B07 |
| `app/libs/ffmpeg-kit-local.aar` | `libs.zip` 解包（`b292b042…`） | 内嵌 FFmpeg 配置串含 `--enable-version3 --enable-libtwolame --enable-libshine --enable-gnutls`，串里没有 `--enable-gpl`；随包 38 份 `res/raw/license_*.txt` 中 twolame/shine 为 GPL-2.0+；`res/raw/source.txt` 的书面要约明文写"GPL v3.0" | 阻塞：这是 GPL 组合还是 LGPL 组合无法由我们判定，且 AAR 无版本号/commit/哈希。详见 §"2026-09-19 第二轮复核" |
| STT 资产 8 项 | Hugging Face，URL 钉死到具体 commit，`:syncSttModelAssets` 按字节数 + SHA-256 校验 | silero_vad = MIT（`safestack/silero-vad` @ `8a63e2e8…`）；sherpa-ncnn 双语模型 7 项在钉死 commit `05945efc…` 上的 HF `cardData.license` 实测为 **apache-2.0** —— `app/config/stt-model-assets.properties` 里"upstream license not declared in model metadata"这条注解是错的，见 §"2026-09-19 第二轮复核" | 许可声明侧不再阻塞；仍待处置的是**训练数据出处**（k2-fsa 文档称该模型训练自未具名内部数据集），且首版范围不含语音，处置为从分发中移除 |
| `:terminal` → `ubuntu-noble-aarch64-pd-v4.18.0.tar.xz`（64,133,552 B，12,684 成员，Git blob `0757d861…`） | terminal 子模块直接跟踪；**来源已证实**：本机与 `github.com/termux/proot-distro` v4.18.0 官方资产逐个字节相同，两侧 SHA-256 均为 `91acaa786b8e2fbb…` | 完整 Ubuntu 用户态：GPL/LGPL/MPL/Apache 等混合许可（140 个包带 `debian/copyright`），含 Ubuntu/Canonical 商标材料 | 来源不再阻塞，**义务未履行的部分**：GPL/LGPL 对应源码或书面要约覆盖 12,684 个成员、Canonical 商标政策要求"未修改才可再分发且不得用于产品名"。首版范围不含 Linux 终端，**处置为从分发中移除**，不以"个人使用"替代许可结论 |
| `assets/accessibility.apk`(1.5)、`desktop.apk`(1.0)、`shizuku.apk`(13.6.0)、`operit_shell_exec` | 上游仓库直接跟踪 | Shizuku **来源已闭环**：与 v13.6.0 官方资产 SHA-256 完全相同（`6e273ab0…`）且签名 `CN=Rikka`，许可 Apache-2.0 附带明确的商标例外（不得改名/换签名/复用图标）；`accessibility.apk` 与 `desktop.apk` 共用自签证书 `O=dec, OU=sw, CN=aa`（既非应用商店密钥，也不同于已清除的 `jks.jks`/`pkcs12.keystore`） | 分列处理见 §"2026-09-19 第二轮复核"与其末的补做段：shizuku 可保留（首版仍禁用但不必移除）；accessibility 上游整棵树无授权文本（取证已完成，处置随 M1 移除）；desktop.apk 全仓零引用＋`NO_SUPPORTED_VCS_FOUND`，建议直接删除 |
| `assets/templates/**/aapt2-arm64-v8a`（4,706,040 B ×2，ELF）、`shower-server.jar`（1,133,303 B）、`main.jar`、`setup_android_env.sh` ×2、`:terminal/setup_fake_sysdata.sh` | 上游仓库直接跟踪（Android SDK 工具与自建脚本/JAR） | aapt2 属 AOSP（Apache-2.0）需 NOTICE；自建项需来源证明 | 待复核；均服务"在设备上建工程 / 虚拟屏 Shell"，属首版禁用面 |

### 依赖冲突实测

`com.tom-roush:pdfbox-android:2.0.27.0` 传递引入 `org.bouncycastle:bcpkix-jdk15to18:1.72`（及其依赖 `bcutil-jdk15to18:1.72`），与为生成每安装导出证书而新增的 `bcpkix-jdk18on:1.78`/`bcutil-jdk18on:1.78` 产生大量重复类，`:app:checkCloneDuplicateClasses` 失败。依赖树经 `dependencyInsight` 定位到 pdfbox 这一条唯一来源。上游已对 `bcprov-jdk15to18` 做全局 exclude，本次把 `bcpkix-jdk15to18`、`bcutil-jdk15to18` 纳入同一 exclude 列表。

首版范围已取消文本型 PDF 专项验收（`docs/scope-freeze-v3.md` P0-8），pdfbox 属可整体移除的候选，届时该 exclude 与本冲突一并消失；在移除前保留此记录，避免被当作无端配置。

## 2026-09-19 第二轮复核（内嵌分发物）

复核方式全部是本机可重跑的实测，不采信任何"看起来像"的推断：

- `sha256sum` 比对包内资产与上游官方 release 资产（经本机代理 `curl -L` 直下）。
- `apksigner verify --print-certs` 读每个内嵌 APK 的签名者 DN 与证书摘要。
- `curl https://huggingface.co/api/models/<repo>?expand[]=cardData&expand[]=sha` 读模型卡许可。
- `raw.githubusercontent.com` 逐个探测源码仓库的 `LICENSE` / `LICENSE.md` / `LICENSE.txt` / `LICENCE`（main、master 两支）。
- 直接在 `app/libs/ffmpeg-kit-local.aar` 的 `jni/arm64-v8a/*.so` 里搜 FFmpeg 配置串与组件符号。

### 本轮纠正的三条错记

| 原记录 | 实测结论 |
|---|---|
| sherpa-ncnn 7 项"upstream license not declared in model metadata"（写死在 `app/config/stt-model-assets.properties` 第 6 字段） | 该 commit 的模型卡 `cardData.license = apache-2.0`，且 `sha` 与钉死的 `05945efc40afe4b572542f01104ca5c413a9f6e1` 一致。注解与事实相反，属于随包分发的错误声明文本，需一并修正或随模型移除 |
| Ubuntu rootfs"来源、版本与许可未闭环" | 来源已证实：与 proot-distro v4.18.0 官方 release 资产字节相同（本地与上游 SHA-256 均为 `91acaa786b8e2fbb…`，体积同为 64,133,552 B）。未闭环的是**义务侧**（对应源码 / 书面要约、Canonical 商标政策），不是来源侧 |
| `liboperit_ripgrep.so` "Rust 产物，许可随 ripgrep" | 它不是 ripgrep CLI，是本仓库自带的 cdylib crate `operit_ripgrep 0.1.0`，源码在 `tools/native_ripgrep/`（`Cargo.toml` 声明 crate-type=cdylib 与 **7 个**直接依赖：globset 0.4.18、grep-matcher 0.1.8、grep-regex 0.1.14、ignore 0.4.27、jni 0.21.1、serde 1.0.228（features=derive）、serde_json 1.0.150；`Cargo.lock` 共 48 包，其中对 `aarch64-linux-android` 只有 32 个是运行期库）。许可 = 上游 grep 系列的 MIT OR Unlicense 双许可 + 自有代码的 LGPL-3.0，不是"随 ripgrep 的 MIT"这一句话能覆盖的。**本轮订正**：首次登记时把它写成"五个依赖"，漏了 serde 与 serde_json——因为只数了 grep/globset/jni 那组名字，没有逐行读 `[dependencies]`。全层许可的实测见 `rust-dependency-licenses.md` |

### 逐项结论

| 项 | 结论 | 剩余动作 |
|---|---|---|
| `shizuku.apk` 2,571,773 B | **已闭环**。与 `RikkaApps/Shizuku` v13.6.0 资产 SHA-256 完全相同（`6e273ab0…`），签名 `CN=Rikka`。Apache-2.0，但附带明确商标保留：不得改名、不得重签名、不得复用其图标，除展示 Shizuku 本身之外 | 首版仍按 B04 在执行层禁用；随包时须落 Apache-2.0 文本与 NOTICE，并保持原签名不动 |
| `ubuntu-noble-aarch64-pd-v4.18.0.tar.xz` | 来源闭环、义务未闭环（140 个包带 `debian/copyright`，混合 GPL-2/GPL-3/LGPL/BSD/MIT/MPL/Apache；proot-distro 工具链自身 GPL-3.0） | 从分发中移除（首版无 Linux 终端）。移除后本行义务一并消失 |
| STT 8 项（sherpa ×7 + silero VAD） | 许可已声明（Apache-2.0 / MIT）；训练数据出处不可证明 | 从分发中移除（首版无语音）。同时把属性文件里那条错误许可注解随条目一起删掉，不留错误文本 |
| `accessibility.apk` 2,763,980 B（`versionCode 4 / 1.5`，包名 `com.ai.assistance.operit.provider`） | 功能上被引用（`data/repository/UIHierarchyManager.kt`），来源是 `AAswordman/OperitAccessbility`。**上游取证已完成**（`upstream-license-retrieval.md` §8）：该仓库只有一个分支 `main`（`git ls-remote`，无 tag、无 release；第二轮写的"main/master"里 master 是改名遗留的假分支，raw 端点仍返回内容）；`main` 头 `1221cf08…` 的**整棵归档 78 个条目里没有任何授权文本**，12 种许可文件名大小写全 404，全树唯一的 Apache-2.0 文本是 `gradlew`/`gradlew.bat` 自带的 wrapper 头，不覆盖仓库自己的 `.kt`；项目级许可信号只有 README 第 5 行一枚 shields.io 徽章。来源侧另有正向闭合：随包 APK 的 `META-INF/version-control-info.textproto` 记录的 revision **就是 `main` 头那个 commit**，但该提交的 `app/build.gradle.kts` 写的是 `1.4/3` 而随包是 `1.5/4` ⇒ 构建它的工作树被改过且未提交。签名 `O=dec, OU=sw, CN=aa` | 徽章不构成可核验授权；且分发物相对可核验来源含未记录改动。处置：**随 M1 载荷移除一并关闭**（该包及解包→安装→无障碍代点链路已定在 M1 移出分发矩阵并在执行层禁用），不以取得授权来替代移除 |
| `desktop.apk` 6,489,542 B（`versionCode 1 / 1.0`，包名 `com.ai.assistance.operit.desktop`，targetSdk 36） | 与 accessibility 共用同一自签证书（SHA-256 `d7ccd870…`）；**全仓（除 `build/`）零引用**；其 `version-control-info.textproto` 只有 `generate_error_reason: NO_SUPPORTED_VCS_FOUND`，即**打包时不在任何 git 工作树内**——"无来源"由推测升为实测事实 | 建议直接删除：既无功能价值也无授权依据，属纯风险负载 |
| `subpack/android.apk` 48,139,093 B、`windows.zip` 11,412,717 B | Operit 自写的 Flutter 模板产物，但**由无校验的 Google Drive 归档取得**；`android.apk` 签名是 `CN=Android Debug` —— 随包分发的是一个 debug 构建。Flutter 侧 BSD-3 + 143 段 BSD-3 / 278 段 Apache-2.0 / 83 段 MIT 的 `flutter_assets/NOTICES.Z`；`WebView2Loader.dll` 受微软 WebView2 分发条款约束 | 无法从本仓库重现（无源码、无版本钉），且 Windows 载荷超出 Android 范围：从分发中移除；若保留 Android 侧，必须改为可复现的 release 构建并随附 NOTICES |
| `app/libs/ffmpeg-kit-local.aar` 15,264,401 B | 配置串含 `--enable-version3 --enable-libtwolame --enable-libshine --enable-gnutls` 而无 `--enable-gpl`；twolame 与 shine 是 GPL-2.0+ 组件，其许可证文本随包（38 份 `res/raw/license_*.txt`）；`res/raw/source.txt` 的书面要约明文称 "FFmpegKit with GPL v3.0 license"。AAR 无版本号、无 commit、无上游哈希 | 归属待定：按 GPL 组合作对待则需履行完整对应源码义务；或换为我们自己构建、可审计的 FFmpeg 包。首版若无视频/转码需求，评估整体移除 |
| `liboperit_ripgrep.so` 3,599,272 B | 源码在本仓库（`tools/native_ripgrep`），许可为 MIT/Unlicense + LGPL-3.0。**源码级重建已执行**（`--locked --offline`，15.04s，产物 3,741,008 B）：与随包产物逐字节不等，实测原因是工具链版本不同（rustc 1.88.0 + clang 18.0.4/LLD 18 对 rustc 1.95.0 + clang 21.0.0/LLD 21，后者本机无）；功能面等价已证（54 个 dynsym、唯一 JNI 导出、DT_NEEDED、crate 字符串字面量覆盖集一致，仅 `getpid`/`gettid` 一个导入差，且 arm64 各 API 档两符号均可用）。**新增分发风险**：随包产物内嵌 106 处 `C:\Users\12809\.cargo` / `C:\Users\12809\.rustup` 反斜杠路径，即该二进制由身份不可核查的个人 Windows 机器手建（无 CI 特征），经无校验 Drive 归档进入分发链 | 用我们自己的源码构建产物替换随包 `.so`；替换前先加 `--remap-path-prefix`，否则只是把 `12809` 换成 `15942`；把 rustc 与 NDK 版本写进构建脚本断言。明细见 `ripgrep-source-rebuild.txt` |
| `operit_shell_exec` 21,496 B | 本仓库 `tools/shell_identity_launcher/` 自带源码，二进制内构建路径指向上游作者的 NDK 26.1.10909125 | 源码可复现；随 LGPL-3.0 一般义务 |

### 2026-09-19 第三轮复核（依赖树层）

前两轮只覆盖"随 APK 分发的二进制载荷"，依赖树是这份文件自己写下的缺口。本轮补齐依赖树的四层，方法与逐条数据不在这里重复：

- Gradle/JVM 依赖树：`gradle-dependency-licenses.md`（结论摘要见下）＋ 机器可读全表 `gradle-dependency-licenses.json` ＋ 原始树 `dependency-tree-clone-runtime.raw.txt`。
- CMake FetchContent 原生库与随包 JS 资产：`native-dependency-licenses.md` ＋ `native-dependency-licenses.json`。
- Rust 层（`liboperit_ripgrep.so`）：`rust-dependency-licenses.md` ＋ `rust-crate-licenses.json`。48 个 lock 包按 `--target aarch64-linux-android` 的依赖图分成 32 运行期 / 4 构建期宏 / 3 宿主构建专用 / 9 从未解析，许可文本全为 MIT / Apache-2.0 / Unlicense / Unicode / BSD 家族，copyleft 计数为 0；另测出该 crate 自己没有 `license` 字段也没有 LICENSE 文件。
- Node 层（`assets/bridge/*`，1,378,177 字节）：`node-dependency-licenses.md` ＋ `node-dependency-licenses.json`。这一层与上面三层是同一天的第四层实测，本节下面的"第四轮"指的是取证手段（联网）不同，不要与"四层"混读。92 包闭包、随包许可文本与声明 92/92 一致、copyleft 计数 0；代价是本机无 `node_modules`，许可文本必须联网取 tarball，口径与前三层的离线读缓存不同。

依赖树层最重要的三条实测结论：

1. **copyleft 风险出在 Java 侧，不出在原生侧**。396 个选中坐标里 362 个是宽松许可，原生 11 个依赖的顶层许可全是 MIT / Apache-2.0 / zlib 家族；copyleft 系只有 5 个构件：`com.itextpdf:itextg:5.5.10`（POM 原文 `GNU Affero General Public License v3`，直接声明于 `app/build.gradle.kts:744`，仅 `util/DocumentConversionUtil.kt` 一处引用）、`ru.noties:jlatexmath-android:0.2.0` 及其两个字体包（GPL-2.0，Markdown/LaTeX 渲染 5 处）、`org.checkerframework:checker-compat-qual:2.5.3`（GPL2 + classpath exception，mediapipe 传递带入的注解包）。另有两个独立条款类构件：`com.github.junrar:junrar:7.5.5`（`UnRar License`，非开源许可）与 ML Kit 全系 15 项（专有服务条款）。**第四轮订正**：这一小节记 5 行，但真正无可选弱许可的 copyleft 构件是 **4 个**——`checker-compat-qual` 的 POM 是双许可，`<licenses>` 里除 GPL2+classpath exception 外还有 `The MIT License`，可择 MIT 而不承担 copyleft 义务；原判定来自"只读第一条 `<license>`"的脚本缺陷。订正后的完整分类见 `gradle-dependency-licenses.md` 第 3 节。
2. **这些不是"树里有但包里没"**。对 `C:/M0/A` 那次干净构建的 43 个 dex 直接搜类型描述符：`com/itextpdf/text` 1,814 处、`com/github/junrar` 235、`ru/noties/jlatexmath` 88，全部实打实在分发物里。**第四轮订正**：needle 统一为锚定形式后 itextg 为 `Lcom/itextpdf/text/` **1,808**，两种计数都留在 `upstream-license-retrieval.json` 的 `dex_needles_anchored`；表格扩到 11 个条目并新增链入证据的方向性警告（命中是正证据，未命中不是"没链入"的反证据）。6 处差额本轮已逐一定位（见 `upstream-license-retrieval.md` §1）。
3. **随包的"开源许可"界面与 POM 原文冲突 7 处**：`ui/features/about/screens/OpenSourceLicenses.kt` 硬编码 69 条，其中 iText 写成 `MPL/LGPL`（实为 AGPL-3.0）、junrar 写成 `The Unlicense`、JLatexMath 凭空加"classpath exception"、ML Kit 写成 `Apache-2.0`、Bouncy Castle 写成 `MIT`、FFmpegKit 写成 `LGPL-3.0`、ObjectBox 写成 `Apache-2.0`（实为三条并列，含 ObjectBox Binary Licence）。计划书 §10.2 禁止用措辞掩盖未实现的保护，这里是同一机制的反向用法——用措辞掩盖未履行的义务。**据此新登记 M0-B08。** **第四轮订正**：条数是 **68** 不是 69（原计数用的 `OpenSourceLibrary(` 模式把第 27 行的 `data class OpenSourceLibrary(` 声明也算成了一条；改用 `rg -c '^\s+OpenSourceLibrary\("'` 得 68），冲突按联网取证后是 **8** 处（新增 RenderX 一条，libsu 一条转为一致）；且该界面还有第二类问题不是"写错"而是"整层没写"——Rust、原生 JS 资产、Node、MathJax 与字体全部无条目，见 `upstream-license-retrieval.md` §6 末。

仍未闭合的：5 个构件（`libsu` core/nio/service、`Sable:axml`、`tech-pw:RenderX`）POM 与构件内都无许可文本，只到"本机不可证"，需向取上游 LICENSE 或移除，与 `accessibility.apk` 同类；kleidiai 无顶层许可文件但按 REUSE 规范授权（`LICENSES/` 目录 + 550 个 Apache-2.0 SPDX 头），不构成缺口。**这一条已被下面的第四轮复核取代。**

### 2026-09-19 第四轮复核（上游与自有 LICENSE 取证）

上一轮把 5 个构件标成"许可无法确定"，依据只是 POM 无 `<licenses>` 且构件内无许可文本。那是**本机不可证**，不是上游无授权——`com.github.*` 坐标出自 jitpack，其生成的 POM 按设计不携带许可声明。本轮改用 `raw.githubusercontent.com`、GitHub 的 HTML/atom 通道与 `jitpack.io/…/<version>/build.log` 联网取证，方法与逐条数据在 `upstream-license-retrieval.md` ＋ `upstream-license-retrieval.json`，此处只记结论与对上一轮的更正。

| 构件 | 本轮结论 | 处置 |
|---|---|---|
| `com.github.topjohnwu.libsu:core/nio/service:6.0.0` | **Apache-2.0 在钉住的 tag 上可证**：`raw/…/libsu/6.0.0/LICENSE` 11,357 字节，正文即 "Apache License Version 2.0"，该文件自 2018-01-25 起未变 | 许可侧结案。仍建议移除的理由是 B04（首版禁用 root），不是许可 |
| `com.github.Sable:axml:2.0.0` | 上游**当前 master** 是 Apache-2.0（`LICENSE.txt` 11,358 字节），但那条许可晚于 2.0.0；2.0.0 tag 上 `LICENSE`/`LICENSE.txt`/`COPYING` 等探测全部 404，而 2.0.0 是上游唯一的 tag——不存在"升级到带许可的发布版"这条路 | **未闭合**：我们分发的是没有授权文本的那个 jar。要么向作者取得对 2.0.0 的追认，要么改用 master 构建，要么整链移除 |
| `com.github.tech-pw:RenderX:1.0.0` | **分发的构件没有任何许可授予**，且不是"没找到"而是有完整反证链：jitpack 构建日志把缓存里那个 1,746,805 字节、SHA-1 `d1d4c451eaf5a743…` 的 AAR 定位到提交 `de7d1e33da264f99…`（`git describe` = `1.0.0-0-gde7d1e3`，恰在唯一 tag 上、无额外提交）；该提交的 14 个根条目里无许可文件，13 个常见拼写在 raw 上全部 404，四个构建脚本无 `license`/`scm`/`url` 行，`readme.md` 里 MIT/Apache/GPL/AGPL/BSD/Unlicense 六词 0 命中；仓库唯一一份 `LICENSE` 是三天后（2024-02-26）加在 main 上的 **GPL-3.0**，AAR 内唯一带许可字样的路径是它捆绑的 `assets/MathJax/LICENSE`（那是 MathJax 的 MIT，不是 RenderX 对自己的授权） | **新登记 M0-B09。** 两种解读都要求处置：无授予即保留所有权利，我们无再分发权；GPL-3.0 则界面写的 "MIT" 是凭空加冕。删除代价本轮实测为**零源码改动**（Kotlin/Java 引用 0 处，另有 5 处真实调用走 Apache/GPL 系的 jlatexmath 那条路），代价与附带收益见 `upstream-license-retrieval.md` §5 |

本轮同时更正了上一轮三处分类：`checker-compat-qual` 由 copyleft 改为双许可可择 MIT（第 1 条结论的计数因此从 5 降到 4）；`OpenSourceLicenses.kt` 条目数 69 → 68、冲突 7 → 8；dex needle 统一为锚定形式后 itextg 计数 1,814 → 1,808。锚定/非锚定的 6 处差额本轮逐一定位：全部在 `classes39.dex` 的字符串池里，是 `com/itextpdf/text/pdf/fonts/cmaps/`、`…/glyphlist.txt`、`…/hyphenation/hyph/`、`…/l10n/error/` 这类**资源路径字面量**（前一字节为 ULEB128 字符串长度），不是类型描述符——故 itextg 的真实类链入面以 1,808 为准，脚本与逐条偏移见本目录 `scripts/itextg_ctx.py` / `scripts/itextg_ctx.json`。

取证方法上有两条坑值得留在记录里，因为它们各自能让读者得出反向结论：`api.github.com` 在取证中途耗尽未认证配额（`403 … for 23.172.200.83`，即代理出口 IP），所以仓库元数据只能从 raw/HTML/atom 通道取，**不能把 JSON 里 `repo_api` 为空读成"仓库不存在"**；RenderX 的说明文件叫 `readme.md`（全小写），只按惯例大小写探测会得到"没有 README"的错误结论。

顺带补齐了本文件开头一直标着"待复核"的自有许可。这一层不能只报"类别是 LGPL"，必须报到"文本有没有被改写、能不能直接拿去交付"，所以比对做到词级：

- **主仓库 `LICENSE` 干净**：与 FSF 官方 `https://www.gnu.org/licenses/lgpl-3.0.txt`（7,652 字节、165 行、SHA-256 前 16 位 `e3a994d82e644b03`）**逐字节相同**，唯一差别是仓库里那份用 CRLF 行尾（7,652 + 165 = 7,817 字节），词级 diff 为 0 处改动。注意官方 `lgpl-3.0.txt` 本身就只有 §0–§6，对 GPL-3.0 是**援引而非内嵌**——所以"里面没有 GPL 全文"不是缺陷，是我们成文时要另补的东西。
- **`terminal/LICENSE` 类别正确、条款词未被改写，但作为交付文本不完整**：词级 diff 显示官方 1,234 个词全部按序出现，替换/删除 **0** 组，只有 3 处插入——中文标题与"版权所有 (C) 2023-2024 Operit AI 项目团队"、正文末一个多余的 `-` 词、以及 LGPL 正文之后另接的 GPL-3.0 标题＋序言开头，然后用一句非规范的编辑性括注"[The full text of the GNU General Public License v3.0 follows, but is omitted for brevity. The full license can be obtained from https://www.gnu.org/licenses/gpl-3.0.html]"把 GPL 正文截掉。**方法教训一条**：先前按行对齐得到的"只有 17/165 行一致"是假信号——文件开头多一个空行就会让后文每一行都错位，许可文本必须做词级/字符级比对才能下"是否被改写"的结论。判据与脚本见 `scripts/own_license_compare.py`。
- 实际动作（两份一样，但理由不是上游违约）：**随包/随源码的许可正文一律取 FSF 官方 LGPL-3.0 与 GPL-3.0 全文**（两者都允许逐字再分发；LGPL 正文自己也写着 "Everyone is permitted to copy and distribute verbatim copies of this license document"），上游那两个文件只作为出处与版权声明保留。否则我们会把一份 GPL 正文被截断、且带着"为篇幅省略"这类编辑性说明的文本再分发一次——那正是计划书 §10.2 反对的"用措辞掩盖未尽义务"的形态。

`tools/hotbuild/OperitNightlyRelease` 本轮实测为"未检出 + 构建脚本零引用"，不产 APK 侧义务；但它只有 SSH 地址、无授权取不到，属发布流水线复现待办而非许可缺口。

补做的一条（同一套通道，落在 `upstream-license-retrieval.md` §8）：**`accessibility.apk` 的上游取证已完成，`desktop.apk` 的来源侧也拿到正向证据**。方法上多了两条比"探文件名"硬的通道——`git ls-remote` 判分支真伪，和读产物自带的 `META-INF/version-control-info.textproto`。结果是：accessibility 的上游仓库只有 `main` 一个分支、无 tag 无 release、整棵归档 78 条目零授权文本（第二轮写的"main/master 均无 LICENSE"里的 master 是分支改名遗留的假分支，raw 端点仍回 200，这一点已在 §8 订正）；随包 1.5 的 textproto 记录的 revision 恰是 `main` 头那个提交，而该提交自带的版本字段是 1.4 ⇒ **分发物相对可核验来源含未记录的本地改动**。desktop.apk 的 textproto 直接是 `NO_SUPPORTED_VCS_FOUND`。

仍未闭合的：ObjectBox Binary Licence 与 UnRAR License 的**条款正文**（本轮只确认 POM 给出的名称与链接）；`gilroy`/`balsamiqsans`/`poppins`/`roboto`/`jetbrains_mono_nerd_font` 五个字体家族均无随包授权文本，其中 12 个字体二进制是随 RenderX 进来的，删依赖会一并带走；`desktop.apk` 对应的公开仓库（若存在）是哪一个——它零引用、已建议删除，未继续投入。**（第五轮订正：UnRAR 的条款正文已取得并逐字附出，ObjectBox 的仍未取得且理由变了；字体一侧"五个家族 / 12 个二进制"这个说法不成立——APK 里实测 83 个字体条目、分属 7 个来源，逐条归属见 `THIRD-PARTY-NOTICES.md` §8.1，其中 `poppins` 根本没有作为字体本体进包，随 RenderX 进来的是 11 个 `res/font` 二进制再加 22 个 MathJax 下的 TeX 派生字体。详见下面的第五轮。）**

### 2026-09-19 第五轮：`THIRD-PARTY-NOTICES` 成文、条款正文取证、字体条目归属

前四轮把料备齐了，这一轮做的是"把四层原料合成一份能交出去的东西"，以及成文过程里必须补齐的三件事：条款正文、随包字体的逐条归属、以及引用不到的许可到底为什么引用不到。

**成文方式**：`THIRD-PARTY-NOTICES.md` 由 `scripts/third_party_notices.py` 整体生成，原料是 `build-manifest.json` 与前四轮的四个层 JSON、`upstream-license-retrieval.json`、`apk-font-entries.txt`、`licence-texts/sources.json`。脚本里凡遇"无法归类的许可字符串""无出处的字体条目""声明了正文却取不到的条款"一律 `raise SystemExit`，不做兜底措辞——所以文件里每个数字都能回指一次测量，重跑即可复核。**不引用应用内 `OpenSourceLicenses.kt`**：它有 8 条错误声明且整层缺失（第三、四轮已记）。

**条款正文**：`licence-texts/` 存 17 份正文，`sources.json` 逐份记 URL、字节数、SHA-256 前 16 位、行数、取回时间与取用通道；生成脚本会逐条对账，URL 或指纹与脚本内常量不一致就直接失败。三条反面记录同样是结论的一部分：

1. `gnu.org/licenses/agpl-3.0.txt` 经代理三次 curl 全部状态 000，改用 SPDX 镜像 `AGPL-3.0-only.txt`（34,020 字节、235 行，`Preamble` 与 `How to Apply These Terms` 齐全）。**这一条要在文件里写明是替代来源**，不能让人以为出自 FSF 原始地址。
2. SPDX `licenses.json` 实测 740 项里没有 `EDL-1.0` 与 `BouncyCastle` 这两个 id，所以 `text/` 端点必然 404——不是网络问题。改取权利方页面并把 HTML 还原为纯文本（原始 HTML 留在 `licence-texts/raw_*`）。顺带一条容易写错的事实：Eclipse 官方在 EDL-1.0 页面上把它的 SPDX 标识标为 `BSD-3-Clause`。
3. ObjectBox Binary Licence 仍然没拿到，而且这次知道为什么拿不到：POM 的 `url` 只指向站点根；站点公开的那份 PDF 是《Terms Of Use》，其 §2 明说产品条款另在下载处提供——即它不是 Binary Licence；且其 §5 禁止再制该文档内容，就算拿到也不得复制进仓库。缺口从"没找到"变成"这条通道本身不通"，处置回到负责人。

**UnRAR 条款正文已闭合**（`junrar/junrar` 的 `LICENSE`，1,861 字节 / 39 行，SHA-256 前 16 位 `9d90c3649d4eb064`，逐字附在 `THIRD-PARTY-NOTICES.md` §9.14）。取的是**被分发版本自己的 tag**：`git ls-remote --tags` 实测 `v7.5.5` = `dabca2849b463847…`，raw 端点取回的文件与 `master` 同名文件逐字节相同，且 `.atom` 显示该文件在 master 上只有一次提交（`60f17a1f49b91d70…`，2020-07-18T08:47:18Z），早于 7.5.5——所以不存在 `axml` 那种"授权文本晚于被分发版本"的效力问题，这一步是必要的而不是讲究：`junrar-7.5.5.jar`（缓存路径段 `4b1300aa…`，161,628 字节，118 条目）内部**没有任何** `LICENSE`/`NOTICE`/`COPYING`，只看构件会得到"无授权文本"的错误结论。闭合后暴露出一个新事实：该条款第 2 条要求**在文档与源码注释里写明"该代码不得用于开发 RAR 兼容压缩器"**——本 fork 至今任何位置都没有这句。这一条不需要外部信息，属本仓库可自证项，已登记在 `THIRD-PARTY-NOTICES.md` §2 第 3 条。

**字体条目归属**（推翻第四轮那句"五个家族、12 个随 RenderX"）：从审计产物 `unzip -Z1` 导出 83 个字体条目（`apk-font-entries.txt`），再用三种通道定位来源——列举 RenderX AAR 内的 `res/font`、在 Gradle 缓存里对每个 AAR/JAR 按条目名全库检索（`roboto_medium_numbers.ttf` 唯一命中 `exoplayer-ui:2.19.1`）、以及 `:terminal` 子模块自带文件（`jetbrains_mono_nerd_font_regular.ttf`）。结果：RenderX 的 `res/font` 11 个、同 AAR 的 MathJax TeX 派生字体 22 个、jlatexmath 46 个、pdfbox 1 个、仓库自身 `assets/fonts` 1 个、exoplayer-ui 1 个、`:terminal` 1 个，合计 83。`poppins_bold.xml` 是 font-family 声明不是字体本体，所以 `poppins` 这一支根本没进包；`gilroy` 4 个条目确实存在且随一个无许可授予的构件进来。

**顺带修掉一处证据缺陷**：`node-dependency-licenses.json` 里 `distributed_js.*.path` 两个值都缺 `bridge/` 段（写成 `assets/index.js`），实际度量对象是 `assets/bridge/index.js`。用的是那条路径而非另一条，所以测量本身有效，登记的路径是错的——已就地改正。

### 仍未开始的部分（第五轮之后）

- 声明文件的**随包位置与应用内呈现**仍未定：`THIRD-PARTY-NOTICES.md` 现在只是仓库根的一份文本，M1 要决定它是打进 `assets/`、还是由一个重写过的"开源许可"界面逐条渲染（后者必须修掉 `OpenSourceLicenses.kt` 那 8 条错误声明并补上缺失的四层，不是把现界面改改字数）。
- Node 层已展开（`node-dependency-licenses.md` ＋ `.json`：92 包闭包、许可文本 92/92 与声明一致、copyleft 0）。同轮否掉了一条早先的猜测：`web-chat/` **当前不进分发**（`.gitignore:74` 忽略该 assets 目录、`app/build.gradle.kts` 全文零引用、APK 5,550 条目里 `assets/web-chat*` 0 命中），所以 `:app:syncMainAssets` 不构成缺口；M1 若真启用 Web Chat，那一层须按同一方法补测。`subpack` 的 `flutter_assets/NOTICES.Z` 随该载荷移除而消失。
- 逐构件"是否真的链入产物"仍未全部证明，但覆盖面已扩大：JVM 侧 dex 检索从 4 个条目增至 11 个 needle / 7 个高风险条目（`gradle-dependency-licenses.md` 第 4 节）；Rust 侧 13 个 crate 有内嵌源码路径正证据、其余只到依赖图；Node 侧 92 包里只有 10 个有字符串字面量正证据；原生嵌套许可仍只数到文件存在，未按 CMake target 源文件列表收敛。
- 已登记但**未处置**的授权文本缺口：`accessibility.apk`（上游整棵树无 LICENSE 文件，仅 README 徽章；取证已完成，处置随 M1 载荷移除）、`desktop.apk`（全仓零引用＋构建时不在 git 工作树内，建议删除）、ObjectBox Binary Licence 的条款正文（UnRAR 那份第五轮已取得）、无随包授权文本的字体（`res/font` 的 `gilroy` 4／`balsamiqsans` 2／`roboto` 5 随 RenderX 进来，另有仓库自身的 `material_symbols_rounded_static.woff2` 与 `:terminal` 的 `jetbrains_mono_nerd_font_regular.ttf`；逐组归属见 `THIRD-PARTY-NOTICES.md` §8.1）、`tools/mcp_bridge` 与根 `package.json` 的自述许可矛盾（`ISC`/`MIT` 对仓库整体 `LGPL-3.0-only`）、`operit_ripgrep` 无 `license` 字段。junrar 的 UnRAR 条款第 2 条要求的那句声明仍未写进任何文档或源码注释——这条是**本仓库自己就能做完**的，不需要外部信息。

## M0 必须补齐

- 固定仓库 commit、子模块 commit、Gradle/JDK/SDK/NDK/Node 版本。
- 从实际构建配置导出所有运行时和打包依赖，逐项记录坐标、版本、来源、许可证、NOTICE 和是否进入 APK。
- 对外部 ZIP/AAR/原生库保留来源 URL、下载日期、文件 SHA-256 和可再获取证明。
- 确认 LGPL 修改源码、对应源码、许可声明、第三方组件和签名/构建材料的分发义务。
- 发现任何来源/许可未知的关键分发组件时，保持 M0 阻塞，不以替代文字放行。
