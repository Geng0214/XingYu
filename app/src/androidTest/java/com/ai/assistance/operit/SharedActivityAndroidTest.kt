package com.ai.assistance.operit

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.data.db.*
import com.ai.assistance.operit.data.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.*
import org.junit.Test
import java.security.SecureRandom
import java.util.UUID

class SharedActivityAndroidTest {
    @Test fun activitiesPersistAcrossReopenRejectWrongOwnersAndCascadeWithChat()=runBlocking {
        System.loadLibrary("sqlcipher")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="activities-${UUID.randomUUID()}.db";val key=ByteArray(32).also {SecureRandom().nextBytes(it)}
        fun open()=Room.databaseBuilder(context,AppDatabase::class.java,name).openHelperFactory(SupportOpenHelperFactory(key.copyOf())).build()
        var db=open()
        try {
            val roles=RoleRepository(db)
            db.dao().saveConfig(ModelConfigData("cfg","test","OpenAI 兼容","https://example.invalid/v1/chat/completions","model",60,"ref",1))
            val a=roles.saveRole(null,"A","人设 A","","cfg");val b=roles.saveRole(null,"B","人设 B","","cfg")
            val chat=roles.createChat(a.scope(),"cfg");val other=roles.createChat(b.scope(),"cfg")
            val text="ACTIVITY-PRIVATE-正文🙂"
            val prepared=PreparedReadingDocument("书","text/plain",text,listOf(ReadingChapter(0,"第一节",0,text.length)),ReadingDocuments.sha256(text))
            val book=ReadingRepository(db).import(a.scope(),prepared)
            var activity=SharedActivityRepository(db).create(a.scope(),chat.id,book.id)
            repeat(50){n->
                try {SharedActivityRepository(db).summary(b.scope(),other.id,activity.id,activity.version);fail()}catch(e:ChatFailure){assertEquals("ACCESS",e.code)}
                SharedActivityRepository(db).change(a.scope(),chat.id,activity.id,activity.version,note="ACTIVITY-PRIVATE-$n")
                try {SharedActivityRepository(db).change(a.scope(),chat.id,activity.id,activity.version,note="stale");fail()}catch(e:ChatFailure){assertEquals("ACTIVITY_CHANGED",e.code)}
                activity=db.activities().read(a.id,a.spaceId,chat.id,activity.id)!!
            }
            try {ReadingRepository(db).delete(a.scope(),book.id);fail()}catch(e:ChatFailure){assertEquals("READING_REFERENCED",e.code)}
            db.close();db=open()
            val restored=db.activities().read(a.id,a.spaceId,chat.id,activity.id)!!
            assertEquals("ACTIVITY-PRIVATE-49",restored.note)
            assertTrue(db.activities().observe(b.id,b.spaceId).first().isEmpty())
            SharedActivityRepository(db).change(a.scope(),chat.id,restored.id,restored.version,action="complete")
            val done=db.activities().read(a.id,a.spaceId,chat.id,restored.id)!!
            try {SharedActivityRepository(db).change(a.scope(),chat.id,done.id,done.version,chapter=0,offset=1);fail()}catch(e:ChatFailure){assertEquals("ACTIVITY_STATE",e.code)}
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
            for(suffix in listOf("","-wal","-shm")) {
                val file=java.io.File(context.getDatabasePath(name).path+suffix)
                if(file.exists())assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains("ACTIVITY-PRIVATE"))
            }
            RoleRepository(db).deleteChat(a.scope(),chat.id)
            assertNull(db.activities().read(a.id,a.spaceId,chat.id,done.id))
            assertNotNull(db.dao().readingDocument(a.id,a.spaceId,book.id))
            RoleRepository(db).deleteRole(a.scope())
            assertNull(db.dao().readingDocument(a.id,a.spaceId,book.id))
        } finally {db.close();context.deleteDatabase(name)}
    }
}
