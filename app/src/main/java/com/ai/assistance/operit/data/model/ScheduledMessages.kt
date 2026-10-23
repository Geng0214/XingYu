package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName="scheduled_messages",
    foreignKeys=[
        ForeignKey(entity=RoleEntity::class,parentColumns=["id"],childColumns=["roleId"],onDelete=ForeignKey.CASCADE),
        ForeignKey(entity=ChatEntity::class,parentColumns=["id"],childColumns=["chatId"],onDelete=ForeignKey.CASCADE)
    ],
    indices=[Index(value=["roleId","dueAt"]),Index("chatId")]
)
data class ScheduledMessage(
    @PrimaryKey val id:String,
    val roleId:String,
    val spaceId:String,
    val chatId:String,
    val configId:String,
    val configVersion:Long,
    val dueAt:Long,
    val mode:String,
    val content:String,
    val state:String,
    val assistantId:String,
    val errorCode:String="",
    val notificationSent:Boolean=false,
    val createdAt:Long,
    val finishedAt:Long=0
)

object ScheduledMessages {
    const val MODE_TEXT="text"
    const val MODE_GENERATE="generate"
    const val STATE_PENDING="PENDING"
    const val STATE_COMPLETE="COMPLETE"
    const val STATE_CANCELLED="CANCELLED"
    const val STATE_FAILED="FAILED"
    const val MAX_TEXT=4000
    const val MAX_INSTRUCTION=2000
    const val MAX_LEAD_MS=365L*24*60*60*1000

    fun validate(item:ScheduledMessage) {
        require(item.id.isNotBlank()&&item.id.length<=128&&item.id.none(Char::isISOControl))
        require(item.roleId.isNotBlank()&&item.spaceId.isNotBlank()&&item.chatId.isNotBlank())
        require(item.mode==MODE_TEXT||item.mode==MODE_GENERATE)
        require(item.state==STATE_PENDING||item.state==STATE_COMPLETE||item.state==STATE_CANCELLED||item.state==STATE_FAILED)
        require(item.dueAt>0&&item.dueAt-item.createdAt<=MAX_LEAD_MS)
        require(item.content.isNotBlank())
        require(item.content.length<=(if(item.mode==MODE_TEXT)MAX_TEXT else MAX_INSTRUCTION))
        if(item.mode==MODE_GENERATE)require(item.configId.isNotBlank()&&item.configVersion>0)
    }

    fun pending(items:List<ScheduledMessage>)=items.filter {it.state==STATE_PENDING}.sortedBy {it.dueAt}
}
