package com.ai.assistance.operit.data.db

import androidx.room.*
import com.ai.assistance.operit.api.chat.MemoryCandidate
import com.ai.assistance.operit.api.chat.MemoryExtractions
import com.ai.assistance.operit.data.model.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao interface MemoryDao {
    @Query("SELECT m.* FROM memory_entries m JOIN roles r ON r.id=m.roleId AND r.spaceId=m.spaceId WHERE r.id=:roleId AND r.spaceId=:spaceId ORDER BY m.priority DESC,m.updatedAt DESC,m.id")
    fun observe(roleId:String,spaceId:String):Flow<List<MemoryEntry>>
    @Query("SELECT m.* FROM memory_entries m JOIN roles r ON r.id=m.roleId AND r.spaceId=m.spaceId WHERE r.id=:roleId AND r.spaceId=:spaceId ORDER BY m.priority DESC,m.updatedAt DESC,m.id")
    suspend fun read(roleId:String,spaceId:String):List<MemoryEntry>
    @Insert suspend fun insert(entry:MemoryEntry)
    @Update suspend fun update(entry:MemoryEntry):Int
    @Query("DELETE FROM memory_entries WHERE id=:id AND roleId=:roleId AND spaceId=:spaceId AND version=:version")
    suspend fun delete(roleId:String,spaceId:String,id:String,version:Long):Int
    @Query("SELECT COUNT(*) FROM memory_entries WHERE roleId=:roleId AND spaceId=:spaceId AND source='extracted' AND sourceChatId=:chatId")
    suspend fun sourceCount(roleId:String,spaceId:String,chatId:String):Int
    @Query("UPDATE memory_entries SET sourceChatId=NULL,sourceRequestId=NULL,sourceUserMessageId=NULL,sourceAssistantMessageId=NULL,version=version+1,updatedAt=CASE WHEN updatedAt>=:updatedAt THEN updatedAt+1 ELSE :updatedAt END WHERE roleId=:roleId AND spaceId=:spaceId AND source='extracted' AND sourceChatId=:chatId")
    suspend fun detachSources(roleId:String,spaceId:String,chatId:String,updatedAt:Long):Int
    @Query("UPDATE memory_entries SET sourceChatId=NULL,sourceRequestId=NULL,sourceUserMessageId=NULL,sourceAssistantMessageId=NULL,version=version+1,updatedAt=CASE WHEN updatedAt>=:updatedAt THEN updatedAt+1 ELSE :updatedAt END WHERE roleId=:roleId AND spaceId=:spaceId AND source='extracted' AND sourceChatId=:chatId AND (sourceUserMessageId IN (:messageIds) OR sourceAssistantMessageId IN (:messageIds))")
    suspend fun detachSourcesForMessages(roleId:String,spaceId:String,chatId:String,messageIds:List<String>,updatedAt:Long):Int
    @Query("DELETE FROM memory_entries WHERE roleId=:roleId AND spaceId=:spaceId AND source='extracted' AND sourceChatId=:chatId")
    suspend fun deleteSources(roleId:String,spaceId:String,chatId:String):Int
    @Query("UPDATE memory_entries SET replacedById=NULL,version=version+1,updatedAt=CASE WHEN updatedAt>=:updatedAt THEN updatedAt+1 ELSE :updatedAt END WHERE roleId=:roleId AND spaceId=:spaceId AND replacedById=:memoryId")
    suspend fun clearReplacementLinks(roleId:String,spaceId:String,memoryId:String,updatedAt:Long):Int
    @Query("UPDATE memory_entries SET replacedById=NULL,version=version+1,updatedAt=CASE WHEN updatedAt>=:updatedAt THEN updatedAt+1 ELSE :updatedAt END WHERE roleId=:roleId AND spaceId=:spaceId AND replacedById IN (SELECT id FROM memory_entries WHERE roleId=:roleId AND spaceId=:spaceId AND source='extracted' AND sourceChatId=:chatId)")
    suspend fun clearReplacementLinksForSource(roleId:String,spaceId:String,chatId:String,updatedAt:Long):Int
    @Query("DELETE FROM memory_entries WHERE roleId=:roleId AND spaceId=:spaceId")
    suspend fun deleteRole(roleId:String,spaceId:String)
}
class MemoryRepository(private val db:AppDatabase) {
    private val roles=RoleRepository(db)

