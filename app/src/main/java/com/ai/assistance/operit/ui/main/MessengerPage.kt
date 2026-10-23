package com.ai.assistance.operit.ui.main

import com.ai.assistance.operit.ui.main.ThemedIcon as Icon

import android.animation.ValueAnimator
import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ai.assistance.operit.data.model.*
import com.ai.assistance.operit.api.audio.MessageVoicePlayer
import com.ai.assistance.operit.data.db.GroupInboxRow
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable internal fun RoleAvatar(name: String, id: String, size: Dp=48.dp) {
    val colors=listOf(Color(0xFF95566F),Color(0xFF487366),Color(0xFF4F7398),Color(0xFF756195))
    val tint=colors[(id.hashCode().toLong().let { if(it<0)-it else it }%colors.size).toInt()]
    val initial=if(name.isEmpty())"AI"else name.substring(0,name.offsetByCodePoints(0,1))
    Box(Modifier.size(size).clip(RoundedCornerShape(size*0.32f)).background(Brush.linearGradient(listOf(tint,lerp(tint,Color.Black,0.12f)))),contentAlignment=Alignment.Center) {
        Text(initial,color=Color.White,fontSize=(size.value*0.40).sp,fontWeight=FontWeight.Medium)
    }
}

@Composable internal fun ColumnScope.MessengerPage(state: CompanionState,vm: CompanionViewModel,onSettings:()->Unit,onRoles:()->Unit,onEditRole:(String)->Unit,onOpenConnections:()->Unit,onErrorHelp:(String)->Unit,onMessageImagePick:(RoleAccessScope,String)->Unit,onMessageFilePick:(RoleAccessScope,String)->Unit,onReadingFilePick:(RoleAccessScope)->Unit,onStickerPick:(RoleAccessScope)->Unit,onWallpaperPick:(RoleAccessScope)->Unit,onPortraitPick:(RoleAccessScope,String)->Unit,openActivityChatId:String?=null,openActivityType:String?=null,openActivityId:String?=null,onActivityOpened:()->Unit={}) {
    var contactToStart by remember { mutableStateOf<RoleEntity?>(null) }
    var connectionRequired by remember { mutableStateOf(false) }
    var forwardingMessage by remember { mutableStateOf<String?>(null) }
    var pendingForwardDraft by remember { mutableStateOf<ForwardDraft?>(null) }
    // A contact row owns its private conversation; tapping it reopens that conversation.
    val openContact: (RoleEntity, ConversationCard?) -> Unit = { role, card ->
        when {
            card != null -> vm.selectChat(role.scope(), card.chatId)
            state.configs.isEmpty() -> connectionRequired = true
            role.defaultConfigId != null && state.configs.any { it.id == role.defaultConfigId } ->
                vm.createChat(role.scope(), role.defaultConfigId)
            else -> contactToStart = role
        }
    }
    if(state.groupEditor!=null){GroupEditorPage(state,vm,state.groupEditor.original,vm::closeGroupEditor);return}
    if(connectionRequired)AlertDialog(
        onDismissRequest={connectionRequired=false},
        title={Text("先添加模型连接")},
        text={Text("当前还没有已保存的模型服务。添加并保存连接后，就可以和角色聊天。")},
        confirmButton={TextButton(onClick={connectionRequired=false;onOpenConnections()}){Text("添加模型连接")}},
        dismissButton={TextButton(onClick={connectionRequired=false}){Text("暂不")}}
    )
    if(state.group!=null)key(state.group.selectionId){GroupConversationPage(state,vm)}
    else if(state.chatId==null) InboxPage(state,vm,onRoles,onEditRole,{vm.beginGroupEditor(null)},openContact) else key(state.chatId) { ConversationPage(state,vm,onSettings,onErrorHelp,onMessageImagePick,onMessageFilePick,onReadingFilePick,onStickerPick,onWallpaperPick,onPortraitPick,openActivityChatId,openActivityType,openActivityId,onActivityOpened,pendingForwardDraft,{pendingForwardDraft=null},{forwardingMessage=it}) }
    forwardingMessage?.let {forwardedText->AlertDialog(
        onDismissRequest={forwardingMessage=null},
        title={Text("转发到聊天")},
        text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text("选择目标聊天。转发内容会先放入输入框，由你确认后发送。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            state.inbox.forEach {card->TextButton(onClick={
                pendingForwardDraft=ForwardDraft(RoleAccessScope(card.roleId,card.spaceId),card.chatId,"转发消息：\n$forwardedText")
                forwardingMessage=null
                vm.selectChat(RoleAccessScope(card.roleId,card.spaceId),card.chatId)
            },modifier=Modifier.fillMaxWidth()) {Text("${card.roleName} · ${card.title}",maxLines=1,overflow=TextOverflow.Ellipsis)} }
            if(state.inbox.isEmpty())Text("还没有可转发到的聊天。",modifier=Modifier.padding(vertical=16.dp))
        }},confirmButton={TextButton(onClick={forwardingMessage=null}){Text("取消")}}
    )}
    contactToStart?.let { role -> NewConversationDialog(state,role,{contactToStart=null}) { config ->
        vm.createChat(role.scope(),config)
        contactToStart=null
    } }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun ColumnScope.InboxPage(state: CompanionState,vm: CompanionViewModel,onRoles:()->Unit,onEditRole:(String)->Unit,onCreateGroup:()->Unit,onOpenContact:(RoleEntity,ConversationCard?)->Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<ChatDeleteRequest?>(null) }
    var deletingRole by remember { mutableStateOf<RoleDeleteRequest?>(null) }
    var menuRoleId by remember { mutableStateOf<String?>(null) }
    BackHandler(searching){search="";searching=false}
    val headerArtwork = LocalCompanionThemeArtwork.current
    Box(Modifier.fillMaxWidth().height(if(headerArtwork==null)56.dp else 68.dp)) {
        headerArtwork?.let { ThemeHeaderArtwork(it,Modifier.matchParentSize()) }
        Text("聊天",Modifier.align(Alignment.Center),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
        Row(Modifier.align(Alignment.CenterEnd).padding(end=8.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick={searching=!searching;if(!searching)search=""}){Icon(Icons.Default.Search,if(searching)"关闭搜索"else"搜索角色、群聊或最近消息")}
            Box {
                IconButton(enabled=!state.working,onClick={addMenu=true}){Icon(Icons.Default.Add,"角色管理或创建群聊")}
                DropdownMenu(expanded=addMenu,onDismissRequest={addMenu=false}) {
                    DropdownMenuItem(text={Text("角色管理")},leadingIcon={Icon(Icons.Default.PersonAdd,null)},onClick={addMenu=false;onRoles()})
                    DropdownMenuItem(text={Text("创建群聊")},leadingIcon={Icon(Icons.Default.Groups,null)},onClick={addMenu=false;onCreateGroup()})
                }
            }
        }
    }
    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.45f))
    if(searching) OutlinedTextField(search,{search=it},Modifier.fillMaxWidth().padding(horizontal=14.dp,vertical=8.dp),singleLine=true,
        label={Text("搜索角色、群聊或最近消息")},leadingIcon={Icon(Icons.Default.Search,null)},
        trailingIcon={IconButton(onClick={search="";searching=false}){Icon(Icons.Default.Close,"关闭搜索")}},shape=RoundedCornerShape(18.dp))
    ToolHistory(state.toolInvocations)
    val contacts=state.roles.map { role ->
        InboxEntry.Contact(role,state.inbox.firstOrNull { it.roleId==role.id && it.spaceId==role.spaceId })
    }.filter { entry -> search.isBlank() || entry.role.name.contains(search,true) ||
        entry.card?.preview?.contains(search,true)==true || entry.card?.title?.contains(search,true)==true }
    val groups=state.groupInbox.filter {search.isBlank()||it.title.contains(search,true)||it.preview?.contains(search,true)==true}
    val entries=buildList {
        contacts.forEach {add(it)}
        groups.forEach {add(InboxEntry.Group(it))}
    }.sortedWith(compareByDescending<InboxEntry>{it.pinned}.thenByDescending {it.updatedAt}.thenBy {it.sortName})
    Box(Modifier.weight(1f).fillMaxWidth()) {
        if(entries.isEmpty()) Column(Modifier.align(Alignment.Center).padding(32.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Surface(shape=CircleShape,color=MaterialTheme.colorScheme.surfaceVariant) { Icon(Icons.Default.Forum,null,Modifier.padding(24.dp).size(36.dp),tint=MaterialTheme.colorScheme.primary) }
            Text(if(search.isNotBlank())"没有找到匹配的角色或群聊"else"添加一位角色",style=MaterialTheme.typography.titleLarge,modifier=Modifier.padding(top=24.dp))
            Text(if(search.isNotBlank())"试试角色名或最近消息中的文字"else"创建角色后，就能从这里打开专属聊天。",color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=8.dp,bottom=20.dp))
            if(search.isBlank())Button(onClick=onRoles,shape=RoundedCornerShape(16.dp)){Text("添加角色")}
        } else LazyColumn(contentPadding=PaddingValues(top=2.dp,bottom=88.dp)) {
            items(entries,key={it.key}) { entry->
                if(entry is InboxEntry.Group)GroupInboxItem(entry.row,state,vm)
                else {
                val contact=entry as InboxEntry.Contact
                val role=contact.role
                val card=contact.card
                // A role exists before its first chat; its edit/delete actions must not depend on a conversation card.
                Surface(color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth().combinedClickable(
                    enabled=!state.working,onClick={onOpenContact(role,card)},onLongClick={menuRoleId=role.id},onLongClickLabel="角色和对话操作"
                )) {
                    Row(Modifier.padding(start=16.dp,end=4.dp,top=14.dp,bottom=14.dp),verticalAlignment=Alignment.CenterVertically) {
                        PortraitAvatar(role.name,role.scope(),vm,58.dp)
                        Column(Modifier.weight(1f).padding(start=14.dp,end=8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                if(card?.pinned==true)Icon(Icons.Default.PushPin,"已置顶",Modifier.size(16.dp),tint=MaterialTheme.colorScheme.primary)
                                Text(role.name,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                                card?.let { Text(timeLabel(it.updatedAt ?: it.createdAt),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            Text(if(card==null)"点击开始聊天"else if(state.busyChat==card.chatId)if(state.thinking)"正在思考…"else"正在输入…"else card.preview?.replace('\n',' ') ?: "暂无消息",
                                color=if(card!=null&&state.busyChat==card.chatId)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                style=MaterialTheme.typography.bodyMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
                        }
                        Box {
                            IconButton(enabled=!state.working,onClick={menuRoleId=role.id}){Icon(Icons.Default.MoreVert,"${role.name}的角色和对话操作",Modifier.size(20.dp))}
                            DropdownMenu(expanded=menuRoleId==role.id,onDismissRequest={menuRoleId=null}) {
                                DropdownMenuItem(text={Text("编辑角色")},leadingIcon={Icon(Icons.Default.Edit,null)},enabled=!state.working,
                                    onClick={menuRoleId=null;onEditRole(role.id)})
                                if(card!=null) {
                                DropdownMenuItem(
                                    text={Text(if(card.pinned)"取消置顶"else"置顶对话")},
                                    leadingIcon={Icon(Icons.Default.PushPin,null)},
                                    enabled=!state.working,
                                    onClick={menuRoleId=null;vm.setPinned(role.scope(),card.chatId,!card.pinned)}
                                )
                                DropdownMenuItem(
                                    text={Text("删除对话",color=MaterialTheme.colorScheme.error)},
                                    leadingIcon={Icon(Icons.Default.Delete,null,tint=MaterialTheme.colorScheme.error)},
                                    enabled=!state.working,
                                    onClick={menuRoleId=null;vm.chatMemorySourceCount(role.scope(),card.chatId){count->deleting=ChatDeleteRequest(card,count)}}
                                )
                                }
                                DropdownMenuItem(text={Text("删除角色",color=MaterialTheme.colorScheme.error)},
                                    leadingIcon={Icon(Icons.Default.PersonRemove,null,tint=MaterialTheme.colorScheme.error)},enabled=!state.working,
                                    onClick={menuRoleId=null;vm.roleChatCount(role.scope()){count->deletingRole=RoleDeleteRequest(role,count)}})
                            }
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(start=86.dp),color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.4f))
                }
            }
        }
    }
    deletingRole?.let { request->RoleDeleteDialog(request,state.working,onDismiss={deletingRole=null},onConfirm={
        vm.deleteRole(request.role.scope()){deletingRole=null}
    }) }
    deleting?.let { request->
        val card=request.card
        AlertDialog(onDismissRequest={if(!state.working)deleting=null},title={Text("删除与${card.roleName}的这段对话？")},text={Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text("本机消息和这段对话关联的共同活动将被删除，无法撤销。角色和共享家园会保留，可重新开始聊天。")
            if(request.dependencies>0) {
                Text("这段对话是 ${request.dependencies} 条提炼记忆的来源，请选择如何处理。",fontWeight=FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth().clickable {deleting=request.copy(disposition="detach")},verticalAlignment=Alignment.CenterVertically){RadioButton(request.disposition=="detach",{deleting=request.copy(disposition="detach")});Text("保留记忆，断开来源")}
                Row(Modifier.fillMaxWidth().clickable {deleting=request.copy(disposition="delete")},verticalAlignment=Alignment.CenterVertically){RadioButton(request.disposition=="delete",{deleting=request.copy(disposition="delete")});Text("同时删除 ${request.dependencies} 条提炼记忆")}
            }
        }},confirmButton={TextButton(enabled=!state.working&&(request.dependencies==0||request.disposition!=null),onClick={vm.deleteChat(RoleAccessScope(card.roleId,card.spaceId),card.chatId,request.disposition?:"reject");deleting=null}){Text("删除对话")}},dismissButton={TextButton(enabled=!state.working,onClick={deleting=null}){Text("取消")}})
    }
}

private data class ChatDeleteRequest(val card:ConversationCard,val dependencies:Int,val disposition:String?=null)
private sealed class InboxEntry(val key:String,val pinned:Boolean,val updatedAt:Long,val sortName:String) {
    class Contact(val role:RoleEntity,val card:ConversationCard?):InboxEntry("contact:${role.id}",card?.pinned==true,card?.updatedAt?:card?.createdAt?:0L,role.name)
    class Group(val row:GroupInboxRow):InboxEntry("group:${row.spaceId}:${row.groupId}",false,row.updatedAt?:0L,row.title)
}
private data class ForwardDraft(val scope:RoleAccessScope,val chatId:String,val text:String)
private data class TimelineBubble(val key:String,val text:String,val message:MessageEntity?,val last:Boolean,val attachment:MessageAttachment?=null,val material:MessageMaterial?=null,val sticker:MessageSticker?=null,val voice:MessageVoice?=null,val reactions:List<MessageReaction> = emptyList(),val generated:GeneratedImage?=null,val timeSeparator:String?=null)
private enum class MessageOperation { COPY,FORWARD,FAVORITE,DELETE,SELECT,QUOTE,TRANSLATE,SPEAK }

@Composable private fun ColumnScope.ConversationPage(state: CompanionState,vm: CompanionViewModel,onSettings:()->Unit,onErrorHelp:(String)->Unit,onMessageImagePick:(RoleAccessScope,String)->Unit,onMessageFilePick:(RoleAccessScope,String)->Unit,onReadingFilePick:(RoleAccessScope)->Unit,onStickerPick:(RoleAccessScope)->Unit,onWallpaperPick:(RoleAccessScope)->Unit,onPortraitPick:(RoleAccessScope,String)->Unit,openActivityChatId:String?,openActivityType:String?,openActivityId:String?,onActivityOpened:()->Unit,pendingForwardDraft:ForwardDraft?,onForwardConsumed:()->Unit,onForwardMessage:(String)->Unit) {
    val role=state.roles.firstOrNull { it.scope()==state.scope }
    val chat=state.chats.firstOrNull { it.id==state.chatId }
    val config=state.configs.firstOrNull {it.id==chat?.configId}
    DisposableEffect(vm,chat?.id){onDispose {vm.endVoiceConversation()}}
    var draft by remember { mutableStateOf("") }
    var pendingQuote by remember(chat?.id) { mutableStateOf<MessageQuoteSnapshot?>(null) }
    var options by remember { mutableStateOf(false) }
    var appearance by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf<MessageEntity?>(null) }
    var portraits by remember { mutableStateOf(false) }
    var persona by remember {mutableStateOf(false)}
    var worldbook by remember {mutableStateOf(false)}
    var relationships by remember {mutableStateOf(false)}
    var memories by remember {mutableStateOf(false)}
    var library by remember { mutableStateOf(false) }
    var messageMenuKey by remember { mutableStateOf<String?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedMessageIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var deleteTargets by remember { mutableStateOf<List<MessageEntity>?>(null) }
    var translationSource by remember { mutableStateOf<MessageEntity?>(null) }
    var translationRequest by remember { mutableStateOf<MessageEntity?>(null) }
    var translatedMessage by remember { mutableStateOf<Pair<MessageEntity,String>?>(null) }
    var translationObservedWorking by remember { mutableStateOf(false) }
    var highlighted by remember { mutableStateOf<String?>(null) }
    var addMenu by remember { mutableStateOf(false) }
    var linkDialog by remember { mutableStateOf(false) }
    var stickerLibrary by remember {mutableStateOf(false)}
    var imageGeneration by remember {mutableStateOf(false)}
    var readingLibrary by remember {mutableStateOf(false)}
    var sharedActivities by rememberSaveable(role?.id,role?.spaceId,chat?.id) {mutableStateOf(false)}
    var selectedSharedActivityType by rememberSaveable(role?.id,role?.spaceId,chat?.id) {mutableStateOf<String?>(null)}
    var selectedSharedActivityId by rememberSaveable(role?.id,role?.spaceId,chat?.id) {mutableStateOf<String?>(null)}
    LaunchedEffect(openActivityChatId,openActivityType,openActivityId,chat?.id) {
        if(chat!=null && chat.id==openActivityChatId && openActivityType!=null) {
            selectedSharedActivityType=openActivityType
            selectedSharedActivityId=openActivityId
            sharedActivities=true
            onActivityOpened()
        }
    }
    LaunchedEffect(state.working,translationRequest) {
        if(state.working&&translationRequest!=null)translationObservedWorking=true
        if(!state.working&&translationObservedWorking){translationRequest=null;translationObservedWorking=false}
    }
    var readingReference by remember {mutableStateOf<MessageMaterial?>(null)}
    var speechMessages by remember {mutableStateOf<List<MessageEntity>?>(null)}
    var voiceConversationStart by remember {mutableStateOf(false)}
    var voiceCaptureStart by remember(role?.id,chat?.id) {mutableStateOf(false)}
    val context=LocalContext.current
    val microphonePermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted)voiceCaptureStart=true else vm.microphonePermissionDenied()}
    val conversationMicrophonePermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->if(granted)voiceConversationStart=true else vm.microphonePermissionDenied()}
    LaunchedEffect(vm,chat?.id){vm.speechDrafts.collect {text->draft=if(draft.isBlank())text.take(12000) else (draft+"\n"+text).take(12000)}}
    val opened=remember { System.currentTimeMillis() }
    val palette=chatPalette(state.appearance?.palette ?: LocalCompanionChatPalette.current,companionIsDark())
    val scroll=rememberLazyListState()
    val live=state.messages.lastOrNull { it.kind=="assistant" && it.bubbleStyle=="short" && it.createdAt>=opened }
    val available=remember(live) { live?.let { ChatBubbles.visible(it.content,true,it.state in activeStates) } ?: emptyList() }
    var revealed by remember(live?.id) { mutableIntStateOf(0) }
    val animate=remember { ValueAnimator.areAnimatorsEnabled() }
    LaunchedEffect(live?.id,available.size,live?.state) {
        if(live?.state==MessageState.CANCELLED || live?.state==MessageState.FAILED || !animate)revealed=available.size
        else while(revealed<available.size) { if(revealed>0)delay(450);revealed++ }
    }
    val timeline=remember(state.messages,state.attachments,state.materials,state.messageStickers,state.messageVoices,state.messageReactions,state.generatedImages,chat?.openingMessage,live?.id,revealed) {
        buildList {
            val events=mutableListOf<Pair<Long,List<TimelineBubble>>>()
            if(!chat?.openingMessage.isNullOrEmpty())events+=(chat!!.createdAt to listOf(TimelineBubble("opening",chat.openingMessage,null,true)))
            state.messages.forEach { message ->
                val attachment=state.attachments.firstOrNull {it.messageId==message.id}
                val material=state.materials.firstOrNull {it.messageId==message.id}
                val sticker=state.messageStickers.firstOrNull {it.messageId==message.id}
                val voice=state.messageVoices.firstOrNull {it.messageId==message.id}
                val reactions=state.messageReactions.filter {it.messageId==message.id}
                val parts=ChatBubbles.visible(message.content,message.kind=="assistant"&&message.bubbleStyle=="short",message.state in activeStates)
                val visible=if(message.id==live?.id)parts.take(revealed)else parts
                val bubbles=buildList {
                    if(visible.isEmpty()&&(attachment!=null||material!=null||sticker!=null||voice!=null))add(TimelineBubble(message.id+":content","",message,true,attachment,material,sticker,voice,reactions))
                    else visible.forEachIndexed { i,text->add(TimelineBubble(message.id+":"+i,text,message,i==visible.lastIndex,attachment.takeIf {i==0},material.takeIf {i==0},sticker.takeIf {i==0},voice.takeIf {i==0},reactions.takeIf {i==visible.lastIndex}.orEmpty())) }
                    if(message.state==MessageState.FAILED || message.state==MessageState.CANCELLED)add(TimelineBubble(message.id+":status","",message,true))
                }
                events+=message.createdAt to bubbles
            }
            state.generatedImages.forEach {image->events+=image.createdAt to listOf(TimelineBubble("generated:${image.id}","",null,true,generated=image))}
            var previousTime:Long?=null
            var previousDay:String?=null
            events.sortedWith(compareBy<Pair<Long,List<TimelineBubble>>>{it.first}.thenBy {it.second.firstOrNull()?.key.orEmpty()}).forEach {(createdAt,bubbles)->
                val day=SimpleDateFormat("yyyyMMdd",Locale.CHINA).format(Date(createdAt))
                // Group nearby messages under one time label so repeated per-bubble timestamps do not fragment the conversation.
                val needsTimeLabel=previousTime?.let {createdAt-it>=5*60*1000||day!=previousDay}?:true
                if(needsTimeLabel) {
                    add(TimelineBubble("time:$createdAt","",null,false,timeSeparator=timelineTimeLabel(createdAt)))
                }
                addAll(bubbles)
                previousTime=createdAt
                previousDay=day
            }
        }
    }
    val typing=state.busyChat==state.chatId || (live!=null && revealed<available.size)
    var following by remember { mutableStateOf(true) }
    LaunchedEffect(pendingForwardDraft,chat?.id,role?.id,role?.spaceId) {
        val forwarded=pendingForwardDraft
        if(forwarded!=null&&forwarded.chatId==chat?.id&&forwarded.scope==role?.scope()) {
            draft=forwarded.text.take(12000)
            onForwardConsumed()
        }
    }
    val dragging by scroll.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(dragging,scroll.canScrollForward) { if(!scroll.canScrollForward)following=true else if(dragging)following=false }
    LaunchedEffect(timeline.size,timeline.lastOrNull()?.text,typing,following) {
        if(following && state.focusMessageId==null) {
            withFrameNanos { }
            val last=scroll.layoutInfo.totalItemsCount-1
            if(last>=0)scroll.scrollToItem(last)
        }
    }
    LaunchedEffect(state.focusMessageId,timeline.size) {
        val target=state.focusMessageId
        val index=timeline.indexOfFirst { it.message?.id==target }
        if(target!=null && index>=0) {
            following=false;highlighted=target
            withFrameNanos { };scroll.scrollToItem(index)
            vm.clearMessageFocus()
        }
    }
    fun copyMessages(messages:List<MessageEntity>) {
        val text=messages.map {it.content}.filter {it.isNotBlank()}.joinToString("\n\n")
        if(text.isBlank())Toast.makeText(context,"这条消息没有可复制的文字。",Toast.LENGTH_SHORT).show()
        else {
            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("消息",text))
            Toast.makeText(context,"已复制",Toast.LENGTH_SHORT).show()
        }
    }
    fun quoteMessages(messages:List<MessageEntity>) {
        // Keep the source as separate send metadata so canceling or changing the quote never edits the reply draft.
        val item=messages.singleOrNull()?:return
        if(item.content.isNotBlank()&&item.state==MessageState.COMPLETE) {
            val speaker=if(item.kind=="user")role?.selfName()?:"我" else role?.name?:"AI"
            pendingQuote=MessageQuoteSnapshot(item.id,speaker,item.content.take(12000))
        }
    }
    fun onMessageOperation(message:MessageEntity,operation:MessageOperation) {
        messageMenuKey=null
        when(operation) {
            MessageOperation.COPY->copyMessages(listOf(message))
            MessageOperation.FORWARD->if(message.content.isBlank())Toast.makeText(context,"暂时只能转发文字消息。",Toast.LENGTH_SHORT).show() else onForwardMessage(message.content)
            MessageOperation.FAVORITE->role?.let {vm.setFavorite(it.scope(),message,!message.favorite)}
            MessageOperation.DELETE->deleteTargets=listOf(message)
            MessageOperation.SELECT->{selectionMode=true;selectedMessageIds=setOf(message.id)}
            MessageOperation.QUOTE->quoteMessages(listOf(message))
            MessageOperation.TRANSLATE->translationSource=message
            MessageOperation.SPEAK->{
                if(state.speakingMessageId!=null)vm.stopSpeech()
                else {
                    val start=state.messages.indexOfFirst {it.id==message.id}
                    val queue=if(start>=0)state.messages.drop(start).filter {it.kind in setOf("assistant","user")&&it.state==MessageState.COMPLETE&&it.content.isNotBlank()}else emptyList()
                    if(state.speechServiceConfigs.none {it.kind==SpeechServices.SYNTHESIS})Toast.makeText(context,"请先设置文字转语音服务，再使用朗读。",Toast.LENGTH_LONG).show()
                    else if(queue.isNotEmpty())speechMessages=queue
                }
            }
        }
    }
    if(sharedActivities&&role!=null&&chat!=null) {
        SharedActivitiesPage(role,chat,state,vm,12000-draft.length-if(draft.isBlank())0 else 2,
            onImport={onReadingFilePick(role.scope())},onDraft={text->draft=if(draft.isBlank())text else draft+"\n\n"+text},onClose={sharedActivities=false;selectedSharedActivityType=null;selectedSharedActivityId=null},
            initialActivityType=selectedSharedActivityType,initialActivityId=selectedSharedActivityId,
            onInitialActivityConsumed={selectedSharedActivityType=null;selectedSharedActivityId=null})
        return
    }
    val selectedMessages=state.messages.filter {it.id in selectedMessageIds}
    Surface(color=MaterialTheme.colorScheme.surface,shadowElevation=1.dp) {
        if(selectionMode)Row(Modifier.fillMaxWidth().heightIn(min=64.dp).padding(horizontal=4.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick={selectionMode=false;selectedMessageIds=emptySet()}){Icon(Icons.Default.Close,"退出多选")}
            Text("已选 ${selectedMessages.size} 条",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
            IconButton(enabled=selectedMessages.isNotEmpty(),onClick={copyMessages(selectedMessages)}){Icon(Icons.Default.ContentCopy,"复制所选消息")}
            IconButton(enabled=selectedMessages.isNotEmpty()&&selectedMessages.all {it.content.isNotBlank()},onClick={onForwardMessage(selectedMessages.joinToString("\n\n") {it.content});selectionMode=false;selectedMessageIds=emptySet()}){Icon(Icons.AutoMirrored.Filled.Send,"转发所选消息")}
            IconButton(enabled=selectedMessages.size==1&&selectedMessages.single().content.isNotBlank()&&selectedMessages.single().state==MessageState.COMPLETE,onClick={quoteMessages(selectedMessages);selectionMode=false;selectedMessageIds=emptySet()}){Icon(Icons.Default.FormatQuote,"引用所选消息")}
            IconButton(enabled=selectedMessages.isNotEmpty()&&!state.working,onClick={deleteTargets=selectedMessages}){Icon(Icons.Default.DeleteOutline,"删除所选消息",tint=MaterialTheme.colorScheme.error)}
        } else Column(Modifier.fillMaxWidth()) {
            // Center the conversation identity and keep secondary voice actions in the composer so the header stays as quiet as the reference chat.
            Box(Modifier.fillMaxWidth().heightIn(min=56.dp)) {
                IconButton(onClick={vm.closeChat()},modifier=Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack,"返回会话列表")
                }
                Column(Modifier.align(Alignment.Center),horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(role?.name ?: "聊天",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                    if(typing)Text(if(state.thinking)"正在思考…"else"正在输入…",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick={options=true},modifier=Modifier.align(Alignment.CenterEnd).padding(end=4.dp)) {
                    Icon(Icons.Default.MoreHoriz,"聊天设置")
                }
            }
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.45f))
        }
    }
    Box(Modifier.weight(1f).fillMaxWidth()) {
        ChatBackdrop(state.wallpaper,state.appearance?.dimming ?: 0.18f,palette)
        LazyColumn(state=scroll,contentPadding=PaddingValues(horizontal=10.dp,vertical=16.dp),verticalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxSize()) {
            if(timeline.isEmpty()&&!typing)item("empty") { Box(Modifier.fillMaxWidth().padding(top=80.dp),contentAlignment=Alignment.Center) {
                Surface(shape=RoundedCornerShape(18.dp),color=palette.incoming.copy(alpha=0.96f)) { Text("想说什么，就从这里开始。",Modifier.padding(20.dp),color=palette.ink) }
            } }
            items(timeline,key={it.key}) { bubble->
                if(bubble.timeSeparator!=null)TimeSeparator(bubble.timeSeparator,palette.ink)
                else if(bubble.generated!=null)GeneratedImageBubble(bubble.generated,role,state.generatingImageId==bubble.generated.id,vm)
                else if(bubble.text.isEmpty()&&bubble.attachment==null&&bubble.material==null&&bubble.sticker==null&&bubble.voice==null) {
                    val message=bubble.message!!
                    Row(Modifier.padding(start=48.dp),verticalAlignment=Alignment.CenterVertically) {
                        Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surface) {
                            Column(Modifier.padding(horizontal=12.dp,vertical=8.dp)) {
                                if(message.requestId in state.proactiveRequestIds)Text("主动消息",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
                                Text(if(message.state==MessageState.CANCELLED)"已停止，收到的内容已保留"else errorText(message.errorCode),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
                                Row {if(message.requestId !in state.proactiveRequestIds)TextButton(enabled=state.busyChat==null&&role!=null,onClick={role?.let { vm.retry(it.scope(),message) }}){Text("重新回复")}
                                    if(message.state==MessageState.FAILED)TextButton(onClick={onErrorHelp(message.errorCode)}){Text(if(SupportDiagnostics.route(message.errorCode)==RecoveryRoute.CONNECTIONS)"检查设置"else"处理方法")}}
                            }
                        }
                    }
                } else MessageBubble(
                    bubble,role,palette,vm,
                    bubble.message?.id==highlighted,bubble.message?.id in selectedMessageIds,selectionMode,messageMenuKey==bubble.key,state.speakingMessageId!=null,
                    onDismissMenu={messageMenuKey=null},
                    onTap={bubble.message?.let {message->if(selectionMode)selectedMessageIds=if(message.id in selectedMessageIds)selectedMessageIds-message.id else selectedMessageIds+message.id}},
                    onLongPress={bubble.message?.let {message->if(selectionMode)selectedMessageIds=if(message.id in selectedMessageIds)selectedMessageIds-message.id else selectedMessageIds+message.id else messageMenuKey=bubble.key}},
                    onOperation={operation->bubble.message?.let {onMessageOperation(it,operation)}},
                    onReaction={emoji->bubble.message?.let {message->role?.let {vm.toggleReaction(it.scope(),message.chatId,message.id,Reactions.USER,emoji)}}}
                )
            }
            if(typing)item("typing") { Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                if(role!=null)PortraitAvatar(role.name,role.scope(),vm,36.dp)
                Surface(shape=themedMessageShape(false),color=palette.incoming) {
                    Row(Modifier.padding(horizontal=18.dp,vertical=13.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                        repeat(3) { Box(Modifier.size(6.dp).background(palette.ink.copy(alpha=0.55f),CircleShape)) }
                        Text(if(state.thinking)"正在思考"else"正在输入",fontSize=11.sp,color=palette.ink,modifier=Modifier.padding(start=4.dp))
                    }
                }
            } }
            item("bottom") { Spacer(Modifier.height(1.dp)) }
        }
        if(!following && scroll.canScrollForward)SmallFloatingActionButton(onClick={following=true},modifier=Modifier.align(Alignment.BottomEnd).padding(16.dp),containerColor=MaterialTheme.colorScheme.surface) {
            Icon(Icons.Default.KeyboardArrowDown,"回到最新消息")
        }
    }
    if(state.busyChat!=null&&state.busyChat!=state.chatId)TextButton(onClick={vm.selectChat(state.busyScope!!,state.busyChat)}){Text("另一段对话正在回复，点击查看")}
    Surface(color=MaterialTheme.colorScheme.surface,shadowElevation=2.dp) {
        Column {
            state.pendingImage?.let {image->
                Row(Modifier.fillMaxWidth().padding(start=60.dp,end=12.dp,top=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Image(image.bitmap.asImageBitmap(),"待发送图片",Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)),contentScale=ContentScale.Crop)
                    Column(Modifier.weight(1f)){Text("图片已准备",fontWeight=FontWeight.SemiBold);Text("发送后会保存到加密对话，并上传给当前模型。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                    IconButton(onClick=vm::dismissMessageImage){Icon(Icons.Default.Close,"移除图片")}
                }
            }
            state.pendingMaterial?.let {material->
                Row(Modifier.fillMaxWidth().padding(start=60.dp,end=12.dp,top=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.primaryContainer) {
                        Icon(if(material.kind==TextMaterials.KIND_LINK)Icons.Default.Link else Icons.Default.Description,null,Modifier.padding(13.dp).size(26.dp),tint=MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                        Text(material.displayName,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text(buildString {append(when(material.kind){TextMaterials.KIND_LINK->"网页资料";TextMaterials.KIND_READING->"伴读引用";else->"本机文件"});append(" · ");append(material.text.length);append(" 字");if(material.truncated)append(" · 已截取")},style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick=vm::dismissMessageMaterial){Icon(Icons.Default.Close,"移除资料")}
                }
            }
            state.pendingSticker?.let {sticker->
                val bitmap=remember(sticker.sha256){BitmapFactory.decodeByteArray(sticker.data,0,sticker.data.size)}
                Row(Modifier.fillMaxWidth().padding(start=60.dp,end=12.dp,top=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    if(bitmap!=null)Image(bitmap.asImageBitmap(),sticker.displayName,Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)),contentScale=ContentScale.Crop)
                    else Icon(Icons.Default.BrokenImage,"素材无法显示",Modifier.size(48.dp))
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(2.dp)) {
                        Text(sticker.displayName,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Text(if(config?.visionEnabled==true)"名称和处理后的图片会发给模型"else"只把名称发给模型；本机仍显示图片",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick=vm::dismissPendingSticker){Icon(Icons.Default.Close,"移除表情素材")}
                }
            }
            // Keep the quote preview immediately above the composer so it cannot be mistaken for chat-header content.
            pendingQuote?.let { quote -> Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth().padding(start=60.dp,end=12.dp,top=8.dp)) {
                Row(Modifier.padding(start=12.dp,end=4.dp,top=6.dp,bottom=6.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("引用 ${quote.speaker}",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSecondaryContainer,fontWeight=FontWeight.SemiBold)
                        Text(quote.preview.replace('\n',' '),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSecondaryContainer,maxLines=1,overflow=TextOverflow.Ellipsis)
                    }
                    IconButton(onClick={pendingQuote=null}){Icon(Icons.Default.Close,"取消引用")}
                }
            } }
            SingleAudioStatus(state,vm)
            // Group voice, text, emoji, and attachment entry in one bottom bar; duplicate controls made the current composer feel crowded.
            HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.45f))
            Row(Modifier.fillMaxWidth().padding(horizontal=6.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically) {
                IconButton(enabled=role!=null&&chat!=null&&state.voiceConversation==null,onClick={
                    if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)voiceConversationStart=true
                    else conversationMicrophonePermission.launch(Manifest.permission.RECORD_AUDIO)
                }){Icon(Icons.Default.Call,"开始连续语音会话")}
                OutlinedTextField(draft,{if(it.length<=12000)draft=it},Modifier.weight(1f),enabled=chat!=null,
                    placeholder={Text("发送消息或资料…")},shape=RoundedCornerShape(12.dp),maxLines=5,
                    trailingIcon={IconButton(enabled=chat!=null&&!state.transcribingVoice&&state.speakingMessageId==null,onClick={
                    if(state.voiceRecording) {if(state.voiceRecordingReady)vm.stopVoiceRecording()else vm.cancelVoiceCapture()}
                    else if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)voiceCaptureStart=true
                    else microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                }){Icon(if(state.voiceRecording)Icons.Default.Stop else Icons.Default.Mic,if(state.voiceRecording){if(state.voiceRecordingReady)"停止录音并预览" else "取消连接麦克风"} else "录音转文字",tint=if(state.voiceRecording)MaterialTheme.colorScheme.error else LocalContentColor.current)}},
                    colors=OutlinedTextFieldDefaults.colors(unfocusedBorderColor=Color.Transparent,focusedBorderColor=MaterialTheme.colorScheme.primary.copy(alpha=0.4f),unfocusedContainerColor=MaterialTheme.colorScheme.surfaceVariant,focusedContainerColor=MaterialTheme.colorScheme.surfaceVariant))
                IconButton(onClick={stickerLibrary=true},enabled=role!=null&&chat!=null){Icon(Icons.Default.EmojiEmotions,"表情与本机素材")}
                Box {
                    IconButton(onClick={addMenu=true},enabled=role!=null&&chat!=null&&state.busyChat==null&&state.generatingImageId==null&&!state.working){Icon(Icons.Default.AddCircleOutline,"添加资料")}
                    DropdownMenu(expanded=addMenu,onDismissRequest={addMenu=false}) {
                        DropdownMenuItem(text={Text(if(config?.visionEnabled==true)"图片" else "图片（当前连接未开启）")},leadingIcon={Icon(Icons.Default.AddPhotoAlternate,null)},enabled=config?.visionEnabled==true,onClick={addMenu=false;if(role!=null&&chat!=null)onMessageImagePick(role.scope(),chat.id)})
                        DropdownMenuItem(text={Text("TXT / Markdown 文件")},leadingIcon={Icon(Icons.Default.Description,null)},onClick={addMenu=false;if(role!=null&&chat!=null)onMessageFilePick(role.scope(),chat.id)})
                        DropdownMenuItem(text={Text("HTTPS 链接")},leadingIcon={Icon(Icons.Default.Link,null)},onClick={addMenu=false;linkDialog=true})
                        DropdownMenuItem(text={Text("TXT / Markdown 伴读")},leadingIcon={Icon(Icons.AutoMirrored.Filled.MenuBook,null)},onClick={addMenu=false;readingReference=null;readingLibrary=true})
                        DropdownMenuItem(text={Text("共同活动 · 约会、游戏、影音")},leadingIcon={Icon(Icons.AutoMirrored.Filled.MenuBook,null)},onClick={addMenu=false;vm.stopAudioForBackground();sharedActivities=true})
                        DropdownMenuItem(text={Text("生成图片")},leadingIcon={Icon(Icons.Default.AutoAwesome,null)},onClick={addMenu=false;imageGeneration=true})
                    }
                }
                if(state.busyChat!=null)FilledIconButton(onClick={vm.cancel()},modifier=Modifier.size(48.dp)){Icon(Icons.Default.Stop,"停止回复")}
                else FilledIconButton(enabled=chat!=null&&state.generatingImageId==null&&(draft.isNotBlank()||state.pendingImage!=null||state.pendingMaterial!=null||state.pendingSticker!=null)&&!state.working,onClick={val sent=draft;val sentQuote=pendingQuote;following=true;vm.send(sent,sentQuote){if(draft==sent)draft="";if(pendingQuote==sentQuote)pendingQuote=null}},modifier=Modifier.size(48.dp)){Icon(Icons.AutoMirrored.Filled.Send,"发送")}
            }
        }
        }
    if(options && role!=null)AlertDialog(onDismissRequest={options=false},title={Text("与${role.name}的聊天")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        ReplyStyleSelector(role.replyStyle,!state.working&&!role.isRaw()){vm.setReplyStyle(role.scope(),it)}
        if(role.isRaw())Text("原味模型不会加入回复风格、人设、关系、记忆、世界书或工具；会话历史仍会发送。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick={options=false;appearance=true},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Wallpaper,null);Spacer(Modifier.width(8.dp));Text("背景与气泡颜色")}
        OutlinedButton(onClick={options=false;portraits=true},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.AccountCircle,null);Spacer(Modifier.width(8.dp));Text("角色与我的头像")}
        OutlinedButton(onClick={options=false;persona=true},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Person,null);Spacer(Modifier.width(8.dp));Text("用户面具")}
        OutlinedButton(onClick={options=false;worldbook=true},modifier=Modifier.fillMaxWidth()){Icon(Icons.AutoMirrored.Filled.MenuBook,null);Spacer(Modifier.width(8.dp));Text("世界书与注入预览")}
        OutlinedButton(onClick={options=false;relationships=true},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Hub,null);Spacer(Modifier.width(8.dp));Text("关系与原味模型")}
        OutlinedButton(onClick={options=false;memories=true},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Bookmarks,null);Spacer(Modifier.width(8.dp));Text("长期记忆")}
        OutlinedButton(onClick={options=false;library=true},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Search,null);Spacer(Modifier.width(8.dp));Text("查找历史与收藏")}
        state.messages.lastOrNull { it.kind=="assistant"&&it.elapsedMs>=0 }?.let { message->TextButton(onClick={options=false;details=message}){Text("最近回复信息")} }
        Text("回复风格从下一次请求生效。背景只保存在本机，不会发给 AI。",style=MaterialTheme.typography.bodySmall)
    }},confirmButton={TextButton(onClick={options=false}){Text("完成")}})
    if(appearance&&role!=null)ChatAppearanceDialog(state,vm,role,onPick={onWallpaperPick(role.scope());appearance=false}){appearance=false}
    if(portraits&&role!=null)PortraitDialog(state,vm,role,onPick={kind->onPortraitPick(role.scope(),kind);portraits=false}){portraits=false}
    if(library&&role!=null)MessageLibrary(role,vm){library=false}
    deleteTargets?.let {targets->AlertDialog(
        onDismissRequest={deleteTargets=null},title={Text(if(targets.size==1)"删除这条消息？" else "删除所选 ${targets.size} 条消息？")},
        text={Text("删除后会从本机聊天记录中移除。已由这些消息提炼的记忆会保留，但解除与原消息的来源关联。")},
        confirmButton={TextButton(enabled=!state.working,onClick={
            val currentRole=role;val currentChat=chat
            if(currentRole!=null&&currentChat!=null)vm.deleteMessages(currentRole.scope(),currentChat.id,targets.map {it.id})
            deleteTargets=null;selectedMessageIds=emptySet();selectionMode=false
        }){Text("删除",color=MaterialTheme.colorScheme.error)}},
        dismissButton={TextButton(onClick={deleteTargets=null}){Text("取消")}}
    )}
    translationSource?.let {source->AlertDialog(
        onDismissRequest={translationSource=null},title={Text("翻译消息")},
        text={Text("将把这条消息发送到当前聊天模型进行翻译。译文只显示在本机，不会加入聊天记录。")},
        confirmButton={TextButton(enabled=!state.working&&chat!=null&&role!=null,onClick={
            val currentRole=role;val currentChat=chat
            translationSource=null
            if(currentRole!=null&&currentChat!=null) {
                translationRequest=source
                vm.translateMessage(currentRole.scope(),currentChat.id,source.id) {translated->translationRequest=null;translatedMessage=source to translated}
            }
        }){Text("翻译")}},dismissButton={TextButton(onClick={translationSource=null}){Text("取消")}}
    )}
    translatedMessage?.let {(source,translated)->AlertDialog(
        onDismissRequest={translatedMessage=null},title={Text("翻译结果")},
        text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("原文",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(source.content)
            HorizontalDivider();Text("译文",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(translated)
        }},confirmButton={TextButton(onClick={copyMessages(listOf(source.copy(content=translated)));translatedMessage=null}){Text("复制译文")}},dismissButton={TextButton(onClick={translatedMessage=null}){Text("关闭")}}
    )}
    if(translationRequest!=null&&state.working)AlertDialog(onDismissRequest={},title={Text("正在翻译")},text={Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {CircularProgressIndicator(Modifier.size(24.dp));Text("正在请求当前聊天模型…")}},confirmButton={})
    speechMessages?.let {messages->if(role!=null&&chat!=null)SpeechPlaybackDialog(role,messages,chat.id,state,vm){speechMessages=null}}
    if(persona&&role!=null)PersonaEditorDialog(role,state,vm){persona=false}
    if(worldbook&&role!=null)WorldbookDialog(role,state,vm){worldbook=false}
    if(relationships&&role!=null)RelationshipDialog(role,state,vm){relationships=false}
    if(memories&&role!=null)MemoryDialog(role,state,vm){memories=false}
    if(linkDialog&&role!=null&&chat!=null)LinkMaterialDialog(onDismiss={linkDialog=false}) {url->linkDialog=false;vm.readMessageLink(role.scope(),chat.id,url)}
    if(stickerLibrary&&role!=null)StickerLibraryDialog(role,state,vm,onImport={stickerLibrary=false;onStickerPick(role.scope())},onEmoji={emoji->if(draft.length+emoji.length<=12000)draft+=emoji},onDismiss={stickerLibrary=false})
    if(imageGeneration&&role!=null&&chat!=null)ImageGenerationDialog(role,chat,state,vm,onSettings={onSettings()},onDismiss={imageGeneration=false})
    if(state.recordedVoice!=null)VoiceTranscriptionDialog(state,vm,onSettings)
    if(voiceCaptureStart&&role!=null&&chat!=null)VoiceCaptureStartDialog(role,chat,vm){voiceCaptureStart=false}
    if(voiceConversationStart&&role!=null&&chat!=null)VoiceConversationStartDialog(role,chat,state,vm,onDismiss={voiceConversationStart=false}){transcription,synthesis,input,output->
        voiceConversationStart=false;vm.startVoiceConversation(role.scope(),chat.id,transcription,synthesis,input,output)
    }
    state.voiceConversation?.takeIf {it.scope==role?.scope()&&it.chatId==chat?.id}?.let {VoiceConversationDialog(it,vm)}
    if(readingLibrary&&role!=null&&chat!=null)ReadingLibraryDialog(role,chat,state,vm,readingReference,onImport={onReadingFilePick(role.scope())},onDismiss={readingLibrary=false;readingReference=null})
    details?.let { message->AlertDialog(onDismissRequest={details=null},title={Text("消息信息")},text={Text("发送时间：${SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.CHINA).format(Date(message.createdAt))}"+
        if(message.kind=="assistant"&&message.elapsedMs>=0)"\n回复用时：${message.elapsedMs/1000.0} 秒"else"")},confirmButton={TextButton(onClick={details=null}){Text("关闭")}}) }
}

