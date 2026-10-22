package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MessageState { QUEUED, WAITING, GENERATING, COMPLETE, CANCELLED, FAILED }
val activeStates = setOf(MessageState.QUEUED, MessageState.WAITING, MessageState.GENERATING)

@Entity(tableName = "roles", indices = [androidx.room.Index(value = ["spaceId"], unique = true),androidx.room.Index(value=["id","spaceId"],unique=true)])
data class RoleEntity(@PrimaryKey val id: String, val name: String, val prompt: String, val version: Long = 1,
    @androidx.room.ColumnInfo(defaultValue = "''") val spaceId: String = java.util.UUID.randomUUID().toString(),
    @androidx.room.ColumnInfo(defaultValue = "''") val openingMessage: String = "", val defaultConfigId: String? = null,
    @androidx.room.ColumnInfo(defaultValue = "'short'") val replyStyle: String = "short",
    @androidx.room.ColumnInfo(defaultValue = "''") val userName:String="",
    @androidx.room.ColumnInfo(defaultValue = "''") val userPersona:String="",
    @androidx.room.ColumnInfo(defaultValue = "0") val personaEnabled:Boolean=false,
    @androidx.room.ColumnInfo(defaultValue = "'character'") val conversationMode:String="character") {
    fun scope() = RoleAccessScope(id, spaceId)
    fun selfName()=if(personaEnabled)userName else "我"
    fun isRaw():Boolean {require(conversationMode in setOf("character","raw"));return conversationMode=="raw"}
    fun effectiveReplyStyle()=if(isRaw())"paragraph"else replyStyle
}

data class RoleAccessScope(val roleId: String, val spaceId: String)

// Deliberately has no API key: only a reference to an independently encrypted credential.
@Entity(tableName = "model_configs")
data class ModelConfigData(
    @PrimaryKey val id: String,
    val name: String,
    val provider: String,
    val endpoint: String,
    val model: String,
    val timeoutSeconds: Int,
    val credentialRef: String,
    val version: Long = 1,
    @androidx.room.ColumnInfo(defaultValue = "0") val visionEnabled:Boolean=false
)

@Entity(tableName = "chats",indices=[androidx.room.Index(value=["id","roleId"],unique=true),androidx.room.Index(value=["roleId"],unique=true)])
data class ChatEntity(@PrimaryKey val id: String, val roleId: String, val configId: String,
    val title: String, val createdAt: Long = System.currentTimeMillis(),
    @androidx.room.ColumnInfo(defaultValue = "''") val openingMessage: String = "",
    @androidx.room.ColumnInfo(defaultValue = "0") val pinned: Boolean = false)

@Entity(tableName = "messages", indices = [androidx.room.Index("chatId")])
data class MessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val requestId: String,
    val kind: String,
    val content: String,
    val state: MessageState,
    val createdAt: Long,
    val errorCode: String = "",
    val firstTokenMs: Long = -1,
    val elapsedMs: Long = -1,
    @androidx.room.ColumnInfo(defaultValue = "'paragraph'") val bubbleStyle: String = "paragraph",
    @androidx.room.ColumnInfo(defaultValue = "0") val favorite: Boolean = false,
    @androidx.room.ColumnInfo(defaultValue = "NULL") val quotedMessageId:String?=null,
    @androidx.room.ColumnInfo(defaultValue = "''") val quotedSpeaker:String="",
    @androidx.room.ColumnInfo(defaultValue = "''") val quotedPreview:String=""
)

data class MessageQuoteSnapshot(val messageId:String,val speaker:String,val preview:String) {
    init {require(messageId.isNotBlank()&&speaker.isNotBlank()&&preview.isNotBlank()&&preview.length<=12000)}
}

@Entity(tableName = "role_appearance")
data class ChatAppearance(@PrimaryKey val roleId: String, val spaceId: String,
    val wallpaper: ByteArray? = null, val dimming: Float = 0.18f, val palette: String = "rose")

