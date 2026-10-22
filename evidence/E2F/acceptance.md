# E2 第六项：Token 账单开发记录

日期：2026-09-20。版本 0.11.0/code 11/schema 10。Token 用量与本地费用已完成开发并形成签名交付；设备与真实服务验收尚未补齐，因此本阶段状态为“开发完成、验收待补齐”，不宣称整个 E2 或 M1/M2/E1 已通过。0.10.0 APK 按原哈希保留为升级基线。

## 已接入

OpenAI 兼容流式请求携带 `stream_options.include_usage=true`，解析结束原因后继续读取可选最终 usage 事件。普通聊天保存一个模型回合；工具聊天逐回合发出用量事件，记录完整、部分或未知覆盖状态。服务商上报的输入、输出和总 Token 与字符估算分列，失败和取消只保存实际已收到的 usage，没有返回时保持未知。

模型连接页可选配置每百万 Token 的输入价、输出价及 USD/CNY/EUR/JPY 币种。价格使用六位十进制定点微单位，两项同时设置，并随请求冻结；修改模型或价格不改写在途和历史请求。费用只由服务商上报 Token 和冻结价格计算，连接测试不写入账单。设置页新增账单汇总及最近 100 次请求，明确显示终态、覆盖状态、字符估算、未定价和本地费用。

`request_usage` 与助手消息按请求、角色、空间、会话、配置和消息身份关联。创建消息时在同一事务插入账单；终态消息、usage、估算和费用在同一事务完成；进行中更新要求所属助手消息仍处于活动状态。重启将遗留记录及消息标为 FAILED/INTERRUPTED，并对已经上报的 Token 使用冻结价格恢复费用；删除会话或角色同步删除所属记录。

备份内容 v5/schema 10 保存模型价格和冻结用量记录，继续读取 v1～v4。旧备份补入未知价格、USD 默认币种和空账单，不补造历史用量；追加恢复重新分配请求及助手消息身份，进行中消息和账单标为 FAILED/INTERRUPTED。备份页面预览用量记录数量。

## 验证范围

最终 137 项 JVM 测试全部通过，无失败、错误或跳过；Android 测试源码编译通过。冻结源码提交为 `6b850d2367eeb75add247197f1ec15996644ee60`。最终干净构建成功，111 项任务中 109 项执行、2 项无需更新；重复验证构建成功。Lint 为 0 错误、28 警告。日志见 build-final-clean.log 和 build-validation.log，测试计数见 core-tests.json。

TokenAccountingTest 覆盖完整、部分、未知回合，定点价格、费用、中文与 emoji 字符估算，以及非法计数、价格和币种。ProviderTest 覆盖最终 usage 事件、请求标志和错误 usage 拒绝。ToolChatServiceTest 验证多工具回合逐轮上报；ChatCoordinatorTest 验证真实用量持久化和失败时未知，已有取消、迟到数据、重试及请求冻结测试保持通过。

BackupTest 覆盖 v5 价格、用量、费用、身份重映射、费用篡改与跨空间拒绝，v4 冻结格式升级为空账单，并继续读取固定 v1/v2/v3 样本。宿主迁移脚本执行实际 9→10 SQL，对照 Room schema 10，核对原 14 张表、旧价格未知、空账单、12 种异常关联和 DDL 中断回滚；宿主 SQLite 通过不等于 Android SQLCipher 通过。

TokenUsageAndroidTest 已编写并通过编译，覆盖 SQLCipher 中的请求价格冻结、完整与未知用量、进程中断费用恢复、迟到用量拒绝、会话删除及正文标记落盘扫描。原迁移矩阵扩展至 schema 9，并接上 9→10；Android 测试编译与运行状态分开记录。

## 发行检查

最终 APK 静态审计通过：versionCode 11/versionName 0.11.0、仅一个导出 Activity、无 Service/Receiver/Provider、权限只含网络、生物识别和 AndroidX 动态接收器保护权限；不含签名材料或已移除载荷。三个原生库均满足 16 KiB PT_LOAD 对齐且未发现 Windows 绝对路径。APK 使用原证书，SHA-256 指纹 `ac7283d4cc843d710a77f3c5b3f96ee3663793b78e8fb30c6a145b8e37f497fa`。构建 APK 为 9,145,966 字节，SHA-256 `95efb544c00dafb70f5858c1ca6b1d3984ff81c4d334e6a2a73be7720027ee39`；最终交付文件哈希以 delivery.json 为准。

## 待补齐验收

ADB 列表为空，未运行设备测试。覆盖安装、真实 SQLCipher 9→10 迁移与 Keystore、账单和正文落盘、进程中断恢复、价格/账单页面输入法和无障碍、备份 v5 文件选择与跨设备恢复、系统认证、Android QuickJS、OpenAI 兼容/DeepSeek/硅基流动实际 usage 口径与服务商后台费用核对、真实 MCP、第二台设备模板和三人观察均标记 NOT_RUN。没有用宿主 SQLite、JVM 测试或 APK 扫描替代这些结果。

下一步按总计划实施 E2 第七项新人引导与帮助；E3～E8 保持完整范围。
