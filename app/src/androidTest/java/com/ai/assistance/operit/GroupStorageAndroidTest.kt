package com.ai.assistance.operit

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.api.chat.RoomRequestStore
import com.ai.assistance.operit.data.backup.*
import com.ai.assistance.operit.data.db.*
import com.ai.assistance.operit.data.model.*
import com.ai.assistance.operit.data.security.CredentialVault
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.*
import org.junit.Test
import java.security.SecureRandom
import java.util.UUID

class GroupStorageAndroidTest {
    @Test fun actualEncryptedGroupsRejectForeignWritesPreserveDeletedMembersAndRecoverWithBackup()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        System.loadLibrary("sqlcipher");val name="group-test-${UUID.randomUUID()}.db"
        val key=ByteArray(32).also {SecureRandom().nextBytes(it)}
        fun open()=Room.databaseBuilder(context,AppDatabase::class.java,name).openHelperFactory(SupportOpenHelperFactory(key.copyOf())).build()
        var db=open()
        try {
            db.dao().saveConfig(ModelConfigData("cfg","test","openai","https://example.invalid/v1/chat/completions","model",60,"ref"))
            val rr=RoleRepository(db)
            val a=rr.saveRole(null,"同名","PUBLIC-A","", "cfg")
            val b=rr.saveRole(null,"同名","PUBLIC-B","", "cfg")
            val c=rr.saveRole(null,"其他","PUBLIC-C","", "cfg")
            val privateChat=rr.createChat(a.scope(),"cfg")
            RoomRequestStore(db).prepare(a.scope(),privateChat.id,"PRIVATE-CHAT-MARKER","private-request")
            var repo=GroupRepository(db)
            val selected=listOf(a.scope() to "cfg",b.scope() to "cfg")
            val groups=listOf(repo.create("群 A","我",selected),repo.create("群 B","我",selected))
            repeat(50) {n->
                val g=groups[n%2];val other=groups[1-n%2];val member=g.members.single {it.role==a.scope()}
                val snapshot=repo.prepare(g.room.scope,member.id,a.scope(),"GROUP-${n%2}-MARKER-${n%10}","request-$n")
                val text=snapshot.turns.joinToString {it.content}
                assertFalse(text.contains("PRIVATE-CHAT-MARKER"));assertFalse(text.contains("GROUP-${1-n%2}-MARKER"))
                assertNull(db.groups().request(other.room.scope.groupId,other.room.scope.spaceId,snapshot.requestId))
                val bad=snapshot.copy(group=snapshot.group!!.copy(group=other.room.scope))
                try{repo.update(bad,"",MessageState.WAITING,"",-1,0);fail()}catch(e:ChatFailure){assertEquals("ACCESS",e.code)}
                repo.update(snapshot,"",MessageState.WAITING,"",-1,0)
                repo.update(snapshot,"回答🙂",MessageState.GENERATING,"",1,2)
                repo.update(snapshot,"回答🙂完成",MessageState.COMPLETE,"",1,3)
                try{repo.update(snapshot,"迟到",MessageState.GENERATING,"",1,4);fail()}catch(e:ChatFailure){assertEquals("ACCESS",e.code)}
            }
            val g=groups[0];val member=g.members.single {it.role==a.scope()}
            val beforePreview=repo.observe(g.room.scope).first()!!
            val preview=repo.preview(g.room.scope,member.id,a.scope(),"PREVIEW-ONLY")
            assertEquals(g.room.scope,preview.group!!.group)
            val afterPreview=repo.observe(g.room.scope).first()!!
            assertEquals(beforePreview,afterPreview)
            assertEquals(2,db.groups().inbox().first().size)
            val pending=repo.prepare(g.room.scope,member.id,a.scope(),"remove-role","pending-role")
            repo.update(pending,"",MessageState.WAITING,"",-1,0);repo.update(pending,"KEEP-PARTIAL",MessageState.GENERATING,"",1,2)
            try{repo.edit(g.room.scope,g.room.version,"changed","我",selected);fail()}catch(e:ChatFailure){assertEquals("MODEL_GENERATION_BUSY",e.code)}
            rr.deleteRole(a.scope())
            assertEquals(1,repo.definition(g.room.scope).members.size)
            assertEquals("FAILED",db.groups().request(g.room.scope.groupId,g.room.scope.spaceId,pending.requestId)!!.terminalState)
            assertTrue(db.groups().history(g.room.scope.groupId,g.room.scope.spaceId).any {it.content=="KEEP-PARTIAL"})
            try{repo.update(pending,"KEEP-PARTIAL-late",MessageState.GENERATING,"",1,3);fail()}catch(e:ChatFailure){assertEquals("ACCESS",e.code)}
            val survivor=repo.create("survivor","我",listOf(b.scope() to "cfg",c.scope() to "cfg"))
            val interrupted=repo.prepare(survivor.room.scope,survivor.members.single {it.role==b.scope()}.id,b.scope(),"restart","restart-request")
            repo.update(interrupted,"",MessageState.WAITING,"",-1,0);repo.update(interrupted,"RESTART-PARTIAL",MessageState.GENERATING,"",1,2)
            db.close();db=open();repo=GroupRepository(db);db.recoverInterruptedRequests()
            val recovered=db.groups().request(survivor.room.scope.groupId,survivor.room.scope.spaceId,"restart-request")!!
            assertEquals("INTERRUPTED",recovered.errorCode)
            assertEquals("RESTART-PARTIAL",db.groups().message(interrupted.assistantId,survivor.room.scope.groupId,survivor.room.scope.spaceId,"restart-request")!!.content)
            val backup=BackupRepository(db,CredentialVault(context));val snapshot=backup.snapshot(false,{})
            val restored=backup.restore(snapshot,{})
            assertEquals(3,restored.groups);assertEquals(6,db.groups().count())
            repo.delete(survivor.room.scope,repo.definition(survivor.room.scope).room.version)
            assertTrue(db.groups().history(survivor.room.scope.groupId,survivor.room.scope.spaceId).isEmpty())
            assertNull(db.groups().request(survivor.room.scope.groupId,survivor.room.scope.spaceId,"restart-request"))
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
            for(suffix in listOf("","-wal","-shm")) {
                val file=java.io.File(context.getDatabasePath(name).path+suffix)
                if(file.exists())assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains("KEEP-PARTIAL"))
            }
        }finally {db.close();context.deleteDatabase(name);key.fill(0)}
    }
}
