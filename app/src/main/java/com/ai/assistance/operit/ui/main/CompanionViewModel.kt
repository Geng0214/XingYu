package com.ai.assistance.operit.ui.main

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.withTransaction
import com.ai.assistance.operit.api.chat.*
import com.ai.assistance.operit.api.chat.llmprovider.*
import com.ai.assistance.operit.api.image.*
import com.ai.assistance.operit.api.audio.*
import com.ai.assistance.operit.api.environment.OpenMeteoService
import com.ai.assistance.operit.data.db.*
import com.ai.assistance.operit.data.model.*
import com.ai.assistance.operit.core.tools.*
import com.ai.assistance.operit.reminder.ReminderScheduler
import com.ai.assistance.operit.proactive.*
import com.ai.assistance.operit.core.tools.mcp.*
import com.ai.assistance.operit.core.tools.packTool.*
import com.ai.assistance.operit.data.security.CredentialVault
import com.ai.assistance.operit.data.security.LocalWallpaper
import com.ai.assistance.operit.data.security.PreparedWallpaper
import com.ai.assistance.operit.data.security.PreparedMessageImage
import com.ai.assistance.operit.data.security.PrivacyGate
import com.ai.assistance.operit.data.backup.*
import android.graphics.Bitmap
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.UUID

data class BilibiliPlaybackUi(val watch:BilibiliWatch,val player:BilibiliPlayer)

data class WallpaperPreview(val scope: RoleAccessScope,val roleName: String,val image: PreparedWallpaper)
data class PortraitPreview(val scope: RoleAccessScope,val kind: String,val roleName: String,val image: PreparedWallpaper)
data class StickerImportPreview(val scope:RoleAccessScope,val roleName:String,val suggestedName:String,val image:PreparedMessageImage)
data class BackupUi(val stage:String,val summary:BackupSummary?=null,val homeExists:Boolean=false)
data class WorldbookData(val entries:List<WorldbookEntry>,val chats:List<ChatEntity>,val error:String?=null)
data class WorldbookFilePreview(val scope:RoleAccessScope,val roleName:String,val template:WorldbookTemplate,val ignored:Int,val exporting:Boolean)
data class RelationshipData(val entries:List<RelationshipEntry>,val chats:List<ChatEntity>,val error:String?=null)
data class MemoryData(val entries:List<MemoryEntry>,val chats:List<ChatEntity>,val error:String?=null)
data class MemoryExtractionUi(val stage:String,val frozen:FrozenMemoryExtraction,val candidates:List<MemoryCandidate> = emptyList(),
    val completedAt:Long?=null)
data class DiaryData(val entries:List<DiaryEntry>,val error:String?=null)
data class DiaryGenerationUi(val stage:String,val frozen:FrozenDiaryGeneration,val draft:DiaryDraft?=null)
data class EnvironmentData(val profile:EnvironmentProfile?,val error:String?=null)
data class WeatherLookupUi(val stage:String,val scope:RoleAccessScope,val roleName:String,val city:String,val candidates:List<WeatherCandidate> = emptyList(),val selected:WeatherCandidate?=null)
data class PlannerData(val entries:List<PlannerEntry>,val cycles:List<CycleRecord>,val error:String?=null)
data class HomeSummaryData(val view:HomeView?=null,val error:String?=null)
data class PlaySummaryData(val sessions:List<PlaySessionRow> = emptyList(),val error:String?=null)
data class GroupUi(val scope:GroupAccessScope,val selectionId:String,val timeline:GroupTimeline?=null,val preview:RequestSnapshot?=null,val draft:String="",val participantId:String?=null,val revision:Long=0)
data class ProactiveData(val setting:ProactiveRoleSetting?,val chats:List<ChatEntity>)
data class GroupEditorUi(val original:GroupDefinition?,val title:String,val userName:String,val selected:Map<RoleAccessScope,String?>,val acknowledged:Boolean=false)
data class FailureNotice(val code:String,val message:String,val route:RecoveryRoute)
private data class ActiveVoiceConversation(
    val id:String,val scope:RoleAccessScope,val chatId:String,val roleName:String,
    val transcription:SpeechServiceConfig,val synthesis:SpeechServiceConfig,val privacyPermit:Long,
    val input:CommunicationDevice,val output:CommunicationDevice,
    val routeFailure:CommunicationRouteFailure=CommunicationRouteFailure())

data class VoiceAudioDevices(val inputs:List<CommunicationDevice>,val outputs:List<CommunicationDevice>)
enum class SingleSpeechStage { CONNECTING, SYNTHESIZING, PREPARING, PLAYING }

data class CompanionState(val ready: Boolean=false, val fatal: Boolean=false,
    val groupInbox:List<GroupInboxRow> = emptyList(),val group:GroupUi?=null,val groupEditor:GroupEditorUi?=null,val busyGroup:GroupRequestIdentity?=null,
    val roles: List<RoleEntity> = emptyList(), val configs: List<ModelConfigData> = emptyList(),
    val chats: List<ChatEntity> = emptyList(), val messages: List<MessageEntity> = emptyList(),
    val scope: RoleAccessScope?=null, val chatId: String?=null, val busyChat: String?=null,val thinking:Boolean=false,
    val busyScope: RoleAccessScope?=null, val testing: Boolean=false, val working: Boolean=false,
    val importPreview: TemplatePreview?=null, val exportPreview: RoleTemplate?=null,
    val inbox: List<ConversationCard> = emptyList(), val appearance: ChatAppearance?=null,
    val wallpaper: Bitmap?=null, val wallpaperPreview: WallpaperPreview?=null,
    val portraitPreview: PortraitPreview?=null, val focusMessageId: String?=null,
    val extensions:List<McpExtension> = emptyList(),val extensionTesting:String?=null,
    val toolInvocations:List<ToolInvocationRecord> = emptyList(),val toolPackages:List<ToolPkgInfo> = emptyList(),
    val toolPkgPreview:ToolPkgBundle?=null,val toolPkgInstalling:Boolean=false,val backup:BackupUi?=null,
    val worldbookFile:WorldbookFilePreview?=null,val worldbookError:String?=null,val relationshipError:String?=null,val memoryError:String?=null,val diaryError:String?=null,val environmentError:String?=null,val plannerError:String?=null,
    val completedReplies:Int=0,val guidePending:Boolean=false,val guideOpen:Boolean=false,val diagnostic:DiagnosticReport?=null,
    val attachments:List<MessageAttachment> = emptyList(),val pendingImage:PreparedMessageImage?=null,
    val materials:List<MessageMaterial> = emptyList(),val pendingMaterial:PromptMaterial?=null,val materialPreview:PromptMaterial?=null,
    val stickerAssets:List<StickerAsset> = emptyList(),val messageStickers:List<MessageSticker> = emptyList(),
    val pendingSticker:PromptSticker?=null,val stickerPreview:StickerImportPreview?=null,
    val imageGenerationConfigs:List<ImageGenerationConfig> = emptyList(),val generatedImages:List<GeneratedImage> = emptyList(),
    val generatingImageId:String?=null,val readingDocuments:List<ReadingDocument> = emptyList(),val sharedActivities:List<SharedActivity> = emptyList(),
    val readingImportPreview:PreparedReadingDocument?=null,val messageExportPreview:MessageExportPreview?=null,
    val memoryExtraction:MemoryExtractionUi?=null,val diaryGeneration:DiaryGenerationUi?=null,val diaryExportPreview:DiaryExportPreview?=null,val weatherLookup:WeatherLookupUi?=null,
    val proactiveMaster:Boolean=false,val proactiveDailyLimit:Int=3,val proactiveRequestIds:Set<String> = emptySet(),
    val speechServiceConfigs:List<SpeechServiceConfig> = emptyList(),val recordedVoice:RecordedVoice?=null,
    val voiceRecording:Boolean=false,val voiceRecordingReady:Boolean=false,val transcribingVoice:Boolean=false,val speakingMessageId:String?=null,
    val singleSpeechStage:SingleSpeechStage?=null,
    val voiceConversation:VoiceConversationUi?=null,
    val musicTracks:List<MusicTrack> = emptyList(),val musicSessions:List<MusicSession> = emptyList(),
    val bilibiliWatches:List<BilibiliWatch> = emptyList(),val bilibiliPlayback:BilibiliPlaybackUi?=null,
    val musicImport:MusicImport?=null,val playingMusicId:String?=null,val play:PlayUi?=null,val home:HomeUi?=null,
    val companionThemeImport:CompanionThemeSpec?=null)


class CompanionViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(CompanionState())
    val state = _state.asStateFlow()
    private val notices = Channel<String>(Channel.BUFFERED)
    val events = notices.receiveAsFlow()
    private val failureNotices=Channel<FailureNotice>(Channel.BUFFERED)
    val failures=failureNotices.receiveAsFlow()
    private val draftInsertions=Channel<String>(Channel.BUFFERED)
    val speechDrafts=draftInsertions.receiveAsFlow()
    private val guidePreferences=app.getSharedPreferences("companion-ui",android.content.Context.MODE_PRIVATE)
    private val proactivePreferences=ProactivePreferences(app)
    private lateinit var db: AppDatabase
    private lateinit var repository: RoleRepository
    private lateinit var vault: CredentialVault
    private lateinit var coordinator: ChatCoordinator
    private lateinit var playRepository:PlayRepository
    private lateinit var homeRepository:HomeRepository
    private var homeJob:Job?=null
    private var homePositionJob:Job?=null
    private var homePositionDraft:HomeAction.Move?=null
    private var homePositionSerial=0L
    private lateinit var playCoordinator:PlayCoordinator
    private var playListJob:Job?=null
    private var playViewJob:Job?=null
    @Volatile private var playEpoch=0L
    private var groupJob:Job?=null
    private lateinit var extensions:ExtensionRepository
    private lateinit var toolPackages:ToolPkgRepository
    private lateinit var imageGenerationRepository:ImageGenerationRepository
    private lateinit var readingRepository:ReadingRepository
    private var installJob:Job?=null
    private val approvals=ToolApprovalGate()
    val pendingApproval=approvals.pending
    private var extensionTestJob:Job?=null
    private var messagesJob: Job?=null
    private var chatsJob: Job?=null
    private var appearanceJob: Job?=null
    private var stickersJob:Job?=null
    private var readingJob:Job?=null
    private var activitiesJob:Job?=null
    private var musicObserveJob:Job?=null
    private lateinit var musicFiles:LocalMusicFiles
    private var musicPickTarget:Triple<RoleAccessScope,MusicTrack?,String>?=null
    @Volatile private var musicJob:Job?=null
    private var readingProgressJob:Job?=null
    private var wallpaperTarget: RoleAccessScope?=null
    private var portraitTarget: Pair<RoleAccessScope,String>?=null
    private var messageImageTarget:Pair<RoleAccessScope,String>?=null
    private var messageFileTarget:Pair<RoleAccessScope,String>?=null
    private var readingFileTarget:RoleAccessScope?=null
    private var stickerTarget:RoleAccessScope?=null
    private var pendingMessageExport:PreparedMessageExport?=null
    private var pendingDiaryExport:PreparedDiaryExport?=null
    private val materialReader=TextMaterialReader()
    private val readingDocumentReader=ReadingDocumentReader()
    private val operations=Any()
    private var operating=false
    @Volatile private var actionJob:Job?=null
    private var externalResult:(()->Unit)?=null
    private var backupSnapshot:BackupSnapshot?=null
    private var backupCiphertext:ByteArray?=null
    private var backupJob:Job?=null
    private val imageGenerationLock=Any()
    @Volatile private var imageGenerationJob:Job?=null
    private var imageGenerationScope:RoleAccessScope?=null
    private var imageGenerationChatId:String?=null
    private val speechLock=Any()
    @Volatile private var recordingJob:Job?=null
    @Volatile private var speechJob:Job?=null
    @Volatile private var voiceConversationJob:Job?=null
    @Volatile private var activeVoiceConversation:ActiveVoiceConversation?=null
    @Volatile private var voiceConversationInterrupting=false
    val privacy=PrivacyGate(::onPrivacyClosed)
    private val service = OpenAIProvider()
    private val memoryCandidateService=MemoryCandidateService(service)
    private val diaryGenerationService=DiaryGenerationService(service)
    private val weatherService=OpenMeteoService()
    private val imageGenerationService:ImageGenerationService=OpenAIImageGenerationService()
    private val speechService:SpeechService=OpenAISpeechService()
    private val voiceRecorder=AndroidVoiceRecorder(app)
    private val pcmPlayer=AndroidPcmPlayer(app)
    private val communicationRoutes=createAndroidCommunicationRoutes(app)
    init {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                vault=CredentialVault(app)
                privacy.initialize(vault.privacyLockEnabled())
                db=AppDatabase.open(app,vault)
                db.recoverInterruptedRequests()
                // A prior process cannot resume requests; retain only keys still referenced by saved service configurations.
                val retainedCredentialRefs=buildSet {
                    addAll(db.dao().configs().first().map {it.credentialRef})
                    addAll(db.extensions().extensions().first().mapNotNull {it.credentialRef})
                    addAll(db.dao().imageGenerationConfigs().first().map {it.credentialRef})
                    addAll(db.dao().speechServiceConfigs().first().map {it.credentialRef})
                }
                vault.pruneCredentials(retainedCredentialRefs)
                musicFiles=LocalMusicFiles(app,db)
                musicFiles.prune()
                repository=RoleRepository(db)
                imageGenerationRepository=ImageGenerationRepository(db)
                readingRepository=ReadingRepository(db)
                playRepository=PlayRepository(db)
                homeRepository=HomeRepository(db)
                playCoordinator=PlayCoordinator(playRepository,service,vault,viewModelScope,
                    accessAllowed={privacy.state.value.canAccess&&state.value.play!=null},accessVersion={playEpoch})
                launch {playCoordinator.errors.collect {reportFailure(it)}}
                extensions=ExtensionRepository(db,vault)
                toolPackages=ToolPkgRepository(db)
                val toolDispatcher=ToolDispatcher(RoomToolInvocationStore(db),{permission->
                    if(ToolPkgAddress.isLocal(permission.endpoint))LocalToolPkgSession({toolPackages.load(permission)})
                    else RemoteMcpSession(permission.endpoint,permission.credentialRef?.let {vault.read(it,permission.endpoint)},permission.timeoutMs)
                },{call->val permit=privacy.permit();val answer=approvals.await(call);privacy.requirePermit(permit);answer},executionAllowed={privacy.state.value.canAccess},accessVersion={privacy.state.value.epoch})
                coordinator=ChatCoordinator(RoomRequestStore(db),ToolChatService(service,toolDispatcher::execute),vault,viewModelScope,
                    accessAllowed={privacy.state.value.canAccess},accessVersion={privacy.state.value.epoch})
                launch {db.extensions().extensions().collect {rows->_state.update {it.copy(extensions=rows)}}}
                launch {db.toolpkgs().list().collect {rows->_state.update {it.copy(toolPackages=rows)}}}
                launch { coordinator.running.collect { running -> _state.update { it.copy(thinking=running?.thinking==true,busyChat=running?.chatId,busyScope=running?.scope,busyGroup=running?.group) } } }
                launch { coordinator.errors.collect { reportFailure(it,extensionErrorText(it)) } }
                launch {
                    var initial=true
                    db.dao().roles().collect { roles -> withContext(Dispatchers.Main) {
                        _state.update { it.copy(roles=roles) }
                        if(initial) { initial=false; if(privacy.state.value.canAccess)selectRole(roles.firstOrNull()?.scope()) }
                        else if(state.value.scope!=null && roles.none { it.scope()==state.value.scope }) selectRole(null)
                    } }
                }
                launch { db.dao().configs().collect { configs -> _state.update { it.copy(configs=configs) } } }
                launch {db.dao().imageGenerationConfigs().collect {configs->_state.update {it.copy(imageGenerationConfigs=configs)}}}
                launch {db.dao().speechServiceConfigs().collect {configs->_state.update {it.copy(speechServiceConfigs=configs)}}}
                launch {db.groups().inbox().collect {rows->_state.update {it.copy(groupInbox=rows)}}}
                launch { db.dao().inbox().collect { cards -> _state.update { it.copy(inbox=cards) } } }
                launch {db.dao().completedReplyCount().collect {count->
                    val wasPending=state.value.guidePending
                    if(count>0&&!guidePreferences.getBoolean("onboarding-finished",false))guidePreferences.edit().putBoolean("onboarding-finished",true).apply()
                    _state.update {it.copy(completedReplies=count,guidePending=if(count>0)false else it.guidePending)}
                    if(count>0&&wasPending)notices.send("第一条完整回复已收到，新手引导完成。")
                }}
                val initialConfigs=db.dao().configs().first().size
                val initialChats=db.dao().inbox().first().size
                val initialReplies=db.dao().completedReplyCount().first()
                val pending=!guidePreferences.getBoolean("onboarding-finished",false)&&initialReplies==0
                _state.update { it.copy(ready=true,completedReplies=initialReplies,guidePending=pending,
                    guideOpen=pending&&initialConfigs==0&&initialChats==0,proactiveMaster=proactivePreferences.enabled(),proactiveDailyLimit=proactivePreferences.dailyLimit()) }
                ProactiveScheduler.sync(app,proactivePreferences.enabled())
            } catch (_: Exception) { if(!privacy.state.value.loaded)privacy.storageFailed();_state.update { it.copy(fatal=true) } }
        }
    }
    private suspend fun reportFailure(code:String,message:String=errorText(code)) {
        previewDiagnostic(code)
        failureNotices.send(FailureNotice(code,message,SupportDiagnostics.route(code)))
    }
    private suspend fun <T> withModelGeneration(owner:String,busyCode:String,block:suspend ()->T):T {
        if(!ModelGenerationGate.acquire(owner))throw ChatFailure(busyCode)
        try {return block()} finally {ModelGenerationGate.release(owner)}
    }
    private fun action(block: suspend () -> Unit) {
        val permit=try {privacy.permit()}catch(_:ChatFailure){notices.trySend("请先解锁应用。");return}
        val job=synchronized(operations) {
        if(operating) { notices.trySend("正在处理上一次操作，请稍候。"); return }
        operating=true
        _state.update { it.copy(working=true) }
        val created=viewModelScope.launch(Dispatchers.IO,start=CoroutineStart.LAZY) {
        try { privacy.requirePermit(permit);block() }
        catch(e: CancellationException) { throw e }
        catch(e: ChatFailure) { reportFailure(e.code) }
        catch(e: com.ai.assistance.operit.core.tools.ToolFailure) { reportFailure(e.code,extensionErrorText(e.code)) }
        catch(_: IllegalArgumentException) { reportFailure("INPUT","输入不完整或格式有误，请检查填写内容。") }
        catch(_: Exception) { reportFailure("STORAGE") }
    }
        actionJob=created
        created.invokeOnCompletion {synchronized(operations){if(actionJob===created)actionJob=null;operating=false;_state.update {it.copy(working=false)}}}
        created
        }
        job.start()
    }
    private fun onPrivacyClosed() {
        homePositionSerial++;homePositionJob?.cancel();homePositionDraft=null
        closeHome()
        closePlay()
        closeGroup();closeGroupEditor()
        stopMusic()
        _state.update {it.copy(musicImport=null)}
        actionJob?.cancel();extensionTestJob?.cancel();installJob?.cancel();imageGenerationJob?.cancel();readingProgressJob?.cancel()
        synchronized(speechLock){activeVoiceConversation=null;voiceConversationJob?.cancel()}
        recordingJob?.cancel();voiceRecorder.cancel();speechJob?.cancel();pcmPlayer.stop()
        state.value.recordedVoice?.pcm?.fill(0)
        backupSnapshot=null
        messageImageTarget=null
        messageFileTarget=null
        readingFileTarget=null
        stickerTarget=null
        pendingMessageExport=null
        pendingDiaryExport=null
        _state.update {s->s.copy(pendingImage=null,pendingMaterial=null,materialPreview=null,pendingSticker=null,stickerPreview=null,readingImportPreview=null,messageExportPreview=null,memoryExtraction=null,diaryGeneration=null,diaryExportPreview=null,weatherLookup=null,recordedVoice=null,voiceRecording=false,voiceRecordingReady=false,transcribingVoice=false,speakingMessageId=null,singleSpeechStage=null,voiceConversation=null,companionThemeImport=null,backup=when(s.backup?.stage){
            "RESTORE_PREVIEW"->BackupUi("RESTORE_PASSWORD")
            "EXPORT_PREVIEW"->null
            else->s.backup
        })}
        approvals.pending.value?.let {approvals.answer(it.id,false)}
        if(::coordinator.isInitialized)viewModelScope.launch {coordinator.cancel()}
    }
    fun receiveExternalResult(block:()->Unit) {
        // Keep only the user-initiated picker result in memory; do not read/write content while locked.
        if(privacy.state.value.canAccess&&state.value.ready&&!state.value.working)block()else externalResult=block
    }
    fun privacyOpened() {
        if(!privacy.state.value.canAccess||!state.value.ready||state.value.working)return
        if(state.value.scope==null&&state.value.group==null&&state.value.groupEditor==null)selectRole(state.value.roles.firstOrNull()?.scope())
        val pending=externalResult;externalResult=null;pending?.invoke()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if(privacy.state.value.canAccess)ReminderScheduler.sync(getApplication(),db.planner().allReminderEntries())
            } catch(_:Exception) {
                notices.trySend("本机提醒暂时无法重新安排；记录仍保留，请稍后重新解锁应用。")
            }
        }
    }
    fun privacyAuthenticated(ticket:String) {viewModelScope.launch {privacy.authenticated(ticket){withContext(Dispatchers.IO){vault.setPrivacyLock(it)}}}}
    fun selectRole(scope: RoleAccessScope?) {
        if(!privacy.state.value.canAccess)return
        closePlay()
        closeGroup()
        stopMusic();musicPickTarget=null;musicObserveJob?.cancel()
        _state.update {it.copy(musicTracks=emptyList(),musicSessions=emptyList(),bilibiliWatches=emptyList(),musicImport=null)}
        stopMusic();endVoiceConversation();cancelVoiceCapture();stopSpeech()
        messagesJob?.cancel(); chatsJob?.cancel(); appearanceJob?.cancel();stickersJob?.cancel();readingJob?.cancel();activitiesJob?.cancel();readingProgressJob?.cancel()
        // Clear synchronously, before either the old collector or a delayed DB query can return.
        messageImageTarget=null
        messageFileTarget=null
        readingFileTarget=null
        stickerTarget=null
        pendingMessageExport=null
        pendingDiaryExport=null
        _state.update { it.copy(scope=scope,chatId=null,chats=emptyList(),messages=emptyList(),attachments=emptyList(),materials=emptyList(),stickerAssets=emptyList(),messageStickers=emptyList(),generatedImages=emptyList(),readingDocuments=emptyList(),sharedActivities=emptyList(),pendingImage=null,pendingMaterial=null,materialPreview=null,pendingSticker=null,stickerPreview=null,readingImportPreview=null,messageExportPreview=null,memoryExtraction=null,diaryGeneration=null,diaryExportPreview=null,weatherLookup=null,toolInvocations=emptyList(),appearance=null,wallpaper=null,proactiveRequestIds=emptySet()) }
        if(scope==null)return
        appearanceJob=viewModelScope.launch {
            try {
                db.dao().appearance(scope.roleId,scope.spaceId).collectLatest { appearance ->
                    val bitmap=withContext(Dispatchers.IO) { appearance?.wallpaper?.let(LocalWallpaper::decode) }
                    _state.update { if(it.scope==scope)it.copy(appearance=appearance,wallpaper=bitmap)else it }
                }
            } catch(e:CancellationException) { throw e }
            catch(_:Exception) { notices.send(errorText("IMAGE")) }
        }
        chatsJob=viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repository.requireRole(scope) }
                db.dao().chats(scope.roleId,scope.spaceId).collect { rows ->
                    _state.update { if(it.scope==scope) it.copy(chats=rows) else it }
                }
            } catch(e: CancellationException) { throw e }
            catch(_: Exception) { notices.send(errorText("ACCESS")) }
        }
        stickersJob=viewModelScope.launch {
            try {
                withContext(Dispatchers.IO){repository.requireRole(scope)}
                db.dao().stickerAssets(scope.roleId,scope.spaceId).collect {rows->_state.update {if(it.scope==scope)it.copy(stickerAssets=rows)else it}}
            } catch(e:CancellationException){throw e}
              catch(_:Exception){notices.send(errorText("ACCESS"))}
        }
        readingJob=viewModelScope.launch {
            try {
                withContext(Dispatchers.IO){repository.requireRole(scope)}
                db.dao().readingDocuments(scope.roleId,scope.spaceId).collect {rows->_state.update {if(it.scope==scope)it.copy(readingDocuments=rows)else it}}
            } catch(e:CancellationException){throw e}
              catch(_:Exception){notices.send(errorText("ACCESS"))}
        }
        activitiesJob?.cancel()
        _state.update {it.copy(sharedActivities=emptyList())}
        activitiesJob=viewModelScope.launch {
            try {db.activities().observe(scope.roleId,scope.spaceId).collect {rows->
                _state.update {if(it.scope==scope)it.copy(sharedActivities=rows)else it}
            }}catch(e:CancellationException){throw e}catch(_:Exception){notices.send(errorText("ACCESS"))}
        }
        musicObserveJob=viewModelScope.launch {
            try {
                combine(db.music().tracks(scope.roleId,scope.spaceId),db.music().sessions(scope.roleId,scope.spaceId),db.bilibili().list(scope.roleId,scope.spaceId)){tracks,sessions,watches->Triple(tracks,sessions,watches)}.collect {(tracks,sessions,watches)->
                    _state.update {if(it.scope==scope)it.copy(musicTracks=tracks,musicSessions=sessions,bilibiliWatches=watches)else it}
                }
            }catch(e:CancellationException){throw e}catch(_:Exception){reportFailure("STORAGE")}
        }
    }
    fun closeChat() {
        closeHome()
        closePlay()
        stopMusic()
        stopMusic();endVoiceConversation();cancelVoiceCapture();stopSpeech()
        messagesJob?.cancel()
        messageImageTarget=null
        messageFileTarget=null
        pendingMessageExport=null
        _state.update { it.copy(chatId=null,messages=emptyList(),attachments=emptyList(),materials=emptyList(),messageStickers=emptyList(),generatedImages=emptyList(),pendingImage=null,pendingMaterial=null,materialPreview=null,pendingSticker=null,messageExportPreview=null,memoryExtraction=null,toolInvocations=emptyList(),proactiveRequestIds=emptySet()) }
    }
    fun beginGroupEditor(original:GroupDefinition?) {
        if(!privacy.state.value.canAccess||state.value.working)return
        showGroupEditor(original)
    }
    private fun showGroupEditor(original:GroupDefinition?) {
        _state.update {it.copy(groupEditor=GroupEditorUi(original,original?.room?.title?:"",original?.room?.userName?:"我",original?.members?.associate {p->p.role to p.configId}?:emptyMap()))}
    }
    fun editGroupFromInbox(scope:GroupAccessScope)=action {
        // Load the current persisted definition; inbox summaries do not contain members or an edit version.
        showGroupEditor(GroupRepository(db).definition(scope))
    }
    fun groupDefinition(scope:GroupAccessScope,ready:(GroupDefinition)->Unit)=action {
        val definition=GroupRepository(db).definition(scope)
        withContext(Dispatchers.Main){ready(definition)}
    }
    fun closeGroupEditor(){_state.update {it.copy(groupEditor=null)}}
    fun updateGroupEditor(change:(GroupEditorUi)->GroupEditorUi) {
        if(!privacy.state.value.canAccess||state.value.working)return
        _state.update {s->s.copy(groupEditor=s.groupEditor?.let(change))}
    }
    fun setGroupComposer(scope:GroupAccessScope,draft:String,participant:String?) {
        if(!privacy.state.value.canAccess||draft.length>GroupConversations.MAX_INPUT)return
        _state.update {s->val g=s.group
            if(g?.scope==scope)s.copy(group=g.copy(draft=draft,participantId=participant,revision=g.revision+1,preview=null))else s}
    }
    fun closeGroup() {
        groupJob?.cancel();groupJob=null
        _state.update {it.copy(group=null)}
    }
    fun selectGroup(scope:GroupAccessScope) {
        if(!privacy.state.value.canAccess||!state.value.ready)return
        val previous=state.value.group?.takeIf {it.scope==scope}
        selectRole(null)
        val selection=UUID.randomUUID().toString()
        _state.update {it.copy(group=if(previous==null)GroupUi(scope,selection)else previous.copy(selectionId=selection,timeline=null,preview=null))}
        groupJob=viewModelScope.launch {
            try {
                GroupRepository(db).observe(scope).collect {timeline->
                    if(timeline==null) {
                        _state.update {if(it.group?.selectionId==selection)it.copy(group=null)else it}
                    } else _state.update {s->
                        val current=s.group
                        if(current?.selectionId==selection&&privacy.state.value.canAccess)s.copy(group=current.copy(timeline=timeline,preview=null))else s
                    }
                }
            } catch(e:CancellationException){throw e}
              catch(_:Exception) {
                _state.update {if(it.group?.selectionId==selection)it.copy(group=null)else it}
                reportFailure("ACCESS")
            }
        }
    }
    fun saveGroup(original:GroupDefinition?,title:String,userName:String,members:List<Pair<RoleAccessScope,String>>,done:()->Unit)=action {
        val groups=GroupRepository(db)
        val result=if(original==null)groups.create(title.trim(),userName.trim(),members) else {
            var edited:GroupDefinition?=null
            coordinator.mutateGroup(original.room.scope) {
                edited=groups.edit(original.room.scope,original.room.version,title.trim(),userName.trim(),members)
            }
            requireNotNull(edited)
        }
        withContext(Dispatchers.Main){selectGroup(result.room.scope);done()}
    }
    fun deleteGroup(definition:GroupDefinition,done:()->Unit={})=action {
        coordinator.mutateGroup(definition.room.scope){GroupRepository(db).delete(definition.room.scope,definition.room.version)}
        withContext(Dispatchers.Main){if(state.value.group?.scope==definition.room.scope)closeGroup();done()}
    }
    private suspend fun sendGroupRequest(scope:GroupAccessScope,participant:String,text:String):Boolean {
        if(activeVoiceConversation!=null)throw ChatFailure("SPEECH_BUSY")
        if(imageGenerationJob!=null)throw ChatFailure("IMAGE_GENERATION_BUSY")
        val groups=GroupRepository(db)
        val member=groups.definition(scope).members.singleOrNull {it.id==participant}?:throw ChatFailure("GROUP_CHANGED")
        return coordinator.sendGroup(GroupRequestStore(groups,scope,participant),member.role,scope,text)
    }
    fun sendGroup(scope:GroupAccessScope,participant:String,text:String,accepted:()->Unit) {
        val composer=state.value.group?.takeIf {it.scope==scope}?:return
        val selection=composer.selectionId
        action {
            if(sendGroupRequest(scope,participant,text.trim()))withContext(Dispatchers.Main) {
                if(state.value.group?.let {it.selectionId==selection&&it.revision==composer.revision}==true)accepted()
            } else notices.send("请先停止当前生成，再发送新消息。")
        }
    }
    fun retryGroup(scope:GroupAccessScope,requestId:String)=action {
        val (text,participant)=GroupRepository(db).retry(scope,requestId)
        if(!sendGroupRequest(scope,participant,text))notices.send("请先停止当前生成。")
    }
    fun previewGroup(scope:GroupAccessScope,participant:String,text:String) {
        val composer=state.value.group?.takeIf {it.scope==scope}?:return
        val selection=composer.selectionId
        action {
            val groups=GroupRepository(db)
            val member=groups.definition(scope).members.singleOrNull {it.id==participant}?:throw ChatFailure("GROUP_CHANGED")
            val snapshot=groups.preview(scope,participant,member.role,text.trim())
            _state.update {s->val current=s.group
                if(current?.selectionId==selection&&current.revision==composer.revision&&privacy.state.value.canAccess)s.copy(group=current.copy(preview=snapshot))else s}
        }
    }
    fun dismissGroupPreview(){_state.update {s->s.copy(group=s.group?.copy(preview=null))}}
    fun setReplyStyle(scope: RoleAccessScope, style: String) = action { repository.setReplyStyle(scope,style) }
    fun setAppearance(scope: RoleAccessScope, dimming: Float, palette: String, removeWallpaper: Boolean=false) = action {
        val previous=db.dao().readAppearance(scope.roleId,scope.spaceId)
        repository.saveAppearance(scope,if(removeWallpaper)null else previous?.wallpaper,dimming,palette)
        notices.send("聊天外观已保存。")
    }
    fun beginWallpaperPick(scope: RoleAccessScope) { wallpaperTarget=scope }
    fun readWallpaper(uri: Uri?) {
        val scope=wallpaperTarget
        wallpaperTarget=null
        if(uri==null)return
        if(scope==null) { notices.trySend("请选择角色后重新选择背景。");return }
        action {
            val role=repository.requireRole(scope)
            val image=try {
                val input=getApplication<Application>().contentResolver.openInputStream(uri) ?: throw ChatFailure("IMAGE")
                input.use(LocalWallpaper::prepare)
            } catch(_:Exception) { throw ChatFailure("IMAGE") }
            _state.update { it.copy(wallpaperPreview=WallpaperPreview(scope,role.name,image)) }
        }
    }
    fun dismissWallpaper() { _state.update { it.copy(wallpaperPreview=null) } }
    fun saveWallpaper(dimming: Float) {
        val preview=state.value.wallpaperPreview ?: return
        action {
            val old=db.dao().readAppearance(preview.scope.roleId,preview.scope.spaceId)
            repository.saveAppearance(preview.scope,preview.image.bytes,dimming,old?.palette ?: "rose")
            dismissWallpaper()
            notices.send("背景已保存，仅用于本机聊天界面。")
        }
    }
    fun selectChat(scope: RoleAccessScope, id: String, focusMessageId: String?=null) {
        if(!privacy.state.value.canAccess)return
        closeHome()
        closePlay()
        closeGroup()
        stopMusic();endVoiceConversation();cancelVoiceCapture();stopSpeech()
        if(state.value.scope!=scope)selectRole(scope)
        messagesJob?.cancel()
        messageImageTarget=null
        messageFileTarget=null
        pendingMessageExport=null
        _state.update { it.copy(chatId=id,messages=emptyList(),attachments=emptyList(),materials=emptyList(),messageStickers=emptyList(),generatedImages=emptyList(),pendingImage=null,pendingMaterial=null,materialPreview=null,pendingSticker=null,messageExportPreview=null,memoryExtraction=null,toolInvocations=emptyList(),focusMessageId=focusMessageId,proactiveRequestIds=emptySet()) }
        messagesJob=viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { repository.requireChat(scope,id) }
                launch {db.extensions().invocations(scope.roleId,scope.spaceId,id).collect {rows->
                    _state.update {if(it.scope==scope&&it.chatId==id)it.copy(toolInvocations=rows)else it}
                }}
                launch {db.dao().attachments(scope.roleId,scope.spaceId,id).collect {rows->
                    _state.update {if(it.scope==scope&&it.chatId==id)it.copy(attachments=rows)else it}
                }}
                launch {db.dao().materials(scope.roleId,scope.spaceId,id).collect {rows->
                    _state.update {if(it.scope==scope&&it.chatId==id)it.copy(materials=rows)else it}
                }}
                launch {db.dao().messageStickers(scope.roleId,scope.spaceId,id).collect {rows->
                    _state.update {if(it.scope==scope&&it.chatId==id)it.copy(messageStickers=rows)else it}
                }}
                launch {db.dao().generatedImages(scope.roleId,scope.spaceId,id).collect {rows->
                    _state.update {if(it.scope==scope&&it.chatId==id)it.copy(generatedImages=rows)else it}
                }}
                launch {db.proactive().requestIds(scope.roleId,scope.spaceId,id).collect {rows->
                    _state.update {if(it.scope==scope&&it.chatId==id)it.copy(proactiveRequestIds=rows.toSet())else it}
                }}
                db.dao().messages(scope.roleId,scope.spaceId,id).collect { rows ->
                    _state.update { if(it.scope==scope && it.chatId==id) it.copy(messages=rows) else it }
                }
            } catch(e: CancellationException) { throw e }
            catch(_: Exception) {
                pendingMessageExport=null
                _state.update { if(it.scope==scope && it.chatId==id) it.copy(chatId=null,messages=emptyList(),attachments=emptyList(),materials=emptyList(),messageStickers=emptyList(),generatedImages=emptyList(),pendingImage=null,pendingMaterial=null,materialPreview=null,pendingSticker=null,messageExportPreview=null,toolInvocations=emptyList(),proactiveRequestIds=emptySet()) else it }
                notices.send(errorText("ACCESS"))
            }
        }
    }
    fun clearMessageFocus() { _state.update { it.copy(focusMessageId=null) } }
    fun portrait(scope: RoleAccessScope,kind: String): Flow<Bitmap?> = flow {
        val lock=privacy.state.value;if(!lock.canAccess)return@flow
        repository.requireRole(scope)
        emitAll(db.dao().portrait(scope.roleId,scope.spaceId,kind).takeWhile {privacy.state.value.canAccess&&privacy.state.value.epoch==lock.epoch}.map {it?.image?.let(LocalWallpaper::decode) })
    }.flowOn(Dispatchers.IO)
    fun search(scope: RoleAccessScope,query: String,favoritesOnly: Boolean,page: Int): Flow<List<MessageEntity>> = flow {
        val lock=privacy.state.value;if(!lock.canAccess)return@flow
        require(query.length<=200 && page in 0..10000)
        repository.requireRole(scope)
        emitAll(db.dao().searchMessages(scope.roleId,scope.spaceId,query,favoritesOnly,50,page*50).takeWhile {privacy.state.value.canAccess&&privacy.state.value.epoch==lock.epoch})
    }.flowOn(Dispatchers.IO)
    fun setPinned(scope: RoleAccessScope,chatId: String,pinned: Boolean)=action { repository.setPinned(scope,chatId,pinned) }
    fun setFavorite(scope: RoleAccessScope,message: MessageEntity,favorite: Boolean)=action { repository.setFavorite(scope,message.chatId,message.id,favorite) }
    fun deleteMessages(scope:RoleAccessScope,chatId:String,messageIds:List<String>)=action {
        val detached=repository.deleteMessages(scope,chatId,messageIds)
        notices.send(if(detached>0)"消息已删除；相关记忆已解除来源关联。" else "消息已删除。")
    }
    fun translateMessage(scope:RoleAccessScope,chatId:String,messageId:String,onTranslated:(String)->Unit)=action {
        val permit=privacy.permit()
        val role=repository.requireRole(scope)
        val chat=repository.requireChat(scope,chatId)
        val message=db.dao().message(scope.roleId,scope.spaceId,chat.id,messageId)?:throw ChatFailure("ACCESS")
        if(message.state!=MessageState.COMPLETE||message.content.isBlank())throw ChatFailure("ACCESS")
        val config=db.dao().config(chat.configId)?.copy()?:throw ChatFailure("ACCESS")
        val requestId=UUID.randomUUID().toString()
        val translator=role.copy(prompt="只负责准确翻译用户提供的内容。保留原意、语气、专有名词和格式，只输出简体中文译文，不回答内容本身的问题。")
        val request=RequestSnapshot(requestId,chat.id,UUID.randomUUID().toString(),translator,config,
            listOf(PromptTurn("user","请把下面内容翻译成简体中文，只输出译文：\n\n${message.content}")),maxOutputTokens=4096)
        val output=StringBuilder()
        withModelGeneration("translation:$requestId","MODEL_GENERATION_BUSY") {
            val key=vault.read(config.credentialRef,config.endpoint)
            service.sendEvents(request,key).collect {event->when(event) {
                is ChatStreamEvent.Text->{if(output.length+event.value.length>12000)throw ChatFailure("OUTPUT_LIMIT");output.append(event.value)}
                is ChatStreamEvent.Reasoning->Unit
                is ChatStreamEvent.RoundEnd->Unit
            }
            }
        }
        privacy.requirePermit(permit)
        val translated=output.toString().trim().takeIf {it.isNotEmpty()}?:throw ChatFailure("PROTOCOL")
        withContext(Dispatchers.Main){onTranslated(translated)}
    }
    fun previewMessageExport(scope:RoleAccessScope,message:MessageEntity)=action {
        val role=repository.requireRole(scope);val chat=repository.requireChat(scope,message.chatId)
        val current=db.dao().message(scope.roleId,scope.spaceId,chat.id,message.id)?:throw ChatFailure("ACCESS")
        val preview=MessageExports.prepare(scope,role,chat,current)
        _state.update {if(it.scope==scope&&it.chatId==chat.id)it.copy(messageExportPreview=preview)else it}
    }
    fun dismissMessageExport(){if(!state.value.working)_state.update {it.copy(messageExportPreview=null)}}
    fun approveMessageExport(format:MessageExportFormat,baseName:String,launch:(String)->Unit) {
        privacy.permit();val preview=state.value.messageExportPreview?:return
        if(state.value.working)return
        val file=try {MessageExports.freeze(preview,format,baseName)}catch(e:ChatFailure){notices.trySend(errorText(e.code));return}
        pendingMessageExport=file
        _state.update {it.copy(messageExportPreview=null)}
        launch(file.fileName)
    }
    fun writeMessageExport(uri:Uri?) {
        val file=pendingMessageExport;pendingMessageExport=null
        if(uri==null)return
        if(file==null){notices.trySend("文件输出已失效，请重新预览后保存。");return}
        action {
            val permit=privacy.permit()
            val preview=file.preview
            val current=db.dao().message(preview.scope.roleId,preview.scope.spaceId,preview.chatId,preview.messageId)?:throw ChatFailure("ACCESS")
            MessageExports.verifySource(file,current)
            val resolver=getApplication<Application>().contentResolver
            MessageExportFiles.writeVerified(file,
                {privacy.requirePermit(permit);resolver.openOutputStream(uri,"wt")},
                {privacy.requirePermit(permit);resolver.openInputStream(uri)})
            privacy.requirePermit(permit)
            notices.send("已保存并核对 ${file.fileName} · ${file.mimeType} · ${file.byteCount} 字节。")
        }
    }
    fun resetPortrait(scope: RoleAccessScope,kind: String)=action { repository.savePortrait(scope,kind,null) }
    fun beginPortraitPick(scope: RoleAccessScope,kind: String) { portraitTarget=scope to kind }
    fun readPortrait(uri: Uri?) {
        val target=portraitTarget;portraitTarget=null
        if(uri==null)return
        if(target==null) { notices.trySend("请选择角色后重新选择头像。");return }
        action {
            val role=repository.requireRole(target.first)
            val image=try {
                val input=getApplication<Application>().contentResolver.openInputStream(uri) ?: throw ChatFailure("IMAGE")
                input.use(LocalWallpaper::portrait)
            } catch(_:Exception) { throw ChatFailure("IMAGE") }
            _state.update { it.copy(portraitPreview=PortraitPreview(target.first,target.second,role.name,image)) }
        }
    }
    fun dismissPortrait() { _state.update { it.copy(portraitPreview=null) } }
    fun savePortrait() {
        val preview=state.value.portraitPreview ?: return
        action {
            repository.savePortrait(preview.scope,preview.kind,preview.image.bytes)
            dismissPortrait();notices.send("头像已保存，仅用于这个角色的本机空间。")
        }
    }
    fun createChat(scope: RoleAccessScope, configId: String) = action {
        val chat=repository.createChat(scope,configId)
        withContext(Dispatchers.Main) { selectChat(scope,chat.id) }
    }
    fun beginMessageImagePick(scope:RoleAccessScope,chatId:String){messageImageTarget=scope to chatId}
    fun readMessageImage(uri:Uri?) {
        val target=messageImageTarget;messageImageTarget=null
        if(uri==null)return
        if(target==null||state.value.scope!=target.first||state.value.chatId!=target.second){notices.trySend("对话已切换，请重新选择图片。");return}
        action {
            repository.requireChat(target.first,target.second)
            val image=try {getApplication<Application>().contentResolver.openInputStream(uri)?.use(LocalWallpaper::message)?:throw ChatFailure("IMAGE")}
            catch(e:ChatFailure){throw e}catch(_:Exception){throw ChatFailure("IMAGE")}
            _state.update {current->if(current.scope==target.first&&current.chatId==target.second)current.copy(pendingImage=image,pendingMaterial=null,materialPreview=null,pendingSticker=null)else current}
        }
    }
    fun dismissMessageImage(){_state.update {it.copy(pendingImage=null)}}
    fun beginMessageFilePick(scope:RoleAccessScope,chatId:String){messageFileTarget=scope to chatId}
    fun readMessageFile(uri:Uri?) {
        val target=messageFileTarget;messageFileTarget=null
        if(uri==null)return
        if(target==null||state.value.scope!=target.first||state.value.chatId!=target.second){notices.trySend("对话已切换，请重新选择资料。");return}
        action {
            repository.requireChat(target.first,target.second)
            val resolver=getApplication<Application>().contentResolver
            val name=try {resolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use {cursor->if(cursor.moveToFirst())cursor.getString(0)else null}}catch(_:Exception){null}
                ?:uri.lastPathSegment?.substringAfterLast('/')?:"未命名.txt"
            val material=resolver.openInputStream(uri)?.use {materialReader.file(it,name,resolver.getType(uri))}?:throw ChatFailure("FILE")
            _state.update {current->if(current.scope==target.first&&current.chatId==target.second)current.copy(materialPreview=material)else current}
        }
    }
    fun readMessageLink(scope:RoleAccessScope,chatId:String,url:String)=action {
        repository.requireChat(scope,chatId)
        val material=materialReader.link(url)
        _state.update {current->if(current.scope==scope&&current.chatId==chatId)current.copy(materialPreview=material)else current}
    }
    fun confirmMaterialPreview(){_state.update {state->state.materialPreview?.let {state.copy(pendingMaterial=it,materialPreview=null,pendingImage=null,pendingSticker=null)}?:state}}
    fun dismissMaterialPreview(){_state.update {it.copy(materialPreview=null)}}
    fun dismissMessageMaterial(){_state.update {it.copy(pendingMaterial=null)}}
    fun beginReadingFilePick(scope:RoleAccessScope){readingFileTarget=scope}
    fun readReadingFile(uri:Uri?) {
        val scope=readingFileTarget;readingFileTarget=null
        if(uri==null)return
        if(scope==null||state.value.scope!=scope){notices.trySend("角色已切换，请重新选择伴读文件。");return}
        action {
            repository.requireRole(scope)
            val resolver=getApplication<Application>().contentResolver
            val name=try {resolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use {cursor->if(cursor.moveToFirst())cursor.getString(0)else null}}catch(_:Exception){null}
                ?:uri.lastPathSegment?.substringAfterLast('/')?:"未命名.txt"
            val prepared=resolver.openInputStream(uri)?.use {readingDocumentReader.file(it,name,resolver.getType(uri))}?:throw ChatFailure("FILE")
            _state.update {current->if(current.scope==scope)current.copy(readingImportPreview=prepared)else current}
        }
    }
    fun dismissReadingImport(){_state.update {it.copy(readingImportPreview=null)}}
    fun confirmReadingImport() {
        val scope=state.value.scope?:return
        val prepared=state.value.readingImportPreview?:return
        action {
            readingRepository.import(scope,prepared)
            _state.update {if(it.scope==scope)it.copy(readingImportPreview=null)else it}
            notices.send("伴读文件已保存到当前角色的加密书架。")
        }
    }
    fun updateReadingProgress(scope:RoleAccessScope,documentId:String,chapter:Int,offset:Int) {
        val permit=try {privacy.permit()}catch(_:ChatFailure){return}
        readingProgressJob?.cancel()
        readingProgressJob=viewModelScope.launch(Dispatchers.IO) {
            try {privacy.requirePermit(permit);readingRepository.updateProgress(scope,documentId,chapter,offset)}
            catch(e:CancellationException){throw e}
            catch(e:ChatFailure){notices.send(errorText(e.code))}
            catch(_:Exception){notices.send(errorText("STORAGE"))}
        }
    }
    fun selectReadingPassage(scope:RoleAccessScope,chatId:String,documentId:String,chapter:Int,start:Int,end:Int)=action {
        val material=readingRepository.passage(scope,chatId,documentId,chapter,start,end)
        readingRepository.updateProgress(scope,documentId,chapter,start)
        _state.update {current->if(current.scope==scope&&current.chatId==chatId)current.copy(pendingMaterial=material,pendingImage=null,pendingSticker=null,materialPreview=null)else current}
        notices.send("已加入明确选择的伴读片段；发送前仍可移除。")
    }
    fun deleteReadingDocument(scope:RoleAccessScope,documentId:String)=action {
        readingRepository.delete(scope,documentId)
        notices.send("未被引用的伴读文件已删除。")
    }
    fun createReadingActivity(scope:RoleAccessScope,chatId:String,documentId:String,onCreated:(String)->Unit)=action {
        val item=SharedActivityRepository(db).create(scope,chatId,documentId)
        withContext(Dispatchers.Main){if(state.value.scope==scope&&state.value.chatId==chatId)onCreated(item.id)}
    }
    fun changeActivity(item:SharedActivity,command:String?=null,chapter:Int?=null,offset:Int?=null,note:String?=null,onSaved:()->Unit={})=action {
        SharedActivityRepository(db).change(RoleAccessScope(item.roleId,item.spaceId),item.chatId,item.id,item.version,command,chapter,offset,note)
        withContext(Dispatchers.Main){if(state.value.scope==RoleAccessScope(item.roleId,item.spaceId)&&state.value.chatId==item.chatId)onSaved()}
    }
    fun deleteActivity(item:SharedActivity,onDeleted:()->Unit)=action {
        SharedActivityRepository(db).delete(RoleAccessScope(item.roleId,item.spaceId),item.chatId,item.id,item.version)
        withContext(Dispatchers.Main){if(state.value.scope==RoleAccessScope(item.roleId,item.spaceId)&&state.value.chatId==item.chatId)onDeleted()}
    }
    fun activitySummaryDraft(item:SharedActivity,expected:String,onReady:(String)->Unit)=action {
        val scope=RoleAccessScope(item.roleId,item.spaceId)
        val text=SharedActivityRepository(db).summary(scope,item.chatId,item.id,item.version)
        if(text!=expected)throw ChatFailure("ACTIVITY_CHANGED")
        withContext(Dispatchers.Main){if(state.value.scope==scope&&state.value.chatId==item.chatId)onReady(text)}
    }
    fun selectActivityPassage(item:SharedActivity,chapter:Int,start:Int,end:Int,onReady:()->Unit)=action {
        val scope=RoleAccessScope(item.roleId,item.spaceId)
        val material=SharedActivityRepository(db).passage(scope,item.chatId,item.id,item.version,chapter,start,end)
        withContext(Dispatchers.Main){if(state.value.scope==scope&&state.value.chatId==item.chatId){
            _state.update {it.copy(pendingMaterial=material,pendingImage=null,pendingSticker=null,materialPreview=null)}
            onReady()
        }}
    }
    fun beginStickerPick(scope:RoleAccessScope){stickerTarget=scope}
    fun readSticker(uri:Uri?) {
        val scope=stickerTarget;stickerTarget=null
        if(uri==null)return
        if(scope==null||state.value.scope!=scope){notices.trySend("角色已切换，请重新选择素材。");return}
        action {
            val role=repository.requireRole(scope)
            val resolver=getApplication<Application>().contentResolver
            val image=resolver.openInputStream(uri)?.use(LocalWallpaper::sticker)?:throw ChatFailure("STICKER_IMAGE")
            _state.update {current->if(current.scope==scope)current.copy(stickerPreview=StickerImportPreview(scope,role.name,"本机表情",image))else current}
        }
    }
    fun dismissStickerPreview(){_state.update {it.copy(stickerPreview=null)}}
    fun saveSticker(name:String) {
        val preview=state.value.stickerPreview?:return
        action {
            val image=preview.image
            val prompt=PromptSticker(null,name,StickerAssets.MIME_TYPE,image.width,image.height,image.sha256,image.bytes.copyOf())
            repository.addSticker(preview.scope,name,prompt)
            _state.update {if(it.stickerPreview===preview)it.copy(stickerPreview=null)else it}
            notices.send("素材已保存到 ${preview.roleName} 的空间。")
        }
    }
    fun chooseSticker(scope:RoleAccessScope,id:String)=action {
        val sticker=repository.selectSticker(scope,id)
        _state.update {current->if(current.scope==scope&&current.chatId!=null)current.copy(pendingSticker=sticker,pendingImage=null,pendingMaterial=null,materialPreview=null)else current}
    }
    fun dismissPendingSticker(){_state.update {it.copy(pendingSticker=null)}}
    fun setStickerFavorite(scope:RoleAccessScope,id:String,favorite:Boolean)=action {repository.setStickerFavorite(scope,id,favorite)}
    fun deleteSticker(scope:RoleAccessScope,id:String)=action {
        repository.deleteSticker(scope,id)
        _state.update {current->if(current.pendingSticker?.sourceAssetId==id)current.copy(pendingSticker=null)else current}
        notices.send("素材已从当前角色的素材库删除，历史消息保持不变。")
    }
    fun chatMemorySourceCount(scope:RoleAccessScope,id:String,ready:(Int)->Unit)=action {
        val count=MemoryRepository(db).sourceCount(scope,id)
        withContext(Dispatchers.Main){ready(count)}
    }
    fun deleteChat(scope: RoleAccessScope, id: String, memoryDisposition:String="reject") = action {
        ProactiveRuntime.cancel(scope)
        if(activeVoiceConversation?.let {it.scope==scope&&it.chatId==id}==true)endVoiceConversation()
        cancelImageGenerationFor(scope,id)
        playCoordinator.mutateRole(scope,id) {coordinator.mutate(scope,id) { repository.deleteChat(scope,id,memoryDisposition) }}
        withContext(Dispatchers.Main) {
            if(state.value.scope==scope && state.value.chatId==id) {
                messagesJob?.cancel(); _state.update { it.copy(chatId=null,messages=emptyList(),attachments=emptyList(),materials=emptyList(),messageStickers=emptyList(),generatedImages=emptyList(),pendingImage=null,pendingMaterial=null,materialPreview=null,pendingSticker=null,toolInvocations=emptyList(),proactiveRequestIds=emptySet()) }
            }
        }
    }
    fun send(text: String, quote:MessageQuoteSnapshot?=null, onAccepted: () -> Unit) {
        if(activeVoiceConversation!=null){notices.trySend(errorText("SPEECH_BUSY"));return}
        if(imageGenerationJob!=null){notices.trySend(errorText("IMAGE_GENERATION_BUSY"));return}
        val scope=state.value.scope ?: return
        val id=state.value.chatId ?: return
        val prepared=state.value.pendingImage
        val image=prepared?.let {PromptImage(MessageImages.MIME_TYPE,it.width,it.height,it.sha256,it.bytes.copyOf())}
        val material=state.value.pendingMaterial
        val sticker=state.value.pendingSticker
        action {
            if(coordinator.send(scope,id,text.trim(),image,material,sticker,quote)) withContext(Dispatchers.Main) {
                if(state.value.scope==scope && state.value.chatId==id){
                    if(state.value.pendingImage?.sha256==prepared?.sha256)_state.update {it.copy(pendingImage=null)}
                    if(state.value.pendingMaterial?.sha256==material?.sha256)_state.update {it.copy(pendingMaterial=null)}
                    if(state.value.pendingSticker?.sha256==sticker?.sha256)_state.update {it.copy(pendingSticker=null)}
                    onAccepted()
                }
            } else notices.send("请先停止当前生成，再发送新消息。")
        }
    }
    fun retry(scope: RoleAccessScope, message: MessageEntity) = action {
        if(activeVoiceConversation!=null)throw ChatFailure("SPEECH_BUSY")
        if(imageGenerationJob!=null)throw ChatFailure("IMAGE_GENERATION_BUSY")
        val original=repository.retryPayload(scope,message.chatId,message.requestId)
        if(!coordinator.send(scope,message.chatId,original.text,original.image,original.material,original.sticker))notices.send("请先停止当前生成。")
    }
    fun cancel() { viewModelScope.launch { coordinator.cancel() } }
    fun saveRole(scope: RoleAccessScope?, name: String, prompt: String, opening: String, configId: String?, replyStyle: String, saved: (RoleEntity)->Unit) = action {
        val role=repository.saveRole(scope,name,prompt,opening,configId,replyStyle)
        withContext(Dispatchers.Main) { saved(role) }
        notices.send("角色已保存。人设从下一次请求生效，开场白用于新对话。")
    }
    fun copyRole(scope: RoleAccessScope, saved: (RoleEntity)->Unit) = action {
        val role=repository.copyRole(scope)
        withContext(Dispatchers.Main) { saved(role) }
        notices.send("已复制角色，聊天空间为空。")
    }
    fun savePersona(role:RoleEntity,name:String,description:String,enabled:Boolean,saved:()->Unit)=action {
        repository.savePersona(role.scope(),role.version,name,description,enabled)
        withContext(Dispatchers.Main){saved()}
        notices.send(if(enabled)"用户面具已启用，从这个角色的下一次请求生效。"else"用户面具已保存为关闭，新请求不再注入；已有聊天历史保持不变。")
    }
    fun openGuide(){_state.update {it.copy(guideOpen=true)}}
    fun closeGuide(permanent:Boolean=false){
        if(permanent)guidePreferences.edit().putBoolean("onboarding-finished",true).apply()
        _state.update {it.copy(guideOpen=false,guidePending=if(permanent)false else it.guidePending)}
    }
    fun previewDiagnostic(code:String="NONE") {
        val current=state.value
        _state.update {it.copy(diagnostic=SupportDiagnostics.create(code,System.currentTimeMillis(),com.ai.assistance.operit.BuildConfig.VERSION_NAME,
            android.os.Build.VERSION.SDK_INT,current.configs.size,current.roles.size,current.inbox.size,current.busyChat!=null,current.configs.map {config->config.provider}))}
    }
    fun relationships(scope:RoleAccessScope):Flow<RelationshipData> = flow {
        privacy.permit();repository.requireRole(scope)
        emitAll(combine(db.relationships().observe(scope.roleId,scope.spaceId),db.dao().chats(scope.roleId,scope.spaceId)){entries,chats->RelationshipData(entries,chats)})
    }.catch {e->if(e is CancellationException)throw e else emit(RelationshipData(emptyList(),emptyList(),errorText(if(e is ChatFailure)e.code else "STORAGE")))}
    private fun relationshipAction(block:suspend ()->Unit)=action {
        _state.update {it.copy(relationshipError=null)}
        try {block()}catch(e:Exception) {
            if(e is CancellationException)throw e
            val message=when(e){is ChatFailure->errorText(e.code);is IllegalArgumentException->"输入不完整或格式有误，请检查填写内容。";else->errorText("STORAGE")}
            _state.update {it.copy(relationshipError=message)}
            throw e
        }
    }
    fun saveRelationship(scope:RoleAccessScope,entry:RelationshipEntry?,chatId:String?,source:String,target:String,label:String,description:String,
        enabled:Boolean,priority:Int,saved:()->Unit)=relationshipAction {
        RelationshipRepository(db).save(scope,entry?.id,entry?.version,chatId,source,target,label,description,enabled,priority)
        withContext(Dispatchers.Main){saved()}
        notices.send("关系已保存，从下一次请求生效。")
    }
    fun deleteRelationship(scope:RoleAccessScope,entry:RelationshipEntry)=relationshipAction {
        RelationshipRepository(db).delete(scope,entry.id,entry.version)
        notices.send("关系已删除。")
    }
    fun setConversationMode(role:RoleEntity,mode:String,saved:()->Unit)=relationshipAction {
        if(mode=="raw")ProactiveRuntime.cancel(role.scope())
        repository.setConversationMode(role.scope(),role.version,mode)
        withContext(Dispatchers.Main){saved()}
        notices.send(if(mode=="raw")"已启用原味模型并关闭该角色的主动消息，从下一次发送或重试生效。"else"已恢复角色模式，从下一次发送或重试使用已保存的角色设定。")
    }
    fun previewRelationships(scope:RoleAccessScope,chatId:String,text:String,ready:(RequestContext.Resolved)->Unit)=relationshipAction {
        val permit=privacy.permit()
        val resolved=db.withTransaction {
            val role=repository.requireRole(scope);val chat=repository.requireChat(scope,chatId)
            RequestContext.resolve(scope,role,chat,db.dao().history(scope.roleId,scope.spaceId,chatId),text,
                db.worldbook().read(scope.roleId,scope.spaceId),db.relationships().read(scope.roleId,scope.spaceId),
                memories=db.memories().read(scope.roleId,scope.spaceId))
        }
        privacy.requirePermit(permit)
        withContext(Dispatchers.Main){ready(resolved)}
    }
    fun memories(scope:RoleAccessScope):Flow<MemoryData> = flow {
        privacy.permit();repository.requireRole(scope)
        emitAll(combine(db.memories().observe(scope.roleId,scope.spaceId),db.dao().chats(scope.roleId,scope.spaceId)){entries,chats->MemoryData(entries,chats)})
    }.catch {e->if(e is CancellationException)throw e else emit(MemoryData(emptyList(),emptyList(),errorText(if(e is ChatFailure)e.code else "STORAGE")))}
    private fun memoryAction(block:suspend ()->Unit)=action {
        _state.update {it.copy(memoryError=null)}
        try {block()}catch(e:Exception) {
            if(e is CancellationException)throw e
            val message=when(e){is ChatFailure->errorText(e.code);is IllegalArgumentException->errorText("INPUT");else->errorText("STORAGE")}
            _state.update {it.copy(memoryError=message)}
            throw e
        }
    }
    fun saveMemory(scope:RoleAccessScope,entry:MemoryEntry?,kind:String,title:String,content:String,enabled:Boolean,priority:Int,
        recallMode:String=entry?.recallMode?:"auto",expiresAt:Long?=entry?.expiresAt,conflictPolicy:String="reject",conflicts:List<MemoryConflict> = emptyList(),saved:()->Unit)=memoryAction {
        MemoryRepository(db).save(scope,entry?.id,entry?.version,kind,title,content,enabled,priority,recallMode,expiresAt,conflictPolicy,conflicts.map {it.id}.toSet())
        withContext(Dispatchers.Main){saved()}
        notices.send("记忆已保存，从下一次发送或重试生效。")
    }
    fun deleteMemory(scope:RoleAccessScope,entry:MemoryEntry)=memoryAction {
        MemoryRepository(db).delete(scope,entry)
        notices.send("记忆已删除，已有聊天内容保持不变。")
    }
    fun forgetMemory(scope:RoleAccessScope,entry:MemoryEntry)=memoryAction {
        MemoryRepository(db).forget(scope,entry)
        notices.send("已暂时忘记这条内容。你可以在长期记忆中恢复。")
    }
    fun restoreMemory(scope:RoleAccessScope,entry:MemoryEntry)=memoryAction {
        MemoryRepository(db).restore(scope,entry)
        notices.send("记忆已恢复，从下一次发送或重试生效。")
    }
    fun previewMemoryExtraction(scope:RoleAccessScope,assistant:MessageEntity)=action {
        if(state.value.busyChat!=null||imageGenerationJob!=null)throw ChatFailure("MEMORY_EXTRACTION_BUSY")
        val frozen=db.withTransaction {
            val role=repository.requireRole(scope)
            val chat=repository.requireChat(scope,assistant.chatId)
            val current=db.dao().message(scope.roleId,scope.spaceId,chat.id,assistant.id)?:throw ChatFailure("MEMORY_SOURCE_CHANGED")
            if(current.kind!="assistant"||current.state!=MessageState.COMPLETE||current.content.isBlank())throw ChatFailure("MEMORY_SOURCE_CHANGED")
            val user=db.dao().history(scope.roleId,scope.spaceId,chat.id).singleOrNull {it.requestId==current.requestId&&it.kind=="user"}
                ?:throw ChatFailure("MEMORY_SOURCE_CHANGED")
            if(user.state!=MessageState.COMPLETE||user.content.isBlank())throw ChatFailure("MEMORY_SOURCE_CHANGED")
            if(user.content.length+current.content.length>MemoryExtractions.SOURCE_LIMIT)throw ChatFailure("MEMORY_SOURCE_LIMIT")
            val config=db.dao().config(chat.configId)?:throw ChatFailure("MEMORY_SOURCE_CHANGED")
            FrozenMemoryExtraction(role=role.copy(),config=config.copy(),source=MemoryExtractionSource(scope,chat.id,current.requestId,user.id,current.id,user.content,current.content,current.createdAt))
        }
        _state.update {it.copy(memoryExtraction=MemoryExtractionUi("CONFIRM",frozen))}
    }
    fun generateMemoryCandidates()=action {
        if(state.value.busyChat!=null||imageGenerationJob!=null)throw ChatFailure("MEMORY_EXTRACTION_BUSY")
        val ui=state.value.memoryExtraction?.takeIf {it.stage=="CONFIRM"}?:throw ChatFailure("MEMORY_SOURCE_CHANGED")
        val frozen=ui.frozen
        db.withTransaction {
            val role=repository.requireRole(frozen.source.scope)
            val chat=repository.requireChat(frozen.source.scope,frozen.source.chatId)
            val config=db.dao().config(chat.configId)?:throw ChatFailure("MEMORY_SOURCE_CHANGED")
            val user=db.dao().message(frozen.source.scope.roleId,frozen.source.scope.spaceId,chat.id,frozen.source.userMessageId)
            val assistant=db.dao().message(frozen.source.scope.roleId,frozen.source.scope.spaceId,chat.id,frozen.source.assistantMessageId)
            if(role!=frozen.role||config!=frozen.config||user?.requestId!=frozen.source.requestId||assistant?.requestId!=frozen.source.requestId||
                user.kind!="user"||assistant.kind!="assistant"||user.state!=MessageState.COMPLETE||assistant.state!=MessageState.COMPLETE||
                user.content!=frozen.source.userText||assistant.content!=frozen.source.assistantText||assistant.createdAt!=frozen.source.messageAt)throw ChatFailure("MEMORY_SOURCE_CHANGED")
        }
        _state.update {current->if(current.memoryExtraction?.frozen?.id==frozen.id)current.copy(memoryExtraction=ui.copy(stage="GENERATING"))else current}
        try {
            val result=withModelGeneration("memory:${frozen.id}","MEMORY_EXTRACTION_BUSY") {
                val key=vault.read(frozen.config.credentialRef,frozen.config.endpoint)
                memoryCandidateService.extract(frozen,key)
            }
            val completedAt=System.currentTimeMillis().coerceAtLeast(frozen.source.messageAt)
            _state.update {current->if(current.memoryExtraction?.frozen?.id==frozen.id)current.copy(memoryExtraction=ui.copy(stage="PREVIEW",candidates=result.candidates,completedAt=completedAt))else current}
        } finally {
            _state.update {current->if(current.memoryExtraction?.frozen?.id==frozen.id&&current.memoryExtraction.stage=="GENERATING")current.copy(memoryExtraction=ui)else current}
        }
    }
    fun cancelMemoryExtraction() {
        val ui=state.value.memoryExtraction?:return
        if(ui.stage!="GENERATING")return
        actionJob?.cancel()
        _state.update {current->if(current.memoryExtraction?.frozen?.id==ui.frozen.id)current.copy(memoryExtraction=ui.copy(stage="CONFIRM",candidates=emptyList(),completedAt=null))else current}
    }
    fun dismissMemoryExtraction() {
        if(state.value.memoryExtraction?.stage=="GENERATING")return
        _state.update {it.copy(memoryExtraction=null)}
    }
    fun confirmMemoryCandidates(candidates:List<MemoryCandidate>)=memoryAction {
        val ui=state.value.memoryExtraction?.takeIf {it.stage=="PREVIEW"}?:throw ChatFailure("MEMORY_SOURCE_CHANGED")
        MemoryRepository(db).saveExtracted(ui.frozen,candidates,ui.completedAt?:throw ChatFailure("MEMORY_SOURCE_CHANGED"))
        _state.update {current->if(current.memoryExtraction?.frozen?.id==ui.frozen.id)current.copy(memoryExtraction=null)else current}
        notices.send("已保存 ${candidates.size} 条确认记忆，从下一次发送或重试生效。")
    }
    fun previewMemories(scope:RoleAccessScope,chatId:String,text:String,ready:(RequestContext.Resolved)->Unit)=memoryAction {
        val permit=privacy.permit()
        val resolved=db.withTransaction {
            val role=repository.requireRole(scope);val chat=repository.requireChat(scope,chatId)
            RequestContext.resolve(scope,role,chat,db.dao().history(scope.roleId,scope.spaceId,chatId),text,
                db.worldbook().read(scope.roleId,scope.spaceId),db.relationships().read(scope.roleId,scope.spaceId),
                memories=db.memories().read(scope.roleId,scope.spaceId))
        }
        privacy.requirePermit(permit)
        withContext(Dispatchers.Main){ready(resolved)}
    }
    fun diaries(scope:RoleAccessScope):Flow<DiaryData> = flow {
        privacy.permit();repository.requireRole(scope)
        emitAll(db.diaries().observe(scope.roleId,scope.spaceId).map {DiaryData(it)})
    }.catch {e->if(e is CancellationException)throw e else emit(DiaryData(emptyList(),errorText(if(e is ChatFailure)e.code else "STORAGE")))}
    private fun diaryAction(block:suspend ()->Unit)=action {
        _state.update {it.copy(diaryError=null)}
        try {block()}catch(e:Exception) {
            if(e is CancellationException)throw e
            val message=when(e){is ChatFailure->errorText(e.code);is IllegalArgumentException->errorText("INPUT");else->errorText("STORAGE")}
            _state.update {it.copy(diaryError=message)}
            throw e
        }
    }
    fun saveDiary(scope:RoleAccessScope,entry:DiaryEntry?,notebook:String,title:String,content:String,mood:String,occurredAt:Long,saved:()->Unit)=diaryAction {
        DiaryRepository(db).saveManual(scope,entry,notebook,title,content,mood,occurredAt)
        withContext(Dispatchers.Main){saved()};notices.send("记录已保存。普通聊天不会自动读取这篇内容。")
    }
    fun deleteDiary(scope:RoleAccessScope,entry:DiaryEntry)=diaryAction {
        DiaryRepository(db).delete(scope,entry);notices.send("记录已永久删除。")
    }
    fun previewDiaryGeneration(role:RoleEntity,notebook:String,configId:String,prompt:String)=diaryAction {
        if(state.value.busyChat!=null||imageGenerationJob!=null||state.value.memoryExtraction?.stage=="GENERATING"||state.value.diaryGeneration?.stage=="GENERATING")throw ChatFailure("DIARY_GENERATION_BUSY")
        Diaries.validatePrompt(prompt);val current=repository.requireRole(role.scope());val config=db.dao().config(configId)?:throw ChatFailure("DIARY_SOURCE_CHANGED")
        if(current!=role)throw ChatFailure("DIARY_SOURCE_CHANGED")
        val frozen=FrozenDiaryGeneration(role=current.copy(),config=config.copy(),notebook=notebook,prompt=prompt)
        DiaryGenerations.validateFrozen(frozen)
        _state.update {it.copy(diaryGeneration=DiaryGenerationUi("CONFIRM",frozen),diaryError=null)}
    }
    fun generateDiaryDraft()=diaryAction {
        if(state.value.busyChat!=null||imageGenerationJob!=null||state.value.memoryExtraction?.stage=="GENERATING")throw ChatFailure("DIARY_GENERATION_BUSY")
        val ui=state.value.diaryGeneration?.takeIf {it.stage=="CONFIRM"}?:throw ChatFailure("DIARY_SOURCE_CHANGED")
        val frozen=ui.frozen
        db.withTransaction {
            val role=repository.requireRole(frozen.role.scope());val config=db.dao().config(frozen.config.id)?:throw ChatFailure("DIARY_SOURCE_CHANGED")
            if(role!=frozen.role||config!=frozen.config)throw ChatFailure("DIARY_SOURCE_CHANGED")
        }
        _state.update {current->if(current.diaryGeneration?.frozen?.id==frozen.id)current.copy(diaryGeneration=ui.copy(stage="GENERATING"))else current}
        try {
            val result=withModelGeneration("diary:${frozen.id}","DIARY_GENERATION_BUSY") {
                val key=vault.read(frozen.config.credentialRef,frozen.config.endpoint)
                diaryGenerationService.generate(frozen,key)
            }
            _state.update {current->if(current.diaryGeneration?.frozen?.id==frozen.id)current.copy(diaryGeneration=ui.copy(stage="PREVIEW",draft=result.draft))else current}
        } finally {
            _state.update {current->if(current.diaryGeneration?.frozen?.id==frozen.id&&current.diaryGeneration.stage=="GENERATING")current.copy(diaryGeneration=ui)else current}
        }
    }
    fun cancelDiaryGeneration() {
        val ui=state.value.diaryGeneration?:return;if(ui.stage!="GENERATING")return
        actionJob?.cancel();_state.update {current->if(current.diaryGeneration?.frozen?.id==ui.frozen.id)current.copy(diaryGeneration=ui.copy(stage="CONFIRM",draft=null))else current}
    }
    fun dismissDiaryGeneration() {
        if(state.value.diaryGeneration?.stage=="GENERATING")return
        _state.update {it.copy(diaryGeneration=null,diaryError=null)}
    }
    fun confirmDiaryDraft(draft:DiaryDraft,occurredAt:Long)=diaryAction {
        val ui=state.value.diaryGeneration?.takeIf {it.stage=="PREVIEW"}?:throw ChatFailure("DIARY_SOURCE_CHANGED")
        DiaryRepository(db).saveGenerated(ui.frozen,draft,occurredAt)
        _state.update {current->if(current.diaryGeneration?.frozen?.id==ui.frozen.id)current.copy(diaryGeneration=null)else current}
        notices.send("模型草稿已由你确认并保存，普通聊天不会自动读取。")
    }
    fun previewDiaryExport(scope:RoleAccessScope,entry:DiaryEntry)=diaryAction {
        val role=repository.requireRole(scope);val current=db.diaries().entry(scope.roleId,scope.spaceId,entry.id)?:throw ChatFailure("DIARY_CHANGED")
        if(current.version!=entry.version)throw ChatFailure("DIARY_CHANGED")
        _state.update {it.copy(diaryExportPreview=DiaryExports.prepare(scope,role,current))}
    }
    fun dismissDiaryExport(){if(!state.value.working)_state.update {it.copy(diaryExportPreview=null)}}
    fun approveDiaryExport(baseName:String,launch:(String)->Unit) {
        privacy.permit();val preview=state.value.diaryExportPreview?:return;if(state.value.working)return
        val file=try {DiaryExports.freeze(preview,baseName)}catch(e:ChatFailure){notices.trySend(errorText(e.code));return}
        pendingDiaryExport=file;_state.update {it.copy(diaryExportPreview=null)};launch(file.fileName)
    }
    fun writeDiaryExport(uri:Uri?) {
        val file=pendingDiaryExport;pendingDiaryExport=null;if(uri==null)return
        if(file==null){notices.trySend("日记导出已失效，请重新预览后保存。");return}
        diaryAction {
            val permit=privacy.permit();val preview=file.preview
            val current=db.diaries().entry(preview.scope.roleId,preview.scope.spaceId,preview.entry.id)?:throw ChatFailure("DIARY_CHANGED")
            DiaryExports.verifySource(file,current);val resolver=getApplication<Application>().contentResolver
            DiaryExportFiles.writeVerified(file,{privacy.requirePermit(permit);resolver.openOutputStream(uri,"wt")},{privacy.requirePermit(permit);resolver.openInputStream(uri)})
            privacy.requirePermit(permit);notices.send("日记已保存并核对 · Markdown · ${file.bytes().size} 字节。")
        }
    }
    fun environment(scope:RoleAccessScope):Flow<EnvironmentData> = flow {
        privacy.permit();repository.requireRole(scope)
        emitAll(db.environment().observe(scope.roleId,scope.spaceId).map {EnvironmentData(it)})
    }.catch {e->if(e is CancellationException)throw e else emit(EnvironmentData(null,errorText(if(e is ChatFailure)e.code else "STORAGE")))}
    private fun environmentAction(block:suspend ()->Unit)=action {
        _state.update {it.copy(environmentError=null)}
        try {block()}catch(e:Exception) {
            if(e is CancellationException)throw e
            val message=when(e){is ChatFailure->errorText(e.code);is IllegalArgumentException->errorText("WEATHER_CITY");else->errorText("STORAGE")}
            _state.update {it.copy(environmentError=message)}
            throw e
        }
    }
    fun setEnvironmentToggles(scope:RoleAccessScope,time:Boolean,weather:Boolean)=environmentAction {
        EnvironmentRepository(db).setToggles(scope,time,weather)
    }
    fun clearWeather(scope:RoleAccessScope)=environmentAction {
        EnvironmentRepository(db).clearWeather(scope);notices.send("已清除这个角色的天气城市和快照。")
    }
    fun previewWeatherSearch(scope:RoleAccessScope,roleName:String,city:String) {
        try {privacy.permit();Environments.validateCity(city)}catch(_:Exception){notices.trySend(errorText("WEATHER_CITY"));return}
        if(state.value.working)return
        _state.update {it.copy(weatherLookup=WeatherLookupUi("SEARCH_CONFIRM",scope,roleName,city),environmentError=null)}
    }
    fun searchWeather()=environmentAction {
        val ui=state.value.weatherLookup?.takeIf {it.stage=="SEARCH_CONFIRM"}?:throw ChatFailure("WEATHER_CHANGED")
        repository.requireRole(ui.scope);Environments.validateCity(ui.city)
        _state.update {current->if(current.weatherLookup==ui)current.copy(weatherLookup=ui.copy(stage="SEARCHING"))else current}
        try {
            val candidates=weatherService.search(ui.city);currentCoroutineContext().ensureActive()
            if(candidates.isEmpty())throw ChatFailure("WEATHER_EMPTY")
            _state.update {current->if(current.weatherLookup?.scope==ui.scope&&current.weatherLookup.city==ui.city&&current.weatherLookup.stage=="SEARCHING")current.copy(weatherLookup=ui.copy(stage="CANDIDATES",candidates=candidates))else current}
        } finally {
            _state.update {current->if(current.weatherLookup?.scope==ui.scope&&current.weatherLookup.city==ui.city&&current.weatherLookup.stage=="SEARCHING")current.copy(weatherLookup=ui)else current}
        }
    }
    fun selectWeatherCandidate(candidate:WeatherCandidate) {
        val ui=state.value.weatherLookup?.takeIf {it.stage=="CANDIDATES"&&candidate in it.candidates}?:return
        _state.update {current->if(current.weatherLookup==ui)current.copy(weatherLookup=ui.copy(stage="FORECAST_CONFIRM",selected=candidate),environmentError=null)else current}
    }
    fun previewWeatherRefresh(scope:RoleAccessScope,roleName:String,profile:EnvironmentProfile) {
        val candidate=try {if(profile.scope()!=scope)throw ChatFailure("ACCESS");Environments.validateStored(profile);requireNotNull(profile.candidate())}catch(_:Exception){notices.trySend(errorText("WEATHER_CHANGED"));return}
        if(state.value.working)return
        _state.update {it.copy(weatherLookup=WeatherLookupUi("FORECAST_CONFIRM",scope,roleName,candidate.name,listOf(candidate),candidate),environmentError=null)}
    }
    fun fetchWeather()=environmentAction {
        val ui=state.value.weatherLookup?.takeIf {it.stage=="FORECAST_CONFIRM"}?:throw ChatFailure("WEATHER_CHANGED")
        val candidate=ui.selected?:throw ChatFailure("WEATHER_CHANGED");repository.requireRole(ui.scope)
        _state.update {current->if(current.weatherLookup==ui)current.copy(weatherLookup=ui.copy(stage="FETCHING"))else current}
        try {
            val snapshot=weatherService.forecast(candidate);currentCoroutineContext().ensureActive()
            val current=state.value.weatherLookup
            if(current?.scope!=ui.scope||current.selected!=candidate||current.stage!="FETCHING")throw CancellationException("Weather request is no longer active")
            EnvironmentRepository(db).saveWeather(ui.scope,candidate,snapshot);currentCoroutineContext().ensureActive()
            _state.update {current->if(current.weatherLookup?.scope==ui.scope&&current.weatherLookup.selected==candidate)current.copy(weatherLookup=null,environmentError=null)else current}
            notices.send("天气已手工更新。只有开启感知后，最近六小时内的快照才会加入新请求。")
        } finally {
            _state.update {current->if(current.weatherLookup?.scope==ui.scope&&current.weatherLookup.selected==candidate&&current.weatherLookup.stage=="FETCHING")current.copy(weatherLookup=ui)else current}
        }
    }
    fun cancelWeatherLookup() {
        val ui=state.value.weatherLookup?:return
        if(ui.stage !in setOf("SEARCHING","FETCHING"))return
        actionJob?.cancel();_state.update {current->if(current.weatherLookup==ui)current.copy(weatherLookup=ui.copy(stage=if(ui.selected==null)"SEARCH_CONFIRM" else "FORECAST_CONFIRM"))else current}
    }
    fun dismissWeatherLookup(){if(!state.value.working)_state.update {it.copy(weatherLookup=null,environmentError=null)}}
    fun planner(scope:RoleAccessScope):Flow<PlannerData> = flow {
        privacy.permit();repository.requireRole(scope)
        emitAll(combine(db.planner().observePlanner(scope.roleId,scope.spaceId),db.planner().observeCycles(scope.roleId,scope.spaceId)){entries,cycles->
            // Validate before projecting calendar dates or sharing; malformed storage must surface as a read error.
            entries.forEach(PlannerRecords::validatePlanner);cycles.forEach(PlannerRecords::validateCycle)
            PlannerData(entries,cycles)
        })
    }.catch {e->if(e is CancellationException)throw e else emit(PlannerData(emptyList(),emptyList(),errorText(if(e is ChatFailure)e.code else "STORAGE")))}
    private fun plannerAction(block:suspend ()->Unit)=action {
        _state.update {it.copy(plannerError=null)}
        try {block()}catch(e:Exception) {
            if(e is CancellationException)throw e
            val message=when(e){is ChatFailure->errorText(e.code);is IllegalArgumentException->errorText("PLANNER_INPUT");else->errorText("STORAGE")}
            _state.update {it.copy(plannerError=message)};throw e
        }
    }
    fun savePlanner(scope:RoleAccessScope,existing:PlannerEntry?,kind:String,title:String,note:String,startsAt:Long,endsAt:Long?,zoneId:String,completedAt:Long?,reminderMinutesBefore:Long?,shareWithRole:Boolean,done:()->Unit)=plannerAction {
        val saved=PlannerRepository(db).savePlanner(scope,existing,kind,title,note,startsAt,endsAt,zoneId,completedAt,reminderMinutesBefore,shareWithRole)
        ReminderScheduler.schedule(getApplication(),saved);withContext(Dispatchers.Main){done()}
    }
    fun setPlannerCompleted(scope:RoleAccessScope,entry:PlannerEntry,completed:Boolean)=plannerAction {
        val saved=PlannerRepository(db).savePlanner(scope,entry,entry.kind,entry.title,entry.note,entry.startsAt,entry.endsAt,entry.zoneId,if(completed)System.currentTimeMillis() else null,entry.reminderMinutesBefore,entry.shareWithRole)
        ReminderScheduler.schedule(getApplication(),saved)
    }
    fun deletePlanner(scope:RoleAccessScope,entry:PlannerEntry)=plannerAction {
        PlannerRepository(db).deletePlanner(scope,entry.id);ReminderScheduler.cancel(getApplication(),entry.id)
    }
    fun saveCycle(scope:RoleAccessScope,existing:CycleRecord?,startedOn:Long,endedOn:Long?,flowLevel:String,note:String,shareWithRole:Boolean,done:()->Unit)=plannerAction {
        PlannerRepository(db).saveCycle(scope,existing,startedOn,endedOn,flowLevel,note,shareWithRole);withContext(Dispatchers.Main){done()}
    }
    fun deleteCycle(scope:RoleAccessScope,record:CycleRecord)=plannerAction {PlannerRepository(db).deleteCycle(scope,record.id)}
    fun worldbook(scope:RoleAccessScope):Flow<WorldbookData> = flow {
        privacy.permit();repository.requireRole(scope)
        emitAll(combine(db.worldbook().observe(scope.roleId,scope.spaceId),db.dao().chats(scope.roleId,scope.spaceId)){entries,chats->WorldbookData(entries,chats)})
    }.catch {e->if(e is CancellationException)throw e else emit(WorldbookData(emptyList(),emptyList(),errorText(if(e is ChatFailure)e.code else "STORAGE")))}
    private fun worldbookAction(block:suspend ()->Unit)=action {
        _state.update {it.copy(worldbookError=null)}
        try {block()}catch(e:Exception) {
            if(e is CancellationException)throw e
            val message=when(e){is ChatFailure->errorText(e.code);is IllegalArgumentException->"输入不完整或格式有误，请检查填写内容。";else->errorText("STORAGE")}
            _state.update {it.copy(worldbookError=message)}
            throw e
        }
    }
    fun saveWorldbook(scope:RoleAccessScope,entry:WorldbookEntry?,chatId:String?,title:String,content:String,trigger:String,keys:String,enabled:Boolean,priority:Int,saved:()->Unit)=worldbookAction {
        WorldbookRepository(db).save(scope,entry?.id,entry?.version,chatId,title,content,trigger,keys,enabled,priority)
        withContext(Dispatchers.Main){saved()}
        notices.send("世界书已保存，从下一次请求生效。")
    }
    fun deleteWorldbook(scope:RoleAccessScope,entry:WorldbookEntry)=worldbookAction {
        WorldbookRepository(db).delete(scope,entry.id,entry.version)
        notices.send("世界书条目已删除。")
    }
    fun previewWorldbook(scope:RoleAccessScope,chatId:String,text:String,ready:(RequestContext.Resolved)->Unit)=worldbookAction {
        val permit=privacy.permit()
        val resolved=db.withTransaction {
            val role=repository.requireRole(scope);val chat=repository.requireChat(scope,chatId)
            RequestContext.resolve(scope,role,chat,db.dao().history(scope.roleId,scope.spaceId,chatId),text,db.worldbook().read(scope.roleId,scope.spaceId),db.relationships().read(scope.roleId,scope.spaceId),
                memories=db.memories().read(scope.roleId,scope.spaceId))
        }
        privacy.requirePermit(permit)
        withContext(Dispatchers.Main){ready(resolved)}
    }
    private var worldbookImportTarget:RoleAccessScope?=null
    private var worldbookExportBytes:ByteArray?=null
    fun beginWorldbookImport(scope:RoleAccessScope) {privacy.permit();worldbookImportTarget=scope}
    fun readWorldbookTemplate(uri:Uri?) {
        val target=worldbookImportTarget;worldbookImportTarget=null
        if(uri==null)return
        if(target==null){notices.trySend("导入目标已失效，请重新选择世界书文件。");return}
        worldbookAction {
            val permit=privacy.permit();val role=repository.requireRole(target)
            val preview=try {getApplication<Application>().contentResolver.openInputStream(uri)?.use(WorldbookTemplateCodec::read)?:throw ChatFailure("FILE")}
                catch(e:ChatFailure){throw e}catch(_:Exception){throw ChatFailure("FILE")}
            privacy.requirePermit(permit)
            _state.update {it.copy(worldbookFile=WorldbookFilePreview(target,role.name,preview.template,preview.ignoredFields,false))}
        }
    }
    fun previewWorldbookExport(scope:RoleAccessScope,ids:List<String>)=worldbookAction {
        val preview=db.withTransaction {
            val role=repository.requireRole(scope);val rows=db.worldbook().read(scope.roleId,scope.spaceId)
            if(ids.isEmpty()||ids.distinct().size!=ids.size||ids.any {id->rows.none {it.id==id}})throw ChatFailure("ACCESS")
            val template=WorldbookTemplateCodec.fromEntries(rows.filter {it.id in ids})
            WorldbookTemplateCodec.encode(template)
            WorldbookFilePreview(scope,role.name,template,0,true)
        }
        _state.update {it.copy(worldbookFile=preview)}
    }
    fun dismissWorldbookFile(){if(!state.value.working)_state.update {it.copy(worldbookFile=null)}}
    fun importWorldbookTemplate() {
        val preview=state.value.worldbookFile?:return
        if(preview.exporting)return
        worldbookAction {
            WorldbookRepository(db).import(preview.scope,preview.template)
            _state.update {if(it.worldbookFile===preview)it.copy(worldbookFile=null)else it}
            notices.send("已导入 ${preview.template.entries.size} 个新条目，全部关闭。请检查后逐条启用。")
        }
    }
    fun approveWorldbookExport(launch:()->Unit) {
        privacy.permit();val preview=state.value.worldbookFile?:return
        if(!preview.exporting||state.value.working)return
        worldbookExportBytes=WorldbookTemplateCodec.encode(preview.template).toByteArray(Charsets.UTF_8)
        _state.update {it.copy(worldbookFile=null)}
        launch()
    }
    fun writeWorldbookTemplate(uri:Uri?) {
        val bytes=worldbookExportBytes;worldbookExportBytes=null
        if(uri==null)return
        if(bytes==null){notices.trySend("导出内容已失效，请重新预览后保存。");return}
        worldbookAction {
            try {
                val permit=privacy.permit();val resolver=getApplication<Application>().contentResolver
                resolver.openOutputStream(uri,"wt")?.use {privacy.requirePermit(permit);it.write(bytes)}?:throw ChatFailure("FILE")
                val saved=resolver.openInputStream(uri)?.use {WorldbookTemplateCodec.read(it)}?:throw ChatFailure("FILE")
                if(saved.template!=WorldbookTemplateCodec.decode(bytes).template||saved.ignoredFields!=0)throw ChatFailure("FILE")
                privacy.requirePermit(permit);notices.send("世界书模板已保存并回读核对。")
            }catch(e:CancellationException){throw e}catch(e:ChatFailure){throw e}catch(_:Exception){throw ChatFailure("FILE")}
        }
    }
    fun roleChatCount(scope: RoleAccessScope, ready: (Int)->Unit) = action {
        repository.requireRole(scope)
        val count=db.dao().chatCount(scope.roleId,scope.spaceId)
        withContext(Dispatchers.Main) { ready(count) }
    }
    fun deleteRole(scope: RoleAccessScope, done: ()->Unit) = action {
        ProactiveRuntime.cancel(scope)
        synchronized(speechLock){musicJob}?.cancelAndJoin()
        if(activeVoiceConversation?.scope==scope)endVoiceConversation()
        cancelImageGenerationFor(scope,null)
        val reminderIds=db.planner().readPlanner(scope.roleId,scope.spaceId).map {it.id}
        playCoordinator.mutateRole(scope) {coordinator.mutateRoleAndGroups(scope,{group->db.groups().forRole(scope.roleId,scope.spaceId).any {it.value().scope==group}}) { repository.deleteRole(scope) }}
        musicFiles.prune()
        reminderIds.forEach {ReminderScheduler.cancel(getApplication(),it)}
        withContext(Dispatchers.Main) {
            if(state.value.scope==scope)selectRole(null)
            done()
        }
    }
    fun readTemplate(uri: Uri) = action {
        val preview=try {
            val input=getApplication<Application>().contentResolver.openInputStream(uri) ?: throw ChatFailure("FILE")
            input.use { RoleTemplateCodec.read(it) }
        } catch(e: ChatFailure) { throw e } catch(_: Exception) { throw ChatFailure("FILE") }
        _state.update { it.copy(importPreview=preview) }
    }
    fun readCompanionTheme(uri: Uri?) {
        if(uri==null)return
        action {
            val permit=privacy.permit()
            val theme=try {
                getApplication<Application>().contentResolver.openInputStream(uri)?.use(CompanionThemeCodec::read)
                    ?:throw ChatFailure("FILE")
            } catch(e:ChatFailure) {throw e} catch(_:Exception) {throw ChatFailure("TEMPLATE")}
            privacy.requirePermit(permit)
            _state.update {it.copy(companionThemeImport=theme)}
        }
    }
    fun dismissCompanionThemeImport() {
        if(!state.value.working)_state.update {it.copy(companionThemeImport=null)}
    }
    fun dismissImport() { _state.update { it.copy(importPreview=null) } }
    fun importTemplate(saved: (RoleEntity)->Unit) {
        val preview=state.value.importPreview ?: return
        dismissImport()
        action {
            val t=preview.template
            val role=repository.saveRole(null,t.name,t.prompt,t.openingMessage,null)
            withContext(Dispatchers.Main) { saved(role) }
            notices.send("已导入新角色，请在新对话中选择本机模型。")
        }
    }
    fun previewExport(scope: RoleAccessScope) = action {
        val role=repository.requireRole(scope)
        _state.update { it.copy(exportPreview=RoleTemplateCodec.fromRole(role)) }
    }
    fun dismissExport() { _state.update { it.copy(exportPreview=null) } }
    // Called before launching the system picker; only this in-memory approved template is exported.
    private var pendingExport: RoleTemplate?=null
    fun approveExport() { pendingExport=state.value.exportPreview; dismissExport() }
    fun writeTemplate(uri: Uri?) {
        val template=pendingExport
        pendingExport=null
        if(uri==null)return
        if(template==null) { notices.trySend("导出预览已失效，请重新预览并保存模板。");return }
        action {
            try {
                val output=getApplication<Application>().contentResolver.openOutputStream(uri,"wt") ?: throw ChatFailure("FILE")
                output.use { it.write(RoleTemplateCodec.encode(template).toByteArray(Charsets.UTF_8)) }
                notices.send("角色模板已保存。")
            } catch(_: Exception) { throw ChatFailure("FILE") }
        }
    }
    fun proactive(scope:RoleAccessScope):Flow<ProactiveData> = combine(db.proactive().observeSetting(scope.roleId,scope.spaceId),db.dao().chats(scope.roleId,scope.spaceId)){setting,chats->ProactiveData(setting,chats)}
    fun setProactiveMaster(enabled:Boolean,dailyLimit:Int)=action {
        require(dailyLimit in 1..10)
        if(enabled&&!ProactiveScheduler.hasNotificationPermission(getApplication()))throw ChatFailure("PROACTIVE_DISABLED")
        if(enabled) {
            ProactiveScheduler.sync(getApplication(),true)
            proactivePreferences.set(true,dailyLimit)
        } else {
            proactivePreferences.set(false,dailyLimit)
            ProactiveScheduler.sync(getApplication(),false)
        }
        _state.update {it.copy(proactiveMaster=enabled,proactiveDailyLimit=dailyLimit)}
        notices.send(if(enabled)"主动消息总开关已开启；系统仍可能因后台限制推迟。" else "主动消息总开关已关闭，正在运行的后台请求已停止。")
    }
    fun previewProactiveNotification()=action {ProactiveScheduler.notify(getApplication(),true);notices.send("已发送隐私通知预览。")}
    fun saveProactive(scope:RoleAccessScope,enabled:Boolean,chatId:String,configId:String,quietStart:Int,quietEnd:Int,intervalHours:Int,
        dailyLimit:Int,expectedVersion:Long?,done:()->Unit)=action {
        if(enabled&&!ProactiveScheduler.hasNotificationPermission(getApplication()))throw ChatFailure("PROACTIVE_DISABLED")
        if(!enabled)ProactiveRuntime.cancel(scope)
        ProactiveRepository(db).save(scope,enabled,chatId,configId,quietStart,quietEnd,intervalHours,dailyLimit,expectedVersion)
        if(proactivePreferences.enabled())ProactiveScheduler.sync(getApplication(),true)
        withContext(Dispatchers.Main){done()}
        notices.send(if(enabled)"这个角色的主动消息已开启。" else "这个角色的主动消息已关闭。")
    }
    suspend fun availableModels(configId:String?,endpoint:String,key:String):List<AvailableModel> {
        val permit=privacy.permit()
        val normalized=EndpointCompleter.completeEndpoint(endpoint)
        return withContext(Dispatchers.IO) {
            privacy.requirePermit(permit)
            val credential=if(key.isNotBlank()) {
                val value=key.trim()
                require(value.length<=8192&&value.none {it.isISOControl()})
                value
            }else {
                if(configId==null)throw ChatFailure("MODEL_LIST_KEY")
                val config=db.dao().config(configId)?:throw ChatFailure("MODEL_LIST_KEY")
                // A saved credential is bound to the confirmed chat address, including its API prefix.
                if(config.endpoint!=normalized)throw ChatFailure("MODEL_LIST_KEY")
                vault.read(config.credentialRef,normalized)
            }
            val models=ModelCatalogService().fetch(normalized,credential)
            privacy.requirePermit(permit)
            models
        }
    }

    fun saveConfig(id: String?, name:String, provider: String, endpoint: String, model: String, timeout: Int, key: String,
        visionEnabled:Boolean,onSaved: (String) -> Unit) = action {
        require(name.trim().isNotBlank()&&name.trim().length<=80&&name.none {it.isISOControl()})
        require(model.isNotBlank() && model.length<=200 && timeout in 15..300)
        require(provider in setOf("OpenAI 兼容","DeepSeek","硅基流动"))
        val normalized = EndpointCompleter.completeEndpoint(endpoint)
        val previous = if(id==null) null else db.dao().config(id)
        val ref = if(key.isNotBlank()) vault.save(key.trim(),normalized) else {
            require(previous != null && previous.endpoint==normalized)
            vault.read(previous.credentialRef,normalized)
            previous.credentialRef
        }
        val savedId=id ?: UUID.randomUUID().toString()
        db.dao().saveConfig(ModelConfigData(savedId,name.trim(),
            provider,normalized,model.trim(),timeout,ref,(previous?.version ?: 0)+1,visionEnabled))
        withContext(Dispatchers.Main) { onSaved(savedId) }
        notices.send("连接配置已保存。Key 仅用于已确认的地址。")
    }
    fun deleteModelConfig(config:ModelConfigData,onDeleted:()->Unit)=action {
        db.withTransaction {
            val saved=db.dao().config(config.id)?:throw ChatFailure("MODEL_CONFIG_CHANGED")
            if(saved.version!=config.version)throw ChatFailure("MODEL_CONFIG_CHANGED")
            // Keep referenced channels: deleting them would break chats and make their backups invalid.
            if(db.dao().configReferenceCount(config.id)>0)throw ChatFailure("MODEL_CONFIG_IN_USE")
            if(db.dao().deleteConfig(config.id,config.version)!=1)throw ChatFailure("MODEL_CONFIG_CHANGED")
        }
        val retainedRefs=buildSet {
            addAll(db.dao().configs().first().map {it.credentialRef})
            addAll(db.extensions().extensions().first().mapNotNull {it.credentialRef})
            addAll(db.dao().imageGenerationConfigs().first().map {it.credentialRef})
            addAll(db.dao().speechServiceConfigs().first().map {it.credentialRef})
        }
        if(config.credentialRef !in retainedRefs)vault.removeCredential(config.credentialRef)
        withContext(Dispatchers.Main){onDeleted()}
        notices.send("模型渠道已删除。")
    }
    fun testConnection(config: ModelConfigData) = action {
        if(state.value.testing) return@action
        _state.update { it.copy(testing=true) }
        try {
            val requestId=UUID.randomUUID().toString()
            withModelGeneration("connection:$requestId","MODEL_GENERATION_BUSY") {
                val key=vault.read(config.credentialRef,config.endpoint)
                val role=RoleEntity("test","测试","请简短回答。")
                val request=RequestSnapshot(requestId,"test","test",role,config.copy(),
                    listOf(PromptTurn("user","请只回复一个字：好")))
                service.sendMessage(request,key).collect { }
            }
            notices.send("连接成功，已收到完整文本响应。")
        } finally { _state.update { it.copy(testing=false) } }
    }
    fun saveImageGenerationConfig(id:String?,endpoint:String,model:String,timeout:Int,key:String,size:String,onSaved:(String)->Unit)=action {
        val normalized=try {ImageGenerationEndpoint.complete(endpoint)}catch(_:Exception){throw ChatFailure("IMAGE_GENERATION_CONFIG")}
        val previous=id?.let {db.dao().imageGenerationConfig(it)}
        val savedId=id?:UUID.randomUUID().toString()
        val candidate=ImageGenerationConfig(savedId,"生图 · ${model.trim()}",normalized,model.trim(),timeout,"pending",(previous?.version?:0)+1,size)
        try {ImageGenerations.validateConfig(candidate)}catch(_:IllegalArgumentException){throw ChatFailure("IMAGE_GENERATION_CONFIG")}
        val ref=if(key.isNotBlank())vault.save(key.trim(),normalized)else {
            if(previous==null||previous.endpoint!=normalized)throw ChatFailure("IMAGE_GENERATION_CONFIG")
            vault.read(previous.credentialRef,normalized);previous.credentialRef
        }
        db.dao().saveImageGenerationConfig(candidate.copy(credentialRef=ref))
        withContext(Dispatchers.Main){onSaved(savedId)}
        notices.send("生图服务已保存。Key 仅用于已确认的生图地址。")
    }
    fun saveSpeechServiceConfig(id:String?,kind:String,endpoint:String,model:String,voice:String,timeout:Int,key:String,onSaved:(String)->Unit)=action {
        val normalized=try {SpeechEndpoint.complete(endpoint,kind)}catch(_:Exception){throw ChatFailure("SPEECH_CONFIG")}
        val previous=id?.let {db.dao().speechServiceConfig(it)}
        val savedId=id?:UUID.randomUUID().toString()
        val candidate=SpeechServiceConfig(savedId,(if(kind==SpeechServices.TRANSCRIPTION)"语音识别" else "语音合成")+" · "+model.trim(),kind,normalized,model.trim(),if(kind==SpeechServices.SYNTHESIS)voice.trim() else "",timeout,"pending",(previous?.version?:0)+1)
        try {SpeechServices.validateConfig(candidate)}catch(_:IllegalArgumentException){throw ChatFailure("SPEECH_CONFIG")}
        val ref=if(key.isNotBlank())vault.save(key.trim(),normalized)else {
            if(previous==null||previous.endpoint!=normalized)throw ChatFailure("SPEECH_CONFIG")
            vault.read(previous.credentialRef,normalized);previous.credentialRef
        }
        db.dao().saveSpeechServiceConfig(candidate.copy(credentialRef=ref))
        withContext(Dispatchers.Main){onSaved(savedId)}
        notices.send("语音服务已保存。Key 只会发往已确认的语音地址。")
    }
    fun startVoiceRecording(scope:RoleAccessScope,chatId:String,input:CommunicationDevice,output:CommunicationDevice) {
        if(!communicationPairSupported(input,output,android.os.Build.VERSION.SDK_INT)){notices.trySend(errorText("AUDIO_ROUTE_PAIR"));return}
        val permit=try {privacy.permit()}catch(_:ChatFailure){notices.trySend("请先解锁应用。");return}
        if(state.value.scope!=scope||state.value.chatId!=chatId||!privacy.state.value.foreground){notices.trySend(errorText("ACCESS"));return}
        val job=synchronized(speechLock) {
            if(musicJob!=null||recordingJob!=null||speechJob!=null||voiceConversationJob!=null||activeVoiceConversation!=null||state.value.working){notices.trySend(errorText("SPEECH_BUSY"));return}
            state.value.recordedVoice?.pcm?.fill(0)
            voiceRecorder.prepare()
            _state.update {it.copy(recordedVoice=null,voiceRecording=true,voiceRecordingReady=false)}
            val created=viewModelScope.launch(Dispatchers.IO,start=CoroutineStart.LAZY) {
                var captured:RecordedVoice?=null
                var published=false
                try {
                    val result=communicationRoutes.withRoute(output,input) {checkRoute->
                        voiceRecorder.record(CommunicationAudioBinding(input,output,checkRoute)) {
                            privacy.withForegroundPermit(permit) {
                                _state.update {
                                    if(!it.voiceRecording)throw CancellationException("Capture ended")
                                    if(it.scope!=scope||it.chatId!=chatId)throw ChatFailure("ACCESS")
                                    it.copy(voiceRecordingReady=true)
                                }
                            }
                        }.also {captured=it}
                    }
                    val coroutine=currentCoroutineContext()
                    privacy.withForegroundPermit(permit) {
                        coroutine.ensureActive()
                        publishVoiceCapture(_state,scope,chatId,result)
                        published=true
                    }
                    notices.send(if(result.durationMs>=SpeechServices.MAX_RECORD_SECONDS*1000L)"已达到 60 秒录音上限，请核对后选择识别服务。" else "录音已停止，请核对时长和识别目的地。")
                } catch(e:CancellationException){throw e}
                  catch(e:ChatFailure){currentCoroutineContext().ensureActive();reportFailure(e.code)}
                  catch(_:Exception){currentCoroutineContext().ensureActive();reportFailure("MICROPHONE_UNAVAILABLE")}
                  finally {if(!published)captured?.pcm?.fill(0)}
            }
            recordingJob=created
            created.invokeOnCompletion {synchronized(speechLock){if(recordingJob===created){recordingJob=null;_state.update {it.copy(voiceRecording=false,voiceRecordingReady=false)}}}}
            created
        }
        job.start()
    }
    fun stopVoiceRecording(){voiceRecorder.stop()}
    fun microphonePermissionDenied(){notices.trySend(errorText("MICROPHONE_PERMISSION"))}
    fun cancelVoiceCapture() {
        val job=synchronized(speechLock){recordingJob}
        voiceRecorder.cancel();job?.cancel()
        discardVoiceCapture(_state)
    }
    fun dismissRecordedVoice(){state.value.recordedVoice?.pcm?.fill(0);_state.update {it.copy(recordedVoice=null)}}
    fun transcribeVoice(configId:String) {
        val permit=try {privacy.permit()}catch(_:ChatFailure){notices.trySend("请先解锁应用。");return}
        val recording=state.value.recordedVoice?:return
        val job=synchronized(speechLock) {
            if(musicJob!=null||recordingJob!=null||speechJob!=null||voiceConversationJob!=null||activeVoiceConversation!=null||state.value.busyChat!=null||state.value.generatingImageId!=null||state.value.working){notices.trySend(errorText("SPEECH_BUSY"));return}
            val created=viewModelScope.launch(Dispatchers.IO,start=CoroutineStart.LAZY) {
                try {
                    _state.update {it.copy(transcribingVoice=true)}
                    val config=db.dao().speechServiceConfig(configId)?.copy()?.also(SpeechServices::validateConfig)?:throw ChatFailure("SPEECH_CONFIG")
                    if(config.kind!=SpeechServices.TRANSCRIPTION)throw ChatFailure("SPEECH_CONFIG")
                    val text=withModelGeneration("transcription:${UUID.randomUUID()}","SPEECH_BUSY") {
                        val key=vault.read(config.credentialRef,config.endpoint)
                        speechService.transcribe(config,key,recording)
                    }
                    privacy.requirePermit(permit);currentCoroutineContext().ensureActive();draftInsertions.send(text)
                    recording.pcm.fill(0);_state.update {it.copy(recordedVoice=null)}
                    notices.send("识别文字已放入输入框，请编辑确认后再发送。")
                } catch(e:CancellationException){throw e}
                  catch(e:ChatFailure){reportFailure(e.code)}
                  catch(_:Exception){reportFailure("SPEECH_PROTOCOL")}
            }
            speechJob=created
            created.invokeOnCompletion {synchronized(speechLock){if(speechJob===created)speechJob=null;_state.update {it.copy(transcribingVoice=false)}}}
            created
        }
        job.start()
    }
    fun speakMessage(scope:RoleAccessScope,messageId:String,configId:String,output:CommunicationDevice) {
        val chatId=state.value.chatId?:return
        speakMessages(scope,chatId,listOf(messageId),configId,output)
    }
    fun speakMessages(scope:RoleAccessScope,chatId:String,messageIds:List<String>,configId:String,output:CommunicationDevice) {
        val permit=try {privacy.permit()}catch(_:ChatFailure){notices.trySend("请先解锁应用。");return}
        val ids=messageIds.distinct()
        if(ids.isEmpty()||state.value.scope!=scope||state.value.chatId!=chatId||!privacy.state.value.foreground){notices.trySend(errorText("ACCESS"));return}
        val job=synchronized(speechLock) {
            if(musicJob!=null||recordingJob!=null||speechJob!=null||voiceConversationJob!=null||activeVoiceConversation!=null||state.value.busyChat!=null||state.value.generatingImageId!=null||state.value.working){notices.trySend(errorText("SPEECH_BUSY"));return}
            val created=viewModelScope.launch(Dispatchers.IO,start=CoroutineStart.LAZY) {
                try {
                    val messages=ids.map {id->db.dao().message(scope.roleId,scope.spaceId,chatId,id)?:throw ChatFailure("ACCESS")}
                    if(messages.any {it.kind !in setOf("assistant","user")||it.state!=MessageState.COMPLETE||it.content.isBlank()})throw ChatFailure("ACCESS")
                    messages.forEach {SpeechServices.validateSpeechText(it.content)}
                    val config=db.dao().speechServiceConfig(configId)?.copy()?.also(SpeechServices::validateConfig)?:throw ChatFailure("SPEECH_CONFIG")
                    if(config.kind!=SpeechServices.SYNTHESIS)throw ChatFailure("SPEECH_CONFIG")
                    privacy.requirePermit(permit);currentCoroutineContext().ensureActive()
                    communicationRoutes.withOutputRoute(output) {checkRoute->
                    for(message in messages) {
                        privacy.requirePermit(permit);currentCoroutineContext().ensureActive()
                        _state.update {it.copy(speakingMessageId=message.id,singleSpeechStage=SingleSpeechStage.CONNECTING)}
                        _state.update {it.copy(singleSpeechStage=SingleSpeechStage.SYNTHESIZING)}
                        val pcm=withModelGeneration("synthesis:${UUID.randomUUID()}","SPEECH_BUSY") {
                            val key=vault.read(config.credentialRef,config.endpoint)
                            speechService.synthesize(config,key,message.content)
                        }
                        try {
                            privacy.requirePermit(permit);currentCoroutineContext().ensureActive()
                            _state.update {it.copy(singleSpeechStage=SingleSpeechStage.PREPARING)}
                            pcmPlayer.play(pcm,CommunicationPlaybackBinding(output,checkRoute)) {
                                privacy.requirePermit(permit)
                                _state.update {it.copy(singleSpeechStage=SingleSpeechStage.PLAYING)}
                            }
                        } finally {pcm.fill(0)}
                        }
                    }
                } catch(e:CancellationException){throw e}
                  catch(e:ChatFailure){currentCoroutineContext().ensureActive();reportFailure(e.code)}
                  catch(_:Exception){currentCoroutineContext().ensureActive();reportFailure("AUDIO_PLAYBACK")}
            }
            speechJob=created
            created.invokeOnCompletion {synchronized(speechLock){if(speechJob===created){speechJob=null;_state.update {it.copy(speakingMessageId=null,singleSpeechStage=null)}}}}
            created
        }
        job.start()
    }
    fun stopSpeech(){pcmPlayer.stop();synchronized(speechLock){speechJob}?.cancel()}
    private fun voiceConversationActive(id:String)=synchronized(speechLock){activeVoiceConversation?.id==id}
    private fun transitionVoiceConversation(id:String,event:VoiceConversationEvent,transcript:String?=null,reply:String?=null,errorCode:String?=null) {
        _state.update {current->
            val ui=current.voiceConversation
            if(ui?.id!=id)current else {
                val next=VoiceConversationMachine.transition(ui.stage,event)
                current.copy(voiceConversation=ui.copy(stage=next,
                    turn=ui.turn+if(event==VoiceConversationEvent.TURN_FINISHED)1 else 0,
                    transcript=when {event in setOf(VoiceConversationEvent.CAPTURED,VoiceConversationEvent.INTERRUPT,VoiceConversationEvent.RESUME)->"";transcript!=null->transcript;else->ui.transcript},
                    reply=when {event in setOf(VoiceConversationEvent.CAPTURED,VoiceConversationEvent.INTERRUPT,VoiceConversationEvent.RESUME)->"";reply!=null->reply;else->ui.reply},
                    errorCode=errorCode?:if(event in setOf(VoiceConversationEvent.INTERRUPT,VoiceConversationEvent.RESUME,VoiceConversationEvent.TURN_FINISHED))"" else ui.errorCode))
            }
        }
    }
    suspend fun voiceAudioDevices(includeInputs:Boolean=true):VoiceAudioDevices {
        val permit=privacy.permit()
        if(!privacy.state.value.foreground)throw ChatFailure("ACCESS")
        val devices=VoiceAudioDevices(if(includeInputs)communicationRoutes.inputs() else emptyList(),communicationRoutes.devices())
        privacy.requirePermit(permit);currentCoroutineContext().ensureActive()
        return devices
    }
    private fun startVoiceConversationLoop(session:ActiveVoiceConversation) {
        val created=viewModelScope.launch(Dispatchers.IO,start=CoroutineStart.LAZY) {
            try {
                communicationRoutes.withRoute(session.output,session.input,session.routeFailure) {requireRoute->
                val binding=CommunicationAudioBinding(session.input,session.output,requireRoute)
                while(voiceConversationActive(session.id)) {
                    privacy.requirePermit(session.privacyPermit)
                    currentCoroutineContext().ensureActive()
                    voiceRecorder.prepare()
                    var recording:RecordedVoice?=null
                    try {
                        recording=voiceRecorder.record(binding) {
                            privacy.requirePermit(session.privacyPermit)
                            if(!voiceConversationActive(session.id))throw CancellationException("Voice session ended")
                            transitionVoiceConversation(session.id,VoiceConversationEvent.CAPTURE_READY)
                        }
                        privacy.requirePermit(session.privacyPermit);currentCoroutineContext().ensureActive()
                        transitionVoiceConversation(session.id,VoiceConversationEvent.CAPTURED)
                        val text=withModelGeneration("voice-transcription:${session.id}:${state.value.voiceConversation?.turn}","SPEECH_BUSY") {
                            val key=vault.read(session.transcription.credentialRef,session.transcription.endpoint)
                            speechService.transcribe(session.transcription,key,requireNotNull(recording))
                        }
                        recording.pcm.fill(0);recording=null
                        privacy.requirePermit(session.privacyPermit);currentCoroutineContext().ensureActive()
                        transitionVoiceConversation(session.id,VoiceConversationEvent.TRANSCRIBED,transcript=text)
                        val response=coordinator.sendAndAwait(session.scope,session.chatId,text)
                        privacy.requirePermit(session.privacyPermit);currentCoroutineContext().ensureActive()
                        when(response.state) {
                            MessageState.COMPLETE->if(response.text.isBlank())throw ChatFailure("EMPTY")
                            MessageState.FAILED->throw ChatFailure(response.errorCode.ifBlank {"PROTOCOL"})
                            MessageState.CANCELLED->throw ChatFailure("INTERRUPTED")
                            else->throw ChatFailure("INTERRUPTED")
                        }
                        transitionVoiceConversation(session.id,VoiceConversationEvent.REPLY_READY,reply=response.text)
                        try {SpeechServices.validateSpeechText(response.text)}catch(_:IllegalArgumentException){throw ChatFailure("SPEECH_LIMIT")}
                        val pcm=withModelGeneration("voice-synthesis:${session.id}:${response.assistantId}","SPEECH_BUSY") {
                            val key=vault.read(session.synthesis.credentialRef,session.synthesis.endpoint)
                            speechService.synthesize(session.synthesis,key,response.text)
                        }
                        try {
                            privacy.requirePermit(session.privacyPermit);currentCoroutineContext().ensureActive()
                            transitionVoiceConversation(session.id,VoiceConversationEvent.AUDIO_READY)
                            pcmPlayer.play(pcm,CommunicationPlaybackBinding(session.output,requireRoute))
                        } finally {pcm.fill(0)}
                        privacy.requirePermit(session.privacyPermit);currentCoroutineContext().ensureActive()
                        transitionVoiceConversation(session.id,VoiceConversationEvent.TURN_FINISHED)
                    } finally {recording?.pcm?.fill(0)}
                }
                }
            } catch(e:CancellationException){throw e}
              catch(e:ChatFailure){
                  currentCoroutineContext().ensureActive()
                  if(voiceConversationActive(session.id))transitionVoiceConversation(session.id,VoiceConversationEvent.FAILURE,errorCode=e.code)
              }
              catch(_:Exception){
                  currentCoroutineContext().ensureActive()
                  val stage=state.value.voiceConversation?.takeIf {it.id==session.id}?.stage
                  val code=when(stage){VoiceConversationStage.CONNECTING->"AUDIO_ROUTE_UNAVAILABLE";VoiceConversationStage.LISTENING->"MICROPHONE_UNAVAILABLE";VoiceConversationStage.SPEAKING->"AUDIO_PLAYBACK";else->"SPEECH_PROTOCOL"}
                  if(voiceConversationActive(session.id))transitionVoiceConversation(session.id,VoiceConversationEvent.FAILURE,errorCode=code)
              }
        }
        synchronized(speechLock) {
            if(activeVoiceConversation?.id!=session.id){created.cancel();return}
            voiceConversationJob=created
        }
        created.invokeOnCompletion {synchronized(speechLock){if(voiceConversationJob===created)voiceConversationJob=null}}
        created.start()
    }
    fun startVoiceConversation(scope:RoleAccessScope,chatId:String,transcriptionConfigId:String,synthesisConfigId:String,input:CommunicationDevice,output:CommunicationDevice) {
        if(!communicationPairSupported(input,output,android.os.Build.VERSION.SDK_INT)){notices.trySend(errorText("AUDIO_ROUTE_PAIR"));return}
        val permit=try {privacy.permit()}catch(_:ChatFailure){notices.trySend("请先解锁应用。");return}
        val current=state.value
        val role=current.roles.firstOrNull {it.scope()==scope}
        val chat=current.chats.firstOrNull {it.id==chatId}
        val transcription=current.speechServiceConfigs.firstOrNull {it.id==transcriptionConfigId}
        val synthesis=current.speechServiceConfigs.firstOrNull {it.id==synthesisConfigId}
        if(role==null||chat==null||current.scope!=scope||current.chatId!=chatId||transcription==null||synthesis==null||!privacy.state.value.foreground){notices.trySend(errorText("ACCESS"));return}
        try {SpeechServices.validateConversationConfigs(transcription,synthesis)}catch(_:IllegalArgumentException){notices.trySend(errorText("SPEECH_CONFIG"));return}
        val session=ActiveVoiceConversation(UUID.randomUUID().toString(),scope,chatId,role.name,transcription.copy(),synthesis.copy(),permit,input,output)
        synchronized(speechLock) {
            if(musicJob!=null||recordingJob!=null||speechJob!=null||voiceConversationJob!=null||voiceConversationInterrupting||activeVoiceConversation!=null||current.busyChat!=null||current.generatingImageId!=null||current.working||ModelGenerationGate.busy()){
                notices.trySend(errorText("SPEECH_BUSY"));return
            }
            state.value.recordedVoice?.pcm?.fill(0)
            activeVoiceConversation=session
            _state.update {it.copy(recordedVoice=null,voiceConversation=VoiceConversationUi(session.id,scope,chatId,role.name,
                transcription.name,transcription.endpoint,synthesis.name,synthesis.endpoint,inputName=voiceDeviceLabel(input),outputName=voiceDeviceLabel(output)))}
        }
        startVoiceConversationLoop(session)
    }
    fun finishVoiceConversationUtterance() {
        if(state.value.voiceConversation?.stage==VoiceConversationStage.LISTENING)voiceRecorder.stop()
    }
    fun interruptVoiceConversation() {
        val session=synchronized(speechLock){activeVoiceConversation}?:return
        val stage=state.value.voiceConversation?.takeIf {it.id==session.id}?.stage?:return
        if(stage !in setOf(VoiceConversationStage.TRANSCRIBING,VoiceConversationStage.GENERATING,VoiceConversationStage.SYNTHESIZING,VoiceConversationStage.SPEAKING))return
        val old=synchronized(speechLock){
            if(voiceConversationInterrupting)return
            voiceConversationInterrupting=true
            voiceConversationJob
        }
        _state.update {if(it.voiceConversation?.id==session.id)it.copy(voiceConversation=it.voiceConversation.copy(reconnecting=true))else it}
        voiceRecorder.cancel();pcmPlayer.stop();old?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            try {
                old?.join()
                if(voiceConversationActive(session.id)) {
                    // Cancellation can mask the route exception; its sticky outcome cannot be masked.
                    val failure=session.routeFailure.code
                    if(failure!=null)transitionVoiceConversation(session.id,VoiceConversationEvent.FAILURE,errorCode=failure)
                    else if(state.value.voiceConversation?.stage!=VoiceConversationStage.PAUSED) {
                        transitionVoiceConversation(session.id,VoiceConversationEvent.INTERRUPT)
                        startVoiceConversationLoop(session)
                    }
                }
            } finally {
                synchronized(speechLock){voiceConversationInterrupting=false}
                _state.update {if(it.voiceConversation?.id==session.id)it.copy(voiceConversation=it.voiceConversation.copy(reconnecting=false))else it}
            }
        }
    }
    fun resumeVoiceConversation(input:CommunicationDevice,output:CommunicationDevice) {
        if(!communicationPairSupported(input,output,android.os.Build.VERSION.SDK_INT)){notices.trySend(errorText("AUDIO_ROUTE_PAIR"));return}
        val session=synchronized(speechLock) {
            val current=activeVoiceConversation?:return
            if(state.value.voiceConversation?.takeIf {it.id==current.id}?.stage!=VoiceConversationStage.PAUSED)return
            if(voiceConversationJob!=null||voiceConversationInterrupting)return
            try {privacy.requirePermit(current.privacyPermit)}catch(_:ChatFailure){return}
            val resumed=current.copy(input=input,output=output,routeFailure=CommunicationRouteFailure())
            activeVoiceConversation=resumed
            transitionVoiceConversation(resumed.id,VoiceConversationEvent.RESUME)
            _state.update {if(it.voiceConversation?.id==resumed.id)it.copy(voiceConversation=it.voiceConversation.copy(
                inputName=voiceDeviceLabel(input),outputName=voiceDeviceLabel(output)))else it}
            resumed
        }
        startVoiceConversationLoop(session)
    }
    fun endVoiceConversation() {
        val pair=synchronized(speechLock) {
            val session=activeVoiceConversation?:return
            activeVoiceConversation=null
            val job=voiceConversationJob
            session to job
        }
        voiceRecorder.cancel();pcmPlayer.stop();pair.second?.cancel()
        _state.update {current->if(current.voiceConversation?.id==pair.first.id)current.copy(voiceConversation=null)else current}
    }
    fun stopAudioForBackground(){stopMusic();endVoiceConversation();cancelVoiceCapture();stopSpeech()}
    fun beginMusicPick(scope:RoleAccessScope,relink:MusicTrack?,kind:String){stopMusic();musicPickTarget=Triple(scope,relink,kind)}
    fun readMusic(uri:Uri?) {
        val target=musicPickTarget;musicPickTarget=null
        if(uri==null)return
        if(target==null){notices.trySend("请重新选择音乐文件。");return}
        action {
            repository.requireRole(target.first)
            val preview=musicFiles.preview(target.first,uri,target.second,target.third)
            withContext(Dispatchers.Main){if(state.value.scope==target.first)_state.update {it.copy(musicImport=preview)}}
        }
    }
    fun dismissMusicImport(){_state.update {it.copy(musicImport=null)}}
    fun confirmMusicImport() {
        val preview=state.value.musicImport?:return
        action {musicFiles.confirm(preview);_state.update {if(it.musicImport===preview)it.copy(musicImport=null)else it}}
    }
    fun createMusic(scope:RoleAccessScope,chatId:String,trackId:String,onCreated:(String)->Unit)=action {
        val session=MusicRepository(db).create(scope,chatId,trackId)
        withContext(Dispatchers.Main){if(state.value.scope==scope&&state.value.chatId==chatId)onCreated(session.id)}
    }
    fun changeMusic(item:MusicSession,position:Long?=null,note:String?=null,onSaved:()->Unit={})=action {
        MusicRepository(db).change(item,position,note)
        withContext(Dispatchers.Main){if(state.value.scope==RoleAccessScope(item.roleId,item.spaceId)&&state.value.chatId==item.chatId)onSaved()}
    }
    fun deleteMusic(item:MusicSession,onDeleted:()->Unit)=action {
        MusicRepository(db).delete(item)
        withContext(Dispatchers.Main){if(state.value.scope==RoleAccessScope(item.roleId,item.spaceId)&&state.value.chatId==item.chatId)onDeleted()}
    }
    fun deleteMusicTrack(track:MusicTrack,onDeleted:()->Unit)=action {musicFiles.delete(track);withContext(Dispatchers.Main){onDeleted()}}
    fun musicSummary(item:MusicSession,expected:String,onReady:(String)->Unit)=action {
        val text=MusicRepository(db).summary(item)
        if(text!=expected)throw ChatFailure("ACTIVITY_CHANGED")
        withContext(Dispatchers.Main){if(state.value.scope==RoleAccessScope(item.roleId,item.spaceId)&&state.value.chatId==item.chatId)onReady(text)}
    }
    fun createBilibili(scope:RoleAccessScope,chat:String,title:String,link:String,onCreated:(String)->Unit)=action {
        val item=BilibiliRepository(db).create(scope,chat,title,BilibiliVideo.parse(link))
        withContext(Dispatchers.Main){if(state.value.scope==scope&&state.value.chatId==chat)onCreated(item.id)}
    }
    fun noteBilibili(item:BilibiliWatch,note:String,onSaved:()->Unit)=action {
        BilibiliRepository(db).note(item,note)
        withContext(Dispatchers.Main){if(state.value.scope==item.scope()&&state.value.chatId==item.chatId)onSaved()}
    }
    fun deleteBilibili(item:BilibiliWatch,onDeleted:()->Unit)=action {
        BilibiliRepository(db).delete(item)
        withContext(Dispatchers.Main){if(state.value.scope==item.scope()&&state.value.chatId==item.chatId)onDeleted()}
    }
    fun summaryBilibili(item:BilibiliWatch,expected:String,onReady:(String)->Unit)=action {
        val text=BilibiliRepository(db).summary(item);if(text!=expected)throw ChatFailure("ACTIVITY_CHANGED")
        withContext(Dispatchers.Main){if(state.value.scope==item.scope()&&state.value.chatId==item.chatId)onReady(text)}
    }
    fun playBilibili(item:BilibiliWatch) {
        val permit=try{privacy.permit()}catch(_:ChatFailure){return}
        if(state.value.scope!=item.scope()||state.value.chatId!=item.chatId)return
        val job=synchronized(speechLock) {
            if(musicJob!=null||recordingJob!=null||speechJob!=null||voiceConversationJob!=null||activeVoiceConversation!=null||state.value.working){notices.trySend(errorText("MUSIC_BUSY"));return}
            val created=viewModelScope.launch(Dispatchers.IO,start=CoroutineStart.LAZY) {
                val repo=BilibiliRepository(db)
                try {
                    BilibiliPlaybackRun(repo::begin,{active,progress->withContext(Dispatchers.Main.immediate) {
                        val player=BilibiliPlayer(getApplication(),active.video(),active.positionMs)
                        try {
                            _state.update {it.copy(bilibiliPlayback=BilibiliPlaybackUi(active,player))}
                            player.run(progress)
                        }finally {
                            player.close()
                            _state.update {if(it.bilibiliPlayback?.player===player)it.copy(bilibiliPlayback=null)else it}
                        }
                    }},repo::progress,{
                        privacy.requirePermit(permit)
                        if(state.value.scope!=item.scope()||state.value.chatId!=item.chatId)throw ChatFailure("ACCESS")
                    },{notices.trySend(errorText("MUSIC_SAVE"))}).run(item)
                }catch(e:CancellationException){throw e}catch(e:ChatFailure){reportFailure(e.code)}catch(_:Exception){reportFailure("BILI_UNAVAILABLE")}
            }
            musicJob=created;_state.update {it.copy(playingMusicId=item.id)}
            created.invokeOnCompletion {synchronized(speechLock){if(musicJob===created){musicJob=null;_state.update {it.copy(playingMusicId=null)}}}}
            created
        }
        job.start()
    }
    fun stopBilibili(id:String){synchronized(speechLock){if(state.value.playingMusicId==id)musicJob?.cancel()}}
    // A released view must not cancel a newer playback of the same watching record.
    fun stopBilibili(player:BilibiliPlayer){synchronized(speechLock){if(state.value.bilibiliPlayback?.player===player)musicJob?.cancel()}}
    fun stopMusic(){synchronized(speechLock){musicJob}?.cancel()}
    fun playMusic(item:MusicSession,surface:android.view.Surface?=null) {
        val permit=try{privacy.permit()}catch(_:ChatFailure){return}
        if(state.value.scope!=RoleAccessScope(item.roleId,item.spaceId)||state.value.chatId!=item.chatId)return
        val job=synchronized(speechLock) {
            if(musicJob!=null||recordingJob!=null||speechJob!=null||voiceConversationJob!=null||activeVoiceConversation!=null||state.value.working){notices.trySend(errorText("MUSIC_BUSY"));return}
            val created=viewModelScope.launch(Dispatchers.IO,start=CoroutineStart.LAZY) {
                val repo=MusicRepository(db)
                try {
                    MediaPlaybackRun(repo::begin,musicFiles::load,{source,start,progress->LocalMusicPlayer(getApplication()).play(source,start,progress,surface)},repo::progress,
                        {privacy.requirePermit(permit)},{notices.trySend(errorText("MUSIC_SAVE"))}).run(item)
                }catch(e:CancellationException){throw e}
                 catch(e:ChatFailure){reportFailure(e.code)}
                 catch(_:Exception){reportFailure("MUSIC_PLAYBACK")}
            }
            musicJob=created;_state.update {it.copy(playingMusicId=item.id)}
            created.invokeOnCompletion {synchronized(speechLock){if(musicJob===created){musicJob=null;_state.update {it.copy(playingMusicId=null)}}}}
            created
        }
        job.start()
    }
    fun generateImage(scope:RoleAccessScope,chatId:String,configId:String,prompt:String) {
        val permit=try {privacy.permit()}catch(_:ChatFailure){notices.trySend("请先解锁应用。");return}
        val job=synchronized(imageGenerationLock) {
            if(imageGenerationJob!=null||state.value.busyChat!=null||state.value.working){notices.trySend(errorText("IMAGE_GENERATION_BUSY"));return}
            val created=viewModelScope.launch(Dispatchers.IO,start=CoroutineStart.LAZY) {
                var request:ImageGenerationRequest?=null
                try {
                    withModelGeneration("image:${UUID.randomUUID()}","IMAGE_GENERATION_BUSY") {
                        privacy.requirePermit(permit)
                        val preparedRequest=imageGenerationRepository.prepare(scope,chatId,configId,prompt)
                        request=preparedRequest
                        _state.update {it.copy(generatingImageId=preparedRequest.image.id)}
                        imageGenerationRepository.start(preparedRequest)
                        val key=vault.read(preparedRequest.config.credentialRef,preparedRequest.config.endpoint)
                        val raw=imageGenerationService.generate(preparedRequest.config,key,preparedRequest.image.prompt)
                        privacy.requirePermit(permit);currentCoroutineContext().ensureActive()
                        val prepared=raw.inputStream().use(LocalWallpaper::generated)
                        currentCoroutineContext().ensureActive();privacy.requirePermit(permit)
                        imageGenerationRepository.complete(preparedRequest,ImageGenerations.MIME_TYPE,prepared.width,prepared.height,prepared.sha256,prepared.bytes,System.currentTimeMillis())
                    }
                    notices.send("图片已生成并保存到当前对话。")
                } catch(e:CancellationException) {
                    request?.let {withContext(NonCancellable){imageGenerationRepository.finish(it,MessageState.CANCELLED,"",System.currentTimeMillis())}}
                    throw e
                } catch(e:ChatFailure) {
                    request?.let {withContext(NonCancellable){imageGenerationRepository.finish(it,MessageState.FAILED,e.code,System.currentTimeMillis())}}
                    reportFailure(e.code)
                } catch(_:IllegalArgumentException) {
                    request?.let {withContext(NonCancellable){imageGenerationRepository.finish(it,MessageState.FAILED,"IMAGE_GENERATION_PROMPT",System.currentTimeMillis())}}
                    reportFailure("IMAGE_GENERATION_PROMPT")
                } catch(_:Exception) {
                    request?.let {withContext(NonCancellable){imageGenerationRepository.finish(it,MessageState.FAILED,"STORAGE",System.currentTimeMillis())}}
                    reportFailure("STORAGE")
                }
            }
            imageGenerationJob=created;imageGenerationScope=scope;imageGenerationChatId=chatId
            created.invokeOnCompletion {synchronized(imageGenerationLock){if(imageGenerationJob===created)imageGenerationJob=null;imageGenerationScope=null;imageGenerationChatId=null;_state.update {it.copy(generatingImageId=null)}}}
            created
        }
        job.start()
    }
    fun cancelImageGeneration(){imageGenerationJob?.cancel()}
    private suspend fun cancelImageGenerationFor(scope:RoleAccessScope,chatId:String?) {
        val job=synchronized(imageGenerationLock){if(imageGenerationScope==scope&&(chatId==null||imageGenerationChatId==chatId))imageGenerationJob else null}
        job?.cancelAndJoin()
    }
    private suspend fun cancelAllImageGeneration(){synchronized(imageGenerationLock){imageGenerationJob}?.cancelAndJoin()}
    fun answerTool(id:String,allow:Boolean) {if(privacy.state.value.canAccess)approvals.answer(id,allow)}
    fun extensionTools(id:String)=db.extensions().tools(id)
    fun roleToolGrants(scope:RoleAccessScope)=db.extensions().grants(scope.roleId,scope.spaceId)
    fun recentToolOperationSummaries()=db.extensions().recentOperationSummaries()
    fun saveExtension(old:McpExtension?,name:String,endpoint:String,key:String,anonymous:Boolean,timeout:Long,saved:(String)->Unit)=action {
        val address=RemoteMcpSession.normalizeEndpoint(endpoint)
        val ref=if(anonymous)null else if(key.isNotBlank())vault.save(key.trim(),address)else {
            require(old?.credentialRef!=null&&old.endpoint==address)
            old.credentialRef
        }
        var result:McpExtension?=null
        coordinator.mutateAll {result=extensions.save(old?.id,old?.version,name,address,ref,timeout)}
        withContext(Dispatchers.Main){saved(requireNotNull(result).id)}
        notices.send("扩展已保存。请获取工具列表、启用扩展，再给角色授权。")
    }
    fun enableExtension(server:McpExtension,enabled:Boolean)=action {
        coordinator.mutateAll {extensions.setEnabled(server.id,server.version,enabled)}
        notices.send(if(enabled)"扩展已启用，请选择允许使用它的角色与工具。"else"扩展已停用，角色授权已撤回。")
    }
    fun deleteExtension(server:McpExtension,done:()->Unit)=action {
        coordinator.mutateAll {extensions.delete(server.id,server.version)}
        withContext(Dispatchers.Main){done()}
        notices.send("扩展已删除。历史调用记录仍保留在所属对话中。")
    }
    fun grantTool(scope:RoleAccessScope,server:McpExtension,name:String,enabled:Boolean)=action {
        coordinator.mutate(scope){extensions.grant(scope,server.id,server.version,name,enabled)}
    }
    fun revokeTool(scope:RoleAccessScope,extensionId:String,name:String)=action {
        coordinator.mutate(scope){extensions.revokeGrant(scope,extensionId,name)}
    }
    fun refreshTools(server:McpExtension)=action {
        extensionTestJob=currentCoroutineContext()[Job]
        _state.update {it.copy(extensionTesting=server.id)}
        var session:McpSession?=null
        try {
            val key=server.credentialRef?.let {vault.read(it,server.endpoint)}
            session=RemoteMcpSession(server.endpoint,key,server.timeoutMs)
            session.connect();val tools=session.listTools()
            session.close();session=null
            coordinator.mutateAll {extensions.replaceCatalog(server.id,server.version,tools)}
            notices.send("连接成功，已获取 ${tools.size} 个工具。请重新选择角色授权。")
        }catch(e:McpFailure){reportFailure(e.code,"扩展连接未完成："+extensionErrorText(e.code))}
        finally {
            try {withContext(NonCancellable){withTimeout(2_000){session?.close()}}}
            finally {extensionTestJob=null;_state.update {it.copy(extensionTesting=null)}}
        }
    }
    fun cancelExtensionTest(){extensionTestJob?.cancel()}
    fun readToolPkg(uri:Uri?) {
        if(uri==null)return
        action {
            val input=getApplication<Application>().contentResolver.openInputStream(uri)?:throw ToolFailure("PKG_FORMAT")
            val preview=input.use(ToolPkgCodec::read)
            _state.update {it.copy(toolPkgPreview=preview)}
        }
    }
    fun previewTimeExample()=action {
        val bytes=ToolPkgCodec.timeExample {path->getApplication<Application>().assets.open("toolpkg/time/$path").use {it.readBytes()}}
        val preview=ToolPkgCodec.decode(bytes)
        _state.update {it.copy(toolPkgPreview=preview)}
    }
    fun dismissToolPkg(){if(state.value.toolPkgInstalling)installJob?.cancel()else _state.update {it.copy(toolPkgPreview=null)}}
    fun installToolPkg() {
        val preview=state.value.toolPkgPreview?:return
        action {
            installJob=currentCoroutineContext()[Job]
            _state.update {it.copy(toolPkgInstalling=true)}
            try {
                toolPackages.install(preview,ToolPkgRepository::validateRegistration)
                _state.update {it.copy(toolPkgPreview=null)}
                notices.send("插件已安装，当前停用。请先启用，再为角色选择工具。")
            } finally {installJob=null;_state.update {it.copy(toolPkgInstalling=false)}}
        }
    }
    private fun backupAction(block:suspend (()->Unit)->Unit)=action {
        backupJob=currentCoroutineContext()[Job]
        val context=currentCoroutineContext();val permit=privacy.permit()
        try {block {context.ensureActive();privacy.requirePermit(permit)}}
        catch(_:OutOfMemoryError){withContext(NonCancellable+Dispatchers.Main){dismissBackup()};throw ChatFailure("BACKUP_LIMIT")}
        finally {backupJob=null}
    }
    fun previewBackup(includeCredentials:Boolean)=backupAction {check->
        stopAudioForBackground()
        cancelAllImageGeneration()
        var snapshot:BackupSnapshot?=null
        playCoordinator.mutateAll {coordinator.mutateAll {snapshot=BackupRepository(db,vault).snapshot(includeCredentials,check)}}
        withContext(Dispatchers.Main){check();backupSnapshot=requireNotNull(snapshot)
            _state.update {it.copy(backup=BackupUi("EXPORT_PREVIEW",snapshot!!.summary()))}}
    }
    fun encryptBackup(password:String,ready:()->Unit)=backupAction {check->
        val snapshot=backupSnapshot?:throw ChatFailure("BACKUP_FORMAT")
        val chars=password.toCharArray()
        val encrypted=try {BackupFormat.encrypt(snapshot,chars)}finally {chars.fill('\u0000')}
        var published=false
        try {withContext(Dispatchers.Main){check();backupCiphertext?.fill(0);backupCiphertext=encrypted;backupSnapshot=null;published=true
            _state.update {it.copy(backup=BackupUi("EXPORT_READY",snapshot.summary()))};ready()}
        }catch(e:Exception){if(!published)encrypted.fill(0);throw e}
    }
    fun writeBackup(uri:Uri?) {
        if(uri==null)return
        backupAction {check->
            if(state.value.backup?.stage!="EXPORT_READY")throw ChatFailure("BACKUP_FORMAT")
            val bytes=backupCiphertext?:throw ChatFailure("BACKUP_FORMAT")
            try {
                check()
                val resolver=getApplication<Application>().contentResolver
                val output=resolver.openOutputStream(uri,"wt")?:throw ChatFailure("FILE")
                output.use {stream->var offset=0
                    while(offset<bytes.size){check();val count=minOf(8192,bytes.size-offset);stream.write(bytes,offset,count);offset+=count}
                    stream.flush()
                }
                check()
                val verified=resolver.openInputStream(uri)?.use {BackupFormat.read(it,check)}?:throw ChatFailure("FILE")
                try {if(!java.security.MessageDigest.isEqual(verified,bytes))throw ChatFailure("FILE")}finally {verified.fill(0)}
                withContext(Dispatchers.Main){check();dismissBackup()};notices.send("加密备份已保存并回读验证。请另行保管口令，遗忘后无法恢复。")
            }catch(e:CancellationException){notices.trySend("备份保存已中断；未完成的文件不能用于恢复。");throw e}
             catch(e:ChatFailure){throw e}catch(_:Exception){throw ChatFailure("FILE")}
        }
    }
    fun readBackup(uri:Uri?) {
        if(uri==null)return
        backupAction {check->
            val bytes=try {getApplication<Application>().contentResolver.openInputStream(uri)?.use {BackupFormat.read(it,check)}?:throw ChatFailure("FILE")}
                catch(e:CancellationException){throw e}catch(e:ChatFailure){throw e}catch(_:Exception){throw ChatFailure("FILE")}
            var published=false
            try {withContext(Dispatchers.Main){check();backupSnapshot=null;backupCiphertext?.fill(0);backupCiphertext=bytes;published=true
                _state.update {it.copy(backup=BackupUi("RESTORE_PASSWORD"))}}
            }catch(e:Exception){if(!published)bytes.fill(0);throw e}
        }
    }
    fun decryptBackup(password:String)=backupAction {check->
        require(state.value.backup?.stage=="RESTORE_PASSWORD")
        val bytes=backupCiphertext?:throw ChatFailure("BACKUP_FORMAT")
        val chars=password.toCharArray()
        val snapshot=try {BackupFormat.decrypt(bytes,chars)}finally {chars.fill('\u0000')}
        BackupValidation.validate(snapshot);BackupRepository(db,vault).validateImages(snapshot)
        val homeExists=db.home().home()!=null
        withContext(Dispatchers.Main){check();backupSnapshot=snapshot
            _state.update {it.copy(backup=BackupUi("RESTORE_PREVIEW",snapshot.summary(),homeExists))}}
    }
    fun restoreBackup(homeMode:HomeRestoreMode)=backupAction {check->
        require(state.value.backup?.stage=="RESTORE_PREVIEW")
        val snapshot=backupSnapshot?:throw ChatFailure("BACKUP_FORMAT")
        withContext(Dispatchers.Main){check();_state.update {it.copy(backup=BackupUi("RESTORING",snapshot.summary()))}}
        var committed=false
        try {withContext(NonCancellable) {
            stopAudioForBackground()
            cancelAllImageGeneration()
            check();playCoordinator.mutateAll {coordinator.mutateAll {BackupRepository(db,vault).restore(snapshot,check,homeMode)}}
            committed=true
            val reminderError=runCatching {ReminderScheduler.sync(getApplication(),db.planner().allReminderEntries())}.exceptionOrNull()
            withContext(Dispatchers.Main){dismissBackup()}
            notices.send("备份已追加恢复，原数据保留。扩展、角色工具授权和导入角色的主动消息开关均已关闭；请核对服务地址和 Key 后再使用。")
            if(reminderError!=null)notices.send("恢复的数据已经保存，但本机提醒未能立即重新安排；请重新解锁应用后检查。")
        }}finally {
            if(!committed)withContext(NonCancellable+Dispatchers.Main){backupSnapshot=null;_state.update {it.copy(backup=BackupUi("RESTORE_PASSWORD"))}}
        }
    }
    fun cancelBackup(){backupJob?.cancel();if(backupJob==null)dismissBackup()}
    fun dismissBackup(){backupSnapshot=null;backupCiphertext?.fill(0);backupCiphertext=null;_state.update {it.copy(backup=null)}}
    fun openPlay(role:RoleAccessScope,chatId:String):String {
        state.value.play?.takeIf {privacy.state.value.canAccess&&it.role==role&&it.chatId==chatId&&state.value.scope==role&&state.value.chatId==chatId}?.let {return it.token}
        closePlay()
        val token=UUID.randomUUID().toString()
        if(!privacy.state.value.canAccess||state.value.scope!=role||state.value.chatId!=chatId)return token
        _state.update {it.copy(play=PlayUi(token,role,chatId))}
        playListJob=viewModelScope.launch(Dispatchers.IO) {
            try {playRepository.list(role,chatId).collect {rows->updatePlay(token){it.copy(activities=rows)}}}
            catch(e:CancellationException){throw e}
            catch(_:Exception){updatePlay(token){it.copy(error="活动列表读取失败，请返回后重试。")}}
        }
        return token
    }
    private fun updatePlay(token:String,block:(PlayUi)->PlayUi) {
        _state.update {s->val p=s.play;if(p?.token==token)s.copy(play=block(p))else s}
    }
    fun setPlayDraft(token:String,id:String?,text:String){updatePlay(token){it.withDraft(id,text)}}
    fun setAdventureForm(token:String,form:AdventureSetup?){updatePlay(token){it.copy(adventureForm=form)}}
    fun closePlay(token:String?=state.value.play?.token) {
        if(token==null||state.value.play?.token!=token)return
        playEpoch++;playListJob?.cancel();playViewJob?.cancel()
        _state.update {if(it.play?.token==token)it.copy(play=null)else it}
        if(::playCoordinator.isInitialized)playCoordinator.running.value?.let {running->
            viewModelScope.launch {playCoordinator.cancelRequest(running.requestId)}
        }
    }
    fun selectPlay(token:String,id:String?) {
        val p=state.value.play?.takeIf {it.token==token}?:return
        playEpoch++;playViewJob?.cancel()
        playCoordinator.running.value?.let {running->viewModelScope.launch {playCoordinator.cancelRequest(running.requestId)}}
        updatePlay(token){it.selected(id)}
        if(id==null)return
        val scope=PlayAccessScope(p.role,p.chatId,id)
        playViewJob=viewModelScope.launch(Dispatchers.IO) {
            try {playRepository.observeView(scope).collect {view->
                updatePlay(token){if(it.selectedId==id)it.copy(view=view,error=if(view==null)"活动已删除，请返回列表。"else null)else it}
            }}catch(e:CancellationException){throw e}
            catch(_:Exception){updatePlay(token){if(it.selectedId==id)it.copy(error="活动读取失败，请返回后重试。")else it}}
        }
    }
    private fun requirePlay(token:String,scope:PlayAccessScope?=null):PlayUi {
        val p=state.value.play?.takeIf {it.token==token}?:throw ChatFailure("PLAY_CHANGED")
        if(state.value.scope!=p.role||state.value.chatId!=p.chatId||
            scope!=null&&(scope.role!=p.role||scope.chatId!=p.chatId||scope.activityId!=p.selectedId))throw ChatFailure("ACCESS")
        return p
    }
    fun createPlay(token:String,title:String,initial:PlayState,onCreated:()->Unit={})=action {
        val p=requirePlay(token);val row=playRepository.create(p.role,p.chatId,title,initial)
        withContext(Dispatchers.Main){if(state.value.play?.token==token){selectPlay(token,row.id);onCreated()}}
    }
    fun playAction(token:String,row:PlaySessionRow,command:PlayAction,onSaved:()->Unit={})=action {
        requirePlay(token,row.scope());playRepository.userAction(row.scope(),row.version,command)
        withContext(Dispatchers.Main){requirePlay(token,row.scope());onSaved()}
    }
    fun playMode(token:String,row:PlaySessionRow,resume:Boolean)=action {
        requirePlay(token,row.scope())
        if(resume)playRepository.resume(row.scope(),row.version)else playRepository.pause(row.scope(),row.version)
    }
    fun stopPlay(){
        playEpoch++
        _state.update {s->s.copy(play=s.play?.copy(approval=null))}
        if(::playCoordinator.isInitialized)playCoordinator.running.value?.let {r->viewModelScope.launch {playCoordinator.cancelRequest(r.requestId)}}
    }
    fun previewPlay(token:String,row:PlaySessionRow)=action {
        requirePlay(token,row.scope());val preview=playRepository.preview(row.scope(),row.version)
        updatePlay(token){if(it.selectedId==row.id)it.copy(approval=preview)else it}
    }
    fun previewAdventureReward(token:String,row:PlaySessionRow,reward:HomeAdventureReward)=action {
        val p=requirePlay(token,row.scope())
        if(p.reward!=null)throw ChatFailure("HOME_PENDING")
        val preview=homeRepository.rewardPreview(row.scope(),row.version,reward)
        requirePlay(token,row.scope())
        updatePlay(token){if(it.selectedId==row.id)it.copy(reward=PlayRewardPending(UUID.randomUUID().toString(),preview))else it}
    }
    fun dismissAdventureReward(token:String){if(!state.value.working)updatePlay(token){it.copy(reward=null)}}
    fun claimAdventureReward(token:String,pending:PlayRewardPending)=action {
        val p=requirePlay(token,pending.preview.source)
        if(p.reward!=pending)throw ChatFailure("HOME_CHANGED")
        try {
            val receipt=homeRepository.claimReward(pending.preview,pending.id)
            requirePlay(token,pending.preview.source)
            updatePlay(token){if(it.reward?.id==pending.id)it.copy(reward=null)else it}
            notices.trySend(if(receipt.repeated)"该纪念物已领取，不会重复发放。"else"纪念物已放入共享家园背包。")
        }catch(e:CancellationException){throw e}
        catch(e:Exception){
            updatePlay(token){if(it.reward?.id==pending.id)it.copy(reward=pending.copy(error="领取尚未确认，可以重试同一操作；若状态已改变，请关闭后重新查看。"))else it}
            throw e
        }
    }
    fun dismissPlayApproval(token:String){updatePlay(token){it.copy(approval=null)}}
    fun startPlay(token:String,preview:FrozenPlayTurn)=action {
        // Capture before checking the UI token: a queued start must never inherit a newer page epoch.
        val expectedEpoch=playEpoch
        val binding=requireNotNull(preview.snapshot.play)
        val p=requirePlay(token,binding.scope)
        if(p.approval!==preview)throw ChatFailure("PLAY_CHANGED")
        updatePlay(token){it.copy(approval=null)}
        if(!playCoordinator.start(binding.scope,binding.version-1,preview,expectedEpoch))throw ChatFailure("MODEL_GENERATION_BUSY")
    }
    fun deletePlay(token:String,row:PlaySessionRow)=action {
        requirePlay(token,row.scope())
        playCoordinator.mutateActivity(row.scope()) {
            val current=playRepository.timeline(row.scope()).activity
            playRepository.delete(row.scope(),current.version)
        }
        withContext(Dispatchers.Main){if(state.value.play?.token==token)selectPlay(token,null)}
    }
    fun playDraft(token:String,row:PlaySessionRow,expected:String,onReady:(String)->Unit)=action {
        requirePlay(token,row.scope());val current=playRepository.timeline(row.scope()).activity
        if(current.version!=row.version||playSummary(current)!=expected)throw ChatFailure("PLAY_CHANGED")
        withContext(Dispatchers.Main){requirePlay(token,row.scope());onReady(expected)}
    }
    fun openHome():String {
        state.value.home?.takeIf {privacy.state.value.canAccess}?.let {return it.token}
        closeHome()
        val token=UUID.randomUUID().toString()
        if(!privacy.state.value.canAccess||!::homeRepository.isInitialized)return token
        _state.update {it.copy(home=HomeUi(token,positionSaving=homePositionJob?.isActive==true))}
        observeHome(token)
        return token
    }
    private fun updateHome(token:String,block:(HomeUi)->HomeUi) {
        _state.update {s->val h=s.home;if(h?.token==token)s.copy(home=block(h))else s}
    }
    private fun requireHome(token:String):HomeUi {
        val h=state.value.home?.takeIf {it.token==token}?:throw ChatFailure("HOME_CHANGED")
        if(!privacy.state.value.canAccess)throw ChatFailure("ACCESS")
        return h
    }
    private fun observeHome(token:String) {
        homeJob?.cancel()
        homeJob=viewModelScope.launch(Dispatchers.IO) {
            try {homeRepository.ensure();homeRepository.observe().collect {view->updateHome(token){it.received(view)}}}
            catch(e:CancellationException){throw e}
            catch(_:Exception){updateHome(token){it.copy(loading=false,view=null,history=null,readError="家园读取失败。请重试；存档不会被清空。")}}
        }
    }
    fun refreshHome(token:String) {
        val h=state.value.home?.takeIf {it.token==token}?:return
        updateHome(token){it.copy(loading=true,readError=null)};observeHome(token)
    }
    fun closeHome(token:String?=state.value.home?.token) {
        if(token==null||state.value.home?.token!=token)return
        homeJob?.cancel();_state.update {if(it.home?.token==token)it.copy(home=null)else it}
    }
    /** A scene hands its latest tile snapshot to the VM immediately, including on disposal.
     * Leaving/rotating cancels rendering, but must not cancel the shared world's queued save. */
    fun homePositions(command:HomeAction.Move) {
        if(!privacy.state.value.canAccess||!::homeRepository.isInitialized)return
        if(homePositionDraft==command&&homePositionJob?.isActive==true)return
        homePositionDraft=command;val serial=++homePositionSerial;val permit=privacy.permit()
        homePositionJob?.cancel()
        _state.update {s->s.copy(home=s.home?.copy(positionSaving=true))}
        homePositionJob=viewModelScope.launch(Dispatchers.IO) {
            try {
                delay(150)
                val view=withContext(NonCancellable){homeRepository.move(command){privacy.requirePermit(permit)}}
                if(serial==homePositionSerial&&privacy.state.value.canAccess)_state.update {s->s.copy(home=s.home?.received(view))}
            }catch(e:CancellationException){throw e}
            catch(e:Exception){if(serial==homePositionSerial&&privacy.state.value.canAccess)notices.send("人物位置保存失败：${errorText(if(e is ChatFailure)e.code else "STORAGE")}")}
            finally {if(serial==homePositionSerial)_state.update {s->s.copy(home=s.home?.copy(positionSaving=false))}}
        }
    }
    fun homeSummary(scope:RoleAccessScope):Flow<HomeSummaryData> = flow {
        privacy.permit()
        repository.requireRole(scope)
        emitAll(homeRepository.observe().map {HomeSummaryData(view=it)})
    }.catch {e->
        if(e is CancellationException)throw e
        emit(HomeSummaryData(error=errorText(if(e is ChatFailure)e.code else "STORAGE")))
    }
    fun playSummary(scope:RoleAccessScope):Flow<PlaySummaryData> = flow {
        val lock=privacy.state.value
        if(!lock.canAccess)return@flow
        repository.requireRole(scope)
        emitAll(playRepository.recentForRole(scope)
            .takeWhile {privacy.state.value.canAccess&&privacy.state.value.epoch==lock.epoch}
            .map {PlaySummaryData(it)})
    }.catch {e->
        if(e is CancellationException)throw e
        emit(PlaySummaryData(error=errorText(if(e is ChatFailure)e.code else "STORAGE")))
    }.flowOn(Dispatchers.IO)
    fun editHomeName(token:String) {
        val h=state.value.home?.takeIf {it.token==token&&it.pending==null}?:return
        val initial=h.view?.home?.value()?.name?:return
        updateHome(token){it.copy(nameDraft=HomeNameDraft(initial))}
    }
    fun homeNameDraft(token:String,text:String){if(text.length<=80)updateHome(token){it.copy(nameDraft=it.nameDraft?.copy(text=text))}}
    fun dismissHomeName(token:String){updateHome(token){it.copy(nameDraft=null)}}
    fun homeAction(token:String,command:HomeAction)=action {
        val h=requireHome(token)
        if(h.pending!=null)throw ChatFailure("HOME_PENDING")
        val id=UUID.randomUUID().toString()
        try {
            val receipt=homeRepository.submit(id,command) {scope,version->
                val current=requireHome(token)
                if(current.pending!=null)throw ChatFailure("HOME_PENDING")
                updateHome(token){it.copy(pending=HomePending(id,scope,version,command))}
            }
            updateHome(token){it.completed(receipt)}
            // The playable map renders earned items and conversation bubbles itself; a second app snackbar repeated pure text outside the map.
        }catch(e:CancellationException){throw e}
        catch(e:Exception) {
            updateHome(token){it.copy(pending=it.pending?.takeIf {p->p.id==id}?.copy(error=errorText(if(e is ChatFailure)e.code else "STORAGE")))}
            throw e
        }
    }
    private suspend fun applyHomePending(token:String,pending:HomePending) {
        val h=requireHome(token)
        if(h.pending?.id!=pending.id)throw ChatFailure("ACCESS")
        try {
            val receipt=homeRepository.apply(pending.scope,pending.version,pending.id,pending.action)
            updateHome(token){it.completed(receipt)}
            // Retry commits use the same map feedback and the same ledger entry as the original command.
        }catch(e:CancellationException){throw e}
        catch(e:Exception) {
            updateHome(token){it.copy(pending=it.pending?.takeIf {p->p.id==pending.id}?.copy(error=if(e is ChatFailure)errorText(e.code)else errorText("STORAGE")))}
            throw e
        }
    }
    fun retryHome(token:String)=action {val pending=requireHome(token).pending?:return@action;applyHomePending(token,pending)}
    fun abandonHomeRetry(token:String){if(!state.value.working)updateHome(token){it.copy(pending=null)}}
    fun homeHistory(token:String)=action {
        val h=requireHome(token);val row=h.view?.home?:throw ChatFailure("HOME_CHANGED")
        val history=homeRepository.history(row.scope());updateHome(token){it.fullHistory(history)}
    }
    override fun onCleared(){closeHome();closePlay();stopAudioForBackground();super.onCleared()}
}

