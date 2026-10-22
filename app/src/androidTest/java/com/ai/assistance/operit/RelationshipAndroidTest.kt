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

class RelationshipAndroidTest {
    @Test fun scopedRelationsAndRawModeKeepDataGrantsAndInFlightRequests()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        System.loadLibrary("sqlcipher");val name="relations-${UUID.randomUUID()}.db"
        val key=ByteArray(32).also {SecureRandom().nextBytes(it)}
        val db=Room.databaseBuilder(context,AppDatabase::class.java,name).openHelperFactory(SupportOpenHelperFactory(key)).build()
        try {
            val roles=RoleRepository(db);val relations=RelationshipRepository(db);val requests=RoomRequestStore(db)
            db.dao().saveConfig(ModelConfigData("cfg","test","OpenAI","https://example.invalid/v1/chat/completions","test",60,"ref"))
            val a=roles.saveRole(null,"same","private persona","private opening","cfg");val b=roles.saveRole(null,"same","B persona","","cfg")
            val ca=roles.createChat(a.scope(),"cfg");val cb=roles.createChat(b.scope(),"cfg")
            for(r in listOf(a,b))repeat(10){i->relations.save(r.scope(),null,null,null,"Me","AI","friend $i","${r.id}-REL-$i",true,50)}
            repeat(50){i->val r=if(i%2==0)a else b;val c=if(i%2==0)ca else cb;val foreign=if(i%2==0)b else a
                val request=requests.prepare(r.scope(),c.id,"hello",UUID.randomUUID().toString())
                repeat(10){n->assertTrue(request.turns.first().content.contains("${r.id}-REL-$n"));assertFalse(request.turns.first().content.contains("${foreign.id}-REL-$n"))}
                requests.update(request,"done",MessageState.COMPLETE,"",1,2)
            }
            db.extensions().saveExtension(McpExtension("ext","echo","https://example.invalid/mcp",null,1000,true))
            db.extensions().insertTools(listOf(ExtensionTool("ext","echo","echo","{\"type\":\"object\"}")))
            db.extensions().saveGrant(RoleToolGrant(a.id,a.spaceId,"ext","echo",true,1))
            val frozen=requests.prepare(a.scope(),ca.id,"before",UUID.randomUUID().toString());assertEquals(1,frozen.tools.size)
            val current=roles.requireRole(a.scope())
            val raw=roles.setConversationMode(a.scope(),current.version,"raw")
            assertEquals("private persona",raw.prompt);assertEquals("private opening",raw.openingMessage)
            assertTrue(frozen.turns.first().content.contains("-REL-"));assertFalse(frozen.role.isRaw())
            val rawChat=roles.createChat(a.scope(),"cfg");assertEquals(ca.id,rawChat.id)
            val next=requests.prepare(a.scope(),rawChat.id,"raw hello",UUID.randomUUID().toString())
            assertEquals(PromptTurn("user","raw hello"),next.turns.last());assertTrue(next.tools.isEmpty())
            assertEquals("paragraph",db.dao().history(a.id,a.spaceId,rawChat.id).last().bubbleStyle)
            assertTrue(db.extensions().grant(a.id,a.spaceId,"ext","echo")!!.enabled)
            db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM request_tool_permissions WHERE requestId=?",arrayOf(next.requestId)).use {assertTrue(it.moveToFirst());assertEquals(0,it.getInt(0))}
            try {roles.setConversationMode(a.scope(),current.version,"character");fail()}catch(e:ChatFailure){assertEquals("ROLE_CHANGED",e.code)}
            try {relations.save(a.scope(),null,null,cb.id,"Me","AI","bad","",true,50);fail()}catch(e:ChatFailure){assertEquals("ACCESS",e.code)}
            try {relations.save(RoleAccessScope(a.id,b.spaceId),null,null,null,"Me","AI","bad","",true,50);fail()}catch(e:ChatFailure){assertEquals("ACCESS",e.code)}
            val item=db.relationships().read(a.id,a.spaceId).first()
            val edited=relations.save(a.scope(),item.id,item.version,null,"Me","AI","partner","edited relation",true,50)
            try {relations.delete(a.scope(),item.id,item.version);fail()}catch(e:ChatFailure){assertEquals("RELATIONSHIP_CHANGED",e.code)}
            assertEquals(edited,db.relationships().read(a.id,a.spaceId).single {it.id==item.id})
            val copy=roles.copyRole(a.scope());assertTrue(copy.isRaw());assertTrue(db.relationships().read(copy.id,copy.spaceId).isEmpty())
            val switchedBack=roles.setConversationMode(a.scope(),roles.requireRole(a.scope()).version,"character")
            val restored=requests.prepare(a.scope(),ca.id,"back",UUID.randomUUID().toString())
            assertEquals(1,restored.tools.size);assertTrue(restored.turns.first().content.contains("edited relation"))
            assertFalse(switchedBack.isRaw())
            relations.save(a.scope(),null,null,rawChat.id,"Me","AI","session","session relation",true,50)
            roles.deleteChat(a.scope(),rawChat.id);assertEquals(10,db.relationships().read(a.id,a.spaceId).size)
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
            for(suffix in listOf("","-wal","-shm")){val file=java.io.File(context.getDatabasePath(name).path+suffix)
                if(file.exists()){val rawBytes=file.readBytes().toString(Charsets.UTF_8);assertFalse(rawBytes.contains("-REL-"));assertFalse(rawBytes.contains("edited relation"))}}
            roles.deleteRole(a.scope());requests.update(frozen,"late",MessageState.COMPLETE,"",1,2)
            assertTrue(db.dao().history(a.id,a.spaceId,ca.id).isEmpty());assertTrue(db.relationships().read(a.id,a.spaceId).isEmpty())
            assertEquals(10,db.relationships().read(b.id,b.spaceId).size)
        }finally {db.close();context.deleteDatabase(name)}
    }
}
