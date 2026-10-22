package com.ai.assistance.operit.data.db

import androidx.room.withTransaction
import com.ai.assistance.operit.data.model.*
import java.util.UUID

class RoleRepository(private val db: AppDatabase,private val homeClock:()->Long={System.currentTimeMillis()}) {
    suspend fun requireRole(scope: RoleAccessScope): RoleEntity {
        val role = db.dao().role(scope.roleId) ?: throw ChatFailure("ACCESS")
        if(role.spaceId!=scope.spaceId) throw ChatFailure("ACCESS")
        return role
    }
    suspend fun requireChat(scope: RoleAccessScope, id: String): ChatEntity =
        db.dao().chat(scope.roleId,scope.spaceId,id) ?: throw ChatFailure("ACCESS")

    suspend fun createChat(scope: RoleAccessScope, configId: String): ChatEntity = db.withTransaction {
        val role=requireRole(scope)
        // A contact owns one private thread; reopening it must not create another inbox row.
        db.dao().privateChat(scope.roleId,scope.spaceId)?.let { return@withTransaction it }
        requireNotNull(db.dao().config(configId))
        val chat=ChatEntity(UUID.randomUUID().toString(),role.id,configId,"与${role.name}的对话",openingMessage=if(role.isRaw())""else role.openingMessage)
        db.dao().insertChat(chat)
        chat
    }
    suspend fun saveRole(scope: RoleAccessScope?, name: String, prompt: String, opening: String, configId: String?, replyStyle: String?=null): RoleEntity = db.withTransaction {
        require(name.isNotBlank() && name.length<=40 && prompt.isNotBlank() && prompt.length<=8000 && opening.length<=8000)
        if(configId!=null) requireNotNull(db.dao().config(configId))
        val role=if(scope==null) RoleEntity(UUID.randomUUID().toString(),name.trim(),prompt.trim(),openingMessage=opening,defaultConfigId=configId)
            else requireRole(scope).let { it.copy(name=name.trim(),prompt=prompt.trim(),openingMessage=opening,defaultConfigId=configId,version=it.version+1) }
        if(replyStyle!=null)require(replyStyle in setOf("short","paragraph"))
        val saved=if(replyStyle==null)role else role.copy(replyStyle=replyStyle)
        db.dao().saveRole(saved)
        HomeRepository(db,homeClock).join(saved.scope())
        saved
    }
    suspend fun copyRole(scope: RoleAccessScope): RoleEntity = db.withTransaction {
        val role=requireRole(scope)
        val copy=RoleEntity(UUID.randomUUID().toString(),role.name,role.prompt,openingMessage=role.openingMessage,defaultConfigId=role.defaultConfigId,replyStyle=role.replyStyle,conversationMode=role.conversationMode)
        db.dao().saveRole(copy)
        HomeRepository(db,homeClock).join(copy.scope())
        copy
    }
    suspend fun savePersona(scope:RoleAccessScope,expectedVersion:Long,name:String,description:String,enabled:Boolean):RoleEntity=db.withTransaction {
        val role=requireRole(scope)
        if(role.version!=expectedVersion)throw ChatFailure("ROLE_CHANGED")
        UserPersona.validate(name,description,enabled)
        val updated=role.copy(userName=name.trim(),userPersona=description,personaEnabled=enabled,version=role.version+1)
        db.dao().saveRole(updated)
        updated
    }
    suspend fun deleteChat(scope: RoleAccessScope, id: String, memoryDisposition:String="reject") = db.withTransaction {
        val role=requireRole(scope)
        requireChat(scope,id)
        val memoryCount=db.memories().sourceCount(scope.roleId,scope.spaceId,id)
        if(memoryCount>0) {
            when(memoryDisposition) {
                "detach"->{
                    val now=System.currentTimeMillis().coerceAtLeast(0)
                    if(db.memories().detachSources(scope.roleId,scope.spaceId,id,now)!=memoryCount)throw ChatFailure("MEMORY_CHANGED")
                }
                "delete"->{
                    db.memories().clearReplacementLinksForSource(scope.roleId,scope.spaceId,id,System.currentTimeMillis().coerceAtLeast(0))
                    if(db.memories().deleteSources(scope.roleId,scope.spaceId,id)!=memoryCount)throw ChatFailure("MEMORY_CHANGED")
                }
                else->throw ChatFailure("MEMORY_SOURCE_REQUIRED")
            }
            db.dao().saveRole(role.copy(version=role.version+1))
        } else require(memoryDisposition in setOf("reject","detach","delete"))
        db.proactive().deleteChatSetting(scope.roleId,scope.spaceId,id)
        db.proactive().deleteChatRequests(scope.roleId,scope.spaceId,id)
        db.relationships().deleteChat(scope.roleId,scope.spaceId,id)
        db.worldbook().deleteChat(scope.roleId,scope.spaceId,id)
        db.dao().deleteMessages(scope.roleId,scope.spaceId,id)
        db.dao().deleteChat(scope.roleId,scope.spaceId,id)
    }
    suspend fun deleteRole(scope: RoleAccessScope) = db.withTransaction {
        requireRole(scope)
        HomeRepository(db,homeClock).leave(scope)
        GroupRepository(db).removeRole(scope)
        db.proactive().deleteRoleSetting(scope.roleId,scope.spaceId)
        db.proactive().deleteRoleRequests(scope.roleId,scope.spaceId)
        db.relationships().deleteRole(scope.roleId,scope.spaceId)
        db.worldbook().deleteRole(scope.roleId,scope.spaceId)
        db.memories().deleteRole(scope.roleId,scope.spaceId)
        db.diaries().deleteRole(scope.roleId,scope.spaceId)
        db.environment().deleteRole(scope.roleId,scope.spaceId)
        db.planner().deleteRolePlanner(scope.roleId,scope.spaceId)
        db.planner().deleteRoleCycles(scope.roleId,scope.spaceId)
        db.dao().deletePortraits(scope.roleId,scope.spaceId)
        db.dao().deleteAppearance(scope.roleId,scope.spaceId)
        db.dao().deleteRoleMessages(scope.roleId,scope.spaceId)
        db.dao().deleteRoleChats(scope.roleId,scope.spaceId)
        db.dao().deleteRole(scope.roleId,scope.spaceId)
    }
    suspend fun setReplyStyle(scope: RoleAccessScope, style: String) = db.withTransaction {
        require(style in setOf("short","paragraph"))
        val role=requireRole(scope)
        db.dao().saveRole(role.copy(replyStyle=style,version=role.version+1))
    }
    suspend fun setConversationMode(scope:RoleAccessScope,expectedVersion:Long,mode:String):RoleEntity=db.withTransaction {
        require(mode in setOf("character","raw"))
        val role=requireRole(scope)
        if(role.version!=expectedVersion)throw ChatFailure("ROLE_CHANGED")
        role.copy(conversationMode=mode,version=role.version+1).also {
            db.dao().saveRole(it)
            if(mode=="raw")db.proactive().deleteRoleSetting(scope.roleId,scope.spaceId)
        }
    }
    suspend fun saveAppearance(scope: RoleAccessScope, wallpaper: ByteArray?, dimming: Float, palette: String) = db.withTransaction {
        requireRole(scope)
        require(dimming in 0f..0.65f && palette in setOf("rose","green","blue"))
        require(wallpaper==null || wallpaper.size<=1536*1024)
        db.dao().saveAppearance(ChatAppearance(scope.roleId,scope.spaceId,wallpaper,dimming,palette))
    }
    suspend fun addSticker(scope:RoleAccessScope,name:String,sticker:PromptSticker):StickerAsset=db.withTransaction {
        requireRole(scope)
        if(db.dao().stickerAssetCount(scope.roleId,scope.spaceId)>=StickerAssets.MAX_PER_ROLE)throw ChatFailure("STICKER_LIMIT")
        val trimmed=name.trim()
        try {StickerAssets.validateName(trimmed);sticker.copy(sourceAssetId=null,displayName=trimmed).validate()}
        catch(_:IllegalArgumentException){throw ChatFailure("STICKER_NAME")}
        val asset=StickerAsset(UUID.randomUUID().toString(),scope.roleId,scope.spaceId,trimmed,sticker.mimeType,sticker.width,sticker.height,sticker.sha256,sticker.data.copyOf())
        db.dao().insertStickerAsset(asset)
        asset
    }
    suspend fun selectSticker(scope:RoleAccessScope,id:String):PromptSticker=db.withTransaction {
        requireRole(scope)
        (db.dao().stickerAsset(scope.roleId,scope.spaceId,id)?:throw ChatFailure("ACCESS")).promptSticker()
    }
    suspend fun setStickerFavorite(scope:RoleAccessScope,id:String,favorite:Boolean)=db.withTransaction {
        requireRole(scope)
        if(db.dao().setStickerFavorite(scope.roleId,scope.spaceId,id,favorite)!=1)throw ChatFailure("ACCESS")
    }
    suspend fun deleteSticker(scope:RoleAccessScope,id:String)=db.withTransaction {
        requireRole(scope)
        if(db.dao().deleteStickerAsset(scope.roleId,scope.spaceId,id)!=1)throw ChatFailure("ACCESS")
    }
    data class RetryPayload(val text:String,val image:PromptImage?,val material:PromptMaterial?,val sticker:PromptSticker?)
    suspend fun retryPayload(scope: RoleAccessScope, chatId: String, requestId: String): RetryPayload = db.withTransaction {
        requireChat(scope,chatId)
        val message=db.dao().history(scope.roleId,scope.spaceId,chatId).firstOrNull { it.requestId==requestId && it.kind=="user" }
            ?:throw ChatFailure("ACCESS")
        RetryPayload(message.content,db.dao().requestAttachment(scope.roleId,scope.spaceId,chatId,requestId)?.promptImage(),
            db.dao().requestMaterial(scope.roleId,scope.spaceId,chatId,requestId)?.promptMaterial(),
            db.dao().requestSticker(scope.roleId,scope.spaceId,chatId,requestId)?.promptSticker())
    }
    suspend fun savePortrait(scope: RoleAccessScope, kind: String, bytes: ByteArray?) = db.withTransaction {
        requireRole(scope)
        require(kind in setOf("role","self"))
        if(bytes==null) db.dao().deletePortrait(scope.roleId,scope.spaceId,kind)
        else {
            require(bytes.isNotEmpty() && bytes.size<=65536)
            db.dao().savePortrait(RolePortrait(scope.roleId,scope.spaceId,kind,bytes))
        }
    }
    suspend fun setPinned(scope: RoleAccessScope, chatId: String, pinned: Boolean) = db.withTransaction {
        requireChat(scope,chatId)
        if(db.dao().setPinned(scope.roleId,scope.spaceId,chatId,pinned)!=1)throw ChatFailure("ACCESS")
    }
    suspend fun setFavorite(scope: RoleAccessScope, chatId: String, messageId: String, favorite: Boolean) = db.withTransaction {
        requireChat(scope,chatId)
        if(db.dao().setFavorite(scope.roleId,scope.spaceId,chatId,messageId,favorite)!=1)throw ChatFailure("ACCESS")
    }
    suspend fun deleteMessages(scope:RoleAccessScope,chatId:String,messageIds:List<String>):Int=db.withTransaction {
        val ids=messageIds.distinct()
        require(ids.isNotEmpty()&&ids.size<=100)
        requireChat(scope,chatId)
        val selected=db.dao().history(scope.roleId,scope.spaceId,chatId).filter {it.id in ids}
        if(selected.size!=ids.size||selected.any {it.state in setOf(MessageState.QUEUED,MessageState.WAITING,MessageState.GENERATING)})throw ChatFailure("ACCESS")
        // Detach approved memory provenance before deleting its source message, so memory records never point at missing chat data.
        val detached=db.memories().detachSourcesForMessages(scope.roleId,scope.spaceId,chatId,ids,System.currentTimeMillis().coerceAtLeast(0))
        if(db.dao().deleteSelectedMessages(scope.roleId,scope.spaceId,chatId,ids)!=ids.size)throw ChatFailure("ACCESS")
        detached
    }
}
