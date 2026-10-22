package com.ai.assistance.operit.data.backup

import com.ai.assistance.operit.data.db.*
import com.ai.assistance.operit.data.model.*

internal object GroupBackup {
    fun validate(s:BackupSnapshot)=try {
        val rooms=s.rows("group_rooms").map {GroupRoom(GroupAccessScope(it.text("groupId"),it.text("spaceId")),it.text("title"),it.text("userName"),it.number("version"))}
        require(rooms.size<=128&&rooms.map {it.scope.spaceId}.distinct().size==rooms.size)
        val scopes=rooms.map {it.scope}.toSet()
        val roleScopes=s.rows("roles").map {RoleAccessScope(it.text("id"),it.text("spaceId"))}.toSet()
        val configs=s.rows("model_configs").map {it.text("id")}.toSet()
        val members=s.rows("group_members").map {r->GroupParticipant(r.text("id"),GroupAccessScope(r.text("groupId"),r.text("spaceId")),
            RoleAccessScope(r.text("roleId"),r.text("roleSpaceId")),r.text("name"),r.text("configId"))}
        members.forEach {require(it.group in scopes&&it.role in roleScopes&&it.configId in configs)}
        val requests=s.rows("group_requests").map(::request)
        requests.forEach {require(it.scope() in scopes&&it.configId in configs)}
        val messages=s.rows("group_messages").map {r->GroupMessage(r.text("id"),GroupAccessScope(r.text("groupId"),r.text("spaceId")),r.text("requestId"),r.text("kind"),
            r["participantId"] as String?,r.text("speakerName"),r.text("content"),MessageState.valueOf(r.text("state")),r.number("createdAt"),r.text("errorCode"),r.number("firstTokenMs"),r.number("elapsedMs"))}
        messages.forEach {require(it.group in scopes)}
        val owners=mutableMapOf<String,Pair<GroupAccessScope,RoleAccessScope>>()
        members.forEach {owners[it.id]=it.group to it.role}
        // A historical, removed member still has one fixed group and role identity.
        requests.forEach {r->val owner=r.scope() to RoleAccessScope(r.roleId,r.roleSpaceId)
            require(owners[r.participantId]?.let {it==owner}!=false);owners[r.participantId]=owner}
        rooms.forEach {room->GroupStorageValidation.validate(GroupDefinition(room,members.filter {it.group==room.scope}),
            requests.filter {it.scope()==room.scope},messages.filter {it.group==room.scope})}
    }catch(_:ChatFailure){throw ChatFailure("BACKUP_FORMAT")}
    private fun request(r:BackupRow):GroupRequestRow {
        return GroupRequestRow(r.text("requestId"),r.text("groupId"),r.text("spaceId"),r.number("groupVersion"),r.text("participantId"),r.text("roleId"),r.text("roleSpaceId"),r.number("roleVersion"),
            r.text("configId"),r.number("configVersion"),r.text("provider"),r.text("model"),r.text("userId"),r.text("assistantId"),r.number("createdAt"),r.text("terminalState"),r.text("errorCode"))
    }
}
