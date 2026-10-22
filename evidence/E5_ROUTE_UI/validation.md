# E5 音频设备选择与连续语音接入

当前为未发布源码推进。最新 APK 仍为 0.32.0，源码中的新增音频路由尚未打入该 APK。数据库 schema 28、备份 v23 未改变，现有数据和签名保留。

开始连续语音前分别选择麦克风、播放设备及 STT/TTS 服务。连接准备期间显示 CONNECTING；实际录音设备确认完成后才显示“正在听你说”。确认输入需先采样并丢弃缓冲。设备失效会停止整轮，恢复需要重新选择。正常打断先取消并等待旧轮次的音频、请求与路由清理结束，再连接相同设备。

独立源码复核发现设备失效与打断交错、旧版 SCO 组合及连接文案三个问题；本轮修正把失效结果保存在取消无法覆盖的会话状态中，显式重选才新建该状态。旧 Android 的蓝牙 SCO 麦克风与非 SCO 输出组合在 UI 与运行入口均被拒绝。

源码提交：`c342bf609d8abdf1a5a2a2bd553df8a2fc2b163b`。执行 `:app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintRelease`，1 分 5 秒完成，75 项任务中 27 项执行、48 项已是最新。315 项 JVM 测试无失败、错误或跳过；Release Lint 为 0 错误、60 警告、1 提示。Android 测试仅编译，没有在设备执行。

新增 3 项测试，其中 20 次执行真实路由控制器配合模拟平台端口的“失效清理中取消并快速重连”，检查取消遮蔽异常时仍保留失效、禁止原会话重开、显式重选可重开及旧回调隔离。另覆盖取消中的清理错误和旧 Android SCO 组合限制。原连续语音状态机的 20 轮测试更新为 CONNECTING → CAPTURE_READY → LISTENING。

独立 reviewer 的 verdict 为 `ship`，范围仅限原三项修正均被判定 `resolved`；不能解读为整个界面或设备发布通过。详细沿用与扩展记录见 [设计复核](design-review.md)，机器可读结果见 [validation.json](validation.json)，构建日志见 [build-validation.log](build-validation.log)。本文件不是设备验收通过记录。

设备视觉、深浅主题、1.3 倍字号、TalkBack、小屏横向、实际蓝牙/USB/有线/听筒路由、焦点争抢、权限撤销、失效与打断交错、真实连续语音和两个独立服务商均为 NOT_RUN。按键录音/单次朗读与音乐/视频的完整设备策略，以及 04d 签名交付仍待完成。
