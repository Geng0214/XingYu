package com.ai.assistance.operit.reminder

import android.content.Context
import androidx.room.withTransaction
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.MessageEntity
import com.ai.assistance.operit.data.model.MessageState
import com.ai.assistance.operit.data.model.ScheduledMessages
import com.ai.assistance.operit.data.security.CredentialVault
import java.util.UUID

/** 到点把一条定时消息写进聊天；固定文本模式不联网。 */
object ScheduledMessageRunner {
    suspend fun run(context:Context,id:String) {
        val db=AppDatabase.open(context,CredentialVault(context))
        try {
            db.withTransaction {
                val item=db.scheduled().byId(id) ?: return@withTransaction
                if(item.state!=ScheduledMessages.STATE_PENDING)return@withTransaction
                if(item.mode!=ScheduledMessages.MODE_TEXT) {
                    db.scheduled().finish(id,ScheduledMessages.STATE_FAILED,"SCHEDULE_MODE",System.currentTimeMillis())
                    return@withTransaction
                }
                val chat=db.dao().chat(item.roleId,item.spaceId,item.chatId)
                if(chat==null) {
                    db.scheduled().finish(id,ScheduledMessages.STATE_FAILED,"ACCESS",System.currentTimeMillis())
                    return@withTransaction
                }
                val now=maxOf(System.currentTimeMillis(),chat.createdAt)
                db.dao().insertMessages(listOf(MessageEntity(UUID.randomUUID().toString(),item.chatId,item.id,"assistant",item.content,MessageState.COMPLETE,now)))
                db.scheduled().finish(id,ScheduledMessages.STATE_COMPLETE,"",now)
            }
        } finally {db.close()}
    }
}
