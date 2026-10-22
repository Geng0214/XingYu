package com.ai.assistance.operit

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.api.chat.RoomRequestStore
import com.ai.assistance.operit.data.db.*
import com.ai.assistance.operit.data.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.*
import org.junit.Test
import java.security.SecureRandom
import java.util.UUID

class RoleIsolationAndroidTest {
    @Test fun fiftyAlternationsRejectCrossRoleQueriesAndWrites()=runBlocking {
        System.loadLibrary("sqlcipher")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="isolation-"+UUID.randomUUID()+".db"
        val key=ByteArray(32).also { SecureRandom().nextBytes(it) }
        val db=Room.databaseBuilder(context,AppDatabase::class.java,name).openHelperFactory(SupportOpenHelperFactory(key)).build()
        try {
            val repo=RoleRepository(db);val dao=db.dao();val store=RoomRequestStore(db)
            dao.saveConfig(ModelConfigData("config","test","OpenAI 兼容","https://example.com/v1/chat/completions","test",90,"synthetic-ref"))
            val a=repo.saveRole(null,"same","persona-a","opening-a","config")
            val b=repo.saveRole(null,"same","persona-b","opening-b","config")
            val ca=repo.createChat(a.scope(),"config");val cb=repo.createChat(b.scope(),"config")
            for((role,chat) in listOf(a to ca,b to cb)) repeat(10) { n ->
                val request=store.prepare(role.scope(),chat.id,"marker-"+role.id+"-"+n,UUID.randomUUID().toString())
                store.update(request,"answer-"+role.id+"-"+n,MessageState.COMPLETE,"",1,2)
            }
            repeat(50) { n ->
                val own=if(n%2==0)a else b;val other=if(n%2==0)b else a
                val chat=if(n%2==0)ca else cb;val foreign=if(n%2==0)cb else ca
                assertNull(dao.chat(own.id,own.spaceId,foreign.id))
                assertTrue(dao.history(own.id,own.spaceId,foreign.id).isEmpty())
                assertTrue(dao.messages(own.id,own.spaceId,foreign.id).first().isEmpty())
                assertTrue(dao.history(own.id,other.spaceId,chat.id).isEmpty())
                try { store.prepare(own.scope(),foreign.id,"forbidden",UUID.randomUUID().toString());fail() }
                catch(e:ChatFailure) { assertEquals("ACCESS",e.code) }
                val request=store.prepare(own.scope(),chat.id,"probe",UUID.randomUUID().toString())
                assertFalse(request.turns.any { it.content.contains(other.id) || it.content.contains(other.prompt) || it.content.contains(other.openingMessage) })
                repeat(10) { marker -> assertTrue(request.turns.any { it.content=="marker-"+own.id+"-"+marker }) }
                assertEquals(own.scope(),request.scope)
                assertEquals(0,dao.updateActive(other.id,other.spaceId,chat.id,request.requestId,request.assistantId,"injected",MessageState.COMPLETE,"",1,2))
                assertEquals(0,dao.updateActive(own.id,own.spaceId,chat.id,"wrong-request",request.assistantId,"injected",MessageState.COMPLETE,"",1,2))
                store.update(request,"cancelled",MessageState.CANCELLED,"",1,2)
                store.update(request,"late",MessageState.COMPLETE,"",1,3)
                assertEquals("cancelled",dao.history(own.id,own.spaceId,chat.id).last().content)
            }
            // Snapshot opening stays with the conversation after editing the role.
            repo.saveRole(a.scope(),a.name,a.prompt,"changed-opening","config")
            val request=store.prepare(a.scope(),ca.id,"opening-check",UUID.randomUUID().toString())
            assertTrue(request.turns.any { it.content=="opening-a" })
            assertFalse(request.turns.any { it.content=="changed-opening" })
            val copy=repo.copyRole(a.scope())
            assertNotEquals(a.id,copy.id);assertNotEquals(a.spaceId,copy.spaceId)
            assertEquals(0,dao.chatCount(copy.id,copy.spaceId));assertEquals("config",copy.defaultConfigId)
            val template=RoleTemplateCodec.decode(RoleTemplateCodec.encode(RoleTemplateCodec.fromRole(a)).toByteArray()).template
            val imports=(1..2).map { repo.saveRole(null,template.name,template.prompt,template.openingMessage,null) }
            assertNotEquals(imports[0].id,imports[1].id);assertNotEquals(imports[0].spaceId,imports[1].spaceId)
            assertTrue(imports.all { it.defaultConfigId==null && dao.chatCount(it.id,it.spaceId)==0 })
            repo.deleteRole(a.scope())
            assertNull(dao.role(a.id));assertNotNull(dao.config("config"));assertEquals(1,dao.chatCount(b.id,b.spaceId))
            store.update(request,"after-delete",MessageState.COMPLETE,"",1,4)
            val replacement=repo.saveRole(null,a.name,a.prompt,"",null)
            assertNotEquals(a.id,replacement.id);assertNotEquals(a.spaceId,replacement.spaceId)
            try { repo.createChat(a.scope(),"config");fail() } catch(e:ChatFailure) { assertEquals("ACCESS",e.code) }
        } finally { db.close();context.deleteDatabase(name) }
    }
}
