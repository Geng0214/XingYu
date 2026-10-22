package com.ai.assistance.operit

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.data.db.*
import com.ai.assistance.operit.data.model.*
import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.*
import org.junit.Test

class ProactiveAndroidTest {
    @Test fun settingsAreScopedCopiedEmptyDeletedAndEncrypted()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext;System.loadLibrary("sqlcipher")
        val name="proactive-${UUID.randomUUID()}.db";val key=ByteArray(32).also {SecureRandom().nextBytes(it)}
        val db=Room.databaseBuilder(context,AppDatabase::class.java,name).openHelperFactory(SupportOpenHelperFactory(key.copyOf())).build()
        try {
            val roles=RoleRepository(db);val a=roles.saveRole(null,"A","persona","",null);val b=roles.saveRole(null,"B","persona","",null)
            val config=ModelConfigData("model","model","OpenAI 兼容","https://example.invalid/v1/chat/completions","m",60,"ref")
            db.dao().saveConfig(config);val chat=roles.createChat(a.scope(),config.id)
            val saved=ProactiveRepository(db).save(a.scope(),true,chat.id,config.id,1320,480,24,1,null)
            assertEquals(saved,db.proactive().observeSetting(a.id,a.spaceId).first());assertNull(db.proactive().setting(a.id,b.spaceId))
            val copied=roles.copyRole(a.scope());assertNull(db.proactive().setting(copied.id,copied.spaceId))
            roles.setConversationMode(a.scope(),a.version,"raw");assertNull(db.proactive().setting(a.id,a.spaceId))
            roles.deleteChat(a.scope(),chat.id);assertNull(db.proactive().setting(a.id,a.spaceId))
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
            for(suffix in listOf("","-wal","-shm")){val file=java.io.File(context.getDatabasePath(name).path+suffix);if(file.exists())assertFalse(file.readBytes().toString(Charsets.UTF_8).contains("model"))}
        } finally {db.close();context.deleteDatabase(name)}
    }
}
