# M0 真机前静态网络、存储、权限与后台审计

日期：2026-09-18
状态：静态审计完成；真机和抓包未完成

## 结论

当前 `operit` 仍是功能完整的 Operit 平台，不满足 V2.0 首版“普通聊天、工具默认拒绝、无常驻后台、私有数据不落公共目录”的放行条件。以下项目继续阻塞 M0，不能以静态审计替代真机证据。

## 权限和外部入口

`app/src/main/AndroidManifest.xml` 当前声明了：

- `MANAGE_EXTERNAL_STORAGE`、读写外部存储、`REQUEST_INSTALL_PACKAGES`
- `SYSTEM_ALERT_WINDOW`、`WRITE_SETTINGS`、`PACKAGE_USAGE_STATS`
- Root/Shizuku 相关 Provider 和权限
- 麦克风、相机、短信、电话、位置、蓝牙
- 开机广播、精确闹钟、多个前台服务类型、唤醒锁
- `QUERY_ALL_PACKAGES`
- `android:allowBackup="true"`
- `android:usesCleartextTraffic="true"`

这些声明与首版范围冲突，需在产品变体中收敛，而不是仅隐藏界面按钮。

## 存储路径

`OperitPaths.kt` 显示以下数据会写入公共 `Download/Operit`：

- `plugins/`、`mcp_plugins/`、`bridge/`
- `exports/`、`workspace/`、`test/`
- `websession/userscripts/`
- 图片、媒体和技能包池（由传入 baseDir 决定）

向量索引位于应用私有 `filesDir/.vector_index`，Sherpa 模型位于应用私有 `filesDir/.sherpa_ncnn_models`。公共目录仍可能包含插件、会话、工作区、导出和附件等可复原数据，必须在 M0 完成迁移或明确纳入保护契约。

## 启动和后台

`OperitApplication.initializeMainApplicationLocked()` 当前会：

- 初始化 `AIMessageManager`
- 初始化内置插件注册表
- 派发应用生命周期插件事件
- 检查并启动全局 AI 前台服务
- 启动长期记忆自动保存轮询器
- 初始化 `AndroidShellExecutor` 和 Shower 虚拟屏 Shell 环境
- 初始化 WorkManager/Workflow 相关能力

这证明首版目前仍会初始化工具、插件、后台、Shell 和自动记忆路径，需增加首版产品策略并在执行层统一拒绝。

## 备份和迁移

`backup_rules.xml` 与 `data_extraction_rules.xml` 当前只排除 `codex_oauth_credentials.xml`，没有覆盖 Room、ObjectBox、向量索引、附件、缓存、日志、公共工作区和导出目录。`allowBackup` 仍为 `true`。在完成数据分类和恢复覆盖测试前，不得宣称备份安全。

## 构建现场新增证据（2026-09-19 修订）

本节此前四条"资源不可得"结论经实测推翻，记录如下以免再次被当作阻塞前提。

| 原结论 | 实测 |
|---|---|
| Google Drive 返回 `502 Bad Gateway`，`libs.zip`/`subpack.zip`/`jniLibs.zip` 不可得 | **误判**。本机代理监听 `127.0.0.1:7897`，但 Bash 工具进程不继承交互 shell 的代理变量；显式设 `https_proxy` 后三个归档均 HTTP 200 并成功解包。直连 `huggingface.co` 实测 `curl` 返回 `000`（超时），所以代理是真实前置条件，不是可选优化。 |
| 本机无 `cargo`/`rustup`，无法生成 `liboperit_ripgrep.so` | **半误判**。`jniLibs.zip` 已附带预编译 `arm64-v8a/liboperit_ripgrep.so`（3,599,272 字节，sha256 前缀 `71b6e8af`），且不在 `prepare_android_dependencies.py` 的排除列表内。Rust 工具链已另行安装，但 `cargo` 在本机仍不可用：缺少任何 C++ 链接器（无 Visual Studio Build Tools、无 mingw），所有 build script 以 "you may need to install Visual Studio build tools" 失败。因此按上游预编译产物构建，源码级复现留作缺口。 |
| `app/libs/ffmpeg-kit-local.aar` 缺失且无法获取 | 已从 `libs.zip` 解出（15,264,401 字节，sha256 前缀 `b292b042`），`:app:compileCloneAidl` 的前置条件满足。 |
| 跳过 STT/原生库门禁的诊断构建 | 结论保留：带 `-x` 的构建不构成放行证据。本轮改为不跳门禁，并把代理写入 JVM 参数使 `:syncSttModelAssets` 能自行下载校验。 |

