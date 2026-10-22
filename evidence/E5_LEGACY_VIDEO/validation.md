# E5/04c Android 8 本机视频解码与同步

2026-09-21 未发布开发，基线 `250d8a2b`。API 26～27 的音乐和视频共用 LegacyLocalMediaPlayer；音频继续使用上一阶段的 MediaCodec / AudioTrack 路径，视频增加独立 MediaExtractor / MediaCodec，输出到既有安全 Surface。API 28+ 保留实际路由可查询的 MediaPlayer。没有新增模块、第三方依赖、权限或数据迁移。

有声视频的显示时间取自实际 AudioTrack 播放头和 PCM 时间戳；音频准备期间画面等待，首个可显示画面准备好后才提交音频。音轨时间戳中的间隙会写为带媒体时间的静音，避免音频延迟起始把视频时钟直接跳过；路由准备静音仍不计入媒体时间。音轨实际排空后，以结束位置锚定单调时钟显示剩余视频。无声视频或定位已超过音轨末尾的视频使用单调时钟，等待第一帧准备完成后起算。

定位保留最接近目标的前一帧，替换时丢弃更旧帧；进入目标之后或抵达文件末尾时，按目标时间呈现该帧，覆盖帧间定位及最后一帧显示区间。正常未来帧按时钟等待，超过 100 ms 的迟到帧丢弃；允许最多 10 ms 的调度提前量。这是应用调度策略，不能据此声称实际音画偏差已测得或满足该阈值。

渲染与停止在 Main dispatcher 串行，显示前再次检查任务、Surface 和音频会话。设备失效先停音轨并取消视频，解码取消后释放自己持有的输出缓冲和编解码器；所有解码任务退出后才关闭来源。两个 extractor 各自使用 AndroidMediaSource 的借用视图，其中一个关闭不能清除另一个正在读取的验证来源。来源仍为用户选择且完整/分块验证的本机内容，不使用 URL，也不写媒体缓存。

平台依据：[MediaCodec 的 Surface 解码与输出释放](https://developer.android.com/reference/android/media/MediaCodec)、[AudioTrack 的播放头与路由](https://developer.android.com/reference/android/media/AudioTrack)。本轮没有使用 MediaSync，也没有更换现有安全 Surface 页面。平台驱动、合成器和耳机输出存在额外延迟，物理切换时的瞬时行为及实际停止耗时仍待测。

新增 18 项宿主测试覆盖实际音轨时钟、音频完成后的画面、无声视频、媒体静音间隙、时间戳等待/迟到/停滞、20 轮取消帧等待、显示前访问复查、帧间及 EOF 定位、20 轮取消预卷帧交付、无效时间戳释放、串行进度回写、取消等待写入及慢存储后的时钟重采样。自查修正了音视频进度并发后写覆盖的问题，音频排空时先锚定时钟再等待存储；等待存储返回后重新取实际音频位置，不能按过期采样渲染已经迟到的画面。新增一个 AndroidTest 验证借用来源不能互相关闭，未执行原生测试。

验证结果与源码哈希记录在 [validation.json](validation.json) 和 [构建日志](build-validation.log)。宿主测试验证时钟与取消逻辑；AndroidTest 仅编译。原生视频解码、真实画面和音画偏差、MP4/WebM 编码矩阵、旋转及比例、持续播放、最后一帧、定位到音频/视频尾部、耳机/蓝牙/USB、锁定及焦点竞争均为 NOT_RUN。不能用释放输出缓冲或编译成功替代实际画面验收。

最终执行 `./gradlew.bat :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintRelease --console=plain`：构建成功，55s，75 项任务（20 executed、55 up-to-date）；52 个 suite、363 tests 全部通过，0 failures、0 errors、0 skipped。Lint 为 0 Error、60 Warning、1 Hint，构建日志保留 Kapt 处理器选项警告，不宣称零警告。ADB 无连接设备；本轮没有执行仪器测试或原生视觉检查。

本机旧版媒体路径已接入源码，04c 仍保留 B 站 WebView 实际输出观测缺口，04d 仍需完整设备和真实服务验收、签名 APK 与覆盖安装验证。最新发布仍是 0.32.0/code 33，Room schema 28、备份 v23 及原签名不变。本轮无新 APK，完整总计划尚未完成。
