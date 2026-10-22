package com.ai.assistance.operit.ui.main

import com.ai.assistance.operit.ui.main.ThemedIcon as Icon

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ai.assistance.operit.api.chat.RequestContext
import com.ai.assistance.operit.data.model.*

@Composable private fun WorldbookSurface(title:String,subtitle:String,leave:()->Unit,error:String?=null,content:@Composable ColumnScope.()->Unit) {
    Dialog(onDismissRequest=leave,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick=leave){Icon(Icons.AutoMirrored.Filled.ArrowBack,"返回")}
                    Column(Modifier.weight(1f)){Text(title,style=MaterialTheme.typography.titleLarge);Text(subtitle,style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=TextOverflow.Ellipsis)}
                }
                if(error!=null)Text(error,Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=8.dp),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                content()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun WorldbookDialog(role:RoleEntity,state:CompanionState,vm:CompanionViewModel,onDismiss:()->Unit) {
    val scope=role.scope()
    val data by remember(scope){vm.worldbook(scope)}.collectAsStateWithLifecycle<WorldbookData?>(null)
    var editing by remember(scope){mutableStateOf<WorldbookEntry?>(null)}
    var creating by remember(scope){mutableStateOf(false)}
    var deleting by remember(scope){mutableStateOf<WorldbookEntry?>(null)}
    var preview by remember(scope){mutableStateOf(false)}
    var query by remember(scope){mutableStateOf("")}
    val files=LocalFileActions.current
    WorldbookSurface("世界书",role.name,{if(!state.working)onDismiss()},state.worldbookError) {
        val loaded=data
        when {
            loaded==null->Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
            loaded.error!=null->Text(loaded.error,Modifier.padding(24.dp),color=MaterialTheme.colorScheme.error)
            else->LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(horizontal=24.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                item {
                    Text("让 TA 记住故事的背景",style=MaterialTheme.typography.titleMedium)
                    Text("记录地点、人物和约定。启用的内容会按触发条件提供给这个角色的模型。",style=MaterialTheme.typography.bodyMedium,modifier=Modifier.padding(top=6.dp))
                }
                item {FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Button(onClick={creating=true},enabled=!state.working&&loaded.entries.size<Worldbook.MAX_ENTRIES){Text("新建条目")}
                    OutlinedButton(onClick={vm.beginWorldbookImport(scope);files.importWorldbook()},enabled=!state.working){Text("导入模板")}
                    OutlinedButton(onClick={vm.previewWorldbookExport(scope,loaded.entries.map {it.id})},enabled=!state.working&&loaded.entries.isNotEmpty()){Text("分享全部")}
                }}
                item {OutlinedButton(onClick={preview=true},enabled=!state.working&&loaded.chats.isNotEmpty(),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("预览实际注入")}
                    if(loaded.chats.isEmpty())Text("创建一段对话后，即可用真实会话预览注入。",style=MaterialTheme.typography.bodySmall)}
                item {OutlinedTextField(query,{if(it.length<=80)query=it},Modifier.fillMaxWidth(),singleLine=true,label={Text("查找条目")},supportingText={Text("${loaded.entries.size}/${Worldbook.MAX_ENTRIES} 条 · 每次注入最多 ${Worldbook.BUDGET} 字符，包含标题")})}
                val visible=loaded.entries.filter {it.title.contains(query,true)||it.content.contains(query,true)}
                if(visible.isEmpty())item {Text(if(loaded.entries.isEmpty())"还没有世界书。可以从一个常去的地点或一条相处约定开始。"else"没有匹配的条目。",Modifier.padding(vertical=20.dp))}
                items(visible,key={it.id}) {entry->
                    Column {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).clickable(enabled=!state.working){editing=entry}.padding(vertical=8.dp)) {
                                Text(entry.title,style=MaterialTheme.typography.titleMedium)
                                Text(if(entry.trigger=="always")"常驻 · 优先级 ${entry.priority}"else"关键词触发 · 优先级 ${entry.priority}",style=MaterialTheme.typography.bodySmall)
                                Text(if(entry.chatId==null)"此角色的全部会话"else loaded.chats.firstOrNull {it.id==entry.chatId}?.title?:"关联会话不可用",style=MaterialTheme.typography.bodySmall)
                            }
                            Switch(entry.enabled,{vm.saveWorldbook(scope,entry,entry.chatId,entry.title,entry.content,entry.trigger,entry.keywords,it,entry.priority){}},enabled=!state.working,
                                modifier=Modifier.semantics {contentDescription="启用世界书条目 ${entry.title}"})
                        }
                        Text(entry.content,maxLines=3,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium)
                        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick={editing=entry},enabled=!state.working){Text("编辑")}
                            TextButton(onClick={vm.previewWorldbookExport(scope,listOf(entry.id))},enabled=!state.working){Text("分享此条")}
                            TextButton(onClick={deleting=entry},enabled=!state.working){Text("删除",color=MaterialTheme.colorScheme.error)}
                        }
                        HorizontalDivider()
                    }
                }
                item {Text("关闭条目不会清除聊天历史中已经提及的内容。复制角色不复制世界书；个人加密备份会保留。",style=MaterialTheme.typography.bodySmall)}
            }
        }
    }
    val loaded=data
    if(loaded!=null&&loaded.error==null&&(creating||editing!=null))WorldbookEditor(role,editing,loaded.chats,state,vm){creating=false;editing=null}
    if(loaded!=null&&loaded.error==null&&preview)WorldbookInjectionPreview(role,loaded,state,vm){preview=false}
    deleting?.let {entry->AlertDialog(onDismissRequest={deleting=null},title={Text("删除“${entry.title}”？")},text={Text("删除后，新请求不再注入这条设定。已有聊天内容保持不变，此操作无法撤销。")},
        confirmButton={TextButton(onClick={vm.deleteWorldbook(scope,entry);deleting=null},enabled=!state.working){Text("删除")}},dismissButton={TextButton(onClick={deleting=null}){Text("取消")}})}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun WorldbookEditor(role:RoleEntity,initial:WorldbookEntry?,chats:List<ChatEntity>,state:CompanionState,vm:CompanionViewModel,onDismiss:()->Unit) {
    var title by remember {mutableStateOf(initial?.title?:"")}
    var body by remember {mutableStateOf(initial?.content?:"")}
    var trigger by remember {mutableStateOf(initial?.trigger?:"always")}
    var keys by remember {mutableStateOf(initial?.keywords?:"")}
    var enabled by remember {mutableStateOf(initial?.enabled?:false)}
    var priority by remember {mutableIntStateOf(initial?.priority?:50)}
    var chatId by remember {mutableStateOf(initial?.chatId)}
    var discard by remember {mutableStateOf(false)}
    val changed=title!=(initial?.title?:"")||body!=(initial?.content?:"")||trigger!=(initial?.trigger?:"always")||keys!=(initial?.keywords?:"")||enabled!=(initial?.enabled?:false)||priority!=(initial?.priority?:50)||chatId!=initial?.chatId
    val valid=runCatching {Worldbook.validate(title,body,trigger,keys,priority)}.isSuccess&&(chatId==null||chats.any {it.id==chatId})
    val leave={if(!state.working){if(changed)discard=true else onDismiss()}}
    WorldbookSurface(if(initial==null)"新建世界书条目"else"编辑世界书",role.name,leave,state.worldbookError) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(title,{if(it.length<=80)title=it},Modifier.fillMaxWidth(),singleLine=true,label={Text("名称")},enabled=!state.working)
            OutlinedTextField(body,{if(it.length<=Worldbook.CONTENT_LIMIT)body=it},Modifier.fillMaxWidth(),minLines=5,maxLines=12,label={Text("设定正文")},supportingText={Text("${body.length}/${Worldbook.CONTENT_LIMIT} 字符")},enabled=!state.working)
            Text("触发方式",style=MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(trigger=="always",{trigger="always"},label={Text("常驻")},enabled=!state.working)
                FilterChip(trigger=="keywords",{trigger="keywords"},label={Text("关键词")},enabled=!state.working)
            }
            if(trigger=="keywords") {
                OutlinedTextField(keys,{if(it.length<=1620)keys=it},Modifier.fillMaxWidth(),minLines=3,maxLines=6,label={Text("关键词，一行一个")},supportingText={Text("最多 20 个，每个最多 80 字符；任一命中即触发。")},enabled=!state.working)
                Text("匹配下一条消息和最近 4 条有完整回答的用户消息。按字面匹配，不区分大小写；不运行正则表达式。",style=MaterialTheme.typography.bodySmall)
            }
            ChatScopePicker(chats,chatId,true,!state.working){chatId=it}
            Text("优先级：$priority",style=MaterialTheme.typography.titleSmall)
            Slider(priority.toFloat(),{priority=it.toInt()},valueRange=0f..100f,steps=99,enabled=!state.working,modifier=Modifier.semantics {contentDescription="世界书优先级"})
            Text("数字越大越先选。每次总预算 4,000 字符，含标题与分隔；放不下的条目不截断，预览会说明原因。",style=MaterialTheme.typography.bodySmall)
            Row(verticalAlignment=Alignment.CenterVertically){Text("保存后启用",Modifier.weight(1f));Switch(enabled,{enabled=it},enabled=!state.working,modifier=Modifier.semantics {contentDescription="保存后启用条目"})}
            if(!valid)Text("请填写名称和正文，并检查关键词、字数及会话范围。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
        }
        Button(onClick={vm.saveWorldbook(role.scope(),initial,chatId,title,body,trigger,keys,enabled,priority,onDismiss)},enabled=valid&&!state.working,modifier=Modifier.fillMaxWidth().padding(20.dp).heightIn(min=48.dp)){Text("保存条目")}
    }
    if(discard)AlertDialog(onDismissRequest={discard=false},title={Text("放弃未保存的修改？")},confirmButton={TextButton(onClick=onDismiss){Text("放弃修改")}},dismissButton={TextButton(onClick={discard=false}){Text("继续编辑")}})
}

