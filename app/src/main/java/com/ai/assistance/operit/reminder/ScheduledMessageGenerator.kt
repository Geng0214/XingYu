package com.ai.assistance.operit.reminder

import android.content.Context
import androidx.room.withTransaction
import com.ai.assistance.operit.api.chat.ModelGenerationGate
import com.ai.assistance.operit.api.chat.RequestContext
import com.ai.assistance.operit.api.chat.RoomRequestStore
import com.ai.assistance.operit.api.chat.llmprovider.ChatStreamEvent
import com.ai.assistance.operit.api.chat.llmprovider.OpenAIProvider
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.ChatFailure
import com.ai.assistance.operit.data.model.MessageEntity
import com.ai.assistance.operit.data.model.MessageState
import com.ai.assistance.operit.data.model.RequestSnapshot
import com.ai.assistance.operit.data.model.ScheduledMessage
import com.ai.assistance.operit.data.model.ScheduledMessages
import com.ai.assistance.operit.data.security.CredentialVault
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** 到点让角色按指令生成一条消息；与前台聊天共用生成闸门。 */
object ScheduledMessageGenerator {
    suspend fun run(context:Context,item:ScheduledMessage):Boolean {
        val owner="schedule:${item.id}"
        if(!ModelGenerationGate.acquire(owner))return false
        val db=AppDatabase.open(context,CredentialVault(context))
        try {
            val snapshot=prepare(db,item)
            val key=CredentialVault(context).read(snapshot.config.credentialRef,snapshot.config.endpoint)
            val started=System.nanoTime()/1_000_000
            var first=-1L
            var persisted=started
            val content=StringBuilder()
            suspend fun update(state:MessageState,error:String="")=RoomRequestStore(db).update(snapshot,content.toString(),state,error,first,System.nanoTime()/1_000_000-started)
            try {
                update(MessageState.WAITING)
                currentCoroutineContext().ensureActive()
                OpenAIProvider().sendEvents(snapshot,key).collect {event->when(event){
                    is ChatStreamEvent.Text->{content.append(event.value);val clock=System.nanoTime()/1_000_000;if(first<0)first=clock-started;if(clock-persisted>=80){update(MessageState.GENERATING);persisted=clock}}
                    is ChatStreamEvent.Reasoning->Unit
                    is ChatStreamEvent.RoundEnd->Unit
                }}
                RoomRequestStore(db).complete(snapshot,content.toString(),MessageState.COMPLETE,"",first,System.nanoTime()/1_000_000-started)
                db.scheduled().finish(item.id,ScheduledMessages.STATE_COMPLETE,"",System.currentTimeMillis())
            } catch(e:CancellationException) {
                withContext(NonCancellable) {
                    RoomRequestStore(db).complete(snapshot,content.toString(),MessageState.CANCELLED,"",first,System.nanoTime()/1_000_000-started)
                    db.scheduled().finish(item.id,ScheduledMessages.STATE_CANCELLED,"",System.currentTimeMillis())
                }
                throw e
            } catch(e:Exception) {
                val code=(e as? ChatFailure)?.code?:"STORAGE"
                withContext(NonCancellable) {
                    RoomRequestStore(db).complete(snapshot,content.toString(),MessageState.FAILED,code,first,System.nanoTime()/1_000_000-started)
                    db.scheduled().finish(item.id,ScheduledMessages.STATE_FAILED,code,System.currentTimeMillis())
                }
            }
            return true
        } finally {
            ModelGenerationGate.release(owner)
            db.close()
        }
    }

    private suspend fun prepare(db:AppDatabase,item:ScheduledMessage):RequestSnapshot=db.withTransaction {
        val role=db.dao().role(item.roleId)?.takeIf {it.spaceId==item.spaceId&&!it.isRaw()}?:throw ChatFailure("SCHEDULE_ROLE")
        val chat=db.dao().chat(item.roleId,item.spaceId,item.chatId)?:throw ChatFailure("ACCESS")
        val config=db.dao().config(item.configId)?:throw ChatFailure("SCHEDULE_CONFIG")
        val history=db.dao().history(item.roleId,item.spaceId,item.chatId)
        val scope=role.scope()
        val now=System.currentTimeMillis()
        val resolved=RequestContext.resolve(scope,role,chat,history,item.content,db.worldbook().read(scope.roleId,scope.spaceId),
            db.relationships().read(scope.roleId,scope.spaceId),db.dao().historyAttachments(scope.roleId,scope.spaceId,chat.id),null,
            db.dao().historyMaterials(scope.roleId,scope.spaceId,chat.id),null,db.dao().historyStickers(scope.roleId,scope.spaceId,chat.id),null,false,
            db.memories().read(scope.roleId,scope.spaceId),db.environment().profile(scope.roleId,scope.spaceId),now,
            plannerEntries=db.planner().readPlanner(scope.roleId,scope.spaceId),cycleRecords=db.planner().readCycles(scope.roleId,scope.spaceId))
        val requestId=UUID.randomUUID().toString()
        val assistantId=UUID.randomUUID().toString()
        val createdAt=maxOf(now,(history.maxOfOrNull {it.createdAt}?:0L)+1)
        db.dao().insertMessages(listOf(MessageEntity(assistantId,chat.id,requestId,"assistant","",MessageState.QUEUED,createdAt,bubbleStyle=role.effectiveReplyStyle())))
        RequestSnapshot(requestId,chat.id,assistantId,role.copy(),config.copy(),resolved.turns.toList(),scope,emptyList(),resolved.worldbook,
            resolved.relationships,resolved.memories,resolved.environment,resolved.planner)
    }
}
