# E17 功能补齐第 1 期验收

日期：2026-10-24。范围：星语 0.40.0／code41，Android 8.0 及以上、ARM64，包名 `cn.localfirst.companion`。功能代码提交 `0870859cd`；本轮工作树另含版本号、文档和 `ci/script/verify_apk.py` 的接收器断言更新，未单独提交。

## 实现

- 语音消息条：录音预览可直接发送；`message_voices` 保存 PCM、32 点波形、时长与哈希，气泡显示播放、波形和时长。模型只收到语音时长占位，不收到原始录音。
- 消息反应：`message_reactions` 保存用户与角色反应；长按消息选择，每侧最多 3 个；角色回复完成后按消息 ID 生成固定反应，不调用模型。
- 定时消息：`scheduled_messages` 保存固定文本或生成指令，使用 `AlarmManager` 到点执行；固定文本直接写入聊天，生成模式调用所选模型。列表显示待发送／已发送／已取消／失败。
- 角色卡导入：解析 SillyTavern V1/V2 JSON，预览后创建独立角色；世界书条目默认关闭，备选开场白和未知字段不写入。
- 数据库 schema 34→35，`VoiceReactionScheduleMigration` 建立三张新表；个人备份 v32→v33，新增语音、反应和定时消息；家园内部格式仍为 3。

## 构建与静态检查

| 检查 | 结果 |
| --- | --- |
| `:app:testDebugUnitTest` | 84 个测试类、599 项，0 失败、0 错误、0 跳过，`BUILD SUCCESSFUL in 4m 52s` |
| `:app:assembleRelease` | `BUILD SUCCESSFUL in 5m 10s` |
| 包检查 `verify_apk.py` | `PASS`，版本 0.40.0／code41，两个非导出接收器（`PlannerReminderReceiver`、`ScheduledMessageReceiver`），权限集合与预期一致 |
| 签名 | APK Signature Scheme v2 通过；证书 SHA-256 `ac7283d4cc843d710a77f3c5b3f96ee3663793b78e8fb30c6a145b8e37f497fa` |
| 16 KB 对齐 | `zipalign -c -P 16` 通过；5 个原生库 PT_LOAD 均 16384 对齐 |
| APK | 90,226,193 字节；SHA-256 `FC0099FB6AC87B9CCF9D6DC1629662F3D86A12159203AED4CCD92BA53A9FA74D` |

构建命令和原始输出摘要见 [build-commands.txt](build-commands.txt)、[jvm-summary.json](jvm-summary.json)、[apk-audit.json](apk-audit.json)、[signature.txt](signature.txt)、[zipalign.txt](zipalign.txt)。

## 设备验收

设备：MuMu Android 15（SDK 35），型号 22041211A，物理分辨率 900×1600，当前横屏截图 1600×900，密度 240，系统字号 1.0。设备信息见 [device.txt](device.txt)。

1. **覆盖升级**：安装前为 0.39.0／code40，原「你好」角色和最近消息可见（[before-install.png](before-install.png)）。用 `adb install -r` 覆盖安装 0.40.0／code41，未卸载或清空数据；冷启动后原角色和消息仍在（[after-upgrade.png](after-upgrade.png)），日志未出现 `FATAL EXCEPTION`、`SQLiteException` 或迁移错误。未直接读取加密库的 `PRAGMA user_version`；本项以升级后启动、原数据保留和无迁移异常作为通过依据。
2. **消息反应**：长按用户消息打开反应条（[reaction-menu.png](reaction-menu.png)），选择爱心后气泡显示徽章（[reaction-added.png](reaction-added.png)）；强制停止并重启后徽章仍在（[reaction-persisted2.png](reaction-persisted2.png)）。
3. **定时消息**：在角色资料页安排固定文本 `Scheduled_test`，时间设为 10 月 24 日 05:50（[scheduled-arranged.png](scheduled-arranged.png)）；到点后聊天出现该消息（[scheduled-fired.png](scheduled-fired.png)），冷启动后仍在（[after-restart.png](after-restart.png)）；列表显示「已发送」（[scheduled-after-fire.png](scheduled-after-fire.png)）。
4. **角色卡导入**：将 V2 JSON 测试卡放入设备下载目录，通过系统文件选择器选中后，预览显示角色名、49 字人设、12 字开场白和 1 条世界书（[import-preview.png](import-preview.png)）；确认后角色列表出现「测试角色」并提示世界书默认关闭（[import-created.png](import-created.png)）。随后删除该测试角色（[test-role-deleted.png](test-role-deleted.png)）。
5. **语音消息条**：麦克风授权、设备选择、录音和停止可用（[voice-recording.png](voice-recording.png)），预览显示 20.7 秒、16 kHz 单声道和「发送语音」（[voice-preview.png](voice-preview.png)）。为避免触发真实模型请求，本轮点击「丢弃」，未发送；因此语音发送后的气泡播放和重启保留未验收。
6. **200% 字号**：系统字号临时设为 2.0 后，聊天和定时消息布局可读（[font-200-chat.png](font-200-chat.png)）；反应徽章未在 200% 字号下单独截图。随后已恢复 `font_scale=1.0`（[final-restored.png](final-restored.png)）。

## 清理与未验收

- 聊天内的 `Scheduled_test` 测试消息已删除（[cleanup-deleted.png](cleanup-deleted.png)），测试爱心反应已移除（[cleanup-reaction-removed.png](cleanup-reaction-removed.png)），测试角色已删除；定时消息历史仍保留一条 `Scheduled_test`「已发送」记录供追溯。设备上的测试 JSON 和 UI 层级临时文件已删除，系统字号已恢复。
- 未验收：语音发送后的气泡播放与冷启动保留、角色生成定时消息、v32→v33 设备备份恢复、通知发送、竖屏 375dp、真机、TalkBack、隐私锁认证和在线模型。
- 定时消息当前不单独发送系统通知，也未接入主动消息的免打扰和每日上限；用户指南已按实际行为说明。
- 本轮没有运行 Android 仪器测试；`MigrationAndroidTest` 已包含 34→35，但只编译，未在设备执行。
- 从设备 `/data/app/.../base.apk` 拉取后计算 SHA-256，与 `output/local-companion-0.40.0.apk` 一致（`FC0099FB...FA74D`）；`output/local-companion-0.40.0.apk` 已复制，历史源码包和 `delivery.json` 未重新生成。

## 结论

四项功能的代码、JVM 用例、Release 构建和本轮设备范围已记录；固定文本定时消息、反应、角色卡导入和覆盖升级通过。语音发送、生成模式定时消息、备份恢复等未执行项不计为通过。本轮范围可进入个人试用，不宣称四项功能已全部设备验收。
