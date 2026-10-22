package com.ai.assistance.operit

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.api.audio.BilibiliPlayer
import com.ai.assistance.operit.data.db.*
import com.ai.assistance.operit.data.model.*
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.*
import org.junit.Test
import java.security.SecureRandom
import java.util.UUID

class BilibiliAndroidTest {
    @Test fun webpageSettingsDisableApplicationBridgesAndPersistentWebStorageBeforeAnyLoad() {
        val i=InstrumentationRegistry.getInstrumentation()
        i.runOnMainSync {
            val player=BilibiliPlayer(i.targetContext,BilibiliVideo("BV1B7411m7LV"),0)
            try {
                val s=player.view.settings
                assertFalse(s.allowFileAccess);assertFalse(s.allowContentAccess);assertFalse(s.domStorageEnabled)
                assertEquals(android.webkit.WebSettings.LOAD_NO_CACHE,s.cacheMode)
                assertEquals(android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW,s.mixedContentMode)
                assertFalse(android.webkit.CookieManager.getInstance().acceptCookie())
                assertNull(player.view.url)
            }finally{player.close()}
        }
    }
    @Test fun encryptedWatchingScopeTokensRecoveryAndDeleteUseActualRepository()=runBlocking {
        System.loadLibrary("sqlcipher")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="bili-${UUID.randomUUID()}.db";val key=ByteArray(32).also {SecureRandom().nextBytes(it)}
        fun open()=Room.databaseBuilder(context,AppDatabase::class.java,name).openHelperFactory(SupportOpenHelperFactory(key.copyOf())).build()
        var db=open()
        try {
            db.dao().saveConfig(ModelConfigData("cfg","test","OpenAI 兼容","https://example.invalid/v1/chat/completions","model",60,"ref",1))
            val rr=RoleRepository(db);val a=rr.saveRole(null,"A","A","","cfg");val b=rr.saveRole(null,"B","B","","cfg")
            val chat=rr.createChat(a.scope(),"cfg");val other=rr.createChat(b.scope(),"cfg")
            val repo=BilibiliRepository(db);var watch=repo.create(a.scope(),chat.id,"WATCH-PRIVATE",BilibiliVideo("BV1B7411m7LV"))
            repeat(50){n->
                assertNull(db.bilibili().get(b.id,b.spaceId,other.id,watch.id))
                assertNull(db.bilibili().get(a.id,b.spaceId,chat.id,watch.id))
                try{repo.begin(watch.copy(roleId=b.id,spaceId=b.spaceId,chatId=other.id),"foreign");fail()}catch(e:ChatFailure){assertEquals("ACCESS",e.code)}
                val token="run-$n";val active=repo.begin(watch,token)
                assertTrue(repo.progress(active,token,1000L+n,590000));assertFalse(repo.progress(active,"old",9000,590000))
                assertTrue(repo.progress(active,token,1000L+n,590000,true));assertFalse(repo.progress(active,token,9000,590000))
                watch=db.bilibili().get(a.id,a.spaceId,chat.id,watch.id)!!
            }
            val active=repo.begin(watch,"interrupted");repo.progress(active,"interrupted",4321,590000)
            db.close();db=open();db.recoverInterruptedRequests()
            watch=db.bilibili().get(a.id,a.spaceId,chat.id,watch.id)!!
            assertEquals(4321L,watch.positionMs);assertNull(watch.playbackToken)
            assertFalse(BilibiliRepository(db).progress(watch,"interrupted",9999,590000))
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
            for(suffix in listOf("","-wal","-shm")) {
                val f=java.io.File(context.getDatabasePath(name).path+suffix)
                if(f.exists())assertFalse(f.readBytes().toString(Charsets.ISO_8859_1).contains("WATCH-PRIVATE"))
            }
            RoleRepository(db).deleteChat(a.scope(),chat.id)
            assertNull(db.bilibili().get(a.id,a.spaceId,chat.id,watch.id))
        }finally{db.close();context.deleteDatabase(name)}
    }
}
