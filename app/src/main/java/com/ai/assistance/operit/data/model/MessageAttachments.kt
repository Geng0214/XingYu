package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import java.security.MessageDigest

data class PromptImage(val mimeType:String,val width:Int,val height:Int,val sha256:String,val data:ByteArray) {
    fun validate() {
        require(mimeType==MessageImages.MIME_TYPE)
        require(width in 1..MessageImages.MAX_EDGE&&height in 1..MessageImages.MAX_EDGE)
        require(data.isNotEmpty()&&data.size<=MessageImages.MAX_STORED)
        require(sha256==MessageImages.sha256(data))
    }
}

@Entity(tableName="message_attachments",
    foreignKeys=[ForeignKey(entity=MessageEntity::class,parentColumns=["id"],childColumns=["messageId"],onDelete=ForeignKey.CASCADE)],
    indices=[Index("chatId"),Index(value=["messageId","chatId","requestId"],unique=true)])
data class MessageAttachment(
    @androidx.room.PrimaryKey val messageId:String,
    val chatId:String,
    val requestId:String,
    val mimeType:String,
    val width:Int,
    val height:Int,
    val sha256:String,
    val data:ByteArray
) {
    fun promptImage()=PromptImage(mimeType,width,height,sha256,data.copyOf()).also(PromptImage::validate)
}

object MessageImages {
    const val MIME_TYPE="image/jpeg"
    const val MAX_INPUT=20*1024*1024
    const val MAX_STORED=1024*1024
    const val MAX_EDGE=1280
    const val HISTORY_IMAGES=3
    fun sha256(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
}
