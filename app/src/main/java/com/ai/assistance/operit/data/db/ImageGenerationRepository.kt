package com.ai.assistance.operit.data.db

import androidx.room.withTransaction
import com.ai.assistance.operit.data.model.*
import java.util.UUID

data class ImageGenerationRequest(val image:GeneratedImage,val config:ImageGenerationConfig)

class ImageGenerationRepository(private val db:AppDatabase) {
    suspend fun prepare(scope:RoleAccessScope,chatId:String,configId:String,prompt:String):ImageGenerationRequest=db.withTransaction {
        ImageGenerations.validatePrompt(prompt)
        val chat=db.dao().chat(scope.roleId,scope.spaceId,chatId)?:throw ChatFailure("ACCESS")
        require(chat.roleId==scope.roleId)
        val config=db.dao().imageGenerationConfig(configId)?.also(ImageGenerations::validateConfig)?:throw ChatFailure("IMAGE_GENERATION_CONFIG")
        val record=GeneratedImage(UUID.randomUUID().toString(),scope.roleId,scope.spaceId,chatId,config.id,config.version,config.model,prompt.trim(),config.size,MessageState.QUEUED)
        ImageGenerations.validateStored(record);db.dao().insertGeneratedImage(record)
        ImageGenerationRequest(record,config.copy())
    }
    suspend fun start(request:ImageGenerationRequest) {
        val i=request.image
        if(db.dao().startGeneratedImage(i.roleId,i.spaceId,i.chatId,i.id)!=1)throw ChatFailure("ACCESS")
    }
    suspend fun complete(request:ImageGenerationRequest,mimeType:String,width:Int,height:Int,sha256:String,data:ByteArray,finishedAt:Long) {
        val i=request.image;val elapsed=(finishedAt-i.createdAt).coerceAtLeast(0)
        val completed=i.copy(state=MessageState.COMPLETE,mimeType=mimeType,width=width,height=height,sha256=sha256,data=data.copyOf(),finishedAt=finishedAt,elapsedMs=elapsed)
        ImageGenerations.validateStored(completed)
        if(db.dao().completeGeneratedImage(i.roleId,i.spaceId,i.chatId,i.id,mimeType,width,height,sha256,data.copyOf(),finishedAt,elapsed)!=1)throw ChatFailure("ACCESS")
    }
    suspend fun finish(request:ImageGenerationRequest,state:MessageState,error:String,finishedAt:Long) {
        require(state in setOf(MessageState.CANCELLED,MessageState.FAILED))
        val i=request.image;val elapsed=(finishedAt-i.createdAt).coerceAtLeast(0)
        if(db.dao().finishGeneratedImage(i.roleId,i.spaceId,i.chatId,i.id,state,error,finishedAt,elapsed)!=1)throw ChatFailure("ACCESS")
    }
}
