package com.ai.assistance.operit.core.tools.mcp

/** Transport boundary adapted from Operit's McpRuntimeSession at companion-m0-handoff. */
data class McpToolDefinition(val name: String,val description: String,val inputSchema: String)
data class McpToolOutput(val json: String,val isError: Boolean)

interface McpSession:com.ai.assistance.operit.core.tools.ToolRuntimeSession

class McpFailure(val code: String) : Exception(code)
