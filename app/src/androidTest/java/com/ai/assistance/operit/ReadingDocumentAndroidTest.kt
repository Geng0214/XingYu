package com.ai.assistance.operit

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.api.chat.ReadingDocumentReader
import com.ai.assistance.operit.api.chat.RoomRequestStore
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.db.ReadingRepository
import com.ai.assistance.operit.data.db.RoleRepository
import com.ai.assistance.operit.data.model.*
import java.io.ByteArrayInputStream
import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.*
import org.junit.Test

class ReadingDocumentAndroidTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun encryptedBookProgressAndPassageStayScopedAndPersistAcrossRestart()=runBlocking {
        System.loadLibrary("sqlcipher")
        val name="reading-${UUID.randomUUID()}.db";val key=ByteArray(32).also {SecureRandom().nextBytes(it)}
        val marker="READING-PRIVATE-中文🙂-${UUID.randomUUID()}"
        fun open()=Room.databaseBuilder(context,AppDatabase::class.java,name).openHelperFactory(SupportOpenHelperFactory(key.copyOf())).build()
        var db=open()
        try {
            db.dao().saveConfig(ModelConfigData("cfg","test","OpenAI 兼容","https://example.invalid/v1/chat/completions","model",60,"ref"))
            val roles=RoleRepository(db);val role=roles.saveRole(null,"A","persona","","cfg");val chat=roles.createChat(role.scope(),"cfg")
            val prepared=ReadingDocumentReader().file(ByteArrayInputStream(("# 第一章\n"+marker+"\n# 第二章\n结尾").toByteArray()),"story.md","text/markdown")
            val books=ReadingRepository(db);val document=books.import(role.scope(),prepared);val chapter=document.chapters().first()
            books.updateProgress(role.scope(),document.id,chapter.index,chapter.start+2)
            val passage=books.passage(role.scope(),chat.id,document.id,chapter.index,chapter.start,chapter.end)
            val snapshot=RoomRequestStore(db).prepareWithContent(role.scope(),chat.id,"一起读",null,passage,"request")
            assertEquals(passage,snapshot.turns.last().materials.single())
            assertNull(db.dao().readingDocument(role.id,"foreign-space",document.id))
            try {books.delete(role.scope(),document.id);fail("Expected referenced document rejection")}catch(e:ChatFailure){assertEquals("READING_REFERENCED",e.code)}
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close();db.close()

            for(suffix in listOf("","-wal","-shm")) {val file=java.io.File(context.getDatabasePath(name).path+suffix)
                if(file.exists())assertFalse(file.readBytes().toString(Charsets.UTF_8).contains(marker))}
            db=open()
            val restored=db.dao().readingDocuments(role.id,role.spaceId).first().single()
            assertEquals(chapter.start+2,restored.currentOffset);assertTrue(restored.text.contains(marker))
            RoleRepository(db).deleteRole(role.scope())
            assertTrue(db.dao().readingDocuments(role.id,role.spaceId).first().isEmpty())
        } finally {db.close();context.deleteDatabase(name)}
    }
}
