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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ai.assistance.operit.data.model.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun ColumnScope.BilibiliPage(role:RoleEntity,chat:ChatEntity,state:CompanionState,vm:CompanionViewModel,
    draftSpace:Int,onDraft:(String)->Unit,onClose:()->Unit,initialWatchId:String?=null) {
    var selected by rememberSaveable(chat.id,initialWatchId){mutableStateOf(initialWatchId)}
    var create by remember {mutableStateOf(false)}
    var consent by remember {mutableStateOf<BilibiliWatch?>(null)}
    var edit by remember {mutableStateOf<BilibiliWatch?>(null)}
    var delete by remember {mutableStateOf<BilibiliWatch?>(null)}
    var summary by remember {mutableStateOf<Pair<BilibiliWatch,String>?>(null)}
    val watches=state.bilibiliWatches.filter {it.scope()==role.scope()&&it.chatId==chat.id}
    val item=watches.firstOrNull {it.id==selected}
    val playback=state.bilibiliPlayback?.takeIf {it.watch.id==selected&&it.watch.scope()==role.scope()&&it.watch.chatId==chat.id}
    val back={vm.stopMusic();if(selected==null)onClose()else selected=null}
    BackHandler(onBack=back)
    val latestSelected by rememberUpdatedState(selected)
    DisposableEffect(chat.id){onDispose{latestSelected?.let(vm::stopBilibili)}}
    Column(Modifier.weight(1f).fillMaxWidth()) {
        TopAppBar(title={Column {Text("一起看 B 站");Text("和 ${role.name}",style=MaterialTheme.typography.labelMedium,maxLines=1,overflow=TextOverflow.Ellipsis)}},
            navigationIcon={IconButton(onClick=back){Icon(Icons.AutoMirrored.Filled.ArrowBack,"返回")}},
            actions={if(selected==null)IconButton(enabled=!state.working,onClick={create=true}){Icon(Icons.Default.Add,"添加观看记录")}})
        if(selected==null) {
            if(watches.isEmpty())Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                Icon(Icons.Default.OndemandVideo,null,Modifier.size(56.dp),tint=MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(20.dp));Text("这段视频，想和你一起看",style=MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp));Text("添加 B 站视频链接，记录看到哪里，再把感想带回聊天。")
                Spacer(Modifier.height(24.dp));Button(onClick={create=true}){Text("添加视频链接")}
            } else LazyColumn(Modifier.weight(1f)) {
                items(watches,key={it.id}) {w->
                    ListItem(headlineContent={Text(w.title,maxLines=2,overflow=TextOverflow.Ellipsis)},supportingContent={Text("第 ${w.part} 集 · ${LocalMusic.time(w.positionMs)}")},
                        leadingContent={Icon(Icons.Default.OndemandVideo,null)},trailingContent={Icon(Icons.Default.ChevronRight,null)},modifier=Modifier.clickable {selected=w.id})
                    HorizontalDivider(Modifier.padding(start=56.dp))
                }
            }
        }else if(item==null)Text("观看记录不可用，请返回列表检查。",Modifier.padding(24.dp))
        else Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            if(playback!=null)key(playback.player) {
                AndroidView(factory={playback.player.view},modifier=Modifier.fillMaxWidth().aspectRatio(16f/9f),onRelease={vm.stopBilibili(playback.player);playback.player.close()})
                BilibiliFullscreen(playback.player)
            }
            Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                Text(item.title,style=MaterialTheme.typography.headlineSmall)
                Text("${item.videoId} · 第 ${item.part} 集",style=MaterialTheme.typography.bodyMedium)
                Text("上次记录 ${LocalMusic.time(item.positionMs)}"+(item.durationMs?.let {" / ${LocalMusic.time(it)}"}?:""),style=MaterialTheme.typography.titleMedium)
                if(item.playbackToken!=null||state.playingMusicId==item.id) {
                    Text("在播放器中播放、暂停或调整进度。离开页面会停止。",style=MaterialTheme.typography.bodyMedium)
                    Button(onClick=vm::stopMusic,modifier=Modifier.fillMaxWidth()){Text("停止并保存进度")}
                }else Button(enabled=!state.working&&state.playingMusicId==null,onClick={consent=item},modifier=Modifier.fillMaxWidth()){Text("打开官方播放器")}
                HorizontalDivider()
                Text(item.note.ifBlank {"还没有留下观看感想。"},style=MaterialTheme.typography.bodyLarge)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    TextButton(enabled=item.playbackToken==null&&!state.working,onClick={edit=item}){Text("写感想")}
                    TextButton(enabled=item.playbackToken==null&&!state.working,onClick={summary=item to BilibiliWatching.summary(item)}){Text("带回聊天")}
                }
                Text("视频画面不会发送给 AI。只有你确认的作品名称、链接、进度和感想会加入草稿。",style=MaterialTheme.typography.bodySmall)
                TextButton(enabled=item.playbackToken==null&&!state.working,onClick={delete=item}){Text("删除观看记录")}
            }
        }
    }
    if(create) {
        var title by remember {mutableStateOf("")};var link by remember {mutableStateOf("")}
        AlertDialog(onDismissRequest={create=false},title={Text("添加 B 站视频")},text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(title,{if(it.length<=160)title=it},label={Text("这次观看的名称")},singleLine=true)
            OutlinedTextField(link,{if(it.length<=2048)link=it},label={Text("完整视频链接或 BV / av 号")},maxLines=3)
            Text("支持公开视频与分集链接。先在本机保存记录，打开播放器时再连接 B 站；短链接请先换成完整视频链接。")
        }},confirmButton={TextButton(enabled=!state.working&&title.isNotBlank()&&link.isNotBlank(),onClick={vm.createBilibili(role.scope(),chat.id,title,link){selected=it;create=false}}){Text("保存")}},dismissButton={TextButton(onClick={create=false}){Text("取消")}})
    }
    consent?.let {w->AlertDialog(onDismissRequest={consent=null},title={Text("连接 B 站官方播放器")},text={Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(w.video().pageUrl());Text("B 站及其内容分发服务会收到你的视频选择、起播进度和网络地址。播放器以访客身份打开，无法播放的视频会显示失败。")
        Text("不会提供聊天、人设、感想或 API Key。系统网页组件已关闭 Cookie、网页缓存与 DOM 存储，但不承诺系统浏览器组件没有任何残留。")
    }},confirmButton={TextButton(enabled=!state.working,onClick={consent=null;vm.playBilibili(w)}){Text("连接并打开")}},dismissButton={TextButton(onClick={consent=null}){Text("取消")}})}
    edit?.let {w->var note by remember(w.id){mutableStateOf(w.note)}
        AlertDialog(onDismissRequest={edit=null},title={Text("观看感想")},text={OutlinedTextField(note,{if(it.length<=SharedActivities.NOTE_LIMIT)note=it},minLines=4,maxLines=8)},confirmButton={TextButton(enabled=!state.working,onClick={vm.noteBilibili(w,note){edit=null}}){Text("保存")}},dismissButton={TextButton(onClick={edit=null}){Text("取消")}})
    }
    summary?.let {(w,text)->AlertDialog(onDismissRequest={summary=null},title={Text("带回聊天的内容")},text={Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState())){Text(text);Spacer(Modifier.height(12.dp));Text(if(text.length<=draftSpace)"确认后加入草稿，由你编辑并发送。" else "草稿空间不足，请先整理草稿。")}},
        confirmButton={TextButton(enabled=!state.working&&text.length<=draftSpace,onClick={vm.summaryBilibili(w,text){summary=null;onDraft(it);onClose()}}){Text("加入草稿")}},dismissButton={TextButton(onClick={summary=null}){Text("取消")}})}
    delete?.let {w->AlertDialog(onDismissRequest={delete=null},title={Text("删除观看记录？")},text={Text("本次进度和感想将删除；已发送的聊天消息会保留。")},confirmButton={TextButton(enabled=!state.working,onClick={vm.deleteBilibili(w){delete=null;selected=null}}){Text("删除")}},dismissButton={TextButton(onClick={delete=null}){Text("取消")}})}
}

@Composable private fun BilibiliFullscreen(player:com.ai.assistance.operit.api.audio.BilibiliPlayer) {
    val full by player.fullscreen.collectAsState()
    full?.let {view->
        androidx.compose.ui.window.Dialog(onDismissRequest=player::exitFullscreen,properties=androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false,securePolicy=androidx.compose.ui.window.SecureFlagPolicy.SecureOn)) {
            Box(Modifier.fillMaxSize()) {
                AndroidView(factory={view},modifier=Modifier.fillMaxSize())
                FilledIconButton(onClick=player::exitFullscreen,modifier=Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(12.dp)){Icon(Icons.Default.Close,"退出全屏")}
            }
        }
    }
}
