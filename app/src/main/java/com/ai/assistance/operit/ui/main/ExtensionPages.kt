package com.ai.assistance.operit.ui.main

import com.ai.assistance.operit.ui.main.ThemedIcon as Icon

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.ai.assistance.operit.data.db.ToolPkgAddress
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ai.assistance.operit.data.model.*

@Composable internal fun ExtensionPage(state:CompanionState,vm:CompanionViewModel,onBack:()->Unit) {
    var editing by remember {mutableStateOf(false)}
    var selected by remember {mutableStateOf<String?>(null)}
    var roleTarget by remember {mutableStateOf<RoleEntity?>(null)}
    val files=LocalFileActions.current
    BackHandler {if(editing)editing=false else onBack()}
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick={if(editing)editing=false else onBack()}){Icon(Icons.AutoMirrored.Filled.ArrowBack,"返回")}
            Text(if(editing)"扩展详情"else"扩展与工具",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
            if(!editing)IconButton(enabled=!state.working,onClick={selected=null;editing=true}){Icon(Icons.Default.Add,"添加 MCP 服务")}
        }
        if(editing) {
            val server=state.extensions.firstOrNull {it.id==selected}
            if(selected!=null&&server==null)Text("此扩展已删除。",Modifier.padding(24.dp))
            else if(server!=null&&ToolPkgAddress.isLocal(server.endpoint))LocalToolPkgEditor(server,state,vm){selected=null;editing=false}
            else ExtensionEditor(server,state,vm,onSaved={selected=it},onDeleted={selected=null;editing=false})
        } else Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("让角色使用你选择的工具",style=MaterialTheme.typography.titleMedium)
            Text("连接 MCP 服务，或安装本机 ToolPkg 插件，再为角色授权。每次执行前都会展示具体参数，请核对后确认。",style=MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled=!state.working,onClick=files.importToolPkg){Text("导入 ToolPkg")}
                TextButton(enabled=!state.working,onClick=vm::previewTimeExample){Text("试用时间示例")}
            }
            if(state.extensions.isEmpty())OutlinedButton(onClick={selected=null;editing=true}){Text("添加第一个 MCP 服务")}
            state.extensions.forEach {server->
                Surface(shape=RoundedCornerShape(18.dp),modifier=Modifier.fillMaxWidth().clickable {selected=server.id;editing=true}) {
                    Row(Modifier.padding(18.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Default.Extension,null,tint=MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            Text(server.name,style=MaterialTheme.typography.titleMedium)
                            Text(if(server.enabled)"已启用 · 按角色授权"else"已停用",style=MaterialTheme.typography.labelMedium)
                            Text(if(ToolPkgAddress.isLocal(server.endpoint))"本机 ToolPkg · 日期与文本计算"else server.endpoint,style=MaterialTheme.typography.bodySmall)
                        }
                        Icon(Icons.Default.ChevronRight,null)
                    }
                }
            }
            Text("角色工具权限",style=MaterialTheme.typography.titleMedium,modifier=Modifier.padding(top=8.dp))
            if(state.roles.isEmpty())Text("先在“角色”页创建一个角色。")
            state.roles.forEach {role->OutlinedButton(modifier=Modifier.fillMaxWidth(),onClick={roleTarget=role}){Text("${role.name} · 选择工具")}}
            Text("支持 MCP Streamable HTTP 和 API 1.0.0 的纯工具子包。依赖设备、网络、界面或 Hook 的旧 ToolPkg 尚不兼容。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    roleTarget?.let {role->RoleToolsDialog(role,state,vm){roleTarget=null}}
}

