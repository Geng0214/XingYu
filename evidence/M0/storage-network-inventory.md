# M0-T04 存储、权限、后台与网络入口清单（静态首轮）

真实设备扫描和 App UID 出网观察尚未执行；本表用于指导现场证据收集。

## 已发现落盘路径

| 类型 | 代码入口/路径 | 风险与验收要求 |
|---|---|---|
| Room 主库 | `AppDatabase.kt` / Context database | 聊天、消息、token 用量；需验证是否含正文/配置及加密范围 |
| ObjectBox | `filesDir/objectbox` 或 `objectbox_<profileId>` | 记忆、文档、索引相关；必须按 profile 和角色授权检查 |
| 向量索引 | `filesDir/.vector_index` | 可能由原生索引落盘；不能因主库加密而视为已保护 |
| 外部应用目录 | `Download/Operit` | `plugins`、`mcp_plugins`、`bridge`、`exports`、`workspace`、`test`、`websession` 等可能含私人资料 |
| 缓存 | `cache/Operit/cleanOnExit`、Coil `image_cache`、图片/媒体池 | 临时文件、缩略图、媒体和失败任务都要扫描 |
| 技能/包 | `skill_repo_zip_pool`、plugins、ToolPkg 目录 | 首版不应初始化或执行任意扩展；导出不能包含私人数据 |
| 备份/恢复 | `data/backup`、`data/recovery`、RawSnapshot 相关代码 | 需验证跨库一致性、损坏包拒绝和 Key 默认不导出 |
| 日志/崩溃 | `AppLogger`、CrashRecovery | 普通日志不得包含正文、Key、完整请求头和私人标记 |
| 构建资产 | 历史 `app/src/main/assets/jks.jks`、`pkcs12.keystore` 已移除；仍有外部 ZIP/AAR | 固定口令密钥库不得恢复；构建秘密不得进入仓库；需用构建门禁扫描 APK/assets |

## 权限/系统能力入口

`AndroidManifest.xml` 静态声明了 INTERNET、外部存储、MANAGE_EXTERNAL_STORAGE、悬浮窗、写设置、查询全部包、Root/Shizuku 相关、无障碍/语音/相机/录音、后台服务、开机广播、精确闹钟、安装包等能力。M1 目标是首版核心聊天在拒绝这些授权后仍可运行，并在非 UI 入口同样拒绝工具执行。

## 后台和监听入口

- `OperitApplication.initializeMainApplicationLocked()`：插件、自动记忆、前台服务、WorkManager/Workflow、数据库和缓存初始化。
- `PluginRegistry.initializeBuiltins()`：Toolbox、ToolPkg、Workflow 内置插件。
- `ExternalChatHttpAutoStarter.kt` / `ExternalChatHttpServer.kt`：本地 HTTP/Web Chat 监听入口。
- `data/mcp` 与 `plugins/toolpkg`：MCP/扩展部署和回调入口。
- `WorkflowExecutor.kt`：工作流执行入口。
- `AIForegroundService`、浮窗、语音/广播/Provider：非主页面的启动路径。

## 出网目的地（静态分类）

1. 用户配置的聊天 API、总结 API、Embedding API：首版允许，但必须显示目的地、绑定 host、默认 HTTPS、重定向不转发 Key。
2. 外部 STT/语音/媒体/图片服务：首版不应成为核心聊天隐含依赖；需要按功能开关和用户确认。
3. 市场、Skill、ToolPkg、MCP、Web Chat/HTTP：首版排除，策略层禁止初始化/调用。
4. 构建阶段 GitHub、Hugging Face、Google Drive 等资源：仅构建时使用，不得在运行时隐式调用。
5. **随包 HTML 里的硬编码 CDN `<script src>`**（M0-A05 许可轮顺带实测出的网络项，明细在 `node-dependency-licenses.md` §7）：产物 5,550 条目中 4 个 `.html` 只有一个带远端脚本——`assets/web/echarts_template.html`（2,709 字节，A/B 两份产物内容一致，SHA-256 前 16 位 `c4b8656cf65d413c`）从 `https://cdn.jsdelivr.net/npm/echarts@5.3.3/dist/echarts.min.js` 取码，**无 `integrity`、无 `crossorigin`**。当前加载者实测为 0（全仓无任何代码引用该文件），所以它今天不出网；但这是随包分发的一段无完整性锚定的远端代码入口，一次 `WebView.loadUrl(...android_asset/web/echarts_template.html...)` 即可激活。首版处置：删除该孤儿模板，或改为随包 ECharts 并补 Apache-2.0 声明；不保留现状。第五轮成文时已按"当前不进分发"处理——ECharts 本体不在 APK 里，故不产生声明义务，该条目连同这条完整性风险记在 `../THIRD-PARTY-NOTICES.md` §7 末，不是漏项。

## 待现场执行

- 关闭 NAS 和个人服务，独立网络下执行 10 次聊天并按 App UID 记录目的地。
- 出网断言里加一条负向项：全程不应出现对 `cdn.jsdelivr.net` 的请求（当前代码路径无加载者，若命中即说明有未盘点的入口）。
- 使用合成 Key/正文写入所有流程，离线检查 Room、ObjectBox、向量、附件、缓存、日志、导出和备份。
- 从 UI、Intent、广播、后台任务、插件回调分别尝试触发工具/HTTP/MCP/工作流。
- 记录清单、哈希、截图和失败样本，未覆盖路径不得标通过。
