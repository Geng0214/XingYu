package com.ai.assistance.operit.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.ai.assistance.operit.data.model.ScheduledMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduledMessageDao {
    @Query("SELECT s.* FROM scheduled_messages s JOIN roles r ON r.id=s.roleId AND r.spaceId=s.spaceId WHERE r.id=:roleId AND r.spaceId=:spaceId ORDER BY s.dueAt")
    fun messages(roleId:String,spaceId:String):Flow<List<ScheduledMessage>>

    @Query("SELECT s.* FROM scheduled_messages s JOIN roles r ON r.id=s.roleId AND r.spaceId=s.spaceId WHERE s.id=:id AND r.id=:roleId AND r.spaceId=:spaceId LIMIT 1")
    suspend fun message(roleId:String,spaceId:String,id:String):ScheduledMessage?

    @Query("SELECT * FROM scheduled_messages WHERE id=:id LIMIT 1")
    suspend fun byId(id:String):ScheduledMessage?

    @Query("SELECT s.* FROM scheduled_messages s JOIN roles r ON r.id=s.roleId AND r.spaceId=s.spaceId WHERE s.state='PENDING' ORDER BY s.dueAt")
    suspend fun pending():List<ScheduledMessage>

    @Insert suspend fun insert(item:ScheduledMessage)

    @Query("UPDATE scheduled_messages SET dueAt=:dueAt,mode=:mode,content=:content WHERE id=:id AND roleId=:roleId AND spaceId=:spaceId AND state='PENDING' AND EXISTS (SELECT 1 FROM roles WHERE id=:roleId AND spaceId=:spaceId)")
    suspend fun update(roleId:String,spaceId:String,id:String,dueAt:Long,mode:String,content:String):Int

    @Query("UPDATE scheduled_messages SET state=:state,errorCode=:error,finishedAt=:finishedAt WHERE id=:id AND state='PENDING'")
    suspend fun finish(id:String,state:String,error:String,finishedAt:Long):Int

    @Query("UPDATE scheduled_messages SET state='CANCELLED',finishedAt=:finishedAt WHERE id=:id AND roleId=:roleId AND spaceId=:spaceId AND state='PENDING' AND EXISTS (SELECT 1 FROM roles WHERE id=:roleId AND spaceId=:spaceId)")
    suspend fun cancel(roleId:String,spaceId:String,id:String,finishedAt:Long):Int

    @Query("UPDATE scheduled_messages SET notificationSent=1 WHERE id=:id")
    suspend fun markNotified(id:String)
}
