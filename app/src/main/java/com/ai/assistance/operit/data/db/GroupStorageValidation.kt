package com.ai.assistance.operit.data.db

import com.ai.assistance.operit.data.model.*

/** Same structural checks for encrypted backup import and persisted group fixtures. */
object GroupStorageValidation {
    fun validate(definition:GroupDefinition,requests:List<GroupRequestRow>,messages:List<GroupMessage>) {
        val room=definition.room
        GroupConversations.validate(room,definition.members);GroupConversations.validateHistory(room.scope,messages)
        require(messages.size<=20000&&requests.size*2==messages.size)
        require(requests.map {it.requestId}.distinct().size==requests.size)
        val byId=messages.associateBy {it.id}
        fun id(v:String)=v.isNotBlank()&&v.length<=128&&v.none(Char::isISOControl)
        requests.forEach {r->
            require(r.scope()==room.scope&&r.groupVersion in 1..room.version&&r.roleVersion>0&&r.configVersion>0)
            require(listOf(r.requestId,r.participantId,r.roleId,r.roleSpaceId,r.configId,r.userId,r.assistantId).all(::id))
            require(r.provider.isNotBlank()&&r.provider.length<=80&&r.model.isNotBlank()&&r.model.length<=200)
            val user=requireNotNull(byId[r.userId]);val assistant=requireNotNull(byId[r.assistantId])
            require(user.kind=="user"&&assistant.kind=="assistant"&&user.requestId==r.requestId&&assistant.requestId==r.requestId)
            require(assistant.participantId==r.participantId&&assistant.createdAt==r.createdAt)
            require(r.terminalState in setOf("PENDING","COMPLETE","FAILED","CANCELLED"))
            if(r.terminalState=="PENDING") {
                require(assistant.state in activeStates&&r.errorCode.isEmpty()&&r.groupVersion==room.version)
                require(definition.members.any {it.id==r.participantId&&it.role==RoleAccessScope(r.roleId,r.roleSpaceId)&&it.configId==r.configId})
            }else require(assistant.state.name==r.terminalState&&assistant.errorCode==r.errorCode)
        }
    }
}
