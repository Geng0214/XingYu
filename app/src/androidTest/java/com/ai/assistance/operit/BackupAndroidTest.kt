package com.ai.assistance.operit

import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.data.backup.*
import com.ai.assistance.operit.data.db.*
import com.ai.assistance.operit.data.model.*
import com.ai.assistance.operit.data.security.CredentialVault
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

/** Actual Room/SQLCipher/Keystore integration; host JVM success does not substitute for these tests. */
class BackupAndroidTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val address="https://example.invalid/v1/chat/completions"
    private val marker="BACKUP-合成私密正文-😀"
    private data class Fixture(val db:AppDatabase,val vault:CredentialVault,val directory:File,val name:String)
    private fun fixture():Fixture {
        System.loadLibrary("sqlcipher")
        val directory=File(context.cacheDir,"backup-test-${UUID.randomUUID()}").apply {mkdirs()}
        val isolated=object:ContextWrapper(context){override fun getNoBackupFilesDir()=directory}
        val vault=CredentialVault(isolated);val name="backup-${UUID.randomUUID()}.db"
        val db=Room.databaseBuilder(context,AppDatabase::class.java,name).openHelperFactory(SupportOpenHelperFactory(vault.databaseKey(false))).build()
        return Fixture(db,vault,directory,name)
    }
    private fun close(f:Fixture){f.db.close();context.deleteDatabase(f.name);f.directory.deleteRecursively()}
    private suspend fun seed(f:Fixture,suffix:String):RoleEntity {
        val key=f.vault.save("synthetic-api-$suffix",address)
        f.db.dao().saveConfig(ModelConfigData("config-$suffix","模型","OpenAI 兼容",address,"test-model",60,key))
        val created=RoleRepository(f.db).saveRole(null,"同名角色","角色设定 $suffix","开场白","config-$suffix")
        val role=RoleRepository(f.db).savePersona(created.scope(),created.version,"用户-$suffix","USER-PERSONA-$suffix",true)
        val chat=RoleRepository(f.db).createChat(role.scope(),"config-$suffix")
        f.db.dao().insertMessages(listOf(MessageEntity("user-$suffix",chat.id,"request-$suffix","user",marker,MessageState.COMPLETE,10),
            MessageEntity("reply-$suffix",chat.id,"request-$suffix","assistant","已收到 $suffix",MessageState.GENERATING,11)))
        val bitmap=Bitmap.createBitmap(4,4,Bitmap.Config.ARGB_8888)
        val bytes=ByteArrayOutputStream().also {bitmap.compress(Bitmap.CompressFormat.JPEG,90,it)}.toByteArray();bitmap.recycle()
        RoleRepository(f.db).savePortrait(role.scope(),"self",bytes)
        RoleRepository(f.db).saveAppearance(role.scope(),bytes,0.3f,"rose")
        f.vault.setPrivacyLock(true)
        return role
    }
    @Test fun encryptedRoundTripAppendsPreservesExistingKeysLockAndImages()=runBlocking {
        val a=fixture();val b=fixture()
        try {
            seed(a,"source");val existing=seed(b,"target");val originalKey=b.vault.databaseKey(true)
            val source=BackupRepository(a.db,a.vault).snapshot(true){}
            val password="备份跨设备测试-very-long-pass".toCharArray()
            val encrypted=BackupFormat.encrypt(source,password)
            assertFalse(encrypted.toString(Charsets.UTF_8).contains(marker))
            val decoded=BackupFormat.decrypt(encrypted,password)
            val repo=BackupRepository(b.db,b.vault)
            repo.restore(decoded,{});repo.restore(decoded,{})
            assertEquals(3,b.db.dao().roles().first().size)
            assertNotNull(b.db.dao().role(existing.id));assertTrue(b.vault.privacyLockEnabled())
            assertArrayEquals(originalKey,b.vault.databaseKey(true))
            val models=b.db.dao().configs().first();assertEquals(3,models.size)
            models.filter {it.id!="config-target"}.forEach {
                assertEquals("synthetic-api-source",b.vault.read(it.credentialRef,address))
                try {b.vault.read(it.credentialRef,"https://wrong.invalid");fail()}catch(e:ChatFailure){assertEquals("CREDENTIAL",e.code)}
            }
            val restored=repo.snapshot(true){}
            assertEquals(3,restored.summary().personas)
            assertEquals(2,restored.rows("roles").count {it.text("userPersona")=="USER-PERSONA-source"})
            assertEquals(3,restored.rows("role_portraits").size);assertEquals(3,restored.rows("role_appearance").size)
            val messages=restored.rows("messages").filter {it.text("id")!="reply-target"&&it.text("kind")=="assistant"}
            assertTrue(messages.all {it.text("state")=="FAILED"&&it.text("errorCode")=="INTERRUPTED"})
            b.db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
            val files=b.directory.walkTopDown().filter {it.isFile}.toList()+listOf("","-wal","-shm").map {File(context.getDatabasePath(b.name).path+it)}.filter {it.exists()}
            files.forEach {file->val raw=file.readBytes().toString(Charsets.UTF_8);assertFalse(raw.contains(marker));assertFalse(raw.contains("synthetic-api-source"));assertFalse(raw.contains("USER-PERSONA-source"))}
        }finally {close(a);close(b)}
    }
    @Test fun sqlFailureAndTwentyCancellationsLeaveOriginalRowsAndCredentialFilesIntact()=runBlocking {
        val a=fixture();val b=fixture()
        try {
            seed(a,"source");seed(b,"target")
            val repo=BackupRepository(b.db,b.vault);val source=BackupRepository(a.db,a.vault).snapshot(true){}
            val before=BackupFormat.encode(repo.snapshot(true){}.copy(createdAt=0))
            val files=File(b.directory,"vault").list()!!.toSet()
            repeat(20){var checks=0
                try {repo.restore(source,{if(++checks==6)throw CancellationException("synthetic cancellation")});fail()}
                catch(_:CancellationException){}
                assertArrayEquals(before,BackupFormat.encode(repo.snapshot(true){}.copy(createdAt=0)))
                assertEquals(files,File(b.directory,"vault").list()!!.toSet())
            }
            b.db.openHelper.writableDatabase.execSQL("CREATE TEMP TRIGGER backup_fail BEFORE INSERT ON messages BEGIN SELECT RAISE(ABORT,'synthetic'); END")
            try {repo.restore(source,{});fail()}catch(_:android.database.SQLException){}
            finally {b.db.openHelper.writableDatabase.execSQL("DROP TRIGGER backup_fail")}
            assertArrayEquals(before,BackupFormat.encode(repo.snapshot(true){}.copy(createdAt=0)))
            assertEquals(files,File(b.directory,"vault").list()!!.toSet())
        }finally {close(a);close(b)}
    }
    @Test fun missingCredentialsDoNotReuseLocalKeyAndDamagedImagesDoNotImport()=runBlocking {
        val a=fixture();val b=fixture()
        try {
            seed(a,"source");seed(b,"target")
            val source=BackupRepository(a.db,a.vault).snapshot(false){}
            val repo=BackupRepository(b.db,b.vault);repo.restore(source,{})
            val model=b.db.dao().configs().first().single {it.id!="config-target"}
            try {b.vault.read(model.credentialRef,address);fail()}catch(e:ChatFailure){assertEquals("CREDENTIAL",e.code)}
            val bad=source.copy(tables=source.tables.map {rows->rows.map {if(it.table.name=="role_portraits")it.changing(mapOf("image" to byteArrayOf(1,2,3)))else it}})
            try {repo.restore(bad,{});fail()}catch(e:ChatFailure){assertEquals("BACKUP_FORMAT",e.code)}
            assertEquals(2,b.db.dao().roles().first().size)
        }finally {close(a);close(b)}
    }
}
