package com.ai.assistance.operit.api.image

import com.ai.assistance.operit.api.ServiceEndpoint
import com.ai.assistance.operit.data.model.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object ImageGenerationEndpoint {
    fun complete(value:String):String {
        val url=ServiceEndpoint.parse(value)
        val path=url.encodedPath.trimEnd('/')
        val completed=when {
            path.isEmpty()->"/v1/images/generations"
            path.endsWith("/v1")->"$path/images/generations"
            path.endsWith("/images/generations")->path
            else->throw IllegalArgumentException("地址须为域名、/v1 基地址或完整 /images/generations 接口。")
        }
        return url.newBuilder().encodedPath(completed).build().toString()
    }
}

interface ImageGenerationService {
    suspend fun generate(config:ImageGenerationConfig,key:String,prompt:String):ByteArray
}

class OpenAIImageGenerationService(private val baseClient:OkHttpClient=OkHttpClient()):ImageGenerationService {
    override suspend fun generate(config:ImageGenerationConfig,key:String,prompt:String):ByteArray {
        ImageGenerations.validateConfig(config);ImageGenerations.validatePrompt(prompt)
        require(ImageGenerationEndpoint.complete(config.endpoint)==config.endpoint)
        val payload=JSONObject().put("model",config.model).put("prompt",prompt).put("size",config.size).put("n",1)
        val client=baseClient.newBuilder().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
            .connectTimeout(20,TimeUnit.SECONDS).readTimeout(config.timeoutSeconds.toLong(),TimeUnit.SECONDS)
            .callTimeout(config.timeoutSeconds.toLong(),TimeUnit.SECONDS).build()
        val call=client.newCall(Request.Builder().url(config.endpoint).header("Authorization","Bearer $key")
            .header("Accept","application/json").post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build())
        return suspendCancellableCoroutine {continuation->
            continuation.invokeOnCancellation {call.cancel()}
            call.enqueue(object:Callback {
                override fun onFailure(call:Call,e:IOException) {
                    if(continuation.isCancelled)return
                    continuation.resumeWithException(ChatFailure(if(e is SocketTimeoutException||e.message=="timeout")"TIMEOUT" else "NETWORK"))
                }
                override fun onResponse(call:Call,response:Response) {
                    response.use {
                        try {
                            if(!response.isSuccessful)throw ChatFailure(when(response.code){401,403->"AUTH";429->"RATE_LIMIT";in 300..399->"REDIRECT";in 500..599->"SERVER";else->"REQUEST"})
                            val body=response.body?:throw ChatFailure("IMAGE_GENERATION_PROTOCOL")
                            if(body.contentType()?.let {it.type!="application"||it.subtype!="json"}!=false)throw ChatFailure("IMAGE_GENERATION_PROTOCOL")
                            if(body.contentLength()>ImageGenerations.MAX_JSON)throw ChatFailure("IMAGE_GENERATION_LIMIT")
                            val input=body.byteStream();val out=ByteArrayOutputStream();val buffer=ByteArray(8192)
                            while(true){val n=input.read(buffer);if(n<0)break;if(n==0)continue;out.write(buffer,0,n);if(out.size()>ImageGenerations.MAX_JSON)throw ChatFailure("IMAGE_GENERATION_LIMIT")}
                            val text=out.toByteArray().toString(Charsets.UTF_8);StrictJson(text).validate()
                            val root=JSONObject(text);val data=root.optJSONArray("data")?:throw ChatFailure("IMAGE_GENERATION_PROTOCOL")
                            if(data.length()!=1)throw ChatFailure("IMAGE_GENERATION_PROTOCOL")
                            val encoded=data.getJSONObject(0).optString("b64_json","")
                            if(encoded.isEmpty())throw ChatFailure("IMAGE_GENERATION_PROTOCOL")
                            if(encoded.length>(ImageGenerations.MAX_RAW*4/3+16))throw ChatFailure("IMAGE_GENERATION_LIMIT")
                            val bytes=try {Base64.getDecoder().decode(encoded)}catch(_:IllegalArgumentException){throw ChatFailure("IMAGE_GENERATION_PROTOCOL")}
                            if(bytes.isEmpty()||bytes.size>ImageGenerations.MAX_RAW)throw ChatFailure("IMAGE_GENERATION_LIMIT")
                            if(!continuation.isCancelled)continuation.resume(bytes)
                        }catch(e:ChatFailure){if(!continuation.isCancelled)continuation.resumeWithException(e)}
                         catch(e:SocketTimeoutException){if(!continuation.isCancelled)continuation.resumeWithException(ChatFailure("TIMEOUT"))}
                         catch(_:IOException){if(!continuation.isCancelled)continuation.resumeWithException(ChatFailure(if(call.isCanceled())"TIMEOUT" else "NETWORK"))}
                         catch(_:Exception){if(!continuation.isCancelled)continuation.resumeWithException(ChatFailure("IMAGE_GENERATION_PROTOCOL"))}
                         catch(_:OutOfMemoryError){if(!continuation.isCancelled)continuation.resumeWithException(ChatFailure("IMAGE_GENERATION_LIMIT"))}
                    }
                }
            })
        }
    }
}
