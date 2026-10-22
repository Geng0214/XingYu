package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.model.*
import com.ai.assistance.operit.api.chat.*
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.io.IOException
import java.net.SocketTimeoutException
import java.nio.charset.CodingErrorAction
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class OpenAIProvider(private val baseClient: OkHttpClient = OkHttpClient()) : AIService,ModelRoundService {
    override fun sendMessage(request:RequestSnapshot,key:String):Flow<String> =flow {
        sendEvents(request,key).collect {if(it is ChatStreamEvent.Text)emit(it.value)}
    }
    override fun sendEvents(request:RequestSnapshot,key:String):Flow<ChatStreamEvent> =flow {
        streamRound(request,key,OpenAiMessages.encode(request.turns),JSONArray()).collect {event->when(event) {
            is ModelRoundEvent.Text->emit(ChatStreamEvent.Text(event.value))
            is ModelRoundEvent.Reasoning->emit(ChatStreamEvent.Reasoning)
            is ModelRoundEvent.End->emit(ChatStreamEvent.RoundEnd)
        }}
    }
    override fun streamRound(request:RequestSnapshot,key:String,messages:JSONArray,tools:JSONArray):Flow<ModelRoundEvent> = callbackFlow {
        OpenAiMessages.checkSize(messages,tools)
        val endpoint = EndpointCompleter.completeEndpoint(request.config.endpoint)
        val payload = JSONObject().put("model", request.config.model).put("stream", true)
            .put("messages",messages)
        request.maxOutputTokens?.let {require(it in 1..4096);payload.put("max_tokens",it)}
        if(tools.length()>0)payload.put("tools",tools).put("parallel_tool_calls",false)
        val client = baseClient.newBuilder()
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(request.config.timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .callTimeout(request.config.timeoutSeconds.toLong(), TimeUnit.SECONDS).build()
        val call = client.newCall(Request.Builder().url(endpoint)
            .header("Authorization", "Bearer $key").header("Accept", "text/event-stream")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build())
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                close(ChatFailure(if (e is SocketTimeoutException || e.message == "timeout") "TIMEOUT" else "NETWORK"))
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        if (!response.isSuccessful) throw ChatFailure(when(response.code) {
                            401,403 -> "AUTH"; 429 -> "RATE_LIMIT"; in 300..399 -> "REDIRECT"
                            in 500..599 -> "SERVER"; else -> "REQUEST"
                        })
                        val body = response.body ?: throw ChatFailure("PROTOCOL")
                        if (body.contentType()?.subtype != "event-stream") throw ChatFailure("PROTOCOL")
                        val end=SseDecoder.decodeRound(body.byteStream(),tools.length()>0,onReasoning={
                            if(trySendBlocking(ModelRoundEvent.Reasoning).isFailure)throw IOException("consumer closed")
                        }) { chunk ->
                            if (trySendBlocking(ModelRoundEvent.Text(chunk)).isFailure) throw IOException("consumer closed")
                        }
                        if(trySendBlocking(end).isFailure)throw IOException("consumer closed")
                        close()
                    } catch (e: ChatFailure) { close(e) }
                    catch (e: SocketTimeoutException) { close(ChatFailure("TIMEOUT")) }
                    catch (e: IOException) { close(ChatFailure(if(call.isCanceled()) "TIMEOUT" else "NETWORK")) }
                    catch (_: Exception) { close(ChatFailure("PROTOCOL")) }
                }
            }
        })
        awaitClose { call.cancel() }
    }
}

