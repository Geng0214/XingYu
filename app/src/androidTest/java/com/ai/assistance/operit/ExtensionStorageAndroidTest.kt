package com.ai.assistance.operit

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.api.chat.RoomRequestStore
import com.ai.assistance.operit.core.tools.*
import com.ai.assistance.operit.core.tools.mcp.*
import com.ai.assistance.operit.data.db.*
import com.ai.assistance.operit.data.model.*
import com.ai.assistance.operit.data.security.Credentials
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.*
import org.junit.Test
import java.security.SecureRandom
import java.util.UUID

class ExtensionStorageAndroidTest {
    @Test fun androidProtocolLoggerHasNoPlatformOutput() {
        assertNull(System.getProperty("kotlin-logging-to-android-native"))
        assertEquals("org.slf4j.helpers.NOPLoggerFactory",org.slf4j.LoggerFactory.getILoggerFactory().javaClass.name)
        val logger=io.github.oshai.kotlinlogging.KotlinLogging.logger("mcp-privacy-test")
        assertFalse(logger.isTraceEnabled());assertFalse(logger.isDebugEnabled())
        assertFalse(logger.isInfoEnabled());assertFalse(logger.isWarnEnabled());assertFalse(logger.isErrorEnabled())
        logger.error {fail("Protocol payload must not enter Android logging");"synthetic-private-payload"}
    }
    private fun fixture(block:suspend (AppDatabase)->Unit)=runBlocking {
        System.loadLibrary("sqlcipher")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="extensions-${UUID.randomUUID()}.db"
        val key=ByteArray(32).also {SecureRandom().nextBytes(it)}
        val db=Room.databaseBuilder(context,AppDatabase::class.java,name).openHelperFactory(SupportOpenHelperFactory(key)).build()
        try {withTimeout(60_000){block(db)}}finally {db.close();context.deleteDatabase(name)}
    }
    private val credentials=object:Credentials {override fun read(ref:String,endpoint:String):String {
        if(ref!="ref"||endpoint!="https://example.invalid/mcp")throw ChatFailure("CREDENTIAL")
        return "synthetic-secret"
    }}
    private val tool=McpToolDefinition("echo","合成工具","{\"type\":\"object\"}")
    private suspend fun setup(db:AppDatabase):Triple<RoleEntity,ChatEntity,McpExtension> {
        val roles=RoleRepository(db);val ext=ExtensionRepository(db,credentials)
        db.dao().saveConfig(ModelConfigData("config","test","OpenAI 兼容","https://example.invalid/v1/chat/completions","test",90,"model-ref"))
        val role=roles.saveRole(null,"A","persona-A","opening-A","config")
        val chat=roles.createChat(role.scope(),"config")
        var server=ext.save(null,null,"Echo","https://example.invalid/mcp","ref",3_000)
        server=ext.replaceCatalog(server.id,server.version,listOf(tool))
        server=ext.setEnabled(server.id,server.version,true)
        ext.grant(role.scope(),server.id,server.version,tool.name,true)
        return Triple(role,chat,server)
    }
    private fun invocation(snapshot:RequestSnapshot,id:String="call",args:String="{}"):ToolInvocation =
        ToolInvocation(ToolRequestOwner(snapshot.scope,snapshot.chatId,snapshot.requestId,snapshot.assistantId),snapshot.tools.single(),id,args)
    private suspend fun denied(code:String="ACCESS",block:suspend ()->Unit) {
        try {block();fail("Expected $code")}catch(e:ToolFailure){assertEquals(code,e.code)}
    }

