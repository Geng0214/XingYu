package com.ai.assistance.operit.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ai.assistance.operit.data.model.McpExtension

@Composable internal fun ToolPkgPreviewDialog(state:CompanionState,vm:CompanionViewModel) {
    val preview=state.toolPkgPreview?:return
    AlertDialog(onDismissRequest=vm::dismissToolPkg,title={Text("安装 ${preview.name}？")},text={Column(
        Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(preview.description)
        Text("版本 ${preview.packageVersion} · API ${preview.apiVersion}",style=MaterialTheme.typography.labelLarge)
        SelectionContainer {Text(preview.packageId,style=MaterialTheme.typography.bodySmall)}
        Text("能力范围",style=MaterialTheme.typography.titleSmall)
        Text("本机脚本计算：日期、文本、数字和 JSON。未提供网络、设备文件、聊天数据库或凭据接口。")
        Text("确认后会在受限运行时验证注册入口，再加密保存。安装后默认停用，启用并为角色授权后，每次调用仍需确认。")
        if(state.toolPackages.any {it.packageId==preview.packageId})Text("已安装同标识的插件。本次会创建独立实例，不覆盖旧插件或授权。",color=MaterialTheme.colorScheme.primary)
        Text("包含 ${preview.tools.size} 个工具",style=MaterialTheme.typography.titleSmall)
        preview.tools.forEach {tool->
            Text(tool.name,style=MaterialTheme.typography.labelLarge)
            Text(tool.definition.description,style=MaterialTheme.typography.bodySmall)
            SelectionContainer {Text(tool.definition.inputSchema,style=MaterialTheme.typography.bodySmall)}
        }
        preview.warnings.forEach {Text(it,style=MaterialTheme.typography.bodySmall)}
        SelectionContainer {Text("SHA-256\n${preview.sha256}",style=MaterialTheme.typography.bodySmall)}
        if(state.toolPkgInstalling)Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(24.dp));Text("正在验证并安装…")
        }
    }},confirmButton={TextButton(onClick=vm::installToolPkg,enabled=!state.working){Text("验证并安装")}},
        dismissButton={TextButton(onClick=vm::dismissToolPkg){Text(if(state.toolPkgInstalling)"取消安装"else"取消")}})
}

@Composable internal fun LocalToolPkgEditor(server:McpExtension,state:CompanionState,vm:CompanionViewModel,onDeleted:()->Unit) {
    var deleting by remember(server.id){mutableStateOf(false)}
    val info=state.toolPackages.firstOrNull {it.extensionId==server.id}
    val tools by remember(server.id){vm.extensionTools(server.id)}.collectAsStateWithLifecycle(emptyList())
    Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text(server.name,style=MaterialTheme.typography.headlineSmall)
        Text("本机 ToolPkg · 纯工具子包",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
        info?.let {Text("版本 ${it.packageVersion} · API ${it.apiVersion}");SelectionContainer {Text(it.packageId,style=MaterialTheme.typography.bodySmall)}}
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)){Text("启用插件",style=MaterialTheme.typography.titleMedium);Text("启用后，请在角色的工具权限中选择工具。",style=MaterialTheme.typography.bodySmall)}
            Switch(server.enabled,{vm.enableExtension(server,it)},enabled=!state.working,modifier=Modifier.semantics{contentDescription="启用 ${server.name}"})
        }
        Text("本机计算，无网络、设备文件或聊天数据库接口。每次执行使用独立实例；启停会停止当前生成并撤回旧授权。",style=MaterialTheme.typography.bodyMedium)
        HorizontalDivider();Text("工具 · ${tools.size}",style=MaterialTheme.typography.titleMedium)
        tools.forEach {tool->Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(tool.name,style=MaterialTheme.typography.titleSmall);Text(tool.description,style=MaterialTheme.typography.bodySmall)
        }}
        info?.let {SelectionContainer {Text("内容校验 SHA-256\n${it.sha256}",style=MaterialTheme.typography.bodySmall)}}
        TextButton(enabled=!state.working,onClick={deleting=true}){Text("卸载插件",color=MaterialTheme.colorScheme.error)}
    }
    if(deleting)AlertDialog(onDismissRequest={deleting=false},title={Text("卸载 ${server.name}？")},text={Text("将停止当前生成，删除加密插件内容并撤回所有角色授权。所属对话中的历史调用记录保留。")},
        confirmButton={TextButton(enabled=!state.working,onClick={deleting=false;vm.deleteExtension(server,onDeleted)}){Text("卸载")}},dismissButton={TextButton(onClick={deleting=false}){Text("保留")}})
}