@Entity(tableName = "role_portraits", primaryKeys = ["roleId", "kind"])
data class RolePortrait(val roleId: String, val spaceId: String, val kind: String, val image: ByteArray)

data class ConversationCard(val chatId: String, val roleId: String, val spaceId: String,
    val roleName: String, val title: String, val preview: String?, val updatedAt: Long?, val createdAt: Long, val pinned: Boolean)

data class PromptTurn(val role: String, val content: String, val images:List<PromptImage> = emptyList(),val materials:List<PromptMaterial> = emptyList()) {
    fun modelText()=buildString {
        materials.forEachIndexed {index,material->if(index>0)append("\n\n");append(material.modelText())}
        if(materials.isNotEmpty()&&content.isNotEmpty())append("\n\n用户消息：\n")
        append(content)
    }
}
data class RequestSnapshot(val requestId: String, val chatId: String, val assistantId: String,
    val role: RoleEntity, val config: ModelConfigData, val turns: List<PromptTurn>,
    val scope: RoleAccessScope = role.scope(),
    val tools:List<com.ai.assistance.operit.core.tools.ToolPermission> = emptyList(),
    val worldbook:WorldbookSelection=WorldbookSelection("",emptyList(),0),
    val relationships:RelationshipSelection=RelationshipSelection("",emptyList(),0),
    val memories:MemorySelection=MemorySelection("",emptyList(),0),
    val environment:EnvironmentSelection=EnvironmentSelection(),
    val planner:PlannerSelection=PlannerSelection(),
    val maxOutputTokens:Int?=null,
    val group:GroupRequestIdentity?=null,
    val play:PlayRequestIdentity?=null)