@Composable private fun GeneratedImageBubble(image:GeneratedImage,role:RoleEntity?,active:Boolean,vm:CompanionViewModel) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.Start,verticalAlignment=Alignment.Top) {
        if(role!=null)PortraitAvatar(role.name,role.scope(),vm,36.dp)
        Spacer(Modifier.width(10.dp))
        Surface(shape=themedMessageShape(false),color=MaterialTheme.colorScheme.surface,shadowElevation=0.5.dp,modifier=Modifier.widthIn(max=340.dp)) {
            Column(Modifier.padding(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                when(image.state) {
                    MessageState.COMPLETE-> {
                        val bytes=image.data
                        val bitmap=remember(image.sha256){bytes?.let {BitmapFactory.decodeByteArray(it,0,it.size)}}
                        if(bitmap!=null)Image(bitmap.asImageBitmap(),"AI 生成图片",Modifier.fillMaxWidth().aspectRatio(image.width.toFloat()/image.height).clip(RoundedCornerShape(14.dp)),contentScale=ContentScale.Fit)
                        else Text("生成图片无法显示",color=MaterialTheme.colorScheme.error)
                    }
                    MessageState.QUEUED,MessageState.WAITING,MessageState.GENERATING->Column(Modifier.fillMaxWidth().padding(18.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        CircularProgressIndicator();Text("正在生成 ${image.requestedSize} 图片…")
                        if(active)OutlinedButton(onClick=vm::cancelImageGeneration){Icon(Icons.Default.Stop,null);Spacer(Modifier.width(6.dp));Text("停止生图")}
                    }
                    MessageState.CANCELLED->Text("图片生成已取消，没有保存伪造结果。",Modifier.padding(10.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
                    MessageState.FAILED->Text(errorText(image.errorCode),Modifier.padding(10.dp),color=MaterialTheme.colorScheme.error)
                }
                HorizontalDivider()
                Text(image.prompt,Modifier.padding(horizontal=6.dp),style=MaterialTheme.typography.bodyMedium,maxLines=5,overflow=TextOverflow.Ellipsis)
                Text("${image.model} · ${image.requestedSize}",Modifier.padding(start=6.dp,end=6.dp,bottom=4.dp),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(46.dp))
    }
}

@Composable private fun TimeSeparator(label:String,ink:Color) {
    Box(Modifier.fillMaxWidth().padding(vertical=7.dp),contentAlignment=Alignment.Center) {
        Surface(color=ink.copy(alpha=0.08f),shape=RoundedCornerShape(8.dp)) {
            Text(label,Modifier.padding(horizontal=10.dp,vertical=4.dp),fontSize=12.sp,color=ink.copy(alpha=0.72f))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun MessageBubble(
    bubble:TimelineBubble,role:RoleEntity?,palette:ChatPalette,vm:CompanionViewModel,highlighted:Boolean,
    selected:Boolean,selectionMode:Boolean,menuExpanded:Boolean,speechPlaying:Boolean,onDismissMenu:()->Unit,onTap:()->Unit,onLongPress:()->Unit,onOperation:(MessageOperation)->Unit,onReaction:(String)->Unit
) {
    val user=bubble.message?.kind=="user"
    Row(Modifier.fillMaxWidth(),horizontalArrangement=if(user)Arrangement.End else Arrangement.Start,verticalAlignment=Alignment.Top) {
        if(!user) { if(role!=null)PortraitAvatar(role.name,role.scope(),vm,36.dp);Spacer(Modifier.width(10.dp)) } else Spacer(Modifier.width(46.dp))
        Column(Modifier.weight(1f,fill=false).widthIn(max=420.dp),horizontalAlignment=if(user)Alignment.End else Alignment.Start) {
            Box {
                // Keep a normal tap inert; the requested message actions belong to the anchored long-press menu.
                Surface(modifier=if(bubble.message!=null)Modifier.heightIn(min=48.dp).combinedClickable(
                    enabled=true,onClickLabel=if(selectionMode)"选择消息" else null,onLongClickLabel="消息操作",onClick=onTap,onLongClick=onLongPress
                )else Modifier,
                    border=when {selected->BorderStroke(2.dp,MaterialTheme.colorScheme.primary);highlighted->BorderStroke(2.dp,MaterialTheme.colorScheme.primary);else->null},
                    color=if(user)palette.outgoing else palette.incoming,shape=themedMessageShape(user),shadowElevation=0.5.dp) {
                    Column(Modifier.padding(horizontal=if(bubble.attachment==null&&bubble.material==null&&bubble.sticker==null&&bubble.voice==null)14.dp else 6.dp,vertical=if(bubble.attachment==null&&bubble.material==null&&bubble.sticker==null&&bubble.voice==null)11.dp else 6.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        bubble.message?.takeIf {it.quotedPreview.isNotBlank()}?.let {message->QuotePreview(message,palette)}
                        bubble.attachment?.let {attachment->ImageContent(attachment)}
                        bubble.material?.let {material->MessageMaterialCard(material,palette.ink)}
                        bubble.sticker?.let {sticker->StickerContent(sticker)}
                        bubble.voice?.let {voice->VoiceBubbleContent(voice,palette)}
                        if(bubble.text.isNotEmpty()&&bubble.voice==null)Box(Modifier.padding(horizontal=if(bubble.attachment==null&&bubble.material==null&&bubble.sticker==null)0.dp else 8.dp,vertical=if(bubble.attachment==null&&bubble.material==null&&bubble.sticker==null)0.dp else 4.dp)){BasicMarkdown(bubble.text,palette.ink)}
                    }
                }
                bubble.message?.let {message->
                    val operations=listOf(
                        Triple(MessageOperation.COPY,"复制",Icons.Default.ContentCopy),
                        Triple(MessageOperation.FORWARD,"转发",Icons.AutoMirrored.Filled.Send),
                        Triple(MessageOperation.FAVORITE,if(message.favorite)"取消收藏" else "收藏",if(message.favorite)Icons.Default.Star else Icons.Default.StarBorder),
                        Triple(MessageOperation.DELETE,"删除",Icons.Default.DeleteOutline),
                        Triple(MessageOperation.SELECT,"多选",Icons.Default.CheckCircleOutline),
                        Triple(MessageOperation.QUOTE,"引用",Icons.Default.FormatQuote),
                        Triple(MessageOperation.TRANSLATE,"翻译",Icons.Default.Translate),
                        Triple(MessageOperation.SPEAK,if(speechPlaying)"停止朗读" else "连续朗读",if(speechPlaying)Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp)
                    )
                    DropdownMenu(expanded=menuExpanded,onDismissRequest=onDismissMenu,modifier=Modifier.width(320.dp),shape=RoundedCornerShape(12.dp),containerColor=Color(0xFF3F3F3F),tonalElevation=0.dp,shadowElevation=10.dp) {
                        Column(Modifier.padding(horizontal=8.dp,vertical=6.dp)) {
                            Row(Modifier.fillMaxWidth().padding(horizontal=2.dp,vertical=2.dp),horizontalArrangement=Arrangement.SpaceEvenly) {
                                Reactions.ROLE_EMOJI.forEach {emoji->
                                    val active=bubble.reactions.any {it.emoji==emoji&&it.side==Reactions.USER}
                                    Text(emoji,Modifier.clip(CircleShape).clickable{onReaction(emoji);onDismissMenu()}.padding(6.dp),fontSize=20.sp,color=if(active)Color.White else Color.White.copy(alpha=0.72f))
                                }
                            }
                            HorizontalDivider(color=Color.White.copy(alpha=0.14f))
                            operations.chunked(4).forEachIndexed {index,row->
                                Row(Modifier.fillMaxWidth()) {
                                row.forEach {(operation,label,icon)->
                                    val textAvailable=message.content.isNotBlank()
                                    val enabled=when(operation) {
                                        MessageOperation.COPY,MessageOperation.FORWARD,MessageOperation.QUOTE,MessageOperation.TRANSLATE->textAvailable&&message.state==MessageState.COMPLETE
                                        MessageOperation.FAVORITE->message.state !in activeStates
                                        MessageOperation.DELETE->message.state !in activeStates
                                        MessageOperation.SPEAK->speechPlaying||(textAvailable&&message.state==MessageState.COMPLETE)
                                        else->true
                                    }
                                    Column(Modifier.weight(1f).height(66.dp).clickable(enabled=enabled,onClickLabel=label,onClick={onOperation(operation)}).padding(horizontal=2.dp,vertical=6.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)) {
                                        Icon(icon,label,Modifier.size(22.dp),tint=if(enabled)Color.White else Color.White.copy(alpha=0.38f))
                                        Text(label,maxLines=1,overflow=TextOverflow.Ellipsis,fontSize=11.sp,color=if(enabled)Color.White else Color.White.copy(alpha=0.38f))
                                    }
                                }
                                }
                                if(index==0)HorizontalDivider(color=Color.White.copy(alpha=0.14f))
                            }
                        }
                    }
                }
            }
            if(bubble.reactions.isNotEmpty()) {
                Row(Modifier.padding(top=4.dp),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    bubble.reactions.groupBy {it.emoji}.forEach {(emoji,list)->
                        Surface(shape=RoundedCornerShape(10.dp),color=palette.reaction,shadowElevation=1.dp) {
                            Text(emoji+(if(list.size>1)" ${list.size}" else ""),Modifier.padding(horizontal=6.dp,vertical=2.dp),fontSize=11.sp,color=palette.ink)
                        }
                    }
                }
            }
        }
        if(user) { Spacer(Modifier.width(10.dp));if(role!=null)PortraitAvatar(role.selfName(),role.scope(),vm,36.dp,"self") } else Spacer(Modifier.width(46.dp))
    }
}

@Composable private fun QuotePreview(message:MessageEntity,palette:ChatPalette) {
    Surface(color=palette.ink.copy(alpha=0.08f),shape=RoundedCornerShape(8.dp)) {
        Text("${message.quotedSpeaker}：${message.quotedPreview.replace('\n',' ')}",Modifier.padding(horizontal=9.dp,vertical=6.dp),maxLines=2,overflow=TextOverflow.Ellipsis,fontSize=12.sp,fontWeight=FontWeight.Medium,color=palette.ink.copy(alpha=0.78f))
    }
}

@Composable private fun ImageContent(attachment:MessageAttachment) {
    val bitmap=remember(attachment.sha256){BitmapFactory.decodeByteArray(attachment.data,0,attachment.data.size)}
    if(bitmap!=null)Image(bitmap.asImageBitmap(),"用户发送的图片",Modifier.width(260.dp).aspectRatio(attachment.width.toFloat()/attachment.height).clip(RoundedCornerShape(14.dp)),contentScale=ContentScale.Crop)
    else Text("图片无法显示",Modifier.padding(8.dp),color=MaterialTheme.colorScheme.error)
}

@Composable private fun StickerContent(sticker:MessageSticker) {
    val bitmap=remember(sticker.sha256){BitmapFactory.decodeByteArray(sticker.data,0,sticker.data.size)}
    if(bitmap!=null)Image(bitmap.asImageBitmap(),sticker.displayName,Modifier.widthIn(max=190.dp).heightIn(max=190.dp).aspectRatio(sticker.width.toFloat()/sticker.height).clip(RoundedCornerShape(14.dp)),contentScale=ContentScale.Fit)
    else Text("表情素材无法显示",Modifier.padding(8.dp),color=MaterialTheme.colorScheme.error)
}

@Composable private fun VoiceBubbleContent(voice:MessageVoice,palette:ChatPalette) {
    var playing by remember(voice.messageId){mutableStateOf(false)}
    val player=remember(voice.messageId){MessageVoicePlayer()}
    DisposableEffect(voice.messageId){onDispose {player.stop()}}
    LaunchedEffect(playing){if(playing){delay(voice.durationMs);playing=false}}
    Row(Modifier.widthIn(min=170.dp,max=260.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        IconButton(onClick={if(playing){player.stop();playing=false}else{playing=true;player.play(voice.sampleRate,voice.data){}}},modifier=Modifier.size(36.dp)) {
            Icon(if(playing)Icons.Default.Pause else Icons.AutoMirrored.Filled.VolumeUp,"播放语音",Modifier.size(20.dp),tint=palette.ink)
        }
        Row(Modifier.weight(1f).height(26.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(2.dp)) {
            voice.waveform.forEach {level->
                val barHeight=(3+(level.toInt() and 0xFF)/255f*22).dp
                Box(Modifier.width(2.dp).height(barHeight).background(palette.waveform.copy(alpha=if(playing)0.9f else 0.45f),RoundedCornerShape(1.dp)))
            }
        }
        Text(MessageVoices.secondsText(voice.durationMs),fontSize=11.sp,color=palette.ink.copy(alpha=0.72f))
    }
}

@Composable private fun MessageMaterialCard(material:MessageMaterial,ink:Color) {
    Surface(shape=RoundedCornerShape(14.dp),color=ink.copy(alpha=0.075f),modifier=Modifier.widthIn(min=220.dp,max=280.dp)) {
        Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Icon(if(material.kind==TextMaterials.KIND_LINK)Icons.Default.Link else Icons.Default.Description,null,Modifier.size(28.dp),tint=ink.copy(alpha=0.8f))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                Text(material.displayName,color=ink,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
                Text(buildString {append(when(material.kind){TextMaterials.KIND_LINK->"网页资料";TextMaterials.KIND_READING->"伴读引用";else->"文本文件"});append(" · ");append(material.text.length);append(" 字");if(material.kind==TextMaterials.KIND_READING)runCatching {ReadingDocuments.parseReference(requireNotNull(material.sourceUrl))}.getOrNull()?.let {append(" · 字符 ").append(it.start).append("–").append(it.end)};if(material.truncated)append(" · 已截取")},fontSize=12.sp,color=ink.copy(alpha=0.72f))
            }
        }
    }
}

@Composable private fun LinkMaterialDialog(onDismiss:()->Unit,onRead:(String)->Unit) {
    var value by remember {mutableStateOf("")}
    val valid=remember(value){runCatching {TextMaterials.normalizeHttpsUrl(value);true}.getOrDefault(false)}
    AlertDialog(onDismissRequest=onDismiss,title={Text("读取 HTTPS 链接")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(value,{if(it.length<=TextMaterials.MAX_URL)value=it},Modifier.fillMaxWidth(),label={Text("网页地址")},placeholder={Text("https://example.com/article")},singleLine=true)
        Text("应用会立即联网读取这个地址。不会携带 API Key 或 Cookie，不执行页面脚本，也不会跟随重定向。只接受 UTF-8 文本、Markdown 或 HTML；读取后仍需预览确认。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }},confirmButton={TextButton(enabled=valid,onClick={onRead(value.trim())}){Text("读取并预览")}},dismissButton={TextButton(onClick=onDismiss){Text("取消")}})
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun ReplyStyleSelector(style: String,enabled: Boolean=true,onChange:(String)->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text("回复方式",style=MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(style=="short",{onChange("short")},enabled=enabled,label={Text("连续短消息")})
            FilterChip(style=="paragraph",{onChange("paragraph")},enabled=enabled,label={Text("整段回复")})
        }
    }
}

@Composable private fun NewConversationDialog(state: CompanionState,role:RoleEntity,onDismiss:()->Unit,onCreate:(String)->Unit) {
    var config by remember(role.id) { mutableStateOf(role.defaultConfigId) }
    AlertDialog(onDismissRequest=onDismiss,title={Text("与${role.name}聊天")},text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("选择聊天模型",style=MaterialTheme.typography.titleSmall)
        state.configs.forEach { item->Row(Modifier.fillMaxWidth().clickable { config=item.id },verticalAlignment=Alignment.CenterVertically) { RadioButton(config==item.id,{config=item.id});Text(item.name) } }
    }},confirmButton={TextButton(enabled=config!=null&&!state.working,onClick={onCreate(requireNotNull(config))}){Text("开始聊天")}},dismissButton={TextButton(onClick=onDismiss){Text("取消")}})
}

@Composable internal fun BasicMarkdown(text: String,ink: Color) {
    Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
        var code=false
        text.lines().forEach { line->
            if(line.startsWith("```")||line.startsWith("~~~"))code=!code else {
                val heading=!code&&Regex("^#{1,3} .*?").containsMatchIn(line)
                Text(if(heading)line.trimStart('#',' ')else line,fontSize=16.sp,lineHeight=24.sp,color=ink,
                    fontWeight=if(heading)FontWeight.SemiBold else FontWeight.Normal,fontFamily=if(code)FontFamily.Monospace else FontFamily.Default)
            }
        }
    }
}

internal fun timeLabel(time: Long)=SimpleDateFormat(if(System.currentTimeMillis()-time<86_400_000)"HH:mm"else"M/d",Locale.CHINA).format(Date(time))
private fun timelineTimeLabel(time:Long):String {
    val now=System.currentTimeMillis()
    val format=when {
        SimpleDateFormat("yyyy",Locale.CHINA).format(Date(time))!=SimpleDateFormat("yyyy",Locale.CHINA).format(Date(now))->"yyyy年M月d日 HH:mm"
        SimpleDateFormat("yyyyMMdd",Locale.CHINA).format(Date(time))!=SimpleDateFormat("yyyyMMdd",Locale.CHINA).format(Date(now))->"M月d日 HH:mm"
        else->"HH:mm"
    }
    return SimpleDateFormat(format,Locale.CHINA).format(Date(time))
}
