package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.security.MessageDigest

data class PromptSticker(
    val sourceAssetId:String?,
    val displayName:String,
    val mimeType:String,
    val width:Int,
    val height:Int,
    val sha256:String,
    val data:ByteArray
) {
    fun validate() {
        require(sourceAssetId==null||(sourceAssetId.isNotBlank()&&sourceAssetId.length<=128))
        StickerAssets.validateName(displayName)
        require(mimeType==StickerAssets.MIME_TYPE)
        require(width in 1..StickerAssets.MAX_EDGE&&height in 1..StickerAssets.MAX_EDGE)
        require(data.isNotEmpty()&&data.size<=StickerAssets.MAX_STORED)
        require(sha256==StickerAssets.sha256(data))
    }
    fun label()="[用户发送了表情：$displayName]"
    fun promptImage()=PromptImage(mimeType,width,height,sha256,data.copyOf()).also(PromptImage::validate)
}

@Entity(
    tableName="sticker_assets",
    foreignKeys=[ForeignKey(entity=RoleEntity::class,parentColumns=["id"],childColumns=["roleId"],onDelete=ForeignKey.CASCADE)],
    indices=[Index(value=["roleId","spaceId"])]
)
data class StickerAsset(
    @PrimaryKey val id:String,
    val roleId:String,
    val spaceId:String,
    val displayName:String,
    val mimeType:String,
    val width:Int,
    val height:Int,
    val sha256:String,
    val data:ByteArray,
    val favorite:Boolean=false,
    val createdAt:Long=System.currentTimeMillis()
) {
    fun promptSticker()=PromptSticker(id,displayName,mimeType,width,height,sha256,data.copyOf()).also(PromptSticker::validate)
}

@Entity(
    tableName="message_stickers",
    foreignKeys=[ForeignKey(entity=MessageEntity::class,parentColumns=["id"],childColumns=["messageId"],onDelete=ForeignKey.CASCADE)],
    indices=[Index("chatId"),Index(value=["messageId","chatId","requestId"],unique=true)]
)
data class MessageSticker(
    @PrimaryKey val messageId:String,
    val chatId:String,
    val requestId:String,
    val displayName:String,
    val mimeType:String,
    val width:Int,
    val height:Int,
    val sha256:String,
    val data:ByteArray
) {
    fun promptSticker()=PromptSticker(null,displayName,mimeType,width,height,sha256,data.copyOf()).also(PromptSticker::validate)
}

object StickerAssets {
    const val MIME_TYPE=MessageImages.MIME_TYPE
    const val MAX_EDGE=768
    const val MAX_STORED=384*1024
    const val MAX_PER_ROLE=64
    const val HISTORY_VISUALS=3
    const val MAX_NAME=40
    val EMOJI=listOf("😊","🥰","😂","😭","🥺","😳","😴","🤔","😤","😎","🙈","💗","💕","✨","🌸","🎉","👍","🤗")

    fun validateName(value:String) {
        require(value.isNotBlank()&&value.length<=MAX_NAME&&value.none(Char::isISOControl))
    }
    fun sha256(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
}