/** Line and event limits bound untrusted responses; UTF-8 decoding survives arbitrary packet boundaries. */
object SseDecoder {
    fun decode(input: InputStream, emit: (String) -> Unit) {
        decodeRound(input,false,emit=emit)
    }
    fun decodeRound(input:InputStream,allowTools:Boolean,onReasoning:()->Unit={},emit:(String)->Unit):ModelRoundEvent.End {
        val reader = BufferedReader(InputStreamReader(input, Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)))
        val data = StringBuilder()
        var ended = false
        var finished = false
        var length = 0
        var toolFinish=false
        var reasoningStarted=false
        var reasoning:StringBuilder?=null
        data class Partial(var id:String="",val name:StringBuilder=StringBuilder(),val arguments:StringBuilder=StringBuilder())
        val calls=sortedMapOf<Int,Partial>()
        fun event() {
            if (data.isEmpty()) return
            val raw = data.toString().trimEnd('\n'); data.setLength(0)
            if (raw == "[DONE]") { if(calls.isNotEmpty()&&!toolFinish)throw ChatFailure("TRUNCATED");ended = true; return }
            val json = try { StrictJson(raw).validate();JSONObject(raw) } catch (_: Exception) { throw ChatFailure("PROTOCOL") }
            if (json.has("error")) throw ChatFailure("SERVER")
            val choices = json.optJSONArray("choices") ?: throw ChatFailure("PROTOCOL")
            if (choices.length() == 0) return // providers may send an empty completion event
            if(finished)throw ChatFailure("PROTOCOL")
            if(choices.length()!=1)throw ChatFailure("PROTOCOL")
            val choice = choices.getJSONObject(0)
            val delta = choice.optJSONObject("delta") ?: throw ChatFailure("PROTOCOL")
            if(delta.has("function_call")&&!delta.isNull("function_call"))throw ChatFailure("TOOLS_DISABLED")
            if(delta.has("tool_calls")&&!delta.isNull("tool_calls")) {
                val chunks=delta.getJSONArray("tool_calls")
                if(!allowTools&&chunks.length()>0)throw ChatFailure("TOOLS_DISABLED")
                for(i in 0 until chunks.length()) {
                    val chunk=chunks.getJSONObject(i)
                    val index=chunk.get("index")
                    if(index !is Int||index !in 0..7)throw ChatFailure("PROTOCOL")
                    val partial=calls.getOrPut(index){Partial()}
                    if(chunk.has("id")&&!chunk.isNull("id")) {
                        val id=chunk.get("id")
                        if(id !is String||id.isBlank()||id.length>128||(partial.id.isNotEmpty()&&partial.id!=id))throw ChatFailure("PROTOCOL")
                        partial.id=id
                    }
                    if(chunk.has("type")&&!chunk.isNull("type")&&chunk.getString("type")!="function")throw ChatFailure("PROTOCOL")
                    chunk.optJSONObject("function")?.let {fn->
                        for((field,target) in listOf("name" to partial.name,"arguments" to partial.arguments))if(fn.has(field)&&!fn.isNull(field)) {
                            val value=fn.get(field)
                            if(value !is String)throw ChatFailure("PROTOCOL")
                            target.append(value)
                        }
                    }
                    if(partial.name.length>64||partial.arguments.length>65536)throw ChatFailure("OUTPUT_LIMIT")
                }
            }
            if(delta.has("reasoning_content")&&!delta.isNull("reasoning_content")) {
                val value=delta.get("reasoning_content")
                if(value !is String)throw ChatFailure("PROTOCOL")
                // Reasoning tokens are progress, not answer text; expose their phase instead of indefinite typing.
                if(value.isNotEmpty()&&!reasoningStarted){reasoningStarted=true;onReasoning()}
                if(allowTools) {
                    if(reasoning==null)reasoning=StringBuilder()
                    reasoning!!.append(value)
                    if(reasoning!!.length>131072)throw ChatFailure("OUTPUT_LIMIT")
                }
            }
            if (delta.has("content") && !delta.isNull("content")) {
                val content = delta.get("content")
                if (content !is String) throw ChatFailure("PROTOCOL")
                length += content.length
                if (length > 131072) throw ChatFailure("OUTPUT_LIMIT")
                if (content.isNotEmpty()) emit(content)
            }
            when (choice.optString("finish_reason", "")) {
                "tool_calls" -> {if(!allowTools)throw ChatFailure("TOOLS_DISABLED");toolFinish=true;finished=true}
                "function_call" -> throw ChatFailure("TOOLS_DISABLED")
                "length" -> throw ChatFailure("OUTPUT_LIMIT")
                "content_filter" -> throw ChatFailure("REQUEST")
                "stop" -> {if(calls.isNotEmpty())throw ChatFailure("PROTOCOL");finished=true}
            }
        }
        while (!ended) {
            val line = readLine(reader)
            if (line == null) { event();if(finished)ended=true;break }
            if (line.isEmpty()) event()
            else if (line.startsWith("data:")) {
                data.append(line.substring(5).removePrefix(" ")).append('\n')
                if (data.length > 65536) throw ChatFailure("PROTOCOL")
            }
        }
        if (!ended) throw ChatFailure("TRUNCATED")
        if(toolFinish) {
            if(calls.isEmpty()||calls.keys.toList()!=(0 until calls.size).toList())throw ChatFailure("PROTOCOL")
            val result=calls.values.map {
                if(it.id.isBlank()||it.name.isEmpty()||it.arguments.isEmpty())throw ChatFailure("PROTOCOL")
                ModelToolCall(it.id,it.name.toString(),it.arguments.toString())
            }
            if(result.map{it.id}.distinct().size!=result.size)throw ChatFailure("PROTOCOL")
            return ModelRoundEvent.End(result,reasoning?.toString())
        }
        if (length == 0) throw ChatFailure("EMPTY")
        return ModelRoundEvent.End(emptyList(),reasoning?.toString())
    }
    private fun readLine(reader: BufferedReader): String? {
        val line = StringBuilder()
        while (true) {
            val c = reader.read()
            if (c == -1) return if (line.isEmpty()) null else line.toString()
            if (c == 10) return line.toString().removeSuffix("\r")
            line.append(c.toChar())
            if (line.length > 65536) throw ChatFailure("PROTOCOL")
        }
    }
}