    @Test fun actualEncryptedRequestsKeepRoleMarkersIsolatedForFiftyAlternations()=fixture { db->
        val (a,ca,server)=setup(db);val roles=RoleRepository(db);val ext=ExtensionRepository(db,credentials)
        val b=roles.saveRole(null,"B","persona-B","opening-B","config");val cb=roles.createChat(b.scope(),"config")
        ext.grant(b.scope(),server.id,server.version,tool.name,true)
        val requests=RoomRequestStore(db);val calls=RoomToolInvocationStore(db)
        for((role,chat) in listOf(a to ca,b to cb))repeat(10) {n->
            val request=requests.prepare(role.scope(),chat.id,"${role.name}-marker-$n",UUID.randomUUID().toString())
            requests.update(request,"${role.name}-answer-$n",MessageState.COMPLETE,"",1,1)
        }
        repeat(50) {n->
            val (own,chat)=if(n%2==0)a to ca else b to cb
            val other=if(n%2==0)b else a
            val request=requests.prepare(own.scope(),chat.id,"probe",UUID.randomUUID().toString())
            repeat(10){assertTrue(request.turns.any {row->row.content=="${own.name}-marker-$it"})}
            assertFalse(request.turns.any {it.content.contains("${other.name}-marker")||it.content==other.prompt})
            val call=invocation(request,args="{\"marker\":\"${own.name}-$n\"}")
            denied {calls.begin(call.copy(owner=call.owner.copy(scope=other.scope())))}
            denied {calls.begin(call.copy(owner=call.owner.copy(assistantId="wrong")))}
            denied {calls.begin(call.copy(permission=call.permission.copy(endpoint="https://other.invalid/mcp")))}
            calls.begin(call);calls.running(call)
            denied {calls.finish(call.copy(arguments="{\"forged\":true}"),McpToolOutput("{}",false))}
            calls.finish(call,McpToolOutput(call.arguments,false))
            denied("DUPLICATE"){calls.begin(call)}
            assertTrue(db.extensions().invocations(other.id,other.spaceId,chat.id).first().isEmpty())
            assertTrue(db.extensions().invocations(own.id,own.spaceId,chat.id).first().all {it.result.contains(own.name)})
            requests.update(request,"ok",MessageState.COMPLETE,"",1,2)
        }
    }

    @Test fun twentyRevokeCancelDisableDeleteAndRecoveryRoundsRejectLateWrites()=fixture { db->
        val (role,chat,initial)=setup(db);val ext=ExtensionRepository(db,credentials)
        var server=initial
        val requests=RoomRequestStore(db);val calls=RoomToolInvocationStore(db)
        repeat(20) {n->
            val request=requests.prepare(role.scope(),chat.id,"request-$n",UUID.randomUUID().toString())
            val call=invocation(request)
            calls.begin(call);calls.running(call)
            when(n%4) {
                0->{ext.grant(role.scope(),server.id,server.version,tool.name,false);ext.grant(role.scope(),server.id,server.version,tool.name,true)}
                1->{calls.terminate(call,ToolInvocationState.CANCELLED,"CANCELLED")}
                2->{server=ext.setEnabled(server.id,server.version,false);server=ext.setEnabled(server.id,server.version,true);ext.grant(role.scope(),server.id,server.version,tool.name,true)}
                3->{requests.update(request,"partial",MessageState.GENERATING,"",1,1);db.recoverInterruptedRequests()}
            }
            denied {calls.finish(call,McpToolOutput("LATE",false))}
            val row=db.extensions().invocations(role.id,role.spaceId,chat.id).first().single {it.requestId==request.requestId}
            assertEquals("",row.result);assertTrue(row.state in setOf("FAILED","CANCELLED"))
            if(n%4==3) {
                assertEquals("INTERRUPTED",row.errorCode)
                assertEquals("partial",db.dao().history(role.id,role.spaceId,chat.id).last().content)
            }
            requests.update(request,"",MessageState.CANCELLED,"",-1,1)
        }
        val request=requests.prepare(role.scope(),chat.id,"last",UUID.randomUUID().toString());val call=invocation(request)
        calls.begin(call)
        val copy=RoleRepository(db).copyRole(role.scope())
        assertTrue(db.extensions().grants(copy.id,copy.spaceId).first().isEmpty())
        RoleRepository(db).deleteChat(role.scope(),chat.id)
        denied {calls.running(call)}
        assertEquals(0,db.extensions().countCalls(request.requestId,request.assistantId))
        assertNotNull(db.extensions().extension(server.id));assertNotNull(db.dao().config("config"))
    }

