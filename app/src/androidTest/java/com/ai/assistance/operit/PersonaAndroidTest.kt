package com.ai.assistance.operit

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.api.chat.RoomRequestStore
import com.ai.assistance.operit.data.db.*
import com.ai.assistance.operit.data.model.*
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.security.SecureRandom
import java.util.UUID

class PersonaAndroidTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    @Test fun savedPersonasAreEncryptedScopedVersionedAndExcludedFromCopies()=runBlocking {
        System.loadLibrary("sqlcipher");val name="persona-${UUID.randomUUID()}.db";val key=ByteArray(32).also {SecureRandom().nextBytes(it)}
        val db=Room.databaseBuilder(context,AppDatabase::class.java,name).openHelperFactory(SupportOpenHelperFactory(key)).build()
        try {
            val repo=RoleRepository(db)
            db.dao().saveConfig(ModelConfigData("cfg","test","OpenAI 兼容","https://example.invalid/v1/chat/completions","test",60,"ref"))
            val original=repo.saveRole(null,"same","AI persona","hello","cfg")
            val a=repo.savePersona(original.scope(),original.version," 小青😀 ","A-PRIVATE-MASK",true)
            val rawB=repo.saveRole(null,"same","AI persona","","cfg")
            val b=repo.savePersona(rawB.scope(),rawB.version,"小白","B-PRIVATE-MASK",true)
            assertEquals("小青😀",a.userName);assertEquals(original.version+1,a.version)
            try {repo.savePersona(original.scope(),original.version,"stale","bad",true);fail()}catch(e:ChatFailure){assertEquals("ROLE_CHANGED",e.code)}
            try {repo.savePersona(RoleAccessScope(a.id,b.spaceId),a.version,"wrong","bad",true);fail()}catch(e:ChatFailure){assertEquals("ACCESS",e.code)}
            val copy=repo.copyRole(a.scope());assertFalse(copy.personaEnabled);assertEquals("",copy.userName);assertEquals("",copy.userPersona)
            val chats=listOf(repo.createChat(a.scope(),"cfg"),repo.createChat(b.scope(),"cfg"))
            val requests=RoomRequestStore(db)
            repeat(50){n->val index=n%2;val role=listOf(a,b)[index]
                val request=requests.prepare(role.scope(),chats[index].id,"probe-$n",UUID.randomUUID().toString())
                assertTrue(request.turns.first().content.contains(if(index==0)"A-PRIVATE-MASK"else"B-PRIVATE-MASK"))
                assertFalse(request.turns.any {it.content.contains(if(index==0)"B-PRIVATE-MASK"else"A-PRIVATE-MASK")})
                requests.update(request,"done",MessageState.COMPLETE,"",1,1)
            }
            val frozen=requests.prepare(a.scope(),chats[0].id,"in-flight",UUID.randomUUID().toString())
            val disabled=repo.savePersona(a.scope(),a.version,a.userName,a.userPersona,false)
            assertTrue(frozen.turns.first().content.contains("A-PRIVATE-MASK"));requests.update(frozen,"done",MessageState.COMPLETE,"",1,1)
            val next=requests.prepare(a.scope(),chats[0].id,"after-disable",UUID.randomUUID().toString())
            assertFalse(next.turns.first().content.contains("A-PRIVATE-MASK"));assertEquals("A-PRIVATE-MASK",disabled.userPersona)
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
            for(suffix in listOf("","-wal","-shm")){val file=java.io.File(context.getDatabasePath(name).path+suffix)
                if(file.exists()){val raw=file.readBytes().toString(Charsets.UTF_8);assertFalse(raw.contains("A-PRIVATE-MASK"));assertFalse(raw.contains("B-PRIVATE-MASK"))}}
            repo.deleteRole(b.scope());assertNull(db.dao().role(b.id));assertNotNull(db.dao().role(a.id))
        }finally {db.close();context.deleteDatabase(name)}
    }
    @Test fun corruptVersionSixOwnershipRejectsPersonaMigrationWithoutAlteringOriginalDatabase()=runBlocking {
        System.loadLibrary("sqlcipher");val name="persona-migration-${UUID.randomUUID()}.db";val key=ByteArray(32).also {SecureRandom().nextBytes(it)}
        val helper=SupportOpenHelperFactory(key.copyOf()).create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name).callback(object:SupportSQLiteOpenHelper.Callback(6){
            override fun onCreate(db:SupportSQLiteDatabase){
                val schema=JSONObject(instrumentation.context.assets.open("com.ai.assistance.operit.data.db.AppDatabase/6.json").bufferedReader().use {it.readText()}).getJSONObject("database")
                val entities=schema.getJSONArray("entities")
                repeat(entities.length()){i->val entity=entities.getJSONObject(i);val table=entity.getString("tableName")
                    db.execSQL(entity.getString("createSql").replace("\u0024{TABLE_NAME}",table))
                    if(entity.has("indices")){val indices=entity.getJSONArray("indices");repeat(indices.length()){j->db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\u0024{TABLE_NAME}",table))}}}
                val setup=schema.getJSONArray("setupQueries");repeat(setup.length()){db.execSQL(setup.getString(it))}
                db.execSQL("INSERT INTO roles(id,name,prompt,version,spaceId,openingMessage,defaultConfigId,replyStyle) VALUES('r','name','original',9,'s','',NULL,'short')")
                db.execSQL("INSERT INTO role_appearance(roleId,spaceId,wallpaper,dimming,palette) VALUES('r','foreign-space',NULL,0.2,'rose')")
            }
            override fun onUpgrade(db:SupportSQLiteDatabase,oldVersion:Int,newVersion:Int)=error("Unexpected upgrade")
        }).build())
        try {
            helper.writableDatabase;helper.close()
            val db=Room.databaseBuilder(context,AppDatabase::class.java,name).openHelperFactory(SupportOpenHelperFactory(key.copyOf())).addMigrations(AppDatabase.MIGRATION_6_7,AppDatabase.MIGRATION_7_8,AppDatabase.MIGRATION_8_9,AppDatabase.MIGRATION_9_10,AppDatabase.MIGRATION_10_11,AppDatabase.MIGRATION_11_12,AppDatabase.MIGRATION_12_13,AppDatabase.MIGRATION_13_14,AppDatabase.MIGRATION_14_15,AppDatabase.MIGRATION_15_16,AppDatabase.MIGRATION_16_17,AppDatabase.MIGRATION_17_18,AppDatabase.MIGRATION_18_19,AppDatabase.MIGRATION_19_20,AppDatabase.MIGRATION_20_21,AppDatabase.MIGRATION_21_22,AppDatabase.MIGRATION_22_23,AppDatabase.MIGRATION_23_24,AppDatabase.MIGRATION_24_25,AppDatabase.MIGRATION_25_26,AppDatabase.MIGRATION_26_27,AppDatabase.MIGRATION_27_28,AppDatabase.MIGRATION_28_29,AppDatabase.MIGRATION_29_30,AppDatabase.MIGRATION_30_31).build()
            try {try {db.dao().role("r");fail()}catch(_:IllegalStateException){}}finally {db.close()}
            val original=helper.readableDatabase
            assertEquals(6,original.version)
            original.query("PRAGMA table_info(roles)").use {cursor->while(cursor.moveToNext())assertNotEquals("userName",cursor.getString(1))}
            original.query("SELECT prompt,version FROM roles WHERE id='r'").use {assertTrue(it.moveToFirst());assertEquals("original",it.getString(0));assertEquals(9,it.getInt(1))}
        }finally {helper.close();context.deleteDatabase(name)}
    }
}
