package com.ai.assistance.operit.ui.main

import com.ai.assistance.operit.ui.main.ThemedIcon as Icon

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.api.image.ImageGenerationEndpoint
import com.ai.assistance.operit.data.model.*

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun ImageGenerationSettingsPage(state:CompanionState,vm:CompanionViewModel,onBack:()->Unit) {
    BackHandler(onBack=onBack)
    var selected by remember {mutableStateOf<String?>(state.imageGenerationConfigs.firstOrNull()?.id)}
    val config=state.imageGenerationConfigs.firstOrNull {it.id==selected}
    var endpoint by remember(selected,config?.version){mutableStateOf(config?.endpoint?:"")}
    var model by remember(selected,config?.version){mutableStateOf(config?.model?:"gpt-image-1")}
    var timeout by remember(selected,config?.version){mutableStateOf((config?.timeoutSeconds?:180).toString())}
    var size by remember(selected,config?.version){mutableStateOf(config?.size?:"1024x1024")}
    var key by remember(selected,config?.version){mutableStateOf("")}
    var issue by remember {mutableStateOf("")}
    var confirm by remember {mutableStateOf(false)}
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {
            IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"返回")}
            Column {Text("生图服务",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold);Text("独立地址、Key 与模型",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(13.dp)) {
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                state.imageGenerationConfigs.forEach {FilterChip(selected==it.id,{selected=it.id},label={Text(it.name)})}
                AssistChip(onClick={selected=null;key=""},label={Text("＋ 新服务")})
            }
            OutlinedTextField(endpoint,{endpoint=it;issue=""},Modifier.fillMaxWidth(),label={Text("HTTPS 生图地址")},placeholder={Text("https://api.example.com/v1")},singleLine=true,
                supportingText={Text("接受域名、/v1 或完整 /images/generations 地址。")})
            OutlinedTextField(model,{if(it.length<=200)model=it},Modifier.fillMaxWidth(),label={Text("生图模型")},singleLine=true)
            OutlinedTextField(key,{key=it},Modifier.fillMaxWidth(),label={Text("API Key")},visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),singleLine=true,
                supportingText={Text(if(config==null)"独立加密保存，只发往确认的生图地址。" else "已保存的 Key 不回显；地址不变时可留空。")})
            OutlinedTextField(timeout,{if(it.length<=3)timeout=it},Modifier.fillMaxWidth(),label={Text("请求超时（15–300 秒）")},singleLine=true,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
            Text("生成尺寸",style=MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {ImageGenerations.SIZES.forEach {value->FilterChip(size==value,{size=value},label={Text(value)})}}
            if(issue.isNotEmpty())Text(issue,color=MaterialTheme.colorScheme.error)
            Button(enabled=!state.working,onClick={
                try {
                    val normalized=ImageGenerationEndpoint.complete(endpoint)
                    val candidate=ImageGenerationConfig(selected?:"preview","预览",normalized,model.trim(),timeout.toInt(),"pending",1,size)
                    ImageGenerations.validateConfig(candidate)
                    require(key.isNotBlank()||(config!=null&&config.endpoint==normalized))
                    issue="";confirm=true
                }catch(_:Exception){issue="请检查 HTTPS 地址、模型、尺寸、超时、Key。"}
            }){Text("保存生图服务")}
            Text("本版发送一条提示词并请求一张图片，只接受服务响应中的 data[0].b64_json。不会跟随重定向或下载结果 URL，也不会把文字回复当图片。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
        }
    }
    if(confirm)AlertDialog(onDismissRequest={confirm=false},title={Text("确认生图目的地")},text={Text("提示词与 API Key 将发送到：\n\n${ImageGenerationEndpoint.complete(endpoint)}\n\n模型：${model.trim()}\n尺寸：$size\n\n请确认地址和模型信息来自你信任的提供方。")},
        confirmButton={TextButton(onClick={confirm=false;vm.saveImageGenerationConfig(selected,endpoint,model,timeout.toInt(),key,size){selected=it;key=""}}){Text("确认并保存")}},
        dismissButton={TextButton(onClick={confirm=false}){Text("取消")}})
}

@Composable internal fun ImageGenerationDialog(role:RoleEntity,chat:ChatEntity,state:CompanionState,vm:CompanionViewModel,onSettings:()->Unit,onDismiss:()->Unit) {
    var selected by remember {mutableStateOf(state.imageGenerationConfigs.firstOrNull()?.id)}
    val config=state.imageGenerationConfigs.firstOrNull {it.id==selected}
    var prompt by remember {mutableStateOf("")}
    AlertDialog(onDismissRequest=onDismiss,icon={Icon(Icons.Default.AutoAwesome,null)},title={Text("让 ${role.name} 发来一张图片")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(13.dp)) {
            if(state.imageGenerationConfigs.isEmpty()) {
                Text("还没有生图服务。请先在设置中确认独立的 HTTPS 地址、模型和 Key。")
                OutlinedButton(onClick={onDismiss();onSettings()},modifier=Modifier.fillMaxWidth()){Text("打开生图服务设置")}
            } else {
                Text("选择服务",style=MaterialTheme.typography.labelLarge)
                state.imageGenerationConfigs.forEach {item->FilterChip(selected==item.id,{selected=item.id},label={Text(item.name)})}
                OutlinedTextField(prompt,{if(it.length<=ImageGenerations.MAX_PROMPT)prompt=it},Modifier.fillMaxWidth(),label={Text("图片提示词")},minLines=4,maxLines=9,
                    supportingText={Text("内容会直接发送到下方生图地址，不会先交给聊天模型改写。")})
                if(config!=null)Surface(shape=MaterialTheme.shapes.large,color=MaterialTheme.colorScheme.surfaceVariant) {
                    Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                        Text(config.model,fontWeight=FontWeight.SemiBold)
                        Text("${config.size} · ${config.endpoint}",style=MaterialTheme.typography.bodySmall)
                    }
                }
                Text("确认后只请求一张。关闭页面不会改变在途请求；可在图片卡片上停止。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    },confirmButton={if(config!=null)TextButton(enabled=prompt.isNotBlank()&&state.generatingImageId==null&&state.busyChat==null&&!state.working,onClick={val value=prompt.trim();onDismiss();vm.generateImage(role.scope(),chat.id,config.id,value)}){Text("生成图片")}},
        dismissButton={TextButton(onClick=onDismiss){Text("取消")}})
}
