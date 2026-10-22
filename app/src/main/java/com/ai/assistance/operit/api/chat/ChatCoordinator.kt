package com.ai.assistance.operit.api.chat

import androidx.room.withTransaction
import com.ai.assistance.operit.api.chat.llmprovider.*
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.db.RoomToolInvocationStore
import com.ai.assistance.operit.data.db.ReadingRepository
import com.ai.assistance.operit.core.tools.ToolRequestOwner
import com.ai.assistance.operit.data.model.*
import com.ai.assistance.operit.data.security.Credentials
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.*

interface RequestStore {
    suspend fun prepare(scope: RoleAccessScope, chatId: String, text: String, requestId: String): RequestSnapshot
    suspend fun prepareWithImage(scope:RoleAccessScope,chatId:String,text:String,image:PromptImage?,requestId:String):RequestSnapshot {
        if(image!=null)throw ChatFailure("VISION_DISABLED")
        return prepare(scope,chatId,text,requestId)
    }
    suspend fun prepareWithContent(scope:RoleAccessScope,chatId:String,text:String,image:PromptImage?,material:PromptMaterial?,requestId:String):RequestSnapshot {
        if(material!=null)throw ChatFailure("MATERIAL_TYPE")
        return prepareWithImage(scope,chatId,text,image,requestId)
    }
    suspend fun prepareWithPayload(scope:RoleAccessScope,chatId:String,text:String,image:PromptImage?,material:PromptMaterial?,sticker:PromptSticker?,requestId:String):RequestSnapshot {
        if(sticker!=null)throw ChatFailure("STICKER_IMAGE")
        return prepareWithContent(scope,chatId,text,image,material,requestId)
    }
    suspend fun prepareWithQuote(scope:RoleAccessScope,chatId:String,text:String,image:PromptImage?,material:PromptMaterial?,sticker:PromptSticker?,quote:MessageQuoteSnapshot?,requestId:String):RequestSnapshot {
        if(quote!=null)throw ChatFailure("QUOTE_UNSUPPORTED")
        return prepareWithPayload(scope,chatId,text,image,material,sticker,requestId)
    }
    suspend fun update(snapshot: RequestSnapshot, text: String, state: MessageState, error: String, first: Long, elapsed: Long)
    suspend fun complete(snapshot:RequestSnapshot,text:String,state:MessageState,error:String,first:Long,elapsed:Long) {
        update(snapshot,text,state,error,first,elapsed)
    }
}

