# 执行层入口盘点（M1 禁用清单的输入）

日期：2026-09-19
性质：**静态源码盘点首轮，未做运行期验证**。下面的行号来自当前基线工作树，只用于定位，不构成"已验证可绕过"的结论；真正放行前必须按 §5 逐项在设备上验证拒绝生效。

路径缩写：
- `$A` = `app/src/main/java/com/ai/assistance/operit`
- `$M` = `app/src/main/AndroidManifest.xml`
- `$T` = `terminal/src/main`

## 1. 口径

计划书 V2.0 首版要求：工具、插件、MCP、Workflow、设备侧 HTTP/后台入口在**注册层和执行层**默认拒绝，不能只隐藏 UI 入口。本文件因此按"能不能在没有 UI 点击的情况下走到执行"来组织，而不是按功能菜单组织。

## 2. 无需 UI 点击即可到达执行的路径

### 2.1 进程启动即执行

| 位置 | 做什么 | 为什么算执行层入口 |
|---|---|---|
| `$A\core\application\OperitApplication.kt:201` | `PluginRegistry.initializeBuiltins()` | 任何 Activity 之前就把内建插件全量装好 |
| `OperitApplication.kt:202` | `AppLifecycleHookPluginRegistry.dispatchAsync(APPLICATION_CREATE)` | ToolPkg 注册过的生命周期钩子在此触发 |
| `OperitApplication.kt:214` + `:497-519` | `startGlobalAIForegroundServiceIfNeeded()` | 唤醒词或持久化的 `ExternalHttpApiPreferences.enabled` 任一为真即起前台服务 |
| `OperitApplication.kt:224` | `MemoryAutoSaveScheduler.start()` | 应用级轮询循环 |
| `OperitApplication.kt:236` | `AndroidShellExecutor.setContext()` | shell 执行器变为进程级可用 |
| `OperitApplication.kt:240` | `ShowerEnvironment.shellRunner = OperitShowerShellRunner` | 给 Shower/subpack 运行时接上 shell 通道 |
| `OperitApplication.kt:355-358` | 后台协程里 `AIToolHandler.registerDefaultTools()` | 工具表在启动期填满 |
| `OperitApplication.kt:364` → `$A\core\workflow\WorkflowSchedulerInitializer.kt:36-38` | 重新排期所有 `enabled` 的 workflow | 用户不开任何页面也会被排期 |
| `OperitApplication.kt:372` | `RoomDatabaseBackupScheduler.ensureScheduled`（WorkManager） | 备份任务冷启动进程 |
| `OperitApplication.kt:386` | `UIHierarchyManager.bindToService` | 提前绑定无障碍 provider |
| `$A\data\backup\RoomDatabaseBackupWorker.kt:8`、`$A\core\workflow\WorkflowWorker.kt:15` | Worker 无 UI 冷启动进程 | 同上 |

### 2.2 注册层与执行层都不拒绝

- `$A\core\tools\ToolRegistration.kt:36` `registerAllTools()`：单一注册点，约 190 次 `handler.registerTool`。
- `$A\core\tools\AIToolHandler.kt:179` `registerTool`：**注册时没有任何策略判断**，只是 `availableTools[name] = executor`。
- `AIToolHandler.kt:363` `executeTool` / `:419` `executeToolAndStream`：只过 `checkToolInterception`（`:100`，钩子），**不过 `ToolPermissionSystem`**。
- 后果见 §4。

### 2.3 外部进程可直接投递

- `$M:384-390` `ExternalChatReceiver`：`android:exported="true"` 且**没有 `android:permission`**（已直接读原文确认），action `com.ai.assistance.operit.EXTERNAL_CHAT` → `$A\integrations\externalchat\ExternalChatRequestExecutor.kt:43 execute` → 一轮带工具的完整 agent 执行。同机任意应用即可触发。
- `$M:460-468` `WorkflowTaskerReceiver`（exported，无权限）与 `$M:471-478` `WorkflowBootReceiver`（`BOOT_COMPLETED`）。
- `$M:440` `ShowerBinderReceiver`（exported，无权限）。
- `$M:285` `DataRecovery`（exported，`:repair`）、`$M:148` `ShizukuProvider`（exported）、`$T\AndroidManifest.xml:11` `UbuntuDocumentsProvider`（exported）。

## 3. 分类清单

### 3.1 shell / 终端 / 沙箱

