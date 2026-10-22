# E5B 设备验收矩阵

| 项目 | 宿主结果 | 真机结果 |
| --- | --- | --- |
| 连续语音 20 轮状态机 | PASS：20 轮显式状态转换 | NOT_RUN：实际麦克风/网络/扬声器 |
| 识别、生成、合成与播放打断 | PASS：所有阶段状态机与取消路径 | NOT_RUN：迟到响应和触控竞态 |
| 失败暂停、恢复与结束 | PASS：JVM 与生命周期源码检查 | NOT_RUN：返回键、锁屏、切后台、进程结束 |
| 普通聊天归属和工具边界 | PASS：复用 ChatCoordinator 与既有完整套件 | NOT_RUN：真实 MCP/ToolPkg 授权弹窗 |
| 22→23 迁移与 Room schema | PASS：宿主 SQLite，0.27.0 无新表 | NOT_RUN：SQLCipher/Keystore 覆盖安装 |
| 录音与播放 | PASS：Android 测试源码编译 | NOT_RUN：60 秒上限、音频焦点、来电、耳机 |
| 语音配置与凭据绑定 | PASS：JVM、备份 v18/v17 | NOT_RUN：SQLCipher/Keystore 落盘 |
| 两个真实语音服务商 | 不适用 | NOT_RUN：需用户在 App 内填写 Key |
| 数据库、WAL 与缓存明文扫描 | 不适用 | NOT_RUN |

ADB 于 2026-09-20 仅返回设备列表表头，没有已授权设备。宿主状态机通过不替代真实音频、网络和生命周期结果。