class RoomRequestStore(private val db: AppDatabase) : RequestStore {
    override suspend fun prepare(scope:RoleAccessScope,chatId:String,text:String,requestId:String)=prepareWithImage(scope,chatId,text,null,requestId)
    override suspend fun prepareWithImage(scope:RoleAccessScope,chatId:String,text:String,image:PromptImage?,requestId:String)=prepareWithContent(scope,chatId,text,image,null,requestId)
    override suspend fun prepareWithContent(scope:RoleAccessScope,chatId:String,text:String,image:PromptImage?,material:PromptMaterial?,requestId:String)=prepareWithPayload(scope,chatId,text,image,material,null,requestId)
    override suspend fun prepareWithPayload(scope: RoleAccessScope, chatId: String, text: String, image:PromptImage?,material:PromptMaterial?,sticker:PromptSticker?, requestId: String): RequestSnapshot = db.withTransaction {
        preparePayload(scope,chatId,text,image,material,sticker,null,requestId)
    }
    override suspend fun prepareWithQuote(scope:RoleAccessScope,chatId:String,text:String,image:PromptImage?,material:PromptMaterial?,sticker:PromptSticker?,quote:MessageQuoteSnapshot?,requestId:String):RequestSnapshot = db.withTransaction {
        preparePayload(scope,chatId,text,image,material,sticker,quote,requestId)
    }
    private suspend fun preparePayload(scope:RoleAccessScope,chatId:String,text:String,image:PromptImage?,material:PromptMaterial?,sticker:PromptSticker?,quote:MessageQuoteSnapshot?,requestId:String):RequestSnapshot {
        require((text.isNotBlank()||image!=null||material!=null||sticker!=null) && text.length <= 12000)
        require(listOfNotNull(image,material,sticker).size<=1)
        val frozenImage=image?.let {PromptImage(it.mimeType,it.width,it.height,it.sha256,it.data.copyOf()).also(PromptImage::validate)}
        val frozenMaterial=material?.copy()?.also(PromptMaterial::validate)
        val dao = db.dao()
        val chat = dao.chat(scope.roleId,scope.spaceId,chatId) ?: throw ChatFailure("ACCESS")
        val role = dao.role(scope.roleId) ?: throw ChatFailure("ACCESS")
        frozenMaterial?.let {ReadingRepository(db).verifyPassage(scope,it)}
        val config = requireNotNull(dao.config(chat.configId))
        if(frozenImage!=null&&!config.visionEnabled)throw ChatFailure("VISION_DISABLED")
        val candidateSticker=sticker?.copy(data=sticker.data.copyOf())?.also(PromptSticker::validate)
        val frozenSticker=candidateSticker?.let {candidate->
            candidate.sourceAssetId?.let {id->
                val stored=dao.stickerAsset(scope.roleId,scope.spaceId,id)?.promptSticker()?:throw ChatFailure("ACCESS")
                if(stored.displayName!=candidate.displayName||stored.mimeType!=candidate.mimeType||stored.width!=candidate.width||stored.height!=candidate.height||stored.sha256!=candidate.sha256||!stored.data.contentEquals(candidate.data))throw ChatFailure("ACCESS")
            }
            candidate.copy(sourceAssetId=null,data=candidate.data.copyOf())
        }
        val history = dao.history(scope.roleId,scope.spaceId,chatId)
        val historyAttachments=dao.historyAttachments(scope.roleId,scope.spaceId,chatId)
        val historyMaterials=dao.historyMaterials(scope.roleId,scope.spaceId,chatId)
        val historyStickers=dao.historyStickers(scope.roleId,scope.spaceId,chatId)
        val frozenQuote=quote?.let {candidate->
            val original=history.firstOrNull {it.id==candidate.messageId&&it.state==MessageState.COMPLETE&&it.content.isNotBlank()}?:throw ChatFailure("QUOTE_SOURCE")
            val speaker=if(original.kind=="user")role.selfName() else role.name
            MessageQuoteSnapshot(original.id,speaker,original.content.take(12000))
        }
        val now = maxOf(System.currentTimeMillis(),(history.maxOfOrNull { it.createdAt } ?: 0L)+1)
        // Keep the stored reply text clean while giving the model the exact referenced message as context.
        val modelText=frozenQuote?.let {"引用消息（${it.speaker}）：\n${it.preview}\n\n用户回复：\n$text"}?:text
        val resolved = RequestContext.resolve(scope,role,chat,history,modelText,db.worldbook().read(scope.roleId,scope.spaceId),db.relationships().read(scope.roleId,scope.spaceId),historyAttachments,frozenImage,historyMaterials,frozenMaterial,historyStickers,frozenSticker,config.visionEnabled,
            db.memories().read(scope.roleId,scope.spaceId),db.environment().profile(scope.roleId,scope.spaceId),now,plannerEntries=db.planner().readPlanner(scope.roleId,scope.spaceId),cycleRecords=db.planner().readCycles(scope.roleId,scope.spaceId))
        val reply = UUID.randomUUID().toString()
        val userId=UUID.randomUUID().toString()
        dao.insertMessages(listOf(
            MessageEntity(userId,chatId,requestId,"user",text,MessageState.COMPLETE,now,
                quotedMessageId=frozenQuote?.messageId,quotedSpeaker=frozenQuote?.speaker.orEmpty(),quotedPreview=frozenQuote?.preview.orEmpty()),
            MessageEntity(reply,chatId,requestId,"assistant","",MessageState.QUEUED,now+1,bubbleStyle=role.effectiveReplyStyle())))
        frozenImage?.let {dao.insertAttachment(MessageAttachment(userId,chatId,requestId,it.mimeType,it.width,it.height,it.sha256,it.data.copyOf()))}
        frozenMaterial?.let {dao.insertMaterial(MessageMaterial(userId,chatId,requestId,it.kind,it.displayName,it.sourceUrl,it.mimeType,it.text,it.originalChars,it.truncated,it.sha256))}
        frozenSticker?.let {dao.insertMessageSticker(MessageSticker(userId,chatId,requestId,it.displayName,it.mimeType,it.width,it.height,it.sha256,it.data.copyOf()))}
        val tools=if(role.isRaw())emptyList()else RoomToolInvocationStore(db).freeze(ToolRequestOwner(scope,chatId,requestId,reply))
        val snapshot=RequestSnapshot(requestId,chatId,reply,role.copy(),config.copy(),resolved.turns.toList(),scope,tools,resolved.worldbook,resolved.relationships,resolved.memories,resolved.environment,resolved.planner)
        return snapshot
    }
    override suspend fun update(snapshot: RequestSnapshot, text: String, state: MessageState, error: String, first: Long, elapsed: Long) {
        GroupConversations.requirePrivate(snapshot)
        db.dao().updateActive(snapshot.scope.roleId,snapshot.scope.spaceId,snapshot.chatId,snapshot.requestId,snapshot.assistantId,text,state,error,first,elapsed)
    }
    override suspend fun complete(snapshot:RequestSnapshot,text:String,state:MessageState,error:String,first:Long,elapsed:Long)=db.withTransaction {
        GroupConversations.requirePrivate(snapshot)
        // Store the same paragraph boundaries shown by chat bubbles so selection and actions target one visible message.
        val paragraphs=if(state==MessageState.COMPLETE&&snapshot.role.effectiveReplyStyle()=="short")MessageParagraphs.split(text) else emptyList()
        val storedText=paragraphs.firstOrNull()?:text
        val changed=db.dao().updateActive(snapshot.scope.roleId,snapshot.scope.spaceId,snapshot.chatId,snapshot.requestId,snapshot.assistantId,storedText,state,error,first,elapsed)
        // An ownership/terminal-state rejection is not a persisted reply for an awaiting caller.
        if(changed!=1)throw ChatFailure("ACCESS")
        if(changed==1) {
            if(paragraphs.size>1) {
                val baseCreatedAt=System.currentTimeMillis()
                db.dao().insertMessages(paragraphs.drop(1).mapIndexed { index, paragraph ->
                    MessageEntity(UUID.randomUUID().toString(),snapshot.chatId,snapshot.requestId,"assistant",paragraph,MessageState.COMPLETE,baseCreatedAt+index,
                        firstTokenMs=first,elapsedMs=elapsed,bubbleStyle=snapshot.role.effectiveReplyStyle())
                })
            }
        }
    }
}

