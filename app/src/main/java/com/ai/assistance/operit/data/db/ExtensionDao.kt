package com.ai.assistance.operit.data.db

import androidx.room.*
import com.ai.assistance.operit.data.model.*
import kotlinx.coroutines.flow.Flow

data class ToolOperationSummary(
    val roleId:String,
    val spaceId:String,
    val roleName:String?,
    val extensionId:String,
    val extensionName:String?,
    val toolName:String,
    val state:String,
    val createdAt:Long
)

@Dao
interface ExtensionDao {
    @Query("SELECT * FROM mcp_extensions ORDER BY name,id") fun extensions():Flow<List<McpExtension>>
    @Query("SELECT * FROM mcp_extensions WHERE id=:id") suspend fun extension(id:String):McpExtension?
    @Upsert suspend fun saveExtension(extension:McpExtension)
    @Query("DELETE FROM mcp_extensions WHERE id=:id") suspend fun deleteExtension(id:String)
    @Query("SELECT * FROM extension_tools WHERE extensionId=:id ORDER BY name") fun tools(id:String):Flow<List<ExtensionTool>>
    @Query("SELECT * FROM extension_tools WHERE extensionId=:id AND name=:name") suspend fun tool(id:String,name:String):ExtensionTool?
    @Query("DELETE FROM extension_tools WHERE extensionId=:id") suspend fun deleteTools(id:String)
    @Insert suspend fun insertTools(tools:List<ExtensionTool>)
    @Query("SELECT g.* FROM role_tool_grants g JOIN roles r ON r.id=g.roleId AND r.spaceId=g.spaceId WHERE r.id=:roleId AND r.spaceId=:spaceId ORDER BY extensionId,toolName")
    fun grants(roleId:String,spaceId:String):Flow<List<RoleToolGrant>>
    @Query("SELECT g.* FROM role_tool_grants g JOIN roles r ON r.id=g.roleId AND r.spaceId=g.spaceId WHERE r.id=:roleId AND r.spaceId=:spaceId AND g.extensionId=:extensionId AND g.toolName=:name")
    suspend fun grant(roleId:String,spaceId:String,extensionId:String,name:String):RoleToolGrant?
    @Upsert suspend fun saveGrant(grant:RoleToolGrant)
    @Query("UPDATE role_tool_grants SET enabled=0,version=version+1 WHERE extensionId=:id AND enabled=1") suspend fun revokeExtension(id:String)
    @Query("SELECT e.id AS extensionId,t.name AS toolName,t.description,t.inputSchema,e.endpoint,e.credentialRef,e.timeoutMs,e.version AS configVersion,g.version AS grantVersion FROM role_tool_grants g JOIN roles r ON r.id=g.roleId AND r.spaceId=g.spaceId JOIN mcp_extensions e ON e.id=g.extensionId JOIN extension_tools t ON t.extensionId=e.id AND t.name=g.toolName WHERE r.id=:roleId AND r.spaceId=:spaceId AND g.enabled=1 AND e.enabled=1 ORDER BY e.id,t.name")
    suspend fun grantedTools(roleId:String,spaceId:String):List<GrantedTool>
    @Insert suspend fun insertPermissions(rows:List<RequestToolPermission>)
    @Query("SELECT p.* FROM request_tool_permissions p JOIN roles r ON r.id=p.roleId AND r.spaceId=p.spaceId JOIN chats c ON c.id=p.chatId AND c.roleId=r.id JOIN messages m ON m.id=p.assistantId AND m.chatId=c.id AND m.requestId=p.requestId JOIN mcp_extensions e ON e.id=p.extensionId AND e.version=p.configVersion JOIN role_tool_grants g ON g.roleId=r.id AND g.spaceId=r.spaceId AND g.extensionId=e.id AND g.toolName=p.toolName AND g.version=p.grantVersion JOIN extension_tools t ON t.extensionId=e.id AND t.name=p.toolName AND t.description=p.description AND t.inputSchema=p.inputSchema WHERE p.roleId=:roleId AND p.spaceId=:spaceId AND p.chatId=:chatId AND p.requestId=:requestId AND p.assistantId=:assistantId AND p.extensionId=:extensionId AND p.toolName=:name AND m.kind='assistant' AND m.state IN ('QUEUED','WAITING','GENERATING') AND e.enabled=1 AND g.enabled=1")
    suspend fun authorized(roleId:String,spaceId:String,chatId:String,requestId:String,assistantId:String,extensionId:String,name:String):RequestToolPermission?
    @Query("SELECT m.* FROM messages m JOIN chats c ON c.id=m.chatId JOIN roles r ON r.id=c.roleId WHERE r.id=:roleId AND r.spaceId=:spaceId AND c.id=:chatId AND m.id=:assistantId AND m.requestId=:requestId AND m.kind='assistant' AND m.state IN ('QUEUED','WAITING','GENERATING')")
    suspend fun activeRequest(roleId:String,spaceId:String,chatId:String,requestId:String,assistantId:String):MessageEntity?
    @Query("SELECT i.* FROM tool_invocations i JOIN roles r ON r.id=i.roleId AND r.spaceId=i.spaceId JOIN chats c ON c.id=i.chatId AND c.roleId=r.id JOIN messages m ON m.id=i.assistantId AND m.chatId=c.id AND m.requestId=i.requestId WHERE r.id=:roleId AND r.spaceId=:spaceId AND c.id=:chatId AND i.requestId=:requestId AND i.assistantId=:assistantId AND i.callId=:callId AND m.kind='assistant'")
    suspend fun invocation(roleId:String,spaceId:String,chatId:String,requestId:String,assistantId:String,callId:String):ToolInvocationRecord?
    @Query("SELECT COUNT(*) FROM tool_invocations WHERE requestId=:requestId AND assistantId=:assistantId")
    suspend fun countCalls(requestId:String,assistantId:String):Int
    @Insert suspend fun insertInvocation(row:ToolInvocationRecord)
    @Query("UPDATE tool_invocations SET state=:state,result=:result,errorCode=:error,finishedAt=:finishedAt WHERE requestId=:requestId AND callId=:callId AND assistantId=:assistantId AND roleId=:roleId AND spaceId=:spaceId AND chatId=:chatId AND state=:expected AND EXISTS (SELECT 1 FROM chats c JOIN roles r ON r.id=c.roleId JOIN messages m ON m.chatId=c.id WHERE c.id=:chatId AND r.id=:roleId AND r.spaceId=:spaceId AND m.id=:assistantId AND m.requestId=:requestId AND m.kind='assistant')")
    suspend fun transition(roleId:String,spaceId:String,chatId:String,requestId:String,assistantId:String,callId:String,expected:String,state:String,result:String,error:String,finishedAt:Long):Int
    @Query("SELECT i.* FROM tool_invocations i JOIN roles r ON r.id=i.roleId AND r.spaceId=i.spaceId JOIN chats c ON c.id=i.chatId AND c.roleId=r.id JOIN messages m ON m.id=i.assistantId AND m.requestId=i.requestId AND m.chatId=c.id WHERE r.id=:roleId AND r.spaceId=:spaceId AND c.id=:chatId ORDER BY i.createdAt,i.callId")
    fun invocations(roleId:String,spaceId:String,chatId:String):Flow<List<ToolInvocationRecord>>
    @Query("SELECT i.roleId AS roleId,i.spaceId AS spaceId,r.name AS roleName,i.extensionId AS extensionId,e.name AS extensionName,i.toolName AS toolName,i.state AS state,i.createdAt AS createdAt FROM tool_invocations i LEFT JOIN roles r ON r.id=i.roleId AND r.spaceId=i.spaceId LEFT JOIN mcp_extensions e ON e.id=i.extensionId ORDER BY i.createdAt DESC,i.callId DESC LIMIT 20")
    fun recentOperationSummaries():Flow<List<ToolOperationSummary>>
    @Query("UPDATE tool_invocations SET state='FAILED',errorCode='REVOKED',finishedAt=:now WHERE extensionId=:id AND state IN ('WAITING_APPROVAL','RUNNING')")
    suspend fun invalidateExtension(id:String,now:Long)
    @Query("UPDATE tool_invocations SET state='FAILED',errorCode='REVOKED',finishedAt=:now WHERE roleId=:roleId AND spaceId=:spaceId AND extensionId=:id AND toolName=:name AND state IN ('WAITING_APPROVAL','RUNNING')")
    suspend fun invalidateGrant(roleId:String,spaceId:String,id:String,name:String,now:Long)
    @Query("UPDATE tool_invocations SET state='FAILED',errorCode='INTERRUPTED',finishedAt=:now WHERE state IN ('WAITING_APPROVAL','RUNNING')")
    suspend fun recoverInterrupted(now:Long):Int
}
