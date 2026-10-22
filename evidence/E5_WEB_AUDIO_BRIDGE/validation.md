# E5：网页音频与原生音轨接线

本轮接通此前 E5_WEB_AUDIO_CORE 接收侧与 BilibiliPlayer。新增 AndroidX WebKit 1.11.0，加载前检查 MUTE_AUDIO、WEB_MESSAGE_LISTENER 和 DOCUMENT_START_SCRIPT，再全局静音；不支持时明确停止并提示更新系统 WebView。包名、签名、数据库、备份和已安装数据不变，最新交付仍为 0.32.0。

## 代码与边界

文档起始脚本只在 player.bilibili.com 顶层运行。消息监听再核对真实来源、主框架和完整目的地址，接受固定协议；确认仅含代际和序号，不传角色、模型、文件或凭据。二次加载拒绝，加载前无文档时等待，不把正常初始空 URL 当跳转。

AudioWorklet 固定下混为立体声，每 1024 帧生成 4096 字节 PCM16，最多 8 包待确认，网页输出为零；原生实际写入后回确认。暂停/定位重建代际、释放旧音轨、清空队列；过载/重复确认/异常停止。页面移动却 5 秒收不到 PCM 时停止，不能静默丢音频继续播放。MediaElementAudioSource 遵循元素音量、静音与倍速语义，参见 [W3C Web Audio](https://www.w3.org/TR/webaudio/#MediaElementAudioSourceNode)。生命周期结构化关联采样任务、音频任务与观看，清理顺序为音轨、桥和脚本、WebView、设备监听和焦点。

## 验证

最终冻结工具链检查成功：373 项 JVM 测试、53 个套件，失败/错误/跳过均为 0；Android 测试源码编译成功，未执行；Release Lint 为 0 错误、62 警告、1 提示。网页脚本宿主测试 9 项全部通过，174 项运行时依赖与声明一致。

- Kotlin/JVM、Android 测试源码编译、Release Lint 的最终结果与版本见本目录 verification.json / build.txt；使用 ci/build.ps1 的冻结 JDK 21、SDK 和 .gradle-m0 缓存。此前快速编译使用默认 Gradle 缓存，未作为最终冻结工具链证据。
- 冻结缓存首次解析新依赖时连接本机旧代理端口失败；最终调用以 `-Dhttp.proxyHost=`、`-Dhttps.proxyHost=` 清除此进程代理设置，从官方仓库解析，未修改用户代理配置。新增依赖 POM/许可证见 new-dependency.json；174 项实际运行时坐标与两份声明一致。
- Node 宿主执行真实交付脚本：PCM 字节序/双声道/削波/零输出、128 帧拼包、8 包积压停止、100 包确认、20 轮暂停定位、非有限数、来源/子框架、视频替换和恶意确认。它不模拟真实 Android 音频设备。
- [桌面公开页面实验](browser-probe.json)：真实 AudioWorklet 产生非零 PCM，音量归零后为零，暂停停止传包；未执行原生发声。临时页已关闭，无音频文件或网页凭据留在证据目录。
- 新增 Android 离线 WAV/真实 WebView 测试源码：全局静音后非零 PCM、音量/静音、20 次暂停/定位/恢复、无外部网络。仅编译；当前 adb 无设备，执行状态 NOT_RUN。该测试也不代表 AudioTrack 的实际听感或音画误差。

## 待验与后续

Android 平台静音是否保留 PCM、原生音轨是否唯一可听输出、8 包额度对启动路由延迟的适应性、原始网页音频旁路、WebView/CORS/CSP 兼容、音画偏差和缓冲漂移仍需真实执行；失败应修正原因，不能解静音绕过。继续旧版设备、真实服务商、覆盖安装、04d 干净签名构建和 APK 扫描。本轮没有新 APK，不能称 E5、M1/M2 或完整计划验收通过。
