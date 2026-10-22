package com.ai.assistance.operit.core.tools.mcp

import com.ai.assistance.operit.api.ServiceEndpoint
import com.ai.assistance.operit.data.model.StrictJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.sse.SSE
import io.ktor.client.request.headers
import io.modelcontextprotocol.kotlin.sdk.client.Client
import io.modelcontextprotocol.kotlin.sdk.client.StreamableHttpClientTransport
import io.modelcontextprotocol.kotlin.sdk.types.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okio.*
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Uses the official SDK as in Operit, with a private, destination-bound HTTP client. */
class RemoteMcpSession(endpoint: String,private val bearer: String?=null,private val timeoutMs: Long=60_000,
    baseClient: OkHttpClient=OkHttpClient()) : McpSession {
    companion object {
        const val MAX_RESPONSE=1024L*1024
        fun normalizeEndpoint(raw: String): String = ServiceEndpoint.parse(raw).toString()
    }
    private val address=normalizeEndpoint(endpoint)
    private val gate=Mutex()
    private var sdk: Client?=null
    private var closed=false
    private val http: HttpClient
    init {
        require(timeoutMs in 250..300_000)
        require(bearer==null || (bearer.isNotBlank() && bearer.length<=8192 && bearer.none { it=='\r'||it=='\n' }))
        val secure=baseClient.newBuilder().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
            .callTimeout(timeoutMs,TimeUnit.MILLISECONDS).readTimeout(timeoutMs,TimeUnit.MILLISECONDS)
            .addInterceptor { chain->
                if(chain.request().url.toString()!=address)throw IOException("MCP_DESTINATION")
                val response=chain.proceed(chain.request())
                if(response.code in 300..399) { response.close();throw IOException("MCP_REDIRECT") }
                val failure=when(response.code) {
                    401,403->"AUTH"
                    404->"SESSION_EXPIRED"
                    429->"RATE_LIMIT"
                    in 500..599->"SERVER"
                    else->null
                }
                if(failure!=null) { response.close();throw IOException("MCP_$failure") }
                val body=response.body
                if(body==null)response else {
                    if(body.contentLength()>MAX_RESPONSE) { response.close();throw IOException("MCP_LIMIT") }
                    val source=object:ForwardingSource(body.source()) {
                        var total=0L
                        val depth=McpJsonDepthGuard(body.contentType()?.subtype=="event-stream")
                        override fun read(sink:Buffer,byteCount:Long):Long {
                            val start=sink.size
                            val count=super.read(sink,minOf(byteCount,MAX_RESPONSE+1-total))
                            if(count>0)total+=count
                            if(total>MAX_RESPONSE)throw IOException("MCP_LIMIT")
                            if(count>0) {
                                val added=Buffer()
                                sink.copyTo(added,start,count)
                                while(!added.exhausted())depth.accept(added.readByte().toInt() and 255)
                            }
                            return count
                        }
                    }.buffer()
                    response.newBuilder().body(object:ResponseBody() {
                        override fun contentType()=body.contentType()
                        override fun contentLength()=body.contentLength()
                        override fun source()=source
                    }).build()
                }
            }.build()
        http=HttpClient(OkHttp) {
            followRedirects=false
            engine { preconfigured=secure }
            install(HttpTimeout) { requestTimeoutMillis=timeoutMs;connectTimeoutMillis=minOf(15_000,timeoutMs);socketTimeoutMillis=timeoutMs }
            install(SSE)
        }
    }
    private suspend fun <T> guarded(block:suspend ()->T):T = try {
        withTimeout(timeoutMs) { block() }
    } catch(e:TimeoutCancellationException) { currentCoroutineContext().ensureActive();throw McpFailure("TIMEOUT") }
      catch(e:CancellationException) { throw e }
      catch(e:McpFailure) { throw e }
      catch(e:Exception) {
          val chain=generateSequence<Throwable>(e){it.cause}.take(12).mapNotNull { it.message }.toList()
          throw McpFailure(when {
              chain.any { "MCP_REDIRECT" in it }->"REDIRECT"
              chain.any { "MCP_DESTINATION" in it }->"DESTINATION"
              chain.any { "MCP_LIMIT" in it }->"LIMIT"
              chain.any { "MCP_AUTH" in it }->"AUTH"
              chain.any { "MCP_RATE_LIMIT" in it }->"RATE_LIMIT"
              chain.any { "MCP_SESSION_EXPIRED" in it }->"SESSION_EXPIRED"
              chain.any { "MCP_SERVER" in it }->"SERVER"
              generateSequence<Throwable>(e){it.cause}.take(12).any { it is java.net.SocketTimeoutException || it is io.ktor.client.plugins.HttpRequestTimeoutException }->"TIMEOUT"
              else->"PROTOCOL"
          })
      }
    override suspend fun connect() = gate.withLock {
        check(!closed)
        if(sdk!=null)return@withLock
        val client=Client(Implementation("local-companion","extensions"))
        try {
            guarded {
                client.connect(StreamableHttpClientTransport(http,address,requestBuilder={
                    headers { bearer?.let { append("Authorization","Bearer $it") } }
                }))
            }
            sdk=client
        } catch(e:Exception) {
            closed=true
            // Dispose the failed SDK even when its initialization was cancelled.
            withContext(NonCancellable) {
                try { withTimeout(2_000){client.close()} } catch(cleanup:Exception) { e.addSuppressed(cleanup) } finally { http.close() }
            }
            throw e
        }
    }
    override suspend fun listTools():List<McpToolDefinition> = gate.withLock {
        val client=sdk ?: throw McpFailure("NOT_CONNECTED")
        guarded {
            val tools=mutableListOf<McpToolDefinition>()
            val cursors=mutableSetOf<String>()
            val names=mutableSetOf<String>()
            var cursor:String?=null
            do {
                val page=if(cursor==null)client.listTools()else client.listTools(ListToolsRequest(PaginatedRequestParams(cursor=cursor)))
                page.tools.forEach { tool->
                    val encoded=JSONObject(McpJson.encodeToString(Tool.serializer(),tool))
                    val schema=encoded.getJSONObject("inputSchema").toString()
                    if(tool.name.isBlank()||tool.name.length>128||!names.add(tool.name)||schema.length>65536||tool.description.orEmpty().length>8000||tools.size>=128)throw McpFailure("LIMIT")
                    tools+=McpToolDefinition(tool.name,tool.description.orEmpty(),schema)
                }
                cursor=page.nextCursor
                if(cursor!=null && (cursor.length>4096||!cursors.add(cursor)||cursors.size>32))throw McpFailure("PAGINATION")
            }while(cursor!=null)
            tools
        }
    }
    override suspend fun callTool(name:String,arguments:String):McpToolOutput = gate.withLock {
        val client=sdk ?: throw McpFailure("NOT_CONNECTED")
        val args=try {
            require(name.isNotBlank()&&name.length<=128&&arguments.toByteArray(Charsets.UTF_8).size<=65536)
            StrictJson(arguments).validate()
            kotlinx.serialization.json.Json.parseToJsonElement(arguments).also { require(it is kotlinx.serialization.json.JsonObject) }
                as kotlinx.serialization.json.JsonObject
        } catch(_:Exception) { throw McpFailure("ARGUMENTS") }
        guarded {
            val response=client.callTool(CallToolRequest(CallToolRequestParams(name=name,arguments=args)))
            val json=McpJson.encodeToString(CallToolResult.serializer(),response)
            if(json.length>131072)throw McpFailure("LIMIT")
            McpToolOutput(json,response.isError==true)
        }
    }
    override suspend fun close() = gate.withLock {
        if(closed)return@withLock
        closed=true
        val current=sdk;sdk=null
        try { if(current!=null)guarded { current.close() } } finally { http.close() }
    }
}

/** Bound nesting before SDK deserialization. SSE metadata is not JSON and must not alter quote state. */
internal class McpJsonDepthGuard(private val sse:Boolean) {
    private var depth=0
    private var quoted=false
    private var escaped=false
    private var field=""
    private var fieldComplete=false
    fun accept(byte:Int) {
        if(sse) {
            if(byte==10) { field="";fieldComplete=false;return }
            if(!fieldComplete) {
                if(byte==58)fieldComplete=true
                else if(field.length<5)field+=byte.toChar()
                return
            }
            if(field!="data")return
        }
        if(quoted) {
            if(escaped)escaped=false
            else if(byte==92)escaped=true
            else if(byte==34)quoted=false
        } else when(byte) {
            34->quoted=true
            123,91->{ depth++;if(depth>32)throw IOException("MCP_LIMIT") }
            125,93->depth--
        }
    }
}
