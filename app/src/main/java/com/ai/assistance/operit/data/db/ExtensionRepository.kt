package com.ai.assistance.operit.data.db

import androidx.room.withTransaction
import com.ai.assistance.operit.core.tools.*
import com.ai.assistance.operit.core.tools.mcp.*
import com.ai.assistance.operit.data.model.*
import com.ai.assistance.operit.data.security.Credentials
import org.json.JSONObject
import java.util.UUID

class ExtensionRepository(private val db:AppDatabase,private val credentials:Credentials) {
    private val dao get()=db.extensions()
    private suspend fun current(id:String,version:Long):McpExtension {
        val row=dao.extension(id)?:throw ToolFailure("ACCESS")
        if(row.version!=version)throw ToolFailure("CONFIG_CHANGED")
        return row
    }
    suspend fun save(id:String?,expectedVersion:Long?,name:String,endpoint:String,credentialRef:String?,timeoutMs:Long):McpExtension= db.withTransaction {
        require(name.isNotBlank()&&name.length<=80&&timeoutMs in 250..300_000)
        val address=RemoteMcpSession.normalizeEndpoint(endpoint)
        // A caller cannot reuse a credential merely by copying its reference to a different destination.
        if(credentialRef!=null)credentials.read(credentialRef,address)
        val old=if(id==null) {require(expectedVersion==null);null}else current(id,requireNotNull(expectedVersion))
        if(old!=null&&ToolPkgAddress.isLocal(old.endpoint))throw ToolFailure("ACCESS")
        if(old!=null) {
            dao.invalidateExtension(old.id,System.currentTimeMillis())
            // A role's consent belongs to the saved endpoint and catalog; a replacement must not inherit it by tool name.
            dao.revokeExtension(old.id)
            dao.deleteTools(old.id)
        }
        val row=McpExtension(id?:UUID.randomUUID().toString(),name.trim(),address,credentialRef,timeoutMs,false,(old?.version?:0)+1)
        dao.saveExtension(row)
        row
    }
    suspend fun setEnabled(id:String,expectedVersion:Long,enabled:Boolean):McpExtension=db.withTransaction {
        val old=current(id,expectedVersion)
        if(old.enabled==enabled)return@withTransaction old
        dao.invalidateExtension(id,System.currentTimeMillis())
        dao.revokeExtension(id)
        old.copy(enabled=enabled,version=old.version+1).also {dao.saveExtension(it)}
    }
    suspend fun delete(id:String,expectedVersion:Long)=db.withTransaction {
        current(id,expectedVersion)
        dao.invalidateExtension(id,System.currentTimeMillis())
        dao.deleteExtension(id)
    }
    suspend fun replaceCatalog(id:String,expectedVersion:Long,tools:List<McpToolDefinition>):McpExtension=db.withTransaction {
        val old=current(id,expectedVersion)
        if(ToolPkgAddress.isLocal(old.endpoint))throw ToolFailure("ACCESS")
        require(tools.size<=128&&tools.map {it.name}.distinct().size==tools.size)
        tools.forEach {
            require(it.name.isNotBlank()&&it.name.length<=128&&it.description.length<=8000&&it.inputSchema.toByteArray().size<=65536)
            StrictJson(it.inputSchema).validate()
            require(JSONObject(it.inputSchema).getString("type")=="object")
        }
        // Catalog changes require fresh grants; pending requests keep their snapshot but lose authorization.
        dao.invalidateExtension(id,System.currentTimeMillis())
        dao.deleteTools(id)
        dao.insertTools(tools.map {ExtensionTool(id,it.name,it.description,it.inputSchema)})
        old.copy(version=old.version+1).also {dao.saveExtension(it)}
    }
    suspend fun grant(scope:RoleAccessScope,id:String,expectedVersion:Long,name:String,enabled:Boolean)=db.withTransaction {
        RoleRepository(db).requireRole(scope)
        val extension=current(id,expectedVersion)
        if(enabled&&!extension.enabled)throw ToolFailure("DISABLED")
        val target=dao.tool(id,name)?:throw ToolFailure("TOOL_CHANGED")
        val old=dao.grant(scope.roleId,scope.spaceId,id,name)
        if(old?.enabled==enabled)return@withTransaction
        if(enabled) {
            val granted=dao.grantedTools(scope.roleId,scope.spaceId)
            if(granted.size>=32||granted.sumOf {it.description.length+it.inputSchema.length}+target.description.length+target.inputSchema.length>65536)throw ToolFailure("TOOL_CONTEXT_LIMIT")
        }
        dao.invalidateGrant(scope.roleId,scope.spaceId,id,name,System.currentTimeMillis())
        dao.saveGrant(RoleToolGrant(scope.roleId,scope.spaceId,id,name,enabled,(old?.version?:0)+1))
    }
    suspend fun revokeGrant(scope:RoleAccessScope,id:String,name:String)=db.withTransaction {
        RoleRepository(db).requireRole(scope)
        val old=dao.grant(scope.roleId,scope.spaceId,id,name)?:return@withTransaction
        if(!old.enabled)return@withTransaction
        dao.invalidateGrant(scope.roleId,scope.spaceId,id,name,System.currentTimeMillis())
        dao.saveGrant(old.copy(enabled=false,version=old.version+1))
    }
}

