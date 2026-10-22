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

@OptIn(ExperimentalCoroutinesApi::class)
class ChatCoordinatorTest {
    open class Store:RequestStore {
        var role=RoleEntity("a","A","original")
        var config=ModelConfigData("config","test","OpenAI 兼容","https://example.com/v1/chat/completions","old",90,"ref")
        val states=mutableMapOf<String,MessageState>()
        val texts=mutableMapOf<String,String>()
        val snapshots=mutableListOf<RequestSnapshot>()
        val errors=mutableMapOf<String,String>()
        override suspend fun prepare(scope:RoleAccessScope,chatId:String,text:String,requestId:String):RequestSnapshot {
            val s=RequestSnapshot(requestId,chatId,requestId,role.copy(),config.copy(),listOf(PromptTurn("user",text)))
            snapshots.add(s);states[requestId]=MessageState.QUEUED;return s
        }
        override suspend fun update(snapshot:RequestSnapshot,text:String,state:MessageState,error:String,first:Long,elapsed:Long) {
            val id=snapshot.assistantId
            if(states[id] in activeStates){states[id]=state;texts[id]=text;errors[id]=error}
        }
    }
    private val credentials=object:Credentials{override fun read(ref:String,endpoint:String)="synthetic-key"}
    @Test fun twentyModeAndRelationshipChangesFreezeCurrentRequestAndApplyOnlyOnRetry()=runTest {
        repeat(20){n->
            val base=Store();val original=base.role
            var relationships=listOf(RelationshipEntry("rel",original.id,original.spaceId,null,"Me","AI","friend","OLD_REL",true,50))
            val store=object:RequestStore by base {
                override suspend fun prepare(scope:RoleAccessScope,chatId:String,text:String,requestId:String):RequestSnapshot {
                    val snapshot=base.prepare(scope,chatId,text,requestId)
                    val resolved=RequestContext.resolve(scope,snapshot.role,ChatEntity(chatId,scope.roleId,snapshot.config.id,"chat"),emptyList(),text,emptyList(),relationships)
                    return snapshot.copy(turns=resolved.turns,relationships=resolved.relationships)
                }
            }
            val seen=mutableListOf<RequestSnapshot>()
            val service=object:AIService {override fun sendMessage(request:RequestSnapshot,key:String)=flow {seen+=request;emit("partial");delay(100);emit("done")}}
            val coordinator=ChatCoordinator(store,service,credentials,backgroundScope)
            coordinator.send(original.scope(),"chat","hello");runCurrent()
            base.role=original.copy(conversationMode=if(n%2==0)"raw"else"character",version=2)
            base.config=base.config.copy(model="changed",version=2)
            relationships=relationships.map {it.copy(description="NEW_REL",version=2)}
            coordinator.cancel()
            assertTrue(seen.first().turns.any {it.content.contains("OLD_REL")});assertEquals("character",seen.first().role.conversationMode)
            coordinator.send(original.scope(),"chat","hello");runCurrent();advanceTimeBy(101);runCurrent()
            val retry=seen.last();assertNotEquals(seen.first().requestId,retry.requestId)
            assertEquals(n%2!=0,retry.turns.any {it.role=="system"&&it.content.contains("NEW_REL")})
            assertFalse(retry.turns.any {it.content.contains("OLD_REL")});assertEquals("changed",retry.config.model)
            base.update(seen.first(),"late",MessageState.COMPLETE,"",1,1)
            assertEquals("partial",base.texts[seen.first().requestId])
        }
    }
    @Test fun twentyWorldbookEditsCancelRetryAndRoleSwitchesKeepFrozenOwnership()=runTest {
        repeat(20){n->
            val base=Store();val a=base.role
            var entries=listOf(WorldbookEntry("lore",a.id,a.spaceId,null,"城市","OLD_LORE","always","",true,50))
            val store=object:RequestStore by base {
                override suspend fun prepare(scope:RoleAccessScope,chatId:String,text:String,requestId:String):RequestSnapshot {
                    val frozen=base.prepare(scope,chatId,text,requestId)
                    val resolved=RequestContext.resolve(scope,frozen.role,ChatEntity(chatId,scope.roleId,frozen.config.id,"chat"),emptyList(),text,entries)
                    return frozen.copy(turns=resolved.turns,worldbook=resolved.worldbook)
                }
            }
            val seen=mutableListOf<RequestSnapshot>()
            val service=object:AIService {override fun sendMessage(request:RequestSnapshot,key:String)=flow{seen+=request;emit("partial");delay(100);emit("done")}}
            val coordinator=ChatCoordinator(store,service,credentials,backgroundScope)
            coordinator.send(a.scope(),"chat-a","hello");runCurrent()
            entries=entries.map {it.copy(content="NEW_LORE",enabled=n%2==0,version=2)}
            base.config=base.config.copy(model="new",version=2)
            coordinator.cancel()
            assertTrue(seen.single().turns.first().content.contains("OLD_LORE"))
            assertEquals(1L,seen.single().worldbook.decisions.single().version)
            coordinator.send(a.scope(),"chat-a","hello");runCurrent()
            assertEquals(n%2==0,seen.last().turns.first().content.contains("NEW_LORE"))
            assertNotEquals(seen.first().requestId,seen.last().requestId)
            assertEquals("new",seen.last().config.model)
            coordinator.mutate(a.scope()){entries=emptyList()}
            assertEquals(MessageState.CANCELLED,base.states[seen.last().requestId])
            base.update(seen.last(),"late",MessageState.COMPLETE,"",1,1)
            assertEquals("partial",base.texts[seen.last().requestId])
            base.role=RoleEntity("b","B","B-SYSTEM")
            coordinator.send(base.role.scope(),"chat-b","next");runCurrent();advanceTimeBy(101);runCurrent()
            assertEquals(base.role.scope(),seen.last().scope);assertTrue(seen.last().worldbook.decisions.isEmpty())
            assertFalse(seen.last().turns.any {it.content.contains("LORE")})
        }
    }
    @Test fun twentyPersonaEditsFreezeInFlightAndRetryUsesCurrentSavedSetting()=runTest {
        repeat(20){n->
            val base=Store();base.role=base.role.copy(userName="old-name",userPersona="OLD_MASK",personaEnabled=true)
            val store=object:RequestStore by base {
                override suspend fun prepare(scope:RoleAccessScope,chatId:String,text:String,requestId:String):RequestSnapshot {
                    val snapshot=base.prepare(scope,chatId,text,requestId)
                    return snapshot.copy(turns=RequestContext.build(scope,snapshot.role,ChatEntity(chatId,scope.roleId,snapshot.config.id,"chat"),emptyList(),text))
                }
            }
            val seen=mutableListOf<RequestSnapshot>()
            val service=object:AIService {override fun sendMessage(request:RequestSnapshot,key:String)=flow{seen+=request;emit("partial");delay(100);emit("done")}}
            val coordinator=ChatCoordinator(store,service,credentials,backgroundScope)
            coordinator.send(base.role.scope(),"chat","hello");runCurrent()
            base.role=base.role.copy(userName="new-name",userPersona="NEW_MASK",personaEnabled=n%2==0,version=2)
            base.config=base.config.copy(version=2,model="new-model")
            coordinator.cancel()
            assertTrue(seen.single().turns.first().content.contains("OLD_MASK"));assertFalse(seen.single().turns.first().content.contains("NEW_MASK"))
            coordinator.send(base.role.scope(),"chat","hello");runCurrent();advanceTimeBy(101);runCurrent()
            val retry=seen.last();assertNotEquals(seen.first().requestId,retry.requestId)
            assertEquals(n%2==0,retry.turns.first().content.contains("NEW_MASK"));assertFalse(retry.turns.first().content.contains("OLD_MASK"))
            assertEquals(2L,retry.role.version);assertEquals("new-model",retry.config.model)
        }
    }
    @Test fun privacyLockBlocksNewRequestsAndStopsDelayedChunks()=runTest {
        val store=Store();var allowed=false;var networkCalls=0
        val service=object:AIService {override fun sendMessage(request:RequestSnapshot,key:String)=flow {
            networkCalls++;emit("partial");delay(100);emit("LATE")
        }}
        val coordinator=ChatCoordinator(store,service,credentials,backgroundScope,accessAllowed={allowed})
        try {coordinator.send(store.role.scope(),"c","blocked");fail()}catch(e:ChatFailure){assertEquals("LOCKED",e.code)}
        assertTrue(store.snapshots.isEmpty());assertEquals(0,networkCalls)
        allowed=true;coordinator.send(store.role.scope(),"c","start");runCurrent()
        allowed=false;advanceTimeBy(101);runCurrent()
        assertEquals("partial",store.texts.values.single());assertEquals(MessageState.CANCELLED,store.states.values.single())
    }
    @Test fun lockingWhilePreparingDoesNotReadCredentialOrStartNetwork()=runTest {
        val base=Store();var allowed=true
        val store=object:RequestStore by base {override suspend fun prepare(scope:RoleAccessScope,chatId:String,text:String,requestId:String):RequestSnapshot {
            val result=base.prepare(scope,chatId,text,requestId);allowed=false;return result
        }}
        val service=object:AIService{override fun sendMessage(request:RequestSnapshot,key:String)=flow<String>{fail("Network must not start")}}
        val coordinator=ChatCoordinator(store,service,object:Credentials{override fun read(ref:String,endpoint:String):String {fail("Credential must not be read");return ""}},backgroundScope,accessAllowed={allowed})
        try {coordinator.send(base.role.scope(),"c","queued");fail()}catch(e:ChatFailure){assertEquals("LOCKED",e.code)}
        assertEquals(MessageState.CANCELLED,base.states.values.single())
    }
    @Test fun rapidReunlockDoesNotReviveTwentyPreparedRequests()=runTest {
        repeat(20) {
            val base=Store();var epoch=0L
            val store=object:RequestStore by base {override suspend fun prepare(scope:RoleAccessScope,chatId:String,text:String,requestId:String):RequestSnapshot {
                val result=base.prepare(scope,chatId,text,requestId);epoch++;return result
            }}
            val service=object:AIService{override fun sendMessage(request:RequestSnapshot,key:String)=flow<String>{fail("Old request revived")}}
            val coordinator=ChatCoordinator(store,service,credentials,backgroundScope,accessAllowed={true},accessVersion={epoch})
            try {coordinator.send(base.role.scope(),"c","queued");fail()}catch(e:ChatFailure){assertEquals("LOCKED",e.code)}
            assertEquals(MessageState.CANCELLED,base.states.values.single())
        }
    }
    @Test fun cancelRetainsPartialAndRetryHasNewIdentity()=runTest {
        val store=Store()
        val service=object:AIService{override fun sendMessage(request:RequestSnapshot,key:String)=flow{emit("部分");awaitCancellation()}}
        val coordinator=ChatCoordinator(store,service,credentials,backgroundScope){testScheduler.currentTime}
        assertTrue(coordinator.send(store.role.scope(),"c","hello"));runCurrent()
        assertFalse(coordinator.send(store.role.scope(),"c","duplicate"))
        coordinator.cancel()
        val first=store.snapshots.first().requestId
        assertEquals(MessageState.CANCELLED,store.states[first]);assertEquals("部分",store.texts[first])
        assertTrue(coordinator.send(store.role.scope(),"c","hello"));runCurrent();coordinator.cancel()
        assertNotEquals(first,store.snapshots.last().requestId)
        assertNull(coordinator.busyChat.value)
    }
    @Test fun immediateCancellationLeavesTerminalState()=runTest {
        val store=Store();val service=object:AIService{override fun sendMessage(request:RequestSnapshot,key:String)=flow<String>{awaitCancellation()}}
        val coordinator=ChatCoordinator(store,service,credentials,backgroundScope)
        coordinator.send(store.role.scope(),"c","hello");coordinator.cancel()
        assertEquals(MessageState.CANCELLED,store.states.values.single());assertNull(coordinator.busyChat.value)
    }
    @Test fun requestSnapshotDoesNotChangeWhileGenerating()=runTest {
        val store=Store();var seen:RequestSnapshot?=null
        val service=object:AIService{override fun sendMessage(request:RequestSnapshot,key:String)=flow{delay(100);seen=request;emit("done")}}
        val coordinator=ChatCoordinator(store,service,credentials,backgroundScope){testScheduler.currentTime}
        coordinator.send(store.role.scope(),"c","hello")
        store.role=store.role.copy(prompt="changed",version=2);store.config=store.config.copy(model="new",version=2)
        advanceTimeBy(101);runCurrent()
        assertEquals("original",seen!!.role.prompt);assertEquals("old",seen!!.config.model)
        assertEquals(MessageState.COMPLETE,store.states.values.single())
    }
    @Test fun networkFailureKeepsPartialAndCannotBecomeCompleteLater()=runTest {
        val store=Store();val service=object:AIService{override fun sendMessage(request:RequestSnapshot,key:String)=flow{emit("partial");throw ChatFailure("NETWORK")}}
        val coordinator=ChatCoordinator(store,service,credentials,backgroundScope)
        coordinator.send(store.role.scope(),"c","hello");runCurrent()
        val id=store.snapshots.single().assistantId
        assertEquals(MessageState.FAILED,store.states[id]);assertEquals("NETWORK",store.errors[id]);assertEquals("partial",store.texts[id])
        store.update(store.snapshots.single(),"late",MessageState.COMPLETE,"",1,1)
        assertEquals("partial",store.texts[id])
    }

