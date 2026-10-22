package com.ai.assistance.operit

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.db.EnvironmentRepository
import com.ai.assistance.operit.data.db.RoleRepository
import com.ai.assistance.operit.data.model.*
import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.*
import org.junit.Test

class EnvironmentAndroidTest {
    @Test fun timeWeatherOwnershipTogglesDeletionAndEncryptedStorageAreIsolated()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        System.loadLibrary("sqlcipher");val name="environment-${UUID.randomUUID()}.db";val key=ByteArray(32).also {SecureRandom().nextBytes(it)}
        val db=Room.databaseBuilder(context,AppDatabase::class.java,name).openHelperFactory(SupportOpenHelperFactory(key.copyOf())).build()
        try {
            val roles=RoleRepository(db);val environments=EnvironmentRepository(db)
            val a=roles.saveRole(null,"A","A persona","",null);val b=roles.saveRole(null,"B","B persona","",null)
            try {environments.setToggles(a.scope(),true,true);fail()}catch(e:ChatFailure){assertEquals("WEATHER_MISSING",e.code)}
            val timeOnly=environments.setToggles(a.scope(),true,false,clock={100});assertTrue(timeOnly.timeContextEnabled)
            val candidate=WeatherCandidate(1,"WEATHER-PRIVATE-A","浙江","中国","CN",30.2,120.1,"Asia/Shanghai")
            val snapshot=WeatherSnapshot("2026-09-20T18:00",26.0,27.0,60,0.0,2,8.0,20.0,29.0,30,200)
            val stored=environments.saveWeather(a.scope(),candidate,snapshot,clock={201});assertEquals(candidate,stored.candidate());assertEquals(snapshot,stored.snapshot())
            val enabled=environments.setToggles(a.scope(),true,true,clock={202});assertTrue(enabled.weatherContextEnabled)
            assertEquals(enabled,db.environment().observe(a.id,a.spaceId).first());assertNull(db.environment().profile(b.id,b.spaceId));assertNull(db.environment().profile(a.id,"foreign"))
            val copied=roles.copyRole(a.scope());assertNull(db.environment().profile(copied.id,copied.spaceId))
            val cleared=environments.clearWeather(a.scope(),clock={203});assertNull(cleared.candidate());assertFalse(cleared.weatherContextEnabled);assertTrue(cleared.timeContextEnabled)
            environments.saveWeather(a.scope(),candidate,snapshot,clock={204});roles.deleteRole(a.scope());assertNull(db.environment().profile(a.id,a.spaceId));assertNull(db.environment().profile(b.id,b.spaceId))
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
            for(suffix in listOf("","-wal","-shm")){val file=java.io.File(context.getDatabasePath(name).path+suffix);if(file.exists())assertFalse(file.readBytes().toString(Charsets.UTF_8).contains("WEATHER-PRIVATE"))}
        } finally {db.close();context.deleteDatabase(name)}
    }
}
