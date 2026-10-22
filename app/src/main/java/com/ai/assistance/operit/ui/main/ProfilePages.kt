package com.ai.assistance.operit.ui.main

import com.ai.assistance.operit.ui.main.ThemedIcon as Icon

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ai.assistance.operit.data.db.ToolPkgAddress
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.*
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalFoundationApi::class)
@Composable internal fun RolePage(state: CompanionState,vm: CompanionViewModel,onPortraitPick:(com.ai.assistance.operit.data.model.RoleAccessScope,String)->Unit,initialHomeRoleId:String?=null,initialHomeCommandId:String?=null,onInitialHomeConsumed:()->Unit={},onBack:(()->Unit)?=null,initialEditRoleId:String?=null) {
    var homeRoleId by rememberSaveable {mutableStateOf<String?>(null)}
    var homeCommandId by rememberSaveable {mutableStateOf<String?>(null)}
    LaunchedEffect(initialHomeRoleId,initialHomeCommandId) {
        if(initialHomeRoleId!=null) {
            homeRoleId=initialHomeRoleId
            homeCommandId=initialHomeCommandId
            onInitialHomeConsumed()
        }
    }
    val homeRole=state.roles.firstOrNull {it.id==homeRoleId}
    BackHandler(homeRoleId!=null&&onBack!=null) {homeRoleId=null;homeCommandId=null}
    if(homeRole!=null) {
        Column(Modifier.fillMaxSize()) {HomePage(homeRole,state,vm,{homeRoleId=null;homeCommandId=null},homeCommandId)}
        return
    }
    var editing by rememberSaveable { mutableStateOf(initialEditRoleId!=null) }
    var roleId by rememberSaveable { mutableStateOf(initialEditRoleId) }
    var search by rememberSaveable { mutableStateOf("") }
    var menuRoleId by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<RoleDeleteRequest?>(null) }
    val roles=state.roles.filter {search.isBlank()||it.name.contains(search,true)||it.prompt.contains(search,true)}
    val files=LocalFileActions.current
    BackHandler(onBack!=null&&homeRoleId==null) {onBack?.invoke()}
    BackHandler(editing){editing=false}
    Column(Modifier.fillMaxSize()) {
        if(editing) {
            EditorHeader(if(roleId==null)"新增角色"else"编辑角色"){editing=false}
            RoleEditorPage(state,vm,roleId,onPortraitPick){editing=false}
        } else {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                // Give large text its own heading row; keeping all actions beside it leaves a single orphaned character on phones.
                val separateActions=maxWidth<480.dp&&LocalDensity.current.fontScale>=1.3f
                Column {
                    Row(Modifier.fillMaxWidth().padding(start=24.dp,end=12.dp,top=16.dp,bottom=16.dp),verticalAlignment=Alignment.CenterVertically) {
                        if(onBack!=null)IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"返回上一页")}
                        Text("角色管理",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f))
                        if(!separateActions) {
                            IconButton(enabled=!state.working,onClick=files.importRole){Icon(Icons.Default.FileDownload,"导入角色模板")}
                            TextButton(enabled=!state.working,onClick={roleId=null;editing=true},modifier=Modifier.heightIn(min=48.dp)){Text("新增角色")}
                        }
                    }
                    if(separateActions)Row(Modifier.fillMaxWidth().padding(horizontal=20.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(enabled=!state.working,onClick=files.importRole){Text("导入模板")}
                        OutlinedButton(enabled=!state.working,onClick={roleId=null;editing=true}){Text("新增角色")}
                    }
                }
            }
            OutlinedTextField(search,{search=it},Modifier.fillMaxWidth().padding(horizontal=20.dp,vertical=8.dp),singleLine=true,
                label={Text("搜索角色")},placeholder={Text("名称或人设")},leadingIcon={Icon(Icons.Default.Search,null)},
                trailingIcon={if(search.isNotEmpty())IconButton(onClick={search=""}){Icon(Icons.Default.Close,"清空角色搜索")}})
            LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(bottom=24.dp)) {
                if(roles.isEmpty())item {
                    Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                        Text(if(search.isBlank())"为对话添一个熟悉的声音。"else"没有找到匹配的角色",style=MaterialTheme.typography.titleMedium)
                        if(search.isBlank())Button(enabled=!state.working,onClick={roleId=null;editing=true}){Text("创建第一个角色")}
                        else TextButton(onClick={search=""}){Text("清空搜索")}
                    }
                }
                items(roles,key={it.id}) { role->
                    Surface(color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth().combinedClickable(
                        enabled=!state.working,onClick={roleId=role.id;editing=true},onLongClick={menuRoleId=role.id},onLongClickLabel="角色操作"
                    )) {
                        Row(Modifier.padding(horizontal=20.dp,vertical=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                            PortraitAvatar(role.name,role.scope(),vm,54.dp)
                            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                                Text(role.name,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,maxLines=1,overflow=TextOverflow.Ellipsis)
                                Text(role.prompt.replace('\n',' '),style=MaterialTheme.typography.bodyMedium,maxLines=1,overflow=TextOverflow.Ellipsis,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(enabled=!state.working,onClick={roleId=role.id;editing=true},modifier=Modifier.heightIn(min=48.dp).semantics {contentDescription="编辑 ${role.name}"}){Text("编辑")}
                            Box {
                                IconButton(enabled=!state.working,onClick={menuRoleId=role.id}){Icon(Icons.Default.MoreVert,"${role.name}的角色操作")}
                                DropdownMenu(expanded=menuRoleId==role.id,onDismissRequest={menuRoleId=null}) {
                                    DropdownMenuItem(text={Text("打开家园")},leadingIcon={Icon(Icons.Default.Home,null)},enabled=!state.working,
                                        onClick={menuRoleId=null;vm.selectRole(role.scope());homeRoleId=role.id})
                                    DropdownMenuItem(text={Text("删除角色",color=MaterialTheme.colorScheme.error)},
                                        leadingIcon={Icon(Icons.Default.PersonRemove,null,tint=MaterialTheme.colorScheme.error)},enabled=!state.working,
                                        onClick={menuRoleId=null;vm.roleChatCount(role.scope()){count->deleting=RoleDeleteRequest(role,count)}})
                                }
                            }
                        }
                    }
                    HorizontalDivider(Modifier.padding(start=88.dp),color=MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.4f))
                }
                item { Text("点“编辑”修改角色资料；右侧菜单或长按角色可打开家园或删除角色。每位角色保留一个专属私聊。",Modifier.padding(24.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
    deleting?.let { request->RoleDeleteDialog(request,state.working,onDismiss={deleting=null},onConfirm={
        vm.deleteRole(request.role.scope()){deleting=null}
    }) }
}

@Composable internal fun SettingsPage(
    state:CompanionState,
    vm:CompanionViewModel,
    requestConnections:Boolean=false,
    onConnectionsOpened:()->Unit={},
    activeThemePluginId:String?,
    dock:CompanionDockConfig,
    onSaveDock:(CompanionDockConfig)->Unit,
    onOpenTheme:()->Unit,
    onApplyTheme:(BuiltinThemePlugin)->Unit
) {
    var backup by remember {mutableStateOf(false)}
    var connections by remember { mutableStateOf(false) }
    var extensions by remember {mutableStateOf(false)}
    var permissionCenter by remember {mutableStateOf(false)}
    var info by remember { mutableStateOf<String?>(null) }
    var privacy by remember {mutableStateOf(false)}
    var imageGeneration by remember {mutableStateOf(false)}
    var proactive by remember {mutableStateOf(false)}
    var speech by remember {mutableStateOf(false)}
    var pluginLibrary by rememberSaveable {mutableStateOf(false)}
    var dockEditor by rememberSaveable {mutableStateOf(false)}
    LaunchedEffect(requestConnections){if(requestConnections){backup=false;extensions=false;permissionCenter=false;privacy=false;imageGeneration=false;speech=false;pluginLibrary=false;connections=true;onConnectionsOpened()}}
    if(backup){BackupPage(state,vm){backup=false};return}
    if(privacy){PrivacySettingsPage(vm){privacy=false};return}
    if(permissionCenter){
        PermissionCenterPage(
            state,vm,
            onBack={permissionCenter=false},
            onManageConnections={permissionCenter=false;connections=true},
            onManageImageGeneration={permissionCenter=false;imageGeneration=true},
            onManageSpeech={permissionCenter=false;speech=true},
            onManageExtensions={permissionCenter=false;extensions=true}
        )
        return
    }
    if(extensions){ExtensionPage(state,vm){extensions=false};return}
    if(imageGeneration&&!connections){ImageGenerationSettingsPage(state,vm){imageGeneration=false};return}
    if(speech&&!connections){SpeechServiceSettingsPage(state,vm){speech=false};return}
    BackHandler(pluginLibrary){pluginLibrary=false}
    if(pluginLibrary) {
        Column(Modifier.fillMaxSize()) {
            EditorHeader("插件与主题"){pluginLibrary=false}
            PluginLibraryPage(state,vm,activeThemePluginId,onOpenTheme,onApplyTheme,showPageHeader=false)
        }
        return
    }
    BackHandler(connections&&!imageGeneration&&!speech){connections=false}
    Column(Modifier.fillMaxSize()) {
        if(connections) {
            Box(Modifier.fillMaxSize()) {
                // Keep the editor composed under media settings, otherwise returning discards its unsaved model and Key.
                Column(Modifier.fillMaxSize()) {
                    EditorHeader("模型连接"){connections=false}
                    ConnectionSettingsPage(state,vm,active=!imageGeneration&&!speech,onImages={imageGeneration=true},onSpeech={speech=true})
                }
                if(imageGeneration)Surface(Modifier.fillMaxSize()){ImageGenerationSettingsPage(state,vm){imageGeneration=false}}
                if(speech)Surface(Modifier.fillMaxSize()){SpeechServiceSettingsPage(state,vm){speech=false}}
            }
        } else {
            Text("设置",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold,modifier=Modifier.padding(start=24.dp,top=24.dp,bottom=24.dp))
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal=20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface) {
                    Row(Modifier.fillMaxWidth().padding(20.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                        Image(painterResource(R.drawable.ic_xingyu_art),contentDescription=null,modifier=Modifier.size(60.dp).clip(RoundedCornerShape(16.dp)))
                        Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            Text("星语",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
                            Text("和更懂你的 AI 聊天",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface) { Column {
                    SettingsRow(Icons.Default.Hub,"模型连接",if(state.configs.isEmpty())"添加你的模型服务"else"${state.configs.size} 个已保存连接"){connections=true}
                    HorizontalDivider(Modifier.padding(start=60.dp))
                    SettingsRow(Icons.Default.AutoAwesome,"生图服务",if(state.imageGenerationConfigs.isEmpty())"配置独立生图地址与模型"else"${state.imageGenerationConfigs.size} 个已保存服务"){imageGeneration=true}
                    HorizontalDivider(Modifier.padding(start=60.dp))
                    SettingsRow(Icons.Default.Mic,"语音服务",if(state.speechServiceConfigs.isEmpty())"配置录音识别与回复朗读"else"${state.speechServiceConfigs.size} 个已保存服务"){speech=true}
                    HorizontalDivider(Modifier.padding(start=60.dp))
                    SettingsRow(Icons.Default.Extension,"插件与主题","管理应用主题和工具扩展"){pluginLibrary=true}
                    HorizontalDivider(Modifier.padding(start=60.dp))
                    // Use the mapped Home vector because themed settings icons require an explicit filled/outlined mapping at runtime.
                    SettingsRow(Icons.Default.Home,"导航栏","调整聊天、家园、日程和设置的顺序及显示"){dockEditor=true}
                    HorizontalDivider(Modifier.padding(start=60.dp))
                    SettingsRow(Icons.Default.Security,"权限中心","查看并撤销角色工具授权，检查最近操作"){permissionCenter=true}
                    HorizontalDivider(Modifier.padding(start=60.dp))
                    SettingsRow(Icons.Default.NotificationsActive,"主动消息",if(state.proactiveMaster)"总开关已开启 · 每日最多 ${state.proactiveDailyLimit} 条" else "总开关已关闭"){proactive=true}
                    HorizontalDivider(Modifier.padding(start=60.dp))
                    SettingsRow(Icons.Default.Palette,"聊天外观与回复","背景、气泡颜色和短消息"){info="外观"}
                } }
                Surface(shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.surface) { Column {
                    SettingsRow(Icons.Default.Lock,"隐私与数据","隐私锁与本机数据保护"){privacy=true}
                    HorizontalDivider(Modifier.padding(start=60.dp))
                    SettingsRow(Icons.Default.EnhancedEncryption,"加密备份","口令保护、跨设备追加恢复"){backup=true}
                    HorizontalDivider(Modifier.padding(start=60.dp))
                    SettingsRow(Icons.Default.School,"新手引导与帮助","首次聊天、错误处理与脱敏诊断"){vm.openGuide()}
                    HorizontalDivider(Modifier.padding(start=60.dp))
                    SettingsRow(Icons.Default.Info,"关于星语","版本 ${com.ai.assistance.operit.BuildConfig.VERSION_NAME}"){info="关于"}
                } }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
    info?.let { topic->AlertDialog(onDismissRequest={info=null},title={Text(when(topic){"外观"->"聊天外观与回复";"隐私"->"隐私与数据";else->"关于星语"})},text={Text(when(topic) {
        "外观"->"打开一段聊天，点右上角 ··· 或输入框旁的调色盘，可以选择本机背景图片和气泡颜色。\n\n每个角色可选择连续短消息或整段回复，修改从下一次回复生效。"
        else->"星语 ${com.ai.assistance.operit.BuildConfig.VERSION_NAME}\n基于 Operit 修改，采用 LGPL-3.0-only。\n\n源码与构建说明随安装包提供。完整开源声明可在“模型连接”页面底部查看。"
    })},confirmButton={TextButton(onClick={info=null}){Text("知道了")}}) }
    if(proactive)ProactiveGlobalDialog(state,vm){proactive=false}
    if(dockEditor)DockSettingsDialog(dock,onDismiss={dockEditor=false},onSave={onSaveDock(it);dockEditor=false})
}

@Composable
private fun DockSettingsDialog(dock:CompanionDockConfig,onDismiss:()->Unit,onSave:(CompanionDockConfig)->Unit) {
    var order by remember(dock){mutableStateOf(dock.order)}
    var visible by remember(dock){mutableStateOf(dock.visible)}
    AlertDialog(
        onDismissRequest=onDismiss,
        title={Text("导航栏")},
        text={
            Column(Modifier.fillMaxWidth().heightIn(max=adaptiveDialogContentMaxHeight(480.dp)).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text("勾选要直接显示的页面，并调整排列顺序。隐藏的页面仍可从‘更多页面’打开。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                order.forEachIndexed { index,destination->
                    Row(Modifier.fillMaxWidth().heightIn(min=56.dp),verticalAlignment=Alignment.CenterVertically) {
                        Row(Modifier.weight(1f).toggleable(value=destination in visible,role=Role.Checkbox,onValueChange={checked->visible=if(checked)visible+destination else visible-destination}),verticalAlignment=Alignment.CenterVertically) {
                            Checkbox(checked=destination in visible,onCheckedChange=null)
                            Text(destination.title,Modifier.weight(1f))
                        }
                        IconButton(enabled=index>0,onClick={order=order.toMutableList().also { it.add(index-1,it.removeAt(index)) }},modifier=Modifier.size(48.dp).semantics {contentDescription="将${destination.title}页面上移"}) {Icon(Icons.Default.ArrowUpward,contentDescription=null)}
                        IconButton(enabled=index<order.lastIndex,onClick={order=order.toMutableList().also { it.add(index+1,it.removeAt(index)) }},modifier=Modifier.size(48.dp).semantics {contentDescription="将${destination.title}页面下移"}) {Icon(Icons.Default.ArrowDownward,contentDescription=null)}
                    }
                }
                TextButton(onClick={order=CompanionDockConfig.DEFAULT.order;visible=CompanionDockConfig.DEFAULT.visible}) {Text("恢复默认顺序")}
            }
        },
        confirmButton={Button(onClick={onSave(CompanionDockConfig(order,visible))}) {Text("保存")}},
        dismissButton={TextButton(onClick=onDismiss){Text("取消")}}
    )
}

@Composable
private fun PermissionCenterPage(
    state:CompanionState,
    vm:CompanionViewModel,
    onBack:()->Unit,
    onManageConnections:()->Unit,
    onManageImageGeneration:()->Unit,
    onManageSpeech:()->Unit,
    onManageExtensions:()->Unit
) {
    val operations by remember(vm){vm.recentToolOperationSummaries()}.collectAsStateWithLifecycle(emptyList())
    val context=LocalContext.current
    var settingsRevision by remember {mutableIntStateOf(0)}
    val appSettings=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){settingsRevision++}
    val lifecycleOwner=LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        // Android permission settings can be changed outside this page's Activity Result launch.
        val observer=LifecycleEventObserver { _,event->if(event==Lifecycle.Event.ON_RESUME)settingsRevision++}
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {lifecycleOwner.lifecycle.removeObserver(observer)}
    }
    val appNotificationsEnabled=remember(context,settingsRevision){NotificationManagerCompat.from(context).areNotificationsEnabled()}
    val reminderChannelEnabled=remember(context,settingsRevision){com.ai.assistance.operit.reminder.ReminderScheduler.notificationChannelEnabled(context)}
    val proactiveChannelEnabled=remember(context,settingsRevision){com.ai.assistance.operit.proactive.ProactiveScheduler.notificationChannelEnabled(context)}
    val notificationPermissionGranted=Build.VERSION.SDK_INT<33||remember(context,settingsRevision){
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED
    }
    val microphonePermissionGranted=remember(context,settingsRevision){
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED
    }
    val notificationsBlocked=!notificationPermissionGranted||!appNotificationsEnabled||reminderChannelEnabled==false||proactiveChannelEnabled==false
    BackHandler(onBack=onBack)
    Column(Modifier.fillMaxSize()) {
        EditorHeader("权限中心",onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal=20.dp),
            verticalArrangement=Arrangement.spacedBy(14.dp)
        ) {
            Text("这里只显示当前应用已保存的工具服务状态和角色授权。远程 MCP、模型、生图和语音地址使用 HTTPS，不能在地址中填写账号口令、查询参数或片段；API Key 单独加密保存。此页不联网探测服务。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("外部服务",Modifier.semantics {heading()},style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            if(state.extensions.isEmpty())Text("还没有配置 MCP 或 ToolPkg 服务。")
            state.extensions.forEach {server->
                val local=ToolPkgAddress.isLocal(server.endpoint)
                val source=if(local)"本机 ToolPkg · 本机计算" else "MCP · ${server.endpoint}"
                Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface) {
                    Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text(server.name,Modifier.semantics {heading()},style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold)
                        Text(source,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if(server.enabled)"服务开关已启用 · 未在此页联网检测" else "服务已停用",style=MaterialTheme.typography.bodySmall,
                            color=if(server.enabled)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Text("模型服务目的地",Modifier.semantics {heading()},style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            if(state.configs.isEmpty())Text("还没有保存模型服务。")
            state.configs.forEach {config->
                PermissionDestinationCard(config.name,"${config.provider} · ${config.model}",config.endpoint,"在你选择此连接并发送聊天，或使用已启用的主动消息时使用")
            }
            OutlinedButton(onClick=onManageConnections,modifier=Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Hub,null);Spacer(Modifier.width(8.dp));Text("管理模型服务")
            }

            Text("生图服务目的地",Modifier.semantics {heading()},style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            if(state.imageGenerationConfigs.isEmpty())Text("还没有保存生图服务。")
            state.imageGenerationConfigs.forEach {config->
                PermissionDestinationCard(config.name,config.model,config.endpoint,"仅在你确认生成图片时使用")
            }
            OutlinedButton(onClick=onManageImageGeneration,modifier=Modifier.fillMaxWidth()) {
                Icon(Icons.Default.AutoAwesome,null);Spacer(Modifier.width(8.dp));Text("管理生图服务")
            }

            Text("语音服务目的地",Modifier.semantics {heading()},style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            if(state.speechServiceConfigs.isEmpty())Text("还没有保存语音服务。")
            state.speechServiceConfigs.forEach {config->
                val use=if(config.kind==SpeechServices.TRANSCRIPTION)"仅在你确认录音转文字时使用" else "仅在你手动请求朗读时使用"
                PermissionDestinationCard(config.name,"${if(config.kind==SpeechServices.TRANSCRIPTION)"录音识别" else "语音合成"} · ${config.model}",config.endpoint,use)
            }
            OutlinedButton(onClick=onManageSpeech,modifier=Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Mic,null);Spacer(Modifier.width(8.dp));Text("管理语音服务")
            }

            Text("Android 系统权限",Modifier.semantics {heading()},style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            PermissionStatusCard(
                "通知",
                "日程提醒：${notificationChannelStatus(reminderChannelEnabled)}；主动消息：${notificationChannelStatus(proactiveChannelEnabled)}。两类通知都使用固定隐私文案。",
                when {
                    !notificationPermissionGranted->"尚未授予 Android 13+ 通知权限"
                    !appNotificationsEnabled->"应用通知在系统设置中关闭"
                    reminderChannelEnabled==false||proactiveChannelEnabled==false->"有通知渠道在系统设置中关闭"
                    else->"应用级通知开关已允许"
                },
                statusColor=if(notificationsBlocked)MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            OutlinedButton(
                onClick={appSettings.launch(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {putExtra(Settings.EXTRA_APP_PACKAGE,context.packageName)})},
                modifier=Modifier.fillMaxWidth()
            ) {Icon(Icons.Default.NotificationsActive,null);Spacer(Modifier.width(8.dp));Text("打开 Android 通知设置")}
            PermissionStatusCard(
                "麦克风",
                "只在你手动开始录音或连续语音时使用；识别上传前会再次确认目的地。",
                if(microphonePermissionGranted)"应用权限已允许" else "尚未授予录音权限",
                statusColor=if(microphonePermissionGranted)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            OutlinedButton(
                onClick={appSettings.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:${context.packageName}")))},
                modifier=Modifier.fillMaxWidth()
            ) {Icon(Icons.Default.Mic,null);Spacer(Modifier.width(8.dp));Text("打开 Android 应用权限设置")}
            PermissionStatusCard(
                "文件与媒体",
                "文档和图片通过系统选择器在使用时读取。加入本机曲库的音频和视频会保留对所选文件的读取授权，供之后播放；移除曲目或删除角色后，会回收不再使用的授权。应用未申请全盘存储或媒体库权限。",
                "按文件授权，不读取其他文件",
                statusColor=MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text("按角色授权",Modifier.semantics {heading()},style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            Text("关闭授权会立即撤销此工具权限，并停止该角色正在进行的生成。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(state.roles.isEmpty())Text("还没有角色。创建角色后，这里会显示它的工具授权状态。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            else state.roles.forEach {role->
                key(role.id,role.spaceId){RoleToolPermissionGroup(role,state.extensions,state.working,vm)}
            }

            Text("最近工具操作",Modifier.semantics {heading()},style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            if(operations.isEmpty())Text("还没有工具操作记录。")
            operations.forEach {operation->
                Column(Modifier.fillMaxWidth().padding(vertical=4.dp),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    Text("${operation.toolName} · ${when(operation.state){"COMPLETE"->"已完成";"WAITING_APPROVAL"->"待确认";"RUNNING"->"执行中";"CANCELLED"->"已取消";else->"未完成"}}",Modifier.semantics {heading()},fontWeight=FontWeight.Medium)
                    Text("${operation.roleName?:"角色记录不可用"} · ${operation.extensionName?:"服务已删除"} · ${DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(operation.createdAt))}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider()
                }
            }
            OutlinedButton(onClick=onManageExtensions,modifier=Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Extension,null);Spacer(Modifier.width(8.dp));Text("管理服务与完整权限")
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

internal data class RoleDeleteRequest(val role:RoleEntity,val conversations:Int)

@Composable internal fun RoleDeleteDialog(request:RoleDeleteRequest,working:Boolean,onDismiss:()->Unit,onConfirm:()->Unit) {
    // Keep one deletion explanation at every entry so a role removal cannot be confused with clearing its private chat.
    AlertDialog(onDismissRequest={if(!working)onDismiss()},title={Text("删除“${request.role.name}”？")},
        text={Column(Modifier.heightIn(max=adaptiveDialogContentMaxHeight(440.dp)).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("将停止该角色的生成，并永久删除其 ${request.conversations} 段私聊、角色设定、家园中的人物与赠礼、长期记忆、日记、日程、经期记录及共同活动。共享家园、田地、家具和背包保留。此操作无法撤销。")
            Text("该角色会从参与的群聊移除，过去的群发言保留。共享模型连接保留。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }},
        confirmButton={TextButton(enabled=!working,onClick=onConfirm){Text(if(working)"正在删除…"else"删除角色及其数据",color=MaterialTheme.colorScheme.error)}},
        dismissButton={TextButton(enabled=!working,onClick=onDismiss){Text("取消")}})
}

@Composable
private fun PermissionDestinationCard(name:String,details:String,endpoint:String,use:String) {
    Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(name,Modifier.semantics {heading()},style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold)
            Text(details,style=MaterialTheme.typography.bodySmall)
            Text(endpoint,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.primary)
            Text("已保存 · 未在此页检测连接 · $use",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PermissionStatusCard(title:String,details:String,status:String,statusColor:Color) {
    Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(title,Modifier.semantics {heading()},style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold)
            Text(status,style=MaterialTheme.typography.bodyMedium,color=statusColor)
            Text(details,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun notificationChannelStatus(enabled:Boolean?)=when(enabled) {
    true->"系统设置已允许"
    false->"系统设置已关闭"
    null->"尚未创建（首次使用时建立）"
}

@Composable
private fun RoleToolPermissionGroup(
    role:RoleEntity,
    extensions:List<McpExtension>,
    working:Boolean,
    vm:CompanionViewModel
) {
    val grants by remember(role.id,role.spaceId){vm.roleToolGrants(role.scope())}.collectAsStateWithLifecycle(emptyList())
    val activeGrants=grants.filter {it.enabled}
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(role.name,Modifier.semantics {heading()},style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold)
        if(activeGrants.isEmpty()) {
            Text("尚未授权工具。到“管理服务与完整权限”中选择服务和工具。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        } else activeGrants.forEach {grant->
            val server=extensions.firstOrNull {it.id==grant.extensionId}
            Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surface) {
                Row(Modifier.padding(start=14.dp,end=8.dp,top=8.dp,bottom=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                        Text(grant.toolName,style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Medium)
                        Text("${server?.name?:"服务已删除"} · ${if(server?.let {ToolPkgAddress.isLocal(it.endpoint)}==true)"仅本机运行" else "向已配置的 MCP 服务发送"}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(when {
                            server==null->"授权记录仍在，但服务不可用"
                            !server.enabled->"已授权 · 服务当前停用"
                            role.isRaw()->"已授权 · 原味模式下不会提供给模型"
                            else->"已授权 · 仅限此角色空间"
                        },style=MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked=grant.enabled,
                        onCheckedChange={vm.revokeTool(role.scope(),grant.extensionId,grant.toolName)},
                        enabled=!working,
                        modifier=Modifier.semantics {contentDescription="撤销${role.name}使用${grant.toolName}的授权"}
                    )
                }
            }
        }
    }
}

@Composable private fun SettingsRow(icon: ImageVector,title: String,caption: String,onClick:()->Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(horizontal=16.dp,vertical=18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
        Icon(icon,null,tint=MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(title,style=MaterialTheme.typography.bodyLarge)
            Text(caption,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Default.ChevronRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun EditorHeader(title: String,onBack:()->Unit) {
    Row(Modifier.fillMaxWidth().padding(top=8.dp,bottom=16.dp),verticalAlignment=Alignment.CenterVertically) {
        IconButton(onClick=onBack){Icon(Icons.AutoMirrored.Filled.ArrowBack,"返回")}
        Text(title,Modifier.semantics {heading()},style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
    }
}
