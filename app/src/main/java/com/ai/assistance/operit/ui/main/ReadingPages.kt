package com.ai.assistance.operit.ui.main

import com.ai.assistance.operit.ui.main.ThemedIcon as Icon

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.data.model.*

@Composable
internal fun ReadingImportPreviewDialog(state:CompanionState,vm:CompanionViewModel) {
    val document=state.readingImportPreview?:return
    AlertDialog(
        onDismissRequest=vm::dismissReadingImport,
        title={Text("导入伴读书架")},
        text={Column(Modifier.heightIn(max=560.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(document.displayName,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick={},enabled=false,label={Text(if(document.mimeType==TextMaterials.MIME_MARKDOWN)"Markdown" else "TXT")})
                AssistChip(onClick={},enabled=false,label={Text("${document.text.length} 字")})
                AssistChip(onClick={},enabled=false,label={Text("${document.chapters.size} 节")})
            }
            Text("文件会完整保存在当前角色的加密书架中。阅读进度留在本机；只有你在阅读器里明确选择的片段才会进入待发送消息。",style=MaterialTheme.typography.bodyMedium)
            Text("章节预览",style=MaterialTheme.typography.labelLarge)
            document.chapters.take(12).forEach {chapter->
                Text("${chapter.index+1}. ${chapter.title} · 字符 ${chapter.start}–${chapter.end}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(document.chapters.size>12)Text("其余 ${document.chapters.size-12} 节将在导入后显示。",style=MaterialTheme.typography.bodySmall)
            SelectionContainer {Text(document.text.take(1200),Modifier.fillMaxWidth(),style=MaterialTheme.typography.bodySmall)}
            Text("请检查文件名和内容是否含私人信息。文件不会因导入而自动发给模型。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }},
        confirmButton={Button(enabled=!state.working,onClick=vm::confirmReadingImport){Text("确认导入")}},
        dismissButton={TextButton(onClick=vm::dismissReadingImport){Text("取消")}}
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReadingLibraryDialog(role:RoleEntity,chat:ChatEntity,state:CompanionState,vm:CompanionViewModel,initialReference:MessageMaterial?,onImport:()->Unit,onDismiss:()->Unit) {
    val initial=remember(initialReference?.sourceUrl) {initialReference?.takeIf {it.kind==TextMaterials.KIND_READING}?.sourceUrl?.let {runCatching {ReadingDocuments.parseReference(it)}.getOrNull()}}
    var selectedId by remember(initial?.documentId) {mutableStateOf(initial?.documentId)}
    var chapterIndex by remember {mutableIntStateOf(0)}
    var offset by remember {mutableIntStateOf(0)}
    var citationEnd by remember(initial?.end) {mutableStateOf(initial?.end)}
    var chapterMenu by remember {mutableStateOf(false)}
    var deleteConfirm by remember {mutableStateOf(false)}
    val document=state.readingDocuments.firstOrNull {it.id==selectedId}
    LaunchedEffect(selectedId,state.readingDocuments.any {it.id==selectedId}) {
        val chosen=state.readingDocuments.firstOrNull {it.id==selectedId}?:return@LaunchedEffect
        val reference=initial?.takeIf {it.documentId==chosen.id}
        val chapters=chosen.chapters()
        chapterIndex=reference?.let {ref->chapters.indexOfFirst {ref.start>=it.start&&ref.end<=it.end}.takeIf {it>=0}}?:chosen.currentChapter
        offset=reference?.start?:chosen.currentOffset
        citationEnd=reference?.end
    }
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
            Column {
                TopAppBar(
                    title={Column {Text(if(document==null)"伴读书架" else document.displayName,maxLines=1,overflow=TextOverflow.Ellipsis);Text("仅明确选择的片段会发送",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}},
                    navigationIcon={IconButton(onClick={if(document==null)onDismiss()else {selectedId=null;citationEnd=null}}){Icon(Icons.AutoMirrored.Filled.ArrowBack,if(document==null)"关闭" else "返回书架")}},
                    actions={if(document==null)IconButton(enabled=!state.working,onClick=onImport){Icon(Icons.Default.UploadFile,"导入 TXT 或 Markdown")}}
                )
                if(document==null) {
                    if(initial!=null&&state.readingDocuments.none {it.id==initial.documentId})Surface(Modifier.fillMaxWidth().widthIn(max=760.dp).align(Alignment.CenterHorizontally).padding(16.dp),shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.errorContainer) {
                        Text("引用对应的本机原文不可用，无法跳转。已保存的引用片段仍显示在聊天中。",Modifier.padding(16.dp),color=MaterialTheme.colorScheme.onErrorContainer)
                    }
                    if(state.readingDocuments.isEmpty())Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
                        Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.AutoMirrored.Filled.MenuBook,null,Modifier.size(52.dp),tint=MaterialTheme.colorScheme.primary)
                            Text("当前角色的书架为空",style=MaterialTheme.typography.titleMedium)
                            Text("导入 UTF-8 TXT 或 Markdown，章节和进度只保存在本机。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Button(enabled=!state.working,onClick=onImport){Icon(Icons.Default.UploadFile,null);Spacer(Modifier.width(8.dp));Text("导入文件")}
                        }
                    } else LazyColumn(Modifier.weight(1f).fillMaxWidth().widthIn(max=760.dp).align(Alignment.CenterHorizontally),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        items(state.readingDocuments,key={it.id}) {item->
                            val chapters=item.chapters();val current=chapters[item.currentChapter]
                            ElevatedCard(Modifier.fillMaxWidth().clickable {selectedId=item.id;citationEnd=null},shape=RoundedCornerShape(18.dp)) {
                                Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                                    Icon(Icons.AutoMirrored.Filled.MenuBook,null,Modifier.size(36.dp),tint=MaterialTheme.colorScheme.primary)
                                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                        Text(item.displayName,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
                                        Text("${chapters.size} 节 · ${item.text.length} 字",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("上次读到：${current.title} · 字符 ${item.currentOffset}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary,maxLines=1,overflow=TextOverflow.Ellipsis)
                                    }
                                    Icon(Icons.Default.ChevronRight,null)
                                }
                            }
                        }
                    }
                } else ReadingDocumentPage(document,chapterIndex,offset,citationEnd,chapterMenu,deleteConfirm,state.working,
                    onChapterMenu={chapterMenu=it},onDeleteConfirm={deleteConfirm=it},onDelete={vm.deleteReadingDocument(role.scope(),document.id);selectedId=null;deleteConfirm=false},
                    onPosition={chapter,newOffset->chapterIndex=chapter;offset=newOffset;citationEnd=null;vm.updateReadingProgress(role.scope(),document.id,chapter,newOffset)},
                    onUse={chapter,start,end->vm.selectReadingPassage(role.scope(),chat.id,document.id,chapter,start,end);onDismiss()})
            }
        }
    }
}

@Composable
internal fun ColumnScope.ReadingDocumentPage(document:ReadingDocument,chapterIndex:Int,offset:Int,citationEnd:Int?,chapterMenu:Boolean,deleteConfirm:Boolean,working:Boolean,
    onChapterMenu:(Boolean)->Unit,onDeleteConfirm:(Boolean)->Unit,onDelete:()->Unit,onPosition:(Int,Int)->Unit,onUse:(Int,Int,Int)->Unit,allowDocumentDelete:Boolean=true,navigationEnabled:Boolean=true) {
    val chapters=document.chapters();val chapter=chapters[chapterIndex]
    val start=offset.coerceIn(chapter.start,(chapter.end-1).coerceAtLeast(chapter.start))
    val end=citationEnd?.takeIf {it in start+1..chapter.end}?:ReadingDocuments.pageEnd(document.text,start,chapter.end)
    val passage=document.text.substring(start,end)
    Column(Modifier.weight(1f).fillMaxWidth().widthIn(max=760.dp).align(Alignment.CenterHorizontally).padding(horizontal=16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                OutlinedButton(enabled=navigationEnabled,onClick={onChapterMenu(true)},modifier=Modifier.fillMaxWidth()) {Text("${chapter.index+1}/${chapters.size} · ${chapter.title}",maxLines=1,overflow=TextOverflow.Ellipsis);Spacer(Modifier.weight(1f));Icon(Icons.Default.ArrowDropDown,null)}
                DropdownMenu(expanded=chapterMenu,onDismissRequest={onChapterMenu(false)}) {chapters.forEach {item->
                    DropdownMenuItem(text={Text("${item.index+1}. ${item.title}",maxLines=2)},onClick={onChapterMenu(false);onPosition(item.index,item.start)})
                }}
            }
            if(allowDocumentDelete)IconButton(onClick={onDeleteConfirm(true)}){Icon(Icons.Default.DeleteOutline,"删除未引用文件")}
        }
        LinearProgressIndicator(progress={if(document.text.isEmpty())0f else start.toFloat()/document.text.length},modifier=Modifier.fillMaxWidth())
        Text("字符 $start–$end · 本页 ${passage.length} 字",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(Modifier.weight(1f).fillMaxWidth(),shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surface) {
            SelectionContainer {Text(passage,Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp),style=MaterialTheme.typography.bodyLarge)}
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
            OutlinedButton(enabled=navigationEnabled&&(start>chapter.start||chapterIndex>0),onClick={if(start>chapter.start)onPosition(chapterIndex,ReadingDocuments.previousPageStart(document.text,start,chapter.start))else {val previous=chapters[chapterIndex-1];onPosition(previous.index,previous.start)}}){Icon(Icons.Default.ChevronLeft,null);Text(if(start>chapter.start)"上一页" else "上一节")}
            OutlinedButton(enabled=navigationEnabled&&(end<chapter.end||chapterIndex<chapters.lastIndex),onClick={if(end<chapter.end)onPosition(chapterIndex,end)else {val next=chapters[chapterIndex+1];onPosition(next.index,next.start)}}){Text(if(end<chapter.end)"下一页" else "下一节");Icon(Icons.Default.ChevronRight,null)}
        }
        Button(enabled=!working&&passage.isNotBlank(),onClick={onUse(chapterIndex,start,end)},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.FormatQuote,null);Spacer(Modifier.width(6.dp));Text("引用当前片段")}
        Text("引用后会先显示在聊天输入区；发送时只上传本页文字及可追溯位置，不上传整份文件。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(bottom=12.dp))
    }
    if(deleteConfirm)AlertDialog(onDismissRequest={onDeleteConfirm(false)},title={Text("删除伴读文件？")},text={Text("只允许删除尚未被聊天引用的文件。已引用文件会被保留，以便从消息跳回原文位置。")},confirmButton={TextButton(enabled=!working,onClick=onDelete){Text("删除")}},dismissButton={TextButton(onClick={onDeleteConfirm(false)}){Text("取消")}})
}