internal fun extensionErrorText(code:String):String=when(code) {
    "PKG_FORMAT"->"插件包格式无效或损坏。请选择包含单个清单的 ZIP/ToolPkg 文件。"
    "PKG_METADATA"->"插件的 METADATA 或工具参数定义无效。"
    "PKG_LIMIT"->"插件超过大小限制：压缩与解压总量均为 1 MiB，单文件 256 KiB。"
    "PKG_VERSION"->"当前支持 schema 1 / API 1.0.0 的纯工具子包。此版本尚不兼容。"
    "PKG_CAPABILITY"->"该插件声明了尚未支持的依赖、资源或宿主权限，暂不能安装。"
    "PKG_INTEGRITY"->"插件内容校验失败。已停止执行，请保留数据并检查安装来源。"
    "JS_EXECUTION","JS_RESULT"->"脚本执行或注册验证未完成，可能调用了尚未支持的宿主 API。"
    "JS_MEMORY","JS_JOB_LIMIT","JS_INPUT_LIMIT"->"脚本超过本机运行资源限制，已停止。"
    "JS_PENDING"->"脚本等待了无法完成的异步任务，已停止。"
    "JS_RUNTIME"->"本机脚本运行库无法加载。"
    "JS_CLOSED","JS_REUSED"->"脚本执行实例已结束，不能重复使用。"
    "PROTOCOL"->"服务响应不符合 MCP Streamable HTTP 协议。"
    "SESSION_EXPIRED"->"地址不存在或服务会话已失效，请检查完整 MCP 地址。"
    "LIMIT","PAGINATION"->"工具列表或响应超过限制，或分页无效。"
    "NOT_CONNECTED"->"服务连接已关闭。"
    "ARGUMENTS"->"工具参数无效或超过限制。"
    "DECLINED"->"你已拒绝本次工具调用。"
    "CLOSE"->"工具已执行，但连接关闭未完成；请先检查调用记录。"
    else->errorText(code)
}
