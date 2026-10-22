package com.ai.assistance.operit.data.db

import androidx.room.*
import com.ai.assistance.operit.data.model.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName="group_rooms",indices=[Index(value=["spaceId"],unique=true),Index(value=["groupId","spaceId"],unique=true)])
data class GroupRoomRow(@PrimaryKey val groupId:String,val spaceId:String,val title:String,val userName:String,val version:Long) {
    fun value()=GroupRoom(GroupAccessScope(groupId,spaceId),title,userName,version)
    companion object {fun from(v:GroupRoom)=GroupRoomRow(v.scope.groupId,v.scope.spaceId,v.title,v.userName,v.version)}
}
@Entity(tableName="group_members",foreignKeys=[
    ForeignKey(entity=GroupRoomRow::class,parentColumns=["groupId","spaceId"],childColumns=["groupId","spaceId"],onDelete=ForeignKey.CASCADE),
    ForeignKey(entity=RoleEntity::class,parentColumns=["id"],childColumns=["roleId"],onDelete=ForeignKey.RESTRICT),
    ForeignKey(entity=ModelConfigData::class,parentColumns=["id"],childColumns=["configId"],onDelete=ForeignKey.RESTRICT)],
    indices=[Index("groupId","spaceId"),Index(value=["groupId","roleId"],unique=true),Index("roleId"),Index("configId")])
data class GroupMemberRow(@PrimaryKey val id:String,val groupId:String,val spaceId:String,val roleId:String,val roleSpaceId:String,val name:String,val configId:String) {
    fun value()=GroupParticipant(id,GroupAccessScope(groupId,spaceId),RoleAccessScope(roleId,roleSpaceId),name,configId)
    companion object {fun from(v:GroupParticipant)=GroupMemberRow(v.id,v.group.groupId,v.group.spaceId,v.role.roleId,v.role.spaceId,v.name,v.configId)}
}
@Entity(tableName="group_requests",foreignKeys=[ForeignKey(entity=GroupRoomRow::class,parentColumns=["groupId","spaceId"],childColumns=["groupId","spaceId"],onDelete=ForeignKey.CASCADE)],
    indices=[Index("groupId","spaceId"),Index(value=["requestId","groupId","spaceId"],unique=true),Index(value=["userId"],unique=true),Index(value=["assistantId"],unique=true)])
data class GroupRequestRow(@PrimaryKey val requestId:String,val groupId:String,val spaceId:String,val groupVersion:Long,val participantId:String,
    val roleId:String,val roleSpaceId:String,val roleVersion:Long,val configId:String,val configVersion:Long,val provider:String,val model:String,
    val userId:String,val assistantId:String,val createdAt:Long,
    val terminalState:String="PENDING",val errorCode:String="") {
    fun scope()=GroupAccessScope(groupId,spaceId)
    fun finish(state:MessageState,error:String)=copy(terminalState=state.name,errorCode=error)
    fun matches(s:RequestSnapshot)=s.group?.let {it.group==scope()&&it.version==groupVersion&&it.participantId==participantId}==true&&
        s.requestId==requestId&&s.assistantId==assistantId&&s.scope==RoleAccessScope(roleId,roleSpaceId)&&s.role.version==roleVersion&&
        s.config.id==configId&&s.config.version==configVersion&&terminalState=="PENDING"
}
@Entity(tableName="group_messages",foreignKeys=[ForeignKey(entity=GroupRequestRow::class,parentColumns=["requestId","groupId","spaceId"],childColumns=["requestId","groupId","spaceId"],onDelete=ForeignKey.CASCADE)],
    indices=[Index("requestId","groupId","spaceId"),Index(value=["requestId","kind"],unique=true),Index("groupId","spaceId")])
data class GroupMessageRow(@PrimaryKey val id:String,val groupId:String,val spaceId:String,val requestId:String,val kind:String,val participantId:String?,
    val speakerName:String,val content:String,val state:MessageState,val createdAt:Long,val errorCode:String,val firstTokenMs:Long,val elapsedMs:Long) {
    fun value()=GroupMessage(id,GroupAccessScope(groupId,spaceId),requestId,kind,participantId,speakerName,content,state,createdAt,errorCode,firstTokenMs,elapsedMs)
    companion object {fun from(v:GroupMessage)=GroupMessageRow(v.id,v.group.groupId,v.group.spaceId,v.requestId,v.kind,v.participantId,v.speakerName,v.content,v.state,v.createdAt,v.errorCode,v.firstTokenMs,v.elapsedMs)}
}

data class GroupInboxRow(val groupId:String,val spaceId:String,val title:String,val memberCount:Int,val preview:String?,val updatedAt:Long?) {
    fun scope()=GroupAccessScope(groupId,spaceId)
}

