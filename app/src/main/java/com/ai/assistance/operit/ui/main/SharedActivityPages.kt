package com.ai.assistance.operit.ui.main

import com.ai.assistance.operit.ui.main.ThemedIcon as Icon

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.*

private fun activityStateLabel(value:String)=when(value){"active"->"进行中";"paused"->"已暂停";"completed"->"已结束";else->error("Unknown activity state")}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun ColumnScope.SharedActivitiesPage(role:RoleEntity,chat:ChatEntity,state:CompanionState,vm:CompanionViewModel,
    draftSpace:Int,onImport:()->Unit,onDraft:(String)->Unit,onClose:()->Unit,initialActivityType:String?=null,
    initialActivityId:String?=null,onInitialActivityConsumed:()->Unit={}) {
    var selectedId by rememberSaveable(chat.id){mutableStateOf<String?>(null)}
    var routeRecordId by rememberSaveable(chat.id){mutableStateOf<String?>(null)}
    var music by rememberSaveable(chat.id){mutableStateOf(false)}
    var video by rememberSaveable(chat.id){mutableStateOf(false)}
    var bilibili by rememberSaveable(chat.id){mutableStateOf(false)}
    var play by rememberSaveable(chat.id){mutableStateOf(false)}
    var home by rememberSaveable(chat.id){mutableStateOf(false)}
    LaunchedEffect(initialActivityType,initialActivityId) {
        if(initialActivityType!=null) {
            val kind=CompanionActivityKind.entries.first {it.key==initialActivityType}
            routeRecordId=initialActivityId
            when(kind) {
                CompanionActivityKind.HUB -> Unit
                CompanionActivityKind.READING -> selectedId=requireNotNull(initialActivityId)
                CompanionActivityKind.AUDIO -> music=true
                CompanionActivityKind.VIDEO -> video=true
                CompanionActivityKind.BILIBILI -> bilibili=true
                CompanionActivityKind.PLAY -> play=true
                CompanionActivityKind.HOME -> Unit
            }
            onInitialActivityConsumed()
        }
    }
    if(home) {
        HomePage(role,state,vm,{home=false})
        return
    }
    if(play) {
        PlayPage(role,chat,state,vm,draftSpace,onDraft={onDraft(it);onClose()},onClose={play=false;routeRecordId=null},initialActivityId=routeRecordId)
        return
    }
    if(bilibili) {
        BilibiliPage(role,chat,state,vm,draftSpace,onDraft={onDraft(it);onClose()},onClose={bilibili=false;routeRecordId=null},initialWatchId=routeRecordId)
        return
    }
    if(video) {
        MusicPage(role,chat,state,vm,draftSpace,onDraft={onDraft(it);onClose()},onClose={video=false;routeRecordId=null},kind="video",initialSessionId=routeRecordId)
        return
    }
    if(music) {
        MusicPage(role,chat,state,vm,draftSpace,onDraft={onDraft(it);onClose()},onClose={music=false;routeRecordId=null},initialSessionId=routeRecordId)
        return
    }
    var chooseBook by remember {mutableStateOf(false)}
    var editNote by remember {mutableStateOf(false)}
    var confirmEnd by remember {mutableStateOf(false)}
    var confirmDelete by remember {mutableStateOf(false)}
    var preview by remember {mutableStateOf<SharedActivity?>(null)}
    val activities=state.sharedActivities.filter {it.chatId==chat.id&&it.roleId==role.id&&it.spaceId==role.spaceId}
    val activity=activities.firstOrNull {it.id==selectedId}
    val document=state.readingDocuments.firstOrNull {it.id==activity?.readingDocumentId}
    val back={if(selectedId==null)onClose()else selectedId=null}
    BackHandler(onBack=back)
    Column(Modifier.weight(1f).fillMaxWidth()) {
        TopAppBar(title={Column {
            Text(if(selectedId==null)"共同活动" else "一起读书",maxLines=1)
            Text("和 ${role.name}",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)
        }},navigationIcon={IconButton(onClick=back){Icon(Icons.AutoMirrored.Filled.ArrowBack,"返回")}},actions={
            if(selectedId==null)TextButton(enabled=!state.working,onClick={chooseBook=true}){Text("发起共读")}
            else if(activity!=null)IconButton(enabled=!state.working,onClick={confirmDelete=true}){Icon(Icons.Default.DeleteOutline,"删除本次活动")}
        })
        if(selectedId==null) {
            ListItem(headlineContent={Text("共享家园")},supportingContent={Text("经营农场、采集钓鱼与居民互动")},leadingContent={Icon(Icons.Default.Yard,null)},trailingContent={Icon(Icons.Default.ChevronRight,null)},modifier=Modifier.clickable {home=true})
            ListItem(headlineContent={Text("约会与小游戏")},supportingContent={Text("一起续写剧情、下五子棋、翻牌配对")},leadingContent={Icon(Icons.Default.SportsEsports,null)},trailingContent={Icon(Icons.Default.ChevronRight,null)},modifier=Modifier.clickable {play=true})
            ListItem(headlineContent={Text("一起看 B 站")},supportingContent={Text("官方播放器、观看进度与感想")},leadingContent={Icon(Icons.Default.OndemandVideo,null)},trailingContent={Icon(Icons.Default.ChevronRight,null)},modifier=Modifier.clickable {bilibili=true})
            ListItem(headlineContent={Text("一起听歌")},supportingContent={Text("本机曲库、听歌进度与感想")},leadingContent={Icon(Icons.Default.Headphones,null)},trailingContent={Icon(Icons.Default.ChevronRight,null)},modifier=Modifier.clickable {music=true})
            HorizontalDivider()
            ListItem(headlineContent={Text("一起看视频")},supportingContent={Text("本机视频、观看进度与感想")},leadingContent={Icon(Icons.Default.Movie,null)},trailingContent={Icon(Icons.Default.ChevronRight,null)},modifier=Modifier.clickable {video=true})
            HorizontalDivider()
            if(activities.isEmpty())Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
                Icon(Icons.AutoMirrored.Filled.MenuBook,null,Modifier.size(48.dp),tint=MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp));Text("留一段一起读书的时间",style=MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp));Text("选一本书，保存这次共读的进度和感想。想聊的时候，再把片段带回聊天。",style=MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(24.dp));Button(enabled=!state.working,onClick={chooseBook=true}){Text("选择一本书")}
            } else LazyColumn(Modifier.weight(1f).fillMaxWidth(),contentPadding=PaddingValues(vertical=12.dp)) {
                items(activities,key={it.id}) {item->
                    val book=state.readingDocuments.firstOrNull {it.id==item.readingDocumentId}
                    ListItem(modifier=Modifier.clickable {selectedId=item.id},
                        leadingContent={Icon(Icons.AutoMirrored.Filled.MenuBook,null,tint=MaterialTheme.colorScheme.primary)},
                        headlineContent={Text(book?.displayName?:"书目不可用",maxLines=2,overflow=TextOverflow.Ellipsis)},
                        supportingContent={Text("${activityStateLabel(item.state)} · 第 ${item.chapter+1} 节 · 字符 ${item.offset}")},
                        trailingContent={Icon(Icons.Default.ChevronRight,null)})
                    HorizontalDivider(Modifier.padding(start=56.dp))
                }
            }
        } else if(activity==null||document==null) {
            Text("活动或书目暂不可用，请返回列表检查。",Modifier.padding(24.dp))
        } else {
            Text(document.displayName,Modifier.padding(horizontal=20.dp,vertical=8.dp),style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal=12.dp),
                horizontalArrangement=Arrangement.spacedBy(4.dp),
                verticalArrangement=Arrangement.spacedBy(2.dp)
            ) {
                Text(activityStateLabel(activity.state),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary,modifier=Modifier.padding(8.dp))
                if(activity.state!="completed") {
                    TextButton(enabled=!state.working,onClick={vm.changeActivity(activity,command=if(activity.state=="active")"pause" else "resume")}){Text(if(activity.state=="active")"暂停" else "继续")}
                    TextButton(enabled=!state.working,onClick={editNote=true}){Text("写感想")}
                    TextButton(enabled=!state.working,onClick={confirmEnd=true}){Text("结束")}
                }
                TextButton(enabled=!state.working,onClick={preview=activity}){Text("带回聊天")}
            }
            var chapterMenu by remember(activity.id){mutableStateOf(false)}
            key(activity.id,activity.chapter,activity.offset) {
                ReadingDocumentPage(document,activity.chapter,activity.offset,null,chapterMenu,false,state.working,
                    onChapterMenu={chapterMenu=it},onDeleteConfirm={},onDelete={},
                    onPosition={chapter,offset->vm.changeActivity(activity,chapter=chapter,offset=offset)},
                    onUse={chapter,start,end->vm.selectActivityPassage(activity,chapter,start,end,onClose)},
                    allowDocumentDelete=false,navigationEnabled=!state.working&&activity.state=="active")
            }
        }
    }
    if(chooseBook)AlertDialog(onDismissRequest={chooseBook=false},title={Text("选一本书，开始共读")},
        text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState())) {
            if(state.readingDocuments.isEmpty())Text("书架还没有书。先导入本机 TXT 或 Markdown 文件。")
            state.readingDocuments.forEach {book->TextButton(enabled=!state.working,onClick={
                vm.createReadingActivity(role.scope(),chat.id,book.id){id->selectedId=id;chooseBook=false}
            },modifier=Modifier.fillMaxWidth()){Text(book.displayName,maxLines=2,overflow=TextOverflow.Ellipsis)}}
        }},confirmButton={TextButton(enabled=!state.working,onClick={chooseBook=false;onImport()}){Text("导入本机书籍")}},dismissButton={TextButton(onClick={chooseBook=false}){Text("取消")}})
    if(editNote&&activity!=null) {
        var note by remember(activity.id,activity.version){mutableStateOf(activity.note)}
        AlertDialog(onDismissRequest={editNote=false},title={Text("这次共读的感想")},text={OutlinedTextField(value=note,onValueChange={if(it.length<=SharedActivities.NOTE_LIMIT)note=it},minLines=4,maxLines=9,label={Text("想记住什么？")},supportingText={Text("${note.length} / ${SharedActivities.NOTE_LIMIT} · 保存不会自动发送")})},
            confirmButton={TextButton(enabled=!state.working,onClick={vm.changeActivity(activity,note=note,onSaved={editNote=false})}){Text("保存")}},dismissButton={TextButton(onClick={editNote=false}){Text("取消")}})
    }
    if(confirmEnd&&activity!=null)AlertDialog(onDismissRequest={confirmEnd=false},title={Text("结束本次共读？")},text={Text("进度和感想会保留，结束后不再修改。以后可以为同一本书发起新的共读。")},
        confirmButton={TextButton(enabled=!state.working,onClick={vm.changeActivity(activity,command="complete",onSaved={confirmEnd=false})}){Text("结束共读")}},dismissButton={TextButton(onClick={confirmEnd=false}){Text("继续读")}})
    if(confirmDelete&&activity!=null)AlertDialog(onDismissRequest={confirmDelete=false},title={Text("删除这次活动？")},text={Text("仅删除本次活动进度与感想，原书籍和已经发送的聊天消息会保留。")},
        confirmButton={TextButton(enabled=!state.working,onClick={vm.deleteActivity(activity){confirmDelete=false;selectedId=null}}){Text("删除")}},dismissButton={TextButton(onClick={confirmDelete=false}){Text("取消")}})
    preview?.let {frozen->
        val book=state.readingDocuments.firstOrNull {it.id==frozen.readingDocumentId}
        if(book!=null) {
            val text=SharedActivities.summary(frozen,book)
            AlertDialog(onDismissRequest={preview=null},title={Text("带回聊天的内容")},text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text(text);Text(if(text.length>draftSpace)"聊天草稿空间不足，请先整理草稿。" else "确认后加入聊天草稿，由你编辑并发送。",style=MaterialTheme.typography.bodySmall)
            }},confirmButton={TextButton(enabled=!state.working&&text.length<=draftSpace,onClick={vm.activitySummaryDraft(frozen,text){onDraft(it);preview=null;onClose()}}){Text("加入草稿")}},dismissButton={TextButton(onClick={preview=null}){Text("取消")}})
        }
    }
}
