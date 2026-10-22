package com.ai.assistance.operit

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.data.db.*
import com.ai.assistance.operit.data.model.*
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.*
import org.junit.Test
import java.security.SecureRandom
import java.util.UUID

class MusicAndroidTest {
    @Test fun scopedProgressTokensRecoveryAndDeletionUseRealEncryptedRepository()=verifyMedia("audio")
    @Test fun videoProgressTokensRecoveryAndDeletionUseRealEncryptedRepository()=verifyMedia("video")
    private fun verifyMedia(kind:String)=runBlocking {
        System.loadLibrary("sqlcipher")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="music-${UUID.randomUUID()}.db";val key=ByteArray(32).also {SecureRandom().nextBytes(it)}
        fun open()=Room.databaseBuilder(context,AppDatabase::class.java,name).openHelperFactory(SupportOpenHelperFactory(key.copyOf())).build()
        var db=open()
        try {
            val roles=RoleRepository(db)
            db.dao().saveConfig(ModelConfigData("cfg","test","OpenAI 兼容","https://example.invalid/v1/chat/completions","model",60,"ref",1))
            val a=roles.saveRole(null,"A","A","","cfg");val b=roles.saveRole(null,"B","B","","cfg")
            val chat=roles.createChat(a.scope(),"cfg");val other=roles.createChat(b.scope(),"cfg")
            val track=MusicTrack("track",a.id,a.spaceId,"MUSIC-PRIVATE",if(kind=="video")"video/mp4" else "audio/mpeg",10000,4,"a".repeat(64),"content://synthetic/song",
                mediaKind=kind,videoWidth=if(kind=="video")1920 else null,videoHeight=if(kind=="video")1080 else null)
            val repo=MusicRepository(db);repo.add(track)
            var s=repo.create(a.scope(),chat.id,track.id)
            repeat(50){n->
                assertNull(db.music().track(b.id,b.spaceId,track.id))
                assertNull(db.music().session(a.id,b.spaceId,chat.id,s.id))
                try{repo.begin(s.copy(roleId=b.id,spaceId=b.spaceId,chatId=other.id),"wrong");fail()}catch(e:ChatFailure){assertEquals("ACCESS",e.code)}
                val token="token-$n";val active=repo.begin(s,token).first
                assertTrue(repo.progress(active,token,1000L+n));assertFalse(repo.progress(active,"old",9000))
                assertTrue(repo.progress(active,token,2000L+n,finish=true))
                assertFalse(repo.progress(active,token,9000))
                s=db.music().session(a.id,a.spaceId,chat.id,s.id)!!
            }
            val active=repo.begin(s,"interrupted").first;repo.progress(active,"interrupted",4321)
            db.close();db=open();db.recoverInterruptedRequests()
            s=db.music().session(a.id,a.spaceId,chat.id,s.id)!!
            assertEquals("paused",s.state);assertNull(s.playbackToken);assertEquals(4321L,s.positionMs)
            assertFalse(MusicRepository(db).progress(s,"interrupted",9000))
            try{MusicRepository(db).delete(track);fail()}catch(e:ChatFailure){assertEquals("MUSIC_REFERENCED",e.code)}
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
            for(suffix in listOf("","-wal","-shm")) {
                val file=java.io.File(context.getDatabasePath(name).path+suffix)
                if(file.exists())assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains("MUSIC-PRIVATE"))
            }
            RoleRepository(db).deleteChat(a.scope(),chat.id)
            assertNull(db.music().session(a.id,a.spaceId,chat.id,s.id));assertNotNull(db.music().track(a.id,a.spaceId,track.id))
            RoleRepository(db).deleteRole(a.scope());assertNull(db.music().track(a.id,a.spaceId,track.id))
        }finally{db.close();context.deleteDatabase(name)}
    }
}
