package com.ai.assistance.operit

import com.ai.assistance.operit.api.chat.*
import com.ai.assistance.operit.api.chat.llmprovider.AIService
import com.ai.assistance.operit.data.model.*
import com.ai.assistance.operit.data.security.Credentials
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OwnedChatRequestTest {
    private val credentials=object:Credentials{override fun read(ref:String,endpoint:String)="synthetic-key"}

    @Test fun twentyActualRequestsReturnTheirPersistedReplyAndFrozenConfiguration()=runTest {
        val store=ChatCoordinatorTest.Store()
        val service=object:AIService{override fun sendMessage(request:RequestSnapshot,key:String)=flow {
            emit("你好🙂 ");delay(100);emit(request.config.model)
        }}
        val coordinator=ChatCoordinator(store,service,credentials,backgroundScope)
        repeat(20){n->
            store.config=store.config.copy(model="model-$n",version=n+1L)
            val task=async{coordinator.sendAndAwait(store.role.scope(),"chat","turn-$n")}
            runCurrent()
            store.config=store.config.copy(model="edited-during-request")
            advanceTimeBy(101);runCurrent()
            val result=task.await();val snapshot=store.snapshots.last()
            assertEquals(snapshot.assistantId,result.assistantId)
            assertEquals(snapshot.requestId,result.requestId)
            assertEquals(snapshot.scope,result.scope)
            assertEquals("chat",result.chatId)
            assertEquals("你好🙂 model-$n",result.text)
            assertEquals(store.texts[result.assistantId],result.text)
            assertEquals(MessageState.COMPLETE,result.state)
            assertFalse(ModelGenerationGate.busy())
        }
    }

    @Test fun cancellingTheWaitingVoiceTaskPersistsPartialAndStopsItsNetwork()=runTest {
        val store=ChatCoordinatorTest.Store();var networkStopped=false
        val service=object:AIService{override fun sendMessage(request:RequestSnapshot,key:String)=flow {
            try {emit("部分🙂");awaitCancellation()}finally{networkStopped=true}
        }}
        val coordinator=ChatCoordinator(store,service,credentials,backgroundScope)
        val waiting=launch{coordinator.sendAndAwait(store.role.scope(),"same-chat","voice")}
        runCurrent();waiting.cancelAndJoin()
        assertTrue(networkStopped)
        assertEquals(MessageState.CANCELLED,store.states.values.single())
        assertEquals("部分🙂",store.texts.values.single());assertFalse(ModelGenerationGate.busy())
        assertTrue(coordinator.send(store.role.scope(),"same-chat","new request"))
        runCurrent();waiting.cancel();runCurrent()
        assertEquals(MessageState.GENERATING,store.states[store.snapshots.last().requestId])
        coordinator.cancel()
    }

    @Test fun busyVoiceRequestCannotCancelAnExistingTextRequest()=runTest {
        val store=ChatCoordinatorTest.Store()
        val service=object:AIService{override fun sendMessage(request:RequestSnapshot,key:String)=flow {emit("text");awaitCancellation()}}
        val coordinator=ChatCoordinator(store,service,credentials,backgroundScope)
        coordinator.send(store.role.scope(),"chat","first");runCurrent()
        try {coordinator.sendAndAwait(store.role.scope(),"chat","voice");fail()}catch(e:ChatFailure){assertEquals("MODEL_GENERATION_BUSY",e.code)}
        assertEquals(1,store.snapshots.size);assertEquals(MessageState.GENERATING,store.states.values.single())
        coordinator.cancel()
    }

    @Test fun externalCancellationReturnsExactCancelledResultInsteadOfWaitingForAnotherChat()=runTest {
        val store=ChatCoordinatorTest.Store()
        val service=object:AIService{override fun sendMessage(request:RequestSnapshot,key:String)=flow {emit("kept");awaitCancellation()}}
        val coordinator=ChatCoordinator(store,service,credentials,backgroundScope)
        val waiting=async{coordinator.sendAndAwait(store.role.scope(),"old-chat","voice")}
        runCurrent();coordinator.cancel();runCurrent()
        coordinator.send(store.role.scope(),"new-chat","new");runCurrent()
        val result=waiting.await()
        assertEquals("old-chat",result.chatId);assertEquals("kept",result.text);assertEquals(MessageState.CANCELLED,result.state)
        assertEquals("new-chat",coordinator.busyChat.value)
        coordinator.cancel()
    }

    @Test fun failedPersistenceCannotReturnSuccessfulSpeechText()=runTest {
        val store=object:ChatCoordinatorTest.Store(){
            override suspend fun complete(snapshot:RequestSnapshot,text:String,state:MessageState,error:String,first:Long,elapsed:Long){throw ChatFailure("STORAGE")}
        }
        val service=object:AIService{override fun sendMessage(request:RequestSnapshot,key:String)=flowOf("not persisted")}
        val coordinator=ChatCoordinator(store,service,credentials,backgroundScope)
        try {coordinator.sendAndAwait(store.role.scope(),"chat","voice");fail()}catch(e:ChatFailure){assertEquals("STORAGE",e.code)}
        assertFalse(ModelGenerationGate.busy())
    }

    @Test fun failedCancellationWriteAfterPrivacyChangeStillReleasesGenerationGate()=runTest {
        var allowed=true
        val store=object:ChatCoordinatorTest.Store(){
            override suspend fun prepare(scope:RoleAccessScope,chatId:String,text:String,requestId:String):RequestSnapshot {
                val snapshot=super.prepare(scope,chatId,text,requestId);allowed=false;return snapshot
            }
            override suspend fun complete(snapshot:RequestSnapshot,text:String,state:MessageState,error:String,first:Long,elapsed:Long){throw ChatFailure("STORAGE")}
        }
        val service=object:AIService{override fun sendMessage(request:RequestSnapshot,key:String)=flow<String>{fail("No network permitted")}}
        val coordinator=ChatCoordinator(store,service,credentials,backgroundScope,accessAllowed={allowed})
        try {coordinator.sendAndAwait(store.role.scope(),"chat","voice");fail()}catch(e:ChatFailure){assertEquals("STORAGE",e.code)}
        assertFalse(ModelGenerationGate.busy())
    }

    @Test fun cancellationPersistenceFailureReachesWaiterWithoutCrashingWorker()=runTest {
        val store=object:ChatCoordinatorTest.Store(){
            override suspend fun complete(snapshot:RequestSnapshot,text:String,state:MessageState,error:String,first:Long,elapsed:Long){throw ChatFailure("STORAGE")}
        }
        val service=object:AIService{override fun sendMessage(request:RequestSnapshot,key:String)=flow {emit("partial");awaitCancellation()}}
        val coordinator=ChatCoordinator(store,service,credentials,backgroundScope)
        var failure:String?=null
        val waiting=launch {
            try {coordinator.sendAndAwait(store.role.scope(),"chat","voice");fail()}
            catch(e:ChatFailure){failure=e.code}
        }
        runCurrent();coordinator.cancel();waiting.join()
        assertEquals("STORAGE",failure)
        assertNull(coordinator.running.value);assertFalse(ModelGenerationGate.busy())
    }
}