@Composable private fun ExtensionEditor(server:McpExtension?,state:CompanionState,vm:CompanionViewModel,onSaved:(String)->Unit,onDeleted:()->Unit) {
    var name by remember(server?.id,server?.version){mutableStateOf(server?.name?:"")}
    var endpoint by remember(server?.id,server?.version){mutableStateOf(server?.endpoint?:"")}
    var key by remember(server?.id){mutableStateOf("")}
    var anonymous by remember(server?.id,server?.version){mutableStateOf(server?.credentialRef==null)}
    var timeout by remember(server?.id,server?.version){mutableStateOf(((server?.timeoutMs?:60000)/1000).toString())}
    var confirmSave by remember {mutableStateOf(false)}
    var deleting by remember {mutableStateOf(false)}
    var detail by remember {mutableStateOf<ExtensionTool?>(null)}
    val normalized=remember(endpoint){try {com.ai.assistance.operit.core.tools.mcp.RemoteMcpSession.normalizeEndpoint(endpoint)}catch(_:Exception){null}}
    val needsKey=!anonymous&&key.isBlank()&&(server?.credentialRef==null||server.endpoint!=normalized)
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(name,{if(it.length<=80)name=it},Modifier.fillMaxWidth(),enabled=!state.working,label={Text("服务名称")},singleLine=true)
        OutlinedTextField(endpoint,{if(it.length<=2048)endpoint=it},Modifier.fillMaxWidth(),enabled=!state.working,isError=endpoint.isNotBlank()&&normalized==null,label={Text("完整 MCP 地址")},supportingText={Text(if(endpoint.isNotBlank()&&normalized==null)"请填写 HTTPS 完整地址，不含用户名、密码、查询参数或片段。"else"例如 https://example.com/mcp。只允许 HTTPS，不自动跳转。")})
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("此服务不需要凭据",Modifier.weight(1f))
            Switch(checked=anonymous,onCheckedChange={anonymous=it},enabled=!state.working,modifier=Modifier.semantics{contentDescription="此服务不需要凭据"})
        }
        if(!anonymous)OutlinedTextField(key,{if(it.length<=8192)key=it},Modifier.fillMaxWidth(),enabled=!state.working,label={Text("Bearer Token")},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),singleLine=true,
            supportingText={Text(if(server?.credentialRef!=null)"地址不变时，留空保留已加密的凭据。"else"凭据独立加密保存，仅用于这个地址。")})
        OutlinedTextField(timeout,{if(it.length<=3&&it.all(Char::isDigit))timeout=it},Modifier.fillMaxWidth(),enabled=!state.working,label={Text("超时（秒）")},singleLine=true,supportingText={Text("1～300 秒")})
        if(needsKey)Text("请为此地址填写凭据，或确认该服务不需要凭据。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
        Button(enabled=!state.working&&name.isNotBlank()&&normalized!=null&&!needsKey&&(timeout.toLongOrNull()?:0) in 1..300,onClick={confirmSave=true}){Text("保存连接")}
        if(server!=null) {
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)){Text("启用扩展",style=MaterialTheme.typography.titleMedium);Text("启用后仍需为角色选择工具。",style=MaterialTheme.typography.bodySmall)}
                Switch(server.enabled,{vm.enableExtension(server,it)},enabled=!state.working,modifier=Modifier.semantics{contentDescription="启用 ${server.name}"})
            }
            if(state.extensionTesting==server.id) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp));Text("正在连接并读取工具…")
                }
                TextButton(onClick={vm.cancelExtensionTest()}){Text("取消连接")}
            } else OutlinedButton(enabled=!state.working,onClick={vm.refreshTools(server)}){Text("测试连接并获取工具")}
            Text("更新连接、停用或刷新列表会停止当前生成并撤回旧授权。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            val tools by remember(server.id){vm.extensionTools(server.id)}.collectAsStateWithLifecycle(emptyList())
            Text("工具列表 · ${tools.size}",style=MaterialTheme.typography.titleMedium)
            if(tools.isEmpty())Text("尚未获取工具。请先测试连接。",style=MaterialTheme.typography.bodyMedium)
            tools.forEach {tool->TextButton(onClick={detail=tool},modifier=Modifier.fillMaxWidth()){Text(tool.name,Modifier.weight(1f));Icon(Icons.Default.ChevronRight,null)}}
            TextButton(enabled=!state.working,onClick={deleting=true}){Text("删除扩展",color=MaterialTheme.colorScheme.error)}
        }
        Spacer(Modifier.height(24.dp))
    }
    if(confirmSave)AlertDialog(onDismissRequest={confirmSave=false},title={Text("确认连接目的地")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(normalized?:endpoint);Text(if(anonymous)"不发送认证凭据。"else"凭据只会发送到以上完整地址。更换地址必须重新填写凭据。")
        if(server!=null)Text("保存会停止当前生成、停用此扩展并清除旧工具授权。")
    }},confirmButton={TextButton(enabled=!state.working,onClick={confirmSave=false;vm.saveExtension(server,name,endpoint,key,anonymous,timeout.toLong()*1000){key="";onSaved(it)}}){Text("确认并保存")}},dismissButton={TextButton(onClick={confirmSave=false}){Text("返回检查")}})
    if(deleting&&server!=null)AlertDialog(onDismissRequest={deleting=false},title={Text("删除 ${server.name}？")},text={Text("将停止当前生成并撤回所有角色授权。所属对话中的历史调用记录会保留。")},confirmButton={TextButton(enabled=!state.working,onClick={deleting=false;vm.deleteExtension(server,onDeleted)}){Text("删除扩展")}},dismissButton={TextButton(onClick={deleting=false}){Text("保留")}})
    detail?.let {tool->AlertDialog(onDismissRequest={detail=null},title={Text(tool.name)},text={SelectionContainer {Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)){Text(tool.description);Text("参数定义");Text(tool.inputSchema)}}},confirmButton={TextButton(onClick={detail=null}){Text("关闭")}})}
}

