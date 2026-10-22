package com.ai.assistance.operit.ui.main

import com.ai.assistance.operit.ui.main.ThemedIcon as Icon

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.db.GroupInboxRow
import com.ai.assistance.operit.data.db.GroupRequestRow
import com.ai.assistance.operit.data.model.*

@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun GroupInboxItem(row:GroupInboxRow,state:CompanionState,vm:CompanionViewModel) {
    var menu by remember {mutableStateOf(false)}
    var deleting by remember {mutableStateOf<GroupDefinition?>(null)}
    Row(Modifier.fillMaxWidth().combinedClickable(enabled=!state.working,onClick={vm.selectGroup(row.scope())},onLongClick={menu=true},onLongClickLabel="群聊操作")
        .padding(start=18.dp,end=4.dp,top=16.dp,bottom=16.dp),verticalAlignment=Alignment.CenterVertically) {
        Surface(shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.secondaryContainer) {
            Icon(Icons.Default.Groups,null,Modifier.padding(15.dp).size(24.dp),tint=MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Column(Modifier.weight(1f).padding(start=14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(row.title,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                row.updatedAt?.let {Text(timeLabel(it),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            }
            Text(if(state.busyGroup?.group==row.scope())"正在输入…"else row.preview?.takeIf {it.isNotBlank()}?.replace('\n',' ')?:"${row.memberCount} 位角色 · 选择一位开始聊天",
                style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        Box {
            IconButton(enabled=!state.working,onClick={menu=true}){Icon(Icons.Default.MoreVert,"${row.title}的群聊操作")}
            DropdownMenu(expanded=menu,onDismissRequest={menu=false}) {
                DropdownMenuItem(text={Text("编辑群聊")},leadingIcon={Icon(Icons.Default.Edit,null)},enabled=!state.working,
                    onClick={menu=false;vm.editGroupFromInbox(row.scope())})
                DropdownMenuItem(text={Text("删除群聊",color=MaterialTheme.colorScheme.error)},leadingIcon={Icon(Icons.Default.Delete,null,tint=MaterialTheme.colorScheme.error)},enabled=!state.working,
                    onClick={menu=false;vm.groupDefinition(row.scope()){deleting=it}})
            }
        }
    }
    HorizontalDivider(Modifier.padding(start=86.dp),color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.4f))
    deleting?.let {definition->GroupDeleteDialog(definition,state.working,onDismiss={deleting=null},onConfirm={vm.deleteGroup(definition){deleting=null}})}
}

@Composable private fun GroupDeleteDialog(definition:GroupDefinition,working:Boolean,onDismiss:()->Unit,onConfirm:()->Unit) {
    AlertDialog(onDismissRequest={if(!working)onDismiss()},title={Text("删除“${definition.room.title}”？")},
        text={Text("将停止本群生成，并永久删除本群所有消息，无法撤销。角色、私聊、家园及共享模型连接会保留。")},
        confirmButton={TextButton(enabled=!working,onClick=onConfirm){Text(if(working)"正在删除…"else"删除群聊",color=MaterialTheme.colorScheme.error)}},
        dismissButton={TextButton(enabled=!working,onClick=onDismiss){Text("取消")}})
}

@Composable internal fun GroupEditorPage(state:CompanionState,vm:CompanionViewModel,original:GroupDefinition?,onClose:()->Unit) {
    BackHandler {if(!state.working)onClose()}
    val editor=state.groupEditor?:return
    val title=editor.title
    val userName=editor.userName
    val selected=editor.selected
    val acknowledged=editor.acknowledged
    val valid=title.isNotBlank()&&userName.isNotBlank()&&selected.size in 2..8&&selected.all {(scope,config)->state.roles.any {it.scope()==scope}&&state.configs.any {it.id==config}}&&acknowledged
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(enabled=!state.working,onClick=onClose){Icon(Icons.AutoMirrored.Filled.ArrowBack,"返回")}
            Text(if(original==null)"创建群聊"else"管理群聊",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
        }
        LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(horizontal=20.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            item {OutlinedTextField(title,{if(it.length<=80&&it.none(Char::isISOControl))vm.updateGroupEditor {e->e.copy(title=it)}},Modifier.fillMaxWidth(),label={Text("群名称")},singleLine=true,enabled=!state.working)}
            item {OutlinedTextField(userName,{if(it.length<=40&&it.none(Char::isISOControl))vm.updateGroupEditor {e->e.copy(userName=it)}},Modifier.fillMaxWidth(),label={Text("你在群里的称呼")},singleLine=true,enabled=!state.working)}
            item {Text("选择 2–8 位角色 · 已选 ${selected.size}",style=MaterialTheme.typography.titleMedium)}
            if(state.roles.size<2)item {Text("请先通过聊天页右上角的“+”添加至少两位角色。",color=MaterialTheme.colorScheme.error)}
            if(state.configs.isEmpty())item {Text("请先在设置中添加模型连接，再为每位成员选择模型。",color=MaterialTheme.colorScheme.error)}
            items(state.roles,key={it.id}) {role->
                val checked=selected.containsKey(role.scope())
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth().heightIn(min=48.dp).toggleable(checked,enabled=!state.working&&(checked||selected.size<8),role=Role.Checkbox,onValueChange={value->
                        vm.updateGroupEditor {e->e.copy(selected=if(value)e.selected+(role.scope() to role.defaultConfigId?.takeIf {id->state.configs.any {it.id==id}})else e.selected-role.scope(),acknowledged=false)}
                    }),verticalAlignment=Alignment.CenterVertically) {
                        Checkbox(checked,onCheckedChange=null,modifier=Modifier.padding(12.dp),enabled=!state.working&&(checked||selected.size<8))
                        PortraitAvatar(role.name,role.scope(),vm,40.dp)
                        Text(role.name,Modifier.weight(1f).padding(start=12.dp),style=MaterialTheme.typography.titleMedium)
                    }
                    if(checked) {
                        Text("本群使用的模型",style=MaterialTheme.typography.labelMedium,modifier=Modifier.padding(start=48.dp))
                        Column(Modifier.padding(start=36.dp)) {state.configs.forEach {config->
                            Row(Modifier.fillMaxWidth().heightIn(min=48.dp).selectable(selected[role.scope()]==config.id,enabled=!state.working,role=Role.RadioButton,onClick={vm.updateGroupEditor {e->e.copy(selected=e.selected+(role.scope() to config.id))}}),verticalAlignment=Alignment.CenterVertically) {
                                RadioButton(selected[role.scope()]==config.id,onClick=null,modifier=Modifier.padding(12.dp),enabled=!state.working)
                                Column(Modifier.weight(1f)) {Text(config.name,style=MaterialTheme.typography.bodyMedium);Text(config.model,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                            }
                        }}
                    }
                    HorizontalDivider()
                }
            }
            item {
                Text("成员发言时，其模型会收到本群已完成的聊天，包括加入之前的群历史。仅加入当前发言角色的基础人设；私聊、私人记忆、面具和工具权限不会自动共享。",style=MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth().heightIn(min=48.dp).toggleable(acknowledged,enabled=!state.working,role=Role.Checkbox,onValueChange={vm.updateGroupEditor {e->e.copy(acknowledged=it)}}),verticalAlignment=Alignment.CenterVertically) {Checkbox(acknowledged,onCheckedChange=null,modifier=Modifier.padding(12.dp),enabled=!state.working);Text("我确认向所选成员共享这些群消息",Modifier.weight(1f))}
                if(original!=null)Text("保存会先停止本群正在生成的回复。移除成员会保留它过去在本群的发言。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Button(onClick={vm.saveGroup(original,title,userName,selected.map {(scope,config)->scope to requireNotNull(config)},onClose)},enabled=valid&&!state.working,
            modifier=Modifier.fillMaxWidth().padding(16.dp),shape=RoundedCornerShape(16.dp)) {Text(if(state.working)"正在保存…"else if(original==null)"创建并开始聊天"else"保存群聊")}
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun ColumnScope.GroupConversationPage(state:CompanionState,vm:CompanionViewModel) {
    val group=state.group?:return
    val timeline=group.timeline
    if(timeline==null) {
        Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
        return
    }
    val definition=timeline.definition
    var deleting by remember {mutableStateOf(false)}
    var options by remember {mutableStateOf(false)}
    val participant=group.participantId
    val draft=group.draft
    val member=definition.members.singleOrNull {it.id==participant}
    val busy=state.busyGroup?.group==group.scope
    val runningName=definition.members.firstOrNull {it.id==state.busyGroup?.participantId}?.name
    val canSend=member!=null&&definition.members.size>=2&&draft.isNotBlank()&&!state.working&&state.busyChat==null
    Surface {
        Row(Modifier.fillMaxWidth().heightIn(min=64.dp).padding(end=8.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=vm::closeGroup){Icon(Icons.AutoMirrored.Filled.ArrowBack,"返回会话列表")}
            Column(Modifier.weight(1f)) {
                Text(definition.room.title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(if(busy)"${runningName?:"成员"}${if(state.thinking)"正在思考…"else"正在输入…"}"else"${definition.members.size} 位角色 · 由你选择发言者",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box {
                IconButton(onClick={options=true}){Icon(Icons.Default.MoreVert,"群聊选项")}
                DropdownMenu(options,{options=false}) {
                    DropdownMenuItem(text={Text("管理成员与模型")},onClick={options=false;vm.beginGroupEditor(definition)})
                    DropdownMenuItem(text={Text("删除群聊",color=MaterialTheme.colorScheme.error)},onClick={options=false;deleting=true},enabled=!state.working)
                }
            }
        }
    }
    val scroll=rememberLazyListState()
    var following by remember {mutableStateOf(true)}
    val dragged by scroll.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(dragged,scroll.canScrollForward){if(!scroll.canScrollForward)following=true else if(dragged)following=false}
    LaunchedEffect(timeline.messages.lastOrNull(),following){if(following&&timeline.messages.isNotEmpty()){withFrameNanos {};scroll.scrollToItem(timeline.messages.size)}}
    Box(Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        if(timeline.messages.isEmpty())Column(Modifier.align(Alignment.Center).padding(32.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Icon(Icons.Default.Groups,null,Modifier.size(48.dp),tint=MaterialTheme.colorScheme.primary)
            Text("大家都在这里",style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(top=16.dp,bottom=8.dp))
            Text("选择一位角色，发出第一句问候。每次只有选中的角色回复。",color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(state=scroll,modifier=Modifier.fillMaxSize(),contentPadding=PaddingValues(horizontal=16.dp,vertical=18.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            items(timeline.messages,key={it.id}) {message->
                val user=message.kind=="user"
                val author=definition.members.firstOrNull {it.id==message.participantId}
                val authorIndex=definition.members.indexOfFirst {it.id==message.participantId}
                Row(Modifier.fillMaxWidth(),horizontalArrangement=if(user)Arrangement.End else Arrangement.Start,verticalAlignment=Alignment.Top) {
                    if(!user){RoleAvatar(message.speakerName,requireNotNull(message.participantId),36.dp);Spacer(Modifier.width(8.dp))}
                    Column(Modifier.widthIn(max=340.dp).weight(1f,fill=false),horizontalAlignment=if(user)Alignment.End else Alignment.Start,verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        Text(if(user)message.speakerName else message.speakerName+(if(author==null)" · 已离开"else" · 成员 ${authorIndex+1}"),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(message.content.isNotEmpty())Surface(shape=RoundedCornerShape(topStart=if(user)18.dp else 4.dp,topEnd=if(user)4.dp else 18.dp,bottomStart=18.dp,bottomEnd=18.dp),
                            color=if(user)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface) {
                            SelectionContainer {Box(Modifier.padding(horizontal=14.dp,vertical=10.dp)){BasicMarkdown(message.content,if(user)MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)}}
                        }
                        Text(timeLabel(message.createdAt),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(!user) {
                            if(message.state in activeStates)Text("正在输入…",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                            if(message.state==MessageState.CANCELLED||message.state==MessageState.FAILED)Text(if(message.state==MessageState.CANCELLED)"已停止 · 部分回复已保留"else errorText(message.errorCode),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                            if(message.state !in activeStates)TextButton(onClick={vm.retryGroup(group.scope,message.requestId)},enabled=author!=null&&definition.members.size>=2&&!state.working&&state.busyChat==null){Text(if(author==null)"成员已离开，无法重试"else"重新回复")}
                        }
                    }
                }
            }
            // An end sentinel follows the bottom even when the last bubble exceeds the viewport.
            item(key="group-end"){Spacer(Modifier.height(1.dp))}
        }
        if(!following)FilledTonalIconButton(onClick={following=true},modifier=Modifier.align(Alignment.BottomEnd).padding(16.dp)){Icon(Icons.Default.ArrowDownward,"回到最新消息")}
    }
    Surface(tonalElevation=2.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp)) {
            if(definition.members.size<2)Text("成员不足两位，请从群聊选项补充成员。",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement=Arrangement.spacedBy(8.dp),
                verticalArrangement=Arrangement.spacedBy(4.dp),
                itemVerticalAlignment=Alignment.CenterVertically
            ) {
                Text("请谁回复",style=MaterialTheme.typography.labelMedium)
                definition.members.forEachIndexed {index,p->FilterChip(participant==p.id,{vm.setGroupComposer(group.scope,draft,p.id)},label={Text("${index+1} · ${p.name}",maxLines=1,overflow=TextOverflow.Ellipsis,modifier=Modifier.widthIn(max=180.dp))})}
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(draft,{vm.setGroupComposer(group.scope,it,participant)},Modifier.weight(1f),placeholder={Text(if(member==null)"先选择一位发言者"else"发送到群聊")},maxLines=5,shape=RoundedCornerShape(22.dp))
                if(busy)FilledTonalIconButton(onClick=vm::cancel,modifier=Modifier.size(48.dp)){Icon(Icons.Default.Stop,"停止本群回复")}
                else FilledIconButton(onClick={if(member!=null)vm.sendGroup(group.scope,member.id,draft){vm.setGroupComposer(group.scope,"",participant)}},enabled=canSend,modifier=Modifier.size(48.dp)){Icon(Icons.AutoMirrored.Filled.Send,"发送群消息")}
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(member?.let {p->state.configs.firstOrNull {it.id==p.configId}?.model}?:"选择成员后显示模型",Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
                TextButton(enabled=canSend,onClick={if(member!=null)vm.previewGroup(group.scope,member.id,draft)}){Text("查看发送内容")}
            }
            if(state.busyChat!=null&&!busy)Text("其他对话正在生成，结束后即可发送。",style=MaterialTheme.typography.labelSmall)
        }
    }
    group.preview?.let {snapshot->AlertDialog(onDismissRequest=vm::dismissGroupPreview,title={Text("本次群上下文预览")},text={
        Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("模型：${snapshot.config.model}。预览不发送请求；正式发送时会重新读取当前设定。")
            SelectionContainer {Column(verticalArrangement=Arrangement.spacedBy(12.dp)){snapshot.turns.forEach {turn->Text(turn.role,style=MaterialTheme.typography.labelMedium);Text(turn.content,style=MaterialTheme.typography.bodySmall)}}}
        }
    },confirmButton={TextButton(onClick=vm::dismissGroupPreview){Text("返回编辑")}})}
    if(deleting)GroupDeleteDialog(definition,state.working,onDismiss={deleting=false},onConfirm={vm.deleteGroup(definition){deleting=false}})
}
