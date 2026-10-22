package com.ai.assistance.operit

import com.ai.assistance.operit.api.chat.*
import com.ai.assistance.operit.api.chat.llmprovider.*
import com.ai.assistance.operit.core.tools.*
import com.ai.assistance.operit.core.tools.mcp.*
import com.ai.assistance.operit.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import okhttp3.tls.*
import org.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.InputStream
import java.util.concurrent.CopyOnWriteArrayList

class ToolChatServiceTest {
    @Test fun rawSnapshotCannotSmuggleToolsAndUnexpectedModelCallsNeverExecute()=runBlocking {
        var calls=0;var executions=0
        val p=object:ModelRoundService {
            override fun streamRound(request:RequestSnapshot,key:String,messages:JSONArray,tools:JSONArray)=flow {
                calls++;assertEquals(0,tools.length())
                emit(ModelRoundEvent.End(listOf(ModelToolCall("call","forged","{}"))))
            }
        }
        val service=ToolChatService(p){executions++;error("Raw mode must not execute tools")}
        val source=snapshot();val raw=source.copy(role=source.role.copy(conversationMode="raw"))
        failure("ACCESS"){service.sendMessage(raw,"key").collect()}
        assertEquals(0,calls);assertEquals(0,executions)
        failure("TOOLS_DISABLED"){service.sendMessage(raw.copy(tools=emptyList()),"key").collect()}
        assertEquals(1,calls);assertEquals(0,executions)
    }
    @Test fun protocolLoggingNeverEvaluatesPayloadLambdas() {
        assertNull(System.getProperty("kotlin-logging-to-android-native"))
        assertEquals("org.slf4j.helpers.NOPLoggerFactory",org.slf4j.LoggerFactory.getILoggerFactory().javaClass.name)
        val logger=io.github.oshai.kotlinlogging.KotlinLogging.logger("mcp-privacy-test")
        assertFalse(logger.isTraceEnabled());assertFalse(logger.isDebugEnabled())
        assertFalse(logger.isInfoEnabled());assertFalse(logger.isWarnEnabled());assertFalse(logger.isErrorEnabled())
        logger.error { fail("Protocol payload must not be evaluated for logging");"synthetic-private-payload" }
    }
    private val permission=ToolPermission("ext",1,1,"https://example.invalid/mcp","mcp-ref",3000,McpToolDefinition("echo","合成工具","{\"type\":\"object\"}"))
    private fun snapshot(p:ToolPermission=permission,endpoint:String="https://example.invalid/v1/chat/completions")=RequestSnapshot("request-A","chat-A","reply-A",RoleEntity("A","A","persona-A",spaceId="space-A"),
        ModelConfigData("config","test","OpenAI 兼容",endpoint,"test",15,"model-ref"),listOf(PromptTurn("system","persona-A"),PromptTurn("user","A-marker 中文🙂")),tools=listOf(p))
    private fun frame(delta:JSONObject,finish:String?=null)="data: "+JSONObject().put("choices",JSONArray().put(JSONObject().put("delta",delta).apply {finish?.let {put("finish_reason",it)}}))+"\n\n"
    private fun callDelta(name:String,args:String,id:String?="call",index:Int=0)=JSONObject().put("tool_calls",JSONArray().put(JSONObject().put("index",index).apply {id?.let {put("id",it);put("type","function")}}
        .put("function",JSONObject().put("name",name).put("arguments",args))))
    private suspend fun failure(code:String,block:suspend ()->Unit) {
        try {block();fail("Expected $code")}catch(e:ToolFailure){assertEquals(code,e.code)}catch(e:ChatFailure){assertEquals(code,e.code)}
    }
    private fun provider(block:suspend kotlinx.coroutines.flow.FlowCollector<ModelRoundEvent>.(Int,JSONArray)->Unit):ModelRoundService=object:ModelRoundService {
        var round=0
        override fun streamRound(request:RequestSnapshot,key:String,messages:JSONArray,tools:JSONArray)=flow {block(++round,messages)}
    }

