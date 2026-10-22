# E5A 设备验收矩阵

| 项目 | 宿主结果 | 真机结果 |
| --- | --- | --- |
| 22→23 迁移与 Room schema | PASS：宿主 SQLite | NOT_RUN：SQLCipher/Keystore |
| 录音权限与按住录音 | PASS：源码编译、Manifest 静态审计 | NOT_RUN：麦克风、60 秒上限 |
| 录音转文字 | PASS：协议、中文及 emoji、取消、限额测试 | NOT_RUN：真实服务和草稿插入 |
| 助手消息朗读 | PASS：协议、PCM 边界、取消测试 | NOT_RUN：真实服务、扬声器和音频焦点 |
| 后台与切换取消 | PASS：ViewModel 归属和生命周期源码检查 | NOT_RUN：页面切换、锁定和退到后台 |
| 语音配置与凭据绑定 | PASS：JVM、Android 测试源码编译 | NOT_RUN：SQLCipher/Keystore 落盘 |
| 备份 v18/v17 | PASS：JVM | NOT_RUN：跨设备文件选择器 |
| 两个真实模型服务商 | 不适用 | NOT_RUN：需用户在 App 内填写 Key |
| 数据库、WAL 与缓存明文扫描 | 不适用 | NOT_RUN |

ADB 于 2026-09-20 仅返回设备列表表头，没有已授权设备。宿主通过不替代真机结果。
