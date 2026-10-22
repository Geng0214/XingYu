# M0-T01/T02 上游与构建基线

记录日期：2026-09-18

## 当前 checkout

| 项 | 值 | 状态 |
|---|---|---|
| 远端 | `https://gh-proxy.com/https://github.com/AAswordman/Operit.git` | 待确认是否改为官方远端镜像/只读上游 |
| 分支 | `main` | 阻塞：未冻结项目候选 tag |
| HEAD | `31646978aa03520f36916a9efd1d3f508b5ca3e0` | 已记录 |
| 本地 `v1.12.2` tag | 不存在 | 阻塞 |
| 工作树 | 当前包含 M0 密钥整改及证据文档改动，尚未提交 | 已记录 |

计划书给出的候选是 `v1.12.2` / 短提交 `dbf7191`，但当前 checkout 与该候选不一致。M0 必须重新解析完整 commit，不得把当前 `main` 直接当稳定基线。

## 模块和子模块

根 Gradle 模块：`:app`、`:dragonbones`、`:terminal`、`:mnn`、`:llama`、`:mmd`、`:fbx`、`:showerclient`、`:quickjs`。

Git 子模块：

- `terminal` → `https://github.com/AAswordman/OperitTerminalCore.git`，当前工作树记录为 `045868988e4091d613cb34b2941057703899b96e`。
- `tools/hotbuild/OperitNightlyRelease` → SSH 地址，当前工作树记录为 `350165237e89b370e45213dcd7ab02ee7e7a6361`；需确认是否为发布必需依赖，及在干净环境的可获得性。

## 构建配置静态结果

- Android namespace/applicationId：`com.ai.assistance.operit`。
- compile SDK：36；target SDK：34；min SDK：26。
- AGP：8.13.2；Kotlin：2.2.21；ObjectBox：5.3.0。
- 根构建已有 `assembleDebugClone`，clone build type 使用 `.clone` applicationIdSuffix 和 `app-clone.apk` 输出名。
- 构建还依赖外部 `subpack.zip`、`jniLibs.zip`、`libs.zip`、终端子模块、CMake FetchContent 和 STT 模型资源；当前未完成来源/版本/哈希冻结。
- 官方 BUILDING 文档与实际 `app/build.gradle.kts` 存在版本/资源说明差异，最终以冻结源码和真实构建日志为准。

## M0-T02 现场构建

状态：阻塞。两份独立副本已写入 `local.properties` 并固定 NDK `27.3.13750724`，正确根任务为 `assembleDebugClone`。该任务已越过最初的 SDK、任务路径和损坏 NDK 阶段，但仍未生成 APK。

最新真实构建结果：

- `build-clean-1-root-task-ndk-fixed.log`：配置阶段越过 NDK；因本地 SDK 缺少 Build Tools `35.0.0`，自动安装时失败。
- `build-clean-2-root-task-ndk-fixed.log`：Build Tools `35.0.0` 成功安装并继续执行；`app:syncSttModelAssets` 从固定 Hugging Face URL 下载时连接超时；同时多个模块的 Kotlin 编译在 Gradle 缓存写入 `core-viewtree-1.0.0-api.jar` 时失败。
- `build-clean-1-skip-stt-no-cache.log`：仅用于诊断，跳过 STT 和 Gradle build cache 后，命中外部 `liboperit_ripgrep.so` 缺失门禁；并出现 Kotlin 增量缓存 `AccessDeniedException`。
- `build-clean-2-skip-stt-no-cache.log`：仅用于诊断，命中 `terminal:compileDebugAidl` 的 Gradle 临时文件 `FileNotFoundException`。

未生成 APK，不能记录 APK SHA-256 或进行安装验证。带 `-x` 跳过完整资产/原生库门禁的诊断命令不构成 M0 可交付构建证据。本次构建仍是当前整改工作树候选，不等同于最终冻结基线。

待执行：

1. 为固定 STT 资产提供可复现的本地/缓存输入，或在构建环境中恢复可验证的网络下载。
2. 使用现有 `tools/native_ripgrep/build_native_ripgrep.ps1` 生成并核验 arm64 `liboperit_ripgrep.so`；当前环境未发现 `cargo`/`rustup`。
3. 获取仓库 CI 要求的 `libs.zip`、`subpack.zip`、`jniLibs.zip`，经 `ci/script/prepare_android_dependencies.py` 解包；当前 Google Drive 下载被本机代理返回 `502 Bad Gateway` 阻塞，且工作树缺少 `app/libs/ffmpeg-kit-local.aar`。
4. 解决 Windows 下 Kotlin 增量缓存和 Gradle 临时文件的访问/并发问题，再以不跳过门禁的根任务构建。
5. 生成两份 APK，记录路径、大小、SHA-256、包名、版本、ABI 和证书指纹。
5. 安装到 ARM64 真机，记录系统、RAM、存储和安装结果。
6. 只在两次完整可复现后把 M0-A01 标为通过。
