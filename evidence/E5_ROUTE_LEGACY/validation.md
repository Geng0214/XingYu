# E5 04b：旧安卓适配与输入归属

状态：未发布的底层开发，04b 整项未完成。最新交付仍是 0.32.0，未重新生成 APK 或源码归档。包名、签名、版本、数据库、备份、Manifest、依赖与模块均未修改。

## 实现

API 26～30 适配器持有通信模式、焦点和所选设备的生命周期。SCO 使用系统连接广播并持有自己的 start/stop 请求，连接之后以 16 kHz 单声道静音 AudioTrack 确认实际输出；扬声器、听筒、有线和 USB 路径同样确认输出。静音音轨只包含零采样，在整个租约期间保持观察，不携带录音或模型回复。模式、焦点、设备、SCO 或音轨失效终止本次连接；清理尝试所有资源，失败禁止同一适配器继续申请。

`CommunicationRoutes` 现在同时接收明确的麦克风和输出身份，在创建平台资源前校验两者。API 31+ 和旧安卓适配器都验证真实输入目录，在整个录音/网络/播放轮次监听所选输入与输出移除。`createAndroidCommunicationRoutes` 只按 OS 版本选择实现，不按错误重试其他路径；后续 ViewModel 应持有单实例。

普通与通信路径的实际录音/播放传输改为非阻塞循环。持续 5 秒无采样或无法写入报错；每次操作前后检查取消/路由状态，保留部分写入的偏移并拒绝负数、奇数字节或越界返回。音轨播放结束仍以播放头位置判断，不能用写入完成冒充播放完成。

## 验证

新增 12 项 JVM 测试，运行生产握手、路由控制器和 PCM 传输代码：已连接的粘性事件、等待/失败、20 次断连与迟到事件、错误输入身份、网络等待中麦克风移除、取消后先清理任务再关路由、部分写入、缓冲暂满、5 秒无进展、取消与停止、设备丢失以及非法返回值。平台端口/读写函数由测试模拟，不能据此宣称原生驱动和蓝牙运行通过。

源码版本、测试数、Lint、Android 测试编译和文件哈希见 [机器记录](validation.json)；最终命令输出见 [验证日志](build-validation-final.log)。首次添加适配器时 Lint 指出广播与轮询回调缺少权限检查，已在实际回调路径检查 MODIFY_AUDIO_SETTINGS；未使用 Lint 豁免或放宽基线。该权限仍须在产品接线时声明，当前代码没有发布。

最终全套 312 项测试通过，无失败、错误或跳过；Android 测试编译通过，设备运行未执行。Release Lint 为 0 错误、60 警告、1 提示，新增 BLE 类型引用使用 API 31 版本判断，未留下本轮新增 Lint 问题。最终验证构建 1 分 8 秒，75 项任务中 18 项执行、57 项已是最新。当前 adb 设备列表为空，最新 0.32.0 APK 哈希复核不变。

## 未完成

ViewModel 尚未调用新工厂或路由绑定，连续语音仍使用已发布的原路径。设备选择、暂停后重新选择、整轮释放屏障、MODIFY_AUDIO_SETTINGS 与 APK 白名单、媒体拔出策略、签名打包仍需接入。

无连接安卓设备：旧安卓 SCO/输入目录、静音探测的耗电与延迟、实际麦克风/输出匹配、听筒/有线/USB/HFP/BLE、来电、20 轮连续语音、锁定/旋转/后台、覆盖安装、双真实语音服务均为 NOT_RUN。系统广播和实际路由存在时序差异；测试没有证明切路瞬间零泄露，也没有证明 API 26～35 的单输出查询能识别设备厂商的所有并行输出。

阶段索引见[项目导航](../../docs/README.md)。设计依据为 [Android AudioManager](https://developer.android.com/reference/android/media/AudioManager#startBluetoothSco())、[AudioRouting](https://developer.android.com/reference/android/media/AudioRouting) 和 [AudioTrack 循环播放](https://developer.android.com/reference/android/media/AudioTrack#setLoopPoints(int,%20int,%20int))；历史 [录放音证据](../E5_ROUTE_AUDIO/validation.md) 与 [0.32.0 交付](../E5D/acceptance.md) 保留。
