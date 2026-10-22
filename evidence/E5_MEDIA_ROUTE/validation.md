# E5/04c 媒体设备变化与焦点处理

2026-09-21 未发布开发。本机音乐、本机视频与 B 站播放器新增所属播放实例的输出设备目录、耳机即将外放广播与焦点监听；收到失效通知时停止该实例。快速重连不清除失效结果，也不自动恢复播放。等待 prepare、seek 或网页采样时同样可因失效退出，旧任务清理完成后才返回；最后确认的进度沿用原播放 token 保存为暂停。

API 28 及以上的本机有声媒体另外观察 MediaPlayer 的实际路由，初始路由从系统播放取得，此后路由集合变化停止播放；API 36 检查全部输出。实际路由持续 5 秒无法确认会停止；无音轨视频不要求音频路由。首次播放使用系统所选输出，没有新增媒体设备选择器或确认前静音协议。

API 26～27 的 MediaPlayer 不提供实际路由接口，本次只有系统输出目录、耳机广播和焦点保护，逐音轨确认仍待实现。WebView 也未暴露其底层音轨，本次不能发现输出目录不变的所有网页内部路由变化。这两项不计为实际音轨验证通过，04c 保持未完成。系统回调存在延迟，不能承诺设备突变时完全没有短片段转到其他输出。

依据：[AudioManager 耳机变化广播](https://developer.android.com/reference/android/media/AudioManager#ACTION_AUDIO_BECOMING_NOISY)、[MediaPlayer 路由接口版本](https://developer.android.com/reference/android/media/MediaPlayer#getRoutedDevice()) 和 [AudioRouting 多输出查询](https://developer.android.com/reference/android/media/AudioRouting#getRoutedDevices())。最初 Lint 查出 3 处 MediaPlayer API 28 调用未隔离，已按实际系统能力加上版本边界，未提高 minSdk；测试中的异常对象引用比较改为类别与诊断比较，以适配协程调试栈恢复。

源码提交 `30074716d2e2767101176e7628249e5d02a00591`。执行 `:app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintRelease`，最终构建 20 秒完成，75 项任务中 10 项执行、65 项已是最新。327 项 JVM 测试无失败、错误或跳过；Release Lint 为 0 错误、60 警告、1 提示。AndroidTest 仅编译。见 [机器可读结果](validation.json) 与 [构建日志](build-validation.log)。

新增 12 项测试：6 项媒体策略、4 项协程等待与失效仲裁、2 项实际播放协调器的进度终态测试。策略执行 20 次移除后快速重连与旧回调场景；它使用合成设备，不是 20 次真实耳机试验。等待测试验证原本 10 秒的平台等待被失效打断，仍等待受控清理完成；本机及 B 站协调器保存最后确认进度，不误记成播放完成。

设备、原生播放器、真实焦点、耳机拔插、蓝牙重连、无声短视频、网页实际停止和覆盖安装均为 NOT_RUN。本轮无新权限、数据库迁移或备份变更，没有发布 APK；最新仍是 0.32.0。后续继续单次录音/朗读、API 26～27 逐音轨路径与完整 04d 验收交付。
