# E13 单应用安装验收

2026-10-03，用户要求 MuMu 只保留一个软件。Debug 和 Release 功能来自同一套源码；本轮调整安装流程，没有修改应用功能或数据库。

## 实际操作

1. 核对 MuMu（`emulator-5554`）原来安装了正式版、`.debug` 和 `.debug.test`。调试版聊天首页只有验收使用的“初见”，正式版显示“你好”角色与最近“你好”消息。
2. 实际执行 [安装脚本](../../ci/install.ps1)。脚本核对 APK 正式包名与非调试标记，`adb install -r` 覆盖成功后，使用 `pm uninstall -k --user 0` 移除调试版和测试包，并保留它们的私有数据。正式版数据没有清空，调试验收样本没有追加导入。
3. [当前已安装包记录](installed-packages.json)只返回 `cn.localfirst.companion`。调试包的 MAIN／LAUNCHER 查询返回 `No activity found`。
4. 重新打开正式版：[聊天](chat.png)显示原“你好”角色与最近“你好”消息，[家园](home.png)显示“我们的家园”、120 币与 7 精力。原生 UI 层级见 [聊天](chat.xml)与 [家园](home.xml)。这些检查验证可见状态，不构成完整数据库内容比较。

## 交付与边界

继续交付同一个 [APK](../../output/local-companion-0.39.0.apk)，0.39.0 / code40；SHA-256 为 `87E02A974086856FB0D3766CAB898261577F966234C019BED8FE95AC47AD1D5A`。功能与 E12 一致，无需合并两份实现。

本轮实际执行安装入口及 MuMu 包状态、聊天、家园检查，没有编译、构建或运行测试。此前 E12 的 564 项单元测试与包校验仍是该相同二进制的历史证据，见 [E12 验收](../E12_MAP_INTERACTIONS/validation.md)。没有执行真机或完整备份恢复验收。后续用户设备只覆盖正式版；Android 仪器测试使用独立验收设备。
