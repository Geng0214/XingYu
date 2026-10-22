package com.ai.assistance.operit

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.SpeechServiceConfig
import com.ai.assistance.operit.data.model.SpeechServices
import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.*
import org.junit.Test

class SpeechAndroidTest {
    @Test fun speechConfigsRemainEncryptedAndAudioIsNotPersisted()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext;System.loadLibrary("sqlcipher")
        val name="speech-${UUID.randomUUID()}.db";val key=ByteArray(32).also {SecureRandom().nextBytes(it)}
        val db=Room.databaseBuilder(context,AppDatabase::class.java,name).openHelperFactory(SupportOpenHelperFactory(key.copyOf())).build()
        try {
            val marker="synthetic-speech-model-${UUID.randomUUID()}"
            val config=SpeechServiceConfig("speech","识别",SpeechServices.TRANSCRIPTION,"https://example.invalid/v1/audio/transcriptions",marker,"",60,"credential-ref",1)
            db.dao().saveSpeechServiceConfig(config)
            assertEquals(config,db.dao().speechServiceConfigs().first().single())
            val columns=db.openHelper.readableDatabase.query("PRAGMA table_info(speech_service_configs)").use {cursor->buildList {while(cursor.moveToNext())add(cursor.getString(1))}}
            assertFalse(columns.any {it.contains("audio",true)||it.contains("pcm",true)||it.contains("recording",true)})
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
            for(suffix in listOf("","-wal","-shm")){val file=java.io.File(context.getDatabasePath(name).path+suffix);if(file.exists())assertFalse(file.readBytes().toString(Charsets.UTF_8).contains(marker))}
        } finally {db.close();context.deleteDatabase(name)}
    }
}
