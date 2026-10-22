package com.ai.assistance.operit.data.db

import androidx.room.*
import com.ai.assistance.operit.api.chat.DiaryDraft
import com.ai.assistance.operit.api.chat.DiaryGenerations
import com.ai.assistance.operit.api.chat.FrozenDiaryGeneration
import com.ai.assistance.operit.data.model.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface DiaryDao {
    @Query("SELECT d.* FROM diary_entries d JOIN roles r ON r.id=d.roleId AND r.spaceId=d.spaceId WHERE r.id=:roleId AND r.spaceId=:spaceId ORDER BY d.occurredAt DESC,d.updatedAt DESC,d.id")
    fun observe(roleId:String,spaceId:String):Flow<List<DiaryEntry>>
    @Query("SELECT d.* FROM diary_entries d JOIN roles r ON r.id=d.roleId AND r.spaceId=d.spaceId WHERE r.id=:roleId AND r.spaceId=:spaceId ORDER BY d.occurredAt DESC,d.updatedAt DESC,d.id")
    suspend fun read(roleId:String,spaceId:String):List<DiaryEntry>
    @Query("SELECT d.* FROM diary_entries d JOIN roles r ON r.id=d.roleId AND r.spaceId=d.spaceId WHERE d.id=:id AND r.id=:roleId AND r.spaceId=:spaceId LIMIT 1")
    suspend fun entry(roleId:String,spaceId:String,id:String):DiaryEntry?
    @Insert suspend fun insert(entry:DiaryEntry)
    @Update suspend fun update(entry:DiaryEntry):Int
    @Query("DELETE FROM diary_entries WHERE id=:id AND roleId=:roleId AND spaceId=:spaceId AND version=:version")
    suspend fun delete(roleId:String,spaceId:String,id:String,version:Long):Int
    @Query("DELETE FROM diary_entries WHERE roleId=:roleId AND spaceId=:spaceId")
    suspend fun deleteRole(roleId:String,spaceId:String)
}

class DiaryRepository(private val db:AppDatabase) {
    private val roles=RoleRepository(db)

    suspend fun saveManual(scope:RoleAccessScope,existing:DiaryEntry?,notebook:String,title:String,content:String,mood:String,occurredAt:Long,clock:()->Long={System.currentTimeMillis()}):DiaryEntry=db.withTransaction {
        roles.requireRole(scope);Diaries.validate(notebook,title,content,mood,occurredAt)
        val now=clock()
        val saved=if(existing==null) {
            if(db.diaries().read(scope.roleId,scope.spaceId).size>=Diaries.MAX_ENTRIES)throw ChatFailure("DIARY_LIMIT")
            DiaryEntry(UUID.randomUUID().toString(),scope.roleId,scope.spaceId,notebook,title,content,mood,"manual",occurredAt=occurredAt,createdAt=now,updatedAt=now)
                .also {Diaries.validateStored(it);db.diaries().insert(it)}
        } else {
            val current=db.diaries().entry(scope.roleId,scope.spaceId,existing.id)?:throw ChatFailure("DIARY_CHANGED")
            if(current.version!=existing.version)throw ChatFailure("DIARY_CHANGED")
            current.copy(notebook=notebook,title=title,content=content,mood=mood,occurredAt=occurredAt,version=current.version+1,updatedAt=now.coerceAtLeast(current.updatedAt+1))
                .also {Diaries.validateStored(it);if(db.diaries().update(it)!=1)throw ChatFailure("DIARY_CHANGED")}
        }
        saved
    }

    suspend fun saveGenerated(frozen:FrozenDiaryGeneration,draft:DiaryDraft,occurredAt:Long,clock:()->Long={System.currentTimeMillis()}):DiaryEntry=db.withTransaction {
        DiaryGenerations.validateFrozen(frozen);draft.validate(frozen.notebook);Diaries.validate(frozen.notebook,draft.title,draft.content,draft.mood,occurredAt)
        val role=roles.requireRole(frozen.role.scope());val config=db.dao().config(frozen.config.id)?:throw ChatFailure("DIARY_SOURCE_CHANGED")
        if(role!=frozen.role||config!=frozen.config)throw ChatFailure("DIARY_SOURCE_CHANGED")
        if(db.diaries().read(role.id,role.spaceId).size>=Diaries.MAX_ENTRIES)throw ChatFailure("DIARY_LIMIT")
        val now=clock().coerceAtLeast(frozen.createdAt)
        DiaryEntry(UUID.randomUUID().toString(),role.id,role.spaceId,frozen.notebook,draft.title,draft.content,draft.mood,"generated",
            "${config.provider} · ${config.model}",config.version,frozen.prompt,occurredAt,createdAt=now,updatedAt=now)
            .also {Diaries.validateStored(it);db.diaries().insert(it)}
    }

    suspend fun delete(scope:RoleAccessScope,entry:DiaryEntry)=db.withTransaction {
        roles.requireRole(scope);if(entry.scope()!=scope)throw ChatFailure("ACCESS")
        if(db.diaries().delete(scope.roleId,scope.spaceId,entry.id,entry.version)!=1)throw ChatFailure("DIARY_CHANGED")
    }
}