@Composable private fun ChatScopePicker(chats:List<ChatEntity>,selected:String?,allowAll:Boolean,enabled:Boolean,onSelect:(String?)->Unit) {
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

@Composable private fun WorldbookInjectionPreview(role:RoleEntity,data:WorldbookData,state:CompanionState,vm:CompanionViewModel,onDismiss:()->Unit) {
    var chatId by remember {mutableStateOf(state.chatId?.takeIf {id->data.chats.any {it.id==id}})}
    var text by remember {mutableStateOf("")}
    var result by remember {mutableStateOf<RequestContext.Resolved?>(null)}
    LaunchedEffect(chatId,text,role.version,data.entries,state.messages){result=null}
    WorldbookSurface("实际注入预览",role.name,{if(!state.working)onDismiss()},state.worldbookError) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("选择真实会话，填写准备发送的话。这里只计算预览，不发送消息，也不调用模型。")
            ChatScopePicker(data.chats,chatId,false,!state.working){chatId=it;result=null}
            OutlinedTextField(text,{if(it.length<=12000)text=it},Modifier.fillMaxWidth(),minLines=3,maxLines=8,label={Text("下一条消息")},enabled=!state.working)
            Button(onClick={val id=chatId;if(id!=null)vm.previewWorldbook(role.scope(),id,text){result=it}},enabled=!state.working&&text.isNotBlank()&&data.chats.any {it.id==chatId},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("计算注入内容")}
            result?.let {resolved->
                Text("世界书 ${resolved.worldbook.characters}/${Worldbook.BUDGET} 字符",style=MaterialTheme.typography.titleMedium)
                resolved.worldbook.decisions.forEach {d->Text("${d.title} · "+when(d.reason){"included"->"已选中（${d.characters} 字符）";"disabled"->"已关闭";"other_chat"->"仅用于其他会话";"no_match"->"关键词未命中";"budget"->"超出剩余预算";"raw_mode"->"原味模式不注入";else->d.reason})}
                if(resolved.worldbook.decisions.isEmpty())Text("此角色还没有条目。")
                Text("本次完整系统提示词",style=MaterialTheme.typography.titleMedium)
                Text("角色模式包含人设、用户面具、关系、回复风格及选中条目；原味模式不注入这些内容。会话正文与工具目录不在此展示，设定或历史变化后需要重新计算。",style=MaterialTheme.typography.bodySmall)
                if(resolved.systemInstruction.isEmpty())Text("本次没有应用系统提示词，发送时不会附加空的 system 消息。")
                else SelectionContainer {Text(resolved.systemInstruction,Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant,MaterialTheme.shapes.medium).padding(16.dp))}
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable internal fun WorldbookFileDialog(state:CompanionState,vm:CompanionViewModel) {
    val preview=state.worldbookFile?:return
    val files=LocalFileActions.current
    var checked by remember(preview){mutableStateOf(false)}
    WorldbookSurface(if(preview.exporting)"分享世界书模板"else"导入世界书模板",preview.roleName,vm::dismissWorldbookFile,state.worldbookError) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text(if(preview.exporting)"以下是将保存的完整内容。请检查你在设定中填写的私人信息。"else"确认后向这个角色追加 ${preview.template.entries.size} 个新条目，全部关闭；不会覆盖已有条目。")
            Text("模板只分享设定文字与触发规则，不包含身份、会话、用户面具、模型配置或 Key。会话绑定不会分享，导入后作用于所选角色。",style=MaterialTheme.typography.bodySmall)
            if(preview.ignored>0)Text("忽略了 ${preview.ignored} 个额外字段；不会执行脚本、读取路径或访问 URL。",color=MaterialTheme.colorScheme.error)
            SelectionContainer {Text(WorldbookTemplateCodec.format(preview.template),style=MaterialTheme.typography.bodySmall)}
            Row(verticalAlignment=Alignment.CenterVertically) {Checkbox(checked,{checked=it},enabled=!state.working);Text(if(preview.exporting)"已检查全部内容，可以分享"else"已检查内容，确认导入此角色")}
            Spacer(Modifier.height(12.dp))
        }
        Button(onClick={if(preview.exporting)vm.approveWorldbookExport(files.exportWorldbook)else vm.importWorldbookTemplate()},enabled=checked&&!state.working,modifier=Modifier.fillMaxWidth().padding(20.dp).heightIn(min=48.dp)){
            Text(if(preview.exporting)"选择位置保存 JSON"else"创建新条目")
        }
    }
}
