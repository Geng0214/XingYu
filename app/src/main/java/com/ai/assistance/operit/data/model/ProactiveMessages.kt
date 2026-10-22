package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.ZoneId

@Entity(tableName="proactive_role_settings",indices=[Index(value=["roleId","spaceId"],unique=true),Index("chatId"),Index("configId"),Index("nextEligibleAt")])
data class ProactiveRoleSetting(
    @PrimaryKey val roleId:String,
    val spaceId:String,
    val enabled:Boolean=false,
    val chatId:String,
    val configId:String,
    val quietStartMinute:Int=1320,
    val quietEndMinute:Int=480,
    val minIntervalHours:Int=24,
    val dailyLimit:Int=1,
    val nextEligibleAt:Long=0,
    val version:Long=1,
    val updatedAt:Long=System.currentTimeMillis()
)

@Entity(tableName="proactive_requests",indices=[
    Index(value=["roleId","dueAt"],unique=true),Index(value=["roleId","spaceId"]),Index("chatId"),Index("assistantId"),Index("configId"),Index("createdAt")])
data class ProactiveRequestEntity(
    @PrimaryKey val requestId:String,
    val roleId:String,
    val spaceId:String,
    val chatId:String,
    val assistantId:String,
    val configId:String,
    val configVersion:Long,
    val dueAt:Long,
    val dayKey:String,
    val state:String="PENDING",
    val errorCode:String="",
    val notificationSent:Boolean=false,
    val createdAt:Long,
    val finishedAt:Long=0
)

data class ProactiveCounts(val dayCount:Long)

object ProactiveMessages {
    const val MAX_OUTPUT_TOKENS=256
    val intervals=setOf(6,12,24,48,72)
    fun validate(setting:ProactiveRoleSetting) {
        require(setting.roleId.isNotBlank()&&setting.spaceId.isNotBlank()&&setting.chatId.isNotBlank()&&setting.configId.isNotBlank())
        require(setting.quietStartMinute in 0..1439&&setting.quietEndMinute in 0..1439)
        require(setting.minIntervalHours in intervals&&setting.dailyLimit in 1..3)
        require(setting.nextEligibleAt>=0&&setting.version>0&&setting.updatedAt>=0)
    }
    fun dayKey(now:Long,zone:ZoneId):String {
        val date=Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return date.toString()
    }
    fun quiet(now:Long,zone:ZoneId,start:Int,end:Int):Boolean {
        require(start in 0..1439&&end in 0..1439)
        if(start==end)return false
        val local=Instant.ofEpochMilli(now).atZone(zone).toLocalTime()
        val minute=local.hour*60+local.minute
        return if(start<end)minute in start until end else minute>=start||minute<end
    }
    fun nextEligibleAt(now:Long,intervalHours:Int):Long {require(now>=0&&intervalHours in intervals);return Math.addExact(now,intervalHours*60L*60L*1000L)}
    fun eligible(setting:ProactiveRoleSetting,counts:ProactiveCounts,globalDayCount:Long,globalDailyLimit:Int,now:Long,zone:ZoneId):Boolean {
        validate(setting)
        require(globalDailyLimit in 1..10&&counts.dayCount>=0&&globalDayCount>=0)
        return setting.enabled&&now>=setting.nextEligibleAt&&!quiet(now,zone,setting.quietStartMinute,setting.quietEndMinute)&&
            counts.dayCount<setting.dailyLimit&&globalDayCount<globalDailyLimit
    }
}
