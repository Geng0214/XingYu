package com.ai.assistance.operit

import com.ai.assistance.operit.api.chat.*
import com.ai.assistance.operit.api.chat.llmprovider.*
import com.ai.assistance.operit.data.model.*
import com.ai.assistance.operit.data.security.Credentials
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

/** Storage contract double: these tests do not stand in for Room/SQLCipher isolation tests. */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayCoordinatorTest {
    private val json="""{"schemaVersion":1,"action":"place","row":7,"column":7,"comment":"一起下棋"}"""
    private class Store:PlayRequestStore {
        var role=RoleEntity("a","角色 A","A_OLD",spaceId="space-a")
        var config=ModelConfigData("cfg","连接","OpenAI 兼容","https://example.invalid/v1/chat/completions","old",60,"old-ref")
        var chat=ChatEntity("chat-a",role.id,config.id,"私聊")
        var session:PlaySession?=PlaySession(PlayAccessScope(role.scope(),chat.id,"game-a"),"棋局",GomokuState.start(PlaySide.ROLE))
        val prepared=mutableListOf<FrozenPlayTurn>()
        val progress=mutableMapOf<String,PlayProgress>()
        var prepareDelay=0L
        override suspend fun prepare(scope:PlayAccessScope,version:Long,requestId:String,approval:FrozenPlayTurn?):FrozenPlayTurn {
            if(prepareDelay>0)delay(prepareDelay)
            val p=PlayGenerations.prepare(requireNotNull(session),scope,version,role,chat,config,requestId)
            if(approval!=null&&!approval.sameApproval(p.frozen))throw ChatFailure("PLAY_CHANGED")
            session=p.session;prepared+=p.frozen;return p.frozen
        }
        private fun current(f:FrozenPlayTurn):PlaySession {
            val s=session?:throw ChatFailure("ACCESS")
            PlaySessions.checkOwner(s,s.scope,role,chat)
            if(!f.accepts(s))throw ChatFailure("PLAY_CHANGED")
            return s
        }
        override suspend fun update(frozen:FrozenPlayTurn,progress:PlayProgress) {current(frozen);this.progress[frozen.snapshot.requestId]=progress}
        override suspend fun complete(frozen:FrozenPlayTurn,result:PlayResponse,progress:PlayProgress) {
            session=frozen.commit(current(frozen),result);this.progress[frozen.snapshot.requestId]=progress
        }
        override suspend fun finishFailed(frozen:FrozenPlayTurn,progress:PlayProgress,interrupted:Boolean) {
            session=frozen.fail(current(frozen),interrupted);this.progress[frozen.snapshot.requestId]=progress
        }
        fun resume(){val s=requireNotNull(session);session=PlaySessions.resume(s,s.scope,s.version)}
    }
    private fun service(block:suspend FlowCollector<ChatStreamEvent>.(RequestSnapshot)->Unit)=object:AIService {
        override fun sendMessage(request:RequestSnapshot,key:String):Flow<String> = error("unused")
        override fun sendEvents(request:RequestSnapshot,key:String)=flow {block(request)}
    }
    private val credentials=object:Credentials{override fun read(ref:String,endpoint:String)="synthetic"}
    @Test fun twentyXiangqiCancelRetryConfigEditAndDeletionRoundsKeepBoardAndColorFrozen()=runTest {
        repeat(20){n->
            val store=Store()
            val initial=if(n%2==0)XiangqiPlayState.start(XqColor.BLACK)else XiangqiPlayState.start().apply(PlayAction.Chess(PlaySide.USER,XqMove.parse("a3a4")))
            store.session=requireNotNull(store.session).copy(state=initial)
            val move=if(n%2==0)"a3a4"else"a6a5"
            val body="""{"schemaVersion":1,"action":"move","move":"$move","comment":"一起下棋🙂"}"""
            val seen=mutableListOf<RequestSnapshot>()
            val coordinator=PlayCoordinator(store,service {request->seen+=request
                emit(ChatStreamEvent.Text(body.take(20)))
                try {delay(100);emit(ChatStreamEvent.Text(body.drop(20)));emit(ChatStreamEvent.RoundEnd)}
                finally {withContext(NonCancellable){delay(10)}}
            },credentials,backgroundScope,{true},{0},{testScheduler.currentTime})
            var session=requireNotNull(store.session)
            assertTrue(coordinator.start(session.scope,session.version));runCurrent()
            val old=store.prepared.single()
            store.config=store.config.copy(model="changed-$n",version=2)
            store.role=store.role.copy(prompt="NEW_$n",version=2)
            val cancel=async {coordinator.cancel()};runCurrent();assertTrue(ModelGenerationGate.busy())
            advanceTimeBy(11);runCurrent();cancel.await()
            assertEquals(initial.encode(),requireNotNull(store.session).state.encode())
            assertEquals("old",seen.single().config.model);assertEquals("A_OLD",seen.single().role.prompt)
            store.resume();session=requireNotNull(store.session)
            assertTrue(coordinator.start(session.scope,session.version));runCurrent()
            val fresh=store.prepared.last();assertNotEquals(old.snapshot.requestId,fresh.snapshot.requestId)
            assertEquals("changed-$n",seen.last().config.model)
            assertEquals(initial.userColor,(PlayStates.decode(fresh.confirmedState) as XiangqiPlayState).userColor)
            try {store.complete(old,PlayGenerations.decode(old,body),PlayProgress(MessageState.COMPLETE,body));fail()}catch(_:ChatFailure){}
            if(n%2==0) {
                advanceTimeBy(111);runCurrent()
                assertEquals(initial.game.moves.size+1,(requireNotNull(store.session).state as XiangqiPlayState).game.moves.size)
                assertEquals(MessageState.COMPLETE,store.progress.getValue(fresh.snapshot.requestId).state)
            }else {
                val deletion=async {coordinator.mutateRole(store.role.scope()){store.session=null}}
                runCurrent();advanceTimeBy(11);runCurrent();deletion.await()
                assertNull(store.session)
                try {store.complete(fresh,PlayGenerations.decode(fresh,body),PlayProgress(MessageState.COMPLETE,body));fail()}catch(_:ChatFailure){}
            }
            assertFalse(ModelGenerationGate.busy())
        }
    }
    @Test fun twentyAdventureCancelRetryDeleteRoundsFreezeLocationInventoryAndDialogue()=runTest {
        repeat(20){n->
            val store=Store();val initial=AdventurePlayState(AdventureGame.start().apply(AdventureAction.Interact("wood_a")).apply(AdventureAction.Say(PlaySide.USER,"一起走🙂")))
            store.session=requireNotNull(store.session).copy(state=initial)
            val body="""{"schemaVersion":1,"action":"say","text":"和你一起找材料🙂"}""";val seen=mutableListOf<RequestSnapshot>()
            val coordinator=PlayCoordinator(store,service {request->seen+=request;emit(ChatStreamEvent.Text(body.take(20)))
                try {delay(100);emit(ChatStreamEvent.Text(body.drop(20)));emit(ChatStreamEvent.RoundEnd)}
                finally {withContext(NonCancellable){delay(10)}}
            },credentials,backgroundScope,{true},{0},{testScheduler.currentTime})
            var s=requireNotNull(store.session);assertTrue(coordinator.start(s.scope,s.version));runCurrent();val old=store.prepared.single()
            store.config=store.config.copy(model="changed-$n",version=2);store.role=store.role.copy(prompt="NEW-$n",version=2)
            val cancel=async {coordinator.cancel()};runCurrent();assertTrue(ModelGenerationGate.busy());advanceTimeBy(11);runCurrent();cancel.await()
            assertEquals(initial.encode(),requireNotNull(store.session).state.encode());assertEquals(body.take(20),store.progress.getValue(old.snapshot.requestId).text)
            assertEquals("old",seen.single().config.model);assertEquals("A_OLD",seen.single().role.prompt)
            store.resume();s=requireNotNull(store.session);assertTrue(coordinator.start(s.scope,s.version));runCurrent();val fresh=store.prepared.last()
            assertNotEquals(old.snapshot.requestId,fresh.snapshot.requestId);assertEquals("changed-$n",seen.last().config.model)
            try {store.complete(old,PlayGenerations.decode(old,body),PlayProgress(MessageState.COMPLETE,body));fail()}catch(_:ChatFailure){}
            if(n%2==0) {
                advanceTimeBy(111);runCurrent();val game=(requireNotNull(store.session).state as AdventurePlayState).game
                assertEquals(initial.game.position,game.position);assertEquals(initial.game.inventory,game.inventory);assertEquals(1,game.roleActionCount)
            }else {
                val deletion=async {coordinator.mutateRole(store.role.scope()){store.session=null}};runCurrent();advanceTimeBy(11);runCurrent();deletion.await()
                assertNull(store.session)
                try {store.complete(fresh,PlayGenerations.decode(fresh,body),PlayProgress(MessageState.COMPLETE,body));fail()}catch(_:ChatFailure){}
            }
            assertFalse(ModelGenerationGate.busy())
        }
    }
    @Test fun outdatedApprovalNeverReadsCredentialOrRunsNetwork()=runTest {
        val store=Store();val s=requireNotNull(store.session)
        val approved=PlayGenerations.prepare(s,s.scope,s.version,store.role,store.chat,store.config).frozen
        store.config=store.config.copy(endpoint="https://changed.invalid/v1/chat/completions",version=2)
        val coordinator=PlayCoordinator(store,service {error("Network must not run")},
            object:Credentials{override fun read(ref:String,endpoint:String):String=error("Key must not be read")},backgroundScope,{true},{0})
        try {coordinator.start(s.scope,s.version,approved);fail()}catch(e:ChatFailure){assertEquals("PLAY_CHANGED",e.code)}
        assertEquals(s,store.session);assertTrue(store.prepared.isEmpty());assertFalse(ModelGenerationGate.busy())
    }
    @Test fun oldPageCancellationCannotCancelNewRequest()=runTest {
        val store=Store();val coordinator=PlayCoordinator(store,service {awaitCancellation()},credentials,backgroundScope,{true},{0})
        var s=requireNotNull(store.session);assertTrue(coordinator.start(s.scope,s.version));runCurrent()
        val old=requireNotNull(coordinator.running.value).requestId;coordinator.cancelRequest(old)
        store.resume();s=requireNotNull(store.session)
        assertTrue(coordinator.start(s.scope,s.version));runCurrent()
        val new=requireNotNull(coordinator.running.value).requestId
        coordinator.cancelRequest(old);assertEquals(new,coordinator.running.value?.requestId)
        assertTrue(ModelGenerationGate.busy());coordinator.cancelRequest(new);assertFalse(ModelGenerationGate.busy())
    }
    @Test fun queuedStartCannotInheritNewPageEpoch()=runTest {
        val store=Store();var epoch=1L;val locked=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        val coordinator=PlayCoordinator(store,service {error("Must not send")},credentials,backgroundScope,{true},{epoch})
        val holder=backgroundScope.launch {coordinator.mutateAll {locked.complete(Unit);release.await()}}
        locked.await();val s=requireNotNull(store.session)
        val queued=async {try {coordinator.start(s.scope,s.version,expectedAccessVersion=1L);null}catch(e:ChatFailure){e.code}}
        runCurrent();epoch=2L;release.complete(Unit);holder.join()
        assertEquals("LOCKED",queued.await());assertEquals(s,store.session);assertTrue(store.prepared.isEmpty());assertFalse(ModelGenerationGate.busy())
    }

    @Test fun twentyDelayedCancelRetryConfigEditAndDeletionRoundsRejectOldTokensAndHoldGlobalGate()=runTest {
        repeat(20){n->
            val store=Store();val seen=mutableListOf<RequestSnapshot>()
            val backend=service {request->seen+=request;emit(ChatStreamEvent.Text(json.take(20)))
                try {delay(100);emit(ChatStreamEvent.Text(json.drop(20)));emit(ChatStreamEvent.RoundEnd)}
                finally {withContext(NonCancellable){delay(50)}}
            }
            val coordinator=PlayCoordinator(store,backend,credentials,backgroundScope,{true},{0},{testScheduler.currentTime})
            val initial=requireNotNull(store.session)
            assertTrue(coordinator.start(initial.scope,initial.version));runCurrent()
            val old=store.prepared.single();assertEquals(MessageState.GENERATING,store.progress.getValue(old.snapshot.requestId).state)
            store.role=store.role.copy(prompt="A_NEW_$n",version=2);store.config=store.config.copy(model="new",version=2,credentialRef="new-ref")
            val cancellation=async {coordinator.cancel()};runCurrent()
            assertFalse(cancellation.isCompleted);assertTrue(ModelGenerationGate.busy());assertFalse(ModelGenerationGate.acquire("private-chat"))
            advanceTimeBy(51);runCurrent();cancellation.await()
            assertFalse(ModelGenerationGate.busy());assertNull(coordinator.running.value)
            assertEquals(MessageState.CANCELLED,store.progress.getValue(old.snapshot.requestId).state)
            assertEquals(json.take(20),store.progress.getValue(old.snapshot.requestId).text)
            assertEquals(0,(requireNotNull(store.session).state as GomokuState).moves.size)
            assertEquals("old",seen.single().config.model);assertEquals("A_OLD",seen.single().role.prompt)
            store.resume();val retry=requireNotNull(store.session)
            assertTrue(coordinator.start(retry.scope,retry.version));runCurrent()
            val fresh=store.prepared.last();assertNotEquals(old.snapshot.requestId,fresh.snapshot.requestId)
            assertEquals("new",seen.last().config.model);assertEquals("A_NEW_$n",seen.last().role.prompt)
            try {store.complete(old,PlayResponse(PlayAction.Place(PlaySide.ROLE,0,0),"late"),PlayProgress(MessageState.COMPLETE));fail()}
            catch(e:ChatFailure){assertEquals("PLAY_CHANGED",e.code)}
            if(n%2==0) {
                advanceTimeBy(151);runCurrent()
                assertEquals(PlaySide.ROLE,(requireNotNull(store.session).state as GomokuState).cell(7,7))
                assertEquals(MessageState.COMPLETE,store.progress.getValue(fresh.snapshot.requestId).state)
            } else {
                val deletion=async {coordinator.mutateRole(store.role.scope()){assertFalse(ModelGenerationGate.busy());store.session=null}}
                runCurrent();assertNotNull(store.session);advanceTimeBy(51);runCurrent();deletion.await()
                assertNull(store.session)
                try {store.complete(fresh,PlayResponse(PlayAction.Place(PlaySide.ROLE,0,0),"late"),PlayProgress(MessageState.COMPLETE));fail()}
                catch(e:ChatFailure){assertEquals("ACCESS",e.code)}
            }
            assertFalse(ModelGenerationGate.busy())
        }
    }
    @Test fun anotherGenerationBlocksBeforeAnyActivityRowIsPrepared()=runTest {
        val store=Store();val s=requireNotNull(store.session)
        val coordinator=PlayCoordinator(store,service {error("must not run")},credentials,backgroundScope,{true},{0})
        assertTrue(ModelGenerationGate.acquire("another-request"))
        try {assertFalse(coordinator.start(s.scope,s.version));assertTrue(store.prepared.isEmpty())}
        finally {ModelGenerationGate.release("another-request")}
    }
    @Test fun twentyJourneyCancelRetryOrDeleteRoundsPreservePlacesAndRejectLateNarration()=runTest {
        repeat(20) {n->
            val store=Store();val mode=if(n%2==0)JourneyKind.CITY else JourneyKind.SECRET
            val scene=JourneyState.start(mode,"约定","我","角色",listOf(JourneyPlace("起点","现在"),JourneyPlace("下一处","FUTURE_ONLY")))
                .apply(PlayAction.Narrate(PlaySide.USER,"你好🙂"))
            store.session=requireNotNull(store.session).copy(state=scene)
            val body="""{"schemaVersion":1,"action":"narrate","text":"我们慢慢走吧🙂"}"""
            val seen=mutableListOf<RequestSnapshot>()
            val coordinator=PlayCoordinator(store,service {request->seen+=request
                emit(ChatStreamEvent.Text(body.take(20)));try {delay(100);emit(ChatStreamEvent.Text(body.drop(20)));emit(ChatStreamEvent.RoundEnd)}
                finally {withContext(NonCancellable){delay(10)}}
            },credentials,backgroundScope,{true},{0},{testScheduler.currentTime})
            var s=requireNotNull(store.session);assertTrue(coordinator.start(s.scope,s.version));runCurrent()
            val old=store.prepared.single();val cancel=async {coordinator.cancelRequest(old.snapshot.requestId)}
            runCurrent();assertTrue(ModelGenerationGate.busy());advanceTimeBy(11);runCurrent();cancel.await()
            assertEquals(scene.encode(),requireNotNull(store.session).state.encode())
            assertEquals(body.take(20),store.progress.getValue(old.snapshot.requestId).text)
            store.config=store.config.copy(version=2,model="retry-model");store.resume();s=requireNotNull(store.session)
            val approval=PlayGenerations.prepare(s,s.scope,s.version,store.role,store.chat,store.config).frozen
            assertTrue(coordinator.start(s.scope,s.version,approval));runCurrent()
            val fresh=store.prepared.last();assertNotEquals(old.snapshot.requestId,fresh.snapshot.requestId)
            assertEquals("retry-model",seen.last().config.model);assertFalse(seen.any {q->q.turns.any {it.content.contains("FUTURE_ONLY")}})
            if(n%2==0) {
                advanceTimeBy(111);runCurrent();s=requireNotNull(store.session)
                assertEquals(0,(s.state as JourneyState).current);assertEquals(2,(s.state as JourneyState).events.size)
                store.session=PlaySessions.userAction(s,s.scope,s.version,PlayAction.Visit(1))
                try {store.complete(old,PlayResponse(PlayAction.Narrate(PlaySide.ROLE,"迟到"),""),PlayProgress(MessageState.COMPLETE));fail()}
                catch(e:ChatFailure){assertEquals("PLAY_CHANGED",e.code)}
            } else {
                val deletion=async {coordinator.mutateRole(store.role.scope()){store.session=null}}
                runCurrent();advanceTimeBy(11);runCurrent();deletion.await();assertNull(store.session)
                try {store.update(fresh,PlayProgress(MessageState.GENERATING,body));fail()}catch(e:ChatFailure){assertEquals("ACCESS",e.code)}
            }
            assertFalse(ModelGenerationGate.busy())
        }
    }
    @Test fun twentyTableGameCancelRetryConfigChangeAndDeletionRoundsKeepPrivateState()=runTest {
        repeat(20) {n->
            val store=Store()
            val initial:PlayState=if(n%2==0)WordPlayState(WordGuessGame.start(PlaySide.ROLE,WordPuzzle("物品","SECRET-$n",emptyList())))
                else CardPlayState(FourColorGame.start(PlaySide.ROLE))
            store.session=requireNotNull(store.session).copy(state=initial)
            val body=if(n%2==0)"""{"schemaVersion":1,"action":"clue","text":"每天都能看到的物品","comment":"来猜猜看"}"""
                else """{"schemaVersion":1,"action":"draw","comment":"我摸一张"}"""
            val seen=mutableListOf<RequestSnapshot>()
            val coordinator=PlayCoordinator(store,service {request->
                seen+=request;emit(ChatStreamEvent.Text(body.take(20)))
                try {delay(100);emit(ChatStreamEvent.Text(body.drop(20)));emit(ChatStreamEvent.RoundEnd)}
                finally {withContext(NonCancellable){delay(10)}}
            },credentials,backgroundScope,{true},{0},{testScheduler.currentTime})
            var s=requireNotNull(store.session);assertTrue(coordinator.start(s.scope,s.version));runCurrent()
            val old=store.prepared.single()
            store.role=store.role.copy(prompt="UPDATED-$n",version=2)
            store.config=store.config.copy(version=2,model="retry-model")
            val cancel=async {coordinator.cancelRequest(old.snapshot.requestId)}
            runCurrent();assertTrue(ModelGenerationGate.busy());advanceTimeBy(11);runCurrent();cancel.await()
            assertEquals(initial.encode(),requireNotNull(store.session).state.encode())
            assertEquals(body.take(20),store.progress.getValue(old.snapshot.requestId).text)
            assertEquals(MessageState.CANCELLED,store.progress.getValue(old.snapshot.requestId).state)
            assertEquals("A_OLD",seen.single().role.prompt);assertEquals("old",seen.single().config.model)
            store.resume();s=requireNotNull(store.session)
            assertTrue(coordinator.start(s.scope,s.version));runCurrent()
            val fresh=store.prepared.last();assertNotEquals(old.snapshot.requestId,fresh.snapshot.requestId)
            assertEquals("UPDATED-$n",seen.last().role.prompt);assertEquals("retry-model",seen.last().config.model)
            assertEquals(com.ai.assistance.operit.data.db.PlayStorageValidation.canonical(initial.observation()),
                com.ai.assistance.operit.data.db.PlayStorageValidation.canonical(org.json.JSONObject(seen.last().turns.last().content).getJSONObject("activity")))
            coordinator.cancelRequest(old.snapshot.requestId)
            assertEquals(fresh.snapshot.requestId,coordinator.running.value?.requestId)
            try {store.complete(old,PlayGenerations.decode(old,body),PlayProgress(MessageState.COMPLETE));fail()}
            catch(e:ChatFailure){assertEquals("PLAY_CHANGED",e.code)}
            if(n%4<2) {
                advanceTimeBy(111);runCurrent();s=requireNotNull(store.session)
                assertEquals(initial.apply(PlayGenerations.decode(fresh,body).action).encode(),s.state.encode())
            } else {
                val deletion=async {coordinator.mutateRole(store.role.scope()){store.session=null}}
                runCurrent();advanceTimeBy(11);runCurrent();deletion.await()
                assertNull(store.session)
                try {store.update(fresh,PlayProgress(MessageState.GENERATING,body));fail()}
                catch(e:ChatFailure){assertEquals("ACCESS",e.code)}
            }
            assertFalse(ModelGenerationGate.busy())
        }
    }
    @Test fun cancellationDuringPreparationClosesTokenWithoutReadingCredentialOrCallingProvider()=runTest {
        val store=Store();store.prepareDelay=100
        val coordinator=PlayCoordinator(store,service {error("must not run")},object:Credentials {
            override fun read(ref:String,endpoint:String):String=error("must not read")
        },backgroundScope,{true},{0})
        val s=requireNotNull(store.session);val starting=launch {coordinator.start(s.scope,s.version)}
        runCurrent();starting.cancel();assertTrue(ModelGenerationGate.busy())
        advanceTimeBy(101);runCurrent();starting.join()
        assertEquals(PlayStatus.INTERRUPTED,store.session!!.status);assertNull(store.session!!.requestId)
        assertEquals(MessageState.CANCELLED,store.progress.getValue(store.prepared.single().snapshot.requestId).state)
        assertFalse(ModelGenerationGate.busy())
    }
    @Test fun lockUnlockEpochStillCancelsResponseAndUnrelatedRoleMutationDoesNotCancel()=runTest {
        val store=Store();var epoch=0L
        val coordinator=PlayCoordinator(store,service {emit(ChatStreamEvent.Text(json.take(5)));delay(100);emit(ChatStreamEvent.Text(json.drop(5)));emit(ChatStreamEvent.RoundEnd)},
            credentials,backgroundScope,{true},{epoch})
        val s=requireNotNull(store.session);assertTrue(coordinator.start(s.scope,s.version));runCurrent()
        var edited=false;coordinator.mutateRole(RoleAccessScope("other","space-other")){edited=true}
        assertTrue(edited);assertNotNull(coordinator.running.value)
        epoch+=2 // Locked then unlocked before the provider resumed.
        advanceTimeBy(101);runCurrent()
        assertEquals(PlayStatus.INTERRUPTED,store.session!!.status)
        assertEquals(0,(store.session!!.state as GomokuState).moves.size);assertFalse(ModelGenerationGate.busy())
    }
    @Test fun invalidResponsePreservesRequestAndDoesNotAlterBoard()=runTest {
        val store=Store()
        val coordinator=PlayCoordinator(store,service {emit(ChatStreamEvent.Text("invalid"));emit(ChatStreamEvent.RoundEnd)},
            credentials,backgroundScope,{true},{0})
        val s=requireNotNull(store.session);assertTrue(coordinator.start(s.scope,s.version));runCurrent()
        val final=store.progress.getValue(store.prepared.single().snapshot.requestId)
        assertEquals(PlayStatus.ACTIVE,store.session!!.status);assertNull(store.session!!.requestId)
        assertEquals(0,(store.session!!.state as GomokuState).moves.size);assertFalse(ModelGenerationGate.busy())
    }
    @Test fun providerToolCallCannotExecuteAnExtensionFromActivityContext()=runTest {
        val store=Store();var executed=0
        val toolService=ToolChatService(object:ModelRoundService {
            override fun streamRound(request:RequestSnapshot,key:String,messages:org.json.JSONArray,tools:org.json.JSONArray)=flow {
                assertEquals(0,tools.length());emit(ModelRoundEvent.End(listOf(ModelToolCall("call","arbitrary","{}"))))
            }
        }){executed++;error("must not execute")}
        val coordinator=PlayCoordinator(store,toolService,credentials,backgroundScope,{true},{0})
        val s=requireNotNull(store.session);assertTrue(coordinator.start(s.scope,s.version));runCurrent()
        assertEquals(0,executed);assertEquals("TOOLS_DISABLED",store.progress.values.single().errorCode)
    }
    @Test fun missingCredentialFinishesRequestWithOriginalFailureAndReleasesGate()=runTest {
        val store=Store();val observed=mutableListOf<Pair<String,String>>()
        val coordinator=PlayCoordinator(store,service {error("must not run")},object:Credentials {
            override fun read(ref:String,endpoint:String):String {observed+=ref to endpoint;throw ChatFailure("CREDENTIAL")}
        },backgroundScope,{true},{0})
        val s=requireNotNull(store.session);assertTrue(coordinator.start(s.scope,s.version));runCurrent()
        assertEquals(listOf(store.config.credentialRef to store.config.endpoint),observed)
        assertEquals("CREDENTIAL",store.progress.values.single().errorCode);assertFalse(ModelGenerationGate.busy())
    }
}
