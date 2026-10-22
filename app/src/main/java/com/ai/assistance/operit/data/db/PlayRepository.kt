package com.ai.assistance.operit.data.db

import androidx.room.withTransaction
import com.ai.assistance.operit.api.chat.*
import com.ai.assistance.operit.data.model.*
import kotlinx.coroutines.flow.map
import java.util.UUID

data class PlayTimeline(val activity:PlaySessionRow,val requests:List<PlayRequestRow>)
data class PlayView(val activity:PlaySessionRow,val recent:List<PlayRequestRow>,val requestCount:Int)
class PlayRepository(private val db:AppDatabase,private val clock:()->Long={System.currentTimeMillis()}):PlayRequestStore {
    private val dao get()=db.play()
    private suspend fun owner(scope:PlayAccessScope):Pair<RoleEntity,ChatEntity> {
        val role=db.dao().role(scope.role.roleId)?.takeIf {it.scope()==scope.role}?:throw ChatFailure("ACCESS")
        val chat=db.dao().chat(scope.role.roleId,scope.role.spaceId,scope.chatId)?:throw ChatFailure("ACCESS")
        return role to chat
    }
    private suspend fun read(scope:PlayAccessScope):PlaySessionRow {
        owner(scope)
        return dao.session(scope.role.roleId,scope.role.spaceId,scope.chatId,scope.activityId)?.also {it.value()}?:throw ChatFailure("ACCESS")
    }
    suspend fun timeline(scope:PlayAccessScope)=db.withTransaction {
        val row=read(scope);val requests=dao.requests(scope.role.roleId,scope.role.spaceId,scope.chatId,scope.activityId)
        PlayStorageValidation.validate(row,requests);PlayTimeline(row,requests)
    }
    fun observe(scope:PlayAccessScope)=db.invalidationTracker.createFlow("play_sessions","play_requests","roles","chats").map {
        db.withTransaction {
            val row=dao.session(scope.role.roleId,scope.role.spaceId,scope.chatId,scope.activityId)
            if(row==null)null else timeline(scope)
        }
    }
    /** Bounded display read; full history validation belongs to mutations, recovery and backup. */
    fun observeView(scope:PlayAccessScope)=db.invalidationTracker.createFlow("play_sessions","play_requests","roles","chats").map {
        db.withTransaction {
            val row=dao.session(scope.role.roleId,scope.role.spaceId,scope.chatId,scope.activityId)
            row?.let {
                it.value()
                PlayView(it,dao.recent(scope.role.roleId,scope.role.spaceId,scope.chatId,scope.activityId).onEach {q->q.validate()},
                    dao.requestCount(scope.role.roleId,scope.role.spaceId,scope.chatId,scope.activityId))
            }
        }
    }
    fun list(scope:RoleAccessScope,chatId:String)=db.invalidationTracker.createFlow("play_sessions","roles","chats").map {
        db.withTransaction {
            db.dao().chat(scope.roleId,scope.spaceId,chatId)?:throw ChatFailure("ACCESS")
            dao.sessions(scope.roleId,scope.spaceId,chatId).onEach {it.value()}
        }
    }
    fun recentForRole(scope:RoleAccessScope)=db.invalidationTracker.createFlow("play_sessions","roles","chats").map {
        db.withTransaction {
            db.dao().role(scope.roleId)?.takeIf {it.scope()==scope}?:throw ChatFailure("ACCESS")
            dao.recentForRole(scope.roleId,scope.spaceId).onEach {row->
                if(row.value().scope.role!=scope)throw ChatFailure("ACCESS")
            }
        }
    }
    suspend fun create(role:RoleAccessScope,chatId:String,title:String,state:PlayState):PlaySessionRow=db.withTransaction {
        val scope=PlayAccessScope(role,chatId,UUID.randomUUID().toString());owner(scope)
        require(when(state){is GomokuState->state.moves.isEmpty();is MemoryPairsState->state.actions.isEmpty();is DateStoryState->state.turns.isEmpty()&&!state.ended;is JourneyState->state.events.isEmpty()&&!state.ended;is WordPlayState->state.game.moves.isEmpty();is CardPlayState->state.game.moves.isEmpty();is XiangqiPlayState->state.game.moves.isEmpty()&&state.game.outcome==null;is AdventurePlayState->state.game.events.isEmpty()})
        // Read the selected clothes in the creation transaction; later home edits cannot retarget a story.
        val initial=if(state is DateStoryState||state is JourneyState) {
            val outfits=PlayOutfits.capture(role,db.home().home()?.value())
            when(state){is DateStoryState->state.withOutfits(outfits);is JourneyState->state.withOutfits(outfits);else->error("Not a story")}
        }else state
        val row=PlaySessionRow.from(PlaySession(scope,title,initial),clock());dao.insert(row);row
    }
    private suspend fun save(old:PlaySessionRow,session:PlaySession):PlaySessionRow {
        val row=old.changed(session,clock())
        if(dao.updateSession(row.roleId,row.spaceId,row.chatId,row.id,old.version,row.title,row.stateJson,row.version,row.status,row.requestId,row.updatedAt)!=1)throw ChatFailure("PLAY_CHANGED")
        return row
    }
    suspend fun userAction(scope:PlayAccessScope,version:Long,action:PlayAction)=db.withTransaction {
        val row=read(scope);save(row,PlaySessions.userAction(row.value(),scope,version,action))
    }
    suspend fun pause(scope:PlayAccessScope,version:Long)=db.withTransaction {
        val row=read(scope)
        if(row.requestId!=null)throw ChatFailure("MODEL_GENERATION_BUSY")
        save(row,PlaySessions.pause(row.value(),scope,version))
    }
    suspend fun resume(scope:PlayAccessScope,version:Long)=db.withTransaction {
        val row=read(scope);save(row,PlaySessions.resume(row.value(),scope,version))
    }
    suspend fun delete(scope:PlayAccessScope,version:Long)=db.withTransaction {
        val row=read(scope)
        if(row.requestId!=null)throw ChatFailure("MODEL_GENERATION_BUSY")
        if(dao.delete(row.roleId,row.spaceId,row.chatId,row.id,version)!=1)throw ChatFailure("PLAY_CHANGED")
    }
    /** Preview has no side effects; prepare compares it inside the write transaction. */
    suspend fun preview(scope:PlayAccessScope,version:Long):FrozenPlayTurn=db.withTransaction {build(scope,version,UUID.randomUUID().toString()).frozen}
    private suspend fun build(scope:PlayAccessScope,version:Long,id:String):PreparedPlayTurn {
        val row=read(scope);val (role,chat)=owner(scope)
        val config=db.dao().config(chat.configId)?:throw ChatFailure("ACCESS")
        return PlayGenerations.prepare(row.value(),scope,version,role,chat,config,id)
    }
    override suspend fun prepare(scope:PlayAccessScope,version:Long,requestId:String,approval:FrozenPlayTurn?):FrozenPlayTurn=db.withTransaction {
        val current=timeline(scope)
        if(current.requests.size>=PlayStorageValidation.MAX_REQUESTS)throw ChatFailure("PLAY_LIMIT")
        val prepared=build(scope,version,requestId)
        if(approval!=null&&!approval.sameApproval(prepared.frozen))throw ChatFailure("PLAY_CHANGED")
        val row=save(current.activity,prepared.session)
        dao.insert(PlayRequestRow.from(prepared.frozen,row.updatedAt));prepared.frozen
    }
    private suspend fun active(f:FrozenPlayTurn):Pair<PlaySessionRow,PlayRequestRow> {
        val scope=f.snapshot.play?.scope?:throw ChatFailure("ACCESS")
        val row=read(scope)
        val request=dao.request(row.roleId,row.spaceId,row.chatId,row.id,f.snapshot.requestId)?:throw ChatFailure("ACCESS")
        request.validate()
        if(!f.accepts(row.value())||!request.matches(f))throw ChatFailure("PLAY_CHANGED")
        return row to request
    }
    private suspend fun saveRequest(row:PlayRequestRow) {
        row.validate()
        if(dao.updateRequest(row.roleId,row.spaceId,row.chatId,row.activityId,row.requestId,row.state,row.content,row.errorCode,row.firstTokenMs,row.elapsedMs)!=1)throw ChatFailure("PLAY_CHANGED")
    }
    override suspend fun update(frozen:FrozenPlayTurn,progress:PlayProgress)=db.withTransaction {
        require(progress.state in activeStates)
        val (_,request)=active(frozen);saveRequest(request.progress(progress))
    }
    override suspend fun complete(frozen:FrozenPlayTurn,result:PlayResponse,progress:PlayProgress)=db.withTransaction {
        require(progress.state==MessageState.COMPLETE)
        val (row,request)=active(frozen)
        require(PlayGenerations.decode(frozen,progress.text)==result)
        val next=frozen.commit(row.value(),result)
        saveRequest(request.progress(progress));save(row,next);Unit
    }
    override suspend fun finishFailed(frozen:FrozenPlayTurn,progress:PlayProgress,interrupted:Boolean)=db.withTransaction {
        require(progress.state in setOf(MessageState.FAILED,MessageState.CANCELLED))
        val (row,request)=active(frozen)
        saveRequest(request.progress(progress));save(row,frozen.fail(row.value(),interrupted));Unit
    }
    suspend fun recover()=db.withTransaction {
        val sessions=dao.pendingSessions();val requests=dao.pendingRequests()
        require(sessions.size==requests.size&&sessions.map {it.requestId}.toSet()==requests.map {it.requestId}.toSet())
        for(row in sessions) {
            val timeline=timeline(row.scope())
            val request=timeline.requests.single {it.requestId==row.requestId}
            saveRequest(request.interrupted());save(row,PlaySessions.recover(row.value()))
        }
    }
}
