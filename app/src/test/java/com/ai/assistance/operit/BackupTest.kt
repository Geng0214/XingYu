package com.ai.assistance.operit

import com.ai.assistance.operit.data.backup.*
import com.ai.assistance.operit.api.chat.*
import com.ai.assistance.operit.data.model.*
import com.ai.assistance.operit.data.db.*
import com.ai.assistance.operit.data.model.ChatFailure
import com.ai.assistance.operit.data.model.MessageImages
import com.ai.assistance.operit.data.model.TextMaterials
import com.ai.assistance.operit.data.model.StickerAssets
import com.ai.assistance.operit.data.model.ImageGenerations
import com.ai.assistance.operit.data.model.ReadingChapter
import com.ai.assistance.operit.data.model.ReadingDocument
import com.ai.assistance.operit.data.model.ReadingDocuments
import com.ai.assistance.operit.data.model.Diaries
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import com.ai.assistance.operit.core.tools.packTool.ToolPkgCodec
import com.ai.assistance.operit.data.db.ToolPkgAddress

class BackupTest {
    @Test fun importingOlderPrivateChatsKeepsTheirMessagesInOneContactThread() {
        val base=fixture(false)
        val older=listOf(
            row("chats","chat-a-older","role-a","model","旧聊天",5L,"旧开场白",0L),
            row("messages","user-a-older","chat-a-older","req-a-older","user","旧消息", "COMPLETE",6L,"",-1L,-1L,"paragraph",0L),
            row("messages","reply-a-older","chat-a-older","req-a-older","assistant","旧回复", "COMPLETE",7L,"",1L,2L,"paragraph",0L)
        )
        val source=base.copy(tables=base.tables.mapIndexed { index, rows ->
            rows+older.filter { it.table==BackupSchema.tables[index] }
        })
        BackupValidation.validate(source)
        val imported=BackupRemap.create(source,mapOf("key-ref" to "restored-ref"),2000)
        BackupValidation.validate(imported)
        val contacts=imported.rows("chats")
        assertEquals(2,contacts.size)
        val restoredRole=imported.rows("roles").single {it.text("prompt")=="A 的人设"}.text("id")
        val privateChat=contacts.single {it.text("roleId")==restoredRole}.text("id")
        val texts=imported.rows("messages").filter {it.text("chatId")==privateChat}.map {it.text("content") }
        assertTrue(texts.containsAll(listOf("A私密标记中文😀","A 已收到部分答案","旧消息","旧回复","旧开场白")))
        assertEquals(5,texts.size)
    }