    @Test fun stalledReasoningTimesOutAndReleasesGeneration()=runTest {
        val store=Store();val service=object:AIService {
            override fun sendMessage(request:RequestSnapshot,key:String)=emptyFlow<String>()
            override fun sendEvents(request:RequestSnapshot,key:String)=flow {
                emit(ChatStreamEvent.Reasoning);delay(Long.MAX_VALUE)
            }
        }
        val coordinator=ChatCoordinator(store,service,credentials,backgroundScope)
        coordinator.send(store.role.scope(),"c","hello");runCurrent()
        assertTrue(coordinator.running.value!!.thinking)
        advanceTimeBy(store.snapshots.single().config.timeoutSeconds*1000L+1);runCurrent()
        val id=store.snapshots.single().assistantId
        assertEquals(MessageState.FAILED,store.states[id]);assertEquals("TIMEOUT",store.errors[id])
        assertNull(coordinator.running.value);assertNull(coordinator.busyChat.value);assertFalse(ModelGenerationGate.busy())
    }

    @Test fun twentySwitchCancelRetryAndDeletionRaces()=runTest {
        repeat(20) { round ->
            val store=Store()
            val a=store.role
            val seen=mutableListOf<RequestSnapshot>()
            val service=object:AIService {
                override fun sendMessage(request:RequestSnapshot,key:String)=flow {
                    seen.add(request); emit("partial"); delay(50); emit("end")
                }
            }
            val coordinator=ChatCoordinator(store,service,credentials,backgroundScope)
            coordinator.send(a.scope(),"chat-a","a-"+round); runCurrent()
            store.role=RoleEntity("b","B","b-secret")
            store.config=store.config.copy(model="changed",version=2)
            assertFalse(coordinator.send(store.role.scope(),"chat-b","busy"))
            if(round%2==0) coordinator.cancel() else { advanceTimeBy(51);runCurrent() }
            assertEquals(a.scope(),seen.first().scope)
            assertEquals("original",seen.first().role.prompt)
            val first=seen.first()
            assertEquals(if(round%2==0)MessageState.CANCELLED else MessageState.COMPLETE,store.states[first.requestId])
            // A retry is a new identity and freezes its own current configuration.
            coordinator.send(store.role.scope(),"chat-b","retry");runCurrent()
            var deleted=false
            coordinator.mutate(store.role.scope()) {
                assertEquals(MessageState.CANCELLED,store.states[seen.last().requestId])
                deleted=true
            }
            assertTrue(deleted)
            assertNotEquals(first.requestId,seen.last().requestId)
            assertEquals("chat-a",first.chatId)
            assertEquals("chat-b",seen.last().chatId)
            assertEquals("changed",seen.last().config.model)
            store.update(first,"late",MessageState.COMPLETE,"",0,0)
            assertEquals(if(round%2==0)"partial" else "partialend",store.texts[first.requestId])
        }
    }
}
