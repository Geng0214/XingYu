package com.ai.assistance.operit.data.db

import androidx.room.*
import com.ai.assistance.operit.data.model.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao interface WorldbookDao {
    @Query("SELECT w.* FROM worldbook_entries w JOIN roles r ON r.id=w.roleId AND r.spaceId=w.spaceId WHERE r.id=:roleId AND r.spaceId=:spaceId ORDER BY w.priority DESC,w.id")
    fun observe(roleId:String,spaceId:String):Flow<List<WorldbookEntry>>
    @Query("SELECT w.* FROM worldbook_entries w JOIN roles r ON r.id=w.roleId AND r.spaceId=w.spaceId WHERE r.id=:roleId AND r.spaceId=:spaceId ORDER BY w.priority DESC,w.id")
    suspend fun read(roleId:String,spaceId:String):List<WorldbookEntry>
    @Insert suspend fun insert(entries:List<WorldbookEntry>)
    @Update suspend fun update(entry:WorldbookEntry):Int
    @Query("DELETE FROM worldbook_entries WHERE id=:id AND roleId=:roleId AND spaceId=:spaceId AND version=:version")
    suspend fun delete(roleId:String,spaceId:String,id:String,version:Long):Int
    @Query("DELETE FROM worldbook_entries WHERE roleId=:roleId AND spaceId=:spaceId AND chatId=:chatId")
    suspend fun deleteChat(roleId:String,spaceId:String,chatId:String)
    @Query("DELETE FROM worldbook_entries WHERE roleId=:roleId AND spaceId=:spaceId")
    suspend fun deleteRole(roleId:String,spaceId:String)
}

class WorldbookRepository(private val db:AppDatabase) {
    private val roles=RoleRepository(db)
    suspend fun save(scope:RoleAccessScope,id:String?,expectedVersion:Long?,chatId:String?,title:String,content:String,
        trigger:String,keywords:String,enabled:Boolean,priority:Int):WorldbookEntry=db.withTransaction {
        val role=roles.requireRole(scope)
        if(chatId!=null)roles.requireChat(scope,chatId)
        Worldbook.validate(title,content,trigger,keywords,priority)
        val entries=db.worldbook().read(scope.roleId,scope.spaceId)
        val entry=if(id==null) {
            require(expectedVersion==null)
            if(entries.size>=Worldbook.MAX_ENTRIES)throw ChatFailure("WORLDBOOK_LIMIT")
            WorldbookEntry(UUID.randomUUID().toString(),scope.roleId,scope.spaceId,chatId,title.trim(),content,trigger,keywords,enabled,priority)
        } else {
            val old=entries.firstOrNull {it.id==id}?:throw ChatFailure("ACCESS")
            if(old.version!=expectedVersion)throw ChatFailure("WORLDBOOK_CHANGED")
            old.copy(chatId=chatId,title=title.trim(),content=content,trigger=trigger,keywords=keywords,enabled=enabled,priority=priority,version=old.version+1)
        }
        if(id==null)db.worldbook().insert(listOf(entry))else check(db.worldbook().update(entry)==1)
        db.dao().saveRole(role.copy(version=role.version+1))
        entry
    }
    suspend fun delete(scope:RoleAccessScope,id:String,version:Long)=db.withTransaction {
        val role=roles.requireRole(scope)
        if(db.worldbook().delete(scope.roleId,scope.spaceId,id,version)!=1)throw ChatFailure("WORLDBOOK_CHANGED")
        db.dao().saveRole(role.copy(version=role.version+1))
    }
    suspend fun import(scope:RoleAccessScope,template:WorldbookTemplate)=db.withTransaction {
        val role=roles.requireRole(scope)
        WorldbookTemplateCodec.validate(template)
        if(db.worldbook().read(scope.roleId,scope.spaceId).size+template.entries.size>Worldbook.MAX_ENTRIES)throw ChatFailure("WORLDBOOK_LIMIT")
        db.worldbook().insert(template.entries.map {e->WorldbookEntry(UUID.randomUUID().toString(),scope.roleId,scope.spaceId,null,e.title,e.content,e.trigger,e.keywords,false,e.priority)})
        db.dao().saveRole(role.copy(version=role.version+1))
    }
}