    @Test fun eachToolRoundEmitsCompletion()=runBlocking {
        var round=0
        val source=object:ModelRoundService {override fun streamRound(request:RequestSnapshot,key:String,messages:JSONArray,tools:JSONArray)=flow {
            round++
            if(round==1)emit(ModelRoundEvent.End(listOf(ModelToolCall("call",ToolChatService.alias(permission),"{}"))))
            else {emit(ModelRoundEvent.Text("完成"));emit(ModelRoundEvent.End(emptyList()))}
        }}
        val events=ToolChatService(source){McpToolOutput("{}",false)}.sendEvents(snapshot(),"key").toList()
        assertEquals(2,events.filterIsInstance<ChatStreamEvent.RoundEnd>().size)
        assertEquals("完成",events.filterIsInstance<ChatStreamEvent.Text>().joinToString(""){it.value})
    }

    @Test fun decoderAccumulatesChineseEmojiAndReasoningAcrossSingleBytes() {
        val text=frame(JSONObject().put("reasoning_content","provider reasoning"))+frame(callDelta("tool_","{\"text\":\"你好"))+
            frame(callDelta("alias","🙂\"}",null))+frame(JSONObject(),"tool_calls")
        val bytes=text.byteInputStream()
        val single=object:InputStream(){override fun read()=bytes.read();override fun read(b:ByteArray,off:Int,len:Int)=bytes.read(b,off,minOf(len,1))}
        val end=SseDecoder.decodeRound(single,true){fail("Tool arguments are not chat text")}
        assertEquals(ModelToolCall("call","tool_alias","{\"text\":\"你好🙂\"}"),end.calls.single())
        assertEquals("provider reasoning",end.reasoning)
    }

    @Test fun pendingToolArgumentsWithoutToolFinishAreNeverExecuted()=runBlocking {
        failure("TRUNCATED"){SseDecoder.decodeRound((frame(callDelta("tool","{"))+"data: [DONE]\n\n").byteInputStream(),true){fail()}}
        failure("PROTOCOL"){SseDecoder.decodeRound((frame(callDelta("tool","{}"))+frame(JSONObject(),"stop")).byteInputStream(),true){fail()}}
    }

    @Test fun optionalNullToolFieldsDoNotBreakPlainTextOrArgumentChunks() {
        val plain=frame(JSONObject().put("content","好").put("tool_calls",JSONObject.NULL).put("function_call",JSONObject.NULL),"stop")
        val text=StringBuilder();SseDecoder.decode(plain.byteInputStream()){text.append(it)};assertEquals("好",text.toString())
        val next=JSONObject().put("tool_calls",JSONArray().put(JSONObject().put("index",0).put("id",JSONObject.NULL).put("type",JSONObject.NULL)
            .put("function",JSONObject().put("name",JSONObject.NULL).put("arguments","}"))))
        val end=SseDecoder.decodeRound((frame(callDelta("alias","{"))+frame(next,"tool_calls")).byteInputStream(),true){fail()}
        assertEquals("{}",end.calls.single().arguments)
    }

    @Test fun invalidArgumentsInLaterCallRejectWholeBatch()=runBlocking {
        var called=0
        val alias=ToolChatService.alias(permission)
        val service=ToolChatService(provider {_,_->emit(ModelRoundEvent.End(listOf(ModelToolCall("one",alias,"{}"),ModelToolCall("two",alias,"{"))))}){called++;McpToolOutput("{}",false)}
        failure("ARGUMENTS"){service.sendMessage(snapshot(),"key").collect()};assertEquals(0,called)
    }

