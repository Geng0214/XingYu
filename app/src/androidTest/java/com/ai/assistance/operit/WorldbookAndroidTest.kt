package com.ai.assistance.operit

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.api.chat.RoomRequestStore
import com.ai.assistance.operit.data.db.*
import com.ai.assistance.operit.data.model.*
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.*
import org.junit.Test
import java.security.SecureRandom
import java.util.UUID

class WorldbookAndroidTest {
    @Test fun sqlCipherLoreIsScopedVersionedFrozenAndDeletedWithItsOwner()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        System.loadLibrary("sqlcipher");val name="worldbook-${UUID.randomUUID()}.db"
        val key=ByteArray(32).also {SecureRandom().nextBytes(it)}
        val db=Room.databaseBuilder(context,AppDatabase::class.java,name).openHelperFactory(SupportOpenHelperFactory(key)).build()
        try {
            val roles=RoleRepository(db);val lore=WorldbookRepository(db);val requests=RoomRequestStore(db)
            db.dao().saveConfig(ModelConfigData("cfg","test","OpenAI","https://example.invalid/v1/chat/completions","test",60,"ref"))
            val a=roles.saveRole(null,"same","persona","","cfg");val b=roles.saveRole(null,"same","persona","","cfg")
            val ca=roles.createChat(a.scope(),"cfg");val cb=roles.createChat(b.scope(),"cfg")
            for(r in listOf(a,b))repeat(10){i->lore.save(r.scope(),null,null,null,"entry $i","${r.id}-WORLD-$i","always","",true,50)}
            repeat(50){i->val r=if(i%2==0)a else b;val c=if(i%2==0)ca else cb;val foreign=if(i%2==0)b else a
                val request=requests.prepare(r.scope(),c.id,"hello",UUID.randomUUID().toString())
                repeat(10){n->assertTrue(request.turns.first().content.contains("${r.id}-WORLD-$n"));assertFalse(request.turns.first().content.contains("${foreign.id}-WORLD-$n"))}
                requests.update(request,"done",MessageState.COMPLETE,"",1,2)
            }
            try {lore.save(RoleAccessScope(a.id,b.spaceId),null,null,null,"bad","bad","always","",true,50);fail()}catch(e:ChatFailure){assertEquals("ACCESS",e.code)}
            try {lore.save(a.scope(),null,null,cb.id,"bad","bad","always","",true,50);fail()}catch(e:ChatFailure){assertEquals("ACCESS",e.code)}
            val entry=db.worldbook().read(a.id,a.spaceId).first()
            val frozen=requests.prepare(a.scope(),ca.id,"in-flight",UUID.randomUUID().toString())
            val changed=lore.save(a.scope(),entry.id,entry.version,null,entry.title,"edited","always","",false,50)
            assertTrue(frozen.turns.first().content.contains(entry.content))
            assertFalse(requests.prepare(a.scope(),ca.id,"retry",UUID.randomUUID().toString()).turns.first().content.contains(entry.content))
            try {lore.delete(a.scope(),entry.id,entry.version);fail()}catch(e:ChatFailure){assertEquals("WORLDBOOK_CHANGED",e.code)}
            assertEquals(changed,db.worldbook().read(a.id,a.spaceId).single {it.id==entry.id})
            val template=WorldbookTemplate(listOf(WorldbookTemplateEntry("same","imported","always","",50)))
            lore.import(a.scope(),template);lore.import(a.scope(),template)
            val imported=db.worldbook().read(a.id,a.spaceId).filter {it.content=="imported"}
            assertEquals(2,imported.size);assertNotEquals(imported[0].id,imported[1].id);assertTrue(imported.none {it.enabled})
            val copy=roles.copyRole(a.scope());assertTrue(db.worldbook().read(copy.id,copy.spaceId).isEmpty())
            val session=lore.save(a.scope(),null,null,ca.id,"session","session-only","always","",true,100)
            roles.deleteChat(a.scope(),ca.id)
            assertFalse(db.worldbook().read(a.id,a.spaceId).any {it.id==session.id})
            assertEquals(12,db.worldbook().read(a.id,a.spaceId).size)
            requests.update(frozen,"late",MessageState.COMPLETE,"",1,2)
            assertTrue(db.dao().history(a.id,a.spaceId,ca.id).isEmpty())
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
            for(suffix in listOf("","-wal","-shm")){val file=java.io.File(context.getDatabasePath(name).path+suffix)
                if(file.exists()){val raw=file.readBytes().toString(Charsets.UTF_8);assertFalse(raw.contains("-WORLD-"));assertFalse(raw.contains("imported"))}}
            roles.deleteRole(a.scope());assertTrue(db.worldbook().read(a.id,a.spaceId).isEmpty())
            assertEquals(10,db.worldbook().read(b.id,b.spaceId).size)
        }finally {db.close();context.deleteDatabase(name)}
    }
}