@Dao interface GroupDao {
    @Query("SELECT g.groupId,g.spaceId,g.title,(SELECT COUNT(*) FROM group_members p WHERE p.groupId=g.groupId AND p.spaceId=g.spaceId) AS memberCount,(SELECT m.content FROM group_messages m WHERE m.groupId=g.groupId AND m.spaceId=g.spaceId ORDER BY m.createdAt DESC,m.id DESC LIMIT 1) AS preview,(SELECT MAX(m.createdAt) FROM group_messages m WHERE m.groupId=g.groupId AND m.spaceId=g.spaceId) AS updatedAt FROM group_rooms g ORDER BY updatedAt DESC,g.title,g.groupId") fun inbox():Flow<List<GroupInboxRow>>
    @Query("SELECT * FROM group_rooms ORDER BY title,groupId") fun rooms():Flow<List<GroupRoomRow>>
    @Query("SELECT * FROM group_rooms WHERE groupId=:id AND spaceId=:space") suspend fun room(id:String,space:String):GroupRoomRow?
    @Query("SELECT m.* FROM group_members m JOIN group_rooms g ON g.groupId=m.groupId AND g.spaceId=m.spaceId JOIN roles r ON r.id=m.roleId AND r.spaceId=m.roleSpaceId WHERE g.groupId=:id AND g.spaceId=:space ORDER BY m.id") suspend fun members(id:String,space:String):List<GroupMemberRow>
    @Query("SELECT m.* FROM group_messages m JOIN group_requests q ON q.requestId=m.requestId AND q.groupId=m.groupId AND q.spaceId=m.spaceId JOIN group_rooms g ON g.groupId=q.groupId AND g.spaceId=q.spaceId WHERE g.groupId=:id AND g.spaceId=:space ORDER BY m.createdAt,m.id") suspend fun history(id:String,space:String):List<GroupMessageRow>
    @Query("SELECT m.* FROM group_messages m JOIN group_requests q ON q.requestId=m.requestId AND q.groupId=m.groupId AND q.spaceId=m.spaceId JOIN group_rooms g ON g.groupId=q.groupId AND g.spaceId=q.spaceId WHERE g.groupId=:id AND g.spaceId=:space ORDER BY m.createdAt,m.id") fun messages(id:String,space:String):Flow<List<GroupMessageRow>>
    @Query("SELECT q.* FROM group_requests q JOIN group_rooms g ON g.groupId=q.groupId AND g.spaceId=q.spaceId WHERE g.groupId=:id AND g.spaceId=:space AND q.requestId=:request") suspend fun request(id:String,space:String,request:String):GroupRequestRow?
    @Query("SELECT * FROM group_requests WHERE groupId=:id AND spaceId=:space AND terminalState='PENDING'") suspend fun pending(id:String,space:String):List<GroupRequestRow>
    @Query("SELECT * FROM group_requests WHERE terminalState='PENDING'") suspend fun pending():List<GroupRequestRow>
    @Query("SELECT * FROM group_messages WHERE id=:id AND groupId=:groupId AND spaceId=:space AND requestId=:request") suspend fun message(id:String,groupId:String,space:String,request:String):GroupMessageRow?
    @Query("SELECT g.* FROM group_rooms g JOIN group_members m ON m.groupId=g.groupId AND m.spaceId=g.spaceId WHERE m.roleId=:role AND m.roleSpaceId=:space") suspend fun forRole(role:String,space:String):List<GroupRoomRow>
    @Query("SELECT COUNT(*) FROM group_rooms") suspend fun count():Int
    @Query("SELECT COUNT(*) FROM group_members WHERE groupId=:id AND spaceId=:space") suspend fun memberCount(id:String,space:String):Int
    @Insert suspend fun insert(row:GroupRoomRow)
    @Insert suspend fun insertMembers(rows:List<GroupMemberRow>)
    @Insert suspend fun insert(row:GroupRequestRow)
    @Insert suspend fun insertMessages(rows:List<GroupMessageRow>)
    @Update suspend fun update(row:GroupRoomRow):Int
    @Update suspend fun update(row:GroupRequestRow):Int
    @Update suspend fun update(row:GroupMessageRow):Int
    @Query("DELETE FROM group_members WHERE groupId=:id AND spaceId=:space") suspend fun deleteMembers(id:String,space:String)
    @Query("DELETE FROM group_members WHERE groupId=:id AND spaceId=:space AND roleId=:role AND roleSpaceId=:roleSpace") suspend fun deleteMember(id:String,space:String,role:String,roleSpace:String)
    @Query("DELETE FROM group_rooms WHERE groupId=:id AND spaceId=:space AND version=:version") suspend fun delete(id:String,space:String,version:Long):Int
}