    @Test fun toolErrorReturnsToModelAsResultWithoutAutomaticRetry()=runBlocking {
        var called=0
        val service=ToolChatService(provider {round,messages->
            if(round==1)emit(ModelRoundEvent.End(listOf(ModelToolCall("call",ToolChatService.alias(permission),"{}"))))
            else {
                val result=messages.getJSONObject(messages.length()-1)
                assertEquals("call",result.getString("tool_call_id"));assertTrue(JSONObject(result.getString("content")).getBoolean("isError"))
                emit(ModelRoundEvent.Text("工具未完成。"));emit(ModelRoundEvent.End(emptyList()))
            }
        }){called++;McpToolOutput("{\"isError\":true,\"content\":[]}",true)}
        assertEquals("工具未完成。",service.sendMessage(snapshot(),"key").toList().joinToString(""));assertEquals(1,called)
    }

    @Test fun entireBatchMustBeAuthorizedBeforeFirstExternalCall()=runBlocking {
        var called=0
        val service=ToolChatService(provider {_,_->emit(ModelRoundEvent.End(listOf(ModelToolCall("one",ToolChatService.alias(permission),"{}"),ModelToolCall("two","unknown","{}"))))}){called++;McpToolOutput("{}",false)}
        failure("ACCESS"){service.sendMessage(snapshot(),"key").collect()}
        assertEquals(0,called)
    }

    @Test fun repeatedCallIdAndNinthCallAreRejected()=runBlocking {
        for(repeatId in listOf(true,false)) {
            var called=0
            val service=ToolChatService(provider {round,_->emit(ModelRoundEvent.End(listOf(ModelToolCall(if(repeatId)"same"else"call-$round",ToolChatService.alias(permission),"{}"))))}){called++;McpToolOutput("{}",false)}
            failure(if(repeatId)"DUPLICATE"else"CALL_LIMIT"){service.sendMessage(snapshot(),"key").collect()}
            assertEquals(if(repeatId)1 else 8,called)
        }
    }

    @Test fun cancellationDiscardsLateToolResultBeforeNextModelRound()=runBlocking {
        var rounds=0;val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        val service=ToolChatService(provider {_,_->rounds++;emit(ModelRoundEvent.End(listOf(ModelToolCall("call",ToolChatService.alias(permission),"{}"))))}) {
            entered.complete(Unit);withContext(NonCancellable){release.await()};McpToolOutput("LATE",false)
        }
        val job=launch {service.sendMessage(snapshot(),"key").collect {fail()}}
        entered.await();job.cancel();release.complete(Unit);job.join()
        assertTrue(job.isCancelled);assertEquals(1,rounds)
    }

    @Test fun staleApprovalCannotAuthorizeAnotherRequestAcrossTwentyRounds()=runBlocking {
        val gate=ToolApprovalGate();var old="old"
        repeat(20){n->
            val call=ToolInvocation(ToolRequestOwner(RoleAccessScope("A","space-A"),"chat","r-$n","a-$n"),permission,"call","{}")
            val answer=async {gate.await(call)}
            val ticket=gate.pending.filterNotNull().first()
            assertFalse(gate.answer(old,true))
            if(n%2==0){answer.cancelAndJoin()}else {assertTrue(gate.answer(ticket.id,true));assertTrue(answer.await())}
            assertNull(gate.pending.value);assertFalse(gate.answer(ticket.id,true));old=ticket.id
        }
    }

