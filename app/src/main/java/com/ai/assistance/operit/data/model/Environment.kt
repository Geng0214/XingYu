package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class WeatherCandidate(
    val id:Long,val name:String,val admin1:String,val country:String,val countryCode:String,
    val latitude:Double,val longitude:Double,val timezone:String
) {
    fun label()=listOf(name,admin1,country).filter {it.isNotBlank()}.distinct().joinToString(" · ")
}

data class WeatherSnapshot(
    val weatherTime:String,val temperature:Double,val apparentTemperature:Double,val humidity:Long,
    val precipitation:Double,val weatherCode:Long,val windSpeed:Double,val todayMin:Double,
    val todayMax:Double,val todayPrecipitationProbability:Long,val fetchedAt:Long
)

@Entity(tableName="environment_profiles",indices=[Index(value=["spaceId"],unique=true)])
data class EnvironmentProfile(
    @PrimaryKey val roleId:String,val spaceId:String,
    val timeContextEnabled:Boolean=false,val weatherContextEnabled:Boolean=false,
    val provider:String="open-meteo",val locationId:Long?=null,val locationName:String?=null,
    val admin1:String?=null,val country:String?=null,val countryCode:String?=null,
    val latitude:Double?=null,val longitude:Double?=null,val timezone:String?=null,
    val weatherTime:String?=null,val temperature:Double?=null,val apparentTemperature:Double?=null,
    val humidity:Long?=null,val precipitation:Double?=null,val weatherCode:Long?=null,
    val windSpeed:Double?=null,val todayMin:Double?=null,val todayMax:Double?=null,
    val todayPrecipitationProbability:Long?=null,val fetchedAt:Long?=null,
    val version:Long=1,val updatedAt:Long=System.currentTimeMillis()
) {
    fun scope()=RoleAccessScope(roleId,spaceId)
    fun candidate()=if(locationId==null)null else WeatherCandidate(locationId,requireNotNull(locationName),admin1.orEmpty(),requireNotNull(country),requireNotNull(countryCode),requireNotNull(latitude),requireNotNull(longitude),requireNotNull(timezone))
    fun snapshot()=if(fetchedAt==null)null else WeatherSnapshot(requireNotNull(weatherTime),requireNotNull(temperature),requireNotNull(apparentTemperature),requireNotNull(humidity),requireNotNull(precipitation),requireNotNull(weatherCode),requireNotNull(windSpeed),requireNotNull(todayMin),requireNotNull(todayMax),requireNotNull(todayPrecipitationProbability),fetchedAt)
}

data class EnvironmentSelection(
    val instruction:String="",val timeState:String="disabled",val weatherState:String="disabled",
    val frozenAt:Long=0,val deviceZone:String="",val profileVersion:Long?=null
)

