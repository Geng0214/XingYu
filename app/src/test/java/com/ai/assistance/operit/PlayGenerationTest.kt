package com.ai.assistance.operit

import com.ai.assistance.operit.api.chat.*
import com.ai.assistance.operit.api.chat.llmprovider.*
import com.ai.assistance.operit.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlayGenerationTest {
    private val role=RoleEntity("role-a","小岚","温柔的角色",version=3,spaceId="space-a",openingMessage="PRIVATE_OPENING",userPersona="PRIVATE_MASK",personaEnabled=true)
    private val config=ModelConfigData("cfg","连接","OpenAI 兼容","https://example.invalid/v1/chat/completions","model",60,"ref",version=4)
    private val chat=ChatEntity("chat-a",role.id,config.id,"PRIVATE_CHAT_TITLE")
    private val scope=PlayAccessScope(role.scope(),chat.id,"activity-a")
    private fun session(state:PlayState=GomokuState.start(PlaySide.ROLE))=PlaySession(scope,"一起玩",state)
    private fun prepare(s:PlaySession=session(),r:RoleEntity=role,c:ModelConfigData=config)=PlayGenerations.prepare(s,s.scope,s.version,r,chat,c)
    private fun rejected(code:String,block:()->Unit){try{block();fail("Expected $code")}catch(e:ChatFailure){assertEquals(code,e.code)}}
    private val place="""{"schemaVersion":1,"action":"place","row":7,"column":7,"comment":"轮到你啦🙂"}"""
    private fun service(events:List<ChatStreamEvent>)=object:AIService {
        override fun sendMessage(request:RequestSnapshot,key:String):Flow<String> = error("unused")
        override fun sendEvents(request:RequestSnapshot,key:String)=events.asFlow()
    }

    @Test fun requestFreezesModelAndPersonaWithoutPrivateHistoryOrToolContext() {
        val frozen=prepare().frozen;val request=frozen.snapshot
        val all=request.turns.joinToString {it.content}
        assertTrue(all.contains(role.prompt));assertFalse(all.contains("PRIVATE_"))
        assertEquals("",request.role.userPersona);assertEquals("",request.role.openingMessage)
        assertEquals(config,request.config);assertEquals(scope,request.play!!.scope)
        assertTrue(request.tools.isEmpty());assertTrue(request.memories.decisions.isEmpty());assertTrue(request.worldbook.decisions.isEmpty());assertNull(request.group)
        try {(request.turns as MutableList<PromptTurn>).clear();fail()}catch(_:UnsupportedOperationException){}
        rejected("ACCESS"){GroupConversations.requirePrivate(request)}
        val changedRole=role.copy(prompt="new",version=4)
        val changedConfig=config.copy(model="new",version=5)
        assertEquals(config,request.config);assertEquals(role.prompt,request.role.prompt)
        val retry=prepare(r=changedRole,c=changedConfig).frozen
        assertNotEquals(request.requestId,retry.snapshot.requestId);assertEquals("new",retry.snapshot.config.model)
    }
    @Test fun rawActivityKeepsGameProtocolButExcludesCharacterPersona() {
        val request=prepare(r=role.copy(conversationMode="raw")).frozen.snapshot
        assertFalse(request.turns.any {it.content.contains(role.prompt)});assertEquals("",request.role.prompt)
        assertTrue(request.turns.first().content.contains("JSON"))
    }
    @Test fun approvalBindsDestinationCredentialsIdentityAndStateButNotRequestIds() {
        val approved=prepare().frozen
        assertTrue(approved.sameApproval(prepare().frozen))
        listOf(config.copy(endpoint="https://other.invalid/v1/chat/completions"),config.copy(credentialRef="other-ref"),
            config.copy(version=config.version+1),config.copy(model="other-model")).forEach {changed->
            assertFalse(approved.sameApproval(prepare(c=changed).frozen))
        }
        assertFalse(approved.sameApproval(prepare(r=role.copy(prompt="different")).frozen))
        assertFalse(approved.sameApproval(prepare(s=session().copy(version=2)).frozen))
        assertFalse(approved.sameApproval(prepare(s=session().copy(scope=scope.copy(activityId="another"))).frozen))
    }
    @Test fun hiddenDeckIsNeverSentEvenAfterSaveRecovery() {
        fun saved(deck:List<Int>)=PlayStates.decode(JSONObject().put("schemaVersion",1).put("kind","memory")
            .put("first","role").put("deck",JSONArray(deck)).put("actions",JSONArray()).toString())
        val a=prepare(session(saved(listOf(0,1,0,1,2,2)))).frozen.snapshot
        val b=prepare(session(saved(listOf(0,1,2,2,0,1)))).frozen.snapshot
        assertEquals(a.turns,b.turns);assertFalse(a.turns.any {it.content.contains("\"deck\"")})
        val result=PlayGenerations.decode(prepare(session(saved(listOf(0,1,0,1)))).frozen,
            """{"schemaVersion":1,"action":"flip","index":0,"comment":"翻一张"}""")
        assertEquals(PlayAction.Flip(PlaySide.ROLE,0),result.action)
    }
    @Test fun onlyOneLegalActionCanBeAcceptedAndNoTextBecomesExecutable() {
        val prepared=prepare();val response=PlayGenerations.decode(prepared.frozen,place)
        val committed=prepared.frozen.commit(prepared.session,response)
        assertEquals(PlaySide.ROLE,(committed.state as GomokuState).cell(7,7));assertEquals(PlaySide.USER,committed.state.turn)
        rejected("PLAY_CHANGED"){prepared.frozen.commit(committed,response)}
        listOf(place.replace("\"row\":7","\"row\":\"7\""),place.replace("\"row\":7","\"row\":7.1"),
            place.replace("\"column\":7","\"column\":15"),place.replace("\"schemaVersion\":1","\"schemaVersion\":1,\"schemaVersion\":1"),
            JSONObject(place).put("url","https://evil.invalid").toString(),JSONObject(place).put("script","run()").toString(),
            JSONObject(place).put("roleId","foreign").toString(),JSONObject(place).put("path","/private").toString(),
            "```json\n$place\n```","[$place]",place+place,place.replace("\"place\"","\"win\""),
            JSONObject(place).put("comment","x".repeat(1201)).toString()).forEach {bad->rejected("PLAY_RESPONSE"){PlayGenerations.decode(prepared.frozen,bad)}}
        val textOnly=JSONObject(place).put("comment","https://evil.invalid <script>run()</script>").toString()
        assertTrue(PlayGenerations.decode(prepared.frozen,textOnly).comment.contains("<script>")) // Inert text, never a tool/action.
        rejected("PLAY_ACTION"){prepared.frozen.commit(prepared.session,PlayResponse(PlayAction.Place(PlaySide.USER,7,7),""))}
    }
    @Test fun invalidOccupiedCoordinateKeepsExactStateAndAllowsNewRequest() {
        val state=GomokuState.start().apply(PlayAction.Place(PlaySide.USER,7,7))
        val prepared=prepare(session(state));val before=prepared.session.state.encode()
        rejected("PLAY_RESPONSE"){PlayGenerations.decode(prepared.frozen,place)}
        val failed=prepared.frozen.fail(prepared.session,false)
        assertEquals(before,failed.state.encode());assertNull(failed.requestId);assertEquals(PlayStatus.ACTIVE,failed.status)
        val retry=prepare(failed);assertNotEquals(prepared.frozen.snapshot.requestId,retry.frozen.snapshot.requestId)
        assertFalse(prepared.frozen.accepts(retry.session))
    }
    @Test fun wrongRoleSpaceChatConfigActivityOrVersionCannotPrepareOrCommit() {
        val s=session()
        rejected("ACCESS"){prepare(s,r=role.copy(spaceId="foreign"))}
        rejected("ACCESS"){prepare(s,c=config.copy(id="foreign"))}
        rejected("ACCESS"){PlayGenerations.prepare(s,scope.copy(activityId="foreign"),1,role,chat,config)}
        rejected("ACCESS"){PlayGenerations.prepare(s,scope,1,role,chat.copy(roleId="foreign"),config)}
        rejected("ACCESS"){PlayGenerations.prepare(s,scope,1,role,chat.copy(id="foreign"),config)}
        rejected("PLAY_CHANGED"){PlayGenerations.prepare(s,scope,2,role,chat,config)}
        val p=prepare();val response=PlayGenerations.decode(p.frozen,place)
        listOf(p.session.copy(scope=scope.copy(activityId="other")),p.session.copy(scope=scope.copy(role=role.copy(spaceId="other").scope())),
            p.session.copy(scope=scope.copy(chatId="other")),p.session.copy(requestId="other"),p.session.copy(version=99)).forEach {bad->
            rejected("PLAY_CHANGED"){p.frozen.commit(bad,response)}
        }
    }
    @Test fun pauseAndRestartInvalidatePendingActionsWithoutLosingConfirmedStory() {
        val story=DateStoryState.start("海边","约会").apply(PlayAction.Narrate(PlaySide.USER,"走走吧"))
        val p=prepare(session(story));val response=PlayGenerations.decode(p.frozen,"""{"schemaVersion":1,"action":"narrate","text":"一起走向海边🙂"}""")
        val paused=PlaySessions.pause(p.session,scope,p.session.version)
        assertEquals(PlayStatus.PAUSED,paused.status);assertEquals(story.encode(),paused.state.encode())
        rejected("PLAY_CHANGED"){p.frozen.commit(paused,response)}
        val resumed=PlaySessions.resume(paused,scope,paused.version);assertNull(resumed.requestId)
        val restarted=PlaySessions.recover(p.session);assertEquals(PlayStatus.INTERRUPTED,restarted.status);assertNull(restarted.requestId)
        assertEquals(story.encode(),restarted.state.encode());rejected("PLAY_CHANGED"){p.frozen.commit(restarted,response)}
        val next=prepare(PlaySessions.resume(restarted,scope,restarted.version))
        val confirmed=next.frozen.commit(next.session,response)
        assertEquals(2,(confirmed.state as DateStoryState).turns.size)
        val ended=PlaySessions.userAction(confirmed,scope,confirmed.version,PlayAction.EndStory)
        assertEquals(PlayStatus.COMPLETE,ended.status);assertEquals(PlayResult.ENDED,ended.state.result)
    }
    @Test fun fiftyAlternatingRequestsContainOnlyTheirTenOwnMarkers() {
        val roles=listOf("A","B").map {id->role.copy(id="role-$id",spaceId="space-$id",prompt=(0..9).joinToString {"MARK_${id}_$it"})}
        val snapshots=mutableListOf<RequestSnapshot>()
        repeat(50){i->
            val owner=roles[i%2];val c=chat.copy(id="chat-${owner.id}",roleId=owner.id)
            val s=PlaySession(PlayAccessScope(owner.scope(),c.id,"game-$i"),"游戏",GomokuState.start(PlaySide.ROLE))
            val p=PlayGenerations.prepare(s,s.scope,1,owner,c,config)
            snapshots+=p.frozen.snapshot
            val text=p.frozen.snapshot.turns.joinToString {it.content}
            val mine=if(i%2==0)"A" else "B";val other=if(i%2==0)"B"else"A"
            repeat(10){n->assertTrue(text.contains("MARK_${mine}_$n"));assertFalse(text.contains("MARK_${other}_$n"))}
            val done=p.frozen.commit(p.session,PlayGenerations.decode(p.frozen,place))
            assertEquals(owner.scope(),done.scope.role)
            rejected("PLAY_CHANGED"){p.frozen.commit(done.copy(scope=done.scope.copy(role=roles[(i+1)%2].scope())),PlayResponse(PlayAction.Place(PlaySide.ROLE,8,8),""))}
        }
        assertEquals(50,snapshots.map {it.requestId}.toSet().size)
    }
    @Test fun streamPreservesSplitChineseAndEmoji()=runTest {
        val recorded=mutableListOf<String>()
        val chunks=place.map {ChatStreamEvent.Text(it.toString())}+ChatStreamEvent.RoundEnd
        val result=PlayGenerationService(service(chunks)).generate(prepare().frozen,"key"){recorded+=it}
        assertEquals("轮到你啦🙂",result.response.comment);assertEquals(place,recorded.last())
        try {PlayGenerationService(service(listOf(ChatStreamEvent.Text("bad"),ChatStreamEvent.RoundEnd)))
            .generate(prepare().frozen,"key");fail()}catch(e:ChatFailure){assertEquals("PLAY_RESPONSE",e.code)}
    }
    @Test fun streamRejectsMissingEndDoubleEndLateTextAndOversize()=runTest {
        val cases=listOf(
            listOf(ChatStreamEvent.Text(place)) to "TRUNCATED",
            listOf(ChatStreamEvent.Text(place),ChatStreamEvent.RoundEnd,ChatStreamEvent.RoundEnd) to "PROTOCOL",
            listOf(ChatStreamEvent.Text(place),ChatStreamEvent.RoundEnd,ChatStreamEvent.Text("late")) to "PROTOCOL",
            listOf(ChatStreamEvent.Text("x".repeat(65537))) to "PLAY_RESPONSE")
        for((events,code) in cases)try {PlayGenerationService(service(events)).generate(prepare().frozen,"key");fail()}catch(e:ChatFailure){assertEquals(code,e.code)}
    }
    @Test fun cancellationAfterRoundEndNeverReturnsAUsableAction()=runTest {
        val p=prepare();var result:PlayGenerationResult?=null
        val job=launch {result=PlayGenerationService(service(listOf(ChatStreamEvent.Text(place),ChatStreamEvent.RoundEnd)))
            .generate(p.frozen,"key"){currentCoroutineContext().cancel()}}
        job.join();assertTrue(job.isCancelled);assertNull(result);assertEquals(0,(p.session.state as GomokuState).moves.size)
    }
}
