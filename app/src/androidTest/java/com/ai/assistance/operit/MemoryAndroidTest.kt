package com.ai.assistance.operit

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.api.chat.RoomRequestStore
import com.ai.assistance.operit.api.chat.FrozenMemoryExtraction
import com.ai.assistance.operit.api.chat.MemoryCandidate
import com.ai.assistance.operit.api.chat.MemoryExtractionSource
import com.ai.assistance.operit.data.db.*
import com.ai.assistance.operit.data.model.*
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.*
import org.junit.Test
import java.security.SecureRandom
import java.util.UUID

class MemoryAndroidTest {
    @Test fun encryptedEditableMemoriesStayScopedFrozenAndExcludedFromCopies()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        System.loadLibrary("sqlcipher");val name="memory-${UUID.randomUUID()}.db"
        val key=ByteArray(32).also {SecureRandom().nextBytes(it)}
        val db=Room.databaseBuilder(context,AppDatabase::class.java,name).openHelperFactory(SupportOpenHelperFactory(key)).build()
        try {
            val roles=RoleRepository(db);val memories=MemoryRepository(db);val requests=RoomRequestStore(db)
            db.dao().saveConfig(ModelConfigData("cfg","test","OpenAI 兼容","https://example.invalid/v1/chat/completions","test",60,"ref"))
            val a=roles.saveRole(null,"same","A persona","","cfg");val b=roles.saveRole(null,"same","B persona","","cfg")
            val ca=roles.createChat(a.scope(),"cfg");val cb=roles.createChat(b.scope(),"cfg")
            for(role in listOf(a,b))repeat(10){index->memories.save(role.scope(),null,null,if(index==9)"fictional_story"else"preference","memory $index","${role.id}-MEMORY-$index",true,60-index)}
            repeat(50){round->
                val role=if(round%2==0)a else b;val chat=if(round%2==0)ca else cb;val foreign=if(round%2==0)b else a
                val request=requests.prepare(role.scope(),chat.id,"hello",UUID.randomUUID().toString())
                repeat(10){index->assertTrue(request.turns.first().content.contains("${role.id}-MEMORY-$index"));assertFalse(request.turns.first().content.contains("${foreign.id}-MEMORY-$index"))}
                assertEquals(10,request.memories.decisions.count {it.reason=="included"})
                requests.update(request,"done",MessageState.COMPLETE,"",1,2)
            }
            val item=db.memories().read(a.id,a.spaceId).first()
            val frozen=requests.prepare(a.scope(),ca.id,"before edit",UUID.randomUUID().toString())
            val edited=memories.save(a.scope(),item.id,item.version,item.kind,item.title,"A-EDITED-MEMORY",false,item.priority)
            assertTrue(frozen.turns.first().content.contains(item.content));assertFalse(frozen.turns.first().content.contains("A-EDITED-MEMORY"))
            assertTrue(frozen.memories.decisions.any {it.id==item.id&&it.version==item.version})
            requests.update(frozen,"done",MessageState.COMPLETE,"",1,2)
            val next=requests.prepare(a.scope(),ca.id,"after edit",UUID.randomUUID().toString())
            assertFalse(next.turns.first().content.contains(item.content));assertFalse(next.turns.first().content.contains("A-EDITED-MEMORY"))
            assertEquals("disabled",next.memories.decisions.single {it.id==item.id}.reason)
            try {memories.delete(a.scope(),item);fail()}catch(e:ChatFailure){assertEquals("MEMORY_CHANGED",e.code)}
            assertEquals(edited,db.memories().read(a.id,a.spaceId).single {it.id==item.id})
            try {memories.save(RoleAccessScope(a.id,b.spaceId),null,null,"preference","bad","bad",true,50);fail()}catch(e:ChatFailure){assertEquals("ACCESS",e.code)}
            val copy=roles.copyRole(a.scope());assertTrue(db.memories().read(copy.id,copy.spaceId).isEmpty())
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
            for(suffix in listOf("","-wal","-shm")){val file=java.io.File(context.getDatabasePath(name).path+suffix)
                if(file.exists()){val raw=file.readBytes().toString(Charsets.UTF_8);assertFalse(raw.contains("-MEMORY-"));assertFalse(raw.contains("A-EDITED-MEMORY"))}}
            roles.deleteRole(a.scope());requests.update(next,"late",MessageState.COMPLETE,"",1,2)
            assertTrue(db.memories().read(a.id,a.spaceId).isEmpty());assertEquals(10,db.memories().read(b.id,b.spaceId).size)
        }finally {db.close();context.deleteDatabase(name)}
    }

    @Test fun confirmedExtractionStoresProvenanceAndChatDeletionRequiresDisposition()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        System.loadLibrary("sqlcipher");val name="memory-source-${UUID.randomUUID()}.db"
        val key=ByteArray(32).also {SecureRandom().nextBytes(it)}
        val db=Room.databaseBuilder(context,AppDatabase::class.java,name).openHelperFactory(SupportOpenHelperFactory(key)).build()
        try {
            val roles=RoleRepository(db);val memories=MemoryRepository(db);val requests=RoomRequestStore(db)
            val config=ModelConfigData("cfg","test","OpenAI 兼容","https://example.invalid/v1/chat/completions","test",60,"ref")
            db.dao().saveConfig(config)
            val role=roles.saveRole(null,"A","persona","","cfg")
            suspend fun extract(chat:ChatEntity,marker:String):MemoryEntry {
                val snapshot=requests.prepare(role.scope(),chat.id,"用户-$marker",UUID.randomUUID().toString())
                requests.update(snapshot,"助手-$marker",MessageState.COMPLETE,"",1,2)
                val history=db.dao().history(role.id,role.spaceId,chat.id)
                val user=history.single {it.requestId==snapshot.requestId&&it.kind=="user"}
                val assistant=history.single {it.id==snapshot.assistantId}
                val currentRole=roles.requireRole(role.scope())
                val frozen=FrozenMemoryExtraction(role=currentRole,config=config,source=MemoryExtractionSource(role.scope(),chat.id,snapshot.requestId,user.id,assistant.id,user.content,assistant.content,assistant.createdAt))
                return memories.saveExtracted(frozen,listOf(MemoryCandidate("preference","偏好-$marker","EXTRACTED-PRIVATE-$marker")),assistant.createdAt+1).single()
            }
            val detachedChat=roles.createChat(role.scope(),"cfg");val linked=extract(detachedChat,"DETACH")
            assertEquals(1,memories.sourceCount(role.scope(),detachedChat.id));assertTrue(linked.sourceLinked())
            try {roles.deleteChat(role.scope(),detachedChat.id);fail()}catch(e:ChatFailure){assertEquals("MEMORY_SOURCE_REQUIRED",e.code)}
            roles.deleteChat(role.scope(),detachedChat.id,"detach")
            val detached=db.memories().read(role.id,role.spaceId).single()
            assertFalse(detached.sourceLinked());assertEquals("extracted",detached.source);assertEquals("OpenAI 兼容 · test",detached.sourceModel)
            Memories.validateStored(detached)

            val deletedChat=roles.createChat(role.scope(),"cfg");extract(deletedChat,"DELETE")
            assertEquals(2,db.memories().read(role.id,role.spaceId).size)
            roles.deleteChat(role.scope(),deletedChat.id,"delete")
            assertEquals(listOf(detached.id),db.memories().read(role.id,role.spaceId).map {it.id})
            try {memories.sourceCount(RoleAccessScope(role.id,"wrong-space"),deletedChat.id);fail()}catch(e:ChatFailure){assertEquals("ACCESS",e.code)}
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
            for(suffix in listOf("","-wal","-shm")){val file=java.io.File(context.getDatabasePath(name).path+suffix)
                if(file.exists())assertFalse(file.readBytes().toString(Charsets.UTF_8).contains("EXTRACTED-PRIVATE"))}
        }finally {db.close();context.deleteDatabase(name)}
    }

    @Test fun localRecallConflictReplacementAndReversibleForgettingAreTransactional()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        System.loadLibrary("sqlcipher");val name="memory-recall-${UUID.randomUUID()}.db"
        val key=ByteArray(32).also {SecureRandom().nextBytes(it)}
        val db=Room.databaseBuilder(context,AppDatabase::class.java,name).openHelperFactory(SupportOpenHelperFactory(key)).build()
        try {
            val roles=RoleRepository(db);val memories=MemoryRepository(db)
            db.dao().saveConfig(ModelConfigData("cfg","test","OpenAI 兼容","https://example.invalid/v1/chat/completions","test",60,"ref"))
            val role=roles.saveRole(null,"A","persona","","cfg")
            val old=memories.save(role.scope(),null,null,"preference","饮品偏好","用户最喜欢茉莉花茶",true,50,"auto",1000,clock={100})
            val conflict=Memories.conflicts(role.scope(),db.memories().read(role.id,role.spaceId),"preference","饮品偏好","用户改为最喜欢乌龙茶",now=200)
            assertEquals(listOf(old.id),conflict.map {it.id})
            try {memories.save(role.scope(),null,null,"preference","饮品偏好","用户改为最喜欢乌龙茶",true,60,"auto",null,clock={200});fail()}
            catch(e:ChatFailure){assertEquals("MEMORY_CONFLICT",e.code)}
            val replacement=memories.save(role.scope(),null,null,"preference","饮品偏好","用户改为最喜欢乌龙茶",true,60,"auto",null,"replace",conflict.map {it.id}.toSet(),clock={200})
            val rows=db.memories().read(role.id,role.spaceId);val forgotten=rows.single {it.id==old.id}
            assertFalse(forgotten.enabled);assertEquals(replacement.id,forgotten.replacedById);assertNotNull(forgotten.forgottenAt)
            val selected=Memories.select(role.scope(),rows,query="今天喝乌龙茶",now=201)
            assertEquals("forgotten",selected.decisions.single {it.id==old.id}.reason);assertEquals("included",selected.decisions.single {it.id==replacement.id}.reason)
            memories.forget(role.scope(),replacement,clock={300})
            val forgottenReplacement=db.memories().read(role.id,role.spaceId).single {it.id==replacement.id};assertNotNull(forgottenReplacement.forgottenAt)
            memories.restore(role.scope(),forgottenReplacement,clock={400})
            val restored=db.memories().read(role.id,role.spaceId).single {it.id==replacement.id};assertNull(restored.forgottenAt);assertTrue(restored.enabled)
            memories.delete(role.scope(),restored)
            val oldAfterDelete=db.memories().read(role.id,role.spaceId).single();assertEquals(old.id,oldAfterDelete.id);assertNull(oldAfterDelete.replacedById)
        }finally {db.close();context.deleteDatabase(name)}
    }
}