object Environments {
    const val PROVIDER="open-meteo"
    const val CITY_LIMIT=120
    const val RESPONSE_LIMIT=512*1024
    const val STALE_MS=6*60*60*1000L
    const val LOCATION_TEXT_LIMIT=160
    private val localWeatherTime=Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}")
    private val formatter=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss XXX '['VV']'")

    fun validateCity(value:String) {
        require(value==value.trim()&&value.length in 2..CITY_LIMIT&&value.none {it=='\u0000'||it.isISOControl()})
    }

    private fun validateText(value:String,allowEmpty:Boolean=false) {
        require(value.length<=LOCATION_TEXT_LIMIT&&(allowEmpty||value.isNotBlank())&&value.none {it=='\u0000'||it.isISOControl()})
    }

    fun validateCandidate(value:WeatherCandidate) {
        require(value.id>0);validateText(value.name);validateText(value.admin1,true);validateText(value.country)
        require(Regex("[A-Z]{2}").matches(value.countryCode))
        require(value.latitude.isFinite()&&value.latitude in -90.0..90.0&&value.longitude.isFinite()&&value.longitude in -180.0..180.0)
        require(value.timezone.length<=80&&ZoneId.of(value.timezone).id==value.timezone)
    }

    fun validateSnapshot(value:WeatherSnapshot) {
        require(localWeatherTime.matches(value.weatherTime))
        require(value.temperature.isFinite()&&value.temperature in -100.0..100.0)
        require(value.apparentTemperature.isFinite()&&value.apparentTemperature in -120.0..120.0)
        require(value.humidity in 0..100&&value.precipitation.isFinite()&&value.precipitation in 0.0..1000.0)
        require(value.weatherCode in 0..99&&value.windSpeed.isFinite()&&value.windSpeed in 0.0..500.0)
        require(value.todayMin.isFinite()&&value.todayMin in -100.0..100.0&&value.todayMax.isFinite()&&value.todayMax in -100.0..100.0&&value.todayMin<=value.todayMax)
        require(value.todayPrecipitationProbability in 0..100&&value.fetchedAt>=0)
    }

    fun validateStored(value:EnvironmentProfile) {
        require(value.roleId.isNotBlank()&&value.spaceId.isNotBlank()&&value.provider==PROVIDER&&value.version>0&&value.updatedAt>=0)
        val locationFields=listOf(value.locationId,value.locationName,value.country,value.countryCode,value.latitude,value.longitude,value.timezone)
        require(locationFields.all {it==null}||locationFields.all {it!=null})
        value.candidate()?.let(::validateCandidate)
        val snapshotFields=listOf(value.weatherTime,value.temperature,value.apparentTemperature,value.humidity,value.precipitation,value.weatherCode,value.windSpeed,value.todayMin,value.todayMax,value.todayPrecipitationProbability,value.fetchedAt)
        require(snapshotFields.all {it==null}||snapshotFields.all {it!=null})
        value.snapshot()?.let(::validateSnapshot)
        require(!value.weatherContextEnabled||value.snapshot()!=null)
    }

    fun select(profile:EnvironmentProfile?,raw:Boolean,now:Long=System.currentTimeMillis(),zone:ZoneId=ZoneId.systemDefault()):EnvironmentSelection {
        if(raw||profile==null)return EnvironmentSelection(timeState=if(raw)"raw" else "disabled",weatherState=if(raw)"raw" else "disabled",frozenAt=now,deviceZone=zone.id,profileVersion=profile?.version)
        validateStored(profile)
        val lines=mutableListOf<String>()
        val timeState=if(profile.timeContextEnabled) {
            lines += "确定的本机时间：${Instant.ofEpochMilli(now).atZone(zone).format(formatter)}。"
            "included"
        } else "disabled"
        val snapshot=profile.snapshot();val candidate=profile.candidate()
        val weatherState=when {
            !profile.weatherContextEnabled->"disabled"
            snapshot==null||candidate==null->"missing"
            now-snapshot.fetchedAt !in 0..STALE_MS->"stale"
            else->{
                lines += "用户确认的天气快照：${candidate.label()}；服务时间 ${snapshot.weatherTime}（${candidate.timezone}）；${WeatherCodes.label(snapshot.weatherCode)}，${format(snapshot.temperature)}°C，体感 ${format(snapshot.apparentTemperature)}°C，湿度 ${snapshot.humidity}%，降水 ${format(snapshot.precipitation)} mm，风速 ${format(snapshot.windSpeed)} km/h；今日 ${format(snapshot.todayMin)}–${format(snapshot.todayMax)}°C，最高降水概率 ${snapshot.todayPrecipitationProbability}%。来源 Open-Meteo，获取于 ${Instant.ofEpochMilli(snapshot.fetchedAt).atZone(zone).format(formatter)}；这是预报数据，可能不准确。"
                "included"
            }
        }
        return EnvironmentSelection(lines.joinToString("\n"),timeState,weatherState,now,zone.id,profile.version)
    }

    fun format(value:Double)=if(value%1.0==0.0)value.toLong().toString() else "%.1f".format(java.util.Locale.ROOT,value)
}

object WeatherCodes {
    fun label(code:Long)=when(code) {
        0L->"晴";1L->"大部晴朗";2L->"多云";3L->"阴";45L,48L->"雾"
        51L,53L,55L->"毛毛雨";56L,57L->"冻毛毛雨";61L,63L,65L->"雨";66L,67L->"冻雨"
        71L,73L,75L,77L->"雪";80L,81L,82L->"阵雨";85L,86L->"阵雪";95L,96L,99L->"雷暴"
        else->"天气码 $code"
    }
}
