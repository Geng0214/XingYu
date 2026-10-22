# 0.33.0 音频路由开发版验收

本版整合 E5_ROUTE_CORE、E5_ROUTE_AUDIO、E5_SINGLE_ROUTE、E5_LEGACY_MUSIC、E5_LEGACY_VIDEO、E5_WEB_AUDIO_CORE 与 E5_WEB_AUDIO_BRIDGE 等未发布音频成果；既有语音、群聊、私聊、MCP/ToolPkg、共同活动和数据保留。版本代码 34，schema 28、备份 v23 无变化，沿用原包名与签名。各历史源码证据保留，实际交付提交和哈希以本目录 delivery.json 为准。

## 构建与自动验证

冻结 JDK 21、Gradle 8.13、AGP 8.13.2、Kotlin 2.2.21、SDK/Build Tools 36、NDK 27.3.13750724、CMake 3.22.1；使用工作区原 .gradle-m0 缓存。一次干净构建执行 JVM 测试、Android 测试源码编译、Release Lint、签名 release 和依赖清单生成，日志为 build-final-clean.log。网页脚本另外执行真实发布脚本的 Node 测试，记录在 web-script-tests.xml。打包脚本检查 373 项以上 JVM 用例及路由/视频/网页相关套件、9 项脚本用例、历史迁移源码哈希和本轮 APK。

APK 扫描覆盖实际 Manifest、权限/入口、签名材料、固定网页脚本、许可声明、原生库和 16 KB 对齐；apksigner 单独校验证书连续性，zipalign 独立检查。源码 ZIP 不含密钥或 APK。上述仅证明构建和静态/宿主验证；Android 测试编译成功不表示执行。

## 设备和真实服务待验

- Android 8～10 的 SCO、扬声器/听筒、有线/USB；Android 12+ 的通信设备与 BLE。核对实际录音输入、播放输出、输入采样率和回声/音质。
- 20 轮真实语音交错，包括取消、来电/焦点竞争、断开/重连、锁定、旋转、前后台和两个独立真实语音提供方；停止后无继续录音或旧回复。
- 本机音视频：旧版 MediaCodec/AudioTrack、多声道和时间戳、无声/音轨结束、尾部定位与音画误差；实际耳机输出。
- B 站：Android 全局 WebView 静音后仍有非零 PCM、原生音轨唯一可听、WebView 与原生焦点交互、暂停/定位/音量/倍速、CORS/CSP 和系统 WebView 版本、8 包额度及启动延迟、长时间音画误差与漂移。
- 设备选择器实际触控、旋转、键盘/TalkBack；截图与人工观察。此前 UI 源码审查不替代设备验收。
- 使用已有 0.32.0 及最早数据样本覆盖安装，保留凭据、历史和锁定策略；SQLCipher 加密落盘、跨设备备份、真实模型/MCP 和三人观察等原验收项保持待验。

当前无已连接 Android 设备，上述状态全部为 NOT_RUN；不清空、不卸载用户应用，也不以更换明文库完成升级。出现问题应保留原数据并修正原因。开发版交付不代表 M1/M2/E1～E5 或总计划通过。

## 下一阶段

继续 E6 的可保存游戏状态、角色回合和独立剧情场景；依次实现五子棋、翻牌记忆、猜词、UNO 类卡牌、象棋与像素冒险，保留 E7 家园/美化、E8 真实服务/设备及完整记忆/扩展任务。音频设备补验作为明确缺口继续保留。
