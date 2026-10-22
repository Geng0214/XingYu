package com.ai.assistance.operit.core.tools.packTool

import com.ai.assistance.operit.core.tools.*
import com.ai.assistance.operit.core.tools.javascript.QuickJsRunner
import com.ai.assistance.operit.core.tools.mcp.*
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class LocalToolPkgSession(private val load:suspend ()->ToolPkgBundle,
    private val execute:suspend (ToolPkgBundle,ToolPkgExport,String)->String={bundle,tool,args->
        QuickJsRunner().execute(bundle.sources,tool.entry,tool.function,args,mainEntry=bundle.main)
    }):ToolRuntimeSession {
    private var bundle:ToolPkgBundle?=null
    private var closed=false
    override suspend fun connect(){check(!closed&&bundle==null);bundle=load()}
    private fun current():ToolPkgBundle {if(closed)throw ToolFailure("JS_CLOSED");return bundle?:throw ToolFailure("NOT_CONNECTED")}
    override suspend fun listTools()=current().tools.map {it.definition}
    override suspend fun callTool(name:String,arguments:String):McpToolOutput {
        val loaded=current();val tool=loaded.tools.singleOrNull {it.name==name}?:throw ToolFailure("ACCESS")
        currentCoroutineContext().ensureActive()
        val text=execute(loaded,tool,arguments)
        currentCoroutineContext().ensureActive();current()
        val result=JSONObject().put("isError",false).put("content",JSONArray().put(JSONObject().put("type","text").put("text",text))).toString()
        if(result.length>131072)throw ToolFailure("OUTPUT_LIMIT")
        return McpToolOutput(result,false)
    }
    override suspend fun close(){closed=true;bundle=null}
}
