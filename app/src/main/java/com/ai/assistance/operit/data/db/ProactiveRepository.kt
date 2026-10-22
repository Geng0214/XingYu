package com.ai.assistance.operit.data.db

import androidx.room.withTransaction
import com.ai.assistance.operit.api.chat.RequestContext
import com.ai.assistance.operit.api.chat.RoomRequestStore
import com.ai.assistance.operit.data.model.*
import java.time.ZoneId
import java.util.UUID

class ProactiveRepository(private val db:AppDatabase) {
    suspend fun save(scope:RoleAccessScope,enabled:Boolean,chatId:String,configId:String,quietStart:Int,quietEnd:Int,
        intervalHours:Int,dailyLimit:Int,expectedVersion:Long?):ProactiveRoleSetting=db.withTransaction {
        val role=db.dao().role(scope.roleId)?.takeIf {it.spaceId==scope.spaceId}?:throw ChatFailure("ACCESS")
        if(role.isRaw())throw ChatFailure("PROACTIVE_CONFIG")
        val chat=db.dao().chat(scope.roleId,scope.spaceId,chatId)?:throw ChatFailure("ACCESS")
        val config=db.dao().config(configId)?:throw ChatFailure("PROACTIVE_CONFIG")
        if(chat.roleId!=role.id)throw ChatFailure("PROACTIVE_CONFIG")
        val old=db.proactive().setting(scope.roleId,scope.spaceId)
        if(expectedVersion!=old?.version)throw ChatFailure("PROACTIVE_CONFIG")
        val now=System.currentTimeMillis().coerceAtLeast(0)
        val setting=ProactiveRoleSetting(role.id,role.spaceId,enabled,chat.id,config.id,quietStart,quietEnd,intervalHours,dailyLimit,
            nextEligibleAt=if(enabled&&!old?.enabled.orFalse())now else old?.nextEligibleAt?:now,
            version=(old?.version?:0)+1,updatedAt=now)
        try {ProactiveMessages.validate(setting)}catch(_:IllegalArgumentException){throw ChatFailure("PROACTIVE_CONFIG")}
        db.proactive().saveSetting(setting)
        setting
    }

    suspend fun setEnabled(scope:RoleAccessScope,enabled:Boolean):ProactiveRoleSetting=db.withTransaction {
        val old=db.proactive().setting(scope.roleId,scope.spaceId)?:throw ChatFailure("PROACTIVE_CONFIG")
        save(scope,enabled,old.chatId,old.configId,old.quietStartMinute,old.quietEndMinute,old.minIntervalHours,old.dailyLimit,old.version)
    }

    data class Prepared(val snapshot:RequestSnapshot,val request:ProactiveRequestEntity,val reservedVersion:Long,val reservedNextAt:Long)

