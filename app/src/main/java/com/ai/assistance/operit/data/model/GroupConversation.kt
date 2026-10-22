package com.ai.assistance.operit.data.model

/** A group is not a role's private space. Rejoining allocates a new participant identity. */
data class GroupAccessScope(val groupId:String,val spaceId:String)
data class GroupRoom(val scope:GroupAccessScope,val title:String,val userName:String="我",val version:Long=1)
data class GroupParticipant(val id:String,val group:GroupAccessScope,val role:RoleAccessScope,
    val name:String,val configId:String)
data class GroupMessage(val id:String,val group:GroupAccessScope,val requestId:String,val kind:String,
    val participantId:String?,val speakerName:String,val content:String,val state:MessageState,val createdAt:Long,
    val errorCode:String="",val firstTokenMs:Long=-1,val elapsedMs:Long=-1)
data class GroupRequestIdentity(val group:GroupAccessScope,val version:Long,val participantId:String)
data class PreparedGroupTurn(val snapshot:RequestSnapshot,val user:GroupMessage,val assistant:GroupMessage)
data class GroupDefinition(val room:GroupRoom,val members:List<GroupParticipant>)
data class GroupMemberChoice(val role:RoleEntity,val config:ModelConfigData)

object GroupConversations {
    const val MAX_MEMBERS=8
    const val MAX_INPUT=12000
    const val MAX_CONTEXT=64000
    const val MAX_OUTPUT=131072
    private fun id(value:String)=value.isNotBlank()&&value.length<=128&&value.none(Char::isISOControl)
    private fun name(value:String,limit:Int)=value.isNotBlank()&&value.length<=limit&&value.none(Char::isISOControl)
    fun validate(scope:GroupAccessScope) {
        if(!id(scope.groupId)||!id(scope.spaceId))throw ChatFailure("GROUP_INPUT")
    }
    fun validate(room:GroupRoom,members:List<GroupParticipant>) {
        validate(room.scope)
        if(!name(room.title,80)||!name(room.userName,40)||room.version<1||members.size>MAX_MEMBERS)throw ChatFailure("GROUP_INPUT")
        if(members.map {it.id}.distinct().size!=members.size||members.map {it.role.roleId}.distinct().size!=members.size)throw ChatFailure("GROUP_INPUT")
        members.forEach {
            if(it.group!=room.scope)throw ChatFailure("ACCESS")
            if(!id(it.id)||!id(it.role.roleId)||!id(it.role.spaceId)||!id(it.configId)||!name(it.name,40))throw ChatFailure("GROUP_INPUT")
        }
    }
    fun validate(message:GroupMessage) {
        validate(message.group)
        if(!id(message.id)||!id(message.requestId)||!name(message.speakerName,40)||message.createdAt<0||message.content.length>MAX_OUTPUT||message.content.contains('\u0000'))throw ChatFailure("GROUP_INPUT")
        if(message.firstTokenMs< -1||message.elapsedMs< -1||message.errorCode.length>80||message.errorCode.any(Char::isISOControl))throw ChatFailure("GROUP_INPUT")
        when(message.kind) {
            "user"->if(message.participantId!=null||message.state!=MessageState.COMPLETE||message.content.isBlank()||message.content.length>MAX_INPUT)throw ChatFailure("GROUP_INPUT")
            "assistant"->if(message.participantId==null||!id(message.participantId)||(message.state==MessageState.COMPLETE&&message.content.isEmpty()))throw ChatFailure("GROUP_INPUT")
            else->throw ChatFailure("GROUP_INPUT")
        }
    }
    /** Validation happens before history filtering: an incomplete foreign row is still forbidden. */
    fun validateHistory(scope:GroupAccessScope,history:List<GroupMessage>) {
        if(history.any {it.group!=scope})throw ChatFailure("ACCESS")
        if(history.map {it.id}.distinct().size!=history.size)throw ChatFailure("GROUP_INPUT")
        history.forEach(::validate)
        history.groupBy {it.requestId}.values.forEach {pair->
            if(pair.size!=2||pair.count {it.kind=="user"}!=1||pair.count {it.kind=="assistant"}!=1)throw ChatFailure("GROUP_INPUT")
            val u=pair.single {it.kind=="user"};val a=pair.single {it.kind=="assistant"}
            if(u.createdAt>=a.createdAt)throw ChatFailure("GROUP_INPUT")
        }
    }
    /** Used by persistent updates too: group, membership generation and exact request must match. */
    fun accepts(room:GroupRoom,members:List<GroupParticipant>,message:GroupMessage,snapshot:RequestSnapshot):Boolean {
        val binding=snapshot.group?:return false
        return snapshot.play==null&&binding.group==room.scope&&binding.version==room.version&&snapshot.chatId==room.scope.groupId&&
            members.any {it.group==room.scope&&it.id==binding.participantId&&it.role==snapshot.scope&&it.configId==snapshot.config.id}&&
            snapshot.role.scope()==snapshot.scope&&message.group==room.scope&&message.id==snapshot.assistantId&&
            message.requestId==snapshot.requestId&&message.participantId==binding.participantId&&
            message.kind=="assistant"&&message.state in activeStates
    }
    fun requirePrivate(snapshot:RequestSnapshot) {if(snapshot.group!=null||snapshot.play!=null)throw ChatFailure("ACCESS")}

