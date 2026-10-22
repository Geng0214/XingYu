package com.ai.assistance.operit.ui.main

import com.ai.assistance.operit.ui.main.ThemedIcon as Icon

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.api.chat.MemoryCandidate
import com.ai.assistance.operit.data.model.Memories

private data class CandidateDraft(val selected:Boolean,val kind:String,val title:String,val content:String)

@Composable internal fun MemoryExtractionDialog(state:CompanionState,vm:CompanionViewModel) {
    val ui=state.memoryExtraction?:return
    Dialog(onDismissRequest={vm.dismissMemoryExtraction()},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.statusBarsPadding().navigationBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
                    IconButton(onClick=vm::dismissMemoryExtraction,enabled=ui.stage!="GENERATING"){Icon(Icons.AutoMirrored.Filled.ArrowBack,"关闭")}
                    Column(Modifier.weight(1f)) {
                        Text(when(ui.stage){"PREVIEW"->"确认记忆候选";"GENERATING"->"正在提炼";else->"提炼这段对话"},style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
                        Text("只处理你选择的这一轮消息",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                when(ui.stage) {
                    "GENERATING"->GeneratingMemoryExtraction(vm)
                    "PREVIEW"->MemoryCandidatePreview(ui,state,vm)
                    else->MemoryExtractionConfirmation(ui,state,vm)
                }
            }
        }
    }
}

@Composable private fun ColumnScope.MemoryExtractionConfirmation(ui:MemoryExtractionUi,state:CompanionState,vm:CompanionViewModel) {
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=24.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
        Surface(shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)){Icon(Icons.Default.AutoAwesome,null);Text("发送前确认",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)}
                Text("应用会向当前模型发起一次独立请求，可能产生服务商费用。它不会调用工具，也不会发送其他历史、图片、资料、世界书、关系或已有记忆。")
            }
        }
        Text("模型与目的地",style=MaterialTheme.typography.titleMedium)
        SelectionContainer {Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text("${ui.frozen.config.name} · ${ui.frozen.config.model}",fontWeight=FontWeight.SemiBold)
            Text(ui.frozen.config.endpoint,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }}
        SourceMessage("你的消息",ui.frozen.source.userText)
        SourceMessage("AI 回复",ui.frozen.source.assistantText)
        Text("模型只生成候选，不会自动写入。返回后你可以修改、取消勾选，再明确保存。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick=vm::dismissMemoryExtraction,enabled=!state.working,modifier=Modifier.weight(1f)){Text("取消")}
        Button(onClick=vm::generateMemoryCandidates,enabled=!state.working,modifier=Modifier.weight(1f)){Text("发送并生成候选")}
    }
}

@Composable private fun ColumnScope.GeneratingMemoryExtraction(vm:CompanionViewModel) {
    Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
        Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(18.dp)) {
            CircularProgressIndicator()
            Text("正在等待模型返回严格的记忆候选…",style=MaterialTheme.typography.titleMedium)
            Text("停止后不会保存候选。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick=vm::cancelMemoryExtraction){Text("停止提炼")}
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun ColumnScope.MemoryCandidatePreview(ui:MemoryExtractionUi,state:CompanionState,vm:CompanionViewModel) {
    val drafts=remember(ui.frozen.id,ui.candidates){ui.candidates.map {CandidateDraft(true,it.kind,it.title,it.content)}.toMutableStateList()}
    val selected=drafts.filter {it.selected}
    val valid=selected.isNotEmpty()&&selected.all {runCatching {Memories.validate(it.kind,it.title,it.content,50,"extracted")}.isSuccess}
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Surface(shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.secondaryContainer) {
            Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text("尚未保存",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                Text("逐条核对事实、现实与虚构分类。只有勾选并确认的内容会进入当前角色的长期记忆。")
            }
        }
        drafts.forEachIndexed {index,draft->CandidateEditor(index,draft,drafts)}
        Text("保存后会记录来源消息、模型和配置版本；删除来源对话时，你需要决定断开来源还是一起删除提炼记忆。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick=vm::dismissMemoryExtraction,enabled=!state.working,modifier=Modifier.weight(1f)){Text("全部放弃")}
        Button(onClick={vm.confirmMemoryCandidates(selected.map {MemoryCandidate(it.kind,it.title.trim(),it.content)})},enabled=!state.working&&valid,modifier=Modifier.weight(1f)){Text("保存 ${selected.size} 条")}
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun CandidateEditor(index:Int,draft:CandidateDraft,drafts:SnapshotStateList<CandidateDraft>) {
    Surface(shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.55f)) {
        Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Checkbox(draft.selected,{drafts[index]=draft.copy(selected=it)})
                Text("候选 ${index+1}",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                Text(if(draft.selected)"将保存"else"不保存",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Memories.KINDS.forEach {kind->FilterChip(selected=draft.kind==kind,onClick={drafts[index]=draft.copy(kind=kind)},label={Text(Memories.label(kind))})}
            }
            OutlinedTextField(draft.title,{if(it.length<=Memories.TITLE_LIMIT)drafts[index]=draft.copy(title=it)},Modifier.fillMaxWidth(),label={Text("标题")},singleLine=true)
            OutlinedTextField(draft.content,{if(it.length<=Memories.CONTENT_LIMIT)drafts[index]=draft.copy(content=it)},Modifier.fillMaxWidth(),label={Text("内容")},minLines=3,maxLines=8,
                supportingText={Text("${draft.content.length}/${Memories.CONTENT_LIMIT}")})
        }
    }
}

@Composable private fun SourceMessage(label:String,text:String) {
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(label,style=MaterialTheme.typography.titleMedium)
        Surface(shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.55f)) {
            SelectionContainer {Text(text,Modifier.fillMaxWidth().padding(16.dp),maxLines=12,overflow=TextOverflow.Ellipsis)}
        }
    }
}