    @Test fun stalePermissionScreenCanRevokeAfterServiceDisable()=fixture {db->
        val (role,_,server)=setup(db)
        val grants=db.extensions()
        val previous=requireNotNull(grants.grant(role.id,role.spaceId,server.id,tool.name))
        val ext=ExtensionRepository(db,credentials)
        ext.setEnabled(server.id,server.version,false)
        ext.revokeGrant(role.scope(),server.id,tool.name)
        ext.revokeGrant(role.scope(),server.id,tool.name)
        val current=requireNotNull(grants.grant(role.id,role.spaceId,server.id,tool.name))
        assertFalse(current.enabled)
        assertEquals(previous.version+1,current.version)
    }

    @Test fun savingServiceConfigurationRevokesGrantsUntilExplicitReauthorization()=fixture {db->
        val (role,chat,server)=setup(db)
        val grants=db.extensions()
        val previous=requireNotNull(grants.grant(role.id,role.spaceId,server.id,tool.name))
        val requests=RoomRequestStore(db)
        val request=requests.prepare(role.scope(),chat.id,"service config change",UUID.randomUUID().toString())
        val calls=RoomToolInvocationStore(db)
        val call=invocation(request)
        calls.begin(call);calls.running(call)
        val ext=ExtensionRepository(db,credentials)

        val changed=ext.save(server.id,server.version,"Echo","https://new.example.invalid/mcp",null,3_000)

        val revoked=requireNotNull(grants.grant(role.id,role.spaceId,server.id,tool.name))
        assertFalse(revoked.enabled)
        assertEquals(previous.version+1,revoked.version)
        denied {calls.finish(call,McpToolOutput("late result",false))}
        assertEquals("REVOKED",grants.invocations(role.id,role.spaceId,chat.id).first().single().errorCode)

        val refreshed=ext.replaceCatalog(changed.id,changed.version,listOf(tool))
        val enabled=ext.setEnabled(refreshed.id,refreshed.version,true)
        assertTrue(requests.prepare(role.scope(),chat.id,"same name is not authorization",UUID.randomUUID().toString()).tools.isEmpty())
        ext.grant(role.scope(),enabled.id,enabled.version,tool.name,true)
        assertEquals(1,requests.prepare(role.scope(),chat.id,"explicit regrant",UUID.randomUUID().toString()).tools.size)
    }

    @Test fun permissionCenterRevocationInvalidatesRunningToolCalls()=fixture {db->
        val (role,chat,server)=setup(db)
        val request=RoomRequestStore(db).prepare(role.scope(),chat.id,"permission revoke",UUID.randomUUID().toString())
        val calls=RoomToolInvocationStore(db)
        val call=invocation(request)
        calls.begin(call);calls.running(call)
        ExtensionRepository(db,credentials).revokeGrant(role.scope(),server.id,tool.name)
        denied {calls.finish(call,McpToolOutput("late result",false))}
        val invocationRow=db.extensions().invocations(role.id,role.spaceId,chat.id).first().single()
        assertEquals("FAILED",invocationRow.state)
        assertEquals("REVOKED",invocationRow.errorCode)
        assertFalse(requireNotNull(db.extensions().grant(role.id,role.spaceId,server.id,tool.name)).enabled)
    }

    @Test fun permissionCenterRevocationInvalidatesWaitingApproval()=fixture {db->
        val (role,chat,server)=setup(db)
        val request=RoomRequestStore(db).prepare(role.scope(),chat.id,"waiting approval revoke",UUID.randomUUID().toString())
        val calls=RoomToolInvocationStore(db)
        val call=invocation(request)
        calls.begin(call)

        ExtensionRepository(db,credentials).revokeGrant(role.scope(),server.id,tool.name)

        val invocationRow=db.extensions().invocations(role.id,role.spaceId,chat.id).first().single()
        assertEquals("FAILED",invocationRow.state)
        assertEquals("REVOKED",invocationRow.errorCode)
        assertEquals("",invocationRow.result)
        denied {calls.running(call)}
    }

