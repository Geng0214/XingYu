package com.ai.assistance.operit

import com.ai.assistance.operit.api.audio.*
import com.ai.assistance.operit.data.model.*
import java.net.Proxy
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.*
import org.junit.Test

class SpeechServiceTest {
    private fun config(kind:String,endpoint:String)=SpeechServiceConfig("speech","语音",kind,endpoint,if(kind==SpeechServices.TRANSCRIPTION)"whisper-1" else "tts-1",if(kind==SpeechServices.SYNTHESIS)"alloy" else "",15,"speech-key",2)
    private suspend fun rejected(code:String,block:suspend ()->Unit){try {block();fail("Expected $code")}catch(e:ChatFailure){assertEquals(code,e.code)}}
    private fun tls(block:(MockWebServer,OpenAISpeechService)->Unit) {
        val certificate=HeldCertificate.Builder().addSubjectAlternativeName("localhost").addSubjectAlternativeName("127.0.0.1").build()
        val serverTls=HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val trust=HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        MockWebServer().use {server->server.useHttps(serverTls.sslSocketFactory(),false);server.start(java.net.InetAddress.getByName("localhost"),0)
            block(server,OpenAISpeechService(OkHttpClient.Builder().proxy(Proxy.NO_PROXY).sslSocketFactory(trust.sslSocketFactory(),trust.trustManager).build()))}
    }

    @Test fun endpointsAcceptOnlyExplicitHttpsAudioDestinations() {
        assertEquals("https://example.com/v1/audio/transcriptions",SpeechEndpoint.complete("https://example.com",SpeechServices.TRANSCRIPTION))
        assertEquals("https://example.com/custom/v1/audio/speech",SpeechEndpoint.complete("https://example.com/custom/v1/",SpeechServices.SYNTHESIS))
        assertEquals("https://example.com/v1/audio/speech",SpeechEndpoint.complete("https://example.com/v1/audio/speech",SpeechServices.SYNTHESIS))
        for(value in listOf("http://example.com/v1","https://u:p@example.com/v1","https://example.com/v1?key=x","https://example.com/v1/audio/speech#x","https://example.com/v1/chat/completions","https://example.com/v1/"+"/".repeat(2048)))
            try {SpeechEndpoint.complete(value,SpeechServices.SYNTHESIS);fail(value)}catch(_:IllegalArgumentException){}
    }

    @Test fun wavHeaderDescribesExactPcmPayload() {
        val pcm=byteArrayOf(1,2,3,4);val wave=PcmWave.encode16KhzMono(pcm)
        assertEquals("RIFF",wave.copyOfRange(0,4).toString(Charsets.US_ASCII));assertEquals("WAVE",wave.copyOfRange(8,12).toString(Charsets.US_ASCII))
        val little=ByteBuffer.wrap(wave).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(40,little.getInt(4));assertEquals(16_000,little.getInt(24));assertEquals(32_000,little.getInt(28));assertEquals(4,little.getInt(40))
        assertArrayEquals(pcm,wave.copyOfRange(44,wave.size))
    }

    @Test fun transcriptionUploadsOneWavAndReturnsEditableUnicodeText()=tls {server,service->runBlocking {
        server.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody("{\"text\":\"你好🙂\"}"))
        val endpoint=server.url("/v1/audio/transcriptions").toString()
        assertEquals("你好🙂",service.transcribe(config(SpeechServices.TRANSCRIPTION,endpoint),"synthetic-speech-key",RecordedVoice(ByteArray(32_000),1000)))
        val sent=server.takeRequest();val body=sent.body.readUtf8()
        assertEquals("Bearer synthetic-speech-key",sent.getHeader("Authorization"));assertTrue(sent.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
        assertTrue(body.contains("name=\"model\""));assertTrue(body.contains("whisper-1"));assertTrue(body.contains("filename=\"recording.wav\""));assertFalse(body.contains("synthetic-speech-key"))
        assertEquals(1,server.requestCount)
    }}

    @Test fun synthesisRequestsPcmAndReturnsOnlyBoundedEvenAudio()=tls {server,service->runBlocking {
        val expected=ByteArray(9_600){(it%127).toByte()};server.enqueue(MockResponse().setHeader("Content-Type","audio/pcm").setBody(okio.Buffer().write(expected)))
        val endpoint=server.url("/v1/audio/speech").toString()
        assertArrayEquals(expected,service.synthesize(config(SpeechServices.SYNTHESIS,endpoint),"synthetic-speech-key","晚安🙂"))
        val sent=server.takeRequest();val body=org.json.JSONObject(sent.body.readUtf8())
        assertEquals("tts-1",body.getString("model"));assertEquals("晚安🙂",body.getString("input"));assertEquals("alloy",body.getString("voice"));assertEquals("pcm",body.getString("response_format"))
    }}

    @Test fun redirectsAuthenticationLimitsAndWrongMediaFailWithoutRetry()=tls {server,service->runBlocking {
        val speech=config(SpeechServices.SYNTHESIS,server.url("/v1/audio/speech").toString())
        for((response,code) in listOf(
            MockResponse().setResponseCode(307).setHeader("Location",server.url("/forward")) to "REDIRECT",
            MockResponse().setResponseCode(401) to "AUTH",MockResponse().setResponseCode(429) to "RATE_LIMIT",
            MockResponse().setHeader("Content-Type","application/json").setBody("{\"audio\":\"remote\"}") to "SPEECH_PROTOCOL",
            MockResponse().setHeader("Content-Type","audio/pcm").setBody("x") to "SPEECH_PROTOCOL"
        )) {server.enqueue(response);rejected(code){service.synthesize(speech,"synthetic-secret","hello")}}
        assertEquals(5,server.requestCount);repeat(5){server.takeRequest()}
    }}

    @Test fun cancellationAbortsSpeechRequestWithoutRetry()=tls {server,service->runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val job=launch {service.synthesize(config(SpeechServices.SYNTHESIS,server.url("/v1/audio/speech").toString()),"synthetic-secret","hello")}
        delay(100);server.takeRequest(5,TimeUnit.SECONDS)?:fail("request missing");job.cancelAndJoin();delay(100);assertEquals(1,server.requestCount)
    }}
}
