package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName="message_reactions",
    primaryKeys=["messageId","side","emoji"],
    foreignKeys=[ForeignKey(entity=MessageEntity::class,parentColumns=["id"],childColumns=["messageId"],onDelete=ForeignKey.CASCADE)],
    indices=[Index("chatId")]
)
data class MessageReaction(
    val messageId:String,
    val side:String,
    val emoji:String,
    val chatId:String,
    val createdAt:Long
)

object Reactions {
    const val USER="user"
    const val ROLE="role"
    const val MAX_PER_SIDE=3
    val ROLE_EMOJI=listOf("😊","🥰","💗","✨","👍","🤗")

    fun validSide(value:String)=value==USER||value==ROLE

    fun countFor(list:List<MessageReaction>,side:String)=list.count {it.side==side}

    fun canAdd(list:List<MessageReaction>,side:String)=countFor(list,side)<MAX_PER_SIDE

    /** 同一侧同一表情再点一次即取消；该侧已满三个时拒绝新增，返回原列表。 */
    fun toggle(list:List<MessageReaction>,messageId:String,chatId:String,side:String,emoji:String,now:Long):List<MessageReaction> {
        require(validSide(side)){"Invalid reaction side"}
        require(emoji in StickerAssets.EMOJI){"Unsupported reaction"}
        val existing=list.firstOrNull {it.messageId==messageId&&it.side==side&&it.emoji==emoji}
        if(existing!=null)return list-existing
        if(!canAdd(list,side))return list
        return list+MessageReaction(messageId,side,emoji,chatId,now)
    }

    /** 助手回复完成后按消息 ID 取一个固定表情，同一消息结果稳定。 */
    fun roleReactionFor(messageId:String):String {
        val hash=messageId.fold(0){acc,c->acc*31+c.code}
        return ROLE_EMOJI[Math.floorMod(hash,ROLE_EMOJI.size)]
    }
}
