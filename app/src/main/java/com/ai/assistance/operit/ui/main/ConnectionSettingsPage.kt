package com.ai.assistance.operit.ui.main

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.api.chat.llmprovider.EndpointCompleter
import com.ai.assistance.operit.data.model.ModelConfigData
import com.ai.assistance.operit.data.model.SpeechServices

private data class ConnectionDraft(
    val name:String,val provider:String,val endpoint:String,val credential:String,val model:String,
    val timeout:String,val vision:Boolean
) {
    companion object {
        fun from(config:ModelConfigData?)=if(config==null)
            ConnectionDraft("新渠道","OpenAI 兼容","","","","90",false)
        else ConnectionDraft(config.name,config.provider,config.endpoint.removeSuffix("/chat/completions"),"",config.model,config.timeoutSeconds.toString(),config.visionEnabled)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun ConnectionSettingsPage(state:CompanionState,vm:CompanionViewModel,active:Boolean,onImages:()->Unit,onSpeech:()->Unit) {
    var selected by remember {mutableStateOf(state.configs.firstOrNull()?.id)}
    val config=state.configs.firstOrNull {it.id==selected}
    var draft by remember(selected,config?.version) {mutableStateOf(ConnectionDraft.from(config))}
    var confirm by remember {mutableStateOf(false)}
    var deleting by remember {mutableStateOf<ModelConfigData?>(null)}
    var issue by remember(selected) {mutableStateOf("")}
    var licenses by remember {mutableStateOf(false)}
    var chosenModel by remember {mutableStateOf<Pair<String,String>?>(null)}
    LaunchedEffect(selected,config?.version) {
        chosenModel?.let {choice->if(choice.first==selected){draft=draft.copy(model=choice.second);chosenModel=null}}
    }
    val chooseFromCatalog:(String?,String)->Unit={id,model->
        if(id==selected)draft=draft.copy(model=model)
        else {chosenModel=checkNotNull(id) to model;selected=id}
        issue=""
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            ConnectionSection("本地模型渠道","管理聊天模型的服务地址与 Key。选择渠道后编辑。",action={
                OutlinedButton(enabled=!state.working,onClick={selected=null;draft=ConnectionDraft.from(null);issue=""},shape=RoundedCornerShape(6.dp)){Text("新增渠道")}
            }) {
                state.configs.forEach {entry->
                    if(entry.id==selected)ChannelEditor(draft,true,!state.working,onChange={draft=it;issue=""},onDelete={deleting=entry})
                    else ChannelSummary(entry,!state.working,onEdit={selected=entry.id},onDelete={deleting=entry})
                }
                if(config==null)ChannelEditor(draft,false,!state.working,onChange={draft=it;issue=""},onDelete=null)
            }
            // Discovery is scoped to this exact address and credential; edits cancel the request and discard its results.
            key(active,state.configs.map {it.id to it.version},selected,draft.provider,draft.endpoint,draft.credential) {
                ConnectionCatalogSection(state,vm,selected,draft.name,draft.endpoint,draft.credential,draft.provider,draft.model,
                    onModel={draft=draft.copy(model=it);issue=""},onCatalogChoice=chooseFromCatalog)
            }
            ConnectionSection("连接偏好","以下设置只应用于当前编辑的渠道。") {
                ConnectionInput(draft.timeout,{if(it.length<=3)draft=draft.copy(timeout=it)},"请求超时（秒）",number=true)
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("图片输入",style=MaterialTheme.typography.titleSmall)
                        Text("确认所选模型支持视觉后开启。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(draft.vision,{draft=draft.copy(vision=it)})
                }
                Text("超时范围 15–300 秒，包含思考和回复时间。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ConnectionSection("生图与语音","分别管理对应模型、地址与语音设置。") {
                ConnectionGrid(listOf(
                    {MediaConnectionButton("生图服务",state.imageGenerationConfigs.size,onImages)},
                    {MediaConnectionButton("语音合成",state.speechServiceConfigs.count {it.kind==SpeechServices.SYNTHESIS},onSpeech)}
                ),maxColumns=2)
            }
            Text("Key 加密保存在本机，只发送到你确认的服务地址。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick={licenses=true}){Text("开源声明与许可")}
            if(state.diagnostic!=null)TextButton(onClick={vm.openGuide()}){Text("查看最近错误与脱敏诊断")}
        }
        HorizontalDivider()
        Column(Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(issue.isNotEmpty())Text(issue,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
            FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp,Alignment.End),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                OutlinedButton(enabled=config!=null&&draft==ConnectionDraft.from(config)&&!state.testing&&!state.working,onClick={vm.testConnection(checkNotNull(config))},shape=RoundedCornerShape(6.dp)){Text(if(state.testing)"正在测试…"else"测试连接")}
                Button(enabled=!state.working,onClick={
                    try {
                        require(draft.name.trim().isNotBlank()&&draft.name.trim().length<=80&&draft.name.none {it.isISOControl()})
                        val normalized=EndpointCompleter.completeEndpoint(draft.endpoint)
                        require(draft.model.isNotBlank()&&draft.model.length<=200&&draft.timeout.toInt() in 15..300)
                        require(draft.credential.isNotBlank()||(config!=null&&config.endpoint==normalized))
                        issue="";confirm=true
                    }catch(_:IllegalArgumentException){issue="请检查渠道名称、HTTPS 地址、模型、Key 和超时。"}
                },shape=RoundedCornerShape(6.dp)){Text("保存渠道")}
            }
            Text("保存后可测试；测试可能产生费用。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("确认渠道地址")},text={
        Text("${draft.name}\n${draft.model}\n\n${EndpointCompleter.completeEndpoint(draft.endpoint)}\n\n聊天内容和 Key 将发送到此服务。")
    },confirmButton={TextButton(enabled=!state.working,onClick={
        confirm=false
        vm.saveConfig(selected,draft.name,draft.provider,draft.endpoint,draft.model,draft.timeout.toInt(),draft.credential,
            draft.vision){selected=it;draft=draft.copy(credential="")}
    }){Text("确认并保存")}},dismissButton={TextButton(onClick={confirm=false}){Text("取消")}})
    deleting?.let {entry->AlertDialog(onDismissRequest={deleting=null},title={Text("删除模型渠道")},text={Text("删除“${entry.name}”及其 Key？被角色、聊天或历史记录使用的渠道不能删除。")},
        confirmButton={TextButton(enabled=!state.working,onClick={deleting=null;vm.deleteModelConfig(entry){if(selected==entry.id){selected=null;draft=ConnectionDraft.from(null)}}}){Text("删除",color=MaterialTheme.colorScheme.error)}},
        dismissButton={TextButton(onClick={deleting=null}){Text("取消")}})}
    if(licenses) {
        val context=LocalContext.current
        var notices by remember {mutableStateOf("正在加载…")}
        LaunchedEffect(Unit){notices=kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO){context.assets.open("THIRD-PARTY-NOTICES.txt").bufferedReader().use {it.readText()}}}
        AlertDialog(onDismissRequest={licenses=false},title={Text("开源声明")},text={SelectionContainer{Text(notices,Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),style=MaterialTheme.typography.bodySmall)}},confirmButton={TextButton(onClick={licenses=false}){Text("关闭")}})
    }
}

@Composable internal fun ConnectionSection(title:String,description:String,action:(@Composable ()->Unit)?=null,content:@Composable ColumnScope.()->Unit) {
    Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(10.dp),color=MaterialTheme.colorScheme.surface,
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.6f))) {
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val heading:@Composable (Modifier)->Unit={modifier->Column(modifier,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(title,style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold)
                    Text(description,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }}
                if(maxWidth>=600.dp&&LocalDensity.current.fontScale<=1.3f)Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    heading(Modifier.weight(1f));action?.invoke()
                }else Column(verticalArrangement=Arrangement.spacedBy(8.dp)){heading(Modifier);action?.invoke()}
            }
            content()
        }
    }
}

