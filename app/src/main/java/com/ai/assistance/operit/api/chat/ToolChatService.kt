package com.ai.assistance.operit.api.chat

import com.ai.assistance.operit.api.chat.llmprovider.*
import com.ai.assistance.operit.core.tools.*
import com.ai.assistance.operit.data.model.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class ModelToolCall(val id:String,val name:String,val arguments:String) {
    fun json()=JSONObject().put("id",id).put("type","function").put("function",JSONObject().put("name",name).put("arguments",arguments))
}
sealed interface ModelRoundEvent {
    data class Text(val value:String):ModelRoundEvent
    data object Reasoning:ModelRoundEvent
    data class End(val calls:List<ModelToolCall>,val reasoning:String?=null):ModelRoundEvent
}
interface ModelRoundService {
    fun streamRound(request:RequestSnapshot,key:String,messages:JSONArray,tools:JSONArray):Flow<ModelRoundEvent>
}

/** Extends the existing text protocol only when the request holds explicitly granted tools. */
class ToolChatService(private val provider:ModelRoundService,private val execute:suspend (ToolInvocation)->com.ai.assistance.operit.core.tools.mcp.McpToolOutput):AIService {
    companion object {
        fun alias(permission:ToolPermission):String="tool_"+MessageDigest.getInstance("SHA-256")
            .digest((permission.extensionId+"\u0000"+permission.definition.name).toByteArray()).take(16).joinToString(""){"%02x".format(it)}
    }
    override fun sendMessage(request:RequestSnapshot,key:String):Flow<String> =flow {
        sendEvents(request,key).collect {if(it is ChatStreamEvent.Text)emit(it.value)}
    }
    override fun sendEvents(request:RequestSnapshot,key:String):Flow<ChatStreamEvent> =flow {
        if(request.role.isRaw()&&request.tools.isNotEmpty())throw ChatFailure("ACCESS")
        // Private role grants do not authorize group or game/story tool calls.
        if((request.group!=null||request.play!=null)&&request.tools.isNotEmpty())throw ChatFailure("ACCESS")
        val owner=ToolRequestOwner(request.scope,request.chatId,request.requestId,request.assistantId)
        val allowed=request.tools.associateBy(::alias)
        require(allowed.size==request.tools.size)
        val tools=JSONArray().apply {allowed.forEach { (name,p)->put(JSONObject().put("type","function").put("function",
            JSONObject().put("name",name).put("description",p.definition.name+"\n"+p.definition.description).put("parameters",JSONObject(p.definition.inputSchema))))}}
        val messages=OpenAiMessages.encode(request.turns)
        val used=mutableSetOf<String>()
        var totalText=0
        while(true) {
            currentCoroutineContext().ensureActive()
            OpenAiMessages.checkSize(messages,tools)
            val text=StringBuilder()
            var terminal:ModelRoundEvent.End?=null
            provider.streamRound(request,key,messages,tools).collect {event->
                currentCoroutineContext().ensureActive()
                if(terminal!=null)throw ChatFailure("PROTOCOL")
                when(event) {
                    is ModelRoundEvent.Text->{totalText+=event.value.length;if(totalText>131072)throw ChatFailure("OUTPUT_LIMIT");text.append(event.value);emit(ChatStreamEvent.Text(event.value))}
                    is ModelRoundEvent.Reasoning->emit(ChatStreamEvent.Reasoning)
                    is ModelRoundEvent.End->terminal=event
                }
            }
            val end=terminal?:throw ChatFailure("TRUNCATED")
            emit(ChatStreamEvent.RoundEnd)
            if(end.calls.isEmpty()){if(text.isEmpty())throw ChatFailure("EMPTY");return@flow}
            if(allowed.isEmpty())throw ChatFailure("TOOLS_DISABLED")
            if(used.size+end.calls.size>ToolDispatcher.MAX_CALLS)throw ToolFailure("CALL_LIMIT")
            // Validate the entire batch before any external action, including duplicated IDs across rounds.
            val batch=mutableSetOf<String>()
            end.calls.forEach {call->
                if(call.id.isBlank()||call.id.length>128||call.id.any {it.isISOControl()}||call.id in used||!batch.add(call.id))throw ToolFailure("DUPLICATE")
                if(call.name !in allowed)throw ToolFailure("ACCESS")
                try {require(call.arguments.toByteArray().size<=65536);StrictJson(call.arguments).validate();JSONObject(call.arguments)}
                catch(_:Exception){throw ToolFailure("ARGUMENTS")}
            }
            messages.put(JSONObject().put("role","assistant").put("content",if(text.isEmpty())JSONObject.NULL else text.toString())
                .put("tool_calls",JSONArray().apply {end.calls.forEach {put(it.json())}})
                .apply {end.reasoning?.let {put("reasoning_content",it)}})
            for(call in end.calls) {
                currentCoroutineContext().ensureActive()
                used+=call.id
                val output=execute(ToolInvocation(owner,allowed.getValue(call.name),call.id,call.arguments))
                currentCoroutineContext().ensureActive()
                messages.put(JSONObject().put("role","tool").put("tool_call_id",call.id).put("content",output.json))
            }
            if(text.isNotEmpty()) {totalText+=2;if(totalText>131072)throw ChatFailure("OUTPUT_LIMIT");emit(ChatStreamEvent.Text("\n\n"))}
        }
    }
}
