package com.ai.assistance.operit

import com.ai.assistance.operit.core.tools.mcp.*
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.net.Proxy
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class McpSessionTest {
    private fun fixture(block:suspend CoroutineScope.(MockWebServer,OkHttpClient,MutableList<RecordedRequest>)->Unit)=runBlocking {
        val cert=HeldCertificate.Builder().addSubjectAlternativeName("localhost").addSubjectAlternativeName("127.0.0.1").build()
        val serverTls=HandshakeCertificates.Builder().heldCertificate(cert).build()
        val trust=HandshakeCertificates.Builder().addTrustedCertificate(cert.certificate).build()
        MockWebServer().use { server->
            server.useHttps(serverTls.sslSocketFactory(),false);server.start()
            val client=OkHttpClient.Builder().proxy(Proxy.NO_PROXY).sslSocketFactory(trust.sslSocketFactory(),trust.trustManager).build()
            withTimeout(15_000){block(server,client,CopyOnWriteArrayList())}
        }
    }
    private fun responder(requests:MutableList<RecordedRequest>,sse:Boolean=false,tools:(JSONObject)->JSONObject={
        JSONObject().put("tools",JSONArray().put(JSONObject().put("name","echo").put("description","中文工具")
            .put("inputSchema",JSONObject().put("type","object").put("properties",JSONObject()))))
    },call:(JSONObject)->MockResponse?={null})=object:Dispatcher() {
        override fun dispatch(request:RecordedRequest):MockResponse {
            requests.add(request)
            if(request.method=="GET"||request.method=="DELETE")return MockResponse().setResponseCode(405)
            val json=JSONObject(request.body.clone().readUtf8())
            val method=json.optString("method")
            if(!json.has("id"))return MockResponse().setResponseCode(202)
            if(method=="tools/call")call(json)?.let { return it }
            val result=when(method) {
                "initialize"->JSONObject().put("protocolVersion","2025-11-25").put("capabilities",JSONObject().put("tools",JSONObject()))
                    .put("serverInfo",JSONObject().put("name","fixture").put("version","1"))
                "tools/list"->tools(json)
                "tools/call"->JSONObject().put("content",JSONArray().put(JSONObject().put("type","text").put("text","你好🙂")))
                "ping"->JSONObject()
                else->error("Unexpected method $method")
            }
            val envelope=JSONObject().put("jsonrpc","2.0").put("id",json.get("id")).put("result",result).toString()
            val response=MockResponse().setHeader("Content-Type",if(sse)"text/event-stream"else"application/json")
                .setBody(if(sse)"event: message\ndata: $envelope\n\n"else envelope)
            if(method=="initialize")response.setHeader("Mcp-Session-Id","synthetic-session")
            return response
        }
    }
    @Test fun initializesEnumeratesCallsAndClosesWithBoundCredentials()=fixture { server,client,requests->
        server.dispatcher=responder(requests)
        val session=RemoteMcpSession(server.url("/mcp").toString(),"synthetic-token",3_000,client)
        try {
            session.connect()
            assertEquals("echo",session.listTools().single().name)
            val result=session.callTool("echo","{\"message\":\"测试🌸\"}")
            assertFalse(result.isError);assertTrue(result.json.contains("你好🙂"))
        } finally { session.close() }
        val posts=requests.filter { it.method=="POST" }
        assertTrue(posts.all { it.getHeader("Authorization")=="Bearer synthetic-token" && it.path=="/mcp" })
        assertTrue(posts.drop(1).all { it.getHeader("Mcp-Session-Id")=="synthetic-session" })
        assertTrue(posts.any { JSONObject(it.body.clone().readUtf8()).optString("method")=="notifications/initialized" })
    }
    @Test fun streamableHttpAcceptsSseResultsAndPagination()=fixture { server,client,requests->
        server.dispatcher=responder(requests,true,tools={ q->
            val second=q.optJSONObject("params")?.has("cursor")==true
            JSONObject().put("tools",JSONArray().put(JSONObject().put("name",if(second)"second"else"first").put("inputSchema",JSONObject().put("type","object"))))
                .apply { if(!second)put("nextCursor","page-2") }
        })
        val session=RemoteMcpSession(server.url("/mcp").toString(),timeoutMs=3_000,baseClient=client)
        try { session.connect();assertEquals(listOf("first","second"),session.listTools().map { it.name });assertTrue(session.callTool("first","{}").json.contains("你好🙂")) }
        finally { session.close() }
    }
    @Test fun repeatedPaginationIsRejected()=fixture { server,client,requests->
        server.dispatcher=responder(requests,tools={JSONObject().put("tools",JSONArray()).put("nextCursor","same")})
        val session=RemoteMcpSession(server.url("/mcp").toString(),timeoutMs=3_000,baseClient=client)
        try { session.connect();try {session.listTools();fail()}catch(e:McpFailure){assertEquals("PAGINATION",e.code)} }
        finally { session.close() }
    }
    @Test fun redirectNeverReachesCredentialDestination()=fixture { server,client,requests->
        server.dispatcher=object:Dispatcher() { override fun dispatch(request:RecordedRequest):MockResponse {
            requests.add(request);return MockResponse().setResponseCode(307).setHeader("Location",server.url("/leak"))
        } }
        val session=RemoteMcpSession(server.url("/mcp").toString(),"synthetic-token",750,client)
        try {session.connect();fail()}catch(_:McpFailure){}
        assertTrue(requests.isNotEmpty());assertTrue(requests.none { it.path=="/leak" })
        session.close()
    }
    @Test fun cancellationReturnsWithoutAcceptingLateResult()=fixture { server,client,requests->
        server.dispatcher=responder(requests,call={MockResponse().setHeader("Content-Type","application/json").setBody("{}").setBodyDelay(4,TimeUnit.SECONDS)})
        val session=RemoteMcpSession(server.url("/mcp").toString(),timeoutMs=5_000,baseClient=client)
        try {
            session.connect()
            var delivered=false
            val job=launch {session.callTool("echo","{}");delivered=true}
            withTimeout(2_000) {while(requests.none { it.body.clone().readUtf8().contains("tools/call") })delay(10)}
            withTimeout(1_000){job.cancelAndJoin()}
            assertFalse(delivered)
        } finally {session.close()}
    }
    @Test fun endpointsRejectCredentialsQueriesAndCleartext() {
        listOf("http://example.com/mcp","https://u:p@example.com/mcp","https://example.com/mcp?key=x","https://example.com/mcp#x","https://example.com/mcp/"+"/".repeat(2048)).forEach {
            try {RemoteMcpSession.normalizeEndpoint(it);fail(it)}catch(_:IllegalArgumentException){}
        }
        assertEquals("https://example.com/custom/mcp",RemoteMcpSession.normalizeEndpoint("https://example.com/custom/mcp"))
    }

    @Test fun httpFailuresExposeOnlyStableCodesAndNeverRetry()=fixture { server,client,requests->
        for((status,code) in listOf(401 to "AUTH",403 to "AUTH",429 to "RATE_LIMIT",404 to "SESSION_EXPIRED",503 to "SERVER")) {
            requests.clear()
            server.dispatcher=object:Dispatcher() { override fun dispatch(request:RecordedRequest):MockResponse {
                requests.add(request)
                return MockResponse().setResponseCode(status).setBody("PRIVATE_RESPONSE_MUST_NOT_ESCAPE")
            } }
            val session=RemoteMcpSession(server.url("/mcp").toString(),timeoutMs=1_000,baseClient=client)
            try {session.connect();fail()} catch(e:McpFailure) {assertEquals(code,e.code);assertEquals(code,e.message);assertNull(e.cause)}
            finally {session.close()}
            assertEquals(1,requests.count { it.method=="POST" })
        }
    }

    @Test fun oversizedChunkedAndDeepResponsesAreRejectedBeforeDeserialization()=fixture { server,client,requests->
        val oversized="x".repeat(RemoteMcpSession.MAX_RESPONSE.toInt()+1)
        val bodies=listOf(oversized to false,oversized to true,("[".repeat(1000)+"]".repeat(1000)) to true)
        for((body,chunked) in bodies) {
            server.dispatcher=object:Dispatcher() { override fun dispatch(request:RecordedRequest):MockResponse {
                requests.add(request)
                return MockResponse().setHeader("Content-Type","application/json").apply {
                    if(chunked)setChunkedBody(body,1024)else setBody(body)
                }
            } }
            val session=RemoteMcpSession(server.url("/mcp").toString(),timeoutMs=3_000,baseClient=client)
            try {session.connect();fail()} catch(e:McpFailure) {assertEquals("LIMIT",e.code)}
            finally {session.close()}
        }
    }

    @Test fun serverToolErrorIsPreservedAndInvalidArgumentsAreNotSent()=fixture { server,client,requests->
        server.dispatcher=responder(requests,call={ q->
            MockResponse().setHeader("Content-Type","application/json").setBody(JSONObject()
                .put("jsonrpc","2.0").put("id",q.get("id")).put("result",JSONObject().put("isError",true)
                    .put("content",JSONArray().put(JSONObject().put("type","text").put("text","synthetic denied")))).toString())
        })
        val session=RemoteMcpSession(server.url("/mcp").toString(),timeoutMs=3_000,baseClient=client)
        try {
            session.connect()
            val result=session.callTool("echo","{}")
            assertTrue(result.isError);assertTrue(result.json.contains("synthetic denied"))
            val before=requests.count { it.body.clone().readUtf8().contains("tools/call") }
            for(args in listOf("[]","{\"x\":1,\"x\":2}","{\"x\":"+"[".repeat(100)+"]".repeat(100)+"}","{\"x\":\""+"中".repeat(22_000)+"\"}")) {
                try {session.callTool("echo",args);fail()}catch(e:McpFailure){assertEquals("ARGUMENTS",e.code)}
            }
            assertEquals(before,requests.count { it.body.clone().readUtf8().contains("tools/call") })
        } finally {session.close()}
    }

    @Test fun delayedResponseTimesOutWithoutReplay()=fixture { server,client,requests->
        server.dispatcher=responder(requests,call={MockResponse().setHeader("Content-Type","application/json").setBody("{}").setBodyDelay(2,TimeUnit.SECONDS)})
        val session=RemoteMcpSession(server.url("/mcp").toString(),timeoutMs=700,baseClient=client)
        try {
            session.connect()
            try {session.callTool("echo","{}");fail()}catch(e:McpFailure){assertEquals("TIMEOUT",e.code)}
            assertEquals(1,requests.count { it.body.clone().readUtf8().contains("tools/call") })
        } finally {session.close()}
    }

    @Test fun sseMetadataCannotHideDeepJsonAndBracketsInsideStringsAreAllowed() {
        val normal=McpJsonDepthGuard(true)
        ("id: \"\nevent: message\ndata: {\"text\":\""+"[".repeat(100)+"\"}\n\n").toByteArray().forEach { normal.accept(it.toInt() and 255) }
        val malicious=McpJsonDepthGuard(true)
        try {
            ("id: \"\ndata: "+"[".repeat(33)).toByteArray().forEach {malicious.accept(it.toInt() and 255)}
            fail()
        }catch(e:java.io.IOException){assertEquals("MCP_LIMIT",e.message)}
    }
}
