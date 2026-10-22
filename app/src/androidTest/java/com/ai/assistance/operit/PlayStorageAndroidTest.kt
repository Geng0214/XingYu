package com.ai.assistance.operit

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.api.chat.*
import com.ai.assistance.operit.api.chat.llmprovider.*
import com.ai.assistance.operit.data.backup.*
import com.ai.assistance.operit.data.db.*
import com.ai.assistance.operit.data.model.*
import com.ai.assistance.operit.data.security.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.*
import org.junit.Test
import java.security.SecureRandom
import java.util.UUID
import org.json.JSONObject

class PlayStorageAndroidTest {
    @Test fun encryptedAdventureSurvivesRestartAndBackupWithConfirmedInventory()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        System.loadLibrary("sqlcipher");val name="adventure-test-${UUID.randomUUID()}.db"
        val key=ByteArray(32).also {SecureRandom().nextBytes(it)}
        fun open()=Room.databaseBuilder(context,AppDatabase::class.java,name).openHelperFactory(SupportOpenHelperFactory(key.copyOf())).build()
        var db=open()
        try {
            db.dao().saveConfig(ModelConfigData("cfg","test","openai","https://example.invalid/v1/chat/completions","model",60,"synthetic-ref"))
            val roles=RoleRepository(db);val role=roles.saveRole(null,"测试","角色设定","","cfg");val chat=roles.createChat(role.scope(),"cfg")
            var repo=PlayRepository(db)
            var row=repo.create(role.scope(),chat.id,"冒险",AdventurePlayState(AdventureGame.start()))
            row=repo.userAction(row.scope(),row.version,PlayAction.Adventure(AdventureAction.Interact("wood_a")))
            row=repo.userAction(row.scope(),row.version,PlayAction.Adventure(AdventureAction.Say(PlaySide.USER,"ENCRYPTED-ADVENTURE-MARKER")))
            val frozen=repo.prepare(row.scope(),row.version,"confirmed")
            val json="""{"schemaVersion":1,"action":"say","text":"一起把灯带回海边"}"""
            repo.update(frozen,PlayProgress(MessageState.WAITING))
            val progress=PlayProgress(MessageState.GENERATING,json,firstTokenMs=1,elapsedMs=2)
            repo.update(frozen,progress);repo.complete(frozen,PlayGenerations.decode(frozen,json),progress.copy(state=MessageState.COMPLETE))
            row=repo.timeline(row.scope()).activity
            row=repo.userAction(row.scope(),row.version,PlayAction.Adventure(AdventureAction.Say(PlaySide.USER,"ENCRYPTED-ADVENTURE-PENDING")))
            val pending=repo.prepare(row.scope(),row.version,"interrupted")
            repo.update(pending,PlayProgress(MessageState.WAITING))
            db.close();db=open();repo=PlayRepository(db);db.recoverInterruptedRequests()
            val restored=repo.timeline(row.scope());val game=(restored.activity.value().state as AdventurePlayState).game
            assertEquals(row.stateJson,restored.activity.stateJson);assertEquals("INTERRUPTED",restored.activity.status)
            assertEquals(1,game.inventory[AdventureItem.PLANK]);assertEquals(1,game.roleActionCount)
            assertEquals(1,restored.requests.count {it.errorCode=="INTERRUPTED"})
            val backup=BackupRepository(db,CredentialVault(context));val result=backup.restore(backup.snapshot(false,{}),{})
            assertEquals(1,result.playSessions);assertEquals(2,result.playRequests)
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
            for(suffix in listOf("","-wal","-shm")) {
                val file=java.io.File(context.getDatabasePath(name).path+suffix)
                if(file.exists())assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains("ENCRYPTED-ADVENTURE"))
            }
        }finally {db.close();context.deleteDatabase(name);key.fill(0)}
    }
    @Test fun encryptedTableGamesSurviveRestartAndBackupWithoutPlaintextSecrets()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        System.loadLibrary("sqlcipher");val name="table-test-${UUID.randomUUID()}.db"
        val key=ByteArray(32).also {SecureRandom().nextBytes(it)}
        fun open()=Room.databaseBuilder(context,AppDatabase::class.java,name).openHelperFactory(SupportOpenHelperFactory(key.copyOf())).build()
        var db=open()
        try {
            db.dao().saveConfig(ModelConfigData("cfg","test","openai","https://example.invalid/v1/chat/completions","model",60,"synthetic-ref"))
            val roles=RoleRepository(db);val role=roles.saveRole(null,"测试","角色设定","","cfg");val chat=roles.createChat(role.scope(),"cfg")
            var repo=PlayRepository(db)
            val states=listOf(WordPlayState(WordGuessGame.start(PlaySide.ROLE,WordPuzzle("物品","ENCRYPTED-WORD-MARKER"))),
                CardPlayState(FourColorGame.start(PlaySide.ROLE)),XiangqiPlayState.start(XqColor.BLACK))
            val saved=states.mapIndexed {i,state->
                val row=repo.create(role.scope(),chat.id,"测试活动 $i",state)
                val frozen=repo.prepare(row.scope(),row.version,"pending-$i")
                repo.update(frozen,PlayProgress(MessageState.WAITING))
                repo.update(frozen,PlayProgress(MessageState.GENERATING,"PARTIAL-$i",firstTokenMs=1,elapsedMs=2))
                row
            }
            db.close();db=open();repo=PlayRepository(db);db.recoverInterruptedRequests()
            saved.forEach {row->
                val restored=repo.timeline(row.scope())
                assertEquals(row.stateJson,restored.activity.stateJson);assertEquals("INTERRUPTED",restored.activity.status)
                assertEquals("INTERRUPTED",restored.requests.single().errorCode)
            }
            val backup=BackupRepository(db,CredentialVault(context));val snapshot=backup.snapshot(false,{})
            val result=backup.restore(snapshot,{})
            assertEquals(3,result.playSessions);assertEquals(3,result.playRequests)
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
            val privateDeck=(states[1] as CardPlayState).game.encode()
            val secret=JSONObject(privateDeck).getString("shuffleKey")
            for(suffix in listOf("","-wal","-shm")) {
                val file=java.io.File(context.getDatabasePath(name).path+suffix)
                if(file.exists()) {
                    val bytes=file.readBytes().toString(Charsets.ISO_8859_1)
                    assertFalse(bytes.contains("ENCRYPTED-WORD-MARKER"));assertFalse(bytes.contains(secret))
                }
            }
        } finally {db.close();context.deleteDatabase(name);key.fill(0)}
    }
    @Test fun schema28And29CipherUpgradePreservesCredentialAndHistoryOrRollsBackBrokenOwner()=runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation();val context=instrumentation.targetContext
        System.loadLibrary("sqlcipher")
        for((legacyVersion,orphan) in listOf(28,29).flatMap {version->listOf(version to false,version to true)}) {
            val name="play-upgrade-${UUID.randomUUID()}.db";val key=ByteArray(32).also {SecureRandom().nextBytes(it)}
            val vault=CredentialVault(context);val endpoint="https://example.invalid/v1/chat/completions"
            val ref=vault.save("synthetic-upgrade-key",endpoint)
            val helper=SupportOpenHelperFactory(key.copyOf()).create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object:SupportSQLiteOpenHelper.Callback(legacyVersion) {
                    override fun onCreate(sql:SupportSQLiteDatabase) {
                        val schema=JSONObject(instrumentation.context.assets.open("com.ai.assistance.operit.data.db.AppDatabase/$legacyVersion.json").bufferedReader().use {it.readText()}).getJSONObject("database")
                        val entities=schema.getJSONArray("entities")
                        repeat(entities.length()){i->val e=entities.getJSONObject(i);val table=e.getString("tableName")
                            sql.execSQL(e.getString("createSql").replace("\u0024{TABLE_NAME}",table))
                            // Room omits the indices member for tables that have none; only declared indexes belong in this fixture.
                            if(e.has("indices")){val indices=e.getJSONArray("indices");repeat(indices.length()){j->sql.execSQL(indices.getJSONObject(j).getString("createSql").replace("\u0024{TABLE_NAME}",table))}}}
                        val setup=schema.getJSONArray("setupQueries");repeat(setup.length()){sql.execSQL(setup.getString(it))}
                        sql.execSQL("INSERT INTO model_configs(id,name,provider,endpoint,model,timeoutSeconds,credentialRef,version) VALUES('cfg','test','openai',?,'model',60,?,7)",arrayOf(endpoint,ref))
                        sql.execSQL("INSERT INTO roles(id,name,prompt,version,spaceId) VALUES('role','same','KEEP-PERSONA',9,'space')")
                        sql.execSQL("INSERT INTO chats(id,roleId,configId,title,createdAt) VALUES('chat',?,'cfg','KEEP-TITLE',10)",arrayOf(if(orphan)"missing"else"role"))
                        sql.execSQL("INSERT INTO messages(id,chatId,requestId,kind,content,state,createdAt,errorCode,firstTokenMs,elapsedMs) VALUES('reply','chat','request','assistant','KEEP-PARTIAL','GENERATING',11,'',1,2)")
                    }
                    override fun onUpgrade(db:SupportSQLiteDatabase,oldVersion:Int,newVersion:Int)=error("Unexpected legacy helper upgrade")
                }).build())
            var db:AppDatabase?=null
            try {
                helper.writableDatabase;helper.close()
                val opened=Room.databaseBuilder(context,AppDatabase::class.java,name).openHelperFactory(SupportOpenHelperFactory(key.copyOf()))
                    .addMigrations(AppDatabase.MIGRATION_28_29,AppDatabase.MIGRATION_29_30,AppDatabase.MIGRATION_30_31,
                        AppDatabase.MIGRATION_31_32,AppDatabase.MIGRATION_32_33,AppDatabase.MIGRATION_33_34).build();db=opened
                if(orphan) {
                    try {opened.dao().role("role");fail()}catch(_:IllegalStateException){}
                    opened.close();db=null
                    val old=helper.readableDatabase;assertEquals(legacyVersion,old.version)
                    old.query("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='homes'").use {assertTrue(it.moveToFirst());assertEquals(0,it.getInt(0))}
                    old.query("SELECT prompt FROM roles WHERE id='role'").use {assertTrue(it.moveToFirst());assertEquals("KEEP-PERSONA",it.getString(0))}
                } else {
                    assertEquals(9L,opened.dao().role("role")!!.version)
                    assertEquals(ref,opened.dao().config("cfg")!!.credentialRef)
                    assertEquals("synthetic-upgrade-key",vault.read(ref,endpoint))
                    opened.recoverInterruptedRequests()
                    opened.openHelper.readableDatabase.query("SELECT content,state,errorCode FROM messages WHERE id='reply'").use {
                        assertTrue(it.moveToFirst());assertEquals("KEEP-PARTIAL",it.getString(0));assertEquals("FAILED",it.getString(1));assertEquals("INTERRUPTED",it.getString(2))}
                    assertTrue(opened.play().sessions("role","space","chat").isEmpty())
                    assertEquals(34,opened.openHelper.readableDatabase.version)
                }
            }finally {db?.close();helper.close();context.deleteDatabase(name);vault.removeCredential(ref);key.fill(0)}
        }
    }
    @Test fun actualEncryptedPlayStorageIsolationCancellationRecoveryAndBackup()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        System.loadLibrary("sqlcipher");val name="play-test-${UUID.randomUUID()}.db"
        val key=ByteArray(32).also {SecureRandom().nextBytes(it)}
        fun open()=Room.databaseBuilder(context,AppDatabase::class.java,name).openHelperFactory(SupportOpenHelperFactory(key.copyOf())).build()
        var db=open();val scope=CoroutineScope(SupervisorJob()+Dispatchers.Default)
        try {
            db.dao().saveConfig(ModelConfigData("cfg","test","openai","https://example.invalid/v1/chat/completions","model",60,"synthetic-ref"))
            val rr=RoleRepository(db)
            val roles=listOf("A","B").map {id->rr.saveRole(null,"同名",(0..9).joinToString {"PLAY_MARK_${id}_$it"},"","cfg")}
            val chats=roles.map {rr.createChat(it.scope(),"cfg")}
            var repo=PlayRepository(db)
            val approvalRow=repo.create(roles[0].scope(),chats[0].id,"费用确认",GomokuState.start(PlaySide.ROLE))
            val approved=repo.preview(approvalRow.scope(),approvalRow.version)
            val oldConfig=requireNotNull(db.dao().config("cfg"))
            db.dao().saveConfig(oldConfig.copy(endpoint="https://changed.invalid/v1/chat/completions",version=2))
            try {repo.prepare(approvalRow.scope(),approvalRow.version,"rejected-approval",approved);fail()}
            catch(e:ChatFailure){assertEquals("PLAY_CHANGED",e.code)}
            assertEquals(approvalRow,repo.timeline(approvalRow.scope()).activity)
            assertTrue(repo.timeline(approvalRow.scope()).requests.isEmpty())
            assertEquals(0,repo.observeView(approvalRow.scope()).first()!!.requestCount)
            db.dao().saveConfig(oldConfig);repo.delete(approvalRow.scope(),approvalRow.version)
            repeat(50){n->
                val mine=n%2;val other=1-mine;val role=roles[mine];val chat=chats[mine]
                val row=repo.create(role.scope(),chat.id,"私密棋局",GomokuState.start(PlaySide.ROLE))
                val f=repo.prepare(row.scope(),row.version,"request-$n")
                val text=f.snapshot.turns.joinToString {it.content};val letter=if(mine==0)"A"else"B";val foreign=if(mine==0)"B"else"A"
                repeat(10){assertTrue(text.contains("PLAY_MARK_${letter}_$it"));assertFalse(text.contains("PLAY_MARK_${foreign}_$it"))}
                assertNull(db.play().session(roles[other].id,roles[other].spaceId,chats[other].id,row.id))
                repo.update(f,PlayProgress(MessageState.WAITING))
                val json="""{"schemaVersion":1,"action":"place","row":7,"column":7,"comment":"PLAY_MARK_${letter}_${n%10}"}"""
                val progress=PlayProgress(MessageState.GENERATING,json,1,2)
                repo.update(f,progress);repo.complete(f,PlayGenerations.decode(f,json),progress.copy(state=MessageState.COMPLETE))
                val saved=repo.timeline(row.scope());assertEquals(json,saved.requests.single().content)
                assertEquals(PlaySide.ROLE,(saved.activity.value().state as GomokuState).cell(7,7))
                try {repo.update(f,progress.copy(text=json+"late",elapsedMs=3));fail()}catch(_:ChatFailure){}
                if(n<48)repo.delete(row.scope(),saved.activity.version)
            }
            repeat(20){n->
                val row=repo.create(roles[0].scope(),chats[0].id,"取消测试",GomokuState.start(PlaySide.ROLE))
                val began=CompletableDeferred<Unit>()
                val backend=object:AIService {
                    override fun sendMessage(request:RequestSnapshot,key:String):Flow<String> = error("unused")
                    override fun sendEvents(request:RequestSnapshot,key:String)=flow {
                        emit(ChatStreamEvent.Text("{\"schemaVersion\":1,"));began.complete(Unit)
                        try {delay(500);emit(ChatStreamEvent.RoundEnd)}finally {withContext(NonCancellable){delay(10)}}
                    }
                }
                val coordinator=PlayCoordinator(repo,backend,object:Credentials{override fun read(ref:String,endpoint:String)="synthetic"},scope,{true},{0})
                assertTrue(coordinator.start(row.scope(),row.version));began.await()
                coordinator.cancel()
                val cancelled=repo.timeline(row.scope());assertEquals("INTERRUPTED",cancelled.activity.status)
                assertEquals(MessageState.CANCELLED,cancelled.requests.single().state);assertEquals(row.stateJson,cancelled.activity.stateJson)
                val resumed=repo.resume(row.scope(),cancelled.activity.version)
                val retry=repo.prepare(row.scope(),resumed.version,"retry-$n")
                repo.update(retry,PlayProgress(MessageState.WAITING))
                repo.finishFailed(retry,PlayProgress(MessageState.CANCELLED),true)
                coordinator.mutateActivity(row.scope()){repo.delete(row.scope(),repo.timeline(row.scope()).activity.version)}
                try {repo.update(retry,PlayProgress(MessageState.WAITING));fail()}catch(_:ChatFailure){}
                assertFalse(ModelGenerationGate.busy())
            }
            val story=repo.create(roles[0].scope(),chats[0].id,"剧情",JourneyState.start(JourneyKind.CITY,"虚构的约会","我","角色",
                listOf(JourneyPlace("海边","正在看海"),JourneyPlace("书店","LATER-SCENE"))))
            val acting=repo.userAction(story.scope(),story.version,PlayAction.Narrate(PlaySide.USER,"ENCRYPTED-STORY-MARKER"))
            val pending=repo.prepare(acting.scope(),acting.version,"restart-request")
            repo.update(pending,PlayProgress(MessageState.WAITING));repo.update(pending,PlayProgress(MessageState.GENERATING,"{\"schemaVersion\":1",firstTokenMs=1,elapsedMs=2))
            db.close();db=open();repo=PlayRepository(db);db.recoverInterruptedRequests()
            val recovered=repo.timeline(story.scope());assertEquals("INTERRUPTED",recovered.activity.status)
            assertEquals("INTERRUPTED",recovered.requests.single().errorCode);assertEquals(acting.stateJson,recovered.activity.stateJson)
            val backup=BackupRepository(db,CredentialVault(context));val snapshot=backup.snapshot(false,{})
            val restored=backup.restore(snapshot,{})
            assertEquals(3,restored.playSessions);assertEquals(3,restored.playRequests)
            RoleRepository(db).deleteRole(roles[0].scope())
            assertNull(db.play().session(story.roleId,story.spaceId,story.chatId,story.id))
            assertNotNull(db.dao().config("cfg"))
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").close()
            for(suffix in listOf("","-wal","-shm")) {
                val file=java.io.File(context.getDatabasePath(name).path+suffix)
                if(file.exists())assertFalse(file.readBytes().toString(Charsets.ISO_8859_1).contains("ENCRYPTED-STORY-MARKER"))
            }
        }finally {scope.cancel();db.close();context.deleteDatabase(name);key.fill(0)}
    }
}
