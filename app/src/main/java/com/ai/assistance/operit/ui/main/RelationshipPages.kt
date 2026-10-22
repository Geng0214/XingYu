package com.ai.assistance.operit.ui.main

import com.ai.assistance.operit.ui.main.ThemedIcon as Icon

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ai.assistance.operit.api.chat.RequestContext
import com.ai.assistance.operit.data.model.*

@Composable private fun RelationshipSurface(title:String,subtitle:String,onBack:()->Unit,error:String?=null,content:@Composable ColumnScope.()->Unit) {
    Dialog(onDismissRequest=onBack,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"返回")}
                    Column(Modifier.weight(1f)) {
                        Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
                        Text(subtitle,style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=TextOverflow.Ellipsis)
                    }
                }
                if(error!=null)Text(error,Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=8.dp),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                content()
            }
        }
    }
}

@Composable private fun ModeChoice(title:String,summary:String,selected:Boolean,enabled:Boolean,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled=enabled,onClick=onClick).padding(vertical=10.dp),verticalAlignment=Alignment.Top) {
        RadioButton(selected,onClick=onClick,enabled=enabled)
        Column(Modifier.weight(1f).padding(start=8.dp,top=3.dp)) {
            Text(title,style=MaterialTheme.typography.titleSmall)
            Text(summary,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun RelationshipDialog(role:RoleEntity,state:CompanionState,vm:CompanionViewModel,onDismiss:()->Unit) {
    val scope=role.scope()
    val data by remember(scope){vm.relationships(scope)}.collectAsStateWithLifecycle<RelationshipData?>(null)
    var editing by remember(scope){mutableStateOf<RelationshipEntry?>(null)}
    var creating by remember(scope){mutableStateOf(false)}
    var deleting by remember(scope){mutableStateOf<RelationshipEntry?>(null)}
    var preview by remember(scope){mutableStateOf(false)}
    var requestedMode by remember(scope){mutableStateOf<String?>(null)}
    RelationshipSurface("关系与对话模式",role.name,{if(!state.working)onDismiss()},state.relationshipError) {
        val loaded=data
        when {
            loaded==null->Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
            loaded.error!=null->Text(loaded.error,Modifier.padding(24.dp),color=MaterialTheme.colorScheme.error)
            else->LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(horizontal=24.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                item {
                    Text("这段对话怎样认识彼此",style=MaterialTheme.typography.titleMedium)
                    Text("模式决定应用在下一次请求中加入哪些设定；切换不会删除已保存内容。",style=MaterialTheme.typography.bodyMedium,modifier=Modifier.padding(top=6.dp))
                    ModeChoice("角色模式","使用角色人设、用户面具、关系、世界书、回复风格和已授权工具。",!role.isRaw(),!state.working){if(role.isRaw())requestedMode="character"}
                    HorizontalDivider()
                    ModeChoice("原味模型","直接发送会话历史和你的消息，不添加应用提示词、开场白或工具。",role.isRaw(),!state.working){if(!role.isRaw())requestedMode="raw"}
                }
                item {
                    Surface(color=MaterialTheme.colorScheme.surfaceVariant,shape=MaterialTheme.shapes.medium) {
                        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Text(if(role.isRaw())"当前：原味模型"else"当前：角色模式",style=MaterialTheme.typography.titleSmall)
                            Text(if(role.isRaw())"已保存的人设、关系、世界书和工具授权仍在，切回后继续使用。旧聊天历史仍会发给模型。"else"启用的关系会随角色设定提供给模型；它们是你的故事设定，不代表模型真实情绪。",style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                item {FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Button(onClick={creating=true},enabled=!state.working&&loaded.entries.size<Relationships.MAX_ENTRIES){Icon(Icons.Default.Add,null);Spacer(Modifier.width(6.dp));Text("新建关系")}
                    OutlinedButton(onClick={preview=true},enabled=!state.working&&loaded.chats.isNotEmpty()){Icon(Icons.Default.Visibility,null);Spacer(Modifier.width(6.dp));Text("预览实际注入")}
                }}
                item {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Icon(Icons.Default.Hub,null,tint=MaterialTheme.colorScheme.primary)
                        Text("关系设定",Modifier.padding(start=10.dp).weight(1f),style=MaterialTheme.typography.titleMedium)
                        Text("${loaded.entries.size}/${Relationships.MAX_ENTRIES}",style=MaterialTheme.typography.labelLarge)
                    }
                    Text("每条关系只属于当前角色空间，可用于全部会话或一段指定会话。",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=6.dp))
                }
                if(loaded.entries.isEmpty())item {
                    Text("还没有关系设定。可以先记录你与角色的关系，也可以补充故事中其他人物之间的联系。",Modifier.padding(vertical=18.dp))
                }
                items(loaded.entries,key={it.id}) {entry->
                    Column(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().clickable(enabled=!state.working){editing=entry}.padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment=Alignment.CenterVertically) {
                                    Text(entry.sourceName,style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f,false))
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward,"指向",Modifier.padding(horizontal=8.dp).size(18.dp))
                                    Text(entry.targetName,style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.weight(1f,false))
                                }
                                Text(entry.label,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(top=4.dp))
                                Text(if(entry.chatId==null)"全部会话 · 优先级 ${entry.priority}"else"${loaded.chats.firstOrNull {it.id==entry.chatId}?.title?:"关联会话不可用"} · 优先级 ${entry.priority}",style=MaterialTheme.typography.bodySmall)
                            }
                            Switch(entry.enabled,{vm.saveRelationship(scope,entry,entry.chatId,entry.sourceName,entry.targetName,entry.label,entry.description,it,entry.priority){}},enabled=!state.working,
                                modifier=Modifier.semantics {contentDescription="启用关系 ${entry.title()}"})
                        }
                        if(entry.description.isNotEmpty())Text(entry.description,maxLines=3,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium)
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick={editing=entry},enabled=!state.working){Text("编辑")}
                            TextButton(onClick={deleting=entry},enabled=!state.working){Text("删除",color=MaterialTheme.colorScheme.error)}
                        }
                        HorizontalDivider()
                    }
                }
                item {Text("复制角色会保留对话模式，但不会复制关系、世界书、用户面具或聊天。个人加密备份会保存这些关系。",style=MaterialTheme.typography.bodySmall)}
            }
        }
    }
    val loaded=data
    if(loaded!=null&&loaded.error==null&&(creating||editing!=null))RelationshipEditor(role,editing,loaded.chats,state,vm){creating=false;editing=null}
    if(loaded!=null&&loaded.error==null&&preview)RelationshipInjectionPreview(role,loaded,state,vm){preview=false}
    deleting?.let {entry->AlertDialog(onDismissRequest={deleting=null},title={Text("删除“${entry.title()}”？")},text={Text("删除后，新请求不再提供这条关系。已有聊天内容保持不变，此操作无法撤销。")},
        confirmButton={TextButton(onClick={vm.deleteRelationship(scope,entry);deleting=null},enabled=!state.working){Text("删除")}},dismissButton={TextButton(onClick={deleting=null}){Text("取消")}})}
    requestedMode?.let {mode->
        val raw=mode=="raw"
        AlertDialog(onDismissRequest={requestedMode=null},title={Text(if(raw)"启用原味模型？"else"恢复角色模式？")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(if(raw)"从下一次发送或重试起，不再加入应用人设、用户面具、关系、世界书、开场白、短消息指令或工具目录。当前生成仍使用原快照。"else"从下一次发送或重试起，重新使用已保存且启用的角色设定、关系、世界书、回复风格和工具授权。")
            Text(if(raw)"会话里的旧消息仍会发送给模型；如果希望不带旧聊天影响，请切换后新建会话。服务商自身规则仍然存在。"else"切换不会重新发送旧消息，也不会自动改变已有回答。",style=MaterialTheme.typography.bodySmall)
        }},confirmButton={TextButton(enabled=!state.working,onClick={vm.setConversationMode(role,mode){requestedMode=null}}){Text(if(raw)"启用原味模型"else"恢复角色模式")}},dismissButton={TextButton(onClick={requestedMode=null}){Text("取消")}})
    }
}

