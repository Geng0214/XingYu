package com.ai.assistance.operit.core.tools.javascript

import com.ai.assistance.operit.core.tools.ToolFailure
import com.ai.assistance.operit.data.model.StrictJson
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal object QuickJsNativeBridge {
    init { System.loadLibrary("quickjsjni") }
    @JvmStatic external fun create(timeoutMs:Int):Long
    @JvmStatic external fun evaluate(handle:Long,script:ByteArray):ByteArray
    @JvmStatic external fun interrupt(handle:Long)
    @JvmStatic external fun close(handle:Long)
}

/** One fresh runtime per invocation; no previous plugin globals, Java host object or device filesystem. */
class QuickJsRunner {
    companion object {
        private val worker=ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,LinkedBlockingQueue(8),
            {task->Thread(task,"companion-toolpkg").apply {isDaemon=true}})
        private val nativeCodes=setOf("JS_MEMORY","JS_INPUT_LIMIT","JS_EXECUTION","JS_CLOSED","JS_REUSED",
            "JS_JOB_LIMIT","JS_PENDING","JS_RESULT","OUTPUT_LIMIT","TIMEOUT","CANCELLED","BUSY","ARGUMENTS")
    }
    suspend fun execute(sources:Map<String,String>,entry:String,function:String,arguments:String,timeoutMs:Int=10_000,
        mainEntry:String?=null,registrationOnly:Boolean=false):String {
        require(timeoutMs in 100..30_000)
        val script=ToolPkgScript.build(sources,entry,function,arguments,mainEntry,registrationOnly).toByteArray(Charsets.UTF_8)
        return try {
            // The shared worker can be occupied by another plugin; queueing must use the same advertised execution budget.
            withTimeout(timeoutMs.toLong()) {
                suspendCancellableCoroutine {continuation->
                    val handle=AtomicLong(0)
                    try {
                        val task=worker.submit {
                            if(continuation.isActive)try {
                                val id=QuickJsNativeBridge.create(timeoutMs)
                                handle.set(id)
                                if(!continuation.isActive)QuickJsNativeBridge.interrupt(id)
                                val bytes=QuickJsNativeBridge.evaluate(id,script)
                                val result=bytes.decodeToString(throwOnInvalidSequence=true)
                                if(continuation.isActive)continuation.resume(result)
                            } catch(e:Exception) {
                                // Never expose raw script errors, source, host paths or JNI exception text.
                                val code=if(e is IllegalStateException && e.message in nativeCodes)requireNotNull(e.message)else"JS_EXECUTION"
                                if(continuation.isActive)continuation.resumeWithException(ToolFailure(code))
                            } catch(_:LinkageError) {
                                if(continuation.isActive)continuation.resumeWithException(ToolFailure("JS_RUNTIME"))
                            } finally {
                                val id=handle.getAndSet(0)
                                if(id!=0L)QuickJsNativeBridge.close(id)
                            }
                        }
                        continuation.invokeOnCancellation {
                            val id=handle.get()
                            if(id!=0L)QuickJsNativeBridge.interrupt(id)
                            task.cancel(false)
                            worker.remove(task as Runnable)
                        }
                    } catch(_:RejectedExecutionException) {continuation.resumeWithException(ToolFailure("BUSY"))}
                }
            }
        } catch(e:TimeoutCancellationException) {
            // A caller's broader timeout or cancellation must keep propagating through this runner.
            currentCoroutineContext().ensureActive()
            throw ToolFailure("TIMEOUT")
        }
    }
}

/** CommonJS tool subpackages use the same exports/complete convention as Operit's JsEngine. */
object ToolPkgScript {
    fun validPath(path:String):Boolean=path.length in 1..240 && path.split('/').all {
        it.isNotEmpty() && it!="." && it!=".." && it.none {c->c.isISOControl()||c in "\\:<>\"|?*"}
    } && !path.startsWith('/')

    fun build(sources:Map<String,String>,entry:String,function:String,arguments:String,
        mainEntry:String?=null,registrationOnly:Boolean=false):String {
        require(sources.size in 1..64 && sources.keys.all(::validPath) && entry in sources)
        require(mainEntry==null||mainEntry in sources)
        require(sources.values.sumOf {it.toByteArray(Charsets.UTF_8).size}<=1024*1024)
        require(Regex("[A-Za-z_][A-Za-z0-9_]{0,63}").matches(function))
        require(arguments.toByteArray(Charsets.UTF_8).size<=65536)
        StrictJson(arguments).validate();JSONObject(arguments)
        val files=JSONObject().apply {sources.forEach { (path,source)->put(path,source)}}
        // Source and parameters are JSON-quoted data, never inserted into executable wrapper syntax.
        return """
            (async function() {
                'use strict';
                const files=JSON.parse(${JSONObject.quote(files.toString())});
                const cache=Object.create(null);
                const denied=new Proxy(Object.create(null),{get(){throw Error('CAPABILITY_DENIED');}});
                Object.defineProperty(globalThis,'Tools',{value:denied,writable:false,configurable:false});
                Object.defineProperty(globalThis,'ToolPkg',{value:denied,writable:false,configurable:false});
                let completed=false, completedValue;
                const complete=value=>{if(completed)throw Error('DUPLICATE_COMPLETE');completed=true;completedValue=value;};
                function resolve(parent, request) {
                    if(typeof request!=='string'||!request.startsWith('./')&&!request.startsWith('../'))throw Error('MODULE_PATH');
                    const parts=parent.split('/');parts.pop();
                    for(const part of request.split('/')) {
                        if(part==='.')continue;
                        if(part==='..'){if(parts.length===0)throw Error('MODULE_PATH');parts.pop();}
                        else {if(!part||Array.from(part).some(c=>c.charCodeAt(0)<32||[92,58,60,62,34,124,63,42].includes(c.charCodeAt(0))))throw Error('MODULE_PATH');parts.push(part);}
                    }
                    let path=parts.join('/');
                    if(!path.endsWith('.js'))path+='.js';
                    return path;
                }
                function load(path) {
                    if(!Object.hasOwn(files,path))throw Error('MODULE_MISSING');
                    if(Object.hasOwn(cache,path))return cache[path].exports;
                    const module={exports:Object.create(null)};cache[path]=module;
                    const require=request=>load(resolve(path,request));
                    new Function('exports','module','require','complete',files[path])(module.exports,module,require,complete);
                    return module.exports;
                }
                const main=${mainEntry?.let(JSONObject::quote)?:"null"};
                if(main!==null) {
                    const registration=load(main);
                    if(!Object.hasOwn(registration,'registerToolPkg')||typeof registration.registerToolPkg!=='function')throw Error('REGISTRATION_MISSING');
                    await registration.registerToolPkg({});
                }
                const entry=load(${JSONObject.quote(entry)});
                const name=${JSONObject.quote(function)};
                if(!Object.hasOwn(entry,name)||typeof entry[name]!=='function')throw Error('FUNCTION_MISSING');
                const value=await entry[name](JSON.parse(${JSONObject.quote(arguments)}));
                if($registrationOnly)return true;
                if(completed)return completedValue;
                if(value===undefined)throw Error('RESULT_MISSING');
                return value;
            })()
        """.trimIndent().also {require(it.toByteArray(Charsets.UTF_8).size<=2*1024*1024)}
    }
}
