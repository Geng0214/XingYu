package com.ai.assistance.operit.data.db

import androidx.room.*
import com.ai.assistance.operit.api.chat.*
import com.ai.assistance.operit.data.model.*
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName="play_sessions",foreignKeys=[
    ForeignKey(entity=RoleEntity::class,parentColumns=["id","spaceId"],childColumns=["roleId","spaceId"],onDelete=ForeignKey.CASCADE),
    ForeignKey(entity=ChatEntity::class,parentColumns=["id","roleId"],childColumns=["chatId","roleId"],onDelete=ForeignKey.CASCADE)],
    indices=[Index("roleId","spaceId"),Index("chatId","roleId"),Index(value=["id","roleId","spaceId","chatId"],unique=true)])
data class PlaySessionRow(@PrimaryKey val id:String,val roleId:String,val spaceId:String,val chatId:String,val title:String,
    val stateJson:String,val version:Long,val status:String,val requestId:String?,val createdAt:Long,val updatedAt:Long) {
    fun scope()=PlayAccessScope(RoleAccessScope(roleId,spaceId),chatId,id)
    fun value():PlaySession {
        require(createdAt>=0&&updatedAt>=createdAt)
        return PlaySession(scope(),title,PlayStates.decode(stateJson),version,PlayStatus.valueOf(status),requestId).also(PlaySessions::validate)
    }
    fun changed(session:PlaySession,now:Long):PlaySessionRow {
        require(session.scope==scope());PlaySessions.validate(session)
        return copy(title=session.title,stateJson=session.state.encode(),version=session.version,status=session.status.name,
            requestId=session.requestId,updatedAt=maxOf(now,updatedAt))
    }
    companion object {
        fun from(s:PlaySession,now:Long)=PlaySessionRow(s.scope.activityId,s.scope.role.roleId,s.scope.role.spaceId,s.scope.chatId,s.title,
            s.state.encode(),s.version,s.status.name,s.requestId,now,now).also {it.value()}
    }
}

@Entity(tableName="play_requests",foreignKeys=[ForeignKey(entity=PlaySessionRow::class,
    parentColumns=["id","roleId","spaceId","chatId"],childColumns=["activityId","roleId","spaceId","chatId"],onDelete=ForeignKey.CASCADE)],
    indices=[Index("activityId","roleId","spaceId","chatId"),Index(value=["assistantId"],unique=true)])
