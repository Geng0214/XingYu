package com.ai.assistance.operit

import com.ai.assistance.operit.api.image.ImageGenerationEndpoint
import com.ai.assistance.operit.api.image.OpenAIImageGenerationService
import com.ai.assistance.operit.data.model.ChatFailure
import com.ai.assistance.operit.data.model.ImageGenerationConfig
import com.ai.assistance.operit.data.model.ImageGenerations
import java.net.Proxy
import java.util.Base64
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
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

class ImageGenerationTest {
    // Pricing fields were removed from the model; this request fixture must use the current image-service contract.
    private fun config(endpoint:String)=ImageGenerationConfig("image-config","OpenAI 图片",endpoint,"gpt-image-1",15,"image-key",3,"1024x1536")
    private suspend fun rejected(code:String,block:suspend ()->Unit) {
        try {block();fail("Expected $code")}catch(e:ChatFailure){assertEquals(code,e.code)}
    }
    private fun tls(block:(MockWebServer,OpenAIImageGenerationService)->Unit) {
        val certificate=HeldCertificate.Builder().addSubjectAlternativeName("localhost").addSubjectAlternativeName("127.0.0.1").build()
        val serverTls=HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val trust=HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        MockWebServer().use {server->
            server.useHttps(serverTls.sslSocketFactory(),false);server.start(java.net.InetAddress.getByName("localhost"),0)
            val client=OkHttpClient.Builder().proxy(Proxy.NO_PROXY).sslSocketFactory(trust.sslSocketFactory(),trust.trustManager).build()
            block(server,OpenAIImageGenerationService(client))
        }
    }

    @Test fun endpointsAcceptOnlyExplicitHttpsImageGenerationDestinations() {
        assertEquals("https://example.com/v1/images/generations",ImageGenerationEndpoint.complete("https://example.com"))
        assertEquals("https://example.com/custom/v1/images/generations",ImageGenerationEndpoint.complete("https://example.com/custom/v1/"))
        assertEquals("https://example.com/v1/images/generations",ImageGenerationEndpoint.complete("https://example.com/v1/images/generations"))
        for(value in listOf("http://example.com/v1","https://user:secret@example.com/v1","https://example.com/v1?key=x","https://example.com/v1#x","https://example.com/v1/chat/completions","https://example.com/v1/"+"/".repeat(2048))) {
            try {ImageGenerationEndpoint.complete(value);fail(value)}catch(_:IllegalArgumentException){}
        }
    }

    @Test fun postsFrozenOneImageRequestAndDecodesOnlyBase64Result()=tls {server,service->runBlocking {
        val expected="synthetic-generated-image".toByteArray()
        server.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody(JSONObject().put("data",org.json.JSONArray().put(JSONObject().put("b64_json",Base64.getEncoder().encodeToString(expected)))).toString()))
        val endpoint=server.url("/v1/images/generations").toString()
        assertArrayEquals(expected,service.generate(config(endpoint),"synthetic-image-key","画一只戴围巾的猫"))
        val sent=server.takeRequest();val body=sent.body.readUtf8();val payload=JSONObject(body)
        assertEquals("Bearer synthetic-image-key",sent.getHeader("Authorization"));assertEquals("application/json",sent.getHeader("Accept"))
        assertEquals("gpt-image-1",payload.getString("model"));assertEquals("画一只戴围巾的猫",payload.getString("prompt"))
        assertEquals("1024x1536",payload.getString("size"));assertEquals(1,payload.getInt("n"));assertEquals(1,server.requestCount)
        assertFalse(body.contains("synthetic-image-key"))
    }}

    @Test fun redirectsErrorsAndNonBase64PayloadsFailWithoutRetryOrCredentialForwarding()=tls {server,service->runBlocking {
        val endpoint=server.url("/v1/images/generations").toString();val current=config(endpoint)
        for((response,code) in listOf(
            MockResponse().setResponseCode(307).setHeader("Location",server.url("/forwarded")) to "REDIRECT",
            MockResponse().setResponseCode(401) to "AUTH",
            MockResponse().setResponseCode(429) to "RATE_LIMIT",
            MockResponse().setResponseCode(503) to "SERVER",
            MockResponse().setHeader("Content-Type","application/json").setBody("{broken") to "IMAGE_GENERATION_PROTOCOL",
            MockResponse().setHeader("Content-Type","application/json").setBody("{\"data\":[{\"url\":\"https://example.invalid/image.png\"}]}") to "IMAGE_GENERATION_PROTOCOL",
            MockResponse().setHeader("Content-Type","text/plain").setBody("not an image response") to "IMAGE_GENERATION_PROTOCOL",
            MockResponse().setHeader("Content-Type","application/json").setBody("x".repeat(ImageGenerations.MAX_JSON+1)) to "IMAGE_GENERATION_LIMIT"
        )) {
            server.enqueue(response);rejected(code){service.generate(current,"synthetic-secret","prompt")}
        }
        assertEquals(8,server.requestCount)
        repeat(8){server.takeRequest()};assertEquals(8,server.requestCount)
    }}

    @Test fun cancellationAbortsHangingCallAndDoesNotRetry()=tls {server,service->runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val job=launch {service.generate(config(server.url("/v1/images/generations").toString()),"synthetic-secret","prompt")}
        delay(100)
        server.takeRequest(5,TimeUnit.SECONDS)?:fail("request missing")
        job.cancelAndJoin();delay(100)
        assertEquals(1,server.requestCount)
    }}
}
