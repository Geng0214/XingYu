# M0 验收证据目录

状态：构建侧与静态侧证据已成型；真机安装、出网抓包与落盘扫描仍缺（本机无 ARM64 设备）。

## 记录要求

每条用例必须记录：用例 ID、源码完整 commit、APK SHA-256（如有）、设备/系统、模型/参数、前置数据、步骤、预期、实际、证据路径、执行者、复核者。

状态只允许：`未执行`、`通过`、`失败`、`阻塞`、`不适用（说明原因）`。

打印任何哈希一律截断到 16 位（完整值写进 `build-manifest.json`），否则会被本仓库的密钥扫描器判为疑似密钥。

## 索引

### 结论与决策

- `acceptance-log.md`：M0-A01 至 A06 逐条状态、本轮完成 / 本轮未做。
- `decision.md`：路线与放行决策，阻塞项 M0-B01 至 M0-B09。
- `capability-gap.md`：V2.0 首版能力与当前代码入口差距。

### 基线与构建

- `upstream-baseline.md`：源码、子模块、构建依赖和基线状态。
- `build-manifest.json`：机器可读的唯一事实来源（基线定义、工具链、外部输入哈希、两次产物对比、`knownGaps`）。
- `m0-remediation.patch`、`terminal-m0-remediation.patch`：基线之上的工作树偏离，`build-manifest.json` 的 `sourceDeviationsFromBaseline` 逐项对应。
- `materialize-*.log`：`ci/script/materialize_m0_clean.sh` 的物化记录（短根 A、B、B 重试）。
- `build-A-shortroot.log`、`build-B-shortroot.log`、`build-B-shortroot-2.log`：两份干净目录不跳门禁的构建日志。
- `build-clean-*.log`、`build-m0-a01-longroot-failure.log`：早期诊断轮次，含长根失败与跳门禁对照；按计划书 §10.2 不作为可交付 APK 证据。
- `build-env-msbuild-proxy-collision.txt`：代理变量大小写双写打挂 MSBuild 的复现与对照。
- `native-build-path-embedding.txt`：两份产物逐字节差异的节归属与指令解码，兼分发物携带构建者身份（M0-B07）。
- `ripgrep-source-rebuild.txt`：`liboperit_ripgrep.so` 的源码级重建与逐字节比对。

### 依赖与许可

- `license-inventory.md`：总览与五轮复核结论（内嵌分发物 → 依赖树 → 上游取证 → 内嵌 APK 上游 → 声明成文）。
- `gradle-dependency-licenses.md` / `.json` / `dependency-tree-clone-runtime.raw.txt`：Gradle/JVM 依赖树许可实测（396 个选中坐标）。
- `native-dependency-licenses.md` / `.json`：CMake FetchContent 原生依赖与随包 JS 资产许可实测。
- `rust-dependency-licenses.md` / `rust-crate-licenses.json`：`liboperit_ripgrep.so` 的 Rust 层（Cargo.lock 48 包）实测与按目标的链入划分。
- `node-dependency-licenses.md` / `.json`：`assets/bridge/*.js` 两支 ncc bundle 与 `pnpm-lock.yaml` 的 92 包闭包实测（含 bundle 内正向归属取证与 ncc 元数据盲区）。
- `upstream-license-retrieval.md` / `.json`：jitpack 三组构件（5 个坐标）的上游 LICENSE 联网取证（libsu 结案、axml 待追认、RenderX 无授予 → M0-B09）、与随包许可界面的对账、自有两份 `LICENSE` 的字节级复核，以及 §8 的两个内嵌 APK 上游取证。
- `../THIRD-PARTY-NOTICES.md`：可交付的第三方声明正文（全文 10 节），由 `scripts/third_party_notices.py` **只读**上述四层 JSON 与 `licence-texts/` 生成——生成器里没有一个手写计数，也不写回退措辞，映射不上就直接失败。第 2 节是 15 项未闭合处置，第 9 节收录 17 份许可全文。
- `licence-texts/`：17 份许可条款正文 + `sources.json`（逐文件的 URL / 通道 / 字节数 / SHA-256 前 16 位 / 行数 / 取回时间，另记 3 条反面结果）。由 `scripts/licence_texts_sources.py` 产出；`sources.json` 与磁盘文件不一致时生成器拒绝出文。
- `apk-font-entries.txt`（83 个条目）与 `font-carrier-scan.json`：字体载荷的条目级归属——前者从被审计 APK 的条目表筛出全部字体资产，后者用 `zipfile.namelist()` 全等匹配在依赖缓存 1,263 个归档里定位非 RenderX 字体的载体（`roboto_medium_numbers.ttf` 恰 1 命中 exoplayer-ui AAR，`jetbrains_mono_nerd_font_regular.ttf` 0 命中即 `:terminal` 子模块自带）。由 `scripts/font_carrier_scan.py` 产出。
- `scripts/`：上述各轮实际执行过的脚本原文与其输出（现 56 个文件 = 49 支脚本 + `README.md` + 6 份 JSON 输出），含重跑前提、每支脚本对应哪一节、已被取代的版本，以及两笔记下的反面记录（`xcheck2.py` 的模糊匹配、行级许可文本比对）。

### 安全与运行面（静态）

- `execution-entrypoints.md`：执行层入口盘点与 M1 真机验证行 E01–E17。
- `runtime-audit.md`：静态审计与误判修订表。
- `storage-network-inventory.md`：权限、存储、缓存、后台、监听和出网风险。
- `signing-material-scan.txt`：APK 全条目签名材料扫描。

## 待补现场证据

- `install-and-smoke-record.md`：真机安装 + 安装录像（M0-A01 剩余缺口）。
- `network-capture.md`：关闭 NAS 后独立网络聊天 10 次的出网清单（M0-A03）。
- `storage-scan.md`：合成 Key / 正文落盘扫描与恢复演练（M0-A04、M0-B05）。
- `license-review-signoff.md`：负责人对 `../THIRD-PARTY-NOTICES.md` 第 2 节 15 项处置项与许可矩阵的签字。成文本体已在索引"依赖与许可"一节列出，不再待补；仍待补的是**处置**：依赖未删未换、`OpenSourceLicenses.kt` 的 8 处错误声明未改、UnRAR 第 2 条义务未写进文档、ObjectBox Binary Licence 正文未取得、83 个字体条目只有载体归属而无上游授权文本，以及这份声明以什么形式随包（APK 内资产还是分发页）与是否在应用内加入口。
