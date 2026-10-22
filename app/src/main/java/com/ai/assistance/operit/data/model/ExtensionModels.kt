package com.ai.assistance.operit.data.model

import androidx.room.*
import com.ai.assistance.operit.core.tools.ToolPermission
import com.ai.assistance.operit.core.tools.mcp.McpToolDefinition

@Entity(tableName="mcp_extensions")
data class McpExtension(@PrimaryKey val id:String,val name:String,val endpoint:String,val credentialRef:String?,
    val timeoutMs:Long,val enabled:Boolean=false,val version:Long=1)

@Entity(tableName="extension_tools",primaryKeys=["extensionId","name"],foreignKeys=[
    ForeignKey(entity=McpExtension::class,parentColumns=["id"],childColumns=["extensionId"],onDelete=ForeignKey.CASCADE)])
data class ExtensionTool(val extensionId:String,val name:String,val description:String,val inputSchema:String) {
    fun definition()=McpToolDefinition(name,description,inputSchema)
}

@Entity(tableName="role_tool_grants",primaryKeys=["roleId","spaceId","extensionId","toolName"],indices=[Index("extensionId","toolName")],foreignKeys=[
    ForeignKey(entity=RoleEntity::class,parentColumns=["id"],childColumns=["roleId"],onDelete=ForeignKey.CASCADE),
    ForeignKey(entity=ExtensionTool::class,parentColumns=["extensionId","name"],childColumns=["extensionId","toolName"],onDelete=ForeignKey.CASCADE)])
data class RoleToolGrant(val roleId:String,val spaceId:String,val extensionId:String,val toolName:String,val enabled:Boolean,val version:Long)

// No extension FK: removing a service must retain the request's encrypted audit trail until its chat is deleted.
@Entity(tableName="request_tool_permissions",primaryKeys=["requestId","extensionId","toolName"],indices=[Index("assistantId")],foreignKeys=[
    ForeignKey(entity=MessageEntity::class,parentColumns=["id"],childColumns=["assistantId"],onDelete=ForeignKey.CASCADE)])
data class RequestToolPermission(val requestId:String,val extensionId:String,val toolName:String,val roleId:String,val spaceId:String,
    val chatId:String,val assistantId:String,val configVersion:Long,val grantVersion:Long,val endpoint:String,val credentialRef:String?,
    val timeoutMs:Long,val description:String,val inputSchema:String) {
    fun permission()=ToolPermission(extensionId,configVersion,grantVersion,endpoint,credentialRef,timeoutMs,McpToolDefinition(toolName,description,inputSchema))
}

@Entity(tableName="tool_invocations",primaryKeys=["requestId","callId"],indices=[Index("assistantId"),Index("extensionId"),Index("chatId")],foreignKeys=[
    ForeignKey(entity=MessageEntity::class,parentColumns=["id"],childColumns=["assistantId"],onDelete=ForeignKey.CASCADE)])
data class ToolInvocationRecord(val requestId:String,val callId:String,val roleId:String,val spaceId:String,val chatId:String,
    val assistantId:String,val extensionId:String,val toolName:String,val arguments:String,val result:String,val state:String,
    val errorCode:String,val createdAt:Long,val finishedAt:Long)

data class GrantedTool(val extensionId:String,val toolName:String,val description:String,val inputSchema:String,val endpoint:String,
    val credentialRef:String?,val timeoutMs:Long,val configVersion:Long,val grantVersion:Long)
