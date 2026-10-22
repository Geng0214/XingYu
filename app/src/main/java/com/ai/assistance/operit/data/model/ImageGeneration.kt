package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName="image_generation_configs")
data class ImageGenerationConfig(
    @PrimaryKey val id:String,
    val name:String,
    val endpoint:String,
    val model:String,
    val timeoutSeconds:Int,
    val credentialRef:String,
    val version:Long=1,
    val size:String="1024x1024"
)

@Entity(
    tableName="generated_images",
    foreignKeys=[ForeignKey(entity=ChatEntity::class,parentColumns=["id"],childColumns=["chatId"],onDelete=ForeignKey.CASCADE)],
    indices=[Index("chatId"),Index(value=["roleId","spaceId"])]
)
data class GeneratedImage(
    @PrimaryKey val id:String,
    val roleId:String,
    val spaceId:String,
    val chatId:String,
    val configId:String,
    val configVersion:Long,
    val model:String,
    val prompt:String,
    val requestedSize:String,
    val state:MessageState,
    val errorCode:String="",
    val mimeType:String="",
    val width:Int=0,
    val height:Int=0,
    val sha256:String="",
    val data:ByteArray?=null,
    val createdAt:Long=System.currentTimeMillis(),
    val finishedAt:Long=0,
    val elapsedMs:Long=-1
)

object ImageGenerations {
    const val MIME_TYPE="image/jpeg"
    const val MAX_PROMPT=4000
    const val MAX_JSON=17*1024*1024
    const val MAX_RAW=12*1024*1024
    const val MAX_STORED=2*1024*1024
    const val MAX_EDGE=1536
    val SIZES=setOf("1024x1024","1024x1536","1536x1024")
    val activeStates=setOf(MessageState.QUEUED,MessageState.WAITING,MessageState.GENERATING)

    fun validatePrompt(value:String) {
        require(value.isNotBlank()&&value.length<=MAX_PROMPT)
        require(value.none {it.isISOControl()&&it!='\n'&&it!='\t'})
    }
    fun validateConfig(value:ImageGenerationConfig) {
        require(value.id.isNotBlank()&&value.name.isNotBlank()&&value.name.length<=240)
        require(value.model.isNotBlank()&&value.model.length<=200&&value.timeoutSeconds in 15..300&&value.version>0)
        require(value.size in SIZES&&value.credentialRef.isNotBlank())
    }
    fun validateStored(value:GeneratedImage) {
        validatePrompt(value.prompt)
        require(value.requestedSize in SIZES&&value.configVersion>0&&value.model.isNotBlank()&&value.model.length<=200)
        require(value.createdAt>=0&&value.finishedAt>=0&&value.elapsedMs>=-1&&value.errorCode.length<=64)
        if(value.state in activeStates)require(value.finishedAt==0L&&value.elapsedMs==-1L&&value.errorCode.isEmpty())
        else require(value.finishedAt>0&&value.elapsedMs>=0)
        if(value.state==MessageState.COMPLETE) {
            val bytes=requireNotNull(value.data)
            require(value.errorCode.isEmpty()&&value.mimeType==MIME_TYPE&&value.width in 1..MAX_EDGE&&value.height in 1..MAX_EDGE)
            require(bytes.isNotEmpty()&&bytes.size<=MAX_STORED&&value.sha256==MessageImages.sha256(bytes))
        } else {
            require(value.data==null&&value.mimeType.isEmpty()&&value.width==0&&value.height==0&&value.sha256.isEmpty())
            require(value.state!=MessageState.FAILED||value.errorCode.isNotEmpty())
            require(value.state!=MessageState.CANCELLED||value.errorCode.isEmpty())
        }
    }
}
