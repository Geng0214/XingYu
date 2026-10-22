package com.ai.assistance.operit.api.chat.llmprovider

import com.ai.assistance.operit.data.model.ChatFailure
import com.ai.assistance.operit.data.model.StrictJson
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject

data class AvailableModel(val id:String,val name:String?)

/** Discovery reads only /models. It never sends a chat message or substitutes model identifiers. */
class ModelCatalogService(private val baseClient:OkHttpClient=OkHttpClient()) {
    suspend fun fetch(endpoint:String,key:String):List<AvailableModel> = suspendCancellableCoroutine {continuation->
        val client=baseClient.newBuilder().followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false).connectTimeout(15,TimeUnit.SECONDS)
            .readTimeout(30,TimeUnit.SECONDS).callTimeout(30,TimeUnit.SECONDS).build()
        val call=client.newCall(Request.Builder().url(EndpointCompleter.modelsEndpoint(endpoint))
            .header("Authorization","Bearer $key").header("Accept","application/json").get().build())
        continuation.invokeOnCancellation {call.cancel()}
        call.enqueue(object:Callback {
            override fun onFailure(call:Call,e:IOException) {
                val code=if(e is SocketTimeoutException||e.message=="timeout")"TIMEOUT"else"NETWORK"
                continuation.resumeWith(Result.failure(ChatFailure(code)))
            }
            override fun onResponse(call:Call,response:Response) {
                val result=runCatching {
                    response.use {
                        if(!response.isSuccessful)throw ChatFailure(when(response.code) {
                            401,403->"AUTH";404,405->"MODEL_LIST_ENDPOINT";429->"RATE_LIMIT"
                            in 300..399->"REDIRECT";in 500..599->"SERVER";else->"REQUEST"
                        })
                        val body=response.body?:throw ChatFailure("MODEL_LIST_PROTOCOL")
                        // Bound the list before decoding so a server cannot allocate an unbounded response.
                        val source=body.source()
                        source.request(MAX_BYTES+1L)
                        if(source.buffer.size>MAX_BYTES)throw ChatFailure("MODEL_LIST_PROTOCOL")
                        decode(source.readUtf8())
                    }
                }
                val failure=result.exceptionOrNull()
                when(failure) {
                    null->continuation.resumeWith(result)
                    is ChatFailure->continuation.resumeWith(Result.failure(failure))
                    is SocketTimeoutException->continuation.resumeWith(Result.failure(ChatFailure("TIMEOUT")))
                    is IOException->continuation.resumeWith(Result.failure(ChatFailure("NETWORK")))
                    else->continuation.resumeWith(Result.failure(ChatFailure("MODEL_LIST_PROTOCOL")))
                }
            }
        })
    }

    companion object {
        private const val MAX_BYTES=2*1024*1024L

        fun decode(text:String):List<AvailableModel> {
            try {
                StrictJson(text).validate()
                val root=JSONObject(text)
                val rows=root.getJSONArray("data")
                if(rows.length()>5000)throw ChatFailure("MODEL_LIST_PROTOCOL")
                val ids=mutableSetOf<String>()
                return (0 until rows.length()).map {index->
                    val row=rows.getJSONObject(index)
                    val id=row.get("id")
                    if(id !is String||id.isBlank()||id.length>200||id.any {it.isISOControl()}||!ids.add(id))throw ChatFailure("MODEL_LIST_PROTOCOL")
                    val name=if(row.has("name")&&!row.isNull("name")) {
                        val value=row.get("name")
                        if(value !is String||value.length>240||value.any {it.isISOControl()})throw ChatFailure("MODEL_LIST_PROTOCOL")
                        value
                    }else null
                    AvailableModel(id,name)
                }
            }catch(e:ChatFailure){throw e}
            catch(_:Exception){throw ChatFailure("MODEL_LIST_PROTOCOL")}
        }
    }
}
