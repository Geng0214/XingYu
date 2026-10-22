package com.ai.assistance.operit.data.db

import androidx.room.*
import com.ai.assistance.operit.data.model.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao interface RelationshipDao {
    @Query("SELECT e.* FROM relationship_entries e JOIN roles r ON r.id=e.roleId AND r.spaceId=e.spaceId WHERE r.id=:roleId AND r.spaceId=:spaceId ORDER BY e.priority DESC,e.id")
    fun observe(roleId:String,spaceId:String):Flow<List<RelationshipEntry>>
    @Query("SELECT e.* FROM relationship_entries e JOIN roles r ON r.id=e.roleId AND r.spaceId=e.spaceId WHERE r.id=:roleId AND r.spaceId=:spaceId ORDER BY e.priority DESC,e.id")
    suspend fun read(roleId:String,spaceId:String):List<RelationshipEntry>
    @Insert suspend fun insert(entry:RelationshipEntry)
    @Update suspend fun update(entry:RelationshipEntry):Int
    @Query("DELETE FROM relationship_entries WHERE id=:id AND roleId=:roleId AND spaceId=:spaceId AND version=:version")
    suspend fun delete(roleId:String,spaceId:String,id:String,version:Long):Int
    @Query("DELETE FROM relationship_entries WHERE roleId=:roleId AND spaceId=:spaceId AND chatId=:chatId")
    suspend fun deleteChat(roleId:String,spaceId:String,chatId:String)
    @Query("DELETE FROM relationship_entries WHERE roleId=:roleId AND spaceId=:spaceId")
    suspend fun deleteRole(roleId:String,spaceId:String)
}

class RelationshipRepository(private val db:AppDatabase) {
    private val roles=RoleRepository(db)
    suspend fun save(scope:RoleAccessScope,id:String?,expectedVersion:Long?,chatId:String?,source:String,target:String,label:String,
        description:String,enabled:Boolean,priority:Int):RelationshipEntry=db.withTransaction {
        val role=roles.requireRole(scope)
        if(chatId!=null)roles.requireChat(scope,chatId)
        Relationships.validate(source,target,label,description,priority)
        val entries=db.relationships().read(scope.roleId,scope.spaceId)
        val updated=if(id==null) {
            require(expectedVersion==null)
            if(entries.size>=Relationships.MAX_ENTRIES)throw ChatFailure("RELATIONSHIP_LIMIT")
            RelationshipEntry(UUID.randomUUID().toString(),scope.roleId,scope.spaceId,chatId,source.trim(),target.trim(),label.trim(),description,enabled,priority)
        }else {
            val original=entries.firstOrNull {it.id==id}?:throw ChatFailure("ACCESS")
            if(original.version!=expectedVersion)throw ChatFailure("RELATIONSHIP_CHANGED")
            original.copy(chatId=chatId,sourceName=source.trim(),targetName=target.trim(),label=label.trim(),description=description,enabled=enabled,priority=priority,version=original.version+1)
        }
        if(id==null)db.relationships().insert(updated)else check(db.relationships().update(updated)==1)
        db.dao().saveRole(role.copy(version=role.version+1))
        updated
    }
    suspend fun delete(scope:RoleAccessScope,id:String,version:Long)=db.withTransaction {
        val role=roles.requireRole(scope)
        if(db.relationships().delete(scope.roleId,scope.spaceId,id,version)!=1)throw ChatFailure("RELATIONSHIP_CHANGED")
        db.dao().saveRole(role.copy(version=role.version+1))
    }
}