其余确认：Android SDK 位于 `C:\Users\15942\dev\sdk\android-sdk`；`adb` 可用但 `adb devices -l` 当前仍无设备。NDK `27.3.13750724` 已固定到主 app 与全部含 CMake 的 library 模块（含 `terminal` 子模块），共 7 个模块此前未声明 `ndkVersion`，属对冻结源码的偏离，登记在 `build-manifest.json`。Gradle wrapper 的 `distributionUrl` 指向 `mirrors.aliyun.com`，但 `distributionSha256Sum` 已钉死，属上游原样配置，非本机改动。

## assets 内嵌外部运行时（能力面与扫描盲区）

实测口径不是"某个目录里有什么"，而是真正进入 APK 的合并资产集。干净目录 `C:/M0/A` 产出的 `app-clone.apk` 内 `assets/` 共 823 个条目、312,312,665 字节，构成 413,136,362 字节 APK 的主体（开发目录中间产物 `mergeCloneAssets` 快照为 821 个文件、312,220,338 字节；包内多出的两项是 `org/apache/commons/math3/exception/util/LocalizedFormats_fr.properties` 与 `org/apache/commons/math3/random/new-joe-kuo-6.1000`（Apache Commons Math 的语言包与 Sobol 序列数据），它们在 `mergeCloneAssets` 之后的打包阶段并入，具体注入点本轮未定位）。其中可执行 / 可安装包类载荷 15 个，合计 146,207,921 字节，逐项字节数在开发目录样包与干净目录产物上完全一致：

| 文件 | 字节 | 来源根 | 判定 |
|---|---|---|---|
| `ubuntu-noble-aarch64-pd-v4.18.0.tar.xz` | 64,133,552 | `:terminal` | proot-distro Ubuntu 24.04 rootfs（XZ + tar，12,684 成员），完整 Linux 用户态 |
| `subpack/android.apk` | 48,139,093 | `:app` | Flutter 构建的独立 Android 包（AGP 8.6.0，3 ABI），431 成员 |
| `subpack/windows.zip` | 11,412,717 | `:app` | Windows 桌面运行时：`assistance_subpack.exe`、`flutter_windows.dll`、WebView2 插件 DLL |
| `desktop.apk` | 6,489,542（版本 1.0） | `:app` | 桌面模式辅助安装包 |
| `templates/android/tools/aapt2/aapt2-arm64-v8a` | 4,706,040 | `:app` | ELF 可执行；资源编译器，随"在手机上建 Android 工程"模板分发 |
| `templates/flutter/android/tools/aapt2/aapt2-arm64-v8a` | 4,706,040 | `:app` | 同上，Flutter 模板副本 |
| `accessibility.apk` | 2,763,980（版本 1.5） | `:app` | 无障碍自动化辅助安装包 |
| `shizuku.apk` | 2,571,773（版本 13.6.0） | `:app` | Shizuku 官方包，提权路径 |
| `shower-server.jar` | 1,133,303 | `:app` | Java 服务端，虚拟屏 / Shell 通道 |
| `templates/**/gradle-wrapper.jar` | 59,203 ×2 | `:app` | Gradle 引导包 |
| `operit_shell_exec` | 21,496 | `:app` | ELF；Shell 执行辅助二进制 |
| `templates/**/setup_android_env.sh` | 40,011 / 19,910 | `:app` | 在设备上装配 Android 构建环境的脚本 |
| `setup_fake_sysdata.sh` | 7,605 | `:terminal` | 伪造 `/proc` 节点以骗过 rootfs 内程序 |
| `main.jar` | 3,656 | `:app` | 小型 Java 载荷 |

`:terminal` 另带两个首版要关闭的入口：`utils/FtpServerManager.kt`（Apache FtpServer，经 `ui/SettingsViewModel.kt` 暴露开关，用户一启用就在设备监听端口）与 `provider/UbuntuDocumentsProvider.kt`（把 rootfs 内文件挂成系统文档提供者，根路径 `filesDir/usr/var/lib/proot-distro/installed-rootfs/ubuntu`）。两者都要进 M1 执行层禁用清单和真机出网清单。

