package com.ai.assistance.operit

import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.api.chat.RoomRequestStore
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.model.*
import com.ai.assistance.operit.data.security.LocalWallpaper
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.UUID

class MessageAttachmentAndroidTest {
    @Test fun imageIsNormalizedEncryptedScopedAndCascadeDeleted()=runBlocking {
        System.loadLibrary("sqlcipher")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="message-image-${UUID.randomUUID()}.db";val key=ByteArray(32).also {SecureRandom().nextBytes(it)}
        var db=Room.databaseBuilder(context,AppDatabase::class.java,name).openHelperFactory(SupportOpenHelperFactory(key.copyOf())).build()
        try {
            val source=Bitmap.createBitmap(80,60,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.rgb(25,120,180))}
            val png=ByteArrayOutputStream().also {source.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray();source.recycle()
            val prepared=LocalWallpaper.message(png.inputStream())
            val image=PromptImage(MessageImages.MIME_TYPE,prepared.width,prepared.height,prepared.sha256,prepared.bytes)
            val config=ModelConfigData("config","vision","OpenAI 兼容","https://example.invalid/v1/chat/completions","vision-model",60,"ref",visionEnabled=true)
            val role=RoleEntity("role","A","persona",spaceId="space");val chat=ChatEntity("chat",role.id,config.id,"chat")
            db.dao().saveConfig(config);db.dao().saveRole(role);db.dao().insertChat(chat)
            val snapshot=RoomRequestStore(db).prepareWithImage(role.scope(),chat.id,"describe",image,"request")
            assertArrayEquals(prepared.bytes,snapshot.turns.last().images.single().data)
            val frozen=snapshot.turns.last().images.single().data.copyOf();image.data[2]=(image.data[2].toInt() xor 0x01).toByte()
            assertArrayEquals(frozen,snapshot.turns.last().images.single().data)
            val stored=db.dao().historyAttachments(role.id,role.spaceId,chat.id).single();assertEquals(prepared.sha256,stored.sha256)
            assertArrayEquals(frozen,stored.data)
            assertFalse(context.getDatabasePath(name).readBytes().toString(Charsets.ISO_8859_1).contains(prepared.bytes.take(48).toByteArray().toString(Charsets.ISO_8859_1)))
            db.close();db=Room.databaseBuilder(context,AppDatabase::class.java,name).openHelperFactory(SupportOpenHelperFactory(key.copyOf())).build()
            assertEquals(prepared.sha256,db.dao().historyAttachments(role.id,role.spaceId,chat.id).single().sha256)
            db.dao().deleteMessages(role.id,role.spaceId,chat.id);assertTrue(db.dao().historyAttachments(role.id,role.spaceId,chat.id).isEmpty())
        }finally{db.close();context.deleteDatabase(name)}
    }
}
