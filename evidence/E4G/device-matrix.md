# E4G 设备验收矩阵

| 项目 | 宿主结果 | 真机结果 |
| --- | --- | --- |
| 21→22 迁移与 Room schema | PASS：宿主 SQLite | NOT_RUN：SQLCipher/Keystore |
| 主动消息 JVM 策略 | PASS | NOT_RUN：系统调度 |
| Compose 与 Android 测试源码 | PASS：编译 | NOT_RUN：界面交互 |
| 通知正文与组件导出 | PASS：静态 APK | NOT_RUN：权限、锁屏与点击 |
| 请求互斥、取消和中断恢复 | PASS：JVM/源码 | NOT_RUN：进程与 Doze |
| 备份 v17/v16 | PASS：JVM | NOT_RUN：跨设备文件选择器 |
| 两个真实模型服务商 | 不适用 | NOT_RUN：需用户在 App 内填写 Key |
| 数据库、WAL 与缓存明文扫描 | 不适用 | NOT_RUN |

ADB 于 2026-09-20 仅返回设备列表表头，没有已授权设备。宿主通过不替代真机结果。
