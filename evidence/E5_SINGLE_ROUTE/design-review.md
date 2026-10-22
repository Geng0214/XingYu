# E5/04c 单次录音与回复朗读：设计延续与验证边界

记录日期：2026-09-21。对照基线为 `d785b1a0`，本次记录对象在复核时为未提交工作区，现已保存为源码提交 `a7966b82ace175e310fb3500968e8d9fb1f6f02b`，尚未发布。

用户目标是微信 / Telegram 风格的完整陪伴小手机。本次属于 Operate 模式下的普通扩展，仅处理单次录音和回复朗读的设备选择、准备状态与取消操作，继承现有 Android Compose Material3 界面。没有 PRODUCT.md、DESIGN.md、approved comp 或原生截图作为额外设计权威；未创建视觉系统、设计令牌或 sidecar，未运行 context。

## 对照范围与组件沿用

已对照基线与当前 [SpeechPages.kt](../../app/src/main/java/com/ai/assistance/operit/ui/main/SpeechPages.kt)、[VoiceAudioPicker.kt](../../app/src/main/java/com/ai/assistance/operit/ui/main/VoiceAudioPicker.kt)、[MessengerPage.kt](../../app/src/main/java/com/ai/assistance/operit/ui/main/MessengerPage.kt) 和 [CompanionViewModel.kt](../../app/src/main/java/com/ai/assistance/operit/ui/main/CompanionViewModel.kt)。设备选择器在基线已存在，本次增加仅选择输出的使用方式。

- 开始录音和回复朗读沿用 `AlertDialog`、`TextButton` 及既有 Material 图标。朗读继续显示原消息、语音服务选择和服务目的地；录音停止后继续进入既有时长与识别地址预览。
- 设备列表继续使用 `RadioButton`、`selectableGroup` 和 `Role.RadioButton`，整行最小触控高度为 48 dp，单选按钮自身不重复添加点击动作。刷新、加载、读取失败、空列表和不支持的输入输出组合沿用现有反馈方式。
- 字体层级与颜色继续引用 `MaterialTheme.typography` 和 `MaterialTheme.colorScheme`；没有增加字体、色板、品牌资产或新的页面结构。聊天输入框和消息气泡沿用原有实现。
- 新录音设备对话框沿用朗读对话框的可滚动内容组织：内容最大高度 440 dp、项目间距 12 dp。聊天输入区上方新增状态行，使用 `bodyMedium`、polite `liveRegion` 和最小高度 48 dp 的直接取消按钮。这些是源码事实，尚未经过截图或辅助技术验证。

## 本次交互扩展

单次录音先取得录音权限，再打开“选择录音设备”。输入和通信输出均不预选，组合有效后才允许“连接并录音”。说明区明确通信输出用于建立耳麦连接，本次录音不会播放回复，并提示准备阶段可能采样及丢弃缓冲。实际输入确认、旧缓冲丢弃后才显示“正在录音 · 最长 60 秒”；准备阶段显示“正在连接麦克风，请稍候”。麦克风按钮在准备阶段执行取消，在就绪阶段执行停止并预览；状态行另有“取消录音”入口。

停止后保留录音时长、16 kHz 单声道说明、识别服务与实际地址预览；只有确认上传才识别，结果仍回填可编辑输入框。开始录音时固定角色、空间与聊天归属。PCM 交付同时检查前台许可、录音活动状态与 scope/chat 归属，未交付的 PCM 在清理时擦除。

回复朗读复用设备选择器的 `includeInputs=false` 模式，只显示“声音播放到”。未选输出时不能确认播放；流程不申请 `RECORD_AUDIO`。ViewModel 的设备查询不调用输入枚举，[CommunicationRoutes.kt](../../app/src/main/java/com/ai/assistance/operit/api/audio/CommunicationRoutes.kt) 的 `withOutputRoute` 不枚举或选择麦克风。连接、合成和播放处于同一输出租约中，设备失效取消整项任务。聊天页分别显示连接设备、合成语音、确认播放位置和朗读状态，并提供“停止朗读”。实际播放路由确认后才显示正在朗读。

