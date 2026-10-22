package com.ai.assistance.operit.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import com.ai.assistance.operit.data.model.ProactiveRequestEntity
import com.ai.assistance.operit.data.model.ProactiveRoleSetting
import kotlinx.coroutines.flow.Flow

@Dao
interface ProactiveDao {
    @Query("SELECT s.* FROM proactive_role_settings s JOIN roles r ON r.id=s.roleId AND r.spaceId=s.spaceId WHERE s.roleId=:roleId AND s.spaceId=:spaceId LIMIT 1")
    fun observeSetting(roleId:String,spaceId:String):Flow<ProactiveRoleSetting?>
    @Query("SELECT s.* FROM proactive_role_settings s JOIN roles r ON r.id=s.roleId AND r.spaceId=s.spaceId WHERE s.roleId=:roleId AND s.spaceId=:spaceId LIMIT 1")
    suspend fun setting(roleId:String,spaceId:String):ProactiveRoleSetting?
    @Query("SELECT s.* FROM proactive_role_settings s JOIN roles r ON r.id=s.roleId AND r.spaceId=s.spaceId JOIN chats c ON c.id=s.chatId AND c.roleId=s.roleId JOIN model_configs m ON m.id=s.configId WHERE s.enabled=1 ORDER BY s.nextEligibleAt,s.updatedAt,s.roleId")
    suspend fun enabledSettings():List<ProactiveRoleSetting>
    @Upsert suspend fun saveSetting(setting:ProactiveRoleSetting)
    @Query("DELETE FROM proactive_role_settings WHERE roleId=:roleId AND spaceId=:spaceId AND chatId=:chatId") suspend fun deleteChatSetting(roleId:String,spaceId:String,chatId:String):Int
    @Query("DELETE FROM proactive_role_settings WHERE roleId=:roleId AND spaceId=:spaceId") suspend fun deleteRoleSetting(roleId:String,spaceId:String):Int
    @Insert suspend fun insertRequest(request:ProactiveRequestEntity)
    @Query("SELECT COUNT(*) FROM proactive_requests WHERE roleId=:roleId AND spaceId=:spaceId AND dayKey=:dayKey") suspend fun roleDayCount(roleId:String,spaceId:String,dayKey:String):Long
    @Query("SELECT COUNT(*) FROM proactive_requests WHERE dayKey=:dayKey") suspend fun globalDayCount(dayKey:String):Long
    @Query("UPDATE proactive_role_settings SET nextEligibleAt=:nextAt,version=version+1,updatedAt=:now WHERE roleId=:roleId AND spaceId=:spaceId AND version=:version AND enabled=1")
    suspend fun reserveWindow(roleId:String,spaceId:String,version:Long,nextAt:Long,now:Long):Int
    @Query("UPDATE proactive_requests SET state=:state,errorCode=:error,notificationSent=:notified,finishedAt=:finishedAt WHERE requestId=:requestId AND roleId=:roleId AND spaceId=:spaceId AND chatId=:chatId AND assistantId=:assistantId AND state='PENDING'")
    suspend fun finish(requestId:String,roleId:String,spaceId:String,chatId:String,assistantId:String,state:String,error:String,notified:Boolean,finishedAt:Long):Int
    @Query("UPDATE proactive_requests SET notificationSent=1 WHERE requestId=:requestId AND roleId=:roleId AND spaceId=:spaceId AND state='COMPLETE'")
    suspend fun markNotified(requestId:String,roleId:String,spaceId:String):Int
    @Query("DELETE FROM proactive_requests WHERE requestId=:requestId AND roleId=:roleId AND spaceId=:spaceId AND chatId=:chatId AND assistantId=:assistantId AND state='PENDING'")
    suspend fun deletePendingRequest(requestId:String,roleId:String,spaceId:String,chatId:String,assistantId:String):Int
    @Query("""UPDATE proactive_role_settings SET nextEligibleAt=:previousNextAt,version=version+1,updatedAt=:now
        WHERE roleId=:roleId AND spaceId=:spaceId AND version=:reservedVersion AND nextEligibleAt=:reservedNextAt""")
    suspend fun releaseWindow(roleId:String,spaceId:String,reservedVersion:Long,reservedNextAt:Long,previousNextAt:Long,now:Long):Int
    @Query("UPDATE proactive_requests SET state='FAILED',errorCode='INTERRUPTED',finishedAt=:now WHERE state='PENDING'") suspend fun recoverInterrupted(now:Long):Int
    @Query("SELECT requestId FROM proactive_requests WHERE roleId=:roleId AND spaceId=:spaceId AND chatId=:chatId") fun requestIds(roleId:String,spaceId:String,chatId:String):Flow<List<String>>
    @Query("DELETE FROM proactive_requests WHERE roleId=:roleId AND spaceId=:spaceId AND chatId=:chatId") suspend fun deleteChatRequests(roleId:String,spaceId:String,chatId:String)
    @Query("DELETE FROM proactive_requests WHERE roleId=:roleId AND spaceId=:spaceId") suspend fun deleteRoleRequests(roleId:String,spaceId:String)
}