    fun create(title:String,userName:String,choices:List<GroupMemberChoice>):GroupDefinition {
        val room=GroupRoom(GroupAccessScope(java.util.UUID.randomUUID().toString(),java.util.UUID.randomUUID().toString()),title,userName)
        return definition(room,emptyList(),choices)
    }
    fun edit(current:GroupDefinition,expectedVersion:Long,title:String,userName:String,choices:List<GroupMemberChoice>):GroupDefinition {
        validate(current.room,current.members)
        if(current.room.version!=expectedVersion)throw ChatFailure("GROUP_CHANGED")
        if(expectedVersion==Long.MAX_VALUE)throw ChatFailure("GROUP_INPUT")
        return definition(current.room.copy(title=title,userName=userName,version=expectedVersion+1),current.members,choices)
    }
    private fun definition(room:GroupRoom,previous:List<GroupParticipant>,choices:List<GroupMemberChoice>):GroupDefinition {
        if(choices.size !in 2..MAX_MEMBERS)throw ChatFailure("GROUP_MEMBERS")
        val members=choices.map {choice->
            val old=previous.singleOrNull {it.role==choice.role.scope()}
            GroupParticipant(old?.id?:java.util.UUID.randomUUID().toString(),room.scope,choice.role.scope(),choice.role.name,choice.config.id)
        }
        validate(room,members)
        return GroupDefinition(room,java.util.Collections.unmodifiableList(members.toList()))
    }
    fun update(room:GroupRoom,members:List<GroupParticipant>,message:GroupMessage,snapshot:RequestSnapshot,
        text:String,state:MessageState,error:String,first:Long,elapsed:Long):GroupMessage {
        if(!accepts(room,members,message,snapshot))throw ChatFailure("ACCESS")
        val next=when(message.state) {
            MessageState.QUEUED->setOf(MessageState.WAITING,MessageState.CANCELLED,MessageState.FAILED)
            MessageState.WAITING->setOf(MessageState.GENERATING,MessageState.COMPLETE,MessageState.CANCELLED,MessageState.FAILED)
            MessageState.GENERATING->setOf(MessageState.GENERATING,MessageState.COMPLETE,MessageState.CANCELLED,MessageState.FAILED)
            else->emptySet()
        }
        if(state !in next||!text.startsWith(message.content)||elapsed<0||first< -1||
            (message.elapsedMs>=0&&elapsed<message.elapsedMs)||
            (message.firstTokenMs>=0&&first!=message.firstTokenMs)||
            (state==MessageState.COMPLETE&&text.isEmpty()))throw ChatFailure("GROUP_INPUT")
        return message.copy(content=text,state=state,errorCode=error,firstTokenMs=first,elapsedMs=elapsed).also(::validate)
    }
}
