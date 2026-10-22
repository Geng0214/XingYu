package com.ai.assistance.operit

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.api.chat.DiaryDraft
import com.ai.assistance.operit.api.chat.FrozenDiaryGeneration
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.db.DiaryRepository
import com.ai.assistance.operit.data.db.RoleRepository
import com.ai.assistance.operit.data.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.*
import org.junit.Test
import java.security.SecureRandom
import java.util.UUID

class DiaryAndroidTest {
    @Test fun diaryOwnershipGenerationEditingDeletionAndEncryptedStorageAreIsolated()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        System.loadLibrary("sqlcipher");val name="diary-${UUID.randomUUID()}.db";val key=ByteArray(32).also {SecureRandom().nextBytes(it)}
        val db=Room.databaseBuilder(context,AppDatabase::class.java,name).openHelperFactory(SupportOpenHelperFactory(key.copyOf())).build()
        try {
            val roles=RoleRepository(db);val diaries=DiaryRepository(db)
            val config=ModelConfigData("cfg","test","OpenAI 兼容","https://example.invalid/v1/chat/completions","model-x",60,"ref",version=2)
            db.dao().saveConfig(config)
            val a=roles.saveRole(null,"A","A persona","","cfg");val b=roles.saveRole(null,"B","B persona","","cfg")
            val manual=diaries.saveManual(a.scope(),null,"heart","雨天","DIARY-PRIVATE-A🙂","calm",100,clock={200})
            assertEquals(listOf(manual.id),db.diaries().observe(a.id,a.spaceId).first().map {it.id});assertTrue(db.diaries().read(b.id,b.spaceId).isEmpty())
            try {diaries.saveManual(b.scope(),manual,"heart","越界","DIARY-PRIVATE-B","sad",100);fail()}catch(e:ChatFailure){assertEquals("DIARY_CHANGED",e.code)}
            try {diaries.delete(b.scope(),manual);fail()}catch(e:ChatFailure){assertEquals("ACCESS",e.code)}

            val frozen=FrozenDiaryGeneration("frozen",roles.requireRole(a.scope()),config,"role_diary","写一篇虚构月光厨房日记",300)
            val generated=diaries.saveGenerated(frozen,DiaryDraft("月光厨房","在虚构故事里做了一顿晚饭。","happy"),100,clock={301})
            assertTrue(generated.generated());assertEquals("OpenAI 兼容 · model-x",generated.sourceModel);assertEquals(frozen.prompt,generated.generationPrompt)
            val edited=diaries.saveManual(a.scope(),generated,"role_diary","月光厨房","用户核对并修改了正文。","hopeful",100,clock={400})
            assertEquals("generated",edited.source);assertEquals(generated.generationPrompt,edited.generationPrompt);assertEquals(2L,edited.version)
            try {diaries.delete(a.scope(),generated);fail()}catch(e:ChatFailure){assertEquals("DIARY_CHANGED",e.code)}

            val stale=FrozenDiaryGeneration("stale",roles.requireRole(a.scope()),config,"heart","一件心事",500)
            roles.setReplyStyle(a.scope(),"paragraph")
            try {diaries.saveGenerated(stale,DiaryDraft("迟到草稿","不应保存。","calm"),100);fail()}catch(e:ChatFailure){assertEquals("DIARY_SOURCE_CHANGED",e.code)}
            assertEquals(2,db.diaries().read(a.id,a.spaceId).size)
            roles.deleteRole(a.scope());assertTrue(db.diaries().read(a.id,a.spaceId).isEmpty());assertTrue(db.diaries().read(b.id,b.spaceId).isEmpty())

            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
            for(suffix in listOf("","-wal","-shm")) {val file=java.io.File(context.getDatabasePath(name).path+suffix);if(file.exists())assertFalse(file.readBytes().toString(Charsets.UTF_8).contains("DIARY-PRIVATE"))}
        } finally {db.close();context.deleteDatabase(name)}
    }
}
