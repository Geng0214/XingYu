# M0-T03 能力差距表（静态首轮）

状态说明：本表只依据源码入口和文档完成静态盘点；“待验证”不等于已实现。

| V2.0 能力 | 当前代码落点 | 结论 | 后续动作 |
|---|---|---|---|
| 多服务商 API | `api/chat/llmprovider`、`ModelConfigManager.kt`、`ApiPreferences.kt` | 已有待验 | M1 两家独立服务商兼容矩阵 |
| 流式聊天/取消 | `api/chat`、`core/chat`、消息状态/取消相关模块 | 已有待验 | M1 固定 SSE/状态机样本和取消对账 |
| 角色与会话 | `data/preferences/CharacterCardManager.kt`、角色 UI、聊天仓储 | 已有待验 | M2 请求快照、复制新 ID、导入安全 |
| 多记忆空间 | `data/model/MemorySpace.kt`、`ObjectBox.kt`、`MemoryRepository.kt` | 已有待验 | M2 统一 ScopeResolver，禁止先取全库再过滤 |
| TXT/Markdown 资料 | `MemorySpaceProfileDocumentRepository.kt`、文档转换工具 | 已有待验 | M3 导入预览、哈希、分片、删除闭环 |
| 文本型 PDF | `DocumentConversionUtil.kt`、PDFBox 依赖 | 待专项验证 | M3 专项测试；扫描 PDF 不得静默成功 |
| 长期记忆 | `MemoryRepository.kt`、`MemoryAutoSaveCandidateRepository.kt`、`MemoryAutoSaveScheduler` | 已有但边界未达标 | M4 三档模式、来源、纠错、遗忘和幂等 |
| 上下文预算/延迟 | `PerformanceMonitorManager.kt`、消息 token 统计 | 部分已有 | M5 固定 t0-t6 口径并做真机基线 |
| 工具/终端默认拒绝 | `ToolRegistration.kt`、`AIToolHandler.kt`、权限系统 | 不符合首版默认边界 | M1 在注册和执行层统一产品策略；不能只隐藏入口 |
| 插件/ToolPkg/MCP | `PluginRegistry.kt`、`plugins/toolpkg`、`data/mcp` | 不符合首版范围 | M1 禁止初始化/执行/后台回调，保留代码但策略拒绝 |
| Workflow/后台任务 | `WorkflowLifecyclePlugin.kt`、`WorkflowExecutor.kt`、WorkManager | 不符合普通聊天无后台依赖 | M1/M4 按产品策略关闭或仅保留受控队列 |
| Web Chat/HTTP 监听 | `integrations/http`、`ExternalChatHttpAutoStarter.kt` | 首版应禁用 | M1 禁止自动启动并清理外部入口 |
| 凭据保护 | `ApiPreferences.kt`、`ModelConfigManager.kt`、`KeyStoreHelper.kt` | 导出签名固定资产已移除；API Key 和真实落盘仍高风险待审 | M0 全路径扫描；Key 不得进普通 DataStore/资源/日志 |
| 数据库/索引/缓存 | `AppDatabase.kt`、`ObjectBox.kt`、`OperitPaths.kt`、Coil/媒体池 | 未证明全量保护 | M0 盘点真实落盘；M6 再做加密与恢复 |
| 备份/恢复 | `data/backup`、`data/recovery`、Manifest backup rules | 已有待验 | 明确不导出 Key、损坏包拒绝、双机恢复和升级失败恢复 |
| 独立安装/品牌 | `build.gradle.kts` clone build type | 只有共存构建雏形 | M1 独立应用名、Provider、更新/分享配置和数据目录 |
| 内嵌可执行分发物 | `:app/src/main/assets`（`accessibility.apk`、`desktop.apk`、`shizuku.apk`、`subpack/android.apk`、`subpack/windows.zip`、`operit_shell_exec`、`templates/**/aapt2-arm64-v8a`、`shower-server.jar`、`main.jar`、`setup_android_env.sh`）+ `:terminal/src/main/assets`（64 MB Ubuntu rootfs、`setup_fake_sysdata.sh`） | 15 项、146,207,921 字节随包分发，实测已进入 APK 合并资产集。这些载荷不止是"占体积"：源码里存在完整的**解包 → 改写 → 设备内签名 → 交给系统安装器**链路（`ShizukuInstaller.kt:22/37/73/91-98/104/115`、`UIHierarchyManager.kt:47/87/107/132-142/208-238/363-424` 用无障碍代点安装确认、`ExportDialogs.kt:782/802/829/840/895` + `ApkEditor.kt:87-257` 的 `change*`/`repack*`/`signApk`） | M1 从分发矩阵中移除并在执行层禁用；只隐藏入口不算关闭。载荷清单见 `runtime-audit.md`，安装/重打包/签名链见 `execution-entrypoints.md` §3.9，真机验证行 E15–E17 |
| 分发物携带构建者身份 | 13 个随包 `.so` 的 `.rodata`：`__FILE__` 断言/日志串、Clang `(lambda at …)` 诊断串、ggml 后端注册表路径常量 | 实测每个 APK 有 669 处绝对检出根；从默认长根出包即把构建者 Windows 用户名与目录结构发给外部测试者。短根干净构建同样有 669 处，只是内容是 `C:/M0/A`，说明是结构性机制而非本机偶发 | M1 出包前给全部 externalNativeBuild 模块加 `-fmacro-prefix-map`/`-fdebug-prefix-map`，并复核 ggml 那个路径常量是否运行期使用；整改后按 `native-build-path-embedding.txt` §2 的判据复扫归零 |
| 依赖许可义务与随包许可声明 | `app/build.gradle.kts:744`（`com.itextpdf:itextg:5.5.10`，POM 原文 AGPL-3.0，唯一调用点 `util/DocumentConversionUtil.kt`）、`:754`（`jlatexmath-android` 三件套，GPL-2.0）、`:755`（`com.github.tech-pw:RenderX:1.0.0`，**分发构件无任何许可授予**）、`:799`（`junrar`，`UnRar License`）、`ui/features/about/screens/OpenSourceLicenses.kt`（硬编码 68 条） | 依赖树四层实测：copyleft 载荷全部落在 Java 侧而非原生侧，锚定 dex 检索证实它们在分发物里（`Lcom/itextpdf/text/` 1,808、`com/github/junrar` 235、`ru/noties/jlatexmath` 88；非锚定的 1,814 那 6 处已逐条定位为资源路径字面量）。随包的"开源许可"界面有 **8 条**与 POM 原文／上游实测不符，方向一律是低估义务（AGPL→`MPL/LGPL`、unRAR→`The Unlicense`、GPL-2.0→加不存在的 classpath exception、无许可的 RenderX→`MIT`），且整层漏掉 Rust/Node/JS 资产。原先 5 个"本机查无授权文本"的构件已联网落定：libsu 在钉住的 tag 上证实 Apache-2.0（结案）、axml 的授权只存在于晚于被分发 tag 的 master（待作者追认）、RenderX 对应提交零授予而上游唯一 LICENSE 晚 3 天且是 GPL-3.0（M0-B09）；两个自签内嵌包里 accessibility 上游整棵树无授权文本、desktop 构建时不在 git 工作树内 | M1 出包前由负责人对 itextg/jlatexmath/junrar/axml/RenderX 逐项决定移除或替换（RenderX 只需删两行、零源码改动），并以已成文、待复核签字的 `operit/THIRD-PARTY-NOTICES.md`（第五轮产出）替换该界面的错误声明；见 `decision.md` M0-B08、M0-B09 与 `gradle-dependency-licenses.md`、`native-dependency-licenses.md`、`upstream-license-retrieval.md` |
| 随包 HTML 里硬编码的 CDN 脚本 | `assets/web/echarts_template.html`（2,709 字节，随两份干净产物一致） | 全 APK 4 个 `.html` 里唯一带远端 `<script src>` 的一个：从 `cdn.jsdelivr.net` 取 ECharts 5.3.3，无 `integrity`、无 `crossorigin`。当前**加载者实测为 0**（无任何代码引用该文件），所以今天不出网，但这是一次改动即可激活的无校验远端代码入口 | M1 二选一：删除该孤儿模板，或把 ECharts 随包并补 Apache-2.0 声明；同时在 M0-A03 真机出网清单加一条负向断言（不应命中 `cdn.jsdelivr.net`）。明细 `node-dependency-licenses.md` §7、`storage-network-inventory.md` 出网第 5 项 |
| 设备侧 FTP 与 rootfs 文件导出 | `:terminal/utils/FtpServerManager.kt`（Apache FtpServer，开关在 `ui/SettingsViewModel.kt`）、`:terminal/provider/UbuntuDocumentsProvider.kt` | 用户一启用即在设备监听端口；DocumentsProvider 把 Linux 用户态文件挂给系统其他 App | 列入 M1 执行层禁用清单与 M0-A03 真机出网清单；首版无场景需要 |

