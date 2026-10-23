package com.ai.assistance.operit

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.data.db.AppDatabase
import com.ai.assistance.operit.data.security.CredentialVault
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.security.SecureRandom
import java.util.UUID

class MigrationAndroidTest {
    private val instrumentation=InstrumentationRegistry.getInstrumentation()
    private val context=instrumentation.targetContext
    private fun versionOne(name: String,key: ByteArray,orphan: Boolean,credentialRef: String) {
        val schema=JSONObject(instrumentation.context.assets.open("com.ai.assistance.operit.data.db.AppDatabase/1.json").bufferedReader().use { it.readText() }).getJSONObject("database")
        val helper=SupportOpenHelperFactory(key.copyOf()).create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
            .callback(object:SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db:SupportSQLiteDatabase) {
                    val entities=schema.getJSONArray("entities")
                    for(i in 0 until entities.length()) {
                        val entity=entities.getJSONObject(i)
                        db.execSQL(entity.getString("createSql").replace("\u0024{TABLE_NAME}",entity.getString("tableName")))
                        val indices=entity.optJSONArray("indices")
                        if(indices!=null)for(j in 0 until indices.length())db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\u0024{TABLE_NAME}",entity.getString("tableName")))
                    }
                    val setup=schema.getJSONArray("setupQueries")
                    for(i in 0 until setup.length())db.execSQL(setup.getString(i))
                    db.execSQL("INSERT INTO roles VALUES ('default','name','persona',7)")
                    db.execSQL("INSERT INTO roles VALUES ('second','name','second-persona',3)")
                    db.execSQL("INSERT INTO model_configs VALUES ('config','model','OpenAI 兼容','https://example.com/v1/chat/completions','test',90,?,4)",arrayOf(credentialRef))
                    db.execSQL("INSERT INTO chats VALUES ('chat',?,'config','existing-title',123)",arrayOf(if(orphan)"missing"else"default"))
                    db.execSQL("INSERT INTO messages VALUES ('user','chat','request','user','existing-text','COMPLETE',123,'',-1,-1)")
                    db.execSQL("INSERT INTO messages VALUES ('answer','chat','request','assistant','partial','GENERATING',124,'',5,6)")
                }
                override fun onUpgrade(db:SupportSQLiteDatabase,oldVersion:Int,newVersion:Int)=error("Unexpected upgrade")
            }).build())
        helper.writableDatabase;helper.close()
    }
    @Test fun preservesEncryptedVersionOneDataAndRejectsBrokenOwnership()=runBlocking {
        System.loadLibrary("sqlcipher")
        for((legacyVersion,orphan) in listOf(1 to false,1 to true,2 to false,3 to false,4 to false,5 to false,6 to false,7 to false,8 to false,9 to false,10 to false,11 to false,12 to false,13 to false,14 to false,15 to false,16 to false,17 to false,18 to false,19 to false,20 to false,21 to false,22 to false,23 to false,24 to false,25 to false,26 to false,27 to false,28 to false,29 to false,30 to false,31 to false,32 to false,33 to false,34 to false)) {
            val name="migration-"+UUID.randomUUID()+".db"
            val key=ByteArray(32).also { SecureRandom().nextBytes(it) }
            val vault=CredentialVault(context)
            val secret="synthetic-migration-key-"+UUID.randomUUID()
            val endpoint="https://example.com/v1/chat/completions"
            val ref=vault.save(secret,endpoint)
            versionOne(name,key,orphan,ref)
            if(legacyVersion>=2) {
                val helper=SupportOpenHelperFactory(key.copyOf()).create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                    .callback(object:SupportSQLiteOpenHelper.Callback(legacyVersion) {
                        override fun onCreate(db:SupportSQLiteDatabase)=error("Missing legacy database")
                        override fun onUpgrade(db:SupportSQLiteDatabase,oldVersion:Int,newVersion:Int) {
                            AppDatabase.MIGRATION_1_2.migrate(db)
                            if(legacyVersion>=3) {
                                AppDatabase.MIGRATION_2_3.migrate(db)
                                db.execSQL("UPDATE roles SET replyStyle='paragraph' WHERE id='default'")
                            }
                            db.execSQL("UPDATE roles SET spaceId='frozen-space' WHERE id='default'")
                            if(legacyVersion>=3)db.execSQL("INSERT INTO role_appearance VALUES ('default','frozen-space',?,0.3,'blue')",arrayOf(byteArrayOf(1,2,3)))
                            if(legacyVersion>=4) {
                                AppDatabase.MIGRATION_3_4.migrate(db)
                                db.execSQL("UPDATE chats SET pinned=1 WHERE id='chat'")
                                db.execSQL("UPDATE messages SET favorite=1 WHERE id='user'")
                                db.execSQL("INSERT INTO role_portraits VALUES ('default','frozen-space','role',?)",arrayOf(byteArrayOf(4,5,6)))
                            }
                            if(legacyVersion>=5) {
                                AppDatabase.MIGRATION_4_5.migrate(db)
                                db.execSQL("INSERT INTO mcp_extensions VALUES ('existing-extension','existing-name','https://example.com/mcp',?,3000,1,4)",arrayOf(ref))
                                db.execSQL("INSERT INTO extension_tools VALUES ('existing-extension','echo','existing-description','{}')")
                                db.execSQL("INSERT INTO role_tool_grants VALUES ('default','frozen-space','existing-extension','echo',1,3)")
                            }
                            if(legacyVersion>=6)AppDatabase.MIGRATION_5_6.migrate(db)
                            if(legacyVersion>=7)AppDatabase.MIGRATION_6_7.migrate(db)
                            if(legacyVersion>=8)AppDatabase.MIGRATION_7_8.migrate(db)
                            if(legacyVersion>=9)AppDatabase.MIGRATION_8_9.migrate(db)
                            if(legacyVersion>=10)AppDatabase.MIGRATION_9_10.migrate(db)
                            if(legacyVersion>=11)AppDatabase.MIGRATION_10_11.migrate(db)
                            if(legacyVersion>=12)AppDatabase.MIGRATION_11_12.migrate(db)
                            if(legacyVersion>=13)AppDatabase.MIGRATION_12_13.migrate(db)
                            if(legacyVersion>=14)AppDatabase.MIGRATION_13_14.migrate(db)
                            if(legacyVersion>=15)AppDatabase.MIGRATION_14_15.migrate(db)
                            if(legacyVersion>=16)AppDatabase.MIGRATION_15_16.migrate(db)
                            if(legacyVersion>=17)AppDatabase.MIGRATION_16_17.migrate(db)
                            if(legacyVersion>=18)AppDatabase.MIGRATION_17_18.migrate(db)
                            if(legacyVersion>=19)AppDatabase.MIGRATION_18_19.migrate(db)
                            if(legacyVersion>=20)AppDatabase.MIGRATION_19_20.migrate(db)
                            if(legacyVersion>=21)AppDatabase.MIGRATION_20_21.migrate(db)
                            if(legacyVersion>=22)AppDatabase.MIGRATION_21_22.migrate(db)
                            if(legacyVersion>=23)AppDatabase.MIGRATION_22_23.migrate(db)
                            if(legacyVersion>=24)AppDatabase.MIGRATION_23_24.migrate(db)
                            if(legacyVersion>=25)AppDatabase.MIGRATION_24_25.migrate(db)
                            if(legacyVersion>=26)AppDatabase.MIGRATION_25_26.migrate(db)
                            if(legacyVersion>=27)AppDatabase.MIGRATION_26_27.migrate(db)
                            if(legacyVersion>=28)AppDatabase.MIGRATION_27_28.migrate(db)
                            if(legacyVersion>=29)AppDatabase.MIGRATION_28_29.migrate(db)
                            if(legacyVersion>=30)AppDatabase.MIGRATION_29_30.migrate(db)
                            if(legacyVersion>=31)AppDatabase.MIGRATION_30_31.migrate(db)
                            if(legacyVersion>=32)AppDatabase.MIGRATION_31_32.migrate(db)
                            if(legacyVersion>=33)AppDatabase.MIGRATION_32_33.migrate(db)
                            if(legacyVersion>=34)AppDatabase.MIGRATION_33_34.migrate(db)
                            val schema=JSONObject(instrumentation.context.assets.open("com.ai.assistance.operit.data.db.AppDatabase/$legacyVersion.json").bufferedReader().use { it.readText() })
                            db.execSQL("UPDATE room_master_table SET identity_hash=? WHERE id=42",arrayOf(schema.getJSONObject("database").getString("identityHash")))
                        }
                    }).build())
                helper.writableDatabase;helper.close()
            }
            val db=Room.databaseBuilder(context,AppDatabase::class.java,name).openHelperFactory(SupportOpenHelperFactory(key.copyOf()))
                .addMigrations(AppDatabase.MIGRATION_1_2,AppDatabase.MIGRATION_2_3,AppDatabase.MIGRATION_3_4,AppDatabase.MIGRATION_4_5,AppDatabase.MIGRATION_5_6,AppDatabase.MIGRATION_6_7,AppDatabase.MIGRATION_7_8,AppDatabase.MIGRATION_8_9,AppDatabase.MIGRATION_9_10,AppDatabase.MIGRATION_10_11,AppDatabase.MIGRATION_11_12,AppDatabase.MIGRATION_12_13,AppDatabase.MIGRATION_13_14,AppDatabase.MIGRATION_14_15,AppDatabase.MIGRATION_15_16,AppDatabase.MIGRATION_16_17,AppDatabase.MIGRATION_17_18,AppDatabase.MIGRATION_18_19,AppDatabase.MIGRATION_19_20,AppDatabase.MIGRATION_20_21,AppDatabase.MIGRATION_21_22,AppDatabase.MIGRATION_22_23,AppDatabase.MIGRATION_23_24,AppDatabase.MIGRATION_24_25,AppDatabase.MIGRATION_25_26,AppDatabase.MIGRATION_26_27,AppDatabase.MIGRATION_27_28,AppDatabase.MIGRATION_28_29,AppDatabase.MIGRATION_29_30,AppDatabase.MIGRATION_30_31,AppDatabase.MIGRATION_31_32,AppDatabase.MIGRATION_32_33,AppDatabase.MIGRATION_33_34,AppDatabase.MIGRATION_34_35).build()
            try {
                if(orphan) {
                    var rejected=false
                    try { db.dao().role("default") } catch(_:Exception) { rejected=true }
                    assertTrue("Orphan ownership must reject upgrade",rejected)
                } else {
                    val role=db.dao().role("default")!!
                    assertEquals(7,role.version.toInt());assertEquals("persona",role.prompt)
                    assertEquals("character",role.conversationMode);assertTrue(db.relationships().read(role.id,role.spaceId).isEmpty())
                    assertEquals("",role.userName);assertEquals("",role.userPersona);assertFalse(role.personaEnabled)
                    assertTrue(db.worldbook().read(role.id,role.spaceId).isEmpty())
                    assertTrue(role.spaceId.isNotBlank());assertEquals("",role.openingMessage);assertNull(role.defaultConfigId)
                    if(legacyVersion>=2)assertEquals("frozen-space",role.spaceId)
                    assertEquals(if(legacyVersion>=3)"paragraph"else"short",role.replyStyle)
                    if(legacyVersion>=3)assertArrayEquals(byteArrayOf(1,2,3),db.dao().readAppearance(role.id,role.spaceId)!!.wallpaper)
                    if(legacyVersion>=4)assertArrayEquals(byteArrayOf(4,5,6),db.dao().portrait(role.id,role.spaceId,"role").first()!!.image)
                    assertTrue(db.toolpkgs().list().first().isEmpty())
                    if(legacyVersion>=5) {
                        val extension=db.extensions().extension("existing-extension")!!
                        assertEquals(4L,extension.version);assertEquals(ref,extension.credentialRef);assertTrue(extension.enabled)
                        val grant=db.extensions().grant(role.id,role.spaceId,extension.id,"echo")!!
                        assertEquals(3L,grant.version);assertTrue(grant.enabled)
                    }
                    assertNotEquals(role.spaceId,db.dao().role("second")!!.spaceId)
                    assertEquals(ref,db.dao().config("config")!!.credentialRef)
                    assertFalse(db.dao().config("config")!!.visionEnabled)
                    assertTrue(db.dao().historyAttachments(role.id,role.spaceId,"chat").isEmpty())
                    assertTrue(db.dao().historyMaterials(role.id,role.spaceId,"chat").isEmpty())
                    assertTrue(db.dao().stickerAssets(role.id,role.spaceId).first().isEmpty())
                    assertTrue(db.dao().historyStickers(role.id,role.spaceId,"chat").isEmpty())
                    assertTrue(db.dao().imageGenerationConfigs().first().isEmpty())
                    assertTrue(db.dao().generatedImages(role.id,role.spaceId,"chat").first().isEmpty())
                    assertTrue(db.memories().read(role.id,role.spaceId).isEmpty())
                    assertTrue(db.diaries().read(role.id,role.spaceId).isEmpty())
                    assertNull(db.environment().profile(role.id,role.spaceId))
                    assertTrue(db.planner().readPlanner(role.id,role.spaceId).isEmpty());assertTrue(db.planner().readCycles(role.id,role.spaceId).isEmpty())
                    assertNull(db.proactive().setting(role.id,role.spaceId));assertTrue(db.proactive().enabledSettings().isEmpty())
                    assertTrue(db.dao().speechServiceConfigs().first().isEmpty())
                    assertTrue(db.activities().observe(role.id,role.spaceId).first().isEmpty())
                    assertTrue(db.dao().historyVoices(role.id,role.spaceId,"chat").isEmpty())
                    assertTrue(db.dao().messageReactions(role.id,role.spaceId,"chat").first().isEmpty())
                    assertTrue(db.scheduled().messages(role.id,role.spaceId).first().isEmpty())
                    assertEquals(35,db.openHelper.readableDatabase.version)
                    assertNull(db.home().home())
                    assertEquals(secret,vault.read(db.dao().config("config")!!.credentialRef,endpoint))
                    assertEquals("existing-title",db.dao().chat(role.id,role.spaceId,"chat")!!.title)
                    assertEquals(legacyVersion>=4,db.dao().chat(role.id,role.spaceId,"chat")!!.pinned)
                    db.dao().recoverInterrupted()
                    val rows=db.dao().history(role.id,role.spaceId,"chat")
                    assertEquals("existing-text",rows.first().content);assertEquals("partial",rows.last().content)
                    assertEquals("paragraph",rows.last().bubbleStyle)
                    assertEquals(legacyVersion>=4,rows.first().favorite)
                    assertFalse(rows.last().favorite)
                    assertEquals("INTERRUPTED",rows.last().errorCode)
                }
            } finally { db.close() }
            if(orphan) {
                // Open the same encrypted file at v1: failed migration must leave schema and rows intact.
                val helper=SupportOpenHelperFactory(key.copyOf()).create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                    .callback(object:SupportSQLiteOpenHelper.Callback(1) {
                        override fun onCreate(db:SupportSQLiteDatabase)=error("Lost original DB")
                        override fun onUpgrade(db:SupportSQLiteDatabase,oldVersion:Int,newVersion:Int)=error("Unexpected version")
                    }).build())
                helper.readableDatabase.query("SELECT content FROM messages WHERE id='user'").use { assertTrue(it.moveToFirst());assertEquals("existing-text",it.getString(0)) }
                assertEquals(1,helper.readableDatabase.version)
                helper.close()
            }
            context.deleteDatabase(name)
        }
    }
}