`ToolRegistration.kt:270` `execute_shell`、`:305-408` terminal session 工具、`:354` `execute_hidden_terminal_command`、`:484`/`:496` 环境变量读写、`:536` `execute_sandbox_script_direct`、`:559` `restart_mcp_with_logs`、`:2711-2734` `ffmpeg_*`、`:2123` `apply_file`。
执行后端按 `$A\core\tools\defaultTool\ToolGetter.kt:20-83` 在 ROOT / Shizuku(DEBUGGER) / STANDARD 之间选择，取决于持久化偏好。
`$T\AndroidManifest.xml:22` `TerminalService`。

### 3.2 文件 / 归档 / 检索

`ToolRegistration.kt:1775-1882` read/write/delete_file(_binary)、`:2035-2208` move/copy/zip/unzip/open/share、`:2222` `grep_code`（背后是随包的 `liboperit_ripgrep.so`）。

### 3.3 网络

`ToolRegistration.kt:1967` `http_request`、`:1989` `multipart_request`、`:2008` `manage_cookies`、`:2258` `download_file`、`:1230-1377` `browser_*`。

### 3.4 包管理 / 意图 / 广播

`:2315` `install_app`、`:2327` `uninstall_app`、`:2348`/`:2360` `start_app`/`stop_app`、`:1002` `use_package`、`:1140` `package_proxy`、`:510`/`:522` 沙箱包启停、`:1412` `execute_intent`、`:1440` `send_broadcast`、`:1464` `trigger_tasker_event`。

### 3.5 插件 / ToolPkg / MCP

- `$A\plugins\PluginRegistry.kt:29-37`：内建 = `ToolboxPlugin`、`ToolPkgCommonBridgePlugin`、`WorkflowLifecyclePlugin`；`installAll()` 调 `register()`。
- `plugins\toolpkg\ToolPkgCommonBridgePlugin.kt:896-911`：注册消息处理 / XML 渲染 / AI provider 桥。
- `$A\core\tools\javascript\JsEngine.kt:1897-1987`：约 20 个 `registerToolPkg*` JS 钩子，含 `registerToolPkgAppLifecycleHook`、`...ToolLifecycleHook`、`...MessageProcessingPlugin` —— **ToolPkg 可以自注册钩子与工具**。
- ToolPkg 形态是 ZIP + QuickJS（`$A\core\tools\packTool\ToolPkgLoader.kt:10-40`、`PackageManager.kt:1942`、`:2024`、`:2101`），不是插件 APK；`DexClassLoader` 只出现在 `$A\core\tools\javascript\JsExternalJavaCodeLoader.kt:16-51,164-171`（JS→Java），ToolPkg 是否可达待运行期确认。
- 自注册进 LLM 工具表：`PackageManager.kt:3450-3467 registerPackageTools`（`pkg:tool` → `aiToolHandler.registerTool`）、`:3911` MCP 工具。
- 惰性激活：`AIToolHandler.kt:303-356 getToolExecutorOrActivate` 找不到执行器时会 `packageManager.usePackage(...)` 并惰性重连 MCP —— 即"未注册"不等于"不可达"。
- MCP：安装/配置在 `$A\data\mcp\MCPRepository.kt:296-490`（GitHub/ZIP 下载解包）、`:856 addRemoteServer`；启动在 `$A\data\mcp\plugins\MCPStarter.kt:99 initBridge`、`:137 MCPBridge.startBridge`（在终端会话里拉起 Node）、`:370 startAllDeployedPlugins`；`MCPToolExecutor.kt:519-533 getOrCreateSession` 任意工具调用即重连；`RemoteMcpRuntimeSession.kt:28` 走 Ktor 出网。
- **已保存的 MCP 服务在打开应用时自动启动**：批量启动的唯一调用点是 `$A\ui\features\startup\screens\PluginLoadingScreen.kt:827`，由 `$A\ui\main\MainActivity.kt:384-393 startPluginLoading()` 在 `onCreate` 里驱动，只受协议同意与权限引导标志位控制（`MainActivity.kt:375`）。

### 3.6 Workflow

- `$A\core\workflow\WorkflowExecutor.kt:1249-1275`：节点直接 `toolHandler.executeTool(AITool(name = node.actionType, ...))`，因此**任何已注册工具（含 shell/文件/装包）都能被 workflow 调起**；`:160-166` 还接受 `pkg:tool` 形式的 action。
- 排期：`$A\core\workflow\WorkflowScheduler.kt:103`/`:217` 周期任务、`:157`/`:263` 单次任务 → `WorkflowWorker.kt:38-39` → `WorkflowRepository.triggerWorkflow`。
- 触发器：`$M:460-468`（Tasker，exported）、`$M:471-478`（BOOT_COMPLETED）、`$A\plugins\workflow\WorkflowLifecyclePlugin.kt:28-40`（冷启动即"应用打开"触发）、工具 `:1599 trigger_workflow`、`:1524 create_workflow`、`:1569`/`:1579` 启停。

