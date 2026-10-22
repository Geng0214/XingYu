package com.ai.assistance.operit

import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.api.chat.RoomRequestStore
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.db.RoleRepository
import com.ai.assistance.operit.data.model.*
import com.ai.assistance.operit.data.security.LocalWallpaper
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.*
import org.junit.Test

class StickerAndroidTest {
    @Test fun staticStickerIsEncryptedScopedFavoritedAndMessageSurvivesLibraryDelete()=runBlocking {
        System.loadLibrary("sqlcipher")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="sticker-${UUID.randomUUID()}.db";val key=ByteArray(32).also {SecureRandom().nextBytes(it)}
        fun open()=Room.databaseBuilder(context,AppDatabase::class.java,name).openHelperFactory(SupportOpenHelperFactory(key.copyOf())).build()
        var db=open()
        try {
            val source=Bitmap.createBitmap(96,80,Bitmap.Config.ARGB_8888).apply {eraseColor(Color.rgb(231,113,158))}
            val png=ByteArrayOutputStream().also {source.compress(Bitmap.CompressFormat.PNG,100,it)}.toByteArray();source.recycle()
            val prepared=LocalWallpaper.sticker(png.inputStream())
            assertTrue(prepared.bytes.size<=StickerAssets.MAX_STORED);assertTrue(maxOf(prepared.width,prepared.height)<=StickerAssets.MAX_EDGE)
            val config=ModelConfigData("cfg","text","OpenAI 兼容","https://example.invalid/v1/chat/completions","model",60,"ref",visionEnabled=false)
            db.dao().saveConfig(config);val repo=RoleRepository(db)
            val a=repo.saveRole(null,"A","persona","","cfg");val b=repo.saveRole(null,"B","persona","","cfg");val chat=repo.createChat(a.scope(),"cfg")
            val prompt=PromptSticker(null,"开心",StickerAssets.MIME_TYPE,prepared.width,prepared.height,prepared.sha256,prepared.bytes)
            val asset=repo.addSticker(a.scope(),"开心",prompt)
            assertEquals(1,db.dao().stickerAssets(a.id,a.spaceId).first().size);assertTrue(db.dao().stickerAssets(b.id,b.spaceId).first().isEmpty())
            try {repo.selectSticker(b.scope(),asset.id);fail()}catch(e:ChatFailure){assertEquals("ACCESS",e.code)}
            repo.setStickerFavorite(a.scope(),asset.id,true);assertTrue(db.dao().stickerAsset(a.id,a.spaceId,asset.id)!!.favorite)
            val snapshot=RoomRequestStore(db).prepareWithPayload(a.scope(),chat.id,"",null,null,asset.promptSticker(),"request")
            assertTrue(snapshot.turns.last().content.contains("开心"));assertTrue(snapshot.turns.last().images.isEmpty())
            val frozen=repo.retryPayload(a.scope(),chat.id,"request").sticker!!
            repo.deleteSticker(a.scope(),asset.id);assertTrue(db.dao().stickerAssets(a.id,a.spaceId).first().isEmpty())
            assertArrayEquals(frozen.data,db.dao().requestSticker(a.id,a.spaceId,chat.id,"request")!!.data)
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close();db.close()
            for(suffix in listOf("","-wal","-shm")){val file=java.io.File(context.getDatabasePath(name).path+suffix);if(file.exists())assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains(prepared.bytes.take(48).toByteArray().toString(Charsets.ISO_8859_1)))}
            db=open();assertEquals("开心",db.dao().messageStickers(a.id,a.spaceId,chat.id).first().single().displayName)
            RoleRepository(db).deleteChat(a.scope(),chat.id);assertTrue(db.dao().historyStickers(a.id,a.spaceId,chat.id).isEmpty())
        } finally {db.close();context.deleteDatabase(name)}
    }
}