class RoomToolInvocationStore(private val db:AppDatabase):ToolInvocationStore {
    private val dao get()=db.extensions()
    suspend fun freeze(owner:ToolRequestOwner):List<ToolPermission> =db.withTransaction {
        val scope=owner.scope
        dao.activeRequest(scope.roleId,scope.spaceId,owner.chatId,owner.requestId,owner.assistantId)?:throw ToolFailure("ACCESS")
        val grants=dao.grantedTools(scope.roleId,scope.spaceId)
        if(grants.size>32||grants.sumOf {it.description.length+it.inputSchema.length}>65536)throw ToolFailure("TOOL_CONTEXT_LIMIT")
        val rows=grants.map {RequestToolPermission(owner.requestId,it.extensionId,it.toolName,scope.roleId,scope.spaceId,owner.chatId,
            owner.assistantId,it.configVersion,it.grantVersion,it.endpoint,it.credentialRef,it.timeoutMs,it.description,it.inputSchema)}
        dao.insertPermissions(rows)
        rows.map {it.permission()}
    }
    private suspend fun authorized(call:ToolInvocation) {
        val o=call.owner;val p=call.permission
        val frozen=dao.authorized(o.scope.roleId,o.scope.spaceId,o.chatId,o.requestId,o.assistantId,p.extensionId,p.definition.name)
            ?:throw ToolFailure("ACCESS")
        if(frozen.permission()!=p)throw ToolFailure("ACCESS")
    }
    private suspend fun read(call:ToolInvocation):ToolInvocationRecord? {
        val o=call.owner
        return dao.invocation(o.scope.roleId,o.scope.spaceId,o.chatId,o.requestId,o.assistantId,call.callId)?.also {
            if(it.extensionId!=call.permission.extensionId||it.toolName!=call.permission.definition.name||it.arguments!=call.arguments)throw ToolFailure("ACCESS")
        }
    }
    override suspend fun begin(call:ToolInvocation)=db.withTransaction {
        authorized(call)
        require(call.callId.isNotBlank()&&call.callId.length<=128&&call.arguments.toByteArray().size<=65536)
        StrictJson(call.arguments).validate();JSONObject(call.arguments)
        if(read(call)!=null)throw ToolFailure("DUPLICATE")
        val o=call.owner
        if(dao.countCalls(o.requestId,o.assistantId)>=ToolDispatcher.MAX_CALLS)throw ToolFailure("CALL_LIMIT")
        dao.insertInvocation(ToolInvocationRecord(o.requestId,call.callId,o.scope.roleId,o.scope.spaceId,o.chatId,o.assistantId,
            call.permission.extensionId,call.permission.definition.name,call.arguments,"",ToolInvocationState.WAITING_APPROVAL.name,"",System.currentTimeMillis(),0))
    }
    override suspend fun validate(call:ToolInvocation)=db.withTransaction {
        authorized(call)
        val row=read(call)?:throw ToolFailure("ACCESS")
        if(row.state !in setOf("WAITING_APPROVAL","RUNNING"))throw ToolFailure("ACCESS")
    }
    private suspend fun transition(call:ToolInvocation,expected:String,state:String,result:String="",error:String="") {
        val o=call.owner
        if(dao.transition(o.scope.roleId,o.scope.spaceId,o.chatId,o.requestId,o.assistantId,call.callId,expected,state,result,error,
            if(state=="RUNNING")0 else System.currentTimeMillis())!=1)throw ToolFailure("ACCESS")
    }
    override suspend fun running(call:ToolInvocation)=db.withTransaction {
        validate(call)
        transition(call,"WAITING_APPROVAL","RUNNING")
    }
    override suspend fun finish(call:ToolInvocation,result:McpToolOutput)=db.withTransaction {
        validate(call)
        if(result.json.length>131072)throw ToolFailure("LIMIT")
        transition(call,"RUNNING",if(result.isError)"FAILED"else"COMPLETE",result.json,if(result.isError)"TOOL_ERROR"else"")
    }
    override suspend fun terminate(call:ToolInvocation,state:ToolInvocationState,code:String)=db.withTransaction {
        require(state==ToolInvocationState.CANCELLED||state==ToolInvocationState.FAILED)
        require(Regex("[A-Z_]{1,40}").matches(code))
        val row=read(call)?:return@withTransaction
        if(row.state in setOf("WAITING_APPROVAL","RUNNING"))transition(call,row.state,state.name,error=code)
    }
}