### 3.7 设备侧监听与套接字

- `$A\integrations\http\ExternalChatHttpServer.kt:45` `NanoHTTPD(LISTEN_HOST, port)`，`:548 LISTEN_HOST = "0.0.0.0"` —— **绑定全部网卡**；同一服务还挂 `$A\integrations\a2a\A2aHttpHandler.kt` 的 JSON-RPC。
- 生命周期：`$A\api\chat\AIForegroundService.kt:837` 构造、`:1323 startExternalHttpMonitoring`、`:443 ensureRunningForExternalHttp`、`:1158 onStartCommand` 重新拉起、`:1160 START_STICKY`。
- 自动启动链：持久化偏好 → `OperitApplication.kt:502` 与 `$A\core\application\ActivityLifecycleManager.kt:152` → `$A\integrations\http\ExternalChatHttpAutoStarter.kt:20-47` → `AIForegroundService`。
- `$A\ui\features\chat\webview\LocalWebServer.kt:52-53` NanoHTTPD + `:179-190` 自定义 `ServerSocket` 工厂；`:434`、`:778` 取得 `AIToolHandler` —— HTTP 请求可以直接走到工具执行。
- `$T\java\com\ai\assistance\operit\terminal\utils\FtpServerManager.kt:21,49,68`：Apache FtpServer 固定端口，入口在 `terminal\...\ui\SettingsViewModel.kt:31`；重启后是否保持开启待确认。
- `$A\core\tools\defaultTool\standard\BluetoothSessionManager.kt:56,172`：`BluetoothServerSocket` 监听（`bluetooth_listen`/`bluetooth_accept`）。
- `$A\data\mcp\plugins\MCPBridge*.kt`：stdio/TCP 桥进 Ubuntu 终端。

### 3.8 Manifest 中与上述能力对应的组件与权限

组件：provider `$M:148` ShizukuProvider、`:157` FileProvider（用于装包）、`:168`/`:179`/`:190` Workspace/Memory/OperitData DocumentsProvider、`:203` 覆写 androidx.startup；activity `:116` WebSessionPermissionRequest、`:125` UserscriptImportPicker、`:213` MainActivity（VIEW/SEND/SEND_MULTIPLE，`*/*`）、`:260` OperitAssistActivity（ASSIST/VOICE_ASSIST）、`:278` CrashReport（`:crash`）、`:285` DataRecovery（exported）、`:297` ActivityConfigAIAgentAction、`:307` ScreenCaptureActivity、`:428` ToolPkgDesktopWidgetConfigActivity、`:449` WorkflowTaskerActivityConfig；service `:314` FloatingChatService、`:320` ScreenCaptureService、`:326` OperitNotificationListenerService、`:337` AIForegroundService（dataSync/microphone/specialUse）、`:392` UIDebuggerService、`:481`/`:495` VoiceInteraction；receiver `:348` ScriptExecutionReceiver（`EXECUTE_JS`，权限 DUMP）、`:357` ToolPkgDebugInstallReceiver、`:366` PackageDebugRefreshReceiver、`:375` ToolPkgComposeDslDebugDumpReceiver、`:384` ExternalChatReceiver、`:403`/`:415` 小组件（ToolPkg widget host）、`:440` ShowerBinderReceiver、`:460` WorkflowTaskerReceiver、`:471` WorkflowBootReceiver。
WorkManager 的 androidx.startup 默认初始化器已在 `$M:202-211` 移除，改由 `OperitApplication.kt:414` 手工初始化；未发现其它 Initializer。

权限：`:18` KILL_BACKGROUND_PROCESSES、`:19-22`+`:90` 外部存储/MANAGE_EXTERNAL_STORAGE/legacy、`:23` Shizuku、`:24` REQUEST_INSTALL_PACKAGES、`:25` SYSTEM_ALERT_WINDOW、`:26` WRITE_SETTINGS、`:27` PACKAGE_USAGE_STATS、`:31`/`:56`/`:57` 三类前台服务、`:34` SUSTAINED_PERFORMANCE_MODE、`:37` SET_ALARM、`:40-43` CALL_PHONE/SEND_SMS/READ_SMS/RECEIVE_SMS、`:46-51` 定位+蓝牙、`:54-55` 电池豁免+WAKE_LOCK、`:59` QUERY_ALL_PACKAGES、`:63-64` RECORD_AUDIO/CAMERA、`:67` BIND_VOICE_INTERACTION、`:70-71` RECEIVE_BOOT_COMPLETED+SCHEDULE_EXACT_ALARM、`:93` usesCleartextTraffic 与 `:92` networkSecurityConfig。

