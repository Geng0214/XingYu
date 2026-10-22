package com.ai.assistance.operit

import com.ai.assistance.operit.core.tools.ToolFailure
import com.ai.assistance.operit.core.tools.javascript.QuickJsRunner
import kotlinx.coroutines.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class QuickJsAndroidTest {
    private val runner=QuickJsRunner()
    private suspend fun run(source:String,args:String="{}",timeout:Int=2000)=runner.execute(mapOf("main.js" to source),"main.js","test",args,timeout)
    private suspend fun fails(code:String,block:suspend ()->Unit) {
        try {block();fail("Expected $code")}catch(e:ToolFailure){assertEquals(code,e.code)}
    }
    @Test fun chineseEmojiNulAndQuotesRoundTripThroughActualJni()=runBlocking {
        val value="你好🙂\u0000\"\\\n"
        val result=JSONObject(run("exports.test=async p=>({value:p.value});",JSONObject().put("value",value).toString()))
        assertEquals(value,result.getString("value"))
    }
    @Test fun commonJsModuleAndCompleteCallbackExecute()=runBlocking {
        val result=runner.execute(mapOf("main.js" to "const m=require('./lib/echo'); exports.test=async p=>complete(await m.echo(p));",
            "lib/echo.js" to "exports.echo=async p=>({value:p.value});"),"main.js","test","{\"value\":\"A🙂\"}")
        assertEquals("A🙂",JSONObject(result).getString("value"))
    }
    @Test fun freshRuntimeCannotReadAnotherInvocationGlobals()=runBlocking {
        repeat(20) {
            assertEquals("\"set\"",run("exports.test=()=>{globalThis.privateMarker='A-secret';return 'set';};"))
            assertEquals("\"undefined\"",run("exports.test=()=>typeof globalThis.privateMarker;"))
        }
    }
    @Test fun osNetworkJavaAndNativeModulesAreUnavailable()=runBlocking {
        val result=JSONObject(run("exports.test=()=>({fetch:typeof fetch,java:typeof Java,process:typeof process,std:typeof std,os:typeof os});"))
        for(key in result.keys())assertEquals("undefined",result.getString(key))
        fails("JS_EXECUTION"){run("const fs=require('fs');exports.test=()=>fs.readFileSync('/data/private');")}
        fails("JS_EXECUTION"){run("const x=require('../escape');exports.test=()=>x;")}
    }
    @Test fun infiniteLoopAndMicrotasksStopAtTheirBudgets()=runBlocking {
        fails("TIMEOUT"){run("exports.test=()=>{while(true){}};",timeout=100)}
        fails("JS_JOB_LIMIT"){run("exports.test=async()=>{while(true)await Promise.resolve();};",timeout=10000)}
        fails("JS_PENDING"){run("exports.test=()=>new Promise(()=>{});")}
        assertEquals("7",run("exports.test=()=>7;"))
    }
    @Test fun cancellationInterruptsNativeWorkAndAllowsNextInvocation()=runBlocking {
        repeat(20) {
            val job=launch {run("exports.test=()=>{while(true){}};",timeout=30000);fail("cancelled result")}
            delay(50);job.cancelAndJoin()
            withTimeout(3000){assertEquals("1",run("exports.test=()=>1;"))}
        }
    }
    @Test fun queuedInvocationUsesItsExecutionTimeout()=runBlocking {
        val blocker=async {fails("TIMEOUT"){run("exports.test=()=>{while(true){}};",timeout=2500)}}
        delay(100)
        val started=System.nanoTime()
        fails("TIMEOUT"){run("exports.test=()=>1;",timeout=200)}
        assertTrue("Queue wait must be bounded by the invocation timeout",System.nanoTime()-started<2_000_000_000L)
        blocker.await()
        assertEquals("2",run("exports.test=()=>2;"))
    }
    @Test fun memoryOutputAndAtomicWaitCannotEscapeLimits()=runBlocking {
        fails("JS_EXECUTION"){run("exports.test=()=>new ArrayBuffer(100000000);")}
        fails("OUTPUT_LIMIT"){run("exports.test=()=> 'x'.repeat(131073);")}
        fails("JS_EXECUTION"){run("exports.test=()=>Atomics.wait(new Int32Array(new SharedArrayBuffer(4)),0,0);")}
        assertEquals("true",run("exports.test=()=>true;"))
    }
    @Test fun scriptErrorsDoNotExposePrivateErrorText()=runBlocking {
        fails("JS_EXECUTION"){run("exports.test=()=>{throw Error('synthetic-private-secret');};")}
        fails("JS_EXECUTION"){run("exports.test=()=>{complete(1);complete(2);};")}
    }
}
