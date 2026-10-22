# E5 连续语音设备选择：设计延续与验证边界

记录日期：2026-09-21。改动前基线为 `686384df`，本次源码为 `c342bf609d8abdf1a5a2a2bd553df8a2fc2b163b`。这是未发布源码的普通交互扩展记录。

用户目标是微信 / Telegram 风格的完整陪伴小手机。本次按 Operate 模式处理连续语音的设备选择、连接状态与恢复操作，沿用现有 Android Compose Material3 界面。没有 PRODUCT.md、DESIGN.md、approved comp 或原生截图可作为额外设计权威，因此未新建视觉世界、设计令牌或 sidecar，也未运行 context。

## 对照范围与既有组件

已读取基线提交与当前源码中的 [SpeechPages.kt](../../app/src/main/java/com/ai/assistance/operit/ui/main/SpeechPages.kt)、[CompanionViewModel.kt](../../app/src/main/java/com/ai/assistance/operit/ui/main/CompanionViewModel.kt) 和 [SpeechServices.kt](../../app/src/main/java/com/ai/assistance/operit/data/model/SpeechServices.kt)。[VoiceAudioPicker.kt](../../app/src/main/java/com/ai/assistance/operit/ui/main/VoiceAudioPicker.kt) 在基线中不存在，是本次新增组件；记录时这四个文件与 `c342bf60` 无差异。

- 开始确认继续使用 `AlertDialog`、`TextButton`、服务选择 `FilterChip` 与 `VoiceDestinationCard`，保留 STT/TTS 服务目的地、上传说明和前台会话说明。
- 会话继续使用全屏 `Dialog` / `Surface`，保留顶部返回及结束入口、轮次、圆形状态图标、识别文字和角色回复卡片，以及底部主操作和结束按钮。
- 颜色、文字和卡片形状继续引用 `MaterialTheme.colorScheme`、`typography`、`shapes.large`。状态图标继续使用既有 Material 图标；没有新增色板、字体体系或品牌资产。
- 录音、识别、生成、合成、播放的既有串行会话流程继续由 ViewModel 与内存状态机管理；输入 / 输出选择不改变聊天归属及服务目的地的显示方式。

## 本次扩展的交互

开始前新增分别选择“麦克风”和“声音播放到”。设备不预选；刷新会清空选择并重新读取列表。加载、空列表、读取失败及不支持的设备组合均有对应说明。选择有效输入、输出和 STT/TTS 服务后才可“连接设备并开始”。

状态机新增 `CONNECTING` 和 `CAPTURE_READY`，开始、继续和轮次结束均先进入连接准备，录音设备就绪回调后才显示“正在听你说”。会话显示本次输入及输出名称。设备失效暂停后重新提供选择器，显式点击“使用所选设备继续”；重新连接硬件本身不启动麦克风。

整个连续会话循环置于同一个 `withRoute` 范围，录音与播放共享 `CommunicationAudioBinding`。打断时先显示“正在停止上一轮”，等待旧任务结束和清理，再决定重新连接或保持暂停；等待期间禁用重复操作。

新增设备列表沿用 Material3 `RadioButton`，整行最小触控高度为 48 dp，使用 `selectableGroup` 与 `Role.RadioButton`；单选按钮自身不重复提供点击动作。状态和设备错误使用 polite `liveRegion`。会话增加 `safeDrawing` 安全区，主体可滚动，顶部导航及底部操作保留在主体外；主操作最小高度为 56 dp，结束按钮最小高度为 48 dp。原 112 dp 状态圆缩为 88 dp、图标由 46 dp 调为 40 dp，连接和停止等待采用线性进度提示。这些是本地布局及语义的源码事实，尚未通过截图或辅助技术验证。

## 原三项 reviewer finding 的收尾

主线程提供的同一 reviewer verdict 为 `disposition: ship`，原三项均为 `resolved`。这是列出的修正项复核，不代表全界面或设备验收通过。文档者读取了相关源码来记录落点，没有重新执行 reviewer 或扩大裁决范围。

1. **设备断开与打断交错**：`CommunicationRouteFailure` 保留首个路由失效结果。打断等待旧任务 `join` 后读取结果，已失效则转为暂停；显式重选继续才创建新的失效状态，取消不会清掉已记录的失败。相关实现位于 ViewModel 与 [CommunicationRoutes.kt](../../app/src/main/java/com/ai/assistance/operit/api/audio/CommunicationRoutes.kt)。
2. **旧 Android SCO 组合**：[CommunicationDevicePair.kt](../../app/src/main/java/com/ai/assistance/operit/api/audio/CommunicationDevicePair.kt) 拒绝 API 31 以下“蓝牙 SCO 麦克风 + 非 SCO 输出”。选择器、开始入口和继续入口使用同一组合检查；界面显示重新选择输入或输出的说明。
3. **连接和打断文案**：开始按钮为“连接设备并开始”，连接状态提示等待“正在听你说”后开口，打断清理显示“正在停止上一轮”。一般打断说明删除“立即”的承诺，暂停继续明确写为“使用所选设备继续”。

## 验证证据与交付边界

文档者只读取已有源码、日志和报告，未运行构建或测试。

- [build-validation.log](build-validation.log)：`BUILD SUCCESSFUL in 1m 5s`，75 项任务，其中 27 executed、48 up-to-date；AndroidTest 仅编译。
- 已读取 `build/app/test-results/testDebugUnitTest/TEST-*.xml`：46 个 suite，共 315 tests，0 failures、0 errors、0 skipped。
- 已读取 `build/app/reports/lint-results-release.xml`：0 Error、60 Warning、1 Hint。不能描述为零警告。
- 原生视觉、深浅主题、1.3 倍字号、TalkBack、小屏横向及截图检查均为 `NOT_RUN`。没有已连接设备或原生截图；未运行只适用于 HTML/CSS 的 detector。
- 实际蓝牙 / USB / 有线 / 听筒路由、焦点争抢、权限撤销、真实失效与打断交错、连续语音和两个独立服务商验证均为 `NOT_RUN`。单元测试及源码复核不证明这些设备行为。

本次没有生成 APK，最新已交付版本仍是 0.32.0；数据库 schema 28、备份 v23 不变。04c 的按键录音 / 单次朗读及音乐 / 视频完整设备策略、04d 的原生验收与签名交付仍待完成。更广泛的产品目标不因本次局部源码收尾而视为完成。

写入边界仅为本文件。现有设计世界文件及其他项目文档未修改；未对范围外既有设计文档作漂移审计。