体积结论：首版范围（纯文本陪伴）明确不用的三块——STT 模型 142,635,813 B + Ubuntu rootfs 64,133,552 B + subpack 59,551,810 B = **266,321,175 B，占整包 64.5%**。这是 v1 瘦身的第一优先项，且与许可缺口同向：7 项 sherpa 资产无许可声明而语音不在首版范围内，正确处置是移出分发而不是继续打包。

对应代码在 `core/subpack/`：`ApkEditor.kt`、`ApkReverseEngineer.kt`、`ExeEditor.kt`、`KeyStoreHelper.kt`，即 App 能产出并签名可安装的 APK 与可执行桌面程序。计划书 §1.2 与 M1-T02 明确排除这套能力，首版必须在执行层禁用并核对后台与导出入口，只隐藏界面按钮不算关闭。

对 M0-B02 门禁的影响：`verifyNoEmbeddedSigningMaterial` 此前只以 `:app/src/main/assets` 与生成的 STT 目录为根，**看不见 `:terminal` 等库模块的 `src/main/assets`**，而 AGP 的 `merge<Variant>Assets` 恰恰会把它们合并进包——上表中 rootfs 与 `setup_fake_sysdata.sh` 就落在盲区内。现改为枚举 `rootProject.subprojects` 的标准资产根（命中路径带模块 path），并保留展开一层 `.apk/.zip/.jar/.aar` 的成员名匹配。

门禁实测（2026-09-19，`./gradlew :app:verifyNoEmbeddedSigningMaterial`）：

| 步骤 | 结果 |
|---|---|
| 放入 `terminal/src/main/assets/probe-scan-check.keystore` 假探针 | `FAILED`，报 `Signing material must not be embedded in application assets: :terminal/src/main/assets/probe-scan-check.keystore`（24s） |
| 删除探针后重跑 | `BUILD SUCCESSFUL`（10s），`terminal/src/main/assets/` 回到只含 rootfs 与 `setup_fake_sysdata.sh` |

rootfs 的成员名已单独流式核查（`tar tvfJ`，12,684 成员）：146 个 `.pem` **全部**位于 `etc/ssl/certs/`，是公共根证书及其软链；`etc/ssl/private/` 是空目录；`.key` / `.p12` / `.pkcs12` / `.jks` / `.keystore` / `ssh_host*` / `id_rsa` 命中数均为 0。结论是 rootfs 不含私钥材料。但这同时说明**只按扩展名判定的规则不能直接扩展到 XZ 归档**——否则 146 个公共证书会全部变成误报。要让门禁管住 tar/xz，需要区分公钥证书与私钥（解析 PKCS#8 结构，或至少结合路径与 `PRIVATE KEY` 标记），而不是再加一个扩展名。

`KeyStoreHelper` 现状核对：导出签名密钥改为安装在私有 `noBackupFilesDir/export-signing.p12`，首次使用时生成随机 RSA-2048 自签名证书，随机口令写入同目录 `export-signing.password`，并主动删除 `filesDir/jks.jks`、`filesDir/pkcs12.keystore` 旧内置材料；不再从 assets 或固定口令加载。该类与用户凭据存储无关，此前"它可能保护 API Key"的推测是错误的。

## 真机待执行

1. 用不跳门禁的根任务完成两份干净目录构建，记录包名、版本、ABI、APK SHA-256 与签名证书指纹。
2. 关闭 NAS/外部服务，执行 10 次独立聊天，记录 HTTP 目的 host、DNS、重定向、401/403/404/429/5xx、超时和取消行为。
3. 对聊天正文、API Key、向量、附件、缓存、日志、导出、备份和恢复目录执行合成标记扫描。
4. 证明工具、MCP、Workflow、HTTP/Web Chat、Root/Shizuku、无障碍、Shell、subpack 产物生成、后台服务和外部 Intent 均默认拒绝。
5. 核实内嵌 APK/EXE 的安装、启动与自更新触发路径在真机上不产生监听端口与额外授权弹窗。
