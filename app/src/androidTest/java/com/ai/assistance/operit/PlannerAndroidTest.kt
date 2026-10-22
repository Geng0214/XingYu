package com.ai.assistance.operit

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.data.db.*
import com.ai.assistance.operit.data.model.*
import java.security.SecureRandom
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.*
import org.junit.Test

class PlannerAndroidTest {
    @Test fun plannerCycleOwnershipCopyDeletionAndEncryptedMarkersAreIsolated()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        System.loadLibrary("sqlcipher");val name="planner-${UUID.randomUUID()}.db";val key=ByteArray(32).also {SecureRandom().nextBytes(it)}
        val db=Room.databaseBuilder(context,AppDatabase::class.java,name).openHelperFactory(SupportOpenHelperFactory(key.copyOf())).build()
        try {
            val roles=RoleRepository(db);val repo=PlannerRepository(db);val a=roles.saveRole(null,"A","persona","",null);val b=roles.saveRole(null,"B","persona","",null)
            val plan=repo.savePlanner(a.scope(),null,"task","PLANNER-PRIVATE-A","note",2_000_000,null,"Asia/Shanghai",null,10,true,clock={100})
            val cycle=repo.saveCycle(a.scope(),null,LocalDate.of(2026,9,1).toEpochDay(),LocalDate.of(2026,9,4).toEpochDay(),"medium","CYCLE-PRIVATE-A",false,clock={101})
            assertEquals(listOf(plan),db.planner().observePlanner(a.id,a.spaceId).first());assertEquals(listOf(cycle),db.planner().observeCycles(a.id,a.spaceId).first())
            assertTrue(db.planner().readPlanner(b.id,b.spaceId).isEmpty());assertNull(db.planner().planner(a.id,"foreign",plan.id))
            val updated=repo.savePlanner(a.scope(),plan,"task",plan.title,plan.note,plan.startsAt,null,plan.zoneId,200,plan.reminderMinutesBefore,plan.shareWithRole,clock={102});assertEquals(2,updated.version)
            try {repo.savePlanner(a.scope(),plan,"task",plan.title,plan.note,plan.startsAt,null,plan.zoneId,null,null,false);fail()}catch(e:ChatFailure){assertEquals("PLANNER_CHANGED",e.code)}
            val copied=roles.copyRole(a.scope());assertTrue(db.planner().readPlanner(copied.id,copied.spaceId).isEmpty());assertTrue(db.planner().readCycles(copied.id,copied.spaceId).isEmpty())
            roles.deleteRole(a.scope());assertTrue(db.planner().readPlanner(a.id,a.spaceId).isEmpty());assertTrue(db.planner().readCycles(a.id,a.spaceId).isEmpty())
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
            for(suffix in listOf("","-wal","-shm")){val file=java.io.File(context.getDatabasePath(name).path+suffix);if(file.exists()){val text=file.readBytes().toString(Charsets.UTF_8);assertFalse(text.contains("PLANNER-PRIVATE"));assertFalse(text.contains("CYCLE-PRIVATE"))}}
        } finally {db.close();context.deleteDatabase(name)}
    }
}