### 3.9 assets 载荷 → 落地 → 改写 → 设备内签名 → 交给系统安装器

上一轮盘点只覆盖了"LLM 工具表里的装包工具"，漏了应用自身代码里已经存在的三条装包/改包链路。它们都不需要 LLM 参与，全部由 UI 点击驱动，因此不属于 §2 的"无点击路径"，但属于 §4 所说的"没有任何策略对象执法"的能力面。

- **随包 Shizuku 安装**：`$A\core\tools\system\ShizukuInstaller.kt:22`（文件名常量）→ `:37 extractApkFromAssets` → `:73 installBundledShizuku` → `:91-98` FileProvider 取 URI → `:104` `Intent.ACTION_VIEW` + `application/vnd.android.package-archive` + `:115 startActivity`。权限 `:24 REQUEST_INSTALL_PACKAGES` 就是为这类交接准备的。
- **随包无障碍助手安装与绑定**：`$A\data\repository\UIHierarchyManager.kt:47`（`accessibility.apk`）→ `:87 extractProviderApkFromAssets` 把 APK 直接写进 `context.cacheDir` → `:107 launchProviderInstall` → `:132-142` 同样的 `ACTION_VIEW` 交接。同一个类还通过 `:208-238` 用隐式/显式意图（`FLAG_INCLUDE_STOPPED_PACKAGES`）`bindService` 那个外部包，`:363-424` 提供 `performClick/LongPress/Swipe/GlobalAction` —— 即"装上另一个包，再遥控它的无障碍服务"。
- **subpack 重打包并签名**：`$A\ui\features\chat\components\ExportDialogs.kt:782` `ApkEditor.fromAsset(context, "subpack/android.apk")` → `:802` 取本安装密钥库 → `:829 withSignature(...)` → `:840 repackAndSignWithWebContent(webContentDir)`；`$A\core\subpack\ApkEditor.kt` 侧对应 `:87 changePackageName`、`:97 changeAppName`、`:107/:117` 改版本号、`:127-159` 换图标、`:198 repackWithWebContent`、`:232 repackAndSignWithWebContent`、`:257 signApk`。链条可以在设备上产出一个**任意包名、任意图标、注入了任意 Web 内容、并由本地密钥签过名的 APK**；`ExportDialogs.kt:895` 还会把 `subpack/windows.zip`（Windows 可执行载荷）原样拷进导出目录。
- **密钥侧现状**（这是 M0-B02 的整改结果，不是缺口）：`$A\core\subpack\KeyStoreHelper.kt:100-118` 只在 `noBackupFilesDir` 里用 `SecureRandom` 生成 RSA-2048 自签密钥库，口令同样随机并落在 `noBackupFilesDir`（`:120-138`），且每次取用前先删除旧版残留 `filesDir/jks.jks` 与 `filesDir/pkcs12.keystore`（`:140-149`）。代码里不再有固定口令或从 assets 读签名材料的分支。
- **纯死载荷**：`assets/desktop.apk`（6,489,542 B）按字面名在全仓检索（排除 `build/`）没有任何文本引用，既无安装入口也无更新逻辑，但每次构建仍随包分发。

## 4. 现有执法点的真实强度

- `$A\ui\permissions\ToolPermissionSystem.kt:192 checkToolPermission`（`:34-36` ALLOW/ASK/FORBID，`:65` 默认 ASK，`:212` 用悬浮窗弹确认框）是唯一像样的策略对象，但它只在 `api\chat\enhance\ToolExecutionManager.kt:464` 和 `ToolRegistration.kt:241`（proxy 工具）被查询；`AIToolHandler.executeTool` 不查它（`:52` 只是持有实例并 `:55` 暴露 getter）。所以 §2.3 的广播入口、§3.6 workflow、§3.5 ToolPkg/MCP、§3.7 的 HTTP 路径都不经过它。
- `ToolExecutionManager.kt:459`：除非回复原文里出现 `deny_tool` 才走检查（`:491` 已注明这是绕过面）；`:448-456` 对 `search`/`proxy` 类 CLI 工具直接 `granted = true`。
- `ToolRegistration.kt:186-220` 的角色卡工具白名单（`CliToolModeSupport.isToolNameAllowedForRoleCard`）只控制**提示词里暴露哪些工具名**，不控制执行。
- `$A\core\tools\system\AndroidShellExecutor.kt:82-108`（`buildStrictUnavailableReason`）与 `:110-127` 抛 `SecurityException` 是"ROOT/Shizuku 不可用时的能力缺失"，不是产品策略。
- 唯一现成的拒绝扩展点：`AIToolHandler.kt:100 checkToolInterception` 支持 `AIToolHookDecision.Block`，但没有任何内建插件注册 Block 钩子。

