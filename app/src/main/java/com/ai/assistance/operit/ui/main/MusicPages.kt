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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.*

internal val LocalMusicFilePick=staticCompositionLocalOf<(RoleAccessScope,MusicTrack?,String)->Unit>{error("Missing music picker")}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ColumnScope.MusicPage(role:RoleEntity,chat:ChatEntity,state:CompanionState,vm:CompanionViewModel,
    draftSpace:Int,onDraft:(String)->Unit,onClose:()->Unit,kind:String="audio",initialSessionId:String?=null) {
    val pick=LocalMusicFilePick.current
    val video=kind=="video"
    val noun=if(video)"视频" else "音乐"
    val libraryName=if(video)"本机视频" else "本机曲库"
    val tracks=state.musicTracks.filter {it.mediaKind==kind}
    var surface by remember {mutableStateOf<android.view.Surface?>(null)}
    var selectedId by rememberSaveable(chat.id,kind,initialSessionId){mutableStateOf(initialSessionId)}
    var library by rememberSaveable(chat.id){mutableStateOf(false)}
    var deleteSession by remember {mutableStateOf<MusicSession?>(null)}
    var deleteTrack by remember {mutableStateOf<MusicTrack?>(null)}
    var noteDialog by remember {mutableStateOf<MusicSession?>(null)}
    var summary by remember {mutableStateOf<Pair<MusicSession,String>?>(null)}
    val sessions=state.musicSessions.filter {it.chatId==chat.id&&it.roleId==role.id&&it.spaceId==role.spaceId&&tracks.any {track->track.id==it.trackId}}
    val session=sessions.firstOrNull {it.id==selectedId}
    val track=tracks.firstOrNull {it.id==session?.trackId}
    val back={vm.stopMusic();if(library)library=false else if(selectedId!=null)selectedId=null else onClose()}
    BackHandler(onBack=back)
    DisposableEffect(chat.id){onDispose{vm.stopMusic()}}
    Column(Modifier.weight(1f).fillMaxWidth()) {
        TopAppBar(title={Column {
            Text(if(library)libraryName else if(video)"一起看视频" else "一起听歌")
            Text("和 ${role.name}",style=MaterialTheme.typography.labelMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
        }},navigationIcon={IconButton(onClick=back){Icon(Icons.AutoMirrored.Filled.ArrowBack,"返回")}},actions={
            if(library)IconButton(enabled=!state.working,onClick={pick(role.scope(),null,kind)}){Icon(Icons.Default.Add,"添加本机$noun")}
            else if(session==null)TextButton(onClick={library=true}){Text(if(video)"选择视频" else "选一首歌")}
        })
        if(library) {
            Text("$noun 保留在原文件中，进度和感想仅本机保存。每个文件不超过 ${LocalMusic.maxBytes(kind)/1024/1024} MiB。",Modifier.padding(20.dp),style=MaterialTheme.typography.bodyMedium)
            if(tracks.isEmpty())Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                Icon(if(video)Icons.Default.VideoLibrary else Icons.Default.LibraryMusic,null,Modifier.size(48.dp),tint=MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp));Text(if(video)"把想看的视频带进来" else "把喜欢的歌带进来",style=MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(20.dp));Button(enabled=!state.working,onClick={pick(role.scope(),null,kind)}){Text("选择本机$noun")}
            }else LazyColumn(Modifier.weight(1f)) {
                items(tracks,key={it.id}) {t->
                    ListItem(headlineContent={Text(t.displayName,maxLines=2,overflow=TextOverflow.Ellipsis)},
                        supportingContent={Text("${LocalMusic.time(t.durationMs)} · ${if(t.sourceUri==null)"需重新选择原文件" else "本机$noun"}")},
                        leadingContent={Icon(if(video)Icons.Default.Movie else Icons.Default.MusicNote,null)},trailingContent={IconButton(enabled=!state.working,onClick={deleteTrack=t}){Icon(Icons.Default.DeleteOutline,"移出媒体库")}},
                        modifier=Modifier.clickable(enabled=!state.working){vm.createMusic(role.scope(),chat.id,t.id){selectedId=it;library=false}})
                    HorizontalDivider(Modifier.padding(start=56.dp))
                }
            }
        }else if(selectedId==null) {
            if(sessions.isEmpty())Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                Icon(if(video)Icons.Default.Movie else Icons.Default.Headphones,null,Modifier.size(56.dp),tint=MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(20.dp));Text(if(video)"留一段一起看的时间" else "这一首，想和你一起听",style=MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(10.dp));Text("播放你收藏的本机$noun，留下此刻的感想，再带回聊天。")
                Spacer(Modifier.height(24.dp));Button(onClick={library=true}){Text("打开$libraryName")}
            }else LazyColumn(Modifier.weight(1f)) {
                items(sessions,key={it.id}) {s->
                    val t=tracks.firstOrNull {it.id==s.trackId}
                    ListItem(headlineContent={Text(requireNotNull(t).displayName,maxLines=2,overflow=TextOverflow.Ellipsis)},
                        supportingContent={Text("${LocalMusic.time(s.positionMs)} · ${if(s.state=="completed")if(video)"已看完" else "已听完" else "已暂停"}")},
                        leadingContent={Icon(if(video)Icons.Default.Movie else Icons.Default.Headphones,null)},trailingContent={Icon(Icons.Default.ChevronRight,null)},modifier=Modifier.clickable {selectedId=s.id})
                    HorizontalDivider(Modifier.padding(start=56.dp))
                }
            }
        }else if(session==null||track==null)Text("活动记录不可用，请返回列表检查。",Modifier.padding(24.dp))
        else Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            val playing=state.playingMusicId==session.id||session.state=="playing"
            if(video)key(session.id) {
                VideoSurface(requireNotNull(track.videoWidth),requireNotNull(track.videoHeight),onSurface={surface=it},onClosed={old->
                    if(surface===old){vm.stopMusic();surface=null}
                })
            } else {Spacer(Modifier.height(24.dp));Icon(Icons.Default.GraphicEq,null,Modifier.size(88.dp),tint=MaterialTheme.colorScheme.primary)}
            Spacer(Modifier.height(24.dp));Text(track.displayName,style=MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(10.dp));Text(when {playing->if(video)"正在观看 · 离开页面会暂停" else "正在听 · 离开页面会暂停";session.state=="completed"->"已播放完，可拖动进度重新播放";else->"已暂停"},style=MaterialTheme.typography.bodyMedium)
            var seek by remember(session.id,session.version,session.positionMs){mutableFloatStateOf(session.positionMs.toFloat())}
            Spacer(Modifier.height(28.dp))
            Slider(value=seek,onValueChange={seek=it},onValueChangeFinished={vm.changeMusic(session,position=seek.toLong())},
                valueRange=0f..track.durationMs.toFloat(),enabled=!playing&&!state.working,
                modifier=Modifier.fillMaxWidth().semantics {contentDescription="播放进度，暂停后可调整"})
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(LocalMusic.time(seek.toLong()));Text(LocalMusic.time(track.durationMs))}
            Spacer(Modifier.height(20.dp))
            if(track.sourceUri==null) {
                Text("恢复的记录需要重新选择原$noun 文件。",style=MaterialTheme.typography.bodyMedium)
                Button(enabled=!state.working,onClick={pick(role.scope(),track,kind)}){Text("重新选择原文件")}
            } else Button(enabled=!state.working&&(playing||session.state=="paused")&&(!video||surface!=null),onClick={if(playing)vm.stopMusic()else vm.playMusic(session,surface)},modifier=Modifier.heightIn(min=56.dp).fillMaxWidth()) {
                Icon(if(playing)Icons.Default.Pause else Icons.Default.PlayArrow,null);Spacer(Modifier.width(8.dp));Text(if(playing)"暂停" else "播放")
            }
            TextButton(enabled=!playing&&!state.working,onClick={pick(role.scope(),track,kind)}){Text("重新关联$noun 文件")}
            HorizontalDivider(Modifier.padding(vertical=20.dp))
            Text(session.note.ifBlank {"还没有留下感想。"},Modifier.fillMaxWidth(),style=MaterialTheme.typography.bodyLarge)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                TextButton(enabled=!playing&&!state.working,onClick={noteDialog=session}){Text("写感想")}
                TextButton(enabled=!playing&&!state.working,onClick={summary=session to LocalMusic.summary(session,track)}){Text("带回聊天")}
            }
            Text("只有你确认的名称、进度和感想会加入聊天草稿。原文件不会发送给 AI。",style=MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(24.dp));TextButton(enabled=!playing&&!state.working,onClick={deleteSession=session}){Text("删除本次活动记录")}
        }
    }
    noteDialog?.let {frozen->
        var note by remember(frozen.id){mutableStateOf(frozen.note)}
        AlertDialog(onDismissRequest={noteDialog=null},title={Text("此刻的感想")},text={OutlinedTextField(value=note,onValueChange={if(it.length<=SharedActivities.NOTE_LIMIT)note=it},minLines=4,maxLines=8,label={Text("想记住什么？")},supportingText={Text("${note.length} / ${SharedActivities.NOTE_LIMIT}")})},
            confirmButton={TextButton(enabled=!state.working,onClick={vm.changeMusic(frozen,note=note,onSaved={noteDialog=null})}){Text("保存")}},dismissButton={TextButton(onClick={noteDialog=null}){Text("取消")}})
    }
    summary?.let {(frozen,text)->AlertDialog(onDismissRequest={summary=null},title={Text("带回聊天的内容")},text={Column(Modifier.heightIn(max=400.dp).verticalScroll(rememberScrollState())){Text(text);Spacer(Modifier.height(12.dp));Text(if(text.length>draftSpace)"草稿空间不足，请先整理草稿。" else "确认后放入草稿，由你编辑并发送。")}},
        confirmButton={TextButton(enabled=!state.working&&text.length<=draftSpace,onClick={vm.musicSummary(frozen,text){onDraft(it);summary=null;onClose()}}){Text("加入草稿")}},dismissButton={TextButton(onClick={summary=null}){Text("取消")}})}
    deleteSession?.let {s->AlertDialog(onDismissRequest={deleteSession=null},title={Text("删除本次活动记录？")},text={Text("删除这次进度和感想，原文件、媒体库和已发送的聊天内容会保留。")},confirmButton={TextButton(enabled=!state.working,onClick={vm.deleteMusic(s){deleteSession=null;selectedId=null}}){Text("删除")}},dismissButton={TextButton(onClick={deleteSession=null}){Text("取消")}})}
    deleteTrack?.let {t->AlertDialog(onDismissRequest={deleteTrack=null},title={Text("从媒体库移除？")},text={Text("原文件不会被删除。仍有关联活动记录时，需要先删除这些记录。")},confirmButton={TextButton(enabled=!state.working,onClick={vm.deleteMusicTrack(t){deleteTrack=null}}){Text("移除")}},dismissButton={TextButton(onClick={deleteTrack=null}){Text("取消")}})}
}

@Composable internal fun MusicImportDialog(state:CompanionState,vm:CompanionViewModel) {
    val preview=state.musicImport?:return
    AlertDialog(onDismissRequest=vm::dismissMusicImport,title={Text(if(preview.relink==null)"加入本机媒体库" else "重新关联原文件")},
        text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(preview.track.displayName,style=MaterialTheme.typography.titleMedium)
            Text("${LocalMusic.time(preview.track.durationMs)} · ${preview.track.byteCount/1024} KiB")
            Text("文件保留在原位置，应用只保留读取授权。移动或删除后需要重新关联。换设备恢复备份后也需要重新选择原文件。")
            Text("文件不会发送给 AI。")
        }},confirmButton={TextButton(enabled=!state.working,onClick=vm::confirmMusicImport){Text("确认")}},dismissButton={TextButton(onClick=vm::dismissMusicImport){Text("取消")}})
}
