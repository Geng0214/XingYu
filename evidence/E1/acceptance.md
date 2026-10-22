# E1 扩展兼容子集：开发验收记录

日期：2026-09-20。当前交付目标 0.5.0/code 5/schema 6；远程 MCP Streamable HTTP 与 ToolPkg tools-v1 纯计算子包开发完成。设备与人工验收待补齐，M1/M2/E1 均不标记全部通过。最终 APK、源码提交、哈希和构建结果以本目录 delivery.json 为准。

## 实现与验证

远程 MCP、角色授权、单次调用确认、模型工具回合和 Room 调用记录沿用前四段实现。新增 ToolPkg 静态检查、完整安装预览、确认后注册验证、SQLCipher BLOB 存包、默认停用、角色授权、启停卸载与实际本地执行。使用统一调度器冻结身份和授权版本，本地调用直接进入 QuickJS，不伪装为 MCP 网络协议。

76 项 JVM 测试通过，无失败或跳过。新测试覆盖实际时间包、UTF-8、ZIP 越界/碰撞/符号链接/CRC/截断/超限、重复字段、Hjson 深度、未知版本和宿主能力拒绝；静态预览包含会抛异常和调用 URL 的脚本但不执行。另覆盖明确注册流程、模型→调度器→本地包→实际 QuickJS 引擎→模型，以及 20 次取消后迟到结果拒绝。

实际引擎测试使用固定哈希的官方同版本 Windows CLI，执行实际 Kotlin 包装器与 LocalToolPkgSession；模型事件在该项测试中是合成数据。它没有执行 Android JNI，不能替代设备测试。远程 MCP 测试则使用实际官方 SDK 和合成 HTTPS 服务，详见 [模型与界面接入](loop-acceptance.md)。

宿主 SQLite 执行源码中的 5→6 迁移 SQL，结果与 Room schema 6 一致；保留 schema 5 所有旧表行，检测错误授权/请求/调用归属，验证重复包身份和卸载级联。见 [toolpkg-sql.json](toolpkg-sql.json)。它不执行 Kotlin Repository 或 SQLCipher。

Android 测试增加重复安装不覆盖、注册失败不留半成品、加密归档落盘扫描、真实 Room→JNI 调用的 50 次 A/B 交替和撤权拒绝；加密检查使用 STORED ZIP，确保压缩不会遮住明文落盘错误。MigrationAndroidTest 扩展至 schema 1～5→6，检查旧授权、凭据引用、头像和收藏。全部仅编译，尚未运行。

## 构建与边界

第三方运行期坐标增加 Hjson 3.1.0，共 173 项；许可正文来自同版本 Maven 源码归档，随应用提供。仍为一个 app 模块、同一 Gradle 缓存，原生库仍为 SQLCipher、Compose path 与 QuickJS 三个。内置示例使用三个小文本资产，不附带 ZIP、命令行程序、签名材料或旧工具载荷。

发布脚本检查同一提交的 APK 源码元数据、无失败/跳过的测试、迁移 SQL、干净构建、lint、APK 审计、签名连续性和 16 KiB 对齐。最终结果写入 delivery.json；内部构建不作为源码对应关系证明。

最终交付已生成：源码提交 65dfb0de9e3310ac6d9258c23935529af60169de，APK 9014854 字节，SHA-256 为 02e3de173cc64987c0ae82cadb3668f6efa623c4033e5c37ece17302718ea328。源码 ZIP 为 1649123 字节；文件完整哈希见 [delivery.json](delivery.json)。同一提交的干净构建完成 107 个任务、2 个任务保持最新；76 项测试再次通过，lint 为 0 错误、28 警告。警告包括冻结依赖版本、版本目录建议、平台 Exif API 和 removeAll Provider 源声明；合并 Manifest 与 APK 审计确认没有 Provider/Service/Receiver。

签名 SHA-256 仍为 ac7283d4cc843d710a77f3c5b3f96ee3663793b78e8fb30c6a145b8e37f497fa，zipalign 16 KiB 校验通过。output 仅保留最新 APK；原 0.4.0 APK 经哈希核对后移入 build 作为覆盖升级基线，与更早版本基线一起保留。

兼容范围详见[使用与升级说明](../../docs/使用与升级.md)。未开放文件、网络或设备宿主桥、旧 UI Hook、stdio/旧 SSE 与任意 Operit 包，不能宣称全部插件兼容。

## 待验

- ARM64 真机：安装与覆盖升级、冷启动、SQLCipher/Keystore、插件注册/运行/超时/取消、操作与视觉检查。
- 两个真实模型服务商、真实 MCP 服务、跨设备模板导入与三人观察。
- M1/M2 历史阻塞项继续保留；资料、记忆和图片中的其余功能按完整总计划 E2～E8 继续实施，不能记为通过。

历史证据保留：[传输](transport-acceptance.md)、[存储](storage-acceptance.md)、[模型回合](loop-acceptance.md)、[QuickJS 基础](quickjs-acceptance.md)。其中“尚未接入”描述对应阶段，不覆盖本记录的新结果。