    private fun legacyPlay(bytes:ByteArray,version:Int):ByteArray =
        encodeLegacy(BackupFormat.decode(bytes),BackupSchema.tablesV24,version,29)
    private fun stored(name:String,value:Any):BackupRow {
        val table=BackupSchema.tables.single {it.name==name}
        return BackupRow(table,table.columns.map {c->
            val field=value.javaClass.getDeclaredField(c.name).apply {isAccessible=true}
            when(val v=field.get(value)){is MessageState->v.name;is Int->v.toLong();is Boolean->if(v)1L else 0L;else->v}
        })
    }
    private fun playFixture(journeys:Boolean=false,tables:Boolean=false,chess:Boolean=false,adventure:Boolean=false,outfits:Boolean=false):BackupSnapshot {
        val base=fixture(false)
        val cfg=ModelConfigData("model","connection","openai","https://example.invalid/v1/chat/completions","model-x",60,"key-ref")
        fun prepare(owner:String,state:PlayState):PreparedPlayTurn {
            val role=RoleEntity("role-$owner","同名","当前角色",spaceId="space-$owner")
            val chat=ChatEntity("chat-$owner",role.id,cfg.id,"chat")
            // Fixtures may already contain the first user turn; inject at the format boundary and then validate it.
            val dressed=if(outfits&&(state is DateStoryState||state is JourneyState))PlayStates.decode(org.json.JSONObject(state.encode())
                .put("schemaVersion",6).put("outfits",PlayOutfits(if(owner=="a")HomeItem.SUN_HAT else HomeItem.GARDEN_SUIT,HomeItem.APRON).encode()).toString())else state
            val s=PlaySession(PlayAccessScope(role.scope(),chat.id,"play-$owner"),"一起玩",dressed)
            return PlayGenerations.prepare(s,s.scope,s.version,role,chat,cfg,"play-request-$owner")
        }
        val places=listOf(JourneyPlace("海边","正在看海"),JourneyPlace("书店","FUTURE-STORY-PRIVATE"))
        val aState=if(adventure)AdventurePlayState(AdventureGame.start("ADVENTURE-A","同名").apply(AdventureAction.Interact("wood_a")))else if(chess)XiangqiPlayState.start()else if(tables)WordPlayState(WordGuessGame.start(PlaySide.USER,WordPuzzle("物品","WORD-SECRET")))else if(journeys)JourneyState.start(JourneyKind.CITY,"虚构约会","我","同名",places)else DateStoryState.start("海边","虚构约会")
        val a=prepare("a",aState.apply(if(adventure)PlayAction.Adventure(AdventureAction.Say(PlaySide.USER,"一起去冒险🙂"))else if(chess)PlayAction.Chess(PlaySide.USER,XqMove.parse("a3a4"))else if(tables)PlayAction.Word(WordMove.Clue(PlaySide.USER,"PUBLIC CLUE"))else PlayAction.Narrate(PlaySide.USER,"STORY-A-PRIVATE🙂")))
        val answer=if(adventure)"""{"schemaVersion":1,"action":"say","text":"一起把灯带回来🙂"}"""else if(chess)"""{"schemaVersion":1,"action":"move","move":"a6a5","comment":"轮到你了🙂"}"""else if(tables)"""{"schemaVersion":1,"action":"guess","text":"不知道","comment":"再提示一下🙂"}"""else """{"schemaVersion":1,"action":"narrate","text":"和你一起看海🌊"}"""
        val q=PlayRequestRow.from(a.frozen,20).progress(PlayProgress(MessageState.WAITING))
            .progress(PlayProgress(MessageState.GENERATING,answer,firstTokenMs=1,elapsedMs=2))
            .progress(PlayProgress(MessageState.COMPLETE,answer,1,3))
        val aRow=PlaySessionRow.from(a.session,20).changed(a.frozen.commit(a.session,PlayGenerations.decode(a.frozen,answer)),21)
        val b=prepare("b",if(adventure)AdventurePlayState(AdventureGame.start("ADVENTURE-B","同名").apply(AdventureAction.Say(PlaySide.USER,"你好")))else if(chess)XiangqiPlayState.start(XqColor.BLACK)else if(tables)CardPlayState(FourColorGame.start(PlaySide.ROLE))else if(journeys)JourneyState.start(JourneyKind.SECRET,"两人的约定","我","同名",places).apply(PlayAction.Narrate(PlaySide.USER,"STORY-B-PRIVATE"))else MemoryPairsState.start(4,PlaySide.ROLE))
        val bRow=PlaySessionRow.from(b.session,20)
        val pending=PlayRequestRow.from(b.frozen,20).progress(PlayProgress(MessageState.WAITING))
            .progress(PlayProgress(MessageState.GENERATING,"{\"schemaVersion\":1,",firstTokenMs=1,elapsedMs=2))
        val records=listOf(stored("play_sessions",aRow),stored("play_sessions",bRow),stored("play_requests",q),stored("play_requests",pending))
        return base.copy(tables=base.tables.mapIndexed {i,old->old+records.filter {it.table==BackupSchema.tables[i]}})
    }
    private fun homeFixture():BackupSnapshot {
        val base=fixture(true);val (home,commands)=HomeTestData.garden()
        val records=listOf(stored("home_world",home))+commands.map {stored("home_events",it)}
        return base.copy(tables=base.tables.mapIndexed {i,old->old+records.filter {it.table==BackupSchema.tables[i]}})
    }
    private fun rewardFixture():BackupSnapshot {
        val base=homeFixture();val (home,commands)=HomeTestData.garden()
        val game=AdventureGameTest().completed(true);val proof=HomeAdventureProof.from(game)
        var state=home.value();val ledger=commands.toMutableList()
        HomeAdventureReward.entries.forEach {reward->
            val action=HomeAction.AdventureReward("reward-source",reward,proof,HomeTestData.a)
            val change=state.apply(state.scope,state.version,state.updatedAt,action)
            ledger+=HomeCommandRow("reward-${reward.name}",home.id,state.version,HomeActionCodec.encode(action),change.description,state.updatedAt)
            state=change.state
        }
        val source=PlaySessionRow.from(PlaySession(PlayAccessScope(HomeTestData.a,"chat-a","reward-source"),"奖励来源",AdventurePlayState(game),game.events.size+1L),100)
        return base.copy(tables=base.tables.mapIndexed {i,rows->when(BackupSchema.tables[i].name) {
            "home_world"->listOf(stored("home_world",home.changed(state)))
            "home_events"->ledger.map {stored("home_events",it)}
            "play_sessions"->rows+stored("play_sessions",source)
            else->rows
        }})
    }
    @Test fun rewardBackupRebindsSourcesWithoutMintingItems() {
        val source=rewardFixture();BackupValidation.validate(source)
        val decoded=BackupFormat.decrypt(BackupFormat.encrypt(source,password),password)
        val restored=BackupRemap.create(decoded,mapOf("key-ref" to "reward-key"),2000000);BackupValidation.validate(restored)
        val activity=restored.rows("play_sessions").single()
        val commands=restored.rows("home_events").map(HomeBackup::command).filter {it.action() is HomeAction.AdventureReward}
        assertEquals(2,commands.size)
        commands.forEach {assertEquals(activity.text("id"),(it.action() as HomeAction.AdventureReward).sourceId)}
        val state=HomeBackup.home(restored.rows("home_world").single()).value()
        assertEquals(1,state.inventory[HomeItem.HARBOR_BADGE]);assertEquals(1,state.inventory[HomeItem.SEAGULL_PIN])
    }
    @Test fun sharedWorldBackupKeepsOneWorldAndRebindsEveryResidentAndEvent() {
        val source=homeFixture();BackupValidation.validate(source)
        val raw=BackupFormat.encode(source);assertEquals(33,ByteBuffer.wrap(raw).int);assertEquals(35,ByteBuffer.wrap(raw,4,4).int)
        val decoded=BackupFormat.decrypt(BackupFormat.encrypt(source,password),password)
        val restored=BackupRemap.create(decoded,mapOf("key-ref" to "restored"),2000000);BackupValidation.validate(restored)
        assertEquals(1,restored.summary().homes)
        val old=HomeBackup.home(source.rows("home_world").single()).value()
        val home=HomeBackup.home(restored.rows("home_world").single()).value()
        assertEquals(HomeScope(),home.scope);assertEquals(old.inventory,home.inventory);assertEquals(old.plots,home.plots)
        assertEquals(old.player,home.player);assertEquals(old.life.coins,home.life.coins);assertEquals(old.userOutfit,home.userOutfit)
        assertEquals(restored.rows("roles").map {it.text("id")}.toSet(),home.residents.keys)
        assertTrue(home.residents.keys.none {it in old.residents})
        assertEquals(setOf(HomeItem.APRON),home.residents.values.mapNotNull {it.outfit}.toSet())
    }
    @Test fun deletingAResidentKeepsReplayableHistoryWithoutRestoringThatRole() {
        val (original,commands)=HomeTestData.garden()
        val before=original.value();val leave=HomeAction.Leave(HomeTestData.a.roleId)
        val change=before.apply(before.scope,before.version,before.updatedAt,leave)
        val last=HomeCommandRow("resident-leaves",original.id,before.version,HomeActionCodec.encode(leave),change.description,before.updatedAt)
        val records=listOf(stored("roles",RoleEntity("role-b","居民 B","陪伴角色",spaceId="space-b")),
            stored("home_world",original.changed(change.state)))+(commands+last).map {stored("home_events",it)}
        val snapshot=BackupSnapshot(before.updatedAt,BackupSchema.tables.map {table->records.filter {it.table==table}},false,emptyList())
        BackupValidation.validate(snapshot)
        val restored=BackupRemap.create(BackupFormat.decrypt(BackupFormat.encrypt(snapshot,password),password),emptyMap(),before.updatedAt)
        BackupValidation.validate(restored)
        val row=HomeBackup.home(restored.rows("home_world").single())
        val events=restored.rows("home_events").map(HomeBackup::command)
        HomeStorageValidation.validate(row,events)
        val departed=(events.last().action() as HomeAction.Leave).roleId
        assertTrue(departed in HomeState.decode(row.initialJson).residents)
        assertFalse(departed in row.value().residents)
        assertEquals(setOf(restored.rows("roles").single().text("id")),row.value().residents.keys)
        assertEquals(before.inventory,row.value().inventory);assertEquals(before.plots,row.value().plots)
    }
    @Test fun sharedWorldBackupRejectsForgedStockAndMissingHistory() {
        val s=homeFixture();val r=s.rows("home_world").single();val j=org.json.JSONObject(r.text("stateJson"))
        j.getJSONObject("inventory").put("CARROT",500)
        rejected("BACKUP_FORMAT"){BackupValidation.validate(change(s,"home_world",0,mapOf("stateJson" to j.toString())))}
        rejected("BACKUP_FORMAT"){BackupValidation.validate(change(s,"home_events",0,mapOf("homeId" to "another-world")))}
        rejected("BACKUP_FORMAT"){BackupValidation.validate(s.copy(tables=s.tables.mapIndexed {i,rows->if(BackupSchema.tables[i].name=="home_events")rows.drop(1)else rows}))}
    }
    @Test fun legacyArchiveLoadsOtherDataWithoutTheUnpublishedHomeRuntime() {
        val source=fixture(false)
        val old=encodeLegacy(source,BackupSchema.tablesV31,31,33)
        val restored=BackupFormat.decode(old);BackupValidation.validate(restored)
        assertTrue(restored.rows("home_world").isEmpty());assertTrue(restored.rows("home_events").isEmpty())
        assertEquals(source.rows("roles").map {it.values},restored.rows("roles").map {it.values})
    }
    @Test fun frozenOutfitsRemainValueSnapshotsWithoutAWorld() {
        val source=playFixture(journeys=true,outfits=true);BackupValidation.validate(source)
        val restored=BackupRemap.create(BackupFormat.decrypt(BackupFormat.encrypt(source,password),password),mapOf("key-ref" to "outfit-key"),2000000)
        BackupValidation.validate(restored);assertTrue(restored.rows("home_world").isEmpty())
        assertEquals(source.rows("play_sessions").map {it.text("stateJson")},restored.rows("play_sessions").map {it.text("stateJson")})
    }
    @Test fun actualV28NonemptyAdventureUpgradesWithoutInventingAWorld() {
        val source=playFixture(adventure=true);val upgraded=BackupFormat.decode(legacyPlay(BackupFormat.encode(source),28))
        BackupValidation.validate(upgraded);assertTrue(upgraded.rows("home_world").isEmpty())
        assertEquals(source.rows("play_sessions").map {it.values},upgraded.rows("play_sessions").map {it.values})
    }
    @Test fun encryptedPlayBackupPreservesStoriesDeckUsageAndRemapsEveryIdentityWithoutResuming() {
        val source=playFixture();BackupValidation.validate(source)
        val encoded=BackupFormat.encrypt(source,password)
        assertFalse(encoded.toString(Charsets.ISO_8859_1).contains("STORY-A-PRIVATE"))
        val decoded=BackupFormat.decrypt(encoded,password);BackupValidation.validate(decoded)
        val originalDeck=decoded.rows("play_sessions")[1].text("stateJson")
        val ids=mutableSetOf<String>()
        repeat(50){n->
            val restored=BackupRemap.create(decoded,mapOf("key-ref" to "new-ref-$n"),2000);BackupValidation.validate(restored)
            assertEquals(2,restored.summary().playSessions);assertEquals(2,restored.summary().playRequests)
            val sessions=restored.rows("play_sessions");val requests=restored.rows("play_requests")
            assertTrue(ids.add(sessions[0].text("id")));assertTrue(ids.add(sessions[1].text("id")))
            requests.forEachIndexed {i,r->
                assertEquals(sessions[i].text("id"),r.text("activityId"));assertEquals(sessions[i].text("roleId"),r.text("roleId"))
                assertEquals(sessions[i].text("spaceId"),r.text("spaceId"));assertEquals(sessions[i].text("chatId"),r.text("chatId"))
                assertNotEquals(decoded.rows("play_requests")[i].text("requestId"),r.text("requestId"))
                assertNotEquals(decoded.rows("play_requests")[i].text("assistantId"),r.text("assistantId"))
            }
            assertEquals(decoded.rows("play_sessions")[0].text("stateJson"),sessions[0].text("stateJson"))
            assertEquals(originalDeck,sessions[1].text("stateJson"));assertNull(sessions[1]["requestId"])
            assertEquals("INTERRUPTED",sessions[1].text("status"));assertEquals("FAILED",requests[1].text("state"))
            assertEquals("INTERRUPTED",requests[1].text("errorCode"));assertEquals(decoded.rows("play_requests")[1].text("content"),requests[1].text("content"))
        }
    }
    @Test fun v25JourneysRoundTripAndRemapWithNoPlaintextOrImplicitContinuation() {
        val source=playFixture(true);BackupValidation.validate(source)
        val plain=BackupFormat.encode(source);assertEquals(33,ByteBuffer.wrap(plain).int)
        val previous=legacyPlay(plain,25)
        assertArrayEquals(plain,BackupFormat.encode(BackupFormat.decode(previous)))
        val encrypted=BackupFormat.encrypt(source,password)
        assertFalse(encrypted.toString(Charsets.ISO_8859_1).contains("FUTURE-STORY-PRIVATE"))
        val decoded=BackupFormat.decrypt(encrypted,password);BackupValidation.validate(decoded)
        repeat(10) {n->
            val restored=BackupRemap.create(decoded,mapOf("key-ref" to "restored-$n"),2000);BackupValidation.validate(restored)
            restored.rows("play_sessions").forEachIndexed {i,row->
                assertEquals(source.rows("play_sessions")[i].text("stateJson"),row.text("stateJson"))
                assertNotEquals(source.rows("play_sessions")[i].text("id"),row.text("id"))
                assertTrue(PlayStates.decode(row.text("stateJson")) is JourneyState)
            }
            assertEquals("FAILED",restored.rows("play_requests")[1].text("state"))
            assertEquals("INTERRUPTED",restored.rows("play_sessions")[1].text("status"))
        }
        val disguised=legacyPlay(plain,24)
        rejected("BACKUP_FORMAT"){BackupFormat.decode(disguised)}
        // v33 is the current archive with voices, reactions and scheduled messages; use the next version to exercise rejection of future formats.
        val future=plain.copyOf();ByteBuffer.wrap(future).putInt(34)
        rejected("BACKUP_FORMAT"){BackupFormat.decode(future)}
    }
    @Test fun actualV24PlayPayloadStillReadsWithoutChangingIdsOrData() {
        val source=playFixture();val old=legacyPlay(BackupFormat.encode(source),24)
        ByteBuffer.wrap(old).putInt(24).putInt(29)
        val decoded=BackupFormat.decode(old);BackupValidation.validate(decoded)
        assertEquals(source.rows("play_sessions").map {it.values},decoded.rows("play_sessions").map {it.values})
        assertEquals(source.rows("play_requests").map {it.values},decoded.rows("play_requests").map {it.values})
        assertEquals(source.rows("messages").map {it.values},decoded.rows("messages").map {it.values})
    }
    @Test fun v26TableGamesPreserveSecretsAndRemapInterruptedRequestsWhileOlderHeadersRejectThem() {
        val source=playFixture(tables=true);BackupValidation.validate(source)
        val raw=BackupFormat.encode(source);assertEquals(33,ByteBuffer.wrap(raw).int)
        val old=legacyPlay(raw,26)
        assertArrayEquals(raw,BackupFormat.encode(BackupFormat.decode(old)))
        val encrypted=BackupFormat.encrypt(source,password)
        assertFalse(encrypted.toString(Charsets.ISO_8859_1).contains("WORD-SECRET"))
        val decoded=BackupFormat.decrypt(encrypted,password)
        repeat(10) {n->
            val restored=BackupRemap.create(decoded,mapOf("key-ref" to "restored-$n"),2000);BackupValidation.validate(restored)
            restored.rows("play_sessions").forEachIndexed {i,row->
                assertEquals(source.rows("play_sessions")[i].text("stateJson"),row.text("stateJson"))
                assertNotEquals(source.rows("play_sessions")[i].text("id"),row.text("id"))
                assertNull(row["requestId"])
            }
            assertEquals("INTERRUPTED",restored.rows("play_sessions")[1].text("status"))
            assertEquals("FAILED",restored.rows("play_requests")[1].text("state"))
            assertFalse(restored.rows("play_requests")[0].text("contextJson").contains("WORD-SECRET"))
        }
        listOf(24,25,30).forEach {version->val wrong=legacyPlay(raw,version);rejected("BACKUP_FORMAT"){BackupFormat.decode(wrong)}}
    }
    @Test fun v27XiangqiKeepsSidesMovesAndInterruptedRequestsAcrossTwentyAppendedRestores() {
        val source=playFixture(chess=true);BackupValidation.validate(source)
        val raw=BackupFormat.encode(source);assertEquals(33,ByteBuffer.wrap(raw).int)
        val previous=legacyPlay(raw,27)
        assertArrayEquals(raw,BackupFormat.encode(BackupFormat.decode(previous)))
        val decoded=BackupFormat.decrypt(BackupFormat.encrypt(source,password),password)
        repeat(20){n->
            val restored=BackupRemap.create(decoded,mapOf("key-ref" to "restored-$n"),2000);BackupValidation.validate(restored)
            restored.rows("play_sessions").forEachIndexed {i,row->
                val game=PlayStates.decode(row.text("stateJson")) as XiangqiPlayState
                assertEquals(if(i==0)XqColor.RED else XqColor.BLACK,game.userColor)
                assertEquals(if(i==0)2 else 0,game.game.moves.size)
                assertEquals(source.rows("play_sessions")[i].text("stateJson"),row.text("stateJson"))
                assertNotEquals(source.rows("play_sessions")[i].text("id"),row.text("id"))
            }
            assertEquals("INTERRUPTED",restored.rows("play_sessions")[1].text("status"))
            assertEquals("FAILED",restored.rows("play_requests")[1].text("state"))
        }
        listOf(24,25,26,30).forEach {version->val wrong=legacyPlay(raw,version);rejected("BACKUP_FORMAT"){BackupFormat.decode(wrong)}}
        val changed=org.json.JSONObject(source.rows("play_sessions")[0].text("stateJson")).put("userColor","BLACK").toString()
        rejected("BACKUP_FORMAT"){BackupValidation.validate(change(source,"play_sessions",0,mapOf("stateJson" to changed)))}
    }
    @Test fun v28AdventurePreservesInventoryAndDialogueAcrossTwentyIndependentRestores() {
        val source=playFixture(adventure=true);BackupValidation.validate(source)
        val raw=BackupFormat.encode(source);assertEquals(33,ByteBuffer.wrap(raw).int)
        val encrypted=BackupFormat.encrypt(source,password);assertFalse(encrypted.toString(Charsets.ISO_8859_1).contains("ADVENTURE-A"))
        val decoded=BackupFormat.decrypt(encrypted,password);val ids=mutableSetOf<String>()
        repeat(20){n->
            val restored=BackupRemap.create(decoded,mapOf("key-ref" to "new-$n"),2000);BackupValidation.validate(restored)
            restored.rows("play_sessions").forEachIndexed {i,row->
                assertTrue(ids.add(row.text("id")));assertEquals(source.rows("play_sessions")[i].text("stateJson"),row.text("stateJson"))
                val game=(PlayStates.decode(row.text("stateJson")) as AdventurePlayState).game
                assertEquals(if(i==0)mapOf(AdventureItem.PLANK to 1)else emptyMap(),game.inventory)
                assertEquals(if(i==0)1 else 0,game.roleActionCount)
            }
            assertEquals("INTERRUPTED",restored.rows("play_sessions")[1].text("status"))
            assertEquals("INTERRUPTED",restored.rows("play_requests")[1].text("errorCode"))
        }
        listOf(24,25,26,27,30).forEach {version->val wrong=legacyPlay(raw,version);rejected("BACKUP_FORMAT"){BackupFormat.decode(wrong)}}
    }
    @Test fun playBackupRejectsForeignScopesMissingRequestHistoryHiddenDeckLeaksAndInventedUsage() {
        val s=playFixture()
        val bad=listOf(
            Triple("play_sessions",0,mapOf("spaceId" to "space-b")),Triple("play_sessions",0,mapOf("chatId" to "chat-b")),
            Triple("play_requests",0,mapOf("activityId" to "play-b")),Triple("play_requests",1,mapOf("requestId" to "missing-token")),
            Triple("play_requests",0,mapOf("activityVersion" to 999L)),Triple("play_sessions",1,mapOf("version" to Long.MAX_VALUE)),
            Triple("play_requests",0,mapOf("content" to """{"schemaVersion":1,"action":"narrate","text":"Invented story"}""")),
            Triple("play_requests",1,mapOf("contextJson" to s.rows("play_requests")[1].text("contextJson").replace("legalIndices","deck"))))
        bad.forEach {(table,index,values)->rejected("BACKUP_FORMAT"){BackupValidation.validate(change(s,table,index,values))}}
        val missing=s.copy(tables=s.tables.mapIndexed {i,rows->if(BackupSchema.tables[i].name=="play_requests")rows.drop(1)else rows})
        rejected("BACKUP_FORMAT"){BackupValidation.validate(missing)}
        val context=org.json.JSONArray(s.rows("play_requests")[0].text("contextJson"))
        val nested=context.getJSONObject(1).getString("content")
        context.getJSONObject(1).put("content",nested.replaceFirst("{","{\"participant\":{},"))
        rejected("BACKUP_FORMAT"){BackupValidation.validate(change(s,"play_requests",0,mapOf("contextJson" to context.toString())))}
        context.getJSONObject(1).put("content","[".repeat(17)+"0"+"]".repeat(17))
        rejected("BACKUP_FORMAT"){BackupValidation.validate(change(s,"play_requests",0,mapOf("contextJson" to context.toString())))}
        val forged=s.copy(tables=s.tables.mapIndexed {i,rows->if(BackupSchema.tables[i].name=="play_requests")rows+rows[0].changing(mapOf("requestId" to "duplicate-action","assistantId" to "duplicate-assistant","activityVersion" to 1L))else rows})
        rejected("BACKUP_FORMAT"){BackupValidation.validate(forged)}
    }
    @Test fun v23NonemptyGroupsAndPrivateHistoryUpgradeToEmptyPlayTables() {
        val source=groupFixture()
        val old=encodeLegacy(source,BackupSchema.tablesV23,23,28)
        val decoded=BackupFormat.decode(old);BackupValidation.validate(decoded)
        assertEquals(source.summary(),decoded.summary())
        assertEquals(source.rows("group_requests").single().values,decoded.rows("group_requests").single().values)
        assertTrue(decoded.rows("play_sessions").isEmpty());assertTrue(decoded.rows("play_requests").isEmpty())
        assertEquals(source.rows("messages")[0].text("content"),decoded.rows("messages")[0].text("content"))
    }
    private fun groupFixture():BackupSnapshot {
        val base=fixture(false)
        val records=listOf(
            row("group_rooms","group","group-space","GROUP-PRIVATE-TITLE","我",2L),
            row("group_members","member-a","group","group-space","role-a","space-a","同名","model"),
            row("group_members","member-b","group","group-space","role-b","space-b","同名","model"),
            row("group_requests","g-request","group","group-space",2L,"member-a","role-a","space-a",1L,"model",1L,"openai","model-x","gu","ga",22L,"PENDING",""),
            row("group_messages","gu","group","group-space","g-request","user",null,"我","GROUP-PRIVATE🙂","COMPLETE",21L,"",-1L,-1L),
            row("group_messages","ga","group","group-space","g-request","assistant","member-a","同名","GROUP-REPLY","GENERATING",22L,"",5L,10L))
        return base.copy(tables=base.tables.mapIndexed {i,old->old+records.filter {it.table==BackupSchema.tables[i]}})
    }
    @Test fun encryptedGroupsRemapEveryOwnershipDomainAndInterruptPendingRequests() {
        val source=groupFixture();BackupValidation.validate(source)
        val bytes=BackupFormat.encrypt(source,password)
        assertFalse(bytes.toString(Charsets.ISO_8859_1).contains("GROUP-PRIVATE"))
        val decoded=BackupFormat.decrypt(bytes,password);BackupValidation.validate(decoded)
        val identities=mutableSetOf<String>()
        repeat(50) {
            val restored=BackupRemap.create(decoded,mapOf("key-ref" to "new-key-$it"),2000)
            BackupValidation.validate(restored)
            val room=restored.rows("group_rooms").single();val request=restored.rows("group_requests").single()
            assertTrue(identities.add(room.text("groupId")));assertNotEquals("group-space",room.text("spaceId"))
            assertEquals(room.text("groupId"),request.text("groupId"));assertEquals(room.text("spaceId"),request.text("spaceId"))
            assertEquals(restored.rows("roles")[0].text("spaceId"),request.text("roleSpaceId"))
            assertNotEquals(request.text("spaceId"),request.text("roleSpaceId"))
            val answer=restored.rows("group_messages").single {r->r.text("kind")=="assistant"}
            assertEquals(request.text("assistantId"),answer.text("id"));assertEquals(request.text("participantId"),answer.text("participantId"))
            assertEquals("FAILED",request.text("terminalState"));assertEquals("INTERRUPTED",answer.text("errorCode"));assertEquals("GROUP-REPLY",answer.text("content"))
            assertEquals(1,restored.summary().groups);assertEquals(2,restored.summary().groupMessages)
        }
    }
    @Test fun groupBackupRejectsForeignOwnersAndMalformedRequests() {
        val source=groupFixture()
        val corruptions=listOf(
            Triple("group_members",0,mapOf("roleSpaceId" to "space-b")),Triple("group_messages",0,mapOf("spaceId" to "space-a")),
            Triple("group_requests",0,mapOf("groupVersion" to 3L)),Triple("group_requests",0,mapOf("participantId" to "member-b")),
            Triple("group_requests",0,mapOf("userId" to "user-a")),Triple("group_messages",1,mapOf("requestId" to "req-a")))
        corruptions.forEach {(table,index,values)->rejected("BACKUP_FORMAT"){BackupValidation.validate(change(source,table,index,values))}}
    }
    @Test fun deletedGroupMemberHistoryRetainsIndependentRemappedIdentity() {
        var source=change(groupFixture(),"group_requests",0,mapOf("terminalState" to "FAILED","errorCode" to "GROUP_CHANGED","roleId" to "deleted-role","roleSpaceId" to "deleted-space"))
        source=change(source,"group_messages",1,mapOf("state" to "FAILED","errorCode" to "GROUP_CHANGED"))
        source=source.copy(tables=source.tables.mapIndexed {i,rows->if(BackupSchema.tables[i].name=="group_members")rows.drop(1)else rows})
        BackupValidation.validate(source)
        val imported=BackupRemap.create(source,mapOf("key-ref" to "fresh-key"),2000);BackupValidation.validate(imported)
        val request=imported.rows("group_requests").single()
        assertNotEquals("deleted-role",request.text("roleId"));assertNotEquals("member-a",request.text("participantId"))
        assertEquals(request.text("participantId"),imported.rows("group_messages")[1].text("participantId"))
    }
    @Test fun versionTwentyTwoKeepsNonemptyBilibiliRecordsAndCreatesNoGroups() {
        val base=fixture(false)
        val watch=row("bilibili_watches","watch","role-a","space-a","chat-a","legacy-watch","BV1B7411m7LV",2L,1234L,590000L,"legacy-note",null,3L,4L,5L)
        val source=base.copy(tables=base.tables.mapIndexed {i,rows->if(BackupSchema.tables[i].name=="bilibili_watches")listOf(watch)else rows})
        val old=encodeLegacy(source,BackupSchema.tablesV22,22,27)
        val decoded=BackupFormat.decode(old);BackupValidation.validate(decoded)
        assertEquals(source.summary(),decoded.summary());assertEquals(watch.values,decoded.rows("bilibili_watches").single().values)
        assertTrue(decoded.rows("group_rooms").isEmpty())
    }
    @Test fun onlineWatchingBackupPreservesVideoIdentityAndRemapsPrivateOwnersWithoutPlayback() {
        val base=fixture(false)
        val watch=row("bilibili_watches","watch","role-a","space-a","chat-a","WATCH-PRIVATE","BV1B7411m7LV",2L,1234L,590000L,"WATCH-NOTE","play-token",3L,4L,5L)
        val current=base.copy(tables=base.tables.mapIndexed {i,rows->if(BackupSchema.tables[i].name=="bilibili_watches")listOf(watch)else rows})
        BackupValidation.validate(current)
        val encrypted=BackupFormat.encrypt(current,password);assertFalse(encrypted.toString(Charsets.ISO_8859_1).contains("WATCH-PRIVATE"))
        val restored=BackupRemap.create(BackupFormat.decrypt(encrypted,password),mapOf("key-ref" to "fresh"),1000)
        BackupValidation.validate(restored)
        val w=restored.rows("bilibili_watches").single()
        assertNull(w["playbackToken"]);assertNotEquals("watch",w.text("id"));assertNotEquals("role-a",w.text("roleId"))
        assertEquals("BV1B7411m7LV",w.text("videoId"));assertEquals(2L,w.number("part"));assertEquals(1234L,w.number("positionMs"))
        for(changes in listOf(mapOf("spaceId" to "space-b"),mapOf("videoId" to "javascript:alert(1)"),mapOf("part" to 4_294_967_297L),mapOf("positionMs" to 590001L)))
            rejected("BACKUP_FORMAT"){BackupValidation.validate(change(current,"bilibili_watches",0,changes))}
        val v21=encodeLegacy(base,BackupSchema.tablesV21,21,26)
        val legacy=BackupFormat.decode(v21);BackupValidation.validate(legacy)
        assertEquals(base.summary(),legacy.summary());assertTrue(legacy.rows("bilibili_watches").isEmpty())
    }
    @Test fun videoBackupRemapsScopeAndRejectsMalformedDimensionsWithoutRestoringFileAccess() {
        val base=fixture(false)
        val track=row("music_tracks","video","role-a","space-a","VIDEO-PRIVATE","video/mp4",3000L,40_000_000L,"b".repeat(64),"content://private/video",1L,1L,"video",1080L,1920L)
        val session=row("music_sessions","watch","role-a","space-a","chat-a","video","playing",1234L,"VIDEO-NOTE","token",2L,3L,4L)
        val current=base.copy(tables=base.tables.mapIndexed {i,rows->when(BackupSchema.tables[i].name){"music_tracks"->listOf(track);"music_sessions"->listOf(session);else->rows}})
        BackupValidation.validate(current)
        val encrypted=BackupFormat.encrypt(current,password)
        assertFalse(encrypted.toString(Charsets.ISO_8859_1).contains("VIDEO-PRIVATE"))
        val restored=BackupRemap.create(BackupFormat.decrypt(encrypted,password),mapOf("key-ref" to "fresh"),1000)
        BackupValidation.validate(restored)
        val t=restored.rows("music_tracks").single();val s=restored.rows("music_sessions").single()
        assertEquals("video",t.text("mediaKind"));assertEquals(1920L,t.number("videoHeight"));assertNull(t["sourceUri"])
        assertNotEquals("role-a",t.text("roleId"));assertEquals(t.text("spaceId"),s.text("spaceId"));assertEquals(t.text("id"),s.text("trackId"))
        assertEquals(1234L,s.number("positionMs"));assertEquals("paused",s.text("state"));assertNull(s["playbackToken"])
        for(changes in listOf(mapOf("videoWidth" to null),mapOf("videoHeight" to 0L),mapOf("videoWidth" to 4_294_967_297L),mapOf("mediaKind" to "audio"),mapOf("spaceId" to "space-b")))
            rejected("BACKUP_FORMAT"){BackupValidation.validate(change(current,"music_tracks",0,changes))}
    }
    @Test fun nonemptyVersionTwentyBackupPreservesMusicAndAddsOnlyAudioDefaults() {
        val base=fixture(false)
        val track=row("music_tracks","legacy-track","role-a","space-a","Legacy audio","audio/mpeg",3000L,100L,"a".repeat(64),"content://private/song",7L,11L,"audio",null,null)
        val session=row("music_sessions","legacy-session","role-a","space-a","chat-a","legacy-track","paused",1234L,"Legacy note",null,8L,12L,13L)
        val current=base.copy(tables=base.tables.mapIndexed {i,rows->when(BackupSchema.tables[i].name){"music_tracks"->listOf(track);"music_sessions"->listOf(session);else->rows}})
        val upgraded=BackupFormat.decode(encodeLegacy(current,BackupSchema.tablesV20,20,25));BackupValidation.validate(upgraded)
        assertArrayEquals(BackupFormat.encode(current),BackupFormat.encode(upgraded))
        assertEquals("audio",upgraded.rows("music_tracks").single().text("mediaKind"))
        assertNull(upgraded.rows("music_tracks").single()["videoWidth"])
    }
    @Test fun musicBackupRestoresIdentityAndProgressButNeverExternalUriGrantsOrPlayback() {
        val base=fixture(false)
        val track=row("music_tracks","track","role-a","space-a","MUSIC-PRIVATE","audio/mpeg",3000L,100L,"a".repeat(64),"content://private/song",1L,1L,"audio",null,null)
        val session=row("music_sessions","session","role-a","space-a","chat-a","track","playing",1200L,"喜欢这首歌","run-token",1L,2L,3L)
        val current=base.copy(tables=base.tables.mapIndexed {i,rows->when(BackupSchema.tables[i].name){"music_tracks"->listOf(track);"music_sessions"->listOf(session);else->rows}})
        BackupValidation.validate(current)
        val encrypted=BackupFormat.encrypt(current,password);assertFalse(encrypted.toString(Charsets.ISO_8859_1).contains("MUSIC-PRIVATE"))
        val restored=BackupRemap.create(BackupFormat.decrypt(encrypted,password),mapOf("key-ref" to "fresh"),1000);BackupValidation.validate(restored)
        val t=restored.rows("music_tracks").single();val s=restored.rows("music_sessions").single()
        assertNull(t["sourceUri"]);assertNull(s["playbackToken"]);assertEquals("paused",s.text("state"));assertEquals(1200L,s.number("positionMs"))
        assertNotEquals("track",t.text("id"));assertEquals(t.text("id"),s.text("trackId"));assertEquals(t.text("roleId"),s.text("roleId"))
        for(changes in listOf(mapOf("spaceId" to "other"),mapOf("trackId" to "missing"),mapOf("positionMs" to Long.MAX_VALUE)))rejected("BACKUP_FORMAT"){BackupValidation.validate(change(current,"music_sessions",0,changes))}
        val v19=encodeLegacy(base,BackupSchema.tablesV19,19,24)
        val legacy=BackupFormat.decode(v19);BackupValidation.validate(legacy)
        assertTrue(legacy.rows("music_tracks").isEmpty());assertTrue(legacy.rows("music_sessions").isEmpty());assertEquals(base.summary().messages,legacy.summary().messages)
    }
    @Test fun v19PreservesActivitiesAndRemapsEveryOwnerWhileV18AddsNoActivity() {
        val base=fixture(false)
        val text="共同阅读🙂 PRIVATE-ACTIVITY"
        val book=row("reading_documents","book","role-a","space-a","书","text/plain",text,
            ReadingDocuments.encodeChapters(listOf(ReadingChapter(0,"第一节",0,text.length))),0L,0L,ReadingDocuments.sha256(text),1L,1L,1L)
        val activity=row("shared_activities","activity","role-a","space-a","chat-a","book","reading","paused",0L,0L,"PRIVATE-ACTIVITY-NOTE",2L,1L,2L)
        val current=base.copy(tables=base.tables.mapIndexed {i,rows->when(BackupSchema.tables[i].name){"reading_documents"->listOf(book);"shared_activities"->listOf(activity);else->rows}})
        BackupValidation.validate(current)
        val encrypted=BackupFormat.encrypt(current,password)
        assertFalse(encrypted.toString(Charsets.ISO_8859_1).contains("PRIVATE-ACTIVITY"))
        val decoded=BackupFormat.decrypt(encrypted,password);BackupValidation.validate(decoded)
        val restored=BackupRemap.create(decoded,mapOf("key-ref" to "fresh"),999);BackupValidation.validate(restored)
        val item=restored.rows("shared_activities").single();val document=restored.rows("reading_documents").single()
        assertEquals(1,restored.summary().sharedActivities);assertEquals(document.text("id"),item.text("readingDocumentId"))
        assertNotEquals("activity",item.text("id"));assertNotEquals("chat-a",item.text("chatId"));assertEquals("paused",item.text("state"))
        assertEquals(document.text("roleId"),item.text("roleId"));assertEquals(document.text("spaceId"),item.text("spaceId"))
        for(change in listOf(mapOf("spaceId" to "other"),mapOf("chatId" to "chat-b"),mapOf("readingDocumentId" to "missing"),mapOf("offset" to Long.MAX_VALUE))) {
            rejected("BACKUP_FORMAT"){BackupValidation.validate(change(current,"shared_activities",0,change))}
        }
        // v18 has exactly the same preceding fields, followed directly by the credentials count.
        val withoutActivities=current.copy(tables=current.tables.mapIndexed {i,rows->if(BackupSchema.tables[i].name=="shared_activities")emptyList()else rows})
        val v18=encodeLegacy(withoutActivities,BackupSchema.tablesV18,18,23)
        val legacy=BackupFormat.decode(v18);BackupValidation.validate(legacy)
        assertTrue(legacy.rows("shared_activities").isEmpty());assertEquals(text,legacy.rows("reading_documents").single().text("text"))
    }
    @Test fun v3GoldenPreservesLoreAndCurrentFormatRestoresRawModeAndRelationshipGraph() {
        val old=BackupFormat.decode(javaClass.getResourceAsStream("/backup-v3.bin")!!.use {it.readBytes()});BackupValidation.validate(old)
        assertEquals("v3-world-marker",old.rows("worldbook_entries").single().text("content"))
        assertEquals("legacy persona",old.rows("roles").single().text("userName"))
        assertEquals("character",old.rows("roles").single().text("conversationMode"))
        assertTrue(old.rows("relationship_entries").isEmpty())
        val base=change(fixture(false),"roles",0,mapOf("conversationMode" to "raw"))
        val relationship=row("relationship_entries","relation","role-a","space-a","chat-a","my-name","their-name","friend","RELATIONSHIP-PRIVATE",1L,50L,4L)
        val original=base.copy(tables=base.tables.mapIndexed {i,rows->if(BackupSchema.tables[i].name=="relationship_entries")listOf(relationship)else rows})
        BackupValidation.validate(original)
        val bytes=BackupFormat.encrypt(original,password)
        assertFalse(bytes.toString(Charsets.ISO_8859_1).contains("RELATIONSHIP-PRIVATE"))
        val decoded=BackupFormat.decrypt(bytes,password);BackupValidation.validate(decoded)
        val imported=BackupRemap.create(decoded,mapOf("key-ref" to "new-key"),999);BackupValidation.validate(imported)
        val r=imported.rows("relationship_entries").single()
        assertNotEquals("relation",r.text("id"));assertNotEquals("space-a",r.text("spaceId"));assertNotEquals("chat-a",r.text("chatId"))
        assertEquals("raw",imported.rows("roles").single {it.text("id")==r.text("roleId")}.text("conversationMode"))
        assertEquals("RELATIONSHIP-PRIVATE",r.text("description"));assertEquals(1L,r.number("enabled"));assertEquals(4L,r.number("version"))
        rejected("BACKUP_FORMAT"){BackupValidation.validate(change(original,"relationship_entries",0,mapOf("chatId" to "chat-b")))}
        rejected("BACKUP_FORMAT"){BackupValidation.validate(change(original,"relationship_entries",0,mapOf("spaceId" to "space-b")))}
        rejected("BACKUP_FORMAT"){BackupValidation.validate(change(original,"roles",0,mapOf("conversationMode" to "unknown")))}
    }
    @Test fun v2GoldenRetainsPersonaAndV3WorldbookRemapsAllOwnership() {
        val bytes=javaClass.getResourceAsStream("/backup-v2.bin")!!.use {it.readBytes()}
        val old=BackupFormat.decode(bytes);BackupValidation.validate(old)
        assertEquals("legacy persona",old.rows("roles").single().text("userName"))
        assertEquals("v2-persona-marker",old.rows("roles").single().text("userPersona"))
        assertEquals(1L,old.rows("roles").single().number("personaEnabled"))
        assertTrue(old.rows("worldbook_entries").isEmpty())
        val base=fixture(false)
        val lore=row("worldbook_entries","lore","role-a","space-a","chat-a","城市","worldbook-private-marker","keywords","城市",1L,50L,3L)
        val snapshot=base.copy(tables=base.tables.mapIndexed {i,rows->if(BackupSchema.tables[i].name=="worldbook_entries")listOf(lore)else rows})
        BackupValidation.validate(snapshot)
        val encrypted=BackupFormat.encrypt(snapshot,password)
        assertFalse(encrypted.toString(Charsets.ISO_8859_1).contains("worldbook-private-marker"))
        val decoded=BackupFormat.decrypt(encrypted,password);BackupValidation.validate(decoded)
        val remapped=BackupRemap.create(decoded,mapOf("key-ref" to "new-key-ref"),2000);BackupValidation.validate(remapped)
        val entry=remapped.rows("worldbook_entries").single()
        assertNotEquals("lore",entry.text("id"));assertNotEquals("chat-a",entry.text("chatId"))
        assertEquals("worldbook-private-marker",entry.text("content"))
        assertEquals(1L,entry.number("enabled"));assertEquals(3L,entry.number("version"))
        assertEquals(entry.text("roleId"),remapped.rows("chats").single {it.text("id")==entry.text("chatId")}.text("roleId"))
        rejected("BACKUP_FORMAT"){BackupValidation.validate(change(snapshot,"worldbook_entries",0,mapOf("chatId" to "chat-b")))}
        rejected("BACKUP_FORMAT"){BackupValidation.validate(change(snapshot,"worldbook_entries",0,mapOf("spaceId" to "space-b")))}
    }
    private val password="一起保存这些回忆🔒-long-password".toCharArray()
    private fun row(name:String,vararg values:Any?):BackupRow=BackupRow(BackupSchema.tables.single {it.name==name},values.toList()+when {
        name=="roles"->listOf("","",0L,"character")
        name=="model_configs"->listOf(0L)
        name=="memory_entries"&&values.size==12->List(8){null}+listOf("always",null,null,null)
        name=="memory_entries"&&values.size==20->listOf("always",null,null,null)
        else->emptyList()})
    private fun fixture(keys:Boolean=true):BackupSnapshot {
        val records=listOf(
            row("model_configs","model","connection","openai","https://example.invalid/v1/chat/completions","model-x",60L,"key-ref",1L),
            row("roles","role-a","同名","A 的人设",1L,"space-a","你好🌸","model","short"),
            row("roles","role-b","同名","B 的人设",2L,"space-b","欢迎","model","paragraph"),
            row("chats","chat-a","role-a","model","A 聊天",10L,"最初开场白",1L),
            row("chats","chat-b","role-b","model","B 聊天",11L,"",0L),
            row("messages","user-a","chat-a","req-a","user","A私密标记中文😀", "COMPLETE",11L,"",-1L,-1L,"paragraph",0L),
            row("messages","reply-a","chat-a","req-a","assistant","A 已收到部分答案", "GENERATING",12L,"",5L,10L,"short",1L),
            row("messages","user-b","chat-b","req-b","user","B私密标记", "COMPLETE",13L,"",-1L,-1L,"paragraph",0L),
            row("mcp_extensions","ext","时间","https://example.invalid/mcp",null,10000L,1L,2L),
            row("extension_tools","ext","clock","获取时间","{\"type\":\"object\"}"),
            row("role_tool_grants","role-a","space-a","ext","clock",1L,3L),
            row("request_tool_permissions","req-a","ext","clock","role-a","space-a","chat-a","reply-a",2L,3L,"https://example.invalid/mcp",null,10000L,"获取时间","{\"type\":\"object\"}"),
            row("tool_invocations","req-a","call-a","role-a","space-a","chat-a","reply-a","ext","clock","{}","半条结果","RUNNING","",12L,0L)
        )
        return BackupSnapshot(1234L,BackupSchema.tables.map {t->records.filter {it.table==t}},keys,
            if(keys)listOf(BackupCredential("key-ref","https://example.invalid/v1/chat/completions","synthetic-SECRET-key"))else emptyList())
    }
    /** Historical fixtures project by name so retired fields cannot shift conversation data. */
    private fun encodeLegacy(snapshot:BackupSnapshot,specs:List<BackupTable>,version:Int,schema:Int):ByteArray {
        val bytes=ByteArrayOutputStream();val output=DataOutputStream(bytes)
        fun blob(value:ByteArray){output.writeInt(value.size);output.write(value)}
        output.writeInt(version);output.writeInt(schema);output.writeLong(snapshot.createdAt);output.writeBoolean(snapshot.includesCredentials)
        val byName=snapshot.tables.flatten().groupBy {it.table.name}
        specs.forEach {table->
            val rows=byName[table.name].orEmpty();output.writeInt(rows.size)
            rows.forEach {row->table.columns.forEach {column->
                val value=if(row.table.columns.any {it.name==column.name})row[column.name]else {
                    require(column.name in RequestMetadataMigration.removedColumns.getValue(table.name))
                    when {
                        column.nullable->null
                        column.type==BackupType.INTEGER->0L
                        column.name=="billingJson"->"{}"
                        column.name=="monthKey"->"2026-09"
                        column.name in setOf("priceCurrency","currency","budgetCurrency")->"USD"
                        else->error("Unexpected historical column")
                    }
                }
                output.writeBoolean(value!=null)
                if(value!=null)when(column.type) {
                    BackupType.TEXT->blob((value as String).toByteArray(Charsets.UTF_8))
                    BackupType.INTEGER->output.writeLong(value as Long)
                    BackupType.REAL->output.writeDouble(value as Double)
                    BackupType.BLOB->blob(value as ByteArray)
                }
            }}
        }
        output.writeInt(snapshot.credentials.size)
        snapshot.credentials.forEach {for(value in listOf(it.ref,it.endpoint,it.key))blob(value.toByteArray(Charsets.UTF_8))}
        output.flush();return bytes.toByteArray()
    }
    private fun encodeV4(snapshot:BackupSnapshot)=encodeLegacy(snapshot,BackupSchema.tablesV4,4,9)
    private fun encodeV5(snapshot:BackupSnapshot)=encodeLegacy(snapshot,BackupSchema.tablesV5,5,10)
    private fun encodeV6(snapshot:BackupSnapshot)=encodeLegacy(snapshot,BackupSchema.tablesV6,6,11)
    private fun encodeV7(snapshot:BackupSnapshot)=encodeLegacy(snapshot,BackupSchema.tablesV7,7,12)
    private fun encodeV8(snapshot:BackupSnapshot)=encodeLegacy(snapshot,BackupSchema.tablesV8,8,13)
    private fun encodeV9(snapshot:BackupSnapshot)=encodeLegacy(snapshot,BackupSchema.tablesV9,9,14)
    private fun encodeV10(snapshot:BackupSnapshot)=encodeLegacy(snapshot,BackupSchema.tablesV10,10,15)
    private fun encodeV11(snapshot:BackupSnapshot)=encodeLegacy(snapshot,BackupSchema.tablesV11,11,16)
    private fun encodeV12(snapshot:BackupSnapshot)=encodeLegacy(snapshot,BackupSchema.tablesV12,12,17)
    private fun encodeV13(snapshot:BackupSnapshot)=encodeLegacy(snapshot,BackupSchema.tablesV13,13,18)
    private fun encodeV14(snapshot:BackupSnapshot)=encodeLegacy(snapshot,BackupSchema.tablesV14,14,19)
    private fun encodeV15(snapshot:BackupSnapshot)=encodeLegacy(snapshot,BackupSchema.tablesV15,15,20)
    private fun encodeV16(snapshot:BackupSnapshot)=encodeLegacy(snapshot,BackupSchema.tablesV16,16,21)
    private fun encodeV17(snapshot:BackupSnapshot)=encodeLegacy(snapshot,BackupSchema.tablesV17,17,22)
    private fun change(s:BackupSnapshot,table:String,index:Int,values:Map<String,Any?>)=s.copy(tables=s.tables.map {rows->
        if(rows.firstOrNull()?.table?.name!=table)rows else rows.mapIndexed {i,r->if(i==index)r.changing(values)else r}})
    private fun rejected(code:String,block:()->Unit) {try {block();fail("Expected rejection: $code")}catch(e:ChatFailure){assertEquals(code,e.code)}}
    @Test fun passwordEnvelopeRoundTripKeepsChineseEmojiHistoryAndSecretOnlyInAuthenticatedPayload() {
        val original=fixture();BackupValidation.validate(original)
        val a=BackupFormat.encrypt(original,password);val b=BackupFormat.encrypt(original,password)
        assertFalse(a.contentEquals(b))
        val binary=a.toString(Charsets.ISO_8859_1)
        assertFalse(binary.contains("synthetic-SECRET-key"));assertFalse(binary.contains("example.invalid"))
        val restored=BackupFormat.decrypt(a,password);BackupValidation.validate(restored)
        assertArrayEquals(BackupFormat.encode(original),BackupFormat.encode(restored))
        assertEquals("A私密标记中文😀",restored.rows("messages")[0].text("content"))
    }
    @Test fun wrongPasswordTamperingTruncationAndTrailingCiphertextAreRejected() {
        val bytes=BackupFormat.encrypt(fixture(),password)
        rejected("BACKUP_AUTH"){BackupFormat.decrypt(bytes,"a-wrong-password".toCharArray())}
        for(position in listOf(12,28,40,bytes.lastIndex)) {
            val bad=bytes.copyOf();bad[position]=(bad[position].toInt() xor 1).toByte()
            rejected("BACKUP_AUTH"){BackupFormat.decrypt(bad,password)}
        }
        rejected("BACKUP_AUTH"){BackupFormat.decrypt(bytes.copyOf(bytes.size-1),password)}
        rejected("BACKUP_AUTH"){BackupFormat.decrypt(bytes+byteArrayOf(0),password)}
    }
    @Test fun hostileHeaderCannotSelectWeakKdfOrUnboundedWork() {
        val bytes=BackupFormat.encrypt(fixture(false),password)
        for(iterations in listOf(0,1,599999,Int.MAX_VALUE)) {
            val bad=bytes.copyOf();ByteBuffer.wrap(bad,8,4).putInt(iterations)
            rejected("BACKUP_FORMAT"){BackupFormat.decrypt(bad,password)}
        }
        rejected("BACKUP_FORMAT"){BackupFormat.decrypt(ByteArray(40),password)}
        rejected("BACKUP_PASSWORD"){BackupFormat.checkPassword("short".toCharArray())}
        rejected("BACKUP_PASSWORD"){BackupFormat.checkPassword(CharArray(129){'x'})}
    }
    @Test fun badSchemaCountsUtf8NullsAndTrailingPlaintextNeverReachSql() {
        val bytes=BackupFormat.encode(fixture(false))
        for(offset in listOf(0,4,17)) {
            val bad=bytes.copyOf();ByteBuffer.wrap(bad,offset,4).putInt(Int.MAX_VALUE)
            rejected("BACKUP_FORMAT"){BackupFormat.decode(bad)}
        }
        rejected("BACKUP_FORMAT"){BackupFormat.decode(bytes+byteArrayOf(0))}
        rejected("BACKUP_FORMAT"){BackupFormat.decode(bytes.copyOf(bytes.size/2))}
        val malformed=bytes.copyOf();malformed[26]=0xff.toByte()
        rejected("BACKUP_FORMAT"){BackupFormat.decode(malformed)}
        rejected("BACKUP_FORMAT"){BackupValidation.validate(change(fixture(false),"roles",0,mapOf("spaceId" to "space-b")))}
    }
    @Test fun repeatedAppendCreatesFreshIdentitiesAndNeverMixesFiftyAlternatingRoleContexts() {
        val original=fixture(false);val seen=mutableSetOf<String>()
        repeat(50){round->
            val copy=BackupRemap.create(original,mapOf("key-ref" to "new-$round"),2000L);BackupValidation.validate(copy)
            copy.rows("roles").forEach {assertTrue(seen.add(it.text("id")));assertTrue(seen.add(it.text("spaceId")))}
            val role=copy.rows("roles")[round%2]
            val chats=copy.rows("chats").filter {it.text("roleId")==role.text("id")}.map {it.text("id")}
            val messages=copy.rows("messages").filter {it.text("chatId") in chats}
            val forbidden=if(round%2==0)"B私密标记"else"A私密标记"
            assertTrue(messages.none {forbidden in it.text("content")})
            assertTrue(copy.rows("mcp_extensions").all {it.number("enabled")==0L})
            assertTrue(copy.rows("role_tool_grants").all {it.number("enabled")==0L})
            assertEquals("INTERRUPTED",copy.rows("messages")[1].text("errorCode"))
            assertEquals("A 已收到部分答案",copy.rows("messages")[1].text("content"))
            assertEquals(1L,copy.rows("messages")[1].number("favorite"))
            assertEquals("INTERRUPTED",copy.rows("tool_invocations")[0].text("errorCode"))
        }
        assertEquals("GENERATING",original.rows("messages")[1].text("state"))
    }
    @Test fun crossSpaceReferencesOrCredentialRebindingAreRejectedBeforeRestore() {
        for((table,fields) in listOf(
            "chats" to mapOf("configId" to "unknown"),
            "messages" to mapOf("chatId" to "unknown"),
            "role_tool_grants" to mapOf("spaceId" to "space-b"),
            "request_tool_permissions" to mapOf("assistantId" to "user-b"),
            "tool_invocations" to mapOf("roleId" to "role-b","spaceId" to "space-b"),
            "model_configs" to mapOf("endpoint" to "http://evil.invalid/v1/chat/completions"),
            "mcp_extensions" to mapOf("endpoint" to "file:///private/data")
        ))rejected("BACKUP_FORMAT"){BackupValidation.validate(change(fixture(),table,0,fields))}
        val original=fixture()
        rejected("BACKUP_FORMAT"){BackupValidation.validate(original.copy(credentials=original.credentials.map {it.copy(endpoint="https://evil.invalid")}))}
        rejected("BACKUP_FORMAT"){BackupValidation.validate(original.copy(credentials=original.credentials+original.credentials))}
        rejected("BACKUP_FORMAT"){BackupValidation.validate(original.copy(includesCredentials=false))}
        rejected("BACKUP_FORMAT"){BackupValidation.validate(original.copy(credentials=emptyList()))}
    }
    @Test fun duplicatePrimaryKeyAndRequestAcrossChatsAreRejected() {
        val original=fixture(false)
        val duplicate=original.copy(tables=original.tables.map {if(it.firstOrNull()?.table?.name=="roles")it+it[0]else it})
        rejected("BACKUP_FORMAT"){BackupValidation.validate(duplicate)}
        rejected("BACKUP_FORMAT"){BackupValidation.validate(change(original,"messages",2,mapOf("requestId" to "req-a")))}
    }
    @Test fun sizeLimitIsEnforcedEvenForStreamsWithNoLength() {
        val stream=object:java.io.InputStream(){var remaining=BackupFormat.MAX_BYTES+100
            override fun read():Int=if(remaining-->0)0 else -1
            override fun read(b:ByteArray,off:Int,len:Int):Int{if(remaining<=0)return -1;val n=minOf(remaining,len);b.fill(0,off,off+n);remaining-=n;return n}}
        rejected("BACKUP_LIMIT"){BackupFormat.read(stream)}
        assertArrayEquals(byteArrayOf(1,2,3),BackupFormat.read(ByteArrayInputStream(byteArrayOf(1,2,3))))
    }
    @Test fun toolPkgArchiveAndCatalogAreCheckedWithoutRunningCode() {
        val root=Path.of(System.getProperty("companion.repo"),"app/src/main/assets/toolpkg/time")
        val bundle=ToolPkgCodec.decode(ToolPkgCodec.timeExample {Files.readAllBytes(root.resolve(it))})
        val original=fixture(false)
        val extra=listOf(row("mcp_extensions","local",bundle.name,ToolPkgAddress.fromHash(bundle.sha256),null,10000L,1L,1L),
            row("toolpkg_packages","local",bundle.packageId,bundle.packageVersion,bundle.apiVersion,bundle.sha256,bundle.archive(),1L))+
            bundle.tools.map {row("extension_tools","local",it.name,it.definition.description,it.definition.inputSchema)}
        val full=original.copy(tables=original.tables.mapIndexed {i,rows->rows+extra.filter {it.table==BackupSchema.tables[i]}})
        BackupValidation.validate(full)
        rejected("BACKUP_FORMAT"){BackupValidation.validate(change(full,"toolpkg_packages",0,mapOf("sha256" to "0".repeat(64))))}
        val restored=BackupRemap.create(full,mapOf("key-ref" to "fresh-key"),123L);BackupValidation.validate(restored)
        assertArrayEquals(bundle.archive(),restored.rows("toolpkg_packages")[0]["archive"] as ByteArray)
    }
    @Test fun tablesExactlyMatchExportedLegacyAndCurrentRoomSchemas() {
      for((version,specs) in listOf(6 to BackupSchema.tablesV1,7 to BackupSchema.tablesV2,8 to BackupSchema.tablesV3,9 to BackupSchema.tablesV4,10 to BackupSchema.tablesV5,11 to BackupSchema.tablesV6,12 to BackupSchema.tablesV7,13 to BackupSchema.tablesV8,14 to BackupSchema.tablesV9,15 to BackupSchema.tablesV10,16 to BackupSchema.tablesV11,17 to BackupSchema.tablesV12,18 to BackupSchema.tablesV13,19 to BackupSchema.tablesV14,20 to BackupSchema.tablesV15,21 to BackupSchema.tablesV16,22 to BackupSchema.tablesV17,23 to BackupSchema.tablesV18,24 to BackupSchema.tablesV19,25 to BackupSchema.tablesV20,26 to BackupSchema.tablesV21,27 to BackupSchema.tablesV22,28 to BackupSchema.tablesV23,29 to BackupSchema.tablesV24,30 to BackupSchema.tablesV30,33 to BackupSchema.tablesV31,34 to BackupSchema.tablesV32,35 to BackupSchema.tables)) {
        val schema=org.json.JSONObject(Files.readAllBytes(Path.of(System.getProperty("companion.repo"),"app/schemas/com.ai.assistance.operit.data.db.AppDatabase/$version.json")).toString(Charsets.UTF_8))
        val entities=schema.getJSONObject("database").getJSONArray("entities")
        assertEquals(specs.size,entities.length())
        repeat(entities.length()){i->val entity=entities.getJSONObject(i);val table=specs.single {it.name==entity.getString("tableName")}
            // The logical archive has always omitted the local quote presentation fields introduced in Room 31.
            val allFields=entity.getJSONArray("fields")
            val fields=org.json.JSONArray((0 until allFields.length()).map {allFields.getJSONObject(it)}.filter {
                version<31||entity.getString("tableName")!="messages"||it.getString("columnName") !in setOf("quotedMessageId","quotedSpeaker","quotedPreview")
            })
            assertEquals(table.columns.map {it.name}.toSet(),(0 until fields.length()).map {fields.getJSONObject(it).getString("columnName")}.toSet())
            repeat(fields.length()){j->val field=fields.getJSONObject(j);val column=table.columns.single {it.name==field.getString("columnName")}
                assertEquals(!(field.has("notNull")&&field.getBoolean("notNull")),column.nullable)
                assertEquals(field.getString("affinity"),column.type.name)
            }
        }
      }
    }
    @Test fun versionFourArchivePreservesRolesAndCurrentConnectionFields() {
        val old=BackupFormat.decode(encodeV4(fixture(false)));BackupValidation.validate(old)
        assertEquals("character",old.rows("roles").first().text("conversationMode"))
        assertEquals(0L,old.rows("model_configs").single().number("visionEnabled"))
        assertEquals(fixture(false).rows("model_configs").single().values,old.rows("model_configs").single().values)
    }
    @Test fun versionSixPreservesImagesAndVersionFiveAddsNoInventedVisionData() {
        val original=fixture(false)
        val bytes=byteArrayOf(0xff.toByte(),0xd8.toByte(),7,0xff.toByte(),0xd9.toByte())
        val attachment=row("message_attachments","user-a","chat-a","req-a",MessageImages.MIME_TYPE,24L,18L,MessageImages.sha256(bytes),bytes)
        val withImage=original.copy(tables=original.tables.mapIndexed {i,rows->if(BackupSchema.tables[i].name=="message_attachments")listOf(attachment)else rows})
        BackupValidation.validate(withImage)
        val decoded=BackupFormat.decode(BackupFormat.encode(withImage));BackupValidation.validate(decoded)
        assertArrayEquals(bytes,decoded.rows("message_attachments").single()["data"] as ByteArray);assertEquals(1,decoded.summary().images)
        val imported=BackupRemap.create(decoded,mapOf("key-ref" to "fresh"),1000);BackupValidation.validate(imported)
        assertNotEquals("user-a",imported.rows("message_attachments").single().text("messageId"))
        rejected("BACKUP_FORMAT"){BackupValidation.validate(change(withImage,"message_attachments",0,mapOf("sha256" to "0".repeat(64))))}
        val old=BackupFormat.decode(encodeV5(original));BackupValidation.validate(old)
        assertFalse(old.rows("model_configs").single().number("visionEnabled")==1L);assertTrue(old.rows("message_attachments").isEmpty())
    }
    @Test fun versionSevenPreservesMaterialsAndVersionSixAddsNoInventedContent() {
        val original=fixture(false);val text="参考资料中文🙂"
        val material=row("message_materials","user-a","chat-a","req-a",TextMaterials.KIND_LINK,"安全页面","https://example.invalid/article",TextMaterials.MIME_HTML,
            text,text.length.toLong(),0L,TextMaterials.sha256(text))
        val current=original.copy(tables=original.tables.mapIndexed {i,rows->if(BackupSchema.tables[i].name=="message_materials")listOf(material)else rows})
        BackupValidation.validate(current)
        val decoded=BackupFormat.decode(BackupFormat.encode(current));BackupValidation.validate(decoded)
        assertEquals(text,decoded.rows("message_materials").single().text("text"));assertEquals(1,decoded.summary().materials)
        val imported=BackupRemap.create(decoded,mapOf("key-ref" to "fresh"),1000);BackupValidation.validate(imported)
        assertNotEquals("user-a",imported.rows("message_materials").single().text("messageId"))
        assertEquals(imported.rows("messages").single {it.text("content").contains("A私密")}.text("id"),imported.rows("message_materials").single().text("messageId"))
        rejected("BACKUP_FORMAT"){BackupValidation.validate(change(current,"message_materials",0,mapOf("sourceUrl" to "http://example.invalid/")))}
        rejected("BACKUP_FORMAT"){BackupValidation.validate(change(current,"message_materials",0,mapOf("requestId" to "req-b")))}
        rejected("BACKUP_FORMAT"){BackupValidation.validate(change(current,"message_materials",0,mapOf("truncated" to 2L)))}
        val old=BackupFormat.decode(encodeV6(original));BackupValidation.validate(old)
        assertTrue(old.rows("message_materials").isEmpty());assertEquals(0,old.summary().materials)
    }
    @Test fun versionEightPreservesRoleAssetsAndMessageSnapshotsWhileVersionSevenAddsNone() {
        val original=fixture(false)
        val bytes=byteArrayOf(0xff.toByte(),0xd8.toByte(),31,0xff.toByte(),0xd9.toByte())
        val asset=row("sticker_assets","sticker-a","role-a","space-a","开心",StickerAssets.MIME_TYPE,24L,18L,StickerAssets.sha256(bytes),bytes,1L,55L)
        val message=row("message_stickers","user-a","chat-a","req-a","开心",StickerAssets.MIME_TYPE,24L,18L,StickerAssets.sha256(bytes),bytes)
        val current=original.copy(tables=original.tables.mapIndexed {i,rows->when(BackupSchema.tables[i].name){"sticker_assets"->listOf(asset);"message_stickers"->listOf(message);else->rows}})
        BackupValidation.validate(current)
        val decoded=BackupFormat.decode(BackupFormat.encode(current));BackupValidation.validate(decoded)
        assertEquals(1,decoded.summary().stickerAssets);assertEquals(1,decoded.summary().stickerMessages)
        assertArrayEquals(bytes,decoded.rows("message_stickers").single()["data"] as ByteArray)
        val imported=BackupRemap.create(decoded,mapOf("key-ref" to "fresh"),1000);BackupValidation.validate(imported)
        assertNotEquals("sticker-a",imported.rows("sticker_assets").single().text("id"))
        assertNotEquals("user-a",imported.rows("message_stickers").single().text("messageId"))
        assertEquals(imported.rows("roles").single {it.text("name")=="同名"&&it.text("id")==imported.rows("sticker_assets").single().text("roleId")}.text("spaceId"),imported.rows("sticker_assets").single().text("spaceId"))
        rejected("BACKUP_FORMAT"){BackupValidation.validate(change(current,"sticker_assets",0,mapOf("spaceId" to "space-b")))}
        rejected("BACKUP_FORMAT"){BackupValidation.validate(change(current,"message_stickers",0,mapOf("requestId" to "req-b")))}
        rejected("BACKUP_FORMAT"){BackupValidation.validate(change(current,"message_stickers",0,mapOf("sha256" to "0".repeat(64))))}
        val old=BackupFormat.decode(encodeV7(original));BackupValidation.validate(old)
        assertTrue(old.rows("sticker_assets").isEmpty());assertTrue(old.rows("message_stickers").isEmpty())
    }
    @Test fun versionNinePreservesScopedGeneratedImagesAndVersionEightAddsNone() {
        val original=fixture(false)
        val bytes=byteArrayOf(0xff.toByte(),0xd8.toByte(),41,0xff.toByte(),0xd9.toByte())
        val config=row("image_generation_configs","image-config","OpenAI 图片","https://images.example.invalid/v1/images/generations","gpt-image-1",90L,"image-key-ref",3L,"1024x1024")
        val complete=row("generated_images","generated-a","role-a","space-a","chat-a","image-config",3L,"gpt-image-1","画一只白猫","1024x1024","COMPLETE","",ImageGenerations.MIME_TYPE,24L,18L,MessageImages.sha256(bytes),bytes,50L,75L,25L)
        val running=row("generated_images","generated-b","role-b","space-b","chat-b","image-config",3L,"gpt-image-1","画一片星空","1024x1024","GENERATING","","",0L,0L,"",null,60L,0L,-1L)
        val current=original.copy(tables=original.tables.mapIndexed {i,rows->when(BackupSchema.tables[i].name){"image_generation_configs"->listOf(config);"generated_images"->listOf(complete,running);else->rows}})
        BackupValidation.validate(current)
        val decoded=BackupFormat.decode(BackupFormat.encode(current));BackupValidation.validate(decoded)
        assertEquals(1,decoded.summary().imageGenerationConfigs);assertEquals(2,decoded.summary().generatedImages)
        assertArrayEquals(bytes,decoded.rows("generated_images").first()["data"] as ByteArray)
        val imported=BackupRemap.create(decoded,mapOf("key-ref" to "fresh-model-key","image-key-ref" to "fresh-image-key"),1000L);BackupValidation.validate(imported)
        assertNotEquals("generated-a",imported.rows("generated_images").first().text("id"))
        assertNotEquals("image-config",imported.rows("generated_images").first().text("configId"))
        assertEquals("FAILED",imported.rows("generated_images").last().text("state"));assertEquals("INTERRUPTED",imported.rows("generated_images").last().text("errorCode"))
        rejected("BACKUP_FORMAT"){BackupValidation.validate(change(current,"generated_images",0,mapOf("spaceId" to "space-b")))}
        rejected("BACKUP_FORMAT"){BackupValidation.validate(change(current,"generated_images",0,mapOf("configId" to "unknown")))}
        rejected("BACKUP_FORMAT"){BackupValidation.validate(change(current,"generated_images",0,mapOf("sha256" to "0".repeat(64))))}
        rejected("BACKUP_FORMAT"){BackupValidation.validate(change(current,"image_generation_configs",0,mapOf("endpoint" to "https://evil.invalid/v1/chat/completions")))}
        val old=BackupFormat.decode(encodeV8(original));BackupValidation.validate(old)
        assertTrue(old.rows("image_generation_configs").isEmpty());assertTrue(old.rows("generated_images").isEmpty())
    }
    @Test fun versionTenPreservesReadingProgressAndRemapsTraceablePassageWhileVersionNineAddsNoBooks() {
        val original=fixture(false)
        val documentId="11111111-1111-1111-1111-111111111111"
        val text="# 第一章\n一起看海🙂\n\n# 第二章\n回家"
        val chapters=listOf(ReadingChapter(0,"第一章",0,text.indexOf("# 第二章")),ReadingChapter(1,"第二章",text.indexOf("# 第二章"),text.length))
        val document=ReadingDocument(documentId,"role-a","space-a","story.md",TextMaterials.MIME_MARKDOWN,text,ReadingDocuments.encodeChapters(chapters),0,4,ReadingDocuments.sha256(text),3,20,30)
        val passage=ReadingDocuments.passage(document,0,0,chapters[0].end)
        val book=row("reading_documents",document.id,document.roleId,document.spaceId,document.displayName,document.mimeType,document.text,document.chaptersJson,document.currentChapter.toLong(),document.currentOffset.toLong(),document.sha256,document.version,document.createdAt,document.updatedAt)
        val material=row("message_materials","user-a","chat-a","req-a",passage.kind,passage.displayName,passage.sourceUrl,passage.mimeType,passage.text,passage.originalChars.toLong(),0L,passage.sha256)
        val current=original.copy(tables=original.tables.mapIndexed {i,rows->when(BackupSchema.tables[i].name){"reading_documents"->listOf(book);"message_materials"->listOf(material);else->rows}})
        BackupValidation.validate(current)
        val decoded=BackupFormat.decode(BackupFormat.encode(current));BackupValidation.validate(decoded)
        assertEquals(1,decoded.summary().readingDocuments);assertEquals(4L,decoded.rows("reading_documents").single().number("currentOffset"))
        val imported=BackupRemap.create(decoded,mapOf("key-ref" to "fresh"),1000);BackupValidation.validate(imported)
        val importedBook=imported.rows("reading_documents").single();val importedPassage=imported.rows("message_materials").single()
        assertNotEquals(documentId,importedBook.text("id"));assertEquals(importedBook.text("id"),ReadingDocuments.parseReference(importedPassage.text("sourceUrl")).documentId)
        assertEquals(passage.text,importedPassage.text("text"))
        rejected("BACKUP_FORMAT"){BackupValidation.validate(change(current,"reading_documents",0,mapOf("spaceId" to "space-b")))}
        rejected("BACKUP_FORMAT"){BackupValidation.validate(change(current,"message_materials",0,mapOf("text" to "篡改片段")))}
        val old=BackupFormat.decode(encodeV9(original));BackupValidation.validate(old)
        assertTrue(old.rows("reading_documents").isEmpty());assertEquals(0,old.summary().readingDocuments)
    }
    @Test fun versionThirteenPreservesRecallExpiryAndForgettingWhileOlderVersionsUpgradeSafely() {
        val original=change(fixture(false),"messages",1,mapOf("state" to "COMPLETE"))
        val memory=row("memory_entries","memory-a","role-a","space-a","fictional_story","月球小屋","MEMORY-PRIVATE-一起住在月球",1L,80L,"manual",3L,20L,30L)
        val extracted=row("memory_entries","memory-b","role-a","space-a","preference","喜欢花茶","用户明确说喜欢花茶",0L,50L,"extracted",1L,40L,50L,
            "chat-a","req-a","user-a","reply-a","openai · model-x",1L,12L,40L,"auto",1000L,50L,"memory-a")
        val current=original.copy(tables=original.tables.mapIndexed {index,rows->if(BackupSchema.tables[index].name=="memory_entries")listOf(memory,extracted)else rows})
        BackupValidation.validate(current)
        val encrypted=BackupFormat.encrypt(current,password);assertFalse(encrypted.toString(Charsets.ISO_8859_1).contains("MEMORY-PRIVATE"))
        val decoded=BackupFormat.decrypt(encrypted,password);BackupValidation.validate(decoded)
        assertEquals(2,decoded.summary().memories);assertEquals("chat-a",decoded.rows("memory_entries").single {it.text("id")=="memory-b"}.text("sourceChatId"))
        val imported=BackupRemap.create(decoded,mapOf("key-ref" to "fresh"),1000);BackupValidation.validate(imported)
        val restored=imported.rows("memory_entries").single {it.text("source")=="manual"}
        assertNotEquals("memory-a",restored.text("id"));assertNotEquals("role-a",restored.text("roleId"));assertNotEquals("space-a",restored.text("spaceId"))
        assertEquals("MEMORY-PRIVATE-一起住在月球",restored.text("content"));assertEquals(3L,restored.number("version"))
        val restoredExtracted=imported.rows("memory_entries").single {it.text("source")=="extracted"}
        assertNotEquals("chat-a",restoredExtracted.text("sourceChatId"));assertEquals(imported.rows("messages").single {it.text("kind")=="assistant"}.text("id"),restoredExtracted.text("sourceAssistantMessageId"))
        assertEquals("auto",restoredExtracted.text("recallMode"));assertEquals(1000L,restoredExtracted.number("expiresAt"));assertNotEquals("memory-a",restoredExtracted.text("replacedById"))
        rejected("BACKUP_FORMAT"){BackupValidation.validate(change(current,"memory_entries",0,mapOf("spaceId" to "space-b")))}
        rejected("BACKUP_FORMAT"){BackupValidation.validate(change(current,"memory_entries",0,mapOf("kind" to "unknown")))}
        rejected("BACKUP_FORMAT"){BackupValidation.validate(change(current,"memory_entries",1,mapOf("sourceAssistantMessageId" to "user-a")))}
        val old=BackupFormat.decode(encodeV10(original));BackupValidation.validate(old)
        assertTrue(old.rows("memory_entries").isEmpty());assertEquals(0,old.summary().memories)
        val v11Snapshot=original.copy(tables=original.tables.mapIndexed {index,rows->if(BackupSchema.tables[index].name=="memory_entries")listOf(memory)else rows})
        val v11=BackupFormat.decode(encodeV11(v11Snapshot));BackupValidation.validate(v11)
        assertNull(v11.rows("memory_entries").single()["sourceChatId"]);assertEquals("manual",v11.rows("memory_entries").single().text("source"));assertEquals("always",v11.rows("memory_entries").single().text("recallMode"))
        val v12=BackupFormat.decode(encodeV12(current));BackupValidation.validate(v12)
        assertEquals("always",v12.rows("memory_entries").single {it.text("id")=="memory-a"}.text("recallMode"));assertNull(v12.rows("memory_entries").single {it.text("id")=="memory-a"}["expiresAt"])
    }
    @Test fun versionFourteenPreservesDiarySourceAndVersionThirteenAddsNoInventedDiary() {
        val original=fixture(false)
        val manual=row("diary_entries","diary-a","role-a","space-a","heart","雨天散步","DIARY-PRIVATE-今天沿着河边散步🙂","calm","manual",null,null,null,100L,2L,110L,120L)
        val generated=row("diary_entries","diary-b","role-a","space-a","role_diary","月光厨房","在虚构故事里做了一顿晚饭","happy","generated","OpenAI 兼容 · model-x",1L,"写一篇月光厨房日记",200L,1L,210L,210L)
        val current=original.copy(tables=original.tables.mapIndexed {index,rows->if(BackupSchema.tables[index].name=="diary_entries")listOf(manual,generated)else rows})
        BackupValidation.validate(current)
        val encrypted=BackupFormat.encrypt(current,password);assertFalse(encrypted.toString(Charsets.ISO_8859_1).contains("DIARY-PRIVATE"))
        val decoded=BackupFormat.decrypt(encrypted,password);BackupValidation.validate(decoded)
        assertEquals(2,decoded.summary().diaries);assertEquals("generated",decoded.rows("diary_entries").single {it.text("id")=="diary-b"}.text("source"))
        val imported=BackupRemap.create(decoded,mapOf("key-ref" to "fresh"),1000L);BackupValidation.validate(imported)
        val restored=imported.rows("diary_entries").single {it.text("source")=="manual"}
        assertNotEquals("diary-a",restored.text("id"));assertNotEquals("role-a",restored.text("roleId"));assertEquals("DIARY-PRIVATE-今天沿着河边散步🙂",restored.text("content"))
        rejected("BACKUP_FORMAT"){BackupValidation.validate(change(current,"diary_entries",0,mapOf("spaceId" to "space-b")))}
        rejected("BACKUP_FORMAT"){BackupValidation.validate(change(current,"diary_entries",1,mapOf("sourceModel" to null)))}
        rejected("BACKUP_FORMAT"){BackupValidation.validate(change(current,"diary_entries",0,mapOf("notebook" to "unknown")))}
        val old=BackupFormat.decode(encodeV13(current));BackupValidation.validate(old)
        assertTrue(old.rows("diary_entries").isEmpty());assertEquals(0,old.summary().diaries)
        assertTrue(Diaries.MAX_ENTRIES>old.summary().diaries)
    }
    @Test fun versionFifteenPreservesEnvironmentAndVersionFourteenAddsNoInventedLocation() {
        val original=fixture(false)
        val environment=row("environment_profiles","role-a","space-a",1L,1L,"open-meteo",1816670L,"杭州","浙江","中国","CN",30.2741,120.1551,"Asia/Shanghai","2026-09-20T18:00",26.5,27.1,63L,0.0,2L,8.4,21.0,29.0,20L,1_000_000L,3L,1_000_001L)
        val current=original.copy(tables=original.tables.mapIndexed {index,rows->if(BackupSchema.tables[index].name=="environment_profiles")listOf(environment)else rows})
        BackupValidation.validate(current)
        val decoded=BackupFormat.decrypt(BackupFormat.encrypt(current,password),password);BackupValidation.validate(decoded)
        assertEquals(1,decoded.summary().environments);assertEquals("杭州",decoded.rows("environment_profiles").single().text("locationName"))
        val imported=BackupRemap.create(decoded,mapOf("key-ref" to "fresh"),2_000_000L);BackupValidation.validate(imported)
        val restored=imported.rows("environment_profiles").single();assertNotEquals("role-a",restored.text("roleId"));assertNotEquals("space-a",restored.text("spaceId"));assertEquals("杭州",restored.text("locationName"))
        rejected("BACKUP_FORMAT"){BackupValidation.validate(change(current,"environment_profiles",0,mapOf("spaceId" to "space-b")))}
        rejected("BACKUP_FORMAT"){BackupValidation.validate(change(current,"environment_profiles",0,mapOf("latitude" to 999.0)))}
        rejected("BACKUP_FORMAT"){BackupValidation.validate(change(current,"environment_profiles",0,mapOf("weatherTime" to null)))}
        val old=BackupFormat.decode(encodeV14(current));BackupValidation.validate(old)
        assertTrue(old.rows("environment_profiles").isEmpty());assertEquals(0,old.summary().environments)
    }
    @Test fun versionSixteenPreservesPlannerAndCyclesWhileVersionFifteenAddsNone() {
        val original=fixture(false)
        val planner=row("planner_entries","plan-a","role-a","space-a","task","买花","下班后",2_000_000L,null,"Asia/Shanghai",null,60L,1L,2L,1_000L,1_100L)
        val cycle=row("cycle_records","cycle-a","role-a","space-a",20_000L,20_004L,"medium","PRIVATE-CYCLE",0L,1L,1_000L,1_100L)
        val current=original.copy(tables=original.tables.mapIndexed {index,rows->when(BackupSchema.tables[index].name){"planner_entries"->listOf(planner);"cycle_records"->listOf(cycle);else->rows}})
        BackupValidation.validate(current);val decoded=BackupFormat.decrypt(BackupFormat.encrypt(current,password),password);BackupValidation.validate(decoded)
        assertEquals(1,decoded.summary().plannerEntries);assertEquals(1,decoded.summary().cycleRecords)
        val imported=BackupRemap.create(decoded,mapOf("key-ref" to "fresh"),3_000_000L);BackupValidation.validate(imported)
        assertNotEquals("plan-a",imported.rows("planner_entries").single().text("id"));assertNotEquals("cycle-a",imported.rows("cycle_records").single().text("id"));assertEquals("PRIVATE-CYCLE",imported.rows("cycle_records").single().text("note"))
        rejected("BACKUP_FORMAT"){BackupValidation.validate(change(current,"planner_entries",0,mapOf("spaceId" to "space-b")))}
        rejected("BACKUP_FORMAT"){BackupValidation.validate(change(current,"planner_entries",0,mapOf("reminderMinutesBefore" to 99_999L)))}
        rejected("BACKUP_FORMAT"){BackupValidation.validate(change(current,"cycle_records",0,mapOf("endedOn" to 21_000L)))}
        val old=BackupFormat.decode(encodeV15(current));BackupValidation.validate(old)
        assertTrue(old.rows("planner_entries").isEmpty());assertTrue(old.rows("cycle_records").isEmpty())
    }
    @Test fun versionSeventeenPreservesProactiveAuditAndRestoreForcesRoleSwitchOff() {
        val original=fixture(false)
        val assistant=row("messages","pro-reply","chat-a","pro-request","assistant","晚饭吃了吗？","COMPLETE",1000L,"",20L,500L,"short",0L)
        val setting=row("proactive_role_settings","role-a","space-a",1L,"chat-a","model",1320L,480L,24L,1L,2000L,3L,900L)
        val request=row("proactive_requests","pro-request","role-a","space-a","chat-a","pro-reply","model",1L,0L,"2026-09-20","COMPLETE","",1L,1000L,1500L)
        val current=original.copy(tables=original.tables.mapIndexed {index,rows->when(BackupSchema.tables[index].name){
            "messages"->rows+assistant;"proactive_role_settings"->listOf(setting);"proactive_requests"->listOf(request);else->rows}})
        BackupValidation.validate(current);val decoded=BackupFormat.decrypt(BackupFormat.encrypt(current,password),password);BackupValidation.validate(decoded)
        assertEquals(1,decoded.summary().proactiveSettings);assertEquals(1,decoded.summary().proactiveRequests)
        val imported=BackupRemap.create(decoded,mapOf("key-ref" to "fresh"),3000L);BackupValidation.validate(imported)
        assertEquals(0L,imported.rows("proactive_role_settings").single().number("enabled"));assertNotEquals("pro-request",imported.rows("proactive_requests").single().text("requestId"))
        val old=BackupFormat.decode(encodeV16(current));BackupValidation.validate(old)
        assertTrue(old.rows("proactive_role_settings").isEmpty());assertTrue(old.rows("proactive_requests").isEmpty())
    }
    @Test fun versionEighteenPreservesSpeechConfigsAndVersionSeventeenAddsAnEmptyTable() {
        val original=fixture(false)
        val config=row("speech_service_configs","speech-a","语音合成 · tts-1","synthesis","https://example.com/v1/audio/speech","tts-1","alloy",90L,"speech-ref",2L)
        val current=original.copy(tables=original.tables.mapIndexed {index,rows->if(BackupSchema.tables[index].name=="speech_service_configs")listOf(config)else rows})
        BackupValidation.validate(current)
        val decoded=BackupFormat.decrypt(BackupFormat.encrypt(current,password),password);BackupValidation.validate(decoded)
        assertEquals(1,decoded.summary().speechServiceConfigs);assertEquals("alloy",decoded.rows("speech_service_configs").single().text("voice"))
        val imported=BackupRemap.create(decoded,mapOf("key-ref" to "fresh-key","speech-ref" to "fresh-speech"),4000L);BackupValidation.validate(imported)
        assertNotEquals("speech-a",imported.rows("speech_service_configs").single().text("id"));assertEquals("fresh-speech",imported.rows("speech_service_configs").single().text("credentialRef"))
        val old=BackupFormat.decode(encodeV17(current));BackupValidation.validate(old)
        assertTrue(old.rows("speech_service_configs").isEmpty())
    }
    @Test fun versionOneGoldenPreservesOldRecordsAndIntroducesOnlyEmptyDisabledPersonas() {
        val bytes=javaClass.getResourceAsStream("/backup-v1.bin")!!.use {it.readBytes()}
        val snapshot=BackupFormat.decode(bytes);BackupValidation.validate(snapshot)
        val role=snapshot.rows("roles").single()
        assertEquals("legacy-role",role.text("id"));assertEquals("legacy-space",role.text("spaceId"));assertEquals(5L,role.number("version"))
        assertEquals("",role.text("userName"));assertEquals("",role.text("userPersona"));assertEquals(0L,role.number("personaEnabled"))
        assertEquals("Preserved title",snapshot.rows("chats").single().text("title"))
        assertEquals(1L,snapshot.rows("messages").first().number("favorite"))
        assertEquals("partial reply",snapshot.rows("messages").last().text("content"))
        assertEquals("synthetic-v1-api-secret",snapshot.credentials.single().key)
        val updated=BackupFormat.decode(BackupFormat.encode(snapshot));BackupValidation.validate(updated)
        assertEquals(snapshot.summary(),updated.summary())
    }
    @Test fun versionTwoPersonasRemainRoleBoundThroughEncryptionAndAppend() {
        val source=change(fixture(),"roles",0,mapOf("userName" to "小青😀","userPersona" to "A-private-user-setting","personaEnabled" to 1L))
        val restored=BackupFormat.decrypt(BackupFormat.encrypt(source,password),password)
        val imported=BackupRemap.create(restored,mapOf("key-ref" to "new-key"),2000L);BackupValidation.validate(imported)
        assertEquals("小青😀",imported.rows("roles")[0].text("userName"));assertEquals(1L,imported.rows("roles")[0].number("personaEnabled"))
        assertEquals("",imported.rows("roles")[1].text("userPersona"));assertEquals(1,imported.summary().personas)
        rejected("BACKUP_FORMAT"){BackupValidation.validate(change(source,"roles",0,mapOf("userName" to "")))}
        rejected("BACKUP_FORMAT"){BackupValidation.validate(change(source,"roles",0,mapOf("personaEnabled" to 5L)))}
    }
}
