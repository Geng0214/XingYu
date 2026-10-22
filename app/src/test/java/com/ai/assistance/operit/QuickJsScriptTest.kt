package com.ai.assistance.operit

import com.ai.assistance.operit.core.tools.javascript.ToolPkgScript
import com.ai.assistance.operit.core.tools.packTool.*
import com.ai.assistance.operit.core.tools.*
import com.ai.assistance.operit.core.tools.mcp.*
import com.ai.assistance.operit.api.chat.*
import com.ai.assistance.operit.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** Actual Kotlin adapter + official same-version Windows CLI. Does not test the Android JNI boundary. */
class QuickJsScriptTest {
    @get:Rule val temp=TemporaryFolder()
    private val repo=File(requireNotNull(System.getProperty("companion.repo")))
    private fun execute(files:Map<String,String>,entry:String="main.js",args:String="{}",name:String="test",mainEntry:String?=null,registrationOnly:Boolean=false):JSONObject {
        val cache=File(repo,".local/.gradle-m0/companion-tools/quickjs-2026-06-04")
        val expected=mapOf("qjs.exe" to "433a35a59bd6ff8950c57e6c7e809cae3fa01302ff673c121488b45f738afe3a",
            "libwinpthread-1.dll" to "1933c3f02ede171b7d9432204a89dfbdd846b86819df3040b0804fe1d02a8b16")
        for((file,hash) in expected) {
            val input=File(cache,file)
            check(input.isFile){"Run .venv/Scripts/python.exe ci/script/prepare_quickjs_test.py before host tests"}
            assertEquals(hash,MessageDigest.getInstance("SHA-256").digest(input.readBytes()).joinToString(""){"%02x".format(it)})
        }
        val script=ToolPkgScript.build(files,entry,name,args,mainEntry,registrationOnly)
        val fixture=temp.newFile("fixture-${System.nanoTime()}.js")
        fixture.writeText(script+".then(value=>print(JSON.stringify({value})),error=>print(JSON.stringify({error:error.message})));",Charsets.UTF_8)
        val output=temp.newFile("output-${System.nanoTime()}.txt")
        val process=ProcessBuilder(File(cache,"qjs.exe").absolutePath,"--memory-limit","33554432","--stack-size","524288",fixture.absolutePath)
            .directory(temp.root).redirectErrorStream(true).redirectOutput(output).start()
        if(!process.waitFor(5,TimeUnit.SECONDS)){process.destroyForcibly();throw AssertionError("QuickJS host fixture timeout")}
        assertEquals(output.readText(),0,process.exitValue())
        return JSONObject(output.readText(Charsets.UTF_8))
    }
    @Test fun actualEnginePreservesArgumentsWithoutCodeInjection() {
        val value="你好🙂\u0000\"}); globalThis.injection='BAD'; //"
        val result=execute(mapOf("main.js" to "exports.test=async p=>({text:p.text,injected:typeof globalThis.injection});"),args=JSONObject().put("text",value).toString()).getJSONObject("value")
        assertEquals(value,result.getString("text"));assertEquals("undefined",result.getString("injected"))
    }
    @Test fun actualEngineResolvesCommonJsWithinPackageAndSupportsComplete() {
        val files=mapOf("main.js" to "const m=require('./lib/echo');exports.test=async p=>complete(await m.echo(p));",
            "lib/echo.js" to "const c=require('../constant');exports.echo=async p=>p.value+c.value;",
            "constant.js" to "module.exports={value:'🙂'};")
        assertEquals("你好🙂",execute(files,args="{\"value\":\"你好\"}").getString("value"))
        for(request in listOf("fs","../../escape","./bad\\name","./bad:name")) {
            val result=execute(mapOf("main.js" to "exports.test=()=>require(${JSONObject.quote(request)});"))
            assertEquals("MODULE_PATH",result.getString("error"))
        }
    }
    @Test fun originalOperitTimeExampleReallyRuns() {
        val files=mapOf("time.js" to File(repo,"app/src/main/assets/toolpkg/time/packages/time.js").readText())
        val before=System.currentTimeMillis()
        val time=execute(files,"time.js",name="get_time").getJSONObject("value")
        assertTrue(time.getLong("timestamp") in before..System.currentTimeMillis())
        assertTrue(time.getString("iso").endsWith("Z"))
        val formatted=execute(files,"time.js",name="format_time").getJSONObject("value")
        assertTrue(formatted.getString("time24h").matches(Regex("[0-9]{2}:[0-9]{2}:[0-9]{2}")))
    }
    @Test fun missingFunctionDuplicateCompleteAndMissingResultAreRejected() {
        assertEquals("FUNCTION_MISSING",execute(mapOf("main.js" to "exports.other=()=>1;")).getString("error"))
        assertEquals("DUPLICATE_COMPLETE",execute(mapOf("main.js" to "exports.test=()=>{complete(1);complete(2);};")).getString("error"))
        assertEquals("RESULT_MISSING",execute(mapOf("main.js" to "exports.test=()=>{};")).getString("error"))
    }
    @Test fun inputBoundsRejectMalformedParametersAndIllegalPaths() {
        fun rejected(block:()->Unit) {try {block();fail("Expected rejection")}catch(_:IllegalArgumentException){}}
        for(path in listOf("/main.js","../main.js","a/../main.js","a\\main.js","C:/main.js","a//b.js")) {
            rejected {ToolPkgScript.build(mapOf(path to ""),path,"test","{}")}
        }
        rejected {ToolPkgScript.build(mapOf("main.js" to ""),"main.js","test","{\"a\":1,\"a\":2}")}
        rejected {ToolPkgScript.build(mapOf("main.js" to "x".repeat(1024*1024+1)),"main.js","test","{}")}
        rejected {ToolPkgScript.build(mapOf("main.js" to ""),"main.js","x'); bad(); //","{}")}
    }
    @Test fun registrationIsExplicitAndUnsupportedHostCapabilitiesAreDenied() {
        val files=mapOf("main.js" to "exports.registerToolPkg=()=>{globalThis.registered=true;};",
            "tool.js" to "exports.test=()=>({registered:globalThis.registered,old:typeof globalThis.old});")
        assertTrue(execute(files,name="registerToolPkg",registrationOnly=true).getBoolean("value"))
        val result=execute(files,entry="tool.js",mainEntry="main.js").getJSONObject("value")
        assertTrue(result.getBoolean("registered"));assertEquals("undefined",result.getString("old"))
        for(host in listOf("Tools.Network","ToolPkg.registerAppLifecycleHook")) {
            val rejected=execute(mapOf("main.js" to "exports.registerToolPkg=()=>{$host();};"),name="registerToolPkg",registrationOnly=true)
            assertEquals("CAPABILITY_DENIED",rejected.getString("error"))
        }
    }
    @Test fun modelDispatcherLocalPackageEngineAndModelRoundTrip()=runBlocking {
        val assetRoot=File(repo,"app/src/main/assets/toolpkg/time")
        val bundle=ToolPkgCodec.decode(ToolPkgCodec.timeExample {File(assetRoot,it).readBytes()})
        val definition=bundle.tools.first {it.name=="time.get_time"}.definition
        val permission=ToolPermission("local",1,1,"toolpkg:sha256:${bundle.sha256}",null,10000,definition)
        val request=RequestSnapshot("request-A","chat-A","reply-A",RoleEntity("A","A","persona-A",spaceId="space-A"),
            ModelConfigData("config","test","OpenAI 兼容","https://example.invalid/v1/chat/completions","test",15,"model-ref"),
            listOf(PromptTurn("user","现在几点")),tools=listOf(permission))
        var state="";var confirmations=0;var executions=0
        val store=object:ToolInvocationStore {
            override suspend fun begin(call:ToolInvocation){assertEquals(request.scope,call.owner.scope);assertEquals("",state);state="WAITING"}
            override suspend fun validate(call:ToolInvocation){assertEquals(request.requestId,call.owner.requestId);assertEquals(permission,call.permission)}
            override suspend fun running(call:ToolInvocation){assertEquals("WAITING",state);state="RUNNING"}
            override suspend fun finish(call:ToolInvocation,result:McpToolOutput){assertEquals("RUNNING",state);state="COMPLETE"}
            override suspend fun terminate(call:ToolInvocation,state:ToolInvocationState,code:String){fail(code)}
        }
        val dispatcher=ToolDispatcher(store,{LocalToolPkgSession({bundle},{loaded,tool,args->
            executions++
            val result=execute(loaded.sources,tool.entry,args,tool.function,loaded.main)
            assertFalse(result.has("error"));result.get("value").toString()
        })},{confirmations++;true})
        var rounds=0
        val provider=object:ModelRoundService {
            override fun streamRound(request:RequestSnapshot,key:String,messages:JSONArray,tools:JSONArray)=flow {
                rounds++
                if(rounds==1)emit(ModelRoundEvent.End(listOf(ModelToolCall("local-call",ToolChatService.alias(permission),"{}"))))
                else {
                    val message=messages.getJSONObject(messages.length()-1)
                    assertEquals("local-call",message.getString("tool_call_id"))
                    val output=JSONObject(message.getString("content")).getJSONArray("content").getJSONObject(0)
                    assertTrue(JSONObject(output.getString("text")).getLong("timestamp")>0)
                    assertFalse(message.toString().contains("model-ref"))
                    emit(ModelRoundEvent.Text("已经查到本机时间。"));emit(ModelRoundEvent.End(emptyList()))
                }
            }
        }
        val text=ToolChatService(provider,dispatcher::execute).sendMessage(request,"synthetic-key").toList().joinToString("")
        assertEquals("已经查到本机时间。",text);assertEquals(2,rounds);assertEquals(1,executions);assertEquals(1,confirmations);assertEquals("COMPLETE",state)
    }
    @Test fun localSessionRejectsTwentyCancelledLateResults()=runBlocking {
        val root=File(repo,"app/src/main/assets/toolpkg/time")
        val bundle=ToolPkgCodec.decode(ToolPkgCodec.timeExample {File(root,it).readBytes()})
        repeat(20) {
            val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
            val session=LocalToolPkgSession({bundle},{_,_,_->entered.complete(Unit);withContext(NonCancellable){release.await()};"LATE"})
            session.connect()
            val job=launch {session.callTool(bundle.tools.first().name,"{}");fail("Late result escaped cancellation")}
            entered.await();job.cancel();release.complete(Unit);job.join();session.close()
            assertTrue(job.isCancelled)
            try {session.listTools();fail()}catch(e:ToolFailure){assertEquals("JS_CLOSED",e.code)}
        }
    }
}