    @Test fun localHttpsModelMcpModelRoundTripUsesSeparateCredentialsAndMatchingCallIds()=runBlocking {
        val cert=HeldCertificate.Builder().addSubjectAlternativeName("localhost").addSubjectAlternativeName("127.0.0.1").build()
        val tls=HandshakeCertificates.Builder().heldCertificate(cert).build()
        val trust=HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        val client=OkHttpClient.Builder().proxy(java.net.Proxy.NO_PROXY).sslSocketFactory(trust.sslSocketFactory(),trust.trustManager).build()
        MockWebServer().use {server->
            server.useHttps(tls.sslSocketFactory(),false);server.start()
            val requests=CopyOnWriteArrayList<RecordedRequest>();var modelRound=0
            server.dispatcher=object:Dispatcher(){override fun dispatch(r:RecordedRequest):MockResponse {
                requests.add(r)
                if(r.method!="POST")return MockResponse().setResponseCode(405)
                val json=JSONObject(r.body.clone().readUtf8())
                if(r.path=="/v1/chat/completions") {
                    modelRound++
                    val alias=json.getJSONArray("tools").getJSONObject(0).getJSONObject("function").getString("name")
                    return MockResponse().setHeader("Content-Type","text/event-stream").setBody(if(modelRound==1)
                        frame(callDelta(alias,"{\"text\":\"你好🙂\"}"))+frame(JSONObject(),"tool_calls")
                        else frame(JSONObject().put("content","已收到工具结果。"),"stop"))
                }
                if(!json.has("id"))return MockResponse().setResponseCode(202)
                val result=when(json.getString("method")) {
                    "initialize"->JSONObject().put("protocolVersion","2025-11-25").put("capabilities",JSONObject().put("tools",JSONObject())).put("serverInfo",JSONObject().put("name","fixture").put("version","1"))
                    "tools/list"->JSONObject().put("tools",JSONArray().put(JSONObject().put("name","echo").put("description","合成工具").put("inputSchema",JSONObject().put("type","object"))))
                    "tools/call"->JSONObject().put("content",JSONArray().put(JSONObject().put("type","text").put("text","你好🙂")))
                    else->JSONObject()
                }
                return MockResponse().setHeader("Content-Type","application/json").setBody(JSONObject().put("jsonrpc","2.0").put("id",json.get("id")).put("result",result).toString())
            }}
            withTimeout(15_000) {
                val endpoint=server.url("/mcp").toString()
                val probe=RemoteMcpSession(endpoint,"mcp-key",3000,client)
                probe.connect();val definition=probe.listTools().single();probe.close()
                val p=permission.copy(endpoint=endpoint,definition=definition)
                val snapshot=snapshot(p,server.url("/v1/chat/completions").toString())
                var completed=false;var confirmed=false
                val store=object:ToolInvocationStore {
                    override suspend fun begin(call:ToolInvocation){assertEquals(snapshot.scope,call.owner.scope);assertEquals(snapshot.requestId,call.owner.requestId)}
                    override suspend fun validate(call:ToolInvocation){}
                    override suspend fun running(call:ToolInvocation){assertTrue(confirmed)}
                    override suspend fun finish(call:ToolInvocation,result:McpToolOutput){completed=true}
                    override suspend fun terminate(call:ToolInvocation,state:ToolInvocationState,code:String){fail(code)}
                }
                val dispatcher=ToolDispatcher(store,{RemoteMcpSession(it.endpoint,"mcp-key",it.timeoutMs,client)},{confirmed=true;true})
                val text=ToolChatService(OpenAIProvider(client),dispatcher::execute).sendMessage(snapshot,"model-key").toList().joinToString("")
                assertEquals("已收到工具结果。",text);assertTrue(completed)
                val model=requests.filter {it.path=="/v1/chat/completions"};assertEquals(2,model.size)
                assertTrue(model.all {it.getHeader("Authorization")=="Bearer model-key"})
                assertTrue(requests.filter {it.path=="/mcp"}.all {it.getHeader("Authorization")=="Bearer mcp-key"})
                val back=JSONObject(model.last().body.clone().readUtf8()).getJSONArray("messages")
                val result=back.getJSONObject(back.length()-1)
                assertEquals("tool",result.getString("role"));assertEquals("call",result.getString("tool_call_id"));assertTrue(result.getString("content").contains("你好🙂"))
                assertFalse(back.toString().contains("B-marker"));assertFalse(back.toString().contains("mcp-key"))
                assertEquals("call",back.getJSONObject(back.length()-2).getJSONArray("tool_calls").getJSONObject(0).getString("id"))
            }
        }
    }
}
