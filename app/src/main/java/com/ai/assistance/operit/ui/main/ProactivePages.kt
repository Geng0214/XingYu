package com.ai.assistance.operit.ui.main

import com.ai.assistance.operit.ui.main.ThemedIcon as Icon

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ai.assistance.operit.data.model.*

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun ProactiveDialog(role:RoleEntity,state:CompanionState,vm:CompanionViewModel,onDismiss:()->Unit) {
    val scope=role.scope();val data by remember(scope){vm.proactive(scope)}.collectAsStateWithLifecycle<ProactiveData?>(null)
    val loaded=data;val setting=loaded?.setting
    var enabled by remember(setting?.version){mutableStateOf(setting?.enabled==true)}
    var chatId by remember(setting?.version,loaded?.chats){mutableStateOf(setting?.chatId?:loaded?.chats?.firstOrNull()?.id.orEmpty())}
    var configId by remember(setting?.version,state.configs){mutableStateOf(setting?.configId?:role.defaultConfigId?:state.configs.firstOrNull()?.id.orEmpty())}
    var quietEnabled by remember(setting?.version){mutableStateOf(setting?.let {it.quietStartMinute!=it.quietEndMinute}?:true)}
    var quietStart by remember(setting?.version){mutableStateOf(minuteText(setting?.quietStartMinute?:1320))}
    var quietEnd by remember(setting?.version){mutableStateOf(minuteText(setting?.quietEndMinute?:480))}
    var interval by remember(setting?.version){mutableIntStateOf(setting?.minIntervalHours?:24)}
    var daily by remember(setting?.version){mutableIntStateOf(setting?.dailyLimit?:1)}
    var error by remember(setting?.version){mutableStateOf<String?>(null)}
    val context=LocalContext.current
    var notificationGranted by remember {mutableStateOf(Build.VERSION.SDK_INT<33||context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)}
    var afterPermission by remember {mutableStateOf("")}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->
        notificationGranted=granted
        if(granted&&afterPermission=="enable")enabled=true
        if(granted&&afterPermission=="preview")vm.previewProactiveNotification()
        afterPermission=""
    }
    Dialog(onDismissRequest={if(!state.working)onDismiss()},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize()) {Column(Modifier.statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
                // Pass the close callback as the click listener; returning it from an if branch does not invoke it.
                IconButton(enabled=!state.working,onClick=onDismiss){Icon(Icons.AutoMirrored.Filled.ArrowBack,"返回")}
                Column(Modifier.weight(1f)){Text("主动消息",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold);Text(role.name,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)}
            }
            when {
                loaded==null->Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
                else->Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal=20.dp,vertical=8.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=MaterialTheme.shapes.large) {Column(Modifier.fillMaxWidth().padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(if(state.proactiveMaster)"本机总开关已开启" else "本机总开关未开启",fontWeight=FontWeight.SemiBold)
                        Text("开启后，后台会把这段对话的有限上下文发到所选模型地址。通知只显示“星语有新消息 · 解锁应用查看”。隐私锁不会阻止你已授权的后台请求。",style=MaterialTheme.typography.bodySmall)
                    }}
                    if(role.isRaw())Text("原味模型角色不能开启主动消息；请先切回角色模式。",color=MaterialTheme.colorScheme.error)
                    Row(verticalAlignment=Alignment.CenterVertically) {Switch(enabled&&!role.isRaw(),{value->
                        if(value&&!notificationGranted&&Build.VERSION.SDK_INT>=33){afterPermission="enable";permission.launch(Manifest.permission.POST_NOTIFICATIONS)}else enabled=value
                    },enabled=!role.isRaw());Spacer(Modifier.width(12.dp));Column(Modifier.weight(1f)){Text("允许这个角色主动联系我");Text("角色开关与本机总开关必须同时开启。",style=MaterialTheme.typography.bodySmall)}}
                    Text("目标对话",fontWeight=FontWeight.SemiBold)
                    if(loaded.chats.isEmpty())Text("请先为这个角色新建一段对话。",color=MaterialTheme.colorScheme.error)
                    loaded.chats.forEach {chat->FilterChip(chatId==chat.id,{chatId=chat.id},label={Text(chat.title,maxLines=1,overflow=TextOverflow.Ellipsis)})}
                    Text("模型连接",fontWeight=FontWeight.SemiBold)
                    state.configs.forEach {config->FilterChip(configId==config.id,{configId=config.id},label={Text(config.name,maxLines=1)})}
                    Row(verticalAlignment=Alignment.CenterVertically){Switch(quietEnabled,{quietEnabled=it});Spacer(Modifier.width(12.dp));Text("免打扰时段")}
                    if(quietEnabled)BoxWithConstraints(Modifier.fillMaxWidth()) {
                        // 窄屏或放大字号时上下排列，保证开始和结束标签都能读清。
                        val stackFields=maxWidth<360.dp||LocalDensity.current.fontScale>=1.2f
                        if(stackFields)Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(quietStart,{quietStart=it.take(5)},Modifier.fillMaxWidth(),label={Text("开始 HH:mm")},singleLine=true)
                            OutlinedTextField(quietEnd,{quietEnd=it.take(5)},Modifier.fillMaxWidth(),label={Text("结束 HH:mm")},singleLine=true)
                        } else Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(quietStart,{quietStart=it.take(5)},Modifier.weight(1f),label={Text("开始 HH:mm")},singleLine=true)
                            OutlinedTextField(quietEnd,{quietEnd=it.take(5)},Modifier.weight(1f),label={Text("结束 HH:mm")},singleLine=true)
                        }
                    }
                    Text("最短间隔",fontWeight=FontWeight.SemiBold);FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){listOf(6,12,24,48,72).forEach {value->FilterChip(interval==value,{interval=value},label={Text("${value}小时")})}}
                    Text("角色每日上限",fontWeight=FontWeight.SemiBold);FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){(1..3).forEach {value->FilterChip(daily==value,{daily=value},label={Text("${value}条")})}}
                    OutlinedButton(onClick={if(notificationGranted)vm.previewProactiveNotification()else {afterPermission="preview";permission.launch(Manifest.permission.POST_NOTIFICATIONS)}},modifier=Modifier.fillMaxWidth()) {Icon(Icons.Default.Notifications,null);Spacer(Modifier.width(8.dp));Text("预览锁屏通知")}
                    Text("系统会在联网且电量允许时不精确检查。每次最多一条，不补发错过时段；主动请求不使用插件、MCP、图片、文件或自动操作。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    error?.let {Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
                    Button(enabled=!state.working&&!role.isRaw()&&loaded.chats.isNotEmpty()&&state.configs.isNotEmpty(),onClick={
                        try {
                            val start=if(quietEnabled)parseMinute(quietStart) else 0;val end=if(quietEnabled)parseMinute(quietEnd) else 0
                            error=null;vm.saveProactive(scope,enabled,chatId,configId,start,end,interval,daily,setting?.version,onDismiss)
                        }catch(_:Exception){error="请检查对话、模型和时间。"}
                    },modifier=Modifier.fillMaxWidth().heightIn(min=50.dp)){Text("保存主动消息设置")}
                    Spacer(Modifier.height(20.dp))
                }
            }
        }}
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun ProactiveGlobalDialog(state:CompanionState,vm:CompanionViewModel,onDismiss:()->Unit) {
    val context=LocalContext.current;var daily by remember(state.proactiveDailyLimit){mutableIntStateOf(state.proactiveDailyLimit)}
    var permissionAction by remember {mutableStateOf("")}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted->
        if(granted&&permissionAction=="enable")vm.setProactiveMaster(true,daily)
        if(granted&&permissionAction=="preview")vm.previewProactiveNotification()
        permissionAction=""
    }
    AlertDialog(onDismissRequest=onDismiss,title={Text("主动消息总开关")},text={Column(Modifier.heightIn(max=adaptiveDialogContentMaxHeight(480.dp)).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("总开关默认关闭。开启后，仅已单独启用的角色可以在后台调用你确认的模型地址。系统调度可能延迟，不会补发积压消息。")
        Text("全局每日上限",fontWeight=FontWeight.SemiBold);FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf(1,2,3,5,10).forEach {value->FilterChip(daily==value,{daily=value},label={Text("$value")})}}
        Text("通知永远只显示固定泛化文字，角色名和消息正文不会出现在锁屏。关闭总开关会取消系统任务和正在运行的主动请求。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick={
            val granted=Build.VERSION.SDK_INT<33||context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED
            if(granted)vm.previewProactiveNotification()else {permissionAction="preview";permission.launch(Manifest.permission.POST_NOTIFICATIONS)}
        },modifier=Modifier.fillMaxWidth()){Text("预览隐私通知")}
    }},confirmButton={TextButton(enabled=!state.working,onClick={
        if(state.proactiveMaster)vm.setProactiveMaster(true,daily)
        else {
            val granted=Build.VERSION.SDK_INT<33||context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED
            if(granted)vm.setProactiveMaster(true,daily)else {permissionAction="enable";permission.launch(Manifest.permission.POST_NOTIFICATIONS)}
        }
    }){Text(if(state.proactiveMaster)"保存上限" else "保存并开启")}},dismissButton={Row {if(state.proactiveMaster)TextButton(enabled=!state.working,onClick={vm.setProactiveMaster(false,daily)}){Text("关闭总开关")};TextButton(onClick=onDismiss){Text("完成")}}})
}

private fun minuteText(value:Int)="%02d:%02d".format(value/60,value%60)
private fun parseMinute(value:String):Int {val parts=value.split(':');require(parts.size==2);val hour=parts[0].toInt();val minute=parts[1].toInt();require(hour in 0..23&&minute in 0..59);return hour*60+minute}
