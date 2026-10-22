package com.ai.assistance.operit.core.tools

import com.ai.assistance.operit.core.tools.mcp.*
import com.ai.assistance.operit.data.model.RoleAccessScope
import com.ai.assistance.operit.data.model.ChatFailure
import com.ai.assistance.operit.data.model.StrictJson
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/** All ownership fields and versions belong to one frozen model request, never to the selected UI page. */
data class ToolRequestOwner(val scope:RoleAccessScope,val chatId:String,val requestId:String,val assistantId:String)
data class ToolPermission(val extensionId:String,val configVersion:Long,val grantVersion:Long,
    val endpoint:String,val credentialRef:String?,val timeoutMs:Long,val definition:McpToolDefinition)
data class ToolInvocation(val owner:ToolRequestOwner,val permission:ToolPermission,val callId:String,val arguments:String)
enum class ToolInvocationState { WAITING_APPROVAL, RUNNING, COMPLETE, FAILED, CANCELLED }
class ToolFailure(val code:String):Exception(code)

/** Common execution boundary; local ToolPkg execution does not pretend to speak MCP. */
interface ToolRuntimeSession {
    suspend fun connect()
    suspend fun listTools():List<McpToolDefinition>
    suspend fun callTool(name:String,arguments:String):McpToolOutput
    suspend fun close()
}

/** Implementations must validate ownership/permission and change state in the same database transaction.
 * begin also rejects reused call IDs and more than MAX_CALLS invocations for one request.
 * finish requires RUNNING and the still-current grant; terminate only updates an existing nonterminal call.
 * Nothing in this interface permits reading another role's chat or replaying an interrupted call.
 */
interface ToolInvocationStore {
    suspend fun begin(call:ToolInvocation)
    suspend fun validate(call:ToolInvocation)
    suspend fun running(call:ToolInvocation)
    suspend fun finish(call:ToolInvocation,result:McpToolOutput)
    suspend fun terminate(call:ToolInvocation,state:ToolInvocationState,code:String)
}

/** One execution at a time. The chat coordinator owns cancellation and the enclosing request lifetime. */
class ToolDispatcher(private val store:ToolInvocationStore,
    private val sessionFactory:suspend (ToolPermission)->ToolRuntimeSession,
    private val confirm:suspend (ToolInvocation)->Boolean,private val executionAllowed:()->Boolean={true},private val accessVersion:()->Long={0}) {
    companion object { const val MAX_CALLS=8 }
    private val gate=Mutex()

    suspend fun execute(call:ToolInvocation):McpToolOutput = gate.withLock {
        val permissionVersion=accessVersion()
        fun requireAccess(){if(!executionAllowed()||accessVersion()!=permissionVersion)throw ToolFailure("LOCKED")}
        requireAccess()
        checkArguments(call)
        // Claim before confirmation/network. A repeated model call must not repeat an external side effect.
        store.begin(call)
        var session:ToolRuntimeSession?=null
        var primary:Throwable?=null
        try {
            if(!confirm(call))throw ToolFailure("DECLINED")
            requireAccess()
            currentCoroutineContext().ensureActive()
            store.validate(call)
            session=sessionFactory(call.permission)
            withTimeout(call.permission.timeoutMs) {
                session.connect()
                val actual=session.listTools().singleOrNull { it.name==call.permission.definition.name }
                if(actual!=call.permission.definition)throw ToolFailure("TOOL_CHANGED")
                // Re-check after network discovery and immediately before handing arguments to the server.
                currentCoroutineContext().ensureActive()
                requireAccess()
                store.running(call)
                requireAccess()
                val result=session.callTool(actual.name,call.arguments)
                currentCoroutineContext().ensureActive()
                requireAccess()
                store.finish(call,result)
                result
            }
        } catch(e:Exception) {
            primary=e
            val cancelled=e is CancellationException && (e !is TimeoutCancellationException || !currentCoroutineContext().isActive)
            val code=when(e) {
                is TimeoutCancellationException->"TIMEOUT"
                is CancellationException->"CANCELLED"
                is ToolFailure->e.code
                is McpFailure->e.code
                is ChatFailure->e.code
                else->"STORAGE"
            }
            withContext(NonCancellable) {
                store.terminate(call,if(cancelled)ToolInvocationState.CANCELLED else ToolInvocationState.FAILED,code)
            }
            if(e is CancellationException) {
                currentCoroutineContext().ensureActive()
                if(e !is TimeoutCancellationException)throw e
            }
            throw ToolFailure(code)
        } finally {
            // A close failure is reported separately; a persisted completion stays terminal and cannot be replayed.
            withContext(NonCancellable) {
                try { withTimeout(2_000) { session?.close() } }
                catch(e:Exception) {
                    if(primary!=null)primary.addSuppressed(e)
                    else throw ToolFailure("CLOSE")
                }
            }
        }
    }

    private fun checkArguments(call:ToolInvocation) {
        try {
            require(call.callId.isNotBlank()&&call.callId.length<=128)
            require(call.permission.timeoutMs in 250..300_000)
            require(call.arguments.toByteArray(Charsets.UTF_8).size<=65536)
            StrictJson(call.arguments).validate()
            JSONObject(call.arguments)
        }catch(_:Exception) {throw ToolFailure("ARGUMENTS")}
    }
}