## 静态阻塞结论

- 历史 `assets/jks.jks` 和 `pkcs12.keystore` 已从工作树移除；`KeyStoreHelper` 不再从 assets 加载固定口令密钥库，改为 no-backup 目录随机生成导出签名密钥。APK 已产出并完成内容侧扫描（5,550 条目、仅 arm64-v8a、无匹配扩展名、无 v1 签名文件），M0-B02 的扫描盲区已闭合到"含各库模块 assets 根"，见 `signing-material-scan.txt`。
- `verifyNoEmbeddedSigningMaterial` 的根清单此前只有 `:app`，看不到 `:terminal` 的 rootfs；现改为枚举全部子项目。仍知的边界：按文件名后缀匹配、只展开一层归档、不解析 PKCS#8 结构，因此不能声称"证明不存在密钥"。
- `AndroidManifest.xml` 仍包含 Root/Shizuku/无障碍/悬浮窗/录音/相机/外部存储/安装包/后台/精确闹钟等能力：M1 必须建立首版 manifest/执行策略，而不是只做 UI 隐藏。
- `OperitApplication` 默认初始化插件、自动记忆和前台服务：M1 需增加受控产品策略入口并覆盖非 UI 启动路径。
- 执行层入口的完整静态盘点（启动即执行、注册层无策略、exported 广播、`0.0.0.0` HTTP、Workflow/ToolPkg/MCP 绕过 `ToolPermissionSystem`、§3.9 的资产安装与设备内重打包签名链）与 M1 必须逐项验证的 E01–E17 清单，见 `execution-entrypoints.md`。该文件是首轮静态盘点，未做运行期验证。
- M0-A01 构建侧已闭环：`C:/M0/A`、`C:/M0/B` 两份干净目录均不跳门禁产出 APK，条目集合与签名证书一致；APK SHA-256 不等的 1,752,396 个差异字节已逐字节定位到 4 个节并收敛为单一根因——`.rodata` 内嵌绝对构建路径改变串池排布，带动 `.text` 取址立即数（180,250 字节，解码后零条分支目标变化）与 `.rela.dyn` addend 位移，见 `native-build-path-embedding.txt`。**这同时意味着"两份产物哈希相同"不能作为验收判据**，也不得在计划书中被写成"可复现构建已达成"；重映射构建根后能否字节一致尚未验证。
- 依赖与许可这一层已完成五轮实测（内嵌分发物 → 依赖树四层 → 上游与自有 LICENSE 联网取证 → 两个自签内嵌包的上游取证 → 声明成文与条款正文取证），来源侧基本闭合、义务侧未闭合：AGPL-3.0 的 `itextg`、GPL-2.0 的 `jlatexmath`、受 unRAR 条款约束的 `junrar` 三项确在分发物里，`RenderX` 是**分发时零许可授予**的构件（M0-B09），随包"开源许可"界面 68 条里有 8 条低估义务的声明。第五轮已产出 `operit/THIRD-PARTY-NOTICES.md`（全文 10 节，四层数据 + 17 份许可全文 + 83 个字体条目的载体归属），故"没有可交付的声明文本"这一条已不再是缺口；**本表仍不提供任何"许可已合规"的结论**，因为成文不等于处置完成：依赖未删未换、界面那 8 条错误声明原文未改、UnRAR 第 2 条义务未写进任何文档、ObjectBox Binary Licence 正文仍未取得（POM 的 url 只指向站点根，公开那份 PDF 是网站服务条款），字体家族的上游授权文本仍为零。处置全部是负责人决策项，见 `decision.md` M0-B06、M0-B08、M0-B09 与声明第 2 节的 15 项。
- 一条与许可同源、但性质是完整性的实测：`accessibility.apk` 的 `version-control-info.textproto` 记录的 revision 等于其上游仓库 `main` 头那个提交，而该提交自带的版本字段（1.4/3）与随包（1.5/4）不符——即**分发物相对可核验来源存在未记录的本地改动**。这是"分发物即基线"这类断言必须逐产物核验的原因，见 `upstream-license-retrieval.md` §8。
- 真机安装、出网抓包、合成 Key 落盘扫描全部未执行（`adb devices` 为空），本表不构成任何运行期结论。
