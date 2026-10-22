package com.ai.assistance.operit

import com.ai.assistance.operit.api.chat.llmprovider.*
import com.ai.assistance.operit.data.model.*
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ProviderTest {
    @Test fun rawHttpsRequestHasOnlyUserHistoryAndNoInjectedSystemOrTools()=tlsServer {server,provider->runBlocking {
        val role=RoleEntity("role","AI","PRIVATE_ROLE",spaceId="space",userName="PRIVATE_NAME",userPersona="PRIVATE_MASK",personaEnabled=true,conversationMode="raw")
        val chat=ChatEntity("chat",role.id,"config","chat",openingMessage="PRIVATE_GREETING")
        val relation=RelationshipEntry("r",role.id,role.spaceId,null,"Me","AI","friend","PRIVATE_RELATION",true,50)
        val lore=WorldbookEntry("w",role.id,role.spaceId,null,"lore","PRIVATE_LORE","always","",true,50)
        val turns=com.ai.assistance.operit.api.chat.RequestContext.build(role.scope(),role,chat,emptyList(),"hello",listOf(lore),listOf(relation))
        val request=snapshot(server.url("/v1/chat/completions").toString()).copy(role=role,scope=role.scope(),turns=turns)
        server.enqueue(MockResponse().setHeader("Content-Type","text/event-stream").setBody(chunk("ok")+done))
        provider.sendMessage(request,"synthetic-raw-key").collect()
        val body=JSONObject(server.takeRequest().body.readUtf8());val messages=body.getJSONArray("messages")
        assertEquals(1,messages.length());assertEquals("user",messages.getJSONObject(0).getString("role"));assertEquals("hello",messages.getJSONObject(0).getString("content"))
        assertFalse(body.toString().contains("PRIVATE_"));assertFalse(body.has("tools"));assertFalse(body.has("tool_choice"));assertEquals(1,server.requestCount)
    }}
    private fun chunk(text:String)="data: {\"choices\":[{\"delta\":{\"content\":${JSONObject.quote(text)}}}]}\n\n"
    private val done="data: [DONE]\n\n"
    private fun failure(code:String,block:()->Unit) {
        try {block();fail("Expected $code")}catch(e:ChatFailure){assertEquals(code,e.code)}
    }
    @Test fun arbitraryByteBoundariesAndEmoji() {
        val source=ByteArrayInputStream((": heartbeat\r\n\r\n"+chunk("你好🌸").replace("\n","\r\n")+chunk("世界")+done).toByteArray())
        val oneByte=object:InputStream(){override fun read()=source.read();override fun read(b:ByteArray,off:Int,len:Int)=source.read(b,off,minOf(1,len))}
        val result=mutableListOf<String>();SseDecoder.decode(oneByte,result::add)
        assertEquals(listOf("你好🌸","世界"),result)
    }
    @Test fun reasoningHasItsOwnProgressAndDoesNotBecomeAnswerText() {
        val stream="data: {\"choices\":[{\"delta\":{\"reasoning_content\":\"thinking\"}}]}\n\n"+
            chunk("好")+done
        val text=StringBuilder();var phases=0
        val end=SseDecoder.decodeRound(stream.byteInputStream(),false,onReasoning={phases++}){text.append(it)}
        assertEquals("好",text.toString());assertEquals(1,phases);assertNull(end.reasoning)
    }
    @Test fun multiLineDataAndFinalEventWithoutNewline() {
        val input="event: message\nid: 1\ndata: {\"choices\":\ndata: [{\"delta\":{\"content\":\"好\"},\"finish_reason\":\"stop\"}]}"
        val result=StringBuilder();SseDecoder.decode(input.byteInputStream()){result.append(it)}
        assertEquals("好",result.toString())
    }
    @Test fun partialAnswerSurvivesMissingEnd() {
        val result=StringBuilder()
        failure("TRUNCATED"){SseDecoder.decode(chunk("部分").byteInputStream()){result.append(it)}}
        assertEquals("部分",result.toString())
    }
    @Test fun toolsAreRejectedWithoutExecution() {
        failure("TOOLS_DISABLED"){SseDecoder.decode("data: {\"choices\":[{\"delta\":{\"tool_calls\":[{}]}}]}\n\n".byteInputStream()){fail()}}
    }
    @Test fun emptyAndMalformedResponsesFail() {
        failure("EMPTY"){SseDecoder.decode(done.byteInputStream()){fail()}}
        failure("PROTOCOL"){SseDecoder.decode("data: broken\n\n".byteInputStream()){fail()}}
    }
    @Test fun limitsUntrustedEvents() {
        failure("PROTOCOL"){SseDecoder.decode(("data: "+"x".repeat(65537)).byteInputStream()){fail()}}
    }
    @Test fun endpointsCannotCarryCredentialsOrCleartext() {
        assertEquals("https://example.com/v1/chat/completions",EndpointCompleter.completeEndpoint("https://example.com"))
        assertEquals("https://example.com/custom/v1/chat/completions",EndpointCompleter.completeEndpoint("https://example.com/custom/v1/"))
        listOf("http://example.com/v1","https://user:secret@example.com/v1","https://example.com/v1?key=x","https://example.com/v1#x","https://example.com/unknown","https://example.com/v1/"+"/".repeat(2048)).forEach {
            try{EndpointCompleter.completeEndpoint(it);fail(it)}catch(_:IllegalArgumentException){}
        }
    }
    private fun snapshot(endpoint:String,timeout:Int=15)=RequestSnapshot("r","c","m",RoleEntity("role","A","prompt"),
        ModelConfigData("cfg","test","OpenAI 兼容",endpoint,"test-model",timeout,"ref"),listOf(PromptTurn("user","hello")))
    private fun tlsServer(block:(MockWebServer,OpenAIProvider)->Unit) {
        val cert=HeldCertificate.Builder().addSubjectAlternativeName("localhost").addSubjectAlternativeName("127.0.0.1").build()
        val serverTls=HandshakeCertificates.Builder().heldCertificate(cert).build()
        val clientTls=HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        MockWebServer().use {server->
            server.useHttps(serverTls.sslSocketFactory(),false);server.start(java.net.InetAddress.getByName("localhost"),0)
            // Keep local TLS fixtures independent of developer proxy configuration and hostname resolution.
            val client=OkHttpClient.Builder().proxy(java.net.Proxy.NO_PROXY)
                .sslSocketFactory(clientTls.sslSocketFactory(),clientTls.trustManager)
                .eventListener(object:okhttp3.EventListener(){override fun callFailed(call:okhttp3.Call,ioe:java.io.IOException){System.err.println("TLS fixture: ${ioe.javaClass.simpleName}: ${ioe.message}")}}).build()
            block(server,OpenAIProvider(client))
        }
    }
    @Test fun httpsRequestUsesFrozenPayloadAndNoTools()=tlsServer{server,provider->runBlocking{
        server.enqueue(MockResponse().setHeader("Content-Type","text/event-stream").setBody(chunk("好")+done).throttleBody(1,1,TimeUnit.MILLISECONDS))
        assertEquals("好",provider.sendMessage(snapshot(server.url("/v1/chat/completions").toString()),"synthetic-test-key").toList().joinToString(""))
        val sent=server.takeRequest();val payload=JSONObject(sent.body.readUtf8())
        assertEquals("Bearer synthetic-test-key",sent.getHeader("Authorization"))
        assertFalse(payload.has("tools"));assertFalse(payload.has("tool_choice"))
        assertFalse(payload.has("stream_options"))
        assertEquals(1,server.requestCount)
    }}
    @Test fun proactiveRequestFreezesSmallOutputLimitInWirePayload()=tlsServer{server,provider->runBlocking{
        server.enqueue(MockResponse().setHeader("Content-Type","text/event-stream").setBody(chunk("来看看你")+done))
        provider.sendMessage(snapshot(server.url("/v1/chat/completions").toString()).copy(maxOutputTokens=256),"synthetic-key").collect()
        val body=JSONObject(server.takeRequest().body.readUtf8());assertEquals(256,body.getInt("max_tokens"));assertFalse(body.has("tools"))
    }}
    @Test fun visionRequestUsesOpenAiImagePartWithoutFileMetadata()=tlsServer{server,provider->runBlocking{
        val bytes=byteArrayOf(0xff.toByte(),0xd8.toByte(),1,2,0xff.toByte(),0xd9.toByte())
        val image=PromptImage(MessageImages.MIME_TYPE,20,10,MessageImages.sha256(bytes),bytes)
        val request=snapshot(server.url("/v1/chat/completions").toString()).copy(turns=listOf(PromptTurn("user","描述图片",listOf(image))))
        server.enqueue(MockResponse().setHeader("Content-Type","text/event-stream").setBody(chunk("好")+done))
        provider.sendMessage(request,"synthetic-vision-key").collect()
        val body=JSONObject(server.takeRequest().body.readUtf8());val content=body.getJSONArray("messages").getJSONObject(0).getJSONArray("content")
        assertEquals("描述图片",content.getJSONObject(0).getString("text"))
        val url=content.getJSONObject(1).getJSONObject("image_url").getString("url")
        assertTrue(url.startsWith("data:image/jpeg;base64,"));assertFalse(url.contains("file:"));assertFalse(body.toString().contains("synthetic-vision-key"))
    }}
    @Test fun actualHttpsBodyUsesPersonaPreviewAndExcludesDisabledPrivateSetting()=tlsServer {server,provider->runBlocking {
        val role=RoleEntity("role","AI","system",userName="小青😀",userPersona="ONLY_THIS_ROLE",personaEnabled=true)
        val chat=ChatEntity("chat",role.id,"config","chat")
        for(enabled in listOf(true,false)) {
            val current=role.copy(personaEnabled=enabled)
            val request=snapshot(server.url("/v1/chat/completions").toString()).copy(role=current,scope=current.scope(),
                turns=com.ai.assistance.operit.api.chat.RequestContext.build(current.scope(),current,chat,emptyList(),"hello"))
            server.enqueue(MockResponse().setHeader("Content-Type","text/event-stream").setBody(chunk("ok")+done))
            provider.sendMessage(request,"synthetic-persona-key").collect()
            val body=JSONObject(server.takeRequest().body.readUtf8())
            assertEquals(com.ai.assistance.operit.api.chat.RequestContext.systemPrompt(current),body.getJSONArray("messages").getJSONObject(0).getString("content"))
            assertEquals(enabled,body.toString().contains("ONLY_THIS_ROLE"));assertFalse(body.has("userPersona"))
        }
    }}
    @Test fun redirectsNeverForwardCredentials()=tlsServer{server,provider->runBlocking{
        server.enqueue(MockResponse().setResponseCode(307).setHeader("Location",server.url("/elsewhere")))
        try {provider.sendMessage(snapshot(server.url("/v1/chat/completions").toString()),"synthetic-key").collect();fail()}
        catch(e:ChatFailure){assertEquals("REDIRECT",e.code)}
        assertEquals(1,server.requestCount)
    }}
    @Test fun actualHttpsWorldbookBodyEqualsResolvedPreviewAndDoesNotExposeEntryMetadata()=tlsServer {server,provider->runBlocking {
        val role=RoleEntity("role","AI","system")
        val chat=ChatEntity("chat",role.id,"config","chat")
        val entry=WorldbookEntry("private-entry-id",role.id,role.spaceId,null,"城市","WORLD-HTTPS-MARKER","keywords","城市",true,50)
        for(text in listOf("城市","普通问候")) {
            val resolved=com.ai.assistance.operit.api.chat.RequestContext.resolve(role.scope(),role,chat,emptyList(),text,listOf(entry))
            val request=snapshot(server.url("/v1/chat/completions").toString()).copy(role=role,scope=role.scope(),turns=resolved.turns,worldbook=resolved.worldbook)
            server.enqueue(MockResponse().setHeader("Content-Type","text/event-stream").setBody(chunk("ok")+done))
            provider.sendMessage(request,"synthetic-worldbook-key").collect()
            val body=JSONObject(server.takeRequest().body.readUtf8())
            assertEquals(resolved.turns.first().content,body.getJSONArray("messages").getJSONObject(0).getString("content"))
            assertEquals(text=="城市",body.toString().contains("WORLD-HTTPS-MARKER"))
            assertFalse(body.toString().contains("private-entry-id"));assertFalse(body.has("worldbook"))
        }
        assertEquals(2,server.requestCount)
    }}
    @Test fun authenticationAndRateErrorsAreClassified()=tlsServer{server,provider->runBlocking{
        for((status,code) in listOf(401 to "AUTH",429 to "RATE_LIMIT",503 to "SERVER")) {
            server.enqueue(MockResponse().setResponseCode(status).setBody("private-server-detail"))
            try{provider.sendMessage(snapshot(server.url("/v1/chat/completions").toString()),"synthetic-key").collect();fail()}
            catch(e:ChatFailure){assertEquals(code,e.code);assertFalse(e.toString().contains("private-server-detail"))}
        }
        assertEquals(3,server.requestCount)
    }}
    @Test fun cancellationClosesHangingCall()=tlsServer{server,provider->runBlocking{
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val job=launch{provider.sendMessage(snapshot(server.url("/v1/chat/completions").toString()),"synthetic-key").collect()}
        withContext(Dispatchers.IO){assertNotNull(server.takeRequest(5,TimeUnit.SECONDS))}
        withTimeout(2000){job.cancelAndJoin()}
        assertTrue(job.isCancelled)
    }}
    @Test fun timeoutIsExplicitAndNeverRetries()=tlsServer{server,provider->runBlocking{
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        try {provider.sendMessage(snapshot(server.url("/v1/chat/completions").toString(),1),"synthetic-key").collect();fail()}
        catch(e:ChatFailure){assertEquals("TIMEOUT",e.code)}
        assertEquals(1,server.requestCount)
    }}
    @Test fun abruptDisconnectDoesNotPretendToComplete()=tlsServer{server,provider->runBlocking{
        server.enqueue(MockResponse().setHeader("Content-Type","text/event-stream").setBody(chunk("partial")+chunk("second"))
            .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
        try{provider.sendMessage(snapshot(server.url("/v1/chat/completions").toString()),"synthetic-key").collect();fail()}
        catch(e:ChatFailure){assertTrue(e.code in setOf("NETWORK","TRUNCATED"))}
        assertEquals(1,server.requestCount)
    }}
}