data class PlayRequestRow(@PrimaryKey val requestId:String,val activityId:String,val roleId:String,val spaceId:String,val chatId:String,
    val activityVersion:Long,val roleVersion:Long,val configId:String,val configVersion:Long,val provider:String,val model:String,val assistantId:String,
    val beforeState:String,val contextJson:String,val createdAt:Long,
    val state:MessageState=MessageState.QUEUED,val content:String="",val errorCode:String="",val firstTokenMs:Long=-1,val elapsedMs:Long=0) {
    fun scope()=PlayAccessScope(RoleAccessScope(roleId,spaceId),chatId,activityId)
    fun validate() {
        listOf(requestId,activityId,roleId,spaceId,chatId,configId,assistantId).forEach(PlaySessions::id)
        require(activityVersion>1&&roleVersion>0&&configVersion>0&&createdAt>=0&&elapsedMs>=0&&firstTokenMs>= -1&&firstTokenMs<=elapsedMs)
        PlayJson.text(provider,80);PlayJson.text(model,200);PlayJson.text(content,PlayGenerations.OUTPUT_LIMIT,true)
        require(content.toByteArray().size<=PlayGenerations.OUTPUT_LIMIT&&errorCode.length<=80&&errorCode.none(Char::isISOControl))
        if(state!=MessageState.FAILED)require(errorCode.isEmpty())
        require(content.isEmpty()==(firstTokenMs<0))
        val before=PlayStates.decode(beforeState);require(PlaySessions.canRequest(before))
        require(contextJson.toByteArray().size<=MAX_CONTEXT);StrictJson(contextJson).validate()
        val turns=JSONArray(contextJson);require(turns.length()==2)
        for(i in 0..1){val t=turns.getJSONObject(i);PlayJson.keys(t,"role","content");require(PlayJson.string(t,"role")==if(i==0)"system"else"user");PlayJson.text(PlayJson.string(t,"content"),MAX_CONTEXT)}
        if(state==MessageState.COMPLETE)PlayGenerations.decode(before,content)
    }
    fun matches(f:FrozenPlayTurn):Boolean {
        val s=f.snapshot
        return state in activeStates&&s.play==PlayRequestIdentity(scope(),activityVersion)&&s.requestId==requestId&&s.assistantId==assistantId&&
            s.role.scope()==scope().role&&s.role.version==roleVersion&&s.config.id==configId&&s.config.version==configVersion&&
            s.config.provider==provider&&s.config.model==model&&f.confirmedState==beforeState&&context(s)==contextJson
    }
    fun progress(next:PlayProgress):PlayRequestRow {
        validate();require(state in activeStates)
        val allowed=when(state){MessageState.QUEUED->setOf(MessageState.WAITING,MessageState.CANCELLED,MessageState.FAILED)
            MessageState.WAITING->setOf(MessageState.WAITING,MessageState.GENERATING,MessageState.COMPLETE,MessageState.CANCELLED,MessageState.FAILED)
            MessageState.GENERATING->setOf(MessageState.GENERATING,MessageState.COMPLETE,MessageState.CANCELLED,MessageState.FAILED)
            else->emptySet()}
        require(next.state in allowed&&next.text.startsWith(content)&&next.elapsedMs>=elapsedMs)
        if(firstTokenMs>=0)require(next.firstTokenMs==firstTokenMs)
        return copy(state=next.state,content=next.text,errorCode=next.errorCode,firstTokenMs=next.firstTokenMs,elapsedMs=next.elapsedMs).also {it.validate()}
    }
    fun interrupted()=copy(state=MessageState.FAILED,errorCode="INTERRUPTED").also {require(state in activeStates);it.validate()}
    companion object {
        const val MAX_CONTEXT=524288
        fun context(s:RequestSnapshot)=JSONArray().apply {s.turns.forEach {put(JSONObject().put("role",it.role).put("content",it.content))}}.toString()
        fun from(f:FrozenPlayTurn,now:Long):PlayRequestRow {
            val s=f.snapshot;val b=requireNotNull(s.play)
            return PlayRequestRow(s.requestId,b.scope.activityId,b.scope.role.roleId,b.scope.role.spaceId,b.scope.chatId,b.version,s.role.version,
                s.config.id,s.config.version,s.config.provider,s.config.model,s.assistantId,f.confirmedState,context(s),now).also {it.validate()}
        }
    }
}