class ChatFailure(val code: String) : Exception(code)
fun errorText(code: String): String = when (code) {
    "MODEL_LIST_KEY" -> "请填写 API Key。已保存连接只有在服务地址不变时才能使用原 Key。"
    "MODEL_LIST_ENDPOINT" -> "服务未提供此地址的 /models 接口，请检查 API 基地址。"
    "MODEL_LIST_PROTOCOL" -> "服务返回的模型列表格式不正确，未加载列表。"
    "MODEL_CONFIG_CHANGED" -> "渠道已变更，请重新打开后操作。"
    "MODEL_CONFIG_IN_USE" -> "此渠道仍被角色、聊天或历史记录使用，不能删除。可编辑渠道设置。"
    "HOME_TOGETHER_BUSY"->"你们正在一起活动，完成后再开始下一件事。"
    "HOME_TOGETHER_EMPTY"->"这次活动已经完成，请查看最新家园记录。"
    "HOME_TOGETHER_RUNNING"->"活动还在进行，倒计时结束后再完成。"
    "HOME_ENERGY"->"精力不足，去卧室一起休息可以恢复 6 点精力。"
    "HOME_REST_FULL"->"精力已经充足，选择其他共同活动吧。"
    "HOME_MEAL"->"请先在厨房制作一份食物，再选择一起吃饭。"
    "HOME_COINS"->"家园币不足。出售物品、共同活动和完成心愿可以获得家园币。"
    "HOME_FURNITURE_LEVEL"->"还没有达到这件家具的家园等级，先一起完成活动吧。"
    "HOME_WISH"->"这个心愿尚未完成、已经领取或已进入新的一天。"
    "HOME_LIFE_LIMIT"->"家园币或经验已达到当前存档上限。"
    "HOME_CHANGED"->"家园状态已更新，请先查看最新存档，再重新选择操作。"
    "HOME_PENDING"->"请先重试上次操作，或放弃重试后再继续。"
    "HOME_COMMAND_CONFLICT"->"这次操作的身份与原记录不符，已拒绝执行。请查看家园记录。"
    "HOME_CLOCK"->"手机时间早于家园上次保存时间或超出范围，请校准手机时间后重试。"
    "HOME_FORMAT"->"家园存档或操作记录格式异常，已停止读取；请保留原数据。"
    "HOME_CAPACITY","HOME_LIMIT"->"物品数量或存档版本已到上限，请先整理背包。"
    "HOME_QUANTITY"->"数量超出允许范围，请重新填写。"
    "HOME_MATERIAL"->"材料或种子不足，请查看最新背包。"
    "HOME_WORN"->"这件衣服正在穿着，请先卸下或保留至少一件。"
    "HOME_GROWING"->"作物还没成熟，请等倒计时结束后收获。"
    "HOME_WATERED","HOME_MATURE"->"这块田已经浇过水或已成熟，请查看最新状态。"
    "HOME_EMPTY","HOME_OCCUPIED","HOME_PLOT"->"田地状态已改变，请重新选择田地。"
    "HOME_GIFT_TYPE","HOME_SEED"->"种子不能赠送或丢弃；原材料需要先制作成礼物。"
    "HOME_OUTFIT"->"对应背包中没有这件服装，请先制作或赠送。"
    "HOME_REQUIRED"->"请先打开共享家园，再领取纪念物。"
    "HOME_RESIDENT","HOME_RESIDENT_EXISTS"->"居民已变动，请重新选择人物。"
    "HOME_RESIDENT_LIMIT"->"家园暂时没有空闲的居民位置。"
    "HOME_MOVE","HOME_DISTANCE"->"先走到目标旁边，再进行互动。"
    "HOME_RESOURCE","HOME_RESOURCE_WAIT"->"资源正在恢复，稍后再来。"
    "HOME_SELL"->"这件物品不能出售。"
    "HOME_BACKUP_EXISTS"->"本机已有共享家园，请在备份预览中选择让角色入住本机家园。"
    "HOME_REWARD_UNEARNED"->"这次冒险尚未获得该纪念物，请先完成对应任务。"
    "PLAY_FORMAT" -> "活动存档格式无效，现有进度没有改动。"
    "PLAY_ACTION" -> "这个动作不符合当前回合或游戏规则，进度没有改动。"
    "PLAY_CHANGED" -> "活动已暂停、结束或发生变化，请重新打开后操作。"
    "PLAY_LIMIT" -> "本次活动已达到记录上限，请结束并新建活动。"
    "PLAY_RESPONSE" -> "角色没有返回有效动作，已保留当前进度，可以重试。"
    "GROUP_INPUT" -> "群资料或消息格式无效，请检查后重试。"
    "GROUP_MEMBERS" -> "群聊至少需要两个有效角色，请先管理群成员。"
    "GROUP_CHANGED" -> "群资料已变更，请重新打开后再操作。"
    "GROUP_LIMIT" -> "群或群消息已达到本机数量上限，请整理后再创建。"
    "BILI_LINK" -> "请输入有效的 B 站完整视频链接或 BV/av 号，分集需在 1～1000 之间。"
    "BILI_NETWORK" -> "B 站网页连接失败，请检查网络后重新打开。"
    "BILI_AUDIO" -> "网页音频传输中断或无法确认，已停止播放。请重新打开视频。"
    "BILI_AUDIO_UNAVAILABLE" -> "系统 WebView 不支持本次播放所需的音频能力，请更新 Android System WebView 后重试。"
    "BILI_UNAVAILABLE" -> "官方播放器未能提供可用视频，请检查视频是否公开、网络和系统 WebView 是否正常。"
    "BILI_PROGRESS" -> "播放器返回的进度无法确认，已停止。现有观看记录仍保留。"
    "BILI_NAVIGATION" -> "本页只播放所选视频；跳转、登录和下载未执行，请重新打开播放器。"
    "INPUT" -> "输入不完整或格式有误，请检查填写内容。"
    "RELATIONSHIP_LIMIT" -> "每个角色最多保存 32 条关系，请整理后再添加。"
    "RELATIONSHIP_CHANGED" -> "关系已被修改或删除，请重新打开后编辑。"
    "MEMORY_LIMIT" -> "每个角色最多保存 64 条长期记忆，请整理后再添加。"
    "MEMORY_CHANGED" -> "记忆已被修改或删除，请重新打开后编辑。"
    "MEMORY_CONFLICT" -> "检测到可能重复或冲突的记忆，请查看比较结果并选择并存或替换。"
    "MEMORY_EXTRACTION_FORMAT" -> "模型没有返回有效的记忆候选。没有保存任何内容，请重试或手工新建记忆。"
    "MEMORY_SOURCE_LIMIT" -> "所选对话片段过长，无法安全提炼。请改为手工新建记忆。"
    "MEMORY_SOURCE_CHANGED" -> "来源消息、角色或模型配置已经变化，没有保存候选。请重新选择回复。"
    "MEMORY_SOURCE_REQUIRED" -> "这段对话仍关联提炼记忆。请先选择保留记忆并断开来源，或连同记忆一起删除。"
    "MEMORY_EXTRACTION_BUSY" -> "当前已有聊天、生图或记忆提炼请求执行中，请先停止后重试。"
    "DIARY_LIMIT" -> "每个角色最多保存 256 篇日记与心事，请整理后再添加。"
    "DIARY_CHANGED" -> "日记已被修改或删除，请重新打开后操作。"
    "DIARY_SOURCE_CHANGED" -> "角色或模型配置已经变化，没有保存草稿。请重新生成。"
    "DIARY_GENERATION_FORMAT" -> "模型没有返回有效的日记草稿。没有保存任何内容，请重试或手工新建。"
    "DIARY_GENERATION_BUSY" -> "当前已有聊天、生图、记忆提炼或日记生成请求执行中，请先停止后重试。"
    "DIARY_EXPORT_LIMIT" -> "日记超过 128 KiB 导出上限，本次未保存。"
    "WEATHER_CITY" -> "城市名称需要 2–120 个字符，请填写城市，并可附加省份或国家。"
    "WEATHER_EMPTY" -> "没有找到匹配城市，请补充省份或国家后重试。"
    "WEATHER_MISSING" -> "请先明确选择城市并手工获取一次天气，再开启聊天天气感知。"
    "WEATHER_CHANGED" -> "城市或天气设置已经变化，请重新打开后操作。"
    "WEATHER_NETWORK" -> "天气服务连接失败，没有更改已保存天气。"
    "WEATHER_PROTOCOL" -> "天气服务返回了无法验证的数据，没有保存。"
    "WEATHER_LIMIT" -> "天气服务返回内容超过 512 KiB 安全上限，已拒绝。"
    "PLANNER_LIMIT" -> "这个角色的日程、待办与纪念日已达到 512 条上限，请整理后再添加。"
    "CYCLE_LIMIT" -> "这个角色的经期记录已达到 256 条上限，请整理后再添加。"
    "PLANNER_CHANGED" -> "记录已被修改或删除，请重新打开后操作。"
    "PLANNER_INPUT" -> "日期、时间或内容格式有误，请检查后重试。"
    "PROACTIVE_DISABLED" -> "主动消息当前未开启，或通知权限已关闭。"
    "PROACTIVE_CONFIG" -> "主动消息需要有效对话、角色模式和有效的模型连接。"
    "PROACTIVE_LIMIT" -> "主动消息仍在免打扰、频率或每日次数限制内，本次没有联网。"
    "MODEL_GENERATION_BUSY" -> "当前已有模型请求正在执行，请先停止后重试。"
    "WORLDBOOK_LIMIT" -> "每个角色最多保存 64 条世界书条目，请整理后再添加。"
    "WORLDBOOK_CHANGED" -> "条目已被修改或删除，请重新打开后编辑。"
    "WORLDBOOK_TEMPLATE" -> "世界书模板无效、版本不支持或超过 256 KiB 限制。"
    "BACKUP_LIMIT" -> "备份过大或可用内存不足。内容最多 32 MiB、共 10 万条记录；原有数据保持不变。"
    "BACKUP_PASSWORD" -> "备份口令需要 12～128 个字符，请使用独立的长口令并妥善保存。"
    "BACKUP_AUTH" -> "口令不正确或备份已损坏，无法验证内容。未恢复任何数据。"
    "BACKUP_FORMAT" -> "备份版本、内容或数据关联不符合要求，未恢复任何数据。"
    "LOCKED" -> "请先解锁应用，再继续操作。"
    "ACCESS" -> "角色或会话已变更，无法访问这段数据。请重新选择角色。"
    "TEMPLATE" -> "角色模板无效、版本不支持或超过限制，请检查文件。"
    "FILE" -> "文件无法读取或保存，请重新选择文件位置。"
    "IMAGE" -> "图片无法读取。请选择 20 MB 以内的 JPEG、PNG 或 WebP 图片。"
    "VISION_DISABLED" -> "当前模型连接未启用图片输入，请在模型设置中确认服务支持后开启。"
    "IMAGE_LIMIT" -> "这段对话已保存 32 张图片，请新建对话后继续。"
    "STICKER_IMAGE" -> "素材无法读取。请选择 20 MB 以内的 JPEG、PNG 或 WebP；动图只保存静态首帧。"
    "STICKER_NAME" -> "素材名称需要 1～40 个字符，不能包含控制字符。"
    "STICKER_LIMIT" -> "这个角色已保存 64 个本机素材，请删除不再使用的素材后重试。"
    "STICKER_CHAT_LIMIT" -> "这段对话已保存 64 个表情素材，请新建对话后继续。"
    "IMAGE_GENERATION_CONFIG" -> "生图服务配置无效，请检查 HTTPS 地址、模型、尺寸、超时、Key。"
    "IMAGE_GENERATION_PROMPT" -> "生图提示词需要 1～4000 个字符，不能包含无效控制字符。"
    "IMAGE_GENERATION_LIMIT" -> "生图响应或本地图片超过安全上限，本次未保存结果。"
    "IMAGE_GENERATION_IMAGE" -> "服务返回的图片无法安全读取或压缩，本次未保存结果。"
    "IMAGE_GENERATION_PROTOCOL" -> "生图服务没有返回可用的 Base64 图片，请检查接口兼容性。"
    "IMAGE_GENERATION_COUNT" -> "这段对话已保存 32 条生图记录，请新建对话后继续。"
    "IMAGE_GENERATION_BUSY" -> "当前已有聊天或生图请求正在执行，请先停止后重试。"
    "SPEECH_CONFIG" -> "语音服务配置无效，请检查类型、HTTPS 地址、模型、声音、超时和 Key。"
    "SPEECH_BUSY" -> "当前已有模型、语音识别或语音合成请求正在执行，请先停止后重试。"
    "SPEECH_PROTOCOL" -> "语音服务返回了无法验证的内容，请检查接口与 PCM 输出格式。"
    "SPEECH_LIMIT" -> "录音、识别文字或合成音频超过安全上限，本次结果未保留。"
    "MICROPHONE_PERMISSION" -> "没有麦克风权限。请在点击录音后允许权限，再重试。"
    "MICROPHONE_UNAVAILABLE" -> "麦克风当前不可用，请关闭占用麦克风的应用后重试。"
    "RECORDING_TOO_SHORT" -> "录音不足 0.25 秒，没有生成可上传内容。"
    "AUDIO_FOCUS" -> "当前无法取得音频播放权限，请停止其他音频后重试。"
    "AUDIO_ROUTE_BUSY" -> "其他通话或音频任务尚未结束，请稍后重新选择设备。"
    "AUDIO_ROUTE_PAIR" -> "此 Android 版本使用蓝牙耳麦麦克风时，声音也必须播放到蓝牙耳麦。请重新选择播放设备或麦克风。"
    "AUDIO_ROUTE_UNAVAILABLE" -> "所选音频设备不可用，请连接设备后刷新并重新选择。"
    "AUDIO_ROUTE_TIMEOUT" -> "音频设备连接超时，请检查耳机连接后重新选择。"
    "AUDIO_ROUTE_CHANGED" -> "音频设备已断开或发生切换，本轮已停止。请重新选择设备。"
    "AUDIO_ROUTE_CLEANUP" -> "音频设备未能正常释放，请结束语音并重启应用后再试。"
    "AUDIO_PLAYBACK" -> "设备无法播放本次语音，请检查媒体音量和音频输出。"
    "MATERIAL_TYPE" -> "当前只支持 UTF-8 编码的 TXT、Markdown，以及 HTTPS 的纯文本、Markdown 或 HTML 链接。"
    "MATERIAL_ENCODING" -> "资料不是有效的 UTF-8 文本，请转换编码后重试。"
    "MATERIAL_LIMIT" -> "资料过大：本机文件最多 256 KiB，链接响应最多 512 KiB。"
    "MATERIAL_TEXT_LIMIT" -> "资料文字过大，无法在本轮安全加入上下文。"
    "MATERIAL_NAME" -> "资料名称无效或过长。"
    "MATERIAL_EMPTY" -> "没有从资料中读取到可用文字。"
    "MATERIAL_FORMAT" -> "资料格式无法安全解析。"
    "MATERIAL_COUNT" -> "这段对话已保存 48 份资料，请新建对话后继续。"
    "READING_TYPE" -> "伴读只支持 UTF-8 编码的 TXT、Markdown 文件。"
    "READING_ENCODING" -> "伴读文件不是有效的 UTF-8 文本，请转换编码后重试。"
    "READING_LIMIT" -> "伴读文件过大，单份最多 1 MiB 且不超过 75 万字符。"
    "READING_EMPTY" -> "伴读文件中没有可阅读的文字。"
    "READING_NAME" -> "伴读文件名无效或过长。"
    "READING_CHAPTERS" -> "伴读文件章节过多，最多支持 512 节。"
    "READING_COUNT" -> "这个角色已保存 16 份伴读文件，请删除未引用的旧文件后重试。"
    "READING_PROGRESS" -> "阅读位置不在当前章节中，请重新打开文件。"
    "READING_REFERENCE" -> "伴读引用与本机原文不一致，请重新选择片段。"
    "READING_REFERENCED" -> "这份书籍已被聊天或共同活动引用。请先处理关联活动；已发送的引用仍需保留原文。"
    "ACTIVITY_STATE" -> "当前活动状态不允许这项操作。结束的活动可以查看，也可重新发起一次活动。"
    "ACTIVITY_CHANGED" -> "活动已更新，请查看最新进度后再试。"
    "MUSIC_SOURCE" -> "无法读取原媒体文件，请在当前页面重新选择原文件并确认。"
    "MUSIC_CHANGED" -> "媒体文件内容已改变，请重新关联同一份原文件。不同内容请作为新条目添加。"
    "MUSIC_FORMAT" -> "无法识别为所选类型，请选择有效的音频文件或平台支持的 MP4/WebM 视频文件。"
    "MUSIC_LIMIT" -> "音频最多 32 MiB，视频最多 512 MiB，时长最多 6 小时；每个角色合计最多 64 个媒体条目。"
    "MUSIC_BUSY" -> "请先暂停当前播放或结束语音，再执行这项操作。"
    "MUSIC_REFERENCED" -> "媒体条目还有关联播放记录，请先删除相关记录，再移出媒体库。"
    "MUSIC_FOCUS" -> "音频被其他应用占用，媒体播放已停止。可以稍后点击播放继续。"
    "MUSIC_ROUTE_CHANGED" -> "音频输出设备发生变化，媒体播放已停止。请确认耳机或扬声器后重新点击播放。"
    "MUSIC_ROUTE_UNAVAILABLE" -> "无法确认当前播放使用的音频设备，播放已停止。请检查系统音频输出后再试。"
    "MUSIC_PLAYBACK" -> "媒体播放失败，进度已尝试保存。请检查原文件、格式与系统输出后重试。"
    "MUSIC_SAVE" -> "播放进度保存失败，请重启应用恢复暂停状态并检查进度。"
    "MEDIA_SEEKABLE" -> "此文件来源不支持随机读取，请选择保存在本机文件夹里的音视频文件。"
    "VIDEO_SURFACE" -> "视频画面尚未准备好，请稍后点击播放。"
    "ACTIVITY_LIMIT" -> "当前会话最多保留 32 次活动，请先整理已结束的活动。"
    "EXPORT_MESSAGE" -> "只支持保存已完成且包含正文的 AI 回复。"
    "EXPORT_FORMAT" -> "文件格式无效，请重新选择 TXT 或 Markdown。"
    "EXPORT_NAME" -> "文件名无效；请勿包含路径符号、控制字符或首尾空格。"
    "EXPORT_LIMIT" -> "回复超过 512 KiB 文件输出上限，本次未保存。"
    "EXPORT_CHANGED" -> "回复已变化或不再属于当前会话，请重新打开预览。"
    "EXPORT_WRITE" -> "文件保存后无法完整回读核对，未确认保存成功。请重新选择位置。"
    "LINK_URL" -> "链接必须是有效的 HTTPS 地址，且不能包含账号信息。"
    "LINK_REDIRECT" -> "链接返回了重定向。为避免把内容发送到未确认地址，本次未继续访问。"
    "LINK_HTTP" -> "链接读取失败，服务器没有返回可读取的正文。"
    "AUTH" -> "API Key 无效或没有访问权限，请检查设置。"
    "RATE_LIMIT" -> "服务商限流或额度不足，请稍后重试并检查账户。"
    "TIMEOUT" -> "等待超时，已保留收到的内容。"
    "NETWORK" -> "网络连接中断，请检查网络后重试。"
    "SERVER" -> "模型服务暂时不可用，请稍后重试。"
    "REDIRECT" -> "服务返回了重定向。为保护 Key，请在设置中确认正确地址。"
    "REQUEST" -> "服务拒绝请求，请检查模型名称和接口地址。"
    "TOOLS_DISABLED" -> "模型请求调用工具，但当前请求没有启用相应工具协议，已停止。"
    "DECLINED" -> "你已拒绝工具调用，本次回复已停止。"
    "ARGUMENTS" -> "模型给出的工具参数不完整、格式无效或超过限制，已停止。"
    "TOOL_ERROR" -> "工具返回执行失败，详情见工具记录。"
    "CLOSE" -> "工具已执行，但连接关闭未完成，请先检查调用记录。"
    "TRUNCATED" -> "连接已结束，但没有收到完整结束标记。"
    "PROTOCOL" -> "服务响应格式不符合 Chat Completions 协议。"
    "INTERRUPTED" -> "上次请求因应用进程结束而中断。"
    "CONTEXT_LIMIT" -> "当前对话已达到本版上下文上限，请新建对话。"
    "TOOL_CONTEXT_LIMIT" -> "已授权的工具说明过多，请减少当前角色的工具数量后重试。"
    "CONFIG_CHANGED" -> "扩展配置已变更，请重新打开设置后操作。"
    "ROLE_CHANGED" -> "角色设定已发生变化，请重新打开编辑页面后保存。"
    "TOOL_CHANGED" -> "工具定义已变化，请刷新工具列表并重新授权。"
    "DISABLED" -> "请先启用这个扩展，再为角色授权工具。"
    "REVOKED" -> "工具授权已撤回，本次调用已停止。"
    "DUPLICATE" -> "模型重复请求了同一次工具调用，已拒绝重复执行。"
    "CALL_LIMIT" -> "本次请求已达到 8 次工具调用上限，已停止。"
    "OUTPUT_LIMIT" -> "回答已达到本版长度上限，已保留收到的内容。"
    "CREDENTIAL" -> "凭据不可读取或与服务地址不匹配，请重新保存 Key。"
    "STORAGE" -> "本地加密存储不可用。请保留应用数据，勿卸载或清空。"
    "EMPTY" -> "服务没有返回可显示的文本，请检查模型设置。"
    else -> "操作未完成，请检查配置后重试。"
}
