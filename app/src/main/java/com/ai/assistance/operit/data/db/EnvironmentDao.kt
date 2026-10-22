package com.ai.assistance.operit.data.db

import androidx.room.*
import com.ai.assistance.operit.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EnvironmentDao {
    @Query("SELECT e.* FROM environment_profiles e JOIN roles r ON r.id=e.roleId AND r.spaceId=e.spaceId WHERE r.id=:roleId AND r.spaceId=:spaceId LIMIT 1")
    fun observe(roleId:String,spaceId:String):Flow<EnvironmentProfile?>
    @Query("SELECT e.* FROM environment_profiles e JOIN roles r ON r.id=e.roleId AND r.spaceId=e.spaceId WHERE r.id=:roleId AND r.spaceId=:spaceId LIMIT 1")
    suspend fun profile(roleId:String,spaceId:String):EnvironmentProfile?
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun save(profile:EnvironmentProfile)
    @Query("DELETE FROM environment_profiles WHERE roleId=:roleId AND spaceId=:spaceId") suspend fun deleteRole(roleId:String,spaceId:String):Int
}

class EnvironmentRepository(private val db:AppDatabase) {
    private val roles=RoleRepository(db)

    suspend fun setToggles(scope:RoleAccessScope,time:Boolean,weather:Boolean,clock:()->Long={System.currentTimeMillis()}):EnvironmentProfile=db.withTransaction {
        roles.requireRole(scope);val current=db.environment().profile(scope.roleId,scope.spaceId)
        if(weather&&current?.snapshot()==null)throw ChatFailure("WEATHER_MISSING")
        val updated=(current?:EnvironmentProfile(scope.roleId,scope.spaceId)).copy(timeContextEnabled=time,weatherContextEnabled=weather,
            version=(current?.version?:0)+1,updatedAt=clock().coerceAtLeast((current?.updatedAt?:-1)+1))
        Environments.validateStored(updated);db.environment().save(updated);updated
    }

    suspend fun saveWeather(scope:RoleAccessScope,candidate:WeatherCandidate,snapshot:WeatherSnapshot,clock:()->Long={System.currentTimeMillis()}):EnvironmentProfile=db.withTransaction {
        roles.requireRole(scope);Environments.validateCandidate(candidate);Environments.validateSnapshot(snapshot)
        val current=db.environment().profile(scope.roleId,scope.spaceId);val now=clock()
        val updated=(current?:EnvironmentProfile(scope.roleId,scope.spaceId)).copy(provider=Environments.PROVIDER,
            locationId=candidate.id,locationName=candidate.name,admin1=candidate.admin1,country=candidate.country,countryCode=candidate.countryCode,
            latitude=candidate.latitude,longitude=candidate.longitude,timezone=candidate.timezone,
            weatherTime=snapshot.weatherTime,temperature=snapshot.temperature,apparentTemperature=snapshot.apparentTemperature,humidity=snapshot.humidity,
            precipitation=snapshot.precipitation,weatherCode=snapshot.weatherCode,windSpeed=snapshot.windSpeed,todayMin=snapshot.todayMin,todayMax=snapshot.todayMax,
            todayPrecipitationProbability=snapshot.todayPrecipitationProbability,fetchedAt=snapshot.fetchedAt,weatherContextEnabled=current?.weatherContextEnabled?:false,
            version=(current?.version?:0)+1,updatedAt=now.coerceAtLeast((current?.updatedAt?:-1)+1))
        Environments.validateStored(updated);db.environment().save(updated);updated
    }

    suspend fun clearWeather(scope:RoleAccessScope,clock:()->Long={System.currentTimeMillis()}):EnvironmentProfile=db.withTransaction {
        roles.requireRole(scope);val current=db.environment().profile(scope.roleId,scope.spaceId)?:EnvironmentProfile(scope.roleId,scope.spaceId)
        val updated=current.copy(weatherContextEnabled=false,locationId=null,locationName=null,admin1=null,country=null,countryCode=null,latitude=null,longitude=null,timezone=null,
            weatherTime=null,temperature=null,apparentTemperature=null,humidity=null,precipitation=null,weatherCode=null,windSpeed=null,todayMin=null,todayMax=null,todayPrecipitationProbability=null,fetchedAt=null,
            version=current.version+1,updatedAt=clock().coerceAtLeast(current.updatedAt+1))
        Environments.validateStored(updated);db.environment().save(updated);updated
    }
}