    suspend fun save(scope:RoleAccessScope,id:String?,expectedVersion:Long?,kind:String,title:String,content:String,
        enabled:Boolean,priority:Int,recallMode:String="always",expiresAt:Long?=null,conflictPolicy:String="reject",
        expectedConflictIds:Set<String> = emptySet(),clock:()->Long={System.currentTimeMillis()}):MemoryEntry=db.withTransaction {
        val role=roles.requireRole(scope)
        try {Memories.validate(kind,title,content,priority,recallMode=recallMode,expiresAt=expiresAt)}catch(_:IllegalArgumentException){throw ChatFailure("INPUT")}
        val entries=db.memories().read(scope.roleId,scope.spaceId)
        val now=clock().coerceAtLeast(0)
        if(expiresAt!=null&&expiresAt<=now)throw ChatFailure("INPUT")
        val old=id?.let {memoryId->entries.firstOrNull {it.id==memoryId}?:throw ChatFailure("ACCESS")}
        if(old!=null&&old.version!=expectedVersion)throw ChatFailure("MEMORY_CHANGED")
        val contentChanged=old==null||old.kind!=kind||old.title!=title.trim()||old.content!=content
        val conflicts=if(contentChanged)Memories.conflicts(scope,entries,kind,title,content,id,now)else emptyList()
        if(conflicts.map {it.id}.toSet()!=expectedConflictIds)throw ChatFailure(if(conflicts.isEmpty())"MEMORY_CHANGED" else "MEMORY_CONFLICT")
        if(conflicts.isNotEmpty()&&conflictPolicy !in setOf("keep","replace"))throw ChatFailure("MEMORY_CONFLICT")
        if(conflicts.isEmpty()&&conflictPolicy !in setOf("reject","keep","replace"))throw ChatFailure("INPUT")
        val entry=if(old==null) {
            require(expectedVersion==null)
            if(entries.size>=Memories.MAX_ENTRIES)throw ChatFailure("MEMORY_LIMIT")
            MemoryEntry(UUID.randomUUID().toString(),scope.roleId,scope.spaceId,kind,title.trim(),content,enabled,priority,createdAt=now,updatedAt=now,
                recallMode=recallMode,expiresAt=expiresAt)
        } else {
            old.copy(kind=kind,title=title.trim(),content=content,enabled=enabled,priority=priority,recallMode=recallMode,expiresAt=expiresAt,
                version=old.version+1,updatedAt=maxOf(now,old.updatedAt+1))
        }
        if(id==null)db.memories().insert(entry)else if(db.memories().update(entry)!=1)throw ChatFailure("MEMORY_CHANGED")
        if(conflictPolicy=="replace")conflicts.forEach {conflict->
            val prior=entries.single {it.id==conflict.id&&it.version==conflict.version}
            val forgottenAt=maxOf(now,prior.updatedAt+1)
            if(db.memories().update(prior.copy(enabled=false,forgottenAt=forgottenAt,replacedById=entry.id,version=prior.version+1,updatedAt=forgottenAt))!=1)throw ChatFailure("MEMORY_CHANGED")
        }
        db.dao().saveRole(role.copy(version=role.version+1))
        entry
    }