## 5. M1 落地时必须逐项验证的清单

| # | 关闭点 | 验证动作（合成标记，不靠肉眼） |
|---|---|---|
| E01 | `ToolRegistration.registerAllTools` 按策略跳过注册危险工具 | 枚举 `AIToolHandler` 可用工具表，断言无 shell/文件/网络/包管理/browser/workflow 类工具 |
| E02 | `AIToolHandler.executeTool` / `executeToolAndStream` 前置策略拒绝 | 直接构造 `AITool(name="execute_shell")` 调用，断言返回拒绝且未起进程 |
| E03 | `getToolExecutorOrActivate` 不再惰性 `usePackage` / 重连 MCP | 预置已启用 ToolPkg/MCP 后冷启动，断言不注册、不连接 |
| E04 | `WorkflowExecutor` 节点执行入口 | 预置一条含 shell 节点的 workflow，手动触发，断言节点被拒 |
| E05 | `WorkflowSchedulerInitializer` / `WorkflowScheduler` / `WorkflowWorker` | 预置 `enabled` workflow 后重启，断言无 WorkManager 排期 |
| E06 | `WorkflowTaskerReceiver`、`WorkflowBootReceiver`、`ExternalChatReceiver`、`ShowerBinderReceiver` | 用 adb 从外部发同名广播，断言不执行且无网络出站 |
| E07 | `ExternalChatHttpServer` + `AIForegroundService.startExternalHttpMonitoring` + `ExternalChatHttpAutoStarter` | 预置 `enabled=true` 后冷启动，断言 `0.0.0.0` 端口不监听、前台服务不起 |
| E08 | `PluginRegistry.initializeBuiltins` 与 `AppLifecycleHookPluginRegistry` | 冷启动断言无插件安装、无钩子回调 |
| E09 | ToolPkg 生命周期/工具钩子（`JsEngine.kt:1897-1987`）与 `registerPackageTools` | 导入含恶意钩子的合成 ToolPkg，断言注册被拒 |
| E10 | MCP 安装与批量启动（`MCPStarter.kt:370`、`PluginLoadingScreen.kt:827`） | 预置两个 MCP 服务后打开应用，断言无 Node 子进程、无出网 |
| E11 | `LocalWebServer`（NanoHTTPD + 自定义 `ServerSocket`） | 聊天页/工作区页打开后断言无监听端口 |
| E12 | `:terminal` 的 `FtpServerManager`、`UbuntuDocumentsProvider`、`TerminalService` | 冷启动后断言 FTP 端口关闭、provider 返回空/拒绝 |
| E13 | Manifest 权限与组件裁剪（§3.8） | `aapt dump xmltree` 比对冻结清单；未列入首版的组件必须不存在 |
| E14 | `AndroidShellExecutor.setContext` 等进程级单例 | 断言在未授权路径下取不到可用执行器 |
| E15 | §3.9 的随包 APK 安装交接（`ShizukuInstaller.installBundledShizuku`、`UIHierarchyManager.launchProviderInstall`） | 点这两个入口，断言不产出解包文件（`cacheDir`/落盘目录无 `accessibility.apk`、`shizuku.apk`）、不起 `ACTION_VIEW` 装包意图；`REQUEST_INSTALL_PACKAGES` 若保留则必须没有代码路径能触发 |
| E16 | §3.9 的 subpack 重打包签名链（`ExportDialogs.kt:782/802/829/840` + `ApkEditor` 全部 `change*`/`repack*`/`signApk`） | 走一次"导出为独立应用"，断言不产出签名 APK、不写出 `windows.zip`；导出目录里不出现任何可安装/可执行载荷 |
| E17 | `KeyStoreHelper`（首版若保留任何本地签名能力） | 真机首启后断言：`noBackupFilesDir` 里的密钥库与口令存在且每次安装不同（两次安装对比证书摘要不等）、旧 `filesDir/jks.jks` 与 `filesDir/pkcs12.keystore` 被清除、备份（`adb backup` / `allowBackup`）取不到该密钥库、日志与导出物里没有口令明文 |

E01–E17 全部有真机证据前，`capability-gap.md` 的"工具/终端默认拒绝""插件/ToolPkg/MCP""Workflow/后台任务""Web Chat/HTTP 监听"四行不得改为"已达标"；§3.9 的三条链路在关闭前，"装包/改包"这一项连"已登记"都不算完成。