@Composable private fun RelationshipScopePicker(chats:List<ChatEntity>,selected:String?,allowAll:Boolean,enabled:Boolean,onSelect:(String?)->Unit) {
    var expanded by remember {mutableStateOf(false)}
    Text(if(allowAll)"作用范围"else"预览会话",style=MaterialTheme.typography.titleSmall)
    Box {
        OutlinedButton(onClick={expanded=true},enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
            Text(if(selected==null&&allowAll)"此角色的全部会话"else chats.firstOrNull {it.id==selected}?.title?:"请选择会话",maxLines=2,overflow=TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded,onDismissRequest={expanded=false},modifier=Modifier.heightIn(max=320.dp)) {
            if(allowAll)DropdownMenuItem(text={Text("此角色的全部会话")},onClick={onSelect(null);expanded=false})
            chats.forEach {chat->DropdownMenuItem(text={Text(chat.title,maxLines=2,overflow=TextOverflow.Ellipsis)},onClick={onSelect(chat.id);expanded=false})}
        }
    }
}

@Composable private fun RelationshipEditor(role:RoleEntity,initial:RelationshipEntry?,chats:List<ChatEntity>,state:CompanionState,vm:CompanionViewModel,onDismiss:()->Unit) {
    var source by remember {mutableStateOf(initial?.sourceName?:role.selfName())}
    var target by remember {mutableStateOf(initial?.targetName?:role.name)}
    var label by remember {mutableStateOf(initial?.label?:"")}
    var description by remember {mutableStateOf(initial?.description?:"")}
    var enabled by remember {mutableStateOf(initial?.enabled?:false)}
    var priority by remember {mutableIntStateOf(initial?.priority?:50)}
    var chatId by remember {mutableStateOf(initial?.chatId)}
    var discard by remember {mutableStateOf(false)}
    val changed=source!=(initial?.sourceName?:role.selfName())||target!=(initial?.targetName?:role.name)||label!=(initial?.label?:"")||description!=(initial?.description?:"")||enabled!=(initial?.enabled?:false)||priority!=(initial?.priority?:50)||chatId!=initial?.chatId
    val valid=runCatching {Relationships.validate(source,target,label,description,priority)}.isSuccess&&(chatId==null||chats.any {it.id==chatId})
    val leave={if(!state.working){if(changed)discard=true else onDismiss()}}
    RelationshipSurface(if(initial==null)"新建关系"else"编辑关系",role.name,leave,state.relationshipError) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("关系方向",style=MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
                OutlinedTextField(source,{if(it.length<=80)source=it},Modifier.weight(1f),label={Text("谁")},singleLine=true,enabled=!state.working)
                Icon(Icons.AutoMirrored.Filled.ArrowForward,"指向")
                OutlinedTextField(target,{if(it.length<=80)target=it},Modifier.weight(1f),label={Text("对谁")},singleLine=true,enabled=!state.working)
            }
            OutlinedTextField(label,{if(it.length<=80)label=it},Modifier.fillMaxWidth(),label={Text("关系名称")},singleLine=true,supportingText={Text("例如：朋友、恋人、家人、同事")},enabled=!state.working)
            OutlinedTextField(description,{if(it.length<=Relationships.DESCRIPTION_LIMIT)description=it},Modifier.fillMaxWidth(),label={Text("补充说明（可选）")},minLines=4,maxLines=10,
                supportingText={Text("${description.length}/${Relationships.DESCRIPTION_LIMIT} 字符 · 写清相处状态、共同经历或边界")},enabled=!state.working)
            RelationshipScopePicker(chats,chatId,true,!state.working){chatId=it}
            Text("优先级：$priority",style=MaterialTheme.typography.titleSmall)
            Slider(priority.toFloat(),{priority=it.toInt()},valueRange=0f..100f,steps=99,enabled=!state.working,modifier=Modifier.semantics {contentDescription="关系优先级"})
            Text("数字越大越先提供。关系总预算 4,000 字符，超出时整条跳过，不截断说明。",style=MaterialTheme.typography.bodySmall)
            Row(verticalAlignment=Alignment.CenterVertically){Text("保存后启用",Modifier.weight(1f));Switch(enabled,{enabled=it},enabled=!state.working,modifier=Modifier.semantics {contentDescription="保存后启用关系"})}
            Text("人物名称只是当前角色空间内的故事标签，不会读取其他角色的聊天或资料。",style=MaterialTheme.typography.bodySmall)
            if(!valid)Text("请填写双方名称和关系名称，并检查字数及会话范围。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }
        Button(onClick={vm.saveRelationship(role.scope(),initial,chatId,source,target,label,description,enabled,priority,onDismiss)},enabled=valid&&!state.working,
            modifier=Modifier.fillMaxWidth().padding(20.dp).heightIn(min=48.dp)){Text("保存关系")}
    }
    if(discard)AlertDialog(onDismissRequest={discard=false},title={Text("放弃未保存的修改？")},text={Text("已保存的关系保持不变。")},
        confirmButton={TextButton(onClick=onDismiss){Text("放弃修改")}},dismissButton={TextButton(onClick={discard=false}){Text("继续编辑")}})
}

@Composable private fun RelationshipInjectionPreview(role:RoleEntity,data:RelationshipData,state:CompanionState,vm:CompanionViewModel,onDismiss:()->Unit) {
    var chatId by remember {mutableStateOf(state.chatId?.takeIf {id->data.chats.any {it.id==id}})}
    var text by remember {mutableStateOf("")}
    var result by remember {mutableStateOf<RequestContext.Resolved?>(null)}
    LaunchedEffect(chatId,text,role.version,data.entries,state.messages){result=null}
    RelationshipSurface("实际设定预览",role.name,{if(!state.working)onDismiss()},state.relationshipError) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("选择真实会话并填写准备发送的话。这里只读取本机设定并计算提示词，不发送消息，也不调用模型。")
            RelationshipScopePicker(data.chats,chatId,false,!state.working){chatId=it;result=null}
            OutlinedTextField(text,{if(it.length<=12000)text=it},Modifier.fillMaxWidth(),label={Text("下一条消息")},minLines=3,maxLines=8,enabled=!state.working)
            Button(onClick={val id=chatId;if(id!=null)vm.previewRelationships(role.scope(),id,text){result=it}},enabled=!state.working&&text.isNotBlank()&&data.chats.any {it.id==chatId},
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("计算这次请求")}
            result?.let {resolved->
                Text("关系 ${resolved.relationships.characters}/${Relationships.BUDGET} 字符",style=MaterialTheme.typography.titleMedium)
                resolved.relationships.decisions.forEach {decision->Text("${decision.title} · "+when(decision.reason){
                    "included"->"已选中（${decision.characters} 字符）";"disabled"->"已关闭";"other_chat"->"仅用于其他会话";"budget"->"超出剩余预算";"raw_mode"->"原味模式不注入";else->decision.reason
                })}
                if(resolved.relationships.decisions.isEmpty())Text("此角色还没有关系设定。")
                Text("世界书：${resolved.worldbook.decisions.count {it.reason=="included"}} 条选中 · ${resolved.worldbook.characters}/${Worldbook.BUDGET} 字符",style=MaterialTheme.typography.bodyMedium)
                Text("本次应用系统提示词",style=MaterialTheme.typography.titleMedium)
                if(resolved.systemInstruction.isEmpty())Text("原味模式没有应用系统提示词，发送时不会附加空的 system 消息。")
                else SelectionContainer {Text(resolved.systemInstruction,Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant,MaterialTheme.shapes.medium).padding(16.dp))}
                Text("预览包含角色、用户面具、关系、世界书和回复风格。聊天历史与工具目录不在此展示；发送前设定或历史变化后需要重新计算。",style=MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