data class RunningRequest(val scope: RoleAccessScope, val chatId: String, val group:GroupRequestIdentity?=null,val thinking:Boolean=false)
data class CompletedRequest(val requestId:String,val assistantId:String,val scope:RoleAccessScope,
    val chatId:String,val text:String,val state:MessageState,val errorCode:String)
private data class RequestHandle(val job:Job,val result:Deferred<CompletedRequest>)

class ChatCoordinator(private val store: RequestStore, private val service: AIService,
    private val credentials: Credentials, private val coroutineScope: CoroutineScope,
    private val accessAllowed:()->Boolean={true},private val accessVersion:()->Long={0},private val clock: () -> Long = { System.nanoTime()/1_000_000 }) {
    private val mutex = Mutex()
    private var job: Job? = null
    private var activeScope: RoleAccessScope? = null
    private val _running=MutableStateFlow<RunningRequest?>(null)
    val running: StateFlow<RunningRequest?> = _running.asStateFlow()
    private val _busyChat = MutableStateFlow<String?>(null)
    val busyChat: StateFlow<String?> = _busyChat.asStateFlow()
    val errors = MutableSharedFlow<String>(extraBufferCapacity = 1)

    suspend fun send(scope: RoleAccessScope, chatId: String, text: String, image:PromptImage?=null,material:PromptMaterial?=null,sticker:PromptSticker?=null,quote:MessageQuoteSnapshot?=null): Boolean =
        start(scope,chatId,text,image,material,sticker,quote=quote)!=null

    // A frozen store belongs to this request; group and private sends share one lock and job.
    suspend fun sendGroup(groupStore:RequestStore, scope:RoleAccessScope, group:GroupAccessScope, text:String):Boolean =
        start(scope,group.groupId,text,null,null,null,groupStore,group)!=null

    // Keep the exact job handle: a later request in the same chat must never be cancelled or read.
    suspend fun sendAndAwait(scope:RoleAccessScope,chatId:String,text:String):CompletedRequest {
        val handle=start(scope,chatId,text,null,null,null)?:throw ChatFailure("MODEL_GENERATION_BUSY")
        try {
            handle.job.join()
            return handle.result.await()
        } finally {
            withContext(NonCancellable){handle.job.cancelAndJoin()}
        }
    }

    private suspend fun start(scope:RoleAccessScope,chatId:String,text:String,image:PromptImage?,material:PromptMaterial?,sticker:PromptSticker?, requestStore:RequestStore=store, expectedGroup:GroupAccessScope?=null,quote:MessageQuoteSnapshot?=null):RequestHandle? = mutex.withLock {
        currentCoroutineContext().ensureActive()
        val permissionVersion=accessVersion()
        fun allowed()=accessAllowed()&&accessVersion()==permissionVersion
        if(!accessAllowed())throw ChatFailure("LOCKED")
        if (job?.isActive == true) return null
        val requestId=UUID.randomUUID().toString()
        if(!ModelGenerationGate.acquire(requestId))return null
        val snapshot = try {
            val prepared=if(quote!=null)requestStore.prepareWithQuote(scope,chatId,text,image,material,sticker,quote,requestId)
                else when {sticker!=null->requestStore.prepareWithPayload(scope,chatId,text,image,material,sticker,requestId);material!=null->requestStore.prepareWithContent(scope,chatId,text,image,material,requestId);image!=null->requestStore.prepareWithImage(scope,chatId,text,image,requestId);else->requestStore.prepare(scope,chatId,text,requestId)}
            prepared.also {if(it.group?.group!=expectedGroup)throw ChatFailure("ACCESS")}
        }
            catch(e:Exception){ModelGenerationGate.release(requestId);throw e}
        if(!allowed()||!currentCoroutineContext().isActive) {
            try {withContext(NonCancellable){requestStore.complete(snapshot,"",MessageState.CANCELLED,"",-1,0)}}
            finally {ModelGenerationGate.release(requestId)}
            currentCoroutineContext().ensureActive()
            throw ChatFailure("LOCKED")
        }
        activeScope = snapshot.scope
        _running.value=RunningRequest(snapshot.scope,chatId,snapshot.group)
        _busyChat.value = chatId
        val result=CompletableDeferred<CompletedRequest>()
        val created = coroutineScope.launch(start = CoroutineStart.UNDISPATCHED) {
            val started = clock()
            var first = -1L
            var persisted = started
            val content = StringBuilder()
            suspend fun update(state: MessageState, error: String = "") = requestStore.update(snapshot,content.toString(),state,error,first,clock()-started)
            suspend fun complete(state:MessageState,error:String="") {
                requestStore.complete(snapshot,content.toString(),state,error,first,clock()-started)
                result.complete(CompletedRequest(snapshot.requestId,snapshot.assistantId,snapshot.scope,snapshot.chatId,content.toString(),state,error))
            }
            try {
                update(MessageState.WAITING)
                if(!allowed())throw CancellationException("LOCKED")
                val key = credentials.read(snapshot.config.credentialRef,snapshot.config.endpoint)
                // A tool chain can start several HTTP calls; bound the entire reply, including reasoning and tools.
                withTimeout(snapshot.config.timeoutSeconds*1000L) {
                service.sendEvents(snapshot,key).collect { event ->
                    currentCoroutineContext().ensureActive()
                    if(!allowed())throw CancellationException("LOCKED")
                    when(event) {
                        is ChatStreamEvent.Text->{
                            _running.value=_running.value?.copy(thinking=false)
                            content.append(event.value)
                            val firstChunk = first < 0
                            if(firstChunk) first = clock()-started
                            // Persist the first chunk immediately and bounded intervals thereafter, not only at EOF.
                            if(firstChunk || clock()-persisted >= 80) { update(MessageState.GENERATING); persisted=clock() }
                        }
                        is ChatStreamEvent.Reasoning->_running.value=_running.value?.copy(thinking=true)
                        is ChatStreamEvent.RoundEnd->Unit
                    }
                }
                }
                currentCoroutineContext().ensureActive()
                if(!allowed())throw CancellationException("LOCKED")
                complete(MessageState.COMPLETE)
            } catch (e: TimeoutCancellationException) {
                try {withContext(NonCancellable){complete(MessageState.FAILED,"TIMEOUT")}}
                catch (_: Exception) {result.completeExceptionally(ChatFailure("STORAGE"));errors.tryEmit("STORAGE")}
                errors.tryEmit("TIMEOUT")
            } catch (e: CancellationException) {
                try {withContext(NonCancellable) { complete(MessageState.CANCELLED) }}
                catch (_: Exception) {result.completeExceptionally(ChatFailure("STORAGE"));errors.tryEmit("STORAGE")}
                throw e
            } catch (e: Exception) {
                val code = when(e) {is ChatFailure->e.code;is com.ai.assistance.operit.core.tools.ToolFailure->e.code;else->"STORAGE"}
                try { complete(MessageState.FAILED,code) } catch (_: Exception) {result.completeExceptionally(ChatFailure("STORAGE"));errors.tryEmit("STORAGE")}
                errors.tryEmit(code)
            } finally { _busyChat.value=null; _running.value=null;ModelGenerationGate.release(requestId) }
        }
        job=created
        created.invokeOnCompletion {failure->if(!result.isCompleted)result.completeExceptionally(failure?:ChatFailure("STORAGE"))}
        RequestHandle(created,result)
    }
    suspend fun cancel() = mutex.withLock { job?.cancelAndJoin() }
    suspend fun mutateAll(block:suspend ()->Unit)=mutex.withLock {job?.cancelAndJoin();block()}

    suspend fun mutateGroup(scope:GroupAccessScope, block:suspend ()->Unit)=mutex.withLock {
        if(_running.value?.group?.group==scope)job?.cancelAndJoin()
        block()
    }
    suspend fun mutateRoleAndGroups(scope:RoleAccessScope, belongs:suspend (GroupAccessScope)->Boolean, block:suspend ()->Unit)=mutex.withLock {
        val running=_running.value
        if(running!=null && (running.scope==scope || running.group?.let {belongs(it.group)}==true))job?.cancelAndJoin()
        block()
    }

    // Hold the same lock used by send until deletion commits; no new request can race into it.
    suspend fun mutate(scope: RoleAccessScope, chatId: String? = null, block: suspend () -> Unit) = mutex.withLock {
        if(activeScope==scope && (chatId==null || _busyChat.value==chatId)) job?.cancelAndJoin()
        block()
    }
}