已读取 [AndroidVoiceRecorder.kt](../../app/src/main/java/com/ai/assistance/operit/api/audio/AndroidVoiceRecorder.kt)、[AndroidPcmPlayer.kt](../../app/src/main/java/com/ai/assistance/operit/api/audio/AndroidPcmPlayer.kt) 的基线差异：录音与播放绑定改为必填，删除无路由绑定分支；播放器使用通信属性，并将服务返回的 24 kHz PCM 转为 16 kHz，SCO 录音继续按 8→16 kHz 转换。这些实现记录不构成实际硬件路由成功的证明。

## 独立 reviewer 修正范围

主线程提供的同一 reviewer verdict 为 `disposition: ship`，原一项“取消与录音发布竞态”评分为 `resolved`。该 verdict 只覆盖列出的修正项，不代表功能、视觉或设备验收通过。文档者未重新运行 reviewer，也未扩大审查范围。

已读取新增 [VoiceCaptureDelivery.kt](../../app/src/main/java/com/ai/assistance/operit/ui/main/VoiceCaptureDelivery.kt)：发布使用带录音活动与归属检查的状态更新；取消使用 `getAndUpdate` 原子移除录音引用和活动状态，再擦除该次移除的 PCM，避免读取旧引用后清除新发布引用而遗漏擦除。ViewModel 在 [PrivacyGate.kt](../../app/src/main/java/com/ai/assistance/operit/data/security/PrivacyGate.kt) 的 `withForegroundPermit` 内完成发布，使许可和前台状态检查与交付处于同一保护范围。

## 已有验证证据

文档者只读取源码、现有构建日志及 XML 报告，未执行编译、构建、测试或设备操作。

- [build-validation.log](build-validation.log) 最终为 `BUILD SUCCESSFUL in 1m 1s`，75 项任务，其中 25 executed、50 up-to-date；AndroidTest 仅编译，未运行仪器测试。
- 已读取 `build/app/test-results/testDebugUnitTest/TEST-*.xml`：50 个 suite，共 334 tests，0 failures、0 errors、0 skipped。
- 已读取 `build/app/reports/lint-results-release.xml`：0 Error、60 Warning、1 Hint。日志另有 `VoiceCaptureDeliveryTest.kt` 中测试代理实现 `MutableStateFlow` 的 coroutines 继承 opt-in 编译 warning；不声称零警告。
- 对照本次新增的 7 个测试：3 个录音交付测试覆盖发布先发生、取消在发布读取与 CAS 之间发生的 20 轮受控交错，以及跨角色 / 空间 / 聊天拒绝发布；2 个前台许可测试覆盖离开前台及存储故障 / epoch 改变；2 个输出路由测试覆盖不枚举或选择输入、合成期间路由失效取消与单一租约归属。这些测试不能替代真实设备时序验证。

## 未验证与交付边界

原生截图、深浅主题、大字体、小屏、TalkBack 与视觉检查均为 `NOT_RUN`。没有原生截图可进行渲染复核；未运行仅适用于 HTML/CSS 的 detector。

实际 SCO / USB / 有线耳机 / 听筒路由、权限撤销、焦点争抢、设备断连、停止延迟及两个真实语音服务均为 `NOT_RUN`。单元测试与本次源码对照不证明这些设备行为或服务调用已经验收。

按本轮交付记录，数据库 schema 28、备份 v23 不变，本次未发布 APK，最新已发布包仍为 0.32.0。API 26～27 媒体逐音轨路径、WebView 路由观测缺口、完整 04d 验收及总计划剩余工作仍待完成；本次局部文档收尾不代表完整陪伴小手机目标已完成。

写入边界仅为本文件。其他项目文档由主线程维护；未修复范围外设计漂移。