    @Test fun revokingOneRoleLeavesAnotherRolesToolGrantUsable()=fixture {db->
        val (roleA,chatA,server)=setup(db)
        val roles=RoleRepository(db);val extensions=ExtensionRepository(db,credentials)
        val roleB=roles.saveRole(null,"B","persona-B","opening-B","config")
        val chatB=roles.createChat(roleB.scope(),"config")
        extensions.grant(roleB.scope(),server.id,server.version,tool.name,true)
        val requests=RoomRequestStore(db)
        val requestA=requests.prepare(roleA.scope(),chatA.id,"revoke role A",UUID.randomUUID().toString())
        val requestB=requests.prepare(roleB.scope(),chatB.id,"preserve role B",UUID.randomUUID().toString())
        val calls=RoomToolInvocationStore(db)
        val callA=invocation(requestA);val callB=invocation(requestB)
        calls.begin(callA);calls.running(callA)
        calls.begin(callB)

        extensions.revokeGrant(roleA.scope(),server.id,tool.name)

        denied {calls.finish(callA,McpToolOutput("revoked",false))}
        assertEquals("REVOKED",db.extensions().invocations(roleA.id,roleA.spaceId,chatA.id).first().single().errorCode)
        calls.running(callB)
        calls.finish(callB,McpToolOutput("role-B-still-authorized",false))
        assertEquals("role-B-still-authorized",db.extensions().invocations(roleB.id,roleB.spaceId,chatB.id).first().single().result)
        assertFalse(requireNotNull(db.extensions().grant(roleA.id,roleA.spaceId,server.id,tool.name)).enabled)
        assertTrue(requireNotNull(db.extensions().grant(roleB.id,roleB.spaceId,server.id,tool.name)).enabled)
    }

    @Test fun permissionCenterRevocationDuringRemoteCallRejectsLateDispatcherWrite()=fixture {db->coroutineScope {
        // The remote call must remain a child of the fixture so teardown waits for cancellation and completion.
        val (role,chat,server)=setup(db)
        val request=RoomRequestStore(db).prepare(role.scope(),chat.id,"late tool result",UUID.randomUUID().toString())
        val calls=RoomToolInvocationStore(db);val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        var toolCalls=0;var closed=false
        val runtime=object:ToolRuntimeSession {
            override suspend fun connect() {}
            override suspend fun listTools()=listOf(tool)
            override suspend fun callTool(name:String,arguments:String):McpToolOutput {
                toolCalls++
                entered.complete(Unit)
                // Model a remote server that finishes after its grant is revoked; the frozen Room permission must still reject persistence.
                withContext(NonCancellable){release.await()}
                return McpToolOutput("{\"result\":\"late\"}",false)
            }
            override suspend fun close() {closed=true}
        }
        val dispatcher=ToolDispatcher(calls,{runtime},{true})
        val result=async {dispatcher.execute(invocation(request))}
        entered.await()

        ExtensionRepository(db,credentials).revokeGrant(role.scope(),server.id,tool.name)
        release.complete(Unit)
        denied {result.await()}

        val invocationRow=db.extensions().invocations(role.id,role.spaceId,chat.id).first().single()
        assertEquals(1,toolCalls);assertTrue(closed)
        assertEquals("FAILED",invocationRow.state);assertEquals("REVOKED",invocationRow.errorCode)
        assertEquals("",invocationRow.result)
    }}

