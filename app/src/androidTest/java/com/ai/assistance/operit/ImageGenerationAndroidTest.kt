package com.ai.assistance.operit

import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.db.ImageGenerationRepository
import com.ai.assistance.operit.data.db.RoleRepository
import com.ai.assistance.operit.data.model.ChatFailure
import com.ai.assistance.operit.data.model.ImageGenerationConfig
import com.ai.assistance.operit.data.model.ImageGenerations
import com.ai.assistance.operit.data.model.MessageState
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.security.LocalWallpaper
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ImageGenerationAndroidTest {
    @Test fun generatedImageIsNormalizedEncryptedScopedRecoveredAndCascadeDeleted()=runBlocking {
        System.loadLibrary("sqlcipher")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="generated-image-${UUID.randomUUID()}.db";val key=ByteArray(32).also {SecureRandom().nextBytes(it)}
        fun open()=Room.databaseBuilder(context,AppDatabase::class.java,name).openHelperFactory(SupportOpenHelperFactory(key.copyOf())).build()
        var db=open()
        try {
            val textConfig=ModelConfigData("text","text","OpenAI 兼容","https://example.invalid/v1/chat/completions","model",60,"text-ref")
            // The current service contract no longer stores pricing, including in encrypted request snapshots.
            val imageConfig=ImageGenerationConfig("image","OpenAI 图片","https://images.example.invalid/v1/images/generations","gpt-image-1",90,"image-ref",4,"1024x1024")
            db.dao().saveConfig(textConfig);db.dao().saveImageGenerationConfig(imageConfig)
            val roles=RoleRepository(db);val a=roles.saveRole(null,"A","persona","","text");val b=roles.saveRole(null,"B","persona","","text")
            val chatA=roles.createChat(a.scope(),"text");val chatB=roles.createChat(b.scope(),"text")
            val repository=ImageGenerationRepository(db)
            try {repository.prepare(b.scope(),chatA.id,imageConfig.id,"wrong-space");fail()}catch(e:ChatFailure){assertEquals("ACCESS",e.code)}

            val marker="IMAGE-GENERATION-PRIVATE-${UUID.randomUUID()}"
            val request=repository.prepare(a.scope(),chatA.id,imageConfig.id,"画面 $marker")
            repository.start(request)
            val bitmap=Bitmap.createBitmap(1800,900,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.rgb(130,91,176))}
            val png=ByteArrayOutputStream().also {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray();bitmap.recycle()
            val prepared=LocalWallpaper.generated(png.inputStream())
            assertTrue(maxOf(prepared.width,prepared.height)<=ImageGenerations.MAX_EDGE);assertTrue(prepared.bytes.size<=ImageGenerations.MAX_STORED)
            repository.complete(request,ImageGenerations.MIME_TYPE,prepared.width,prepared.height,prepared.sha256,prepared.bytes,request.image.createdAt+25)
            val saved=db.dao().generatedImages(a.id,a.spaceId,chatA.id).first().single()
            assertEquals(MessageState.COMPLETE,saved.state);assertEquals(marker,saved.prompt.removePrefix("画面 "));assertNotNull(saved.data)
            assertTrue(db.dao().generatedImages(b.id,b.spaceId,chatB.id).first().isEmpty())

            val interrupted=repository.prepare(b.scope(),chatB.id,imageConfig.id,"process-interruption-$marker");repository.start(interrupted)
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close();db.close()
            for(suffix in listOf("","-wal","-shm")) {val file=java.io.File(context.getDatabasePath(name).path+suffix)
                if(file.exists())assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains(marker))}

            db=open();db.recoverInterruptedRequests()
            val recovered=db.dao().generatedImages(b.id,b.spaceId,chatB.id).first().single()
            assertEquals(MessageState.FAILED,recovered.state);assertEquals("INTERRUPTED",recovered.errorCode)
            assertNotNull(db.dao().generatedImages(a.id,a.spaceId,chatA.id).first().single().data)
            RoleRepository(db).deleteChat(a.scope(),chatA.id)
            assertTrue(db.dao().generatedImages(a.id,a.spaceId,chatA.id).first().isEmpty());assertNull(db.dao().chat(a.id,a.spaceId,chatA.id))
        } finally {db.close();context.deleteDatabase(name)}
    }
}
