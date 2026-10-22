package com.ai.assistance.operit

import com.ai.assistance.operit.api.chat.*
import com.ai.assistance.operit.api.chat.llmprovider.*
import com.ai.assistance.operit.data.db.*
import com.ai.assistance.operit.data.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class JourneyTest {
    private val places=listOf(JourneyPlace("起点","PRESENT🙂"),JourneyPlace("河岸","FUTURE_ONE"),JourneyPlace("终点","FUTURE_TWO"))
    private fun start(mode:JourneyKind=JourneyKind.CITY)=JourneyState.start(mode,"一次虚构约会","小我","小岚",places)
    private fun round(s:JourneyState)=s.apply(PlayAction.Narrate(PlaySide.USER,"你好🙂")).apply(PlayAction.Narrate(PlaySide.ROLE,"一起走走"))
    private fun invalid(block:()->Unit){try{block();fail("Expected rejection")}catch(_:IllegalArgumentException){}catch(_:ChatFailure){}}
    private val role=RoleEntity("role","小岚","ROLE_PERSONA",spaceId="space")
    private val cfg=ModelConfigData("cfg","连接","openai","https://example.invalid/v1/chat/completions","model",60,"ref")
    private val chat=ChatEntity("chat",role.id,cfg.id,"PRIVATE_CHAT")
    private fun prepare(state:JourneyState):PreparedPlayTurn {
        val session=PlaySession(PlayAccessScope(role.scope(),chat.id,"journey"),"一起漫游",state)
        return PlayGenerations.prepare(session,session.scope,session.version,role,chat,cfg)
    }
    @Test fun cityCanRevisitAndRestoresExactChronologyWithoutMovingForTheUser() {
        val journey=round(start()).apply(PlayAction.Visit(2)).apply(PlayAction.Visit(0))
        assertEquals(listOf(0,2),journey.visited);assertEquals(0,journey.current)
        assertEquals(listOf(0,0,0,1,2),journey.records.map {it.visit})
        assertEquals(journey.encode(),PlayStates.decode(journey.encode()).encode())
        assertEquals(listOf(0,2,0),journey.records.filter {it.side==null}.map {it.place})
        invalid {journey.apply(PlayAction.Visit(0))};invalid {journey.apply(PlayAction.Visit(3))}
    }
    @Test fun secretJourneyRequiresConfirmedDialogueAndCannotSkipOrRewind() {
        val s=start(JourneyKind.SECRET)
        assertFalse(s.canAdvance);invalid {s.apply(PlayAction.Visit(1))}
        val said=s.apply(PlayAction.Narrate(PlaySide.USER,"一起开始吧"))
        invalid {said.apply(PlayAction.Visit(1))}
        val first=said.apply(PlayAction.Narrate(PlaySide.ROLE,"我准备好了"))
        assertTrue(first.canAdvance);invalid {first.apply(PlayAction.Visit(2))}
        val next=first.apply(PlayAction.Visit(1));assertFalse(next.canAdvance)
        invalid {next.apply(PlayAction.Visit(0))}
        val last=round(next).apply(PlayAction.Visit(2));assertFalse(last.canAdvance)
        val ended=round(last).apply(PlayAction.EndStory);assertNull(ended.turn);assertEquals(PlayResult.ENDED,ended.result)
        invalid {ended.apply(PlayAction.Narrate(PlaySide.USER,"再来"))}
    }
    @Test fun futureScenesAreNotPresentBeforeArrivalAndDuplicatePlaceNamesRetainIndices() {
        val a=start();assertFalse(a.observation().toString().contains("FUTURE"))
        val b=a.apply(PlayAction.Visit(1));assertTrue(b.observation().toString().contains("FUTURE_ONE"))
        assertFalse(b.observation().toString().contains("FUTURE_TWO"))
        val same=JourneyState.start(JourneyKind.CITY,"设定","我","你",places.map {it.copy(name="同名")}).apply(PlayAction.Visit(2))
        assertEquals(2,same.observation().getJSONObject("currentPlace").getInt("index"))
        assertEquals(2,same.observation().getJSONArray("history").getJSONObject(1).getInt("placeIndex"))
    }
    @Test fun versionTwoRejectsUnknownKindsFieldsTypesAndMalformedTextWhileOldSavesStillRead() {
        listOf(DateStoryState.start("旧地点","旧场景"),GomokuState.start(),MemoryPairsState.start()).forEach {assertEquals(it.encode(),PlayStates.decode(it.encode()).encode())}
        val original=JSONObject(start().encode())
        listOf(JSONObject(original.toString()).put("schemaVersion",1),JSONObject(original.toString()).put("schemaVersion",3),
            JSONObject(original.toString()).put("kind","date"),JSONObject(original.toString()).put("externalId","attack"),
            JSONObject(original.toString()).put("ended","false")).forEach {invalid {PlayStates.decode(it.toString())}}
        invalid {JourneyState.start(JourneyKind.CITY,"x","我","你",places.take(1))}
        invalid {JourneyState.start(JourneyKind.CITY,"x","我","你",List(13){places[0]})}
        invalid {JourneyState.start(JourneyKind.CITY,"x","我","你",listOf(places[0],JourneyPlace("x","\uD800")))}
        invalid {start().apply(PlayAction.Narrate(PlaySide.USER,"x\u0000"))}
        invalid {start().apply(PlayAction.Narrate(PlaySide.USER,"x".repeat(4001)))}
    }
    @Test fun travelAndNarrationStayBoundedAndDataNeverExecutesOrOpensUrls() {
        val malicious="https://example.invalid/private <script>fetch('/secret')</script> ../../private"
        val s=JourneyState.start(JourneyKind.CITY,malicious,"我","你",places)
        assertTrue(s.observation().getString("premise").contains("<script>"))
        var current=s;repeat(JourneyState.MAX_EVENTS){n->current=current.apply(PlayAction.Visit(if(n%2==0)1 else 0))}
        invalid {current.apply(PlayAction.Visit(1))}
        var full=start();repeat(2){full=full.apply(PlayAction.Narrate(PlaySide.USER,"x".repeat(4000))).apply(PlayAction.Narrate(PlaySide.ROLE,"y".repeat(8000)))}
        invalid {full.apply(PlayAction.Narrate(PlaySide.USER,"再说一句"))}
    }
    @Test fun fiftyRoleSnapshotsContainOnlyTheirConfirmedJourneyAndNoFutureScenes() {
        repeat(50){i->
            val owner=if(i%2==0)"A"else"B";val other=if(owner=="A")"B"else"A"
            val markers=(0..9).joinToString {"JOURNEY_${owner}_$it"}
            val r=role.copy(id="role-$owner",spaceId="space-$owner",prompt=markers)
            val c=chat.copy(id="chat-$owner",roleId=r.id)
            val s=PlaySession(PlayAccessScope(r.scope(),c.id,"walk-$i"),"同名",JourneyState.start(JourneyKind.CITY,markers,"我","你",places).apply(PlayAction.Narrate(PlaySide.USER,markers)))
            val prepared=PlayGenerations.prepare(s,s.scope,s.version,r,c,cfg)
            val text=prepared.frozen.snapshot.turns.joinToString {it.content}
            repeat(10){assertTrue(text.contains("JOURNEY_${owner}_$it"));assertFalse(text.contains("JOURNEY_${other}_$it"))}
            assertFalse(text.contains("FUTURE"));assertFalse(text.contains("PRIVATE_CHAT"));assertTrue(prepared.frozen.snapshot.tools.isEmpty())
            invalid {PlayGenerations.decode(prepared.frozen,"""{"schemaVersion":1,"action":"visit","place":1}""")}
        }
    }
    @Test fun saturatedStatesAreRejectedBeforeApprovalForEventsCharactersAndEncodedBytes() {
        var almost=start()
        repeat(199){n->almost=almost.apply(PlayAction.Visit(if(n%2==0)1 else 0))}
        invalid {almost.apply(PlayAction.Narrate(PlaySide.USER,"最后一句"))}
        fun stored(source:JourneyState,text:String):JourneyState {
            val json=JSONObject(source.encode())
            json.getJSONArray("events").put(JSONObject().put("action","narrate").put("side","user").put("text",text))
            return PlayStates.decode(json.toString()) as JourneyState
        }
        val eventFull=stored(almost,"最后一句")
        assertFalse(PlaySessions.canRequest(eventFull));invalid {prepare(eventFull)}
        var chars=start()
        chars=chars.apply(PlayAction.Narrate(PlaySide.USER,"a".repeat(4000))).apply(PlayAction.Narrate(PlaySide.ROLE,"b".repeat(8000)))
        chars=chars.apply(PlayAction.Narrate(PlaySide.USER,"c".repeat(4000))).apply(PlayAction.Narrate(PlaySide.ROLE,"d".repeat(4000)))
        invalid {chars.apply(PlayAction.Narrate(PlaySide.USER,"e".repeat(4000)))}
        val characterFull=stored(chars,"e".repeat(4000))
        assertFalse(PlaySessions.canRequest(characterFull));invalid {prepare(characterFull)}
        var bytes=start()
        bytes=bytes.apply(PlayAction.Narrate(PlaySide.USER,"\u0001".repeat(4000))).apply(PlayAction.Narrate(PlaySide.ROLE,"\u0001".repeat(7000)))
        bytes=bytes.apply(PlayAction.Narrate(PlaySide.USER,"\u0001".repeat(4000))).apply(PlayAction.Narrate(PlaySide.ROLE,"\u0001".repeat(5000)))
        val overhead=stored(bytes,"x").encode().toByteArray(Charsets.UTF_8).size-1
        val remaining=PlayJson.MAX_BYTES-overhead
        val finalText="\u0001".repeat(remaining/6)+"x".repeat(remaining%6)
        val byteFull=stored(bytes,finalText)
        assertEquals(PlayJson.MAX_BYTES,byteFull.encode().toByteArray(Charsets.UTF_8).size)
        assertFalse(PlaySessions.canRequest(byteFull));invalid {prepare(byteFull)}
        invalid {bytes.apply(PlayAction.Narrate(PlaySide.USER,finalText))}
        listOf(eventFull,characterFull,byteFull).forEach {
            assertEquals(PlayResult.ENDED,it.apply(PlayAction.EndStory).result)
            assertTrue(it.restart().events.isEmpty())
        }
    }
    @Test fun modelRoundCommitsOnlyNarrationAndMalformedRouteEditsNeverChangeState()=runTest {
        val p=prepare(start().apply(PlayAction.Narrate(PlaySide.USER,"你好")))
        val json="""{"schemaVersion":1,"action":"narrate","text":"一起走走🙂"}"""
        val service=object:AIService {
            override fun sendMessage(request:RequestSnapshot,key:String):Flow<String> = error("unused")
            override fun sendEvents(request:RequestSnapshot,key:String)=flow {
                json.forEach {emit(ChatStreamEvent.Text(it.toString()))}
                emit(ChatStreamEvent.RoundEnd)
            }
        }
        val answer=PlayGenerationService(service).generate(p.frozen,"synthetic")
        val completed=p.frozen.commit(p.session,answer.response)
        val s=completed.state as JourneyState;assertEquals(0,s.current);assertEquals("一起走走🙂",s.records.last().text)
        invalid {PlayGenerations.decode(p.frozen,json.dropLast(1)+",\"place\":2}")}
        val restarted=s.restart();assertTrue(restarted.events.isEmpty());assertEquals(s.places,restarted.places)
    }
    @Test fun storedRequestHistoryRejectsRewrittenRoutesOrParticipantNamesAndRecoversPendingTurns() {
        val s=start().apply(PlayAction.Narrate(PlaySide.USER,"你好"));val p=prepare(s)
        val row=PlaySessionRow.from(p.session,10)
        val request=PlayRequestRow.from(p.frozen,10).progress(PlayProgress(MessageState.WAITING))
        PlayStorageValidation.validate(row,listOf(request))
        val edited=JourneyState.start(JourneyKind.CITY,s.premise,s.userName,"陌生人",places).apply(PlayAction.Narrate(PlaySide.USER,"你好"))
        invalid {PlayStorageValidation.validate(row.copy(stateJson=edited.encode()),listOf(request))}
        val moved=s.apply(PlayAction.Narrate(PlaySide.ROLE,"走吧")).apply(PlayAction.Visit(1))
        assertTrue(s.isPrefixOf(moved));assertFalse(moved.isPrefixOf(s))
        val recovered=PlaySessions.recover(p.session)
        PlayStorageValidation.validate(row.changed(recovered,20),listOf(request.interrupted()))
        assertEquals(s.encode(),recovered.state.encode());assertFalse(p.frozen.accepts(recovered))
    }
}