    @Test fun catalogRefreshRevokesEveryRoleGrantUntilEachRoleReauthorizes()=fixture {db->
        val (roleA,chatA,server)=setup(db)
        val roles=RoleRepository(db);val extensions=ExtensionRepository(db,credentials)
        val roleB=roles.saveRole(null,"B","persona-B","opening-B","config")
        val chatB=roles.createChat(roleB.scope(),"config")
        extensions.grant(roleB.scope(),server.id,server.version,tool.name,true)
        val grantA=requireNotNull(db.extensions().grant(roleA.id,roleA.spaceId,server.id,tool.name))
        val grantB=requireNotNull(db.extensions().grant(roleB.id,roleB.spaceId,server.id,tool.name))
        assertTrue(grantA.enabled);assertTrue(grantB.enabled)
        val requests=RoomRequestStore(db)
        val frozenA=requests.prepare(roleA.scope(),chatA.id,"before catalog refresh A",UUID.randomUUID().toString())
        val frozenB=requests.prepare(roleB.scope(),chatB.id,"before catalog refresh B",UUID.randomUUID().toString())
        val calls=RoomToolInvocationStore(db)
        val callA=invocation(frozenA);val callB=invocation(frozenB)
        calls.begin(callA);calls.running(callA)
        calls.begin(callB);calls.running(callB)

        extensions.replaceCatalog(server.id,server.version,listOf(tool.copy(description="updated definition")))

        assertNull(db.extensions().grant(roleA.id,roleA.spaceId,server.id,tool.name))
        assertNull(db.extensions().grant(roleB.id,roleB.spaceId,server.id,tool.name))
        for((role,chat,call) in listOf(Triple(roleA,chatA,callA),Triple(roleB,chatB,callB))) {
            denied {calls.finish(call,McpToolOutput("late result",false))}
            val row=db.extensions().invocations(role.id,role.spaceId,chat.id).first().single()
            assertEquals("FAILED",row.state);assertEquals("REVOKED",row.errorCode);assertEquals("",row.result)
        }
        denied {calls.begin(callA)}
        denied {calls.begin(callB)}
        assertTrue(requests.prepare(roleA.scope(),chatA.id,"no implicit grant A",UUID.randomUUID().toString()).tools.isEmpty())
        assertTrue(requests.prepare(roleB.scope(),chatB.id,"no implicit grant B",UUID.randomUUID().toString()).tools.isEmpty())

        val refreshed=requireNotNull(db.extensions().extension(server.id))
        extensions.grant(roleA.scope(),refreshed.id,refreshed.version,tool.name,true)
        assertEquals(1,requests.prepare(roleA.scope(),chatA.id,"explicit regrant",UUID.randomUUID().toString()).tools.size)
        assertNull(db.extensions().grant(roleB.id,roleB.spaceId,server.id,tool.name))
        assertTrue(requests.prepare(roleB.scope(),chatB.id,"other role stays revoked",UUID.randomUUID().toString()).tools.isEmpty())
    }

    @Test fun budgetPersistsAcrossDispatchersAndCatalogEditsInvalidateSnapshots()=fixture {db->
        val (role,chat,server)=setup(db);val requests=RoomRequestStore(db)
        val request=requests.prepare(role.scope(),chat.id,"budget",UUID.randomUUID().toString())
        repeat(8) {n->
            val calls=RoomToolInvocationStore(db);val call=invocation(request,"call-$n")
            calls.begin(call);calls.running(call);calls.finish(call,McpToolOutput("{}",false))
        }
        denied("CALL_LIMIT"){RoomToolInvocationStore(db).begin(invocation(request,"ninth"))}
        val second=requests.prepare(role.scope(),chat.id,"changed",UUID.randomUUID().toString())
        val ext=ExtensionRepository(db,credentials)
        ext.replaceCatalog(server.id,server.version,listOf(tool.copy(description="new")))
        denied {RoomToolInvocationStore(db).begin(invocation(second))}
        val next=requests.prepare(role.scope(),chat.id,"no implicit grant",UUID.randomUUID().toString())
        assertTrue(next.tools.isEmpty())
        try {ext.save(server.id,server.version,"bad","https://other.invalid/mcp","ref",1000);fail()}
        catch(e:ChatFailure){assertEquals("CREDENTIAL",e.code)}
    }

    @Test fun concurrentBeginsShareOneDurableBudgetAndRoleDeletionRemovesGrants()=fixture {db->
        val (role,chat,server)=setup(db)
        val request=RoomRequestStore(db).prepare(role.scope(),chat.id,"concurrent",UUID.randomUUID().toString())
        val results=withContext(Dispatchers.Default) {
            supervisorScope {
                (0 until 20).map {n->async {
                    try {RoomToolInvocationStore(db).begin(invocation(request,"call-$n"));"ACCEPTED"}
                    catch(e:ToolFailure){e.code}
                }}.awaitAll()
            }
        }
        assertEquals(8,results.count {it=="ACCEPTED"});assertEquals(12,results.count {it=="CALL_LIMIT"})
        assertEquals(8,db.extensions().countCalls(request.requestId,request.assistantId))
        RoleRepository(db).deleteRole(role.scope())
        assertEquals(0,db.extensions().countCalls(request.requestId,request.assistantId))
        assertTrue(db.extensions().grants(role.id,role.spaceId).first().isEmpty())
        assertNotNull(db.extensions().extension(server.id));assertNotNull(db.dao().config("config"))
    }
}