    suspend fun prepare(setting:ProactiveRoleSetting,globalDailyLimit:Int,now:Long,zone:ZoneId):Prepared=db.withTransaction {
        val current=db.proactive().setting(setting.roleId,setting.spaceId)?:throw ChatFailure("PROACTIVE_CONFIG")
        if(current!=setting)throw ChatFailure("PROACTIVE_CONFIG")
        val role=db.dao().role(setting.roleId)?.takeIf {it.spaceId==setting.spaceId&&!it.isRaw()}?:throw ChatFailure("PROACTIVE_CONFIG")
        val chat=db.dao().chat(setting.roleId,setting.spaceId,setting.chatId)?:throw ChatFailure("PROACTIVE_CONFIG")
        val config=db.dao().config(setting.configId)?:throw ChatFailure("PROACTIVE_CONFIG")
        val history=db.dao().history(setting.roleId,setting.spaceId,setting.chatId)
        val trigger="请结合我们已有的对话和你现在知道的事情，像熟悉的人一样主动发来一条自然、简短、不重复的消息。不要提到这是定时任务，不要声称做过现实世界中没有发生的事，也不要调用工具。"
        val scope=role.scope()
        val resolved=RequestContext.resolve(scope,role,chat,history,trigger,db.worldbook().read(scope.roleId,scope.spaceId),
            db.relationships().read(scope.roleId,scope.spaceId),db.dao().historyAttachments(scope.roleId,scope.spaceId,chat.id),null,
            db.dao().historyMaterials(scope.roleId,scope.spaceId,chat.id),null,db.dao().historyStickers(scope.roleId,scope.spaceId,chat.id),null,false,
            db.memories().read(scope.roleId,scope.spaceId),db.environment().profile(scope.roleId,scope.spaceId),now,
            plannerEntries=db.planner().readPlanner(scope.roleId,scope.spaceId),cycleRecords=db.planner().readCycles(scope.roleId,scope.spaceId))
        val requestId=UUID.randomUUID().toString();val assistantId=UUID.randomUUID().toString()
        val snapshot=RequestSnapshot(requestId,chat.id,assistantId,role.copy(),config.copy(),resolved.turns.toList(),scope,emptyList(),resolved.worldbook,
            resolved.relationships,resolved.memories,resolved.environment,resolved.planner,ProactiveMessages.MAX_OUTPUT_TOKENS)
        val dayKey=ProactiveMessages.dayKey(now,zone)
        val counts=ProactiveCounts(db.proactive().roleDayCount(scope.roleId,scope.spaceId,dayKey))
        if(!ProactiveMessages.eligible(setting,counts,db.proactive().globalDayCount(dayKey),globalDailyLimit,now,zone))throw ChatFailure("PROACTIVE_LIMIT")
        val dueAt=setting.nextEligibleAt
        val nextAt=ProactiveMessages.nextEligibleAt(now,setting.minIntervalHours)
        if(db.proactive().reserveWindow(scope.roleId,scope.spaceId,setting.version,nextAt,now)!=1)throw ChatFailure("PROACTIVE_CONFIG")
        val createdAt=maxOf(now,(history.maxOfOrNull {it.createdAt}?:0L)+1)
        db.dao().insertMessages(listOf(MessageEntity(assistantId,chat.id,requestId,"assistant","",MessageState.QUEUED,createdAt,bubbleStyle=role.effectiveReplyStyle())))
        val request=ProactiveRequestEntity(requestId,scope.roleId,scope.spaceId,chat.id,assistantId,config.id,config.version,dueAt,dayKey,createdAt=createdAt)
        db.proactive().insertRequest(request)
        Prepared(snapshot,request,setting.version+1,nextAt)
    }

    suspend fun discardBeforeNetwork(prepared:Prepared)=db.withTransaction {
        val request=prepared.request
        val removed=db.proactive().deletePendingRequest(request.requestId,request.roleId,request.spaceId,request.chatId,request.assistantId)
        if(removed==0)return@withTransaction
        if(db.dao().deleteActive(request.roleId,request.spaceId,request.chatId,request.requestId,request.assistantId)!=1)throw ChatFailure("STORAGE")
        db.proactive().releaseWindow(request.roleId,request.spaceId,prepared.reservedVersion,prepared.reservedNextAt,request.dueAt,System.currentTimeMillis().coerceAtLeast(request.createdAt))
    }

    suspend fun finish(prepared:Prepared,text:String,state:MessageState,error:String,first:Long,elapsed:Long,notified:Boolean)=db.withTransaction {
        RoomRequestStore(db).complete(prepared.snapshot,text,state,error,first,elapsed)
        if(db.proactive().finish(prepared.request.requestId,prepared.request.roleId,prepared.request.spaceId,prepared.request.chatId,prepared.request.assistantId,state.name,error,notified,System.currentTimeMillis().coerceAtLeast(prepared.request.createdAt))!=1)throw ChatFailure("STORAGE")
    }

    private fun Boolean?.orFalse()=this==true
}