    suspend fun saveExtracted(frozen:com.ai.assistance.operit.api.chat.FrozenMemoryExtraction,candidates:List<com.ai.assistance.operit.api.chat.MemoryCandidate>,extractedAt:Long,
        clock:()->Long={System.currentTimeMillis()}):List<MemoryEntry> = db.withTransaction {
        val scope=frozen.source.scope
        val role=roles.requireRole(scope)
        val chat=roles.requireChat(scope,frozen.source.chatId)
        val config=db.dao().config(chat.configId)?:throw ChatFailure("MEMORY_SOURCE_CHANGED")
        if(role!=frozen.role||config!=frozen.config)throw ChatFailure("MEMORY_SOURCE_CHANGED")
        val user=db.dao().message(scope.roleId,scope.spaceId,chat.id,frozen.source.userMessageId)?:throw ChatFailure("MEMORY_SOURCE_CHANGED")
        val assistant=db.dao().message(scope.roleId,scope.spaceId,chat.id,frozen.source.assistantMessageId)?:throw ChatFailure("MEMORY_SOURCE_CHANGED")
        if(user.requestId!=frozen.source.requestId||assistant.requestId!=frozen.source.requestId||user.kind!="user"||assistant.kind!="assistant"||
            user.state!=MessageState.COMPLETE||assistant.state!=MessageState.COMPLETE||user.content!=frozen.source.userText||assistant.content!=frozen.source.assistantText||
            assistant.createdAt!=frozen.source.messageAt)throw ChatFailure("MEMORY_SOURCE_CHANGED")
        if(candidates.isEmpty()||candidates.size>MemoryExtractions.MAX_CANDIDATES)throw ChatFailure("INPUT")
        candidates.forEach(MemoryCandidate::validate)
        if(candidates.map {it.kind to (it.title.trim() to it.content)}.distinct().size!=candidates.size)throw ChatFailure("INPUT")
        val existing=db.memories().read(scope.roleId,scope.spaceId)
        if(existing.size+candidates.size>Memories.MAX_ENTRIES)throw ChatFailure("MEMORY_LIMIT")
        val now=clock().coerceAtLeast(extractedAt)
        if(extractedAt<frozen.source.messageAt)throw ChatFailure("MEMORY_SOURCE_CHANGED")
        val saved=candidates.map {candidate->
            MemoryEntry(UUID.randomUUID().toString(),scope.roleId,scope.spaceId,candidate.kind,candidate.title.trim(),candidate.content,true,50,"extracted",createdAt=now,updatedAt=now,
                sourceChatId=chat.id,sourceRequestId=frozen.source.requestId,sourceUserMessageId=user.id,sourceAssistantMessageId=assistant.id,
                sourceModel="${frozen.config.provider} · ${frozen.config.model}",sourceConfigVersion=frozen.config.version,sourceMessageAt=assistant.createdAt,extractedAt=extractedAt,
                recallMode="auto").also {
                Memories.validateStored(it);db.memories().insert(it)
            }
        }
        db.dao().saveRole(role.copy(version=role.version+1))
        saved
    }

    suspend fun sourceCount(scope:RoleAccessScope,chatId:String):Int=db.withTransaction {
        roles.requireChat(scope,chatId)
        db.memories().sourceCount(scope.roleId,scope.spaceId,chatId)
    }

    suspend fun delete(scope:RoleAccessScope,entry:MemoryEntry)=db.withTransaction {
        val role=roles.requireRole(scope)
        if(entry.scope()!=scope)throw ChatFailure("ACCESS")
        db.memories().clearReplacementLinks(scope.roleId,scope.spaceId,entry.id,System.currentTimeMillis().coerceAtLeast(0))
        if(db.memories().delete(scope.roleId,scope.spaceId,entry.id,entry.version)!=1)throw ChatFailure("MEMORY_CHANGED")
        db.dao().saveRole(role.copy(version=role.version+1))
    }

    suspend fun forget(scope:RoleAccessScope,entry:MemoryEntry,clock:()->Long={System.currentTimeMillis()})=db.withTransaction {
        val role=roles.requireRole(scope)
        if(entry.scope()!=scope)throw ChatFailure("ACCESS")
        val current=db.memories().read(scope.roleId,scope.spaceId).singleOrNull {it.id==entry.id}?:throw ChatFailure("ACCESS")
        if(current.version!=entry.version)throw ChatFailure("MEMORY_CHANGED")
        val now=maxOf(clock().coerceAtLeast(0),current.updatedAt+1)
        if(db.memories().update(current.copy(enabled=false,forgottenAt=now,replacedById=null,version=current.version+1,updatedAt=now))!=1)throw ChatFailure("MEMORY_CHANGED")
        db.dao().saveRole(role.copy(version=role.version+1))
    }

    suspend fun restore(scope:RoleAccessScope,entry:MemoryEntry,clock:()->Long={System.currentTimeMillis()})=db.withTransaction {
        val role=roles.requireRole(scope)
        if(entry.scope()!=scope)throw ChatFailure("ACCESS")
        val current=db.memories().read(scope.roleId,scope.spaceId).singleOrNull {it.id==entry.id}?:throw ChatFailure("ACCESS")
        if(current.version!=entry.version||current.forgottenAt==null)throw ChatFailure("MEMORY_CHANGED")
        val now=maxOf(clock().coerceAtLeast(0),current.updatedAt+1)
        val restored=current.copy(enabled=true,forgottenAt=null,replacedById=null,version=current.version+1,updatedAt=now)
        Memories.validateStored(restored)
        if(db.memories().update(restored)!=1)throw ChatFailure("MEMORY_CHANGED")
        db.dao().saveRole(role.copy(version=role.version+1))
    }
}
