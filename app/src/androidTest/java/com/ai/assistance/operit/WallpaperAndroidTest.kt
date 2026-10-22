package com.ai.assistance.operit

import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.data.db.*
import com.ai.assistance.operit.data.model.*
import com.ai.assistance.operit.data.security.LocalWallpaper
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.UUID

class WallpaperAndroidTest {
    @Test fun normalizeEncryptScopeCopyAndDelete()=runBlocking {
        System.loadLibrary("sqlcipher")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="wallpaper-"+UUID.randomUUID()+".db"
        val key=ByteArray(32).also { SecureRandom().nextBytes(it) }
        var db=Room.databaseBuilder(context,AppDatabase::class.java,name).openHelperFactory(SupportOpenHelperFactory(key.copyOf())).build()
        try {
            val bitmap=Bitmap.createBitmap(2400,1200,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(184,130,161)) }
            val original=ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }.toByteArray()
            bitmap.recycle()
            val prepared=LocalWallpaper.prepare(original.inputStream())
            assertTrue(maxOf(prepared.bitmap.width,prepared.bitmap.height)<=1600)
            assertTrue(prepared.bytes.size<=2*1024*1024)
            assertEquals(0xff,prepared.bytes[0].toInt() and 255)
            val repo=RoleRepository(db)
            val a=repo.saveRole(null,"A","a-persona","",null)
            val b=repo.saveRole(null,"B","b-persona","",null)
            repo.saveAppearance(a.scope(),prepared.bytes,0.25f,"rose")
            assertNull(db.dao().readAppearance(b.id,a.spaceId))
            assertNull(db.dao().readAppearance(b.id,b.spaceId))
            val copied=repo.copyRole(a.scope())
            assertNull(db.dao().readAppearance(copied.id,copied.spaceId))
            assertFalse(RoleTemplateCodec.encode(RoleTemplateCodec.fromRole(a)).contains("wallpaper"))
            db.close()
            db=Room.databaseBuilder(context,AppDatabase::class.java,name).openHelperFactory(SupportOpenHelperFactory(key.copyOf())).build()
            assertArrayEquals(prepared.bytes,db.dao().readAppearance(a.id,a.spaceId)!!.wallpaper)
            assertEquals(0.25f,db.dao().readAppearance(a.id,a.spaceId)!!.dimming)
            val encrypted=context.getDatabasePath(name).readBytes()
            val prefix=prepared.bytes.take(64).toByteArray()
            assertFalse(encrypted.toString(Charsets.ISO_8859_1).contains(prefix.toString(Charsets.ISO_8859_1)))
            RoleRepository(db).deleteRole(a.scope())
            assertNull(db.dao().readAppearance(a.id,a.spaceId))
            try { LocalWallpaper.prepare(byteArrayOf(1,2,3).inputStream());fail() }catch(e:ChatFailure){assertEquals("IMAGE",e.code)}
        } finally { db.close();context.deleteDatabase(name) }
    }
}
