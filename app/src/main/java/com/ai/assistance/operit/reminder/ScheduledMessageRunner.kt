package com.ai.assistance.operit.reminder

import android.content.Context
import androidx.room.withTransaction
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.MessageEntity
import com.ai.assistance.operit.data.model.MessageState
import com.ai.assistance.operit.data.model.ScheduledMessages
import com.ai.assistance.operit.data.security.CredentialVault
import java.util.UUID

/** 到点执行一条定时消息：固定文本直接落库，生成模式交给模型。 */
object ScheduledMessageRunner {
    suspend fun run(context:Context,id:String) {
        val reader=AppDatabase.open(context,CredentialVault(context))
        val item=try {reader.scheduled().byId(id)} finally {reader.close()}
        if(item==null||item.state!=ScheduledMessages.STATE_PENDING)return
        if(item.mode==ScheduledMessages.MODE_GENERATE) {
            ScheduledMessageGenerator.run(context,item)
            return
        }
        val db=AppDatabase.open(context,CredentialVault(context))
        try {
            db.withTransaction {
                val current=db.scheduled().byId(id) ?: return@withTransaction
                if(current.state!=ScheduledMessages.STATE_PENDING)return@withTransaction
                val chat=db.dao().chat(current.roleId,current.spaceId,current.chatId)
                if(chat==null) {
                    db.scheduled().finish(id,ScheduledMessages.STATE_FAILED,"ACCESS",System.currentTimeMillis())
                    return@withTransaction
                }
                val now=maxOf(System.currentTimeMillis(),chat.createdAt)
                db.dao().insertMessages(listOf(MessageEntity(UUID.randomUUID().toString(),current.chatId,current.id,"assistant",current.content,MessageState.COMPLETE,now)))
                db.scheduled().finish(id,ScheduledMessages.STATE_COMPLETE,"",now)
            }
        } finally {db.close()}
    }
}
