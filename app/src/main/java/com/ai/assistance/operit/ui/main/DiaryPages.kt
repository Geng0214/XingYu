package com.ai.assistance.operit.ui.main

import com.ai.assistance.operit.ui.main.ThemedIcon as Icon

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ai.assistance.operit.api.chat.DiaryDraft
import com.ai.assistance.operit.data.model.*

@Composable private fun DiarySurface(title:String,subtitle:String,onBack:()->Unit,error:String?=null,content:@Composable ColumnScope.()->Unit) {
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
@Composable internal fun DiaryDialog(role:RoleEntity,state:CompanionState,vm:CompanionViewModel,onDismiss:()->Unit) {
    val scope=role.scope();val data by remember(scope){vm.diaries(scope)}.collectAsStateWithLifecycle<DiaryData?>(null)
    var notebook by remember(scope){mutableStateOf("heart")}
    var creating by remember(scope){mutableStateOf(false)}
    var editing by remember(scope){mutableStateOf<DiaryEntry?>(null)}
    var deleting by remember(scope){mutableStateOf<DiaryEntry?>(null)}
    var generationSetup by remember(scope){mutableStateOf(false)}
    DiarySurface("日记与心事簿",role.name,{if(!state.working)onDismiss()},state.diaryError) {
        val loaded=data
        when {
            loaded==null->Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
            loaded.error!=null->Text(loaded.error,Modifier.padding(24.dp),color=MaterialTheme.colorScheme.error)
            else->LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(horizontal=24.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                item {Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=MaterialTheme.shapes.large) {
                    Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.AutoMirrored.Filled.MenuBook,null);Spacer(Modifier.width(10.dp));Text("只在你打开时查看",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)}
                        Text("这里的内容不会自动进入普通聊天。模型生成的条目会一直标明来源，也不代表模型拥有真实秘密或内部意识。")
                    }
                }}
                item {FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Diaries.NOTEBOOKS.forEach {value->FilterChip(selected=notebook==value,onClick={notebook=value},label={Text(Diaries.notebookLabel(value))})}
                }}
                item {FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Button(onClick={creating=true},enabled=!state.working&&loaded.entries.size<Diaries.MAX_ENTRIES){Icon(Icons.Default.Add,null);Spacer(Modifier.width(6.dp));Text("手工新建")}
                    OutlinedButton(onClick={generationSetup=true},enabled=!state.working&&state.configs.isNotEmpty()&&loaded.entries.size<Diaries.MAX_ENTRIES){Icon(Icons.Default.AutoAwesome,null);Spacer(Modifier.width(6.dp));Text("AI 写草稿")}
                }}
                val visible=loaded.entries.filter {it.notebook==notebook}
                item {Text("${visible.size} 篇 · 共 ${loaded.entries.size}/${Diaries.MAX_ENTRIES}",style=MaterialTheme.typography.titleMedium)}
                if(visible.isEmpty())item {Text(if(notebook=="heart")"还没有心事记录。可以自己写，也可以先明确主题，让模型生成一份待确认草稿。" else "还没有角色日记。模型生成内容会标注为虚构角色视角，并在保存前交给你核对。")}
                items(visible,key={it.id}) {entry->Surface(shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.55f)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment=Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Text("${Diaries.formatDate(entry.occurredAt)} · ${Diaries.moodLabel(entry.mood)}",style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                                Text(entry.title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
                                Text(Diaries.sourceLabel(entry.source)+(entry.sourceModel?.let {" · $it"}?:""),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
                            }
                        }
                        Text(entry.content,maxLines=6,overflow=TextOverflow.Ellipsis)
                        Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick={editing=entry},enabled=!state.working){Text("查看与编辑")}
                            TextButton(onClick={vm.previewDiaryExport(scope,entry)},enabled=!state.working){Text("导出")}
                            TextButton(onClick={deleting=entry},enabled=!state.working){Text("删除",color=MaterialTheme.colorScheme.error)}
                        }
                    }
                }}
                item {Text("复制角色和公开角色模板不包含日记。个人加密备份会保存日记及其来源标记。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            }
        }
    }
    if(creating||editing!=null)DiaryEditor(role,editing,state,vm){creating=false;editing=null}
    if(generationSetup)DiaryGenerationSetup(role,notebook,state,vm){generationSetup=false}
    deleting?.let {entry->AlertDialog(onDismissRequest={deleting=null},title={Text("删除“${entry.title}”？")},text={Text("删除后无法撤销，导出的文件和已有聊天不会变化。")},
        confirmButton={TextButton(enabled=!state.working,onClick={vm.deleteDiary(scope,entry);deleting=null}){Text("删除")}},dismissButton={TextButton(onClick={deleting=null}){Text("取消")}})}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun DiaryEditor(role:RoleEntity,initial:DiaryEntry?,state:CompanionState,vm:CompanionViewModel,onDismiss:()->Unit) {
    var notebook by remember(initial?.id){mutableStateOf(initial?.notebook?:"heart")}
    var title by remember(initial?.id){mutableStateOf(initial?.title?:"")}
    var content by remember(initial?.id){mutableStateOf(initial?.content?:"")}
    var mood by remember(initial?.id){mutableStateOf(initial?.mood?:"calm")}
    var date by remember(initial?.id){mutableStateOf(Diaries.formatDate(initial?.occurredAt?:Diaries.today()))}
    val occurredAt=runCatching {Diaries.parseDate(date)}.getOrNull()
    val valid=occurredAt!=null&&runCatching {Diaries.validate(notebook,title,content,mood,occurredAt)}.isSuccess
    DiarySurface(if(initial==null)"新建记录" else "编辑记录",role.name,{if(!state.working)onDismiss()},state.diaryError) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {Diaries.NOTEBOOKS.forEach {value->FilterChip(notebook==value,{notebook=value},label={Text(Diaries.notebookLabel(value))},enabled=!state.working)}}
            OutlinedTextField(title,{if(it.length<=Diaries.TITLE_LIMIT)title=it},Modifier.fillMaxWidth(),label={Text("标题")},singleLine=true,enabled=!state.working,supportingText={Text("${title.length}/${Diaries.TITLE_LIMIT}")})
            OutlinedTextField(content,{if(it.length<=Diaries.CONTENT_LIMIT)content=it},Modifier.fillMaxWidth(),label={Text("完整正文")},minLines=8,maxLines=18,enabled=!state.working,supportingText={Text("${content.length}/${Diaries.CONTENT_LIMIT}")})
            Text("心情",style=MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {Diaries.MOODS.forEach {value->FilterChip(mood==value,{mood=value},label={Text(Diaries.moodLabel(value))},enabled=!state.working)}}
            OutlinedTextField(date,{if(it.length<=10)date=it},Modifier.fillMaxWidth(),label={Text("记录日期")},singleLine=true,enabled=!state.working,supportingText={Text("格式：YYYY-MM-DD")},isError=occurredAt==null)
            initial?.let {Text("来源：${Diaries.sourceLabel(it.source)}${it.sourceModel?.let {model->" · $model"}?:""}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            if(initial?.generated()==true)Text("你可以修改模型草稿。来源标记会保留，用来区分用户原写内容和生成内容。",style=MaterialTheme.typography.bodySmall)
            Text("普通聊天不会自动读取这篇内容。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Button(onClick={vm.saveDiary(role.scope(),initial,notebook,title,content,mood,requireNotNull(occurredAt),onDismiss)},enabled=valid&&!state.working,modifier=Modifier.fillMaxWidth().padding(20.dp).heightIn(min=48.dp)){Text("保存记录")}
    }
}

@Composable private fun DiaryGenerationSetup(role:RoleEntity,notebook:String,state:CompanionState,vm:CompanionViewModel,onDismiss:()->Unit) {
    var configId by remember(role.id){mutableStateOf(role.defaultConfigId?.takeIf {id->state.configs.any {it.id==id}}?:state.configs.firstOrNull()?.id)}
    var prompt by remember(role.id,notebook){mutableStateOf("")}
    val valid=configId!=null&&runCatching {Diaries.validatePrompt(prompt)}.isSuccess
    DiarySurface("生成待确认草稿",Diaries.notebookLabel(notebook),{if(!state.working)onDismiss()},state.diaryError) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=MaterialTheme.shapes.large) {Text("这里填写的主题会在下一步逐字预览。只有你再次确认后才发送给所选模型，模型返回内容仍需编辑并保存。",Modifier.padding(16.dp))}
            Text("模型连接",style=MaterialTheme.typography.titleMedium)
            state.configs.forEach {config->Row(Modifier.fillMaxWidth().clickable(enabled=!state.working){configId=config.id},verticalAlignment=Alignment.CenterVertically) {RadioButton(configId==config.id,{configId=config.id},enabled=!state.working);Column {Text(config.name);Text(config.model,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}
            OutlinedTextField(prompt,{if(it.length<=Diaries.PROMPT_LIMIT)prompt=it},Modifier.fillMaxWidth(),label={Text("写作主题和必须遵守的事实")},minLines=6,maxLines=14,enabled=!state.working,supportingText={Text("${prompt.length}/${Diaries.PROMPT_LIMIT} · 不会自动附带普通聊天或长期记忆")})
        }
        Button(onClick={vm.previewDiaryGeneration(role,notebook,requireNotNull(configId),prompt);onDismiss()},enabled=valid&&!state.working,modifier=Modifier.fillMaxWidth().padding(20.dp).heightIn(min=48.dp)){Text("进入发送预览")}
    }
}

@Composable internal fun DiaryGenerationDialog(state:CompanionState,vm:CompanionViewModel) {
    val ui=state.diaryGeneration?:return
    DiarySurface(when(ui.stage){"PREVIEW"->"确认日记草稿";"GENERATING"->"正在生成草稿";else->"发送前确认"},Diaries.notebookLabel(ui.frozen.notebook),{vm.dismissDiaryGeneration()},state.diaryError) {
        when(ui.stage) {
            "GENERATING"->Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {CircularProgressIndicator();Text("正在等待模型返回一篇严格格式的草稿…");OutlinedButton(onClick=vm::cancelDiaryGeneration){Text("停止生成")}}}
            "PREVIEW"->DiaryDraftPreview(ui,state,vm)
            else-> {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=MaterialTheme.shapes.large) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {Text("独立模型请求",fontWeight=FontWeight.SemiBold);Text("可能产生服务商费用。不会发送聊天历史、图片、资料、世界书、关系、记忆或工具，也不会自动保存返回内容。")}}
                    Text("${ui.frozen.config.name} · ${ui.frozen.config.model}",fontWeight=FontWeight.SemiBold)
                    SelectionContainer {Text(ui.frozen.config.endpoint,style=MaterialTheme.typography.bodySmall)}
                    Text("将发送的主题",style=MaterialTheme.typography.titleMedium)
                    SelectionContainer {Text(ui.frozen.prompt,Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant,MaterialTheme.shapes.medium).padding(16.dp))}
                    Text("还会发送当前冻结的角色名称和人设，以便写出对应视角。")
                }
                Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {OutlinedButton(onClick=vm::dismissDiaryGeneration,enabled=!state.working,modifier=Modifier.weight(1f)){Text("取消")};Button(onClick=vm::generateDiaryDraft,enabled=!state.working,modifier=Modifier.weight(1f)){Text("发送并生成")}}
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun ColumnScope.DiaryDraftPreview(ui:DiaryGenerationUi,state:CompanionState,vm:CompanionViewModel) {
    val original=requireNotNull(ui.draft)
    var title by remember(ui.frozen.id,original){mutableStateOf(original.title)}
    var content by remember(ui.frozen.id,original){mutableStateOf(original.content)}
    var mood by remember(ui.frozen.id,original){mutableStateOf(original.mood)}
    var date by remember(ui.frozen.id){mutableStateOf(Diaries.formatDate(Diaries.today()))}
    val occurredAt=runCatching {Diaries.parseDate(date)}.getOrNull()
    val valid=occurredAt!=null&&runCatching {Diaries.validate(ui.frozen.notebook,title,content,mood,occurredAt)}.isSuccess
    Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Surface(color=MaterialTheme.colorScheme.secondaryContainer,shape=MaterialTheme.shapes.large) {Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {Text("尚未保存",fontWeight=FontWeight.SemiBold);Text("请核对和修改。只有点击保存后才会进入加密数据库。")}}
        OutlinedTextField(title,{if(it.length<=Diaries.TITLE_LIMIT)title=it},Modifier.fillMaxWidth(),label={Text("标题")},singleLine=true,enabled=!state.working)
        OutlinedTextField(content,{if(it.length<=Diaries.CONTENT_LIMIT)content=it},Modifier.fillMaxWidth(),label={Text("正文")},minLines=8,maxLines=18,enabled=!state.working)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {Diaries.MOODS.forEach {value->FilterChip(mood==value,{mood=value},label={Text(Diaries.moodLabel(value))},enabled=!state.working)}}
        OutlinedTextField(date,{if(it.length<=10)date=it},Modifier.fillMaxWidth(),label={Text("记录日期")},singleLine=true,enabled=!state.working,isError=occurredAt==null)
        Text("保存后会一直标明“模型生成后确认”。这不代表模型拥有真实记忆、秘密或未表达的想法。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Row(Modifier.fillMaxWidth().padding(16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {OutlinedButton(onClick=vm::dismissDiaryGeneration,enabled=!state.working,modifier=Modifier.weight(1f)){Text("放弃草稿")};Button(onClick={vm.confirmDiaryDraft(DiaryDraft(title,content,mood),requireNotNull(occurredAt))},enabled=valid&&!state.working,modifier=Modifier.weight(1f)){Text("保存记录")}}
}

@Composable internal fun DiaryExportPreviewDialog(state:CompanionState,vm:CompanionViewModel,onSave:(String)->Unit) {
    val preview=state.diaryExportPreview?:return
    var baseName by remember(preview.entry.id,preview.entry.version){mutableStateOf(preview.suggestedBaseName)}
    val valid=DiaryExports.isValidBaseName(baseName)
    AlertDialog(onDismissRequest={if(!state.working)vm.dismissDiaryExport()},icon={Icon(Icons.Default.SaveAlt,null)},title={Text("导出完整记录")},
        text={Column(Modifier.heightIn(max=620.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Markdown · UTF-8 · ${preview.byteCount} 字节",style=MaterialTheme.typography.labelMedium)
            OutlinedTextField(baseName,{if(it.length<=DiaryExports.MAX_BASE_NAME)baseName=it},Modifier.fillMaxWidth(),label={Text("文件名（不含扩展名）")},singleLine=true,isError=!valid,enabled=!state.working,supportingText={Text(if(valid)"实际文件名：${DiaryExports.fileName(baseName)}" else "不能含路径符号、控制字符或首尾空格")})
            Surface(color=MaterialTheme.colorScheme.surfaceVariant,shape=MaterialTheme.shapes.medium) {SelectionContainer {Text(preview.content,Modifier.fillMaxWidth().padding(14.dp))}}
            Text("请检查私人信息。应用会直接写入系统文件选择器指定的位置并回读核对，不创建公共目录缓存。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }},confirmButton={Button(onClick={onSave(baseName)},enabled=valid&&!state.working){Text("选择位置并保存")}},dismissButton={TextButton(onClick=vm::dismissDiaryExport,enabled=!state.working){Text("取消")}})
}
