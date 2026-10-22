# E5 第一项：受控语音收发开发记录

日期：2026-09-20。版本 0.26.0/code 26/schema 23，个人备份内容 v18。已完成按用户操作启动的录音、录音转可编辑草稿、明确操作后的助手消息朗读、语音服务独立配置、请求目的地预览、进程级模型互斥、生命周期取消及备份兼容，并形成签名交付。真实设备上的麦克风、音频焦点、SQLCipher/Keystore、覆盖安装和真实语音服务尚未执行，因此状态为“开发完成、验收待补齐”，不宣称 E5 或历史阶段已全部验收通过。

## 已接入

聊天输入栏提供麦克风入口。录音只在用户点击并授予系统权限后开始，采用 16 kHz 单声道 PCM16，最长 60 秒，停录后先显示识别目的地预览。识别结果进入输入草稿，由用户检查和编辑后再发送，不会自动成为消息。录音、上传 WAV 和合成 PCM 只保存在内存中，不写入公共目录或数据库。

助手文本消息菜单增加“朗读这条回复”。朗读前再次显示服务地址、模型、音色和完整文本预览；确认后请求 24 kHz 单声道 PCM 并使用 Android AudioTrack 播放。切换角色、切换会话、应用进入后台、应用锁定、开始备份或 ViewModel 销毁会停止录音、识别和播放，并清理内存音频。

设置页增加独立语音服务配置，支持 HTTPS 地址、API Key、识别模型、合成模型和音色。凭据使用既有 Android Keystore 方案保存并绑定服务地址；地址变化后必须重新填写 Key。HTTP 重定向、非 HTTPS 地址、超限响应和错误媒体类型会被拒绝。语音请求与聊天、记忆、日记、生图、连接测试和主动消息共用进程级模型互斥。现有 MCP、ToolPkg、世界书、记忆、资料、日程、主动消息和其他 Operit 衍生能力均未移除。

Room 22→23 新增语音服务配置表，旧数据和凭据引用保持不变。个人备份 v18 保存配置和凭据引用并执行地址绑定检查；v17 及更早备份按空语音配置升级。实现依据 Android [AudioRecord](https://developer.android.com/reference/android/media/AudioRecord)、[AudioTrack](https://developer.android.com/reference/android/media/AudioTrack) 以及 OpenAI [Speech to text](https://platform.openai.com/docs/guides/speech-to-text) 和 [Text to speech](https://platform.openai.com/docs/guides/text-to-speech) 接口约定。

## 自动化验证

最终 221 项 JVM 测试通过，无失败、错误或跳过；Android 测试源码编译通过。新增测试覆盖 HTTPS 端点补全、WAV 头、中文及 emoji multipart 识别、PCM 合成请求、重定向、401、429、错误内容类型、奇数字节 PCM、取消、备份 v18/v17 及模型互斥。

22→23 DDL 已在宿主 SQLite 实际执行并与 Room schema 23 对比；旧行全部保留，新表为空，中途 DDL 可回滚。Android SQLCipher 用例包含语音配置往返、数据库字段检查和合成正文落盘扫描，当前仅编译，未在设备执行。干净构建和独立验证构建均成功。Lint 为 0 错误、58 警告。

APK 静态审计确认 versionCode 26/versionName 0.26.0，仅一个导出 Activity、一个非导出提醒 Receiver、一个以 `BIND_JOB_SERVICE` 保护的非导出主动消息 Service、零 Provider。权限集合只新增 `RECORD_AUDIO`，不含精确闹钟、定位或存储权限。三个原生库均满足 16 KiB PT_LOAD 与 ZIP 对齐。合成密钥、语音正文和本机路径标记在 APK 中零命中。

## 发行检查

冻结源码提交为 `5b280459230067a04dcd137a8a93bc71a7b67814`，标签 `companion-v0.26.0` 指向该提交。APK 继续使用既有证书，SHA-256 指纹为 `ac7283d4cc843d710a77f3c5b3f96ee3663793b78e8fb30c6a145b8e37f497fa`。

最终 APK 为 9,477,514 字节，SHA-256 `411782500387807628d01de93b1d82b5bced71f14ffa7c78947d1c81344c6d7c`。源码 ZIP 为 2,484,851 字节，SHA-256 `f9db359e88370aa86a6778569af9d1d56806915dc233db1b770e4e8b44808641`。安装说明及全部交付文件哈希见 `delivery.json`。

## 待补齐验收

ADB 可执行但没有已授权设备。未运行 0.25.0→0.26.0 覆盖安装、schema 22→23 SQLCipher 迁移、数据库/WAL/缓存明文扫描、录音权限、实际录音时长、识别草稿插入、扬声器播放、音频焦点、来电及后台中断、两个真实语音服务以及跨设备备份恢复。此前的主动消息、日程提醒、时间天气、日记、记忆、图片、资料、表情、生图、伴读、系统认证、真实 MCP、跨设备模板和三人观察等项目也保持 NOT_RUN。

E5 第一项开发已经完成。下一项按总计划实施可打断的连续语音会话，再推进共同活动；设备和外部服务条件不足的项目继续单独标记待验。
