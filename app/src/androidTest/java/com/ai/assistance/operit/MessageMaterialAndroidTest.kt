package com.ai.assistance.operit

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.api.chat.RoomRequestStore
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.db.RoleRepository
import com.ai.assistance.operit.data.model.*
import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.*
import org.junit.Test

class MessageMaterialAndroidTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun encryptedMaterialIsScopedPersistsAcrossRestartAndCascadesWithChat()=runBlocking {
        System.loadLibrary("sqlcipher")
        val name="material-${UUID.randomUUID()}.db";val key=ByteArray(32).also {SecureRandom().nextBytes(it)}
        val marker="MATERIAL-PRIVATE-中文🙂-${UUID.randomUUID()}"
        fun open()=Room.databaseBuilder(context,AppDatabase::class.java,name).openHelperFactory(SupportOpenHelperFactory(key.copyOf())).build()
        var db=open()
        try {
            db.dao().saveConfig(ModelConfigData("cfg","test","OpenAI 兼容","https://example.invalid/v1/chat/completions","model",60,"ref"))
            val repo=RoleRepository(db);val role=repo.saveRole(null,"A","persona","","cfg");val chat=repo.createChat(role.scope(),"cfg")
            val material=PromptMaterial(TextMaterials.KIND_FILE,"notes.md",null,TextMaterials.MIME_MARKDOWN,marker,marker.length,false,TextMaterials.sha256(marker))
            val snapshot=RoomRequestStore(db).prepareWithContent(role.scope(),chat.id,"请阅读",null,material,"request")
            assertEquals(material,snapshot.turns.last().materials.single())
            assertEquals(marker,db.dao().requestMaterial(role.id,role.spaceId,chat.id,"request")!!.text)
            assertNull(db.dao().requestMaterial(role.id,"foreign-space",chat.id,"request"))
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close();db.close()

            for(suffix in listOf("","-wal","-shm")) {val file=java.io.File(context.getDatabasePath(name).path+suffix)
                if(file.exists())assertFalse(file.readBytes().toString(Charsets.UTF_8).contains(marker))}
            db=open()
            assertEquals(marker,db.dao().materials(role.id,role.spaceId,chat.id).first().single().text)
            RoleRepository(db).deleteChat(role.scope(),chat.id)
            assertTrue(db.dao().historyMaterials(role.id,role.spaceId,chat.id).isEmpty())
        } finally {db.close();context.deleteDatabase(name)}
    }
}
