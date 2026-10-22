package com.ai.assistance.operit.ui.main

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.api.chat.llmprovider.AvailableModel
import com.ai.assistance.operit.data.model.ChatFailure
import com.ai.assistance.operit.data.model.errorText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private data class CatalogChannel(val id:String?,val name:String,val models:List<AvailableModel>)

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun ConnectionCatalogSection(state:CompanionState,vm:CompanionViewModel,
    selected:String?,name:String,endpoint:String,credential:String,provider:String,model:String,
    onModel:(String)->Unit,onCatalogChoice:(String?,String)->Unit) {
    var entries by remember {mutableStateOf<List<CatalogChannel>>(emptyList())}
    var failures by remember {mutableStateOf<List<Pair<String,String>>>(emptyList())}
    var currentModels by remember {mutableStateOf<List<AvailableModel>?>(null)}
    var loading by remember {mutableStateOf(false)}
    var showing by remember {mutableStateOf(false)}
    var showAll by remember {mutableStateOf(false)}
    val scope=rememberCoroutineScope()
    val fetch:(Boolean)->Unit={all->
        scope.launch {
            loading=true;entries=emptyList();failures=emptyList();currentModels=null;showing=false
            try {
                if(all) {
                    val results=mutableListOf<CatalogChannel>()
                    val errors=mutableListOf<Pair<String,String>>()
                    for(channel in state.configs) {
                        val active=channel.id==selected
                        val channelName=if(active)name else channel.name
                        try {
                            val models=vm.availableModels(channel.id,if(active)endpoint else channel.endpoint,if(active)credential else "")
                            results.add(CatalogChannel(channel.id,channelName,models))
                            if(active)currentModels=models
                        }catch(e:CancellationException){throw e}
                        catch(e:ChatFailure){errors.add(channelName to errorText(e.code))}
                        catch(_:IllegalArgumentException){errors.add(channelName to "请检查 HTTPS 服务地址和 Key 格式。")}
                    }
                    entries=results;failures=errors;showAll=true
                }else {
                    val models=vm.availableModels(selected,endpoint,credential)
                    currentModels=models;entries=listOf(CatalogChannel(selected,name,models));showing=true
                }
            }catch(e:CancellationException){throw e}
            catch(e:ChatFailure){failures=listOf(name to errorText(e.code))}
            catch(_:IllegalArgumentException){failures=listOf(name to "请检查 HTTPS 服务地址和 Key 格式。")}
            finally {loading=false}
        }
    }
    ConnectionSection("模型列表",if(entries.isEmpty())"选择当前渠道使用的文本模型。"else"本次已读取 ${entries.sumOf {it.models.size}} 个模型。",action={
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            OutlinedButton(enabled=!loading&&!state.working,onClick={fetch(false)},shape=RoundedCornerShape(6.dp)){Text(if(loading)"正在拉取…"else"拉取当前渠道")}
            OutlinedButton(enabled=!loading&&!state.working&&state.configs.isNotEmpty(),onClick={fetch(true)},shape=RoundedCornerShape(6.dp)){Text("拉取全部渠道")}
        }
    }) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val input:@Composable (Modifier)->Unit={modifier->OutlinedTextField(model,onModel,modifier,label={Text("当前文本模型 ID")},singleLine=true,shape=RoundedCornerShape(6.dp),textStyle=MaterialTheme.typography.bodySmall)}
            val picker:@Composable (Modifier)->Unit={modifier->OutlinedButton(enabled=!loading&&!state.working,onClick={if(currentModels==null)fetch(false)else showing=true},modifier=modifier,shape=RoundedCornerShape(6.dp)){Text("选择")}}
            if(maxWidth<500.dp||LocalDensity.current.fontScale>1.3f)Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                input(Modifier.fillMaxWidth());picker(Modifier.fillMaxWidth())
            }else Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                input(Modifier.weight(1f));picker(Modifier.padding(top=8.dp))
            }
        }
        if(provider=="DeepSeek") {
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("deepseek-flash" to "Flash","deepseek-v4-pro" to "Pro").forEach {(id,title)->
                    FilterChip(selected=model==id,onClick={onModel(id)},label={Text(title)})
                }
            }
            if(model in setOf("deepseek-chat","deepseek-reasoner"))Text("当前是旧模型名称，请选择 Flash 或 Pro 后保存。",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
        }
        if(entries.isNotEmpty())OutlinedButton(onClick={showAll=true},shape=RoundedCornerShape(6.dp)){Text("查看模型列表（${entries.sumOf {it.models.size}}）")}
        failures.forEach {(channel,error)->Text("$channel：$error",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
        Text("拉取只读取模型列表，不发送聊天内容。模型选择后请保存渠道。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if(showing)ModelCatalogDialog(checkNotNull(currentModels),model,{onModel(it);showing=false}){showing=false}
    if(showAll)CatalogChannelsDialog(entries,{id,chosen->onCatalogChoice(id,chosen);showAll=false}){showAll=false}
}

@Composable private fun ModelCatalogDialog(models:List<AvailableModel>,selected:String,onChoose:(String)->Unit,onDismiss:()->Unit) {
    var query by remember {mutableStateOf("")}
    val visible=remember(models,query){models.filter {it.id.contains(query,true)||it.name?.contains(query,true)==true}}
    AlertDialog(onDismissRequest=onDismiss,title={Text("选择模型")},text={
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text("搜索模型名称或 ID")},singleLine=true)
            if(models.isEmpty())Text("服务返回的模型列表为空。")
            else if(visible.isEmpty())Text("没有匹配的模型。")
            LazyColumn(Modifier.heightIn(max=320.dp)) {
                items(visible,key={it.id}) {entry->
                    Surface(onClick={onChoose(entry.id)},color=if(selected==entry.id)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal=12.dp,vertical=14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                            if(!entry.name.isNullOrBlank()&&entry.name!=entry.id)Text(entry.name,style=MaterialTheme.typography.titleSmall)
                            Text(entry.id,style=MaterialTheme.typography.bodyMedium)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    },confirmButton={TextButton(onClick=onDismiss){Text("关闭")}})
}

@Composable private fun CatalogChannelsDialog(channels:List<CatalogChannel>,onChoose:(String?,String)->Unit,onDismiss:()->Unit) {
    var query by remember {mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,title={Text("渠道模型列表")},text={
        Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text("搜索渠道或模型")},singleLine=true)
            val visible=channels.map {channel->channel to channel.models.filter {channel.name.contains(query,true)||it.id.contains(query,true)||it.name?.contains(query,true)==true}}
            if(visible.sumOf {it.second.size}==0)Text(if(channels.isEmpty())"没有成功读取的模型。"else"没有匹配的模型。")
            LazyColumn(Modifier.heightIn(max=320.dp)) {
                visible.forEach {(channel,models)->
                    if(models.isNotEmpty()) {
                        item(key="channel:${channel.id}"){Text(channel.name,Modifier.padding(vertical=10.dp),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)}
                        items(models,key={"${channel.id}/${it.id}"}) {entry->
                            Surface(onClick={onChoose(channel.id,entry.id)},modifier=Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(horizontal=12.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                                    if(!entry.name.isNullOrBlank()&&entry.name!=entry.id)Text(entry.name,style=MaterialTheme.typography.titleSmall,maxLines=2,overflow=TextOverflow.Ellipsis)
                                    Text(entry.id,style=MaterialTheme.typography.bodySmall)
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    },confirmButton={TextButton(onClick=onDismiss){Text("关闭")}})
}
