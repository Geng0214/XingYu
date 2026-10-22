package com.ai.assistance.operit

import android.graphics.Bitmap
import android.graphics.Color
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.data.db.*
import com.ai.assistance.operit.data.model.*
import com.ai.assistance.operit.data.security.LocalWallpaper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.UUID

class ChatLibraryAndroidTest {
    @Test fun privatePortraitLibraryIsolationAndPersistence()=runBlocking {
        System.loadLibrary("sqlcipher")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="library-"+UUID.randomUUID()+".db"
        val key=ByteArray(32).also { SecureRandom().nextBytes(it) }
        fun open()=Room.databaseBuilder(context,AppDatabase::class.java,name).openHelperFactory(SupportOpenHelperFactory(key.copyOf())).build()
        var db=open()
        try {
            val repo=RoleRepository(db)
            val a=repo.saveRole(null,"same","A-persona","",null)
            val b=repo.saveRole(null,"same","B-persona","",null)
            db.dao().saveConfig(ModelConfigData("config","test","OpenAI 兼容","https://example.com/v1/chat/completions","test",90,"synthetic-ref"))
            val ca=repo.createChat(a.scope(),"config");val cb=repo.createChat(b.scope(),"config")
            val rows=(0 until 120).flatMap { i->listOf(
                MessageEntity("A-$i",ca.id,"request-$i","assistant","A-marker-$i 中文🙂 100% _",MessageState.COMPLETE,i.toLong()),
                MessageEntity("B-$i",cb.id,"request-$i","assistant","B-marker-$i 中文🙂 100% _",MessageState.COMPLETE,i.toLong())) }
            db.dao().insertMessages(rows)
            repeat(50) { i->
                val r=if(i%2==0)a else b;val c=if(i%2==0)ca else cb;val label=if(i%2==0)"A"else"B"
                val found=db.dao().searchMessages(r.id,r.spaceId,"中文🙂",false,50,0).first()
                assertEquals(50,found.size);assertTrue(found.all { it.content.startsWith(label) })
                repo.setFavorite(r.scope(),c.id,"$label-0",true)
                try { repo.setFavorite(r.scope(),if(c==ca)cb.id else ca.id,if(label=="A")"B-0"else"A-0",true);fail() }catch(e:ChatFailure){assertEquals("ACCESS",e.code)}
            }
            val pages=listOf(0,50,100).map { offset->db.dao().searchMessages(a.id,a.spaceId,"_",false,50,offset).first() }
            assertEquals(listOf(50,50,20),pages.map { it.size });assertEquals(120,pages.flatten().map { it.id }.distinct().size)
            assertTrue(db.dao().searchMessages(a.id,b.spaceId,"",false,50,0).first().isEmpty())
            assertTrue(db.dao().searchMessages(a.id,a.spaceId,"' OR 1=1 --",false,50,0).first().isEmpty())
            repo.setPinned(a.scope(),ca.id,true)
            assertEquals(ca.id,db.dao().inbox().first().first().chatId)
            val image=Bitmap.createBitmap(320,160,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.MAGENTA) }
            val input=ByteArrayOutputStream().also { image.compress(Bitmap.CompressFormat.PNG,100,it) }.toByteArray();image.recycle()
            val portrait=LocalWallpaper.portrait(input.inputStream())
            assertEquals(128,portrait.bitmap.width);assertEquals(128,portrait.bitmap.height);assertTrue(portrait.bytes.size<=65536)
            repo.savePortrait(a.scope(),"role",portrait.bytes);repo.savePortrait(a.scope(),"self",portrait.bytes)
            assertNull(db.dao().portrait(a.id,b.spaceId,"role").first())
            val copy=repo.copyRole(a.scope())
            assertNull(db.dao().portrait(copy.id,copy.spaceId,"role").first())
            assertFalse(RoleTemplateCodec.encode(RoleTemplateCodec.fromRole(a)).contains("portrait"))
            db.close();db=open()
            assertArrayEquals(portrait.bytes,db.dao().portrait(a.id,a.spaceId,"role").first()!!.image)
            assertTrue(db.dao().chat(a.id,a.spaceId,ca.id)!!.pinned)
            assertEquals("A-0",db.dao().searchMessages(a.id,a.spaceId,"",true,50,0).first().single().id)
            val disk=context.getDatabasePath(name).readBytes().toString(Charsets.ISO_8859_1)
            assertFalse(disk.contains("A-marker"));assertFalse(disk.contains(portrait.bytes.take(64).toByteArray().toString(Charsets.ISO_8859_1)))
            RoleRepository(db).deleteChat(a.scope(),ca.id)
            assertTrue(db.dao().searchMessages(a.id,a.spaceId,"",true,50,0).first().isEmpty())
            assertEquals(50,db.dao().searchMessages(b.id,b.spaceId,"",false,50,0).first().size)
            RoleRepository(db).deleteRole(a.scope())
            assertNull(db.dao().portrait(a.id,a.spaceId,"role").first())
        } finally { db.close();context.deleteDatabase(name) }
    }
}