@Dao interface PlayDao {
    @Query("SELECT a.* FROM play_sessions a JOIN roles r ON r.id=a.roleId AND r.spaceId=a.spaceId JOIN chats c ON c.id=a.chatId AND c.roleId=r.id WHERE a.id=:id AND a.roleId=:role AND a.spaceId=:space AND a.chatId=:chat")
    suspend fun session(role:String,space:String,chat:String,id:String):PlaySessionRow?
    @Query("SELECT a.* FROM play_sessions a JOIN roles r ON r.id=a.roleId AND r.spaceId=a.spaceId JOIN chats c ON c.id=a.chatId AND c.roleId=r.id WHERE a.roleId=:role AND a.spaceId=:space AND a.chatId=:chat ORDER BY a.updatedAt DESC,a.id")
    suspend fun sessions(role:String,space:String,chat:String):List<PlaySessionRow>
    @Query("SELECT a.* FROM play_sessions a JOIN roles r ON r.id=a.roleId AND r.spaceId=a.spaceId JOIN chats c ON c.id=a.chatId AND c.roleId=r.id WHERE a.roleId=:role AND a.spaceId=:space ORDER BY a.updatedAt DESC,a.id LIMIT 50")
    suspend fun recentForRole(role:String,space:String):List<PlaySessionRow>
    @Query("SELECT q.* FROM play_requests q JOIN play_sessions a ON a.id=q.activityId AND a.roleId=q.roleId AND a.spaceId=q.spaceId AND a.chatId=q.chatId JOIN roles r ON r.id=a.roleId AND r.spaceId=a.spaceId JOIN chats c ON c.id=a.chatId AND c.roleId=r.id WHERE a.id=:id AND a.roleId=:role AND a.spaceId=:space AND a.chatId=:chat ORDER BY q.activityVersion,q.requestId")
    suspend fun requests(role:String,space:String,chat:String,id:String):List<PlayRequestRow>
    @Query("SELECT * FROM play_requests WHERE roleId=:role AND spaceId=:space AND chatId=:chat AND activityId=:id ORDER BY activityVersion DESC,requestId DESC LIMIT 20")
    suspend fun recent(role:String,space:String,chat:String,id:String):List<PlayRequestRow>
    @Query("SELECT COUNT(*) FROM play_requests WHERE roleId=:role AND spaceId=:space AND chatId=:chat AND activityId=:id")
    suspend fun requestCount(role:String,space:String,chat:String,id:String):Int
    @Query("SELECT q.* FROM play_requests q JOIN play_sessions a ON a.id=q.activityId AND a.roleId=q.roleId AND a.spaceId=q.spaceId AND a.chatId=q.chatId JOIN roles r ON r.id=a.roleId AND r.spaceId=a.spaceId JOIN chats c ON c.id=a.chatId AND c.roleId=r.id WHERE a.id=:id AND a.roleId=:role AND a.spaceId=:space AND a.chatId=:chat AND q.requestId=:request")
    suspend fun request(role:String,space:String,chat:String,id:String,request:String):PlayRequestRow?
    @Query("SELECT * FROM play_sessions WHERE requestId IS NOT NULL") suspend fun pendingSessions():List<PlaySessionRow>
    @Query("SELECT * FROM play_requests WHERE state IN ('QUEUED','WAITING','GENERATING')") suspend fun pendingRequests():List<PlayRequestRow>
    @Insert suspend fun insert(row:PlaySessionRow)
    @Insert suspend fun insert(row:PlayRequestRow)
    @Query("UPDATE play_requests SET state=:state,content=:text,errorCode=:error,firstTokenMs=:first,elapsedMs=:elapsed WHERE requestId=:request AND activityId=:id AND roleId=:role AND spaceId=:space AND chatId=:chat AND state IN ('QUEUED','WAITING','GENERATING') AND EXISTS (SELECT 1 FROM play_sessions a WHERE a.id=:id AND a.roleId=:role AND a.spaceId=:space AND a.chatId=:chat AND a.requestId=:request AND a.version=play_requests.activityVersion)")
    suspend fun updateRequest(role:String,space:String,chat:String,id:String,request:String,state:MessageState,text:String,error:String,first:Long,elapsed:Long):Int
    @Query("UPDATE play_sessions SET title=:title,stateJson=:json,version=:next,status=:status,requestId=:request,updatedAt=:now WHERE id=:id AND roleId=:role AND spaceId=:space AND chatId=:chat AND version=:previous")
    suspend fun updateSession(role:String,space:String,chat:String,id:String,previous:Long,title:String,json:String,next:Long,status:String,request:String?,now:Long):Int
    @Query("DELETE FROM play_sessions WHERE id=:id AND roleId=:role AND spaceId=:space AND chatId=:chat AND version=:version AND requestId IS NULL")
    suspend fun delete(role:String,space:String,chat:String,id:String,version:Long):Int
}