@Composable private fun ChannelSummary(config:ModelConfigData,enabled:Boolean,onEdit:()->Unit,onDelete:()->Unit) {
    Surface(color=MaterialTheme.colorScheme.surfaceContainerLow,shape=RoundedCornerShape(6.dp),modifier=Modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.padding(12.dp)) {
            val wide=maxWidth>=740.dp&&LocalDensity.current.fontScale<=1.3f
            val information:@Composable ()->Unit={
                Text(config.name,style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(config.provider,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(config.endpoint.removeSuffix("/chat/completions"),style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text(config.model,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary,maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            if(wide)Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                Column(Modifier.weight(0.7f)){Text(config.name,style=MaterialTheme.typography.titleSmall);Text(config.model,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)}
                Text(config.provider,Modifier.weight(0.45f),style=MaterialTheme.typography.bodySmall)
                Text(config.endpoint.removeSuffix("/chat/completions"),Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                Text("Key 已保存",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                ChannelActions(enabled,onEdit,onDelete)
            }else Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {information();ChannelActions(enabled,onEdit,onDelete)}
        }
    }
}

@Composable private fun ChannelActions(enabled:Boolean,onEdit:()->Unit,onDelete:()->Unit) {
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        OutlinedButton(enabled=enabled,onClick=onEdit,shape=RoundedCornerShape(6.dp)){Text("编辑")}
        OutlinedButton(enabled=enabled,onClick=onDelete,shape=RoundedCornerShape(6.dp),colors=ButtonDefaults.outlinedButtonColors(contentColor=MaterialTheme.colorScheme.error)){Text("删除")}
    }
}

@Composable private fun ChannelEditor(draft:ConnectionDraft,saved:Boolean,enabled:Boolean,onChange:(ConnectionDraft)->Unit,onDelete:(()->Unit)?) {
    var reveal by remember {mutableStateOf(false)}
    Surface(color=MaterialTheme.colorScheme.surfaceContainerLow,shape=RoundedCornerShape(6.dp),modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            val fields=listOf<@Composable ()->Unit>(
                {ConnectionInput(draft.name,{if(it.length<=80)onChange(draft.copy(name=it))},"渠道名称")},
                {ConnectionDropdown("服务商",draft.provider,listOf("OpenAI 兼容","DeepSeek","硅基流动")){provider->
                    if(provider!=draft.provider)onChange(draft.copy(provider=provider,credential="",model="",endpoint=when(provider){"DeepSeek"->"https://api.deepseek.com/v1";"硅基流动"->"https://api.siliconflow.cn/v1";else->""}))
                }},
                {ConnectionInput(draft.endpoint,{onChange(draft.copy(endpoint=it))},"API 地址")},
                {OutlinedTextField(draft.credential,{onChange(draft.copy(credential=it))},Modifier.fillMaxWidth(),label={Text("API Key")},singleLine=true,
                    shape=RoundedCornerShape(6.dp),textStyle=MaterialTheme.typography.bodySmall,
                    visualTransformation=if(reveal)VisualTransformation.None else PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),
                    trailingIcon={TextButton(onClick={reveal=!reveal},contentPadding=PaddingValues(4.dp)){Text(if(reveal)"隐藏"else"显示",style=MaterialTheme.typography.labelSmall)}})}
            )
            val actions:List<@Composable ()->Unit> = if(onDelete==null)emptyList()else listOf({
                Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    Text("操作",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(enabled=enabled,onClick=onDelete,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),shape=RoundedCornerShape(6.dp),colors=ButtonDefaults.outlinedButtonColors(contentColor=MaterialTheme.colorScheme.error)){Text("删除")}
                }
            })
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if(maxWidth>=880.dp&&LocalDensity.current.fontScale<=1.3f)Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.Bottom) {
                    val widths=listOf(1f,0.8f,1.45f,1.1f)
                    fields.forEachIndexed {index,field->Box(Modifier.weight(widths[index])){field()}}
                    actions.forEach {action->Box(Modifier.width(84.dp)){action()}}
                }else ConnectionGrid(fields+actions,maxColumns=2)
            }
            Text(if(saved)"已保存 Key；地址不变时留空继续使用。"else"填写 API Key 后可拉取模型。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable internal fun ConnectionGrid(fields:List<@Composable ()->Unit>,maxColumns:Int=4) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns=when {
            LocalDensity.current.fontScale>1.3f->1
            maxWidth>=880.dp->maxColumns
            maxWidth>=500.dp->minOf(2,maxColumns)
            else->1
        }
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            fields.chunked(columns).forEach {row->
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    row.forEach {field->Box(Modifier.weight(1f)){field()}}
                    repeat(columns-row.size){Spacer(Modifier.weight(1f))}
                }
            }
        }
    }
}

