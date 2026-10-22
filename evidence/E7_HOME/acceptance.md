# 0.39.0 家园与活动联动交付记录

2026-09-21，开发交付完成、设备验收待补齐。完整小手机目标尚未完成，高级美化及 E8 继续按 [项目计划与当前进度](../../docs/当前计划.md)推进。此前 M0/M1/M2 与 E1～E6 证据及待验项保留，不因本次构建成功改为通过。

## 本次能力

0.39.0/code 40 整合六块耕地、种植与收获、背包、做饭与制衣、赠送、双方着装、可选本机虚拟菜园及操作记录。家园按角色与空间隔离，操作使用版本条件更新和唯一命令流水。虚拟菜园不是其他真人或其他角色的实际家园。

约会和城市漫游创建时冻结双方服装，后续换装不改写旧剧情。像素冒险的归航纪念章与海鸥胸针可预览后领取，每来源每种一次；丢弃、赠送、重试、删除源活动或备份恢复均不能重复发放。领取不调用模型。规则、界面和存储细节分别见 [页面记录](../E7_HOME_UI/acceptance.md)、[服装记录](../E7_OUTFITS/acceptance.md) 和 [奖励记录](../E7_REWARDS/acceptance.md)。

保留已有聊天、本机背景、群聊、语音、共同活动、小游戏及扩展入口。插件仍是已声明的兼容子集：MCP Streamable HTTP over HTTPS；ToolPkg schema 1 / API 1.0.0 的 tools-v1 纯计算工具。没有宣称任意 Operit 旧插件、平台工具或 UI Hook 已兼容，详见[使用与升级说明](../../docs/使用与升级.md)。

## 产物与升级

- 源码提交：`5da65ec02d3c2a1976f59d065896bcb7f043778c`
- APK：`local-companion-0.39.0.apk`，11,367,635 字节；SHA-256 `816e5b3cc42617e702415b8b81f6b499864a7e33c887cadb062842f375333bb7`
- 源码包：`local-companion-source.zip`，4,685,677 字节，1,107 个文件；SHA-256 `8bcdc27f6302b37802a8047d55c4271d9596226d4ca506bc3c637de70827bc5f`
- 签名证书 SHA-256：`ac7283d4cc843d710a77f3c5b3f96ee3663793b78e8fb30c6a145b8e37f497fa`，与保留的 0.38.0 基线一致

包名继续为 `cn.localfirst.companion`。Room 显式迁移链延伸至 schema 30；备份格式 v30 保持读取 v1～29，新增奖励内容不得伪装成旧版。覆盖安装保留现有数据；禁止卸载、清数据或明文替代作为升级方案。实际手机覆盖安装仍为 NOT_RUN。使用步骤见 [安装说明](../../docs/使用与升级.md)，机器可读产物与验证状态见 [delivery.json](delivery.json)。

APK 内源码版本与提交一致，源码 ZIP 与该提交 Git archive 逐文件比较一致。最终发布证据在源提交之后生成并单独提交，源码 ZIP 不包含本记录和自身哈希，避免循环校验。

## 已执行验证

执行冻结工具链下的 `ci/build.ps1`，任务为 `clean`、`:app:testDebugUnitTest`、`:app:compileDebugAndroidTestKotlin`、`:app:lintRelease`、`:app:assembleRelease`，清空 HTTP/HTTPS 构建代理属性。干净构建 4 分 47 秒成功，109 项任务中 107 项执行、2 项已最新。完整日志见 [build-final-clean.log](build-final-clean.log)。

563 项 JVM 测试、75 套件全部通过，0 失败、错误或跳过。Android 仪器测试仅编译通过。Release Lint 0 错误、62 警告、1 提示；警告未因此视为消除。选定家园、页面、服装、奖励和备份原始 JUnit 结果随本目录保存，完整结果哈希及 Lint 汇总见 [verification.json](verification.json)。网页音频 Node 测试 9 项通过。

宿主 SQLite 验证包含 schema 30 对照、旧行保留、异常关联拒绝、回滚、唯一命令与版本、50 次 A/B 读取及 20 轮条件更新回滚。这是宿主 SQLite 证据，不能替代 Android SQLCipher 或真实并发。具体结果见 [home-migration.json](home-migration.json)。

实际 APK 二进制 Manifest、权限、组件、禁止载荷、原生库和私有路径扫描通过；签名验证和 `zipalign -c -P 16 4` 通过。原生库保留 SQLCipher、QuickJS JNI 与 AndroidX graphics，16 KiB PT_LOAD 检查通过。见 [apk-audit.json](apk-audit.json)、[signature.txt](signature.txt) 和 [alignment.txt](alignment.txt)。

## 产物整理

输出目录只保留当前 APK、对应源码 ZIP、安装说明、交付清单及上一版升级基线。操作前检查绝对路径、重解析点、文件哈希、相关进程及排他读取。0.38.0 原样移入 upgrade-baseline，删除已过时的 0.37.0 基线 9,947,431 字节；未清理其他目录或构建缓存。

此次替换前输出目录为 25,775,521 字节，完成后为 27,333,435 字节。净增加 1,557,914 字节来自当前包、源码与保留基线变化，不宣称总体磁盘占用下降。完整前后文件表见 [output-before.json](output-before.json) 和 [cleanup.json](cleanup.json)。

## 待验与后续

本次 `adb devices -l` 没有连接设备。原生视觉、触控、TalkBack、大字体、横屏、页面恢复、SQLCipher 实际迁移及加密落盘、20 轮真实双协程领取竞争、覆盖安装、跨设备备份、两个真实服务商和三人操作观察均为 NOT_RUN。此前音频路由、真实 MCP/QuickJS、通知、完整象棋棋例矩阵等待验边界继续保留。

下一开发项为 E7 高级美化：桌面布局、Dock、挂件、图标与字体、聊天和通讯主题、受限模板预览与导入；之后继续 E8 外部服务、外设与可选云能力。截图中的真实交易、设备控制和人力客服不能用模拟卡片或占位入口计为实现。
