package com.ai.assistance.operit

import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.security.CredentialVault
import com.ai.assistance.operit.data.model.*
import com.ai.assistance.operit.api.chat.RoomRequestStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class StorageAndroidTest {
    @Test fun credentialPruningKeepsReferencedKeysAndRemovesOrphanedEncryptedFiles() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val directory=File(context.cacheDir,"credential-prune-${UUID.randomUUID()}").apply {mkdirs()}
        val isolated=object:ContextWrapper(context){override fun getNoBackupFilesDir()=directory}
        val vault=CredentialVault(isolated)
        try {
            val databaseKey=vault.databaseKey(false)
            vault.setPrivacyLock(true)
            val endpoint="https://example.invalid/v1/chat/completions"
            val retained=vault.save("retained-synthetic-key",endpoint)
            val orphaned=vault.save("orphaned-synthetic-key",endpoint)

            vault.pruneCredentials(setOf(retained))

            assertEquals("retained-synthetic-key",vault.read(retained,endpoint))
            try {vault.read(orphaned,endpoint);fail("Orphaned credential file remains readable")}
            catch(e:ChatFailure){assertEquals("CREDENTIAL",e.code)}
            assertTrue(File(directory,"vault/$retained.enc").exists())
            assertFalse(File(directory,"vault/$orphaned.enc").exists())
            assertArrayEquals(databaseKey,vault.databaseKey(true))
            assertTrue(vault.privacyLockEnabled())
        } finally {directory.deleteRecursively()}
    }

    @Test fun encryptedPersistenceRecoveryAndCredentialBinding()=runBlocking {
        // Instrumentation targets the separate .debug package and uses synthetic content only.
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val marker="synthetic-secret-"+UUID.randomUUID()
        val vault=CredentialVault(context)
        val endpoint="https://example.com/v1/chat/completions"
        val ref=vault.save(marker,endpoint)
        assertEquals(marker,vault.read(ref,endpoint))
        try{vault.read(ref,"https://other.example/v1/chat/completions");fail()}catch(e:ChatFailure){assertEquals("CREDENTIAL",e.code)}
        var db=AppDatabase.open(context,vault)
        val id=UUID.randomUUID().toString()
        val role=RoleEntity(id,marker,marker)
        db.dao().saveRole(role)
        db.dao().saveConfig(ModelConfigData(id,marker,"OpenAI 兼容",endpoint,"test",90,ref))
        db.dao().insertChat(ChatEntity(id,id,id,marker))
        val snapshot=RoomRequestStore(db).prepare(role.scope(),id,marker,UUID.randomUUID().toString())
        db.dao().updateActive(role.id,role.spaceId,id,snapshot.requestId,snapshot.assistantId,marker,MessageState.GENERATING,"",1,2)
        db.close()
        db=AppDatabase.open(context,vault)
        db.dao().recoverInterrupted()
        val reply=db.dao().history(role.id,role.spaceId,id).last()
        assertEquals(MessageState.FAILED,reply.state);assertEquals("INTERRUPTED",reply.errorCode)
        assertEquals(marker,reply.content)
        assertEquals(0,db.dao().updateActive(role.id,role.spaceId,id,reply.requestId,reply.id,"late",MessageState.COMPLETE,"",1,3))
        db.close()
        val roots=listOf(context.filesDir,context.cacheDir,context.noBackupFilesDir,context.getDatabasePath("companion.db").parentFile!!)
        roots.forEach { root -> root.walkTopDown().filter{it.isFile}.forEach{file->
            assertFalse("Plaintext in ${file.name}",file.readBytes().toString(Charsets.ISO_8859_1).contains(marker))
        } }
        val probe=java.io.File(context.cacheDir,"encrypted-read-probe.db")
        context.getDatabasePath("companion.db").copyTo(probe,overwrite=true)
        try {
            android.database.sqlite.SQLiteDatabase.openDatabase(probe.path,null,android.database.sqlite.SQLiteDatabase.OPEN_READONLY).use {
                it.rawQuery("SELECT * FROM roles",null).use { rows -> rows.moveToFirst() }
            }
            fail("Plain SQLite read encrypted DB")
        } catch(_:android.database.sqlite.SQLiteException){}
        finally { probe.delete() }
    }
}
