package com.ai.assistance.operit.ui.main

import com.ai.assistance.operit.ui.main.ThemedIcon as Icon

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmarks
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
import java.text.DateFormat
import java.util.Date

@Composable private fun MemorySurface(title:String,subtitle:String,onBack:()->Unit,error:String?=null,content:@Composable ColumnScope.()->Unit) {
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
@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun MemoryDialog(role:RoleEntity,state:CompanionState,vm:CompanionViewModel,onDismiss:()->Unit) {
    val scope=role.scope()
    val data by remember(scope){vm.memories(scope)}.collectAsStateWithLifecycle<MemoryData?>(null)
    var creating by remember(scope){mutableStateOf(false)}
    var editing by remember(scope){mutableStateOf<MemoryEntry?>(null)}
    var deleting by remember(scope){mutableStateOf<MemoryEntry?>(null)}
    var preview by remember(scope){mutableStateOf(false)}
    MemorySurface("长期记忆",role.name,{if(!state.working)onDismiss()},state.memoryError) {
        val loaded=data
        when {
            loaded==null->Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
            loaded.error!=null->Text(loaded.error,Modifier.padding(24.dp),color=MaterialTheme.colorScheme.error)
            else->LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(horizontal=24.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                item {
                    Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=MaterialTheme.shapes.large) {
                        Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                Icon(Icons.Default.Bookmarks,null,tint=MaterialTheme.colorScheme.primary)
                                Text("你决定记住什么",Modifier.padding(start=10.dp),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                            }
                            Text("记忆可以始终带入，也可以只在本机检索命中时带入。过期或暂时忘记的内容不会发送给模型。",style=MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                item {FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Button(onClick={creating=true},enabled=!state.working&&loaded.entries.size<Memories.MAX_ENTRIES){Icon(Icons.Default.Add,null);Spacer(Modifier.width(6.dp));Text("新建记忆")}
                    OutlinedButton(onClick={preview=true},enabled=!state.working&&loaded.chats.isNotEmpty()){Icon(Icons.Default.Visibility,null);Spacer(Modifier.width(6.dp));Text("预览实际注入")}
                }}
                item {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text("已保存的记忆",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                        Text("${loaded.entries.count {it.forgottenAt==null}} 条在用 · ${loaded.entries.size}/${Memories.MAX_ENTRIES}",style=MaterialTheme.typography.labelLarge)
                    }
                    Text("每条只属于当前角色空间，编辑和删除只影响之后的发送与重试。",style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=6.dp))
                }
                if(loaded.entries.isEmpty())item {
                    Text("还没有长期记忆。可以先记录一个偏好、现实经历、当前计划或明确的虚构剧情。",Modifier.padding(vertical=18.dp))
                }
                items(loaded.entries,key={it.id}) {entry->
                    val now=System.currentTimeMillis()
                    val expired=entry.expiresAt?.let {it<=now}==true
                    Surface(shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.55f)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment=Alignment.Top) {
                                Column(Modifier.weight(1f)) {
                                    Text(Memories.label(entry.kind),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                                    Text(entry.title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
                                    Text("优先级 ${entry.priority} · ${if(entry.recallMode=="always")"始终带入" else "按内容检索"} · "+when {entry.forgottenAt!=null->"已暂时忘记";expired->"已过期";entry.source=="manual"->"手工保存";entry.sourceLinked()->"对话提炼 · ${entry.sourceModel}";else->"对话提炼 · 来源已删除 · ${entry.sourceModel}"},style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
                                    entry.expiresAt?.let {Text("有效至 ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                                }
                                Switch(entry.enabled&&entry.forgottenAt==null,{vm.saveMemory(scope,entry,entry.kind,entry.title,entry.content,it,entry.priority,saved={})},enabled=!state.working&&entry.forgottenAt==null,
                                    modifier=Modifier.semantics {contentDescription="启用记忆 ${entry.title}"})
                            }
                            Text(entry.content,maxLines=5,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick={editing=entry},enabled=!state.working){Text("编辑")}
                                if(entry.forgottenAt==null)TextButton(onClick={vm.forgetMemory(scope,entry)},enabled=!state.working){Text("暂时忘记")}
                                else TextButton(onClick={vm.restoreMemory(scope,entry)},enabled=!state.working){Text("恢复")}
                                TextButton(onClick={deleting=entry},enabled=!state.working){Text("删除",color=MaterialTheme.colorScheme.error)}
                            }
                        }
                    }
                }
                item {Text("复制角色和公开角色模板都不包含记忆。个人加密备份会保存它们。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            }
        }
    }
    val loaded=data
    if(loaded!=null&&loaded.error==null&&(creating||editing!=null))MemoryEditor(role,loaded.entries,editing,state,vm){creating=false;editing=null}
    if(loaded!=null&&loaded.error==null&&preview)MemoryInjectionPreview(role,loaded,state,vm){preview=false}
    deleting?.let {entry->AlertDialog(onDismissRequest={deleting=null},title={Text("删除“${entry.title}”？")},
        text={Text("删除后无法撤销，之后的发送和重试不再提供这条记忆。已有聊天内容保持不变。")},
        confirmButton={TextButton(enabled=!state.working,onClick={vm.deleteMemory(scope,entry);deleting=null}){Text("删除")}},
        dismissButton={TextButton(onClick={deleting=null}){Text("取消")}})}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun MemoryEditor(role:RoleEntity,entries:List<MemoryEntry>,initial:MemoryEntry?,state:CompanionState,vm:CompanionViewModel,onDismiss:()->Unit) {
    var kind by remember(initial?.id){mutableStateOf(initial?.kind?:"preference")}
    var title by remember(initial?.id){mutableStateOf(initial?.title?:"")}
    var content by remember(initial?.id){mutableStateOf(initial?.content?:"")}
    var enabled by remember(initial?.id){mutableStateOf(initial?.enabled?:true)}
    var priority by remember(initial?.id){mutableIntStateOf(initial?.priority?:50)}
    var recallMode by remember(initial?.id){mutableStateOf(initial?.recallMode?:"auto")}
    var expiryChoice by remember(initial?.id){mutableStateOf(if(initial?.expiresAt==null)"never" else "keep")}
    val editorNow=remember(initial?.id){System.currentTimeMillis()}
    val expiresAt=when(expiryChoice){"keep"->initial?.expiresAt;"7"->editorNow+7L*24*60*60*1000;"30"->editorNow+30L*24*60*60*1000;"90"->editorNow+90L*24*60*60*1000;else->null}
    var discard by remember {mutableStateOf(false)}
    val changed=kind!=(initial?.kind?:"preference")||title!=(initial?.title?:"")||content!=(initial?.content?:"")||enabled!=(initial?.enabled?:true)||priority!=(initial?.priority?:50)||recallMode!=(initial?.recallMode?:"auto")||expiresAt!=initial?.expiresAt
    val valid=runCatching {Memories.validate(kind,title,content,priority,recallMode=recallMode,expiresAt=expiresAt)}.isSuccess
    val conflicts=if(valid)runCatching {Memories.conflicts(role.scope(),entries,kind,title,content,initial?.id,editorNow)}.getOrDefault(emptyList())else emptyList()
    var conflictPolicy by remember(initial?.id){mutableStateOf<String?>(null)}
    LaunchedEffect(conflicts.map {it.id to it.version}){conflictPolicy=if(conflicts.isEmpty())"reject" else null}
    val leave={if(!state.working){if(changed)discard=true else onDismiss()}}
    MemorySurface(if(initial==null)"新建记忆"else"编辑记忆",role.name,leave,state.memoryError) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("记忆类型",style=MaterialTheme.typography.titleMedium)
            Text("类型会一起提供给模型，虚构剧情不会被标记为现实经历。",style=MaterialTheme.typography.bodySmall)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Memories.KINDS.forEach {value->FilterChip(selected=kind==value,onClick={kind=value},label={Text(Memories.label(value))},enabled=!state.working)}
            }
            OutlinedTextField(title,{if(it.length<=Memories.TITLE_LIMIT)title=it},Modifier.fillMaxWidth(),label={Text("标题")},singleLine=true,
                supportingText={Text("${title.length}/${Memories.TITLE_LIMIT} 字符")},enabled=!state.working)
            OutlinedTextField(content,{if(it.length<=Memories.CONTENT_LIMIT)content=it},Modifier.fillMaxWidth(),label={Text("记忆内容")},minLines=6,maxLines=14,
                supportingText={Text("${content.length}/${Memories.CONTENT_LIMIT} 字符 · 请写清事实、偏好或剧情边界")},enabled=!state.working)
            Text("优先级：$priority",style=MaterialTheme.typography.titleSmall)
            Slider(priority.toFloat(),{priority=it.toInt()},valueRange=0f..100f,steps=99,enabled=!state.working,modifier=Modifier.semantics {contentDescription="记忆优先级"})
            Text("数字越大越先提供。本次请求的记忆预算为 4,000 字符，超出时整条跳过。",style=MaterialTheme.typography.bodySmall)
            Text("召回方式",style=MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(selected=recallMode=="auto",onClick={recallMode="auto"},label={Text("按内容检索")},enabled=!state.working)
                FilterChip(selected=recallMode=="always",onClick={recallMode="always"},label={Text("始终带入")},enabled=!state.working)
            }
            Text(if(recallMode=="auto")"只在最近对话与这条记忆的本机文字向量达到阈值时带入；检索不会出网。" else "每次角色模式请求都尝试带入，仍受 4,000 字符总预算限制。",style=MaterialTheme.typography.bodySmall)
            Text("有效期",style=MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                if(initial?.expiresAt!=null)FilterChip(selected=expiryChoice=="keep",onClick={expiryChoice="keep"},label={Text("保持 ${DateFormat.getDateInstance(DateFormat.SHORT).format(Date(initial.expiresAt))}")},enabled=!state.working)
                listOf("never" to "长期","7" to "7 天","30" to "30 天","90" to "90 天").forEach {(value,label)->FilterChip(selected=expiryChoice==value,onClick={expiryChoice=value},label={Text(label)},enabled=!state.working)}
            }
            Row(verticalAlignment=Alignment.CenterVertically){Text("保存后启用",Modifier.weight(1f));Switch(enabled,{enabled=it},enabled=!state.working,modifier=Modifier.semantics {contentDescription="保存后启用记忆"})}
            if(conflicts.isNotEmpty()) {
                Text("发现 ${conflicts.size} 条可能重复或冲突的记忆",style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.error)
                conflicts.forEach {conflict->Surface(color=MaterialTheme.colorScheme.errorContainer.copy(alpha=0.45f),shape=MaterialTheme.shapes.medium) {Column(Modifier.fillMaxWidth().padding(12.dp)) {Text("${conflict.title} · 相似 ${conflict.matchPercent}%",fontWeight=FontWeight.SemiBold);Text(conflict.content,maxLines=3,overflow=TextOverflow.Ellipsis)}}}
                Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.clickable(enabled=!state.working){conflictPolicy="keep"}) {RadioButton(conflictPolicy=="keep",{conflictPolicy="keep"},enabled=!state.working);Text("保留两边，由我之后整理")}
                Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.clickable(enabled=!state.working){conflictPolicy="replace"}) {RadioButton(conflictPolicy=="replace",{conflictPolicy="replace"},enabled=!state.working);Text("用当前内容替换这些记忆（旧内容可恢复）")}
            }
            Text("内容只保存在当前角色的本机加密数据中。只有启用后才会随之后的角色模式请求发给当前模型服务。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(!valid)Text("请选择类型并填写标题和内容，同时检查字数。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }
        Button(onClick={vm.saveMemory(role.scope(),initial,kind,title,content,enabled,priority,recallMode,expiresAt,conflictPolicy?:"reject",conflicts,onDismiss)},enabled=valid&&!state.working&&conflictPolicy!=null,
            modifier=Modifier.fillMaxWidth().padding(20.dp).heightIn(min=48.dp)){Text("保存记忆")}
    }
    if(discard)AlertDialog(onDismissRequest={discard=false},title={Text("放弃未保存的修改？")},text={Text("已保存的记忆保持不变。")},
        confirmButton={TextButton(onClick=onDismiss){Text("放弃修改")}},dismissButton={TextButton(onClick={discard=false}){Text("继续编辑")}})
}

@Composable private fun MemoryChatPicker(chats:List<ChatEntity>,selected:String?,enabled:Boolean,onSelect:(String)->Unit) {
    var expanded by remember {mutableStateOf(false)}
    Text("预览会话",style=MaterialTheme.typography.titleSmall)
    Box {
        OutlinedButton(onClick={expanded=true},enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
            Text(chats.firstOrNull {it.id==selected}?.title?:"请选择会话",maxLines=2,overflow=TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded,onDismissRequest={expanded=false},modifier=Modifier.heightIn(max=320.dp)) {
            chats.forEach {chat->DropdownMenuItem(text={Text(chat.title,maxLines=2,overflow=TextOverflow.Ellipsis)},onClick={onSelect(chat.id);expanded=false})}
        }
    }
}

@Composable private fun MemoryInjectionPreview(role:RoleEntity,data:MemoryData,state:CompanionState,vm:CompanionViewModel,onDismiss:()->Unit) {
    var chatId by remember {mutableStateOf(state.chatId?.takeIf {id->data.chats.any {it.id==id}})}
    var text by remember {mutableStateOf("")}
    var result by remember {mutableStateOf<RequestContext.Resolved?>(null)}
    LaunchedEffect(chatId,text,role.version,data.entries,state.messages){result=null}
    MemorySurface("实际记忆预览",role.name,{if(!state.working)onDismiss()},state.memoryError) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("选择真实会话并填写准备发送的话。这里只读取本机设定并计算提示词，不发送消息，也不调用模型。")
            MemoryChatPicker(data.chats,chatId,!state.working){chatId=it;result=null}
            OutlinedTextField(text,{if(it.length<=12000)text=it},Modifier.fillMaxWidth(),label={Text("下一条消息")},minLines=3,maxLines=8,enabled=!state.working)
            Button(onClick={val id=chatId;if(id!=null)vm.previewMemories(role.scope(),id,text){result=it}},enabled=!state.working&&text.isNotBlank()&&data.chats.any {it.id==chatId},
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("计算这次请求")}
            result?.let {resolved->
                Text("记忆 ${resolved.memories.characters}/${Memories.BUDGET} 字符",style=MaterialTheme.typography.titleMedium)
                resolved.memories.decisions.forEach {decision->Text("${Memories.label(decision.kind)} · ${decision.title} · "+when(decision.reason){
                    "included"->"已选中（${decision.characters} 字符，匹配 ${decision.matchPercent}%）";"disabled"->"已关闭";"forgotten"->"已暂时忘记";"expired"->"已过期";"no_match"->"本次内容未命中（${decision.matchPercent}%）";"budget"->"超出剩余预算";"raw_mode"->"原味模式不注入";else->decision.reason
                })}
                if(resolved.memories.decisions.isEmpty())Text("此角色还没有长期记忆。")
                Text("本次应用系统提示词",style=MaterialTheme.typography.titleMedium)
                if(resolved.systemInstruction.isEmpty())Text("原味模式没有应用系统提示词，记忆也不会发送。")
                else SelectionContainer {Text(resolved.systemInstruction,Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant,MaterialTheme.shapes.medium).padding(16.dp))}
                Text("预览会同时显示当前角色中实际启用的人设、用户面具、关系、记忆和世界书。记忆检索只在本机计算，不调用模型或外部向量服务。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