@Composable internal fun RoleToolsDialog(role:RoleEntity,state:CompanionState,vm:CompanionViewModel,onDismiss:()->Unit) {
    val grants by remember(role.id,role.spaceId){vm.roleToolGrants(role.scope())}.collectAsStateWithLifecycle(emptyList())
    AlertDialog(onDismissRequest=onDismiss,title={Text("${role.name}的工具")},text={Column(Modifier.heightIn(max=460.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("选择该角色可使用的工具，最多 32 个。修改授权会停止该角色正在进行的生成。")
        if(role.isRaw())Text("当前使用原味模型，不会向模型提供工具。授权会保留，切回角色模式后继续生效。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
        if(state.extensions.isEmpty())Text("请先到“设置 → 扩展与工具”添加服务。")
        state.extensions.forEach {server->
            val tools by remember(server.id){vm.extensionTools(server.id)}.collectAsStateWithLifecycle(emptyList())
            HorizontalDivider();Text(server.name,style=MaterialTheme.typography.titleMedium)
            if(!server.enabled)Text("请先在扩展设置中启用此服务。",style=MaterialTheme.typography.bodySmall)
            tools.forEach {tool->
                val selected=grants.any {it.extensionId==server.id&&it.toolName==tool.name&&it.enabled}
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f)){Text(tool.name);Text(tool.description,style=MaterialTheme.typography.bodySmall)}
                    Switch(selected,{vm.grantTool(role.scope(),server,tool.name,it)},enabled=!state.working&&server.enabled&&(selected||grants.count {it.enabled}<32),modifier=Modifier.semantics{contentDescription="允许${role.name}使用 ${tool.name}"})
                }
            }
        }
    }},confirmButton={TextButton(onClick=onDismiss){Text("完成")}})
}

@Composable internal fun ToolApprovalDialog(state:CompanionState,vm:CompanionViewModel) {
    val pending by vm.pendingApproval.collectAsStateWithLifecycle()
    pending?.let {ticket->
        val call=ticket.call
        val role=state.roles.firstOrNull {it.scope()==call.owner.scope}
        AlertDialog(onDismissRequest={vm.answerTool(ticket.id,false)},title={Text("${role?.name?:"角色"}请求使用工具")},text={SelectionContainer {
            Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(call.permission.definition.name,style=MaterialTheme.typography.titleMedium)
                if(ToolPkgAddress.isLocal(call.permission.endpoint)) {
                    Text("在本机运行插件");Text(state.extensions.firstOrNull {it.id==call.permission.extensionId}?.name?:call.permission.extensionId)
                    Text("本机计算；未提供网络、文件或聊天数据库接口。",style=MaterialTheme.typography.bodySmall)
                }else {Text("发送到");Text(call.permission.endpoint)}
                Text("本次完整参数");Text(call.arguments)
                Text("确认后才执行。工具结果会交给本次请求的模型用于回复；外部操作执行后可能无法撤销。",style=MaterialTheme.typography.bodySmall)
            }
        }},confirmButton={TextButton(onClick={vm.answerTool(ticket.id,true)}){Text("允许本次调用")}},dismissButton={TextButton(onClick={vm.answerTool(ticket.id,false)}){Text("拒绝并停止")}})
    }
}

@Composable internal fun ToolHistory(records:List<ToolInvocationRecord>) {
    var open by remember {mutableStateOf(false)}
    if(records.isNotEmpty())TextButton(onClick={open=true},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Extension,null);Spacer(Modifier.width(8.dp));Text("工具记录 · ${records.size}")}
    if(open)AlertDialog(onDismissRequest={open=false},title={Text("本对话的工具记录")},text={SelectionContainer {LazyColumn(Modifier.heightIn(max=480.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        items(records,key={it.requestId+":"+it.callId}) {row->Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(row.toolName,style=MaterialTheme.typography.titleMedium)
            Text(when(row.state){"WAITING_APPROVAL"->"等待确认";"RUNNING"->"执行中";"COMPLETE"->"已完成";"CANCELLED"->"已取消";else->"未完成 · ${extensionErrorText(row.errorCode)}"})
            Text("参数：${row.arguments}",style=MaterialTheme.typography.bodySmall)
            if(row.result.isNotEmpty())Text("结果：${row.result}",style=MaterialTheme.typography.bodySmall)
            HorizontalDivider()
        }}
    }}},confirmButton={TextButton(onClick={open=false}){Text("关闭")}})
}