@Composable private fun ConnectionInput(value:String,onValue:(String)->Unit,label:String,number:Boolean=false,decimal:Boolean=false) {
    OutlinedTextField(value,onValue,Modifier.fillMaxWidth(),label={Text(label)},singleLine=true,shape=RoundedCornerShape(6.dp),textStyle=MaterialTheme.typography.bodySmall,
        keyboardOptions=KeyboardOptions(keyboardType=when {number->KeyboardType.Number;decimal->KeyboardType.Decimal;else->KeyboardType.Text}))
}

@Composable private fun ConnectionDropdown(label:String,value:String,values:List<String>,onValue:(String)->Unit) {
    var open by remember {mutableStateOf(false)}
    Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
        Text(label,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            OutlinedButton(onClick={open=true},Modifier.fillMaxWidth().heightIn(min=48.dp),shape=RoundedCornerShape(6.dp),contentPadding=PaddingValues(horizontal=10.dp)) {
                Text(value,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis);Text("▾")
            }
            DropdownMenu(expanded=open,onDismissRequest={open=false}) {
                values.forEach {option->DropdownMenuItem(text={Text(option)},onClick={open=false;onValue(option)})}
            }
        }
    }
}

@Composable private fun MediaConnectionButton(title:String,count:Int,onClick:()->Unit) {
    OutlinedButton(onClick=onClick,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(6.dp)) {
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(title,style=MaterialTheme.typography.titleSmall)
            Text(if(count==0)"未配置 · 点击设置"else"$count 个已保存服务",style=MaterialTheme.typography.bodySmall)
        }
        Text("设置")
    }
}
