package com.ai.assistance.operit

import com.ai.assistance.operit.core.tools.*
import com.ai.assistance.operit.core.tools.mcp.*
import com.ai.assistance.operit.data.model.RoleAccessScope
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class ToolDispatcherTest {
    private val definition=McpToolDefinition("echo","Synthetic echo","{\"type\":\"object\"}")
    private val permission=ToolPermission("extension",1,1,"https://example.invalid/mcp","credential-reference",1_000,definition)
    private fun owner(role:String="A")=ToolRequestOwner(RoleAccessScope(role,"space-$role"),"chat-$role","request-$role","reply-$role")
    private fun call(id:String="call",role:String="A")=ToolInvocation(owner(role),permission,id,"{\"marker\":\"$role\"}")

    /** Behavioral contract fixture; actual Room queries and migration require their own integration tests. */
    private class Store(var expected:ToolRequestOwner):ToolInvocationStore {
        var allowed=true
        var version=1L
        val states=mutableMapOf<String,ToolInvocationState>()
        val results=mutableMapOf<String,String>()
        override suspend fun validate(call:ToolInvocation) {
            if(!allowed||call.owner!=expected||call.permission.grantVersion!=version)throw ToolFailure("ACCESS")
        }
        override suspend fun begin(call:ToolInvocation) {
            validate(call)
            if(states.containsKey(call.callId))throw ToolFailure("DUPLICATE")
            if(states.size>=ToolDispatcher.MAX_CALLS)throw ToolFailure("CALL_LIMIT")
            states[call.callId]=ToolInvocationState.WAITING_APPROVAL
        }
        override suspend fun running(call:ToolInvocation) {
            validate(call)
            if(states[call.callId]!=ToolInvocationState.WAITING_APPROVAL)throw ToolFailure("ACCESS")
            states[call.callId]=ToolInvocationState.RUNNING
        }
        override suspend fun finish(call:ToolInvocation,result:McpToolOutput) {
            validate(call)
            if(states[call.callId]!=ToolInvocationState.RUNNING)throw ToolFailure("ACCESS")
            states[call.callId]=if(result.isError)ToolInvocationState.FAILED else ToolInvocationState.COMPLETE
            results[call.callId]=result.json
        }
        override suspend fun terminate(call:ToolInvocation,state:ToolInvocationState,code:String) {
            if(call.owner==expected && states[call.callId] in setOf(ToolInvocationState.WAITING_APPROVAL,ToolInvocationState.RUNNING))states[call.callId]=state
        }
    }
    private inner class Session(private val beforeCall:suspend ()->Unit={}) : McpSession {
        var connects=0;var calls=0;var closed=false
        var discover:suspend ()->List<McpToolDefinition> = {listOf(definition)}
        override suspend fun connect() {connects++}
        override suspend fun listTools()=discover()
        override suspend fun callTool(name:String,arguments:String):McpToolOutput {
            calls++;beforeCall();return McpToolOutput(arguments,false)
        }
        override suspend fun close() {closed=true}
    }
    private suspend fun failure(code:String,block:suspend ()->Unit) {
        try {block();fail("Expected $code")}catch(e:ToolFailure){assertEquals(code,e.code)}
    }

    @Test fun deniedConfirmationAndWrongOwnerNeverOpenNetwork()=runBlocking {
        val store=Store(owner());var created=0
        val dispatcher=ToolDispatcher(store,{created++;Session()},{false})
        failure("DECLINED"){dispatcher.execute(call())}
        failure("ACCESS"){dispatcher.execute(call("other","B"))}
        assertEquals(0,created);assertEquals(ToolInvocationState.FAILED,store.states["call"])
    }
    @Test fun privacyLockRejectsBeforeDiscoveryBeforeExecutionAndBeforeResultWrite()=runBlocking {
        repeat(4){stage->
            var allowed=stage!=0;val store=Store(owner());var sessions=0
            val session=Session {if(stage==3)allowed=false}
            session.discover={if(stage==2)allowed=false;listOf(definition)}
            val dispatcher=ToolDispatcher(store,{sessions++;session},{if(stage==1)allowed=false;true},executionAllowed={allowed})
            failure("LOCKED"){dispatcher.execute(call())}
            assertEquals(if(stage<=1)0 else 1,sessions)
            assertEquals(if(stage==3)1 else 0,session.calls)
            assertTrue(store.results.isEmpty())
        }
    }
    @Test fun rapidReunlockDuringDiscoveryRejectsOldToolTicket()=runBlocking {
        repeat(20) {
            var epoch=0L;val store=Store(owner());val session=Session()
            session.discover={epoch++;listOf(definition)}
            val dispatcher=ToolDispatcher(store,{session},{true},executionAllowed={true},accessVersion={epoch})
            failure("LOCKED"){dispatcher.execute(call())}
            assertEquals(0,session.calls);assertTrue(store.results.isEmpty())
        }
    }

    @Test fun completionCannotBeReplayedAndBudgetCannotBeExceeded()=runBlocking {
        val store=Store(owner());var calls=0
        val dispatcher=ToolDispatcher(store,{Session {calls++}},{true})
        repeat(8) {assertEquals(call().arguments,dispatcher.execute(call("call-$it")).json)}
        failure("DUPLICATE"){dispatcher.execute(call("call-0"))}
        failure("CALL_LIMIT"){dispatcher.execute(call("call-9"))}
        assertEquals(8,calls);assertTrue(store.states.values.all {it==ToolInvocationState.COMPLETE})
    }

    @Test fun revocationDuringConfirmationOrDiscoveryPreventsInvocation()=runBlocking {
        for(duringDiscovery in listOf(false,true)) {
            val store=Store(owner());val session=Session()
            session.discover={store.allowed=false;listOf(definition)}
            val dispatcher=ToolDispatcher(store,{session},{if(!duringDiscovery)store.allowed=false;true})
            failure("ACCESS"){dispatcher.execute(call())}
            assertEquals(0,session.calls)
            if(duringDiscovery)assertTrue(session.closed)else assertEquals(0,session.connects)
        }
    }

    @Test fun changedToolSchemaIsRejectedBeforeCall()=runBlocking {
        val store=Store(owner());val session=Session()
        session.discover={listOf(definition.copy(inputSchema="{\"type\":\"object\",\"required\":[\"secret\"]}"))}
        failure("TOOL_CHANGED"){ToolDispatcher(store,{session},{true}).execute(call())}
        assertEquals(0,session.calls);assertTrue(session.closed)
    }

    @Test fun twentyCancellationRevocationAndLateResultRoundsStayTerminal()=runBlocking {
        repeat(20) { index->
            supervisorScope {
            val store=Store(owner());val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
            // Deliberately hostile adapter delivers after cancellation: dispatcher must still reject it.
            val session=Session {entered.complete(Unit);withContext(NonCancellable){release.await()}}
            val dispatcher=ToolDispatcher(store,{session},{true})
            val result=async {dispatcher.execute(call())}
            entered.await()
            if(index%2==0) {result.cancel();release.complete(Unit);result.join();assertTrue(result.isCancelled)}
            else {store.version++;release.complete(Unit);failure("ACCESS"){result.await()}}
            assertTrue(store.results.isEmpty());assertTrue(session.closed)
            assertEquals(if(index%2==0)ToolInvocationState.CANCELLED else ToolInvocationState.FAILED,store.states["call"])
            }
        }
    }

    @Test fun fiftyRoleAlternationsKeepOnlyFrozenOwnerArguments()=runBlocking {
        val stores=mapOf("A" to Store(owner("A")),"B" to Store(owner("B")))
        val seen=mutableListOf<Pair<String,String>>()
        repeat(50) { index->
            val role=if(index%2==0)"A"else"B"
            // Each turn is a new request; real storage enforces the per-request call budget.
            val request=owner(role).copy(requestId="request-$index",assistantId="reply-$index")
            val store=stores.getValue(role);store.expected=request;store.states.clear()
            val invocation=call("call-$index",role).copy(owner=request)
            val runtime=object:McpSession {
                override suspend fun connect() {}
                override suspend fun listTools()=listOf(definition)
                override suspend fun callTool(name:String,arguments:String):McpToolOutput {
                    seen+=role to arguments;return McpToolOutput(arguments,false)
                }
                override suspend fun close() {}
            }
            val dispatcher=ToolDispatcher(store,{runtime},{true})
            dispatcher.execute(invocation)
            failure("ACCESS"){dispatcher.execute(invocation.copy(callId="foreign-$index",owner=owner(if(role=="A")"B"else"A")))}
        }
        assertEquals(50,seen.size)
        assertTrue(seen.all { (role,args)->args=="{\"marker\":\"$role\"}" })
    }

    @Test fun unreadableCredentialsFailBeforeNetworkWithoutLosingErrorCategory()=runBlocking {
        val store=Store(owner())
        val dispatcher=ToolDispatcher(store,{throw com.ai.assistance.operit.data.model.ChatFailure("CREDENTIAL")},{true})
        failure("CREDENTIAL"){dispatcher.execute(call())}
        assertEquals(ToolInvocationState.FAILED,store.states["call"])
    }

    @Test fun cancellationWhileWaitingForConfirmationNeverCreatesSession()=runBlocking {
        val store=Store(owner());val waiting=CompletableDeferred<Unit>();var created=0
        val dispatcher=ToolDispatcher(store,{created++;Session()},{waiting.complete(Unit);awaitCancellation()})
        val job=launch {dispatcher.execute(call())}
        waiting.await();job.cancelAndJoin()
        assertEquals(0,created);assertEquals(ToolInvocationState.CANCELLED,store.states["call"])
    }
}
