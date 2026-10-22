# E5/04c 单次录音与朗读设备接入

2026-09-21 未发布开发。录音先选择输入和通信输出，连接期间可取消，实际输入确认且旧缓冲丢弃后显示正在录音；停止后的时长预览、确认识别服务及文字回填仍保留。角色、空间和聊天身份在开始时固定，取消、失去前台或访问关闭后不发布旧录音，未交付 PCM 会擦除。

朗读只选择输出，不枚举或选择麦克风，不申请录音权限。连接、合成和播放同属一个输出租约，设备失效会取消整项任务；播放实际路由确认后才显示朗读。通话属性播放把服务返回的 24 kHz PCM 转为 16 kHz，录音的 SCO 输入按 8→16 kHz 转换。录音器/播放器绑定现在为必填，已移除无设备绑定的调用分支。

新增选择器沿用 Material3、原生单选语义及 48 dp 触控高度。聊天页显示单次录音或朗读状态和直接取消入口。独立源码复核发现取消与 PCM 发布之间的一项竞态：现以同一 StateFlow 的原子状态更新取走并擦除被取消的音频，迟到发布重新校验活动状态后拒绝。原项复核为 resolved / ship，仅表示该修正项通过源码复核，不代表完整功能或视觉验收。

最终构建命令为 `./gradlew.bat :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintRelease --console=plain`。构建成功，耗时 1m 1s，75 项任务（25 executed、50 up-to-date）；334 项测试全部通过，0 failures、0 errors、0 skipped。新增 7 项测试覆盖输出租约不读取输入、合成期间路由失效、前台许可、录音交付与取消、跨身份拒绝。其中取消发生在发布读取与 CAS 之间的顺序执行 20 轮受控交错，不能替代硬件时序验证。Lint 为 0 Error、60 Warning、1 Hint；测试代理实现 MutableStateFlow 另有 coroutines 继承 opt-in 编译 warning。

AndroidTest 仅编译，未运行。ADB 无连接设备；原生截图、TalkBack、深浅主题、大字体、小屏、真实设备、SCO/USB/有线/听筒、停止延迟及两个真实服务均为 NOT_RUN。详见 [设计与复核记录](design-review.md)、[构建日志](build-validation.log) 和 [源码与证据哈希](validation.json)。

数据库 schema 28、备份 v23 及现有数据未改变，未增加权限或新模块，本轮不发布 APK；最新已发布包仍为 0.32.0。媒体 API 26～27 的逐音轨路径、WebView 路由观测缺口以及完整 04d 验收继续实施，完整总计划仍未完成。
