# E5/04c Android 8 本机音乐音轨接入

2026-09-21 未发布开发，基线为 `4460ebf7`。API 26～27 的本机音乐改用 MediaExtractor / MediaCodec 解码与 AudioTrack 流式播放，实际播放音轨负责路由观察；API 28+ 继续使用可观察实际路由的 MediaPlayer。本次没有恢复后台播放，也没有新增模块、第三方依赖、权限或数据结构。

音频继续来自已经校验哈希的 AndroidMediaSource，不把 URI 交给解码器，不生成公共目录或私有目录的音频缓存。解码工作在 IO dispatcher，PCM 队列最多保留一个等待消费的块；生产、消费和未交付的内存缓冲均按所有权擦除。设备/焦点失效先停止本次 AudioTrack，再取消并等待解码器结束，关闭本次监听、焦点与来源。原生同步 API 本身是否存在驱动阻塞仍须设备验证，不能声称所有平台调用都有可强制中断的时间上限。

实际播放前先写入同一音轨的静音，最长五秒确认首次实际输出，首次输出由系统选择。确认后的输出改变、耳机移除、输出目录变化和焦点丢失保持失效，不自动重连。Android 回调存在调度延迟，本次不承诺物理切换瞬间绝无音频转到其他输出。准备静音不计入媒体位置；进度使用实际 playback head 与分块时间戳，完整播放要求硬件计数赶上提交帧数。拖动进度先找之前的压缩采样，再按完整交错 PCM 帧移除目标之前的部分。

平台依据：[MediaCodec 原始音频格式](https://developer.android.com/reference/android/media/MediaCodec)、[AudioTrack 流式播放与路由](https://developer.android.com/reference/android/media/AudioTrack)、[MediaExtractor 采样及定位](https://developer.android.com/reference/android/media/MediaExtractor)。使用平台默认的有符号 16 位解码输出，验证采样率、通道数、块大小与格式持续一致；解码、格式或路由错误明确停止，不另试播放器。异常格式和厂商编解码器的兼容性尚待真实格式样本验证。

新增 11 项宿主测试覆盖帧级定位、负时间戳预卷、格式边界、静音/未播放缓冲不计进度、时间戳间隙、六小时高采样率无符号计数、顺序交付及擦除、20 轮取消时队列和生产者收尾、解码错误与设备失效传播。源码自查修正了一项停止顺序问题：消费失败或取消时先停止原生音轨，再等待解码器清理；正常解码结束保留音轨等待实际播放完成。这两条分别有测试，避免为取消加速而截断正常结尾。

新增两个 AndroidTest 使用运行时合成的 WAV 调用真实解码器，分别检查完整立体声采样与半途定位；它们仅编译，执行为 NOT_RUN，且不覆盖实际发声或路由。

最终命令为 `./gradlew.bat :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintRelease --console=plain`，构建成功，耗时 56s，75 项任务（24 executed、51 up-to-date）。51 个 suite 合计 345 tests，0 failures、0 errors、0 skipped；Lint 为 0 Error、60 Warning、1 Hint，保留日志中的 Kapt 处理器选项警告，不宣称零警告。结果与哈希见 [validation.json](validation.json) 与 [build-validation.log](build-validation.log)。

ADB 当前无连接设备；API 26/27 解码播放、MP3/M4A/OGG/FLAC/WAV 实际格式矩阵、多声道和音质、耳机/USB/蓝牙切换、焦点竞争及锁定均为 NOT_RUN。没有以宿主测试替代这些项目。

API 26～27 本机视频仍使用 MediaPlayer，其音视频同步与逐音轨实现继续待做；B 站 WebView 的实际路由观察缺口也未关闭。04c/04d 与完整总计划均未完成。最新已发布 APK 仍为 0.32.0/code 33，Room schema 28、备份 v23 不变；本轮不发布 APK、不改原签名或覆盖安装数据。
