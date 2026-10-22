package com.ai.assistance.operit.api.audio

import com.ai.assistance.operit.api.ServiceEndpoint
import com.ai.assistance.operit.data.model.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object SpeechEndpoint {
    fun complete(value:String,kind:String):String {
        require(kind in SpeechServices.kinds)
        val url=ServiceEndpoint.parse(value)
        val suffix=if(kind==SpeechServices.TRANSCRIPTION)"audio/transcriptions" else "audio/speech"
        val path=url.encodedPath.trimEnd('/')
        val completed=when {
            path.isEmpty()->"/v1/$suffix"
            path.endsWith("/v1")->"$path/$suffix"
            path.endsWith("/$suffix")->path
            else->throw IllegalArgumentException("地址须为域名、/v1 基地址或完整语音接口。")
        }
        return url.newBuilder().encodedPath(completed).build().toString()
    }
}

object PcmWave {
    fun encode16KhzMono(pcm:ByteArray):ByteArray {
        require(pcm.isNotEmpty()&&pcm.size<=SpeechServices.MAX_RECORD_BYTES&&pcm.size%2==0)
        val result=ByteArray(44+pcm.size)
        fun ascii(offset:Int,value:String)=value.toByteArray(Charsets.US_ASCII).copyInto(result,offset)
        fun short(offset:Int,value:Int){result[offset]=(value and 0xff).toByte();result[offset+1]=((value ushr 8) and 0xff).toByte()}
        fun int(offset:Int,value:Int){for(i in 0..3)result[offset+i]=((value ushr (8*i)) and 0xff).toByte()}
        ascii(0,"RIFF");int(4,36+pcm.size);ascii(8,"WAVE");ascii(12,"fmt ");int(16,16);short(20,1);short(22,1)
        int(24,SpeechServices.RECORD_SAMPLE_RATE);int(28,SpeechServices.RECORD_SAMPLE_RATE*2);short(32,2);short(34,16)
        ascii(36,"data");int(40,pcm.size);pcm.copyInto(result,44)
        return result
    }
}

interface SpeechService {
    suspend fun transcribe(config:SpeechServiceConfig,key:String,recording:RecordedVoice):String
    suspend fun synthesize(config:SpeechServiceConfig,key:String,text:String):ByteArray
}

class OpenAISpeechService(private val baseClient:OkHttpClient=OkHttpClient()):SpeechService {
    override suspend fun transcribe(config:SpeechServiceConfig,key:String,recording:RecordedVoice):String {
        SpeechServices.validateConfig(config);SpeechServices.validateRecording(recording)
        require(config.kind==SpeechServices.TRANSCRIPTION&&SpeechEndpoint.complete(config.endpoint,config.kind)==config.endpoint&&key.isNotBlank())
        val wave=PcmWave.encode16KhzMono(recording.pcm)
        val body=MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("model",config.model)
            .addFormDataPart("response_format","json")
            .addFormDataPart("file","recording.wav",wave.toRequestBody("audio/wav".toMediaType()))
            .build()
        val bytes=try {request(config,key,body,"application/json",SpeechServices.MAX_TRANSCRIPT_JSON)}finally {wave.fill(0)}
        val value=try {
            val json=bytes.toString(Charsets.UTF_8);StrictJson(json).validate();JSONObject(json).getString("text")
        } catch(e:ChatFailure){throw e} catch(_:Exception){throw ChatFailure("SPEECH_PROTOCOL")}
        if(value.isBlank()||value.length>SpeechServices.MAX_TRANSCRIPT_CHARS)throw ChatFailure("SPEECH_LIMIT")
        return value
    }

    override suspend fun synthesize(config:SpeechServiceConfig,key:String,text:String):ByteArray {
        SpeechServices.validateConfig(config);SpeechServices.validateSpeechText(text)
        require(config.kind==SpeechServices.SYNTHESIS&&SpeechEndpoint.complete(config.endpoint,config.kind)==config.endpoint&&key.isNotBlank())
        val payload=JSONObject().put("model",config.model).put("input",text).put("voice",config.voice).put("response_format","pcm")
        val bytes=request(config,key,payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()),"audio/*",SpeechServices.MAX_SPEECH_BYTES)
        if(bytes.isEmpty()||bytes.size%2!=0)throw ChatFailure("SPEECH_PROTOCOL")
        return bytes
    }

    private suspend fun request(config:SpeechServiceConfig,key:String,body:RequestBody,accept:String,limit:Int):ByteArray {
        val client=baseClient.newBuilder().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
            .connectTimeout(20,TimeUnit.SECONDS).readTimeout(config.timeoutSeconds.toLong(),TimeUnit.SECONDS)
            .callTimeout(config.timeoutSeconds.toLong(),TimeUnit.SECONDS).build()
        val call=client.newCall(Request.Builder().url(config.endpoint).header("Authorization","Bearer $key").header("Accept",accept).post(body).build())
        return suspendCancellableCoroutine {continuation->
            continuation.invokeOnCancellation {call.cancel()}
            call.enqueue(object:Callback {
                override fun onFailure(call:Call,e:IOException) {
                    if(!continuation.isCancelled)continuation.resumeWithException(ChatFailure(if(e is SocketTimeoutException||e.message=="timeout")"TIMEOUT" else "NETWORK"))
                }
                override fun onResponse(call:Call,response:Response) {response.use {
                    try {
                        if(!response.isSuccessful)throw ChatFailure(when(response.code){401,403->"AUTH";429->"RATE_LIMIT";in 300..399->"REDIRECT";in 500..599->"SERVER";else->"REQUEST"})
                        val responseBody=response.body?:throw ChatFailure("SPEECH_PROTOCOL")
                        val type=responseBody.contentType()
                        if(config.kind==SpeechServices.TRANSCRIPTION) {
                            if(type==null||type.type!="application"||type.subtype!="json")throw ChatFailure("SPEECH_PROTOCOL")
                        } else if(type==null||!(type.type=="audio"||(type.type=="application"&&type.subtype=="octet-stream")))throw ChatFailure("SPEECH_PROTOCOL")
                        if(responseBody.contentLength()>limit)throw ChatFailure("SPEECH_LIMIT")
                        val input=responseBody.byteStream();val out=ByteArrayOutputStream();val buffer=ByteArray(8192)
                        while(true){val n=input.read(buffer);if(n<0)break;if(n==0)continue;out.write(buffer,0,n);if(out.size()>limit)throw ChatFailure("SPEECH_LIMIT")}
                        if(!continuation.isCancelled)continuation.resume(out.toByteArray())
                    } catch(e:ChatFailure){if(!continuation.isCancelled)continuation.resumeWithException(e)}
                      catch(e:SocketTimeoutException){if(!continuation.isCancelled)continuation.resumeWithException(ChatFailure("TIMEOUT"))}
                      catch(_:IOException){if(!continuation.isCancelled)continuation.resumeWithException(ChatFailure(if(call.isCanceled())"TIMEOUT" else "NETWORK"))}
                      catch(_:Exception){if(!continuation.isCancelled)continuation.resumeWithException(ChatFailure("SPEECH_PROTOCOL"))}
                      catch(_:OutOfMemoryError){if(!continuation.isCancelled)continuation.resumeWithException(ChatFailure("SPEECH_LIMIT"))}
                }}
            })
        }
    }
}
