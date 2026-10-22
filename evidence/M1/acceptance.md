# 最小聊天版验收记录

日期：2026-09-19。范围：本地伴侣 0.1.0，Android 8.0+ / ARM64，独立包名 cn.localfirst.companion。

## 已通过

- JVM 单元测试 17/17：协议解析 13 项、聊天协调器 4 项。覆盖任意 UTF-8 分片和 emoji、多行 SSE、CRLF、结束事件、部分答案、工具调用拒绝、HTTPS 地址验证、TLS 请求、重定向不转发凭据、401/429/503、断网、超时、取消关闭连接、即时取消、快速重试、角色和模型配置快照。见 test-results/。
- 全部产物的干净构建：`build-final-clean.log`，2 分 24 秒，141 个任务，139 executed、2 up-to-date；执行了 clean、单元测试、release/debug APK、设备测试 APK 和 release lint。
- 发布版 Lint 0 错误。保留依赖更新、KAPT/KSP、版本目录、ChromeOS ABI 等非阻断提示；冻结工具链和 ARM64 范围未因这些提示改变。见 lint-results-release.xml。
- 实际 APK 包扫描：100 个条目；只有启动 Activity，service/receiver/provider 均为 0；没有旧签名材料、内嵌 APK、语音模型或终端载荷。仅有 INTERNET 和 AndroidX 的应用自身 signature 权限。见 apk-audit.json 和 merged-manifest.xml。
- 仅保留 SQLCipher 与 Compose graphics path 两个原生库；两者 PT_LOAD 均满足 16 KB 对齐，未检出 Windows Users/M0 路径标记。SDK zipalign 检查通过。
- APK 签名校验通过。使用原本机 `Operit M0 Test` 测试证书；私钥不进入源码、APK 或交付目录。见 signature.txt。
- workspace-before.json / workspace-after.json 与 cleanup-summary.json 记录工程精简依据和空间变化；工作区仅剩主仓库、一套 Gradle 缓存、签名目录及已验证的历史依赖归档。

## 未执行，不能计为通过

- 真机安装、冷启动、键盘与小屏幕布局、历史恢复和取消操作体验。
- Android Keystore 实际读写、数据库重开、合成 Key/正文全路径扫描、系统迁移排除验证。设备测试代码已编译通过，等待 `connectedDebugAndroidTest` 实际运行。
- 两个真实服务商的聊天测试；用户在 App 内填写 Key。目前 ADB 无已连接设备，见 adb-devices.txt。

## 问题与处理

- 首轮 6 个本地 TLS 网络测试未到达测试服务器。测试夹具改为显式 localhost 绑定、localhost/127.0.0.1 证书和 NO_PROXY 后全部通过；信任配置仅存在于 JVM 测试源码，生产客户端没有放宽证书校验。
- 首次完整 Lint 检出 Manifest 删除节点的 MissingClass 和本机 properties 驱动器分隔符转义问题。改为按产品范围移除全部库注册后台组件，并修正本机路径转义；未抑制检查或创建 lint baseline。
- 本轮未实现完整隐私锁、长期记忆或备份恢复。新包只用于当前范围的个人试用；不能据静态检查宣称完整首版、真机安全验收或 M0 全项已通过。

## 交付追溯

接手基线为 `companion-m0-handoff`；实际 APK 哈希、源码提交和源码包哈希记录在 output/delivery.json。后续生成源码包时排除 local.properties、密钥库和历史 M0 机器诊断；主仓库保留历史证据供接续开发。
