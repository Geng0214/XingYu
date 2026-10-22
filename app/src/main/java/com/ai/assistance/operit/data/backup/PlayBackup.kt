package com.ai.assistance.operit.data.backup

import com.ai.assistance.operit.data.db.*
import com.ai.assistance.operit.data.model.*

internal object PlayBackup {
    fun requireLegacyStates(s:BackupSnapshot,version:Int) {
        fun allowed(state:PlayState)=when(state){is AdventurePlayState->version>=28;is XiangqiPlayState->version>=27;is WordPlayState,is CardPlayState->version>=26;is JourneyState->version>=if(state.outfits==null)25 else 29;is DateStoryState->state.outfits==null||version>=29;else->true}
        require(s.rows("play_sessions").all {allowed(PlayStates.decode(it.text("stateJson")))})
        require(s.rows("play_requests").all {allowed(PlayStates.decode(it.text("beforeState")))})
    }
    fun session(r:BackupRow)=PlaySessionRow(r.text("id"),r.text("roleId"),r.text("spaceId"),r.text("chatId"),r.text("title"),
        r.text("stateJson"),r.number("version"),r.text("status"),r["requestId"] as String?,r.number("createdAt"),r.number("updatedAt"))
    fun request(r:BackupRow)=PlayRequestRow(r.text("requestId"),r.text("activityId"),r.text("roleId"),r.text("spaceId"),r.text("chatId"),
        r.number("activityVersion"),r.number("roleVersion"),r.text("configId"),r.number("configVersion"),r.text("provider"),r.text("model"),r.text("assistantId"),
        r.text("beforeState"),r.text("contextJson"),r.number("createdAt"),MessageState.valueOf(r.text("state")),r.text("content"),r.text("errorCode"),r.number("firstTokenMs"),r.number("elapsedMs"))
    fun validate(s:BackupSnapshot)=try {
        val roles=s.rows("roles").map {RoleAccessScope(it.text("id"),it.text("spaceId"))}.toSet()
        val chats=s.rows("chats").associate {it.text("id") to it.text("roleId")}
        val sessions=s.rows("play_sessions").map(::session)
        val requests=s.rows("play_requests").map(::request)
        require(requests.map {it.assistantId}.distinct().size==requests.size)
        val byScope=sessions.associateBy {it.scope()}
        require(requests.all {it.scope() in byScope})
        sessions.forEach {row->
            require(row.scope().role in roles&&chats[row.chatId]==row.roleId)
            PlayStorageValidation.validate(row,requests.filter {it.scope()==row.scope()})
        }
    }catch(_:Exception){throw ChatFailure("BACKUP_FORMAT")}
}
