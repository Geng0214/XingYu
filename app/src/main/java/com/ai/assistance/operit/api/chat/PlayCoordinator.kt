package com.ai.assistance.operit.api.chat

import com.ai.assistance.operit.api.chat.llmprovider.AIService
import com.ai.assistance.operit.data.model.*
import com.ai.assistance.operit.data.security.Credentials
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class PlayProgress(val state:MessageState,val text:String="",
    val firstTokenMs:Long=-1,val elapsedMs:Long=0,val errorCode:String="")
data class RunningPlayTurn(val scope:PlayAccessScope,val requestId:String)

/** Each method must check role/space/chat/activity/version/token in the same storage transaction. */
interface PlayRequestStore {
    suspend fun prepare(scope:PlayAccessScope,version:Long,requestId:String,approval:FrozenPlayTurn?=null):FrozenPlayTurn
    suspend fun update(frozen:FrozenPlayTurn,progress:PlayProgress)
    suspend fun complete(frozen:FrozenPlayTurn,result:PlayResponse,progress:PlayProgress)
    suspend fun finishFailed(frozen:FrozenPlayTurn,progress:PlayProgress,interrupted:Boolean)
}

/** Owns the exact job through cancellation and storage finalization, also across page changes. */
class PlayCoordinator(private val store:PlayRequestStore,service:AIService,private val credentials:Credentials,
    private val coroutineScope:CoroutineScope,private val accessAllowed:()->Boolean,private val accessVersion:()->Long,
    private val clock:()->Long={System.nanoTime()/1_000_000}) {
    private val service=PlayGenerationService(service)
    private val mutex=Mutex()
    private var job:Job?=null
    private val _running=MutableStateFlow<RunningPlayTurn?>(null)
    val running:StateFlow<RunningPlayTurn?> = _running.asStateFlow()
    val errors=MutableSharedFlow<String>(extraBufferCapacity=1)

    suspend fun start(scope:PlayAccessScope,version:Long,approval:FrozenPlayTurn?=null,expectedAccessVersion:Long=accessVersion()):Boolean=mutex.withLock {
        currentCoroutineContext().ensureActive()
        val accessEpoch=expectedAccessVersion
        fun allowed()=accessAllowed()&&accessVersion()==accessEpoch
        if(!allowed())throw ChatFailure("LOCKED")
        if(job?.isActive==true)return false
        val id=UUID.randomUUID().toString()
        if(!ModelGenerationGate.acquire(id))return false
        // Finish the short prepare transaction even if the caller is cancelled; then terminalize it below.
        val frozen=try {withContext(NonCancellable){store.prepare(scope,version,id,approval)}}catch(e:Exception){ModelGenerationGate.release(id);throw e}
        // A faulty store cannot route an activity response to an unrelated request or private chat.
        if(frozen.snapshot.play?.scope!=scope||frozen.snapshot.play.version!=version+1||frozen.snapshot.requestId!=id||frozen.snapshot.scope!=scope.role||
            frozen.snapshot.chatId!=scope.chatId||frozen.snapshot.group!=null||frozen.snapshot.tools.isNotEmpty()) {
            ModelGenerationGate.release(id);throw ChatFailure("ACCESS")
        }
        if(!allowed()||!currentCoroutineContext().isActive) {
            try {withContext(NonCancellable){store.finishFailed(frozen,PlayProgress(MessageState.CANCELLED),true)}}
            finally {ModelGenerationGate.release(id)}
            currentCoroutineContext().ensureActive();throw ChatFailure("LOCKED")
        }
        _running.value=RunningPlayTurn(scope,id)
        job=coroutineScope.launch(start=CoroutineStart.UNDISPATCHED) {
            val started=clock();var persisted=started
            var progress=PlayProgress(MessageState.WAITING)
            suspend fun check(){currentCoroutineContext().ensureActive();if(!allowed())throw CancellationException("LOCKED")}
            try {
                check();store.update(frozen,progress)
                val key=credentials.read(frozen.snapshot.config.credentialRef,frozen.snapshot.config.endpoint)
                check()
                val result=service.generate(frozen,key,onText={text->
                    check();val now=clock();val first=progress.firstTokenMs<0
                    progress=progress.copy(state=MessageState.GENERATING,text=text,
                        firstTokenMs=if(first)now-started else progress.firstTokenMs,elapsedMs=now-started)
                    if(first||now-persisted>=80){store.update(frozen,progress);persisted=now}
                })
                check();store.complete(frozen,result.response,progress.copy(state=MessageState.COMPLETE,elapsedMs=clock()-started))
            }catch(e:CancellationException) {
                try {withContext(NonCancellable){store.finishFailed(frozen,
                    progress.copy(state=MessageState.CANCELLED,elapsedMs=clock()-started),true)}}
                catch(_:Exception){errors.tryEmit("STORAGE")}
                throw e
            }catch(e:Exception) {
                val code=if(e is ChatFailure)e.code else "STORAGE"
                try {store.finishFailed(frozen,progress.copy(state=MessageState.FAILED,errorCode=code,elapsedMs=clock()-started),false)}
                catch(_:Exception){errors.tryEmit("STORAGE")}
                errors.tryEmit(code)
            }finally {
                _running.value=null
                ModelGenerationGate.release(id)
            }
        }
        true
    }
    suspend fun cancel()=mutex.withLock {job?.cancelAndJoin()}
    suspend fun cancelRequest(id:String)=mutex.withLock {if(_running.value?.requestId==id)job?.cancelAndJoin()}
    suspend fun mutateAll(block:suspend ()->Unit)=mutex.withLock {job?.cancelAndJoin();block()}
    suspend fun mutateActivity(scope:PlayAccessScope,block:suspend ()->Unit)=mutex.withLock {
        if(_running.value?.scope==scope)job?.cancelAndJoin()
        block()
    }
    suspend fun mutateRole(scope:RoleAccessScope,chatId:String?=null,block:suspend ()->Unit)=mutex.withLock {
        val running=_running.value
        if(running!=null&&running.scope.role==scope&&(chatId==null||running.scope.chatId==chatId))job?.cancelAndJoin()
        block()
    }
}
