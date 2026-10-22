package com.ai.assistance.operit.data.db

import androidx.room.withTransaction
import com.ai.assistance.operit.api.chat.*
import com.ai.assistance.operit.data.model.*
import java.util.UUID
import kotlinx.coroutines.flow.map

data class GroupTimeline(val definition:GroupDefinition,val messages:List<GroupMessage>)

class GroupRepository(private val db:AppDatabase) {
    private val dao get()=db.groups()
    suspend fun definition(scope:GroupAccessScope):GroupDefinition=db.withTransaction {
        val room=dao.room(scope.groupId,scope.spaceId)?.value()?:throw ChatFailure("ACCESS")
        val members=dao.members(scope.groupId,scope.spaceId).map {it.value()}
        if(members.size!=dao.memberCount(scope.groupId,scope.spaceId))throw ChatFailure("ACCESS")
        GroupDefinition(room,members).also {GroupConversations.validate(it.room,it.members)}
    }
    fun observe(scope:GroupAccessScope)=db.invalidationTracker.createFlow("group_rooms","group_members","group_messages","group_requests").map {
        db.withTransaction {
            if(dao.room(scope.groupId,scope.spaceId)==null)null
            else GroupTimeline(definition(scope),dao.history(scope.groupId,scope.spaceId).map {it.value()})
        }
    }
    suspend fun preview(scope:GroupAccessScope,participantId:String,speakerScope:RoleAccessScope,text:String)=db.withTransaction {
        build(scope,participantId,speakerScope,text,UUID.randomUUID().toString()).snapshot
    }
    private suspend fun choices(input:List<Pair<RoleAccessScope,String>>)=input.map {(scope,config)->
        val role=db.dao().role(scope.roleId)?.takeIf {it.scope()==scope}?:throw ChatFailure("ACCESS")
        GroupMemberChoice(role,db.dao().config(config)?:throw ChatFailure("ACCESS"))
    }
    suspend fun create(title:String,userName:String,selected:List<Pair<RoleAccessScope,String>>)=db.withTransaction {
        if(dao.count()>=128)throw ChatFailure("GROUP_LIMIT")
        val result=GroupConversations.create(title,userName,choices(selected))
        dao.insert(GroupRoomRow.from(result.room));dao.insertMembers(result.members.map(GroupMemberRow::from));result
    }
    suspend fun edit(scope:GroupAccessScope,version:Long,title:String,userName:String,selected:List<Pair<RoleAccessScope,String>>)=db.withTransaction {
        val old=definition(scope)
        if(dao.pending(scope.groupId,scope.spaceId).isNotEmpty())throw ChatFailure("MODEL_GENERATION_BUSY")
        val result=GroupConversations.edit(old,version,title,userName,choices(selected))
        check(dao.update(GroupRoomRow.from(result.room))==1)
        dao.deleteMembers(scope.groupId,scope.spaceId);dao.insertMembers(result.members.map(GroupMemberRow::from));result
    }
    suspend fun delete(scope:GroupAccessScope,version:Long)=db.withTransaction {
        definition(scope)
        if(dao.pending(scope.groupId,scope.spaceId).isNotEmpty())throw ChatFailure("MODEL_GENERATION_BUSY")
        if(dao.delete(scope.groupId,scope.spaceId,version)!=1)throw ChatFailure("GROUP_CHANGED")
    }
    suspend fun retry(scope:GroupAccessScope,requestId:String):Pair<String,String> =db.withTransaction {
        definition(scope)
        val request=dao.request(scope.groupId,scope.spaceId,requestId)?:throw ChatFailure("ACCESS")
        if(request.terminalState=="PENDING")throw ChatFailure("MODEL_GENERATION_BUSY")
        val user=dao.message(request.userId,scope.groupId,scope.spaceId,requestId)?.value()?:throw ChatFailure("ACCESS")
        if(user.kind!="user")throw ChatFailure("ACCESS")
        user.content to request.participantId
    }
    private suspend fun build(scope:GroupAccessScope,participantId:String,speakerScope:RoleAccessScope,text:String,requestId:String):PreparedGroupTurn {
        val definition=definition(scope)
        val member=definition.members.singleOrNull {it.id==participantId&&it.role==speakerScope}?:throw ChatFailure("ACCESS")
        val speaker=db.dao().role(speakerScope.roleId)?.takeIf {it.scope()==speakerScope}?:throw ChatFailure("ACCESS")
        val config=db.dao().config(member.configId)?:throw ChatFailure("ACCESS")
        val history=dao.history(scope.groupId,scope.spaceId).map {it.value()}
        if(history.size>=20000)throw ChatFailure("GROUP_LIMIT")
        val last=history.maxOfOrNull {it.createdAt}?:0L
        if(last>=Long.MAX_VALUE-1)throw ChatFailure("GROUP_INPUT")
        val prepared=GroupRequestContext.prepare(definition.room,definition.members,participantId,speaker,config,history,text,requestId,
            UUID.randomUUID().toString(),UUID.randomUUID().toString(),maxOf(System.currentTimeMillis(),last+1))
        return prepared
    }
    suspend fun prepare(scope:GroupAccessScope,participantId:String,speakerScope:RoleAccessScope,text:String,requestId:String)=db.withTransaction {
        val prepared=build(scope,participantId,speakerScope,text,requestId)
        val snapshot=prepared.snapshot
        val config=snapshot.config
        val speaker=snapshot.role
        val binding=requireNotNull(snapshot.group)
        dao.insert(GroupRequestRow(requestId,scope.groupId,scope.spaceId,binding.version,participantId,speaker.id,speaker.spaceId,speaker.version,
            config.id,config.version,config.provider,config.model,prepared.user.id,prepared.assistant.id,prepared.assistant.createdAt))
        dao.insertMessages(listOf(prepared.user,prepared.assistant).map(GroupMessageRow::from));snapshot
    }
    private suspend fun active(snapshot:RequestSnapshot):Triple<GroupDefinition,GroupRequestRow,GroupMessage> {
        val scope=snapshot.group?.group?:throw ChatFailure("ACCESS")
        val definition=definition(scope)
        val request=dao.request(scope.groupId,scope.spaceId,snapshot.requestId)?:throw ChatFailure("ACCESS")
        val message=dao.message(snapshot.assistantId,scope.groupId,scope.spaceId,snapshot.requestId)?.value()?:throw ChatFailure("ACCESS")
        if(!request.matches(snapshot)||!GroupConversations.accepts(definition.room,definition.members,message,snapshot))throw ChatFailure("ACCESS")
        return Triple(definition,request,message)
    }
    suspend fun update(snapshot:RequestSnapshot,text:String,state:MessageState,error:String,first:Long,elapsed:Long)=db.withTransaction {
        val (definition,request,message)=active(snapshot)
        val result=GroupConversations.update(definition.room,definition.members,message,snapshot,text,state,error,first,elapsed)
        check(dao.update(GroupMessageRow.from(result))==1)
        if(state !in activeStates) {
            check(dao.update(request.finish(state,error))==1)
        }
    }
    /** Called in the same transaction as role deletion. Pending rows cannot accept later network output. */
    suspend fun removeRole(scope:RoleAccessScope)=db.withTransaction {
        for(room in dao.forRole(scope.roleId,scope.spaceId)) {
            terminate(dao.pending(room.groupId,room.spaceId),"GROUP_CHANGED")
            if(room.version==Long.MAX_VALUE)throw ChatFailure("GROUP_INPUT")
            check(dao.update(room.copy(version=room.version+1))==1)
            dao.deleteMember(room.groupId,room.spaceId,scope.roleId,scope.spaceId)
        }
    }
    suspend fun recover()=db.withTransaction {terminate(dao.pending(),"INTERRUPTED")}
    private suspend fun terminate(requests:List<GroupRequestRow>,error:String) {
        for(request in requests) {
            val message=dao.message(request.assistantId,request.groupId,request.spaceId,request.requestId)?:throw ChatFailure("STORAGE")
            if(message.state !in activeStates)throw ChatFailure("STORAGE")
            check(dao.update(message.copy(state=MessageState.FAILED,errorCode=error))==1)
            check(dao.update(request.finish(MessageState.FAILED,error))==1)
        }
    }
}

/** Bind the group and selected membership before passing a send into the shared generation coordinator. */
class GroupRequestStore(private val repository:GroupRepository,private val scope:GroupAccessScope,private val participantId:String):RequestStore {
    override suspend fun prepare(scope:RoleAccessScope,chatId:String,text:String,requestId:String):RequestSnapshot {
        if(chatId!=this.scope.groupId)throw ChatFailure("ACCESS")
        return repository.prepare(this.scope,participantId,scope,text,requestId)
    }
    override suspend fun update(snapshot:RequestSnapshot,text:String,state:MessageState,error:String,first:Long,elapsed:Long) {
        checkScope(snapshot);repository.update(snapshot,text,state,error,first,elapsed)
    }
    override suspend fun complete(snapshot:RequestSnapshot,text:String,state:MessageState,error:String,first:Long,elapsed:Long) {
        checkScope(snapshot);repository.update(snapshot,text,state,error,first,elapsed)
    }
    private fun checkScope(snapshot:RequestSnapshot){if(snapshot.group?.group!=scope||snapshot.group.participantId!=participantId)throw ChatFailure("ACCESS")}
}
