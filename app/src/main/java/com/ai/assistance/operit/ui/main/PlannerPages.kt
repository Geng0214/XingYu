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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import java.time.*
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun SchedulePage(state:CompanionState,vm:CompanionViewModel,onOpenChats:()->Unit,onManageRoles:()->Unit) {
    var selectedRoleId by rememberSaveable {mutableStateOf(state.scope?.roleId.orEmpty())}
    var selectedSpaceId by rememberSaveable {mutableStateOf(state.scope?.spaceId.orEmpty())}
    LaunchedEffect(state.scope,state.roles) {
        state.scope?.let {scope->state.roles.firstOrNull {it.scope()==scope}?.let {role->selectedRoleId=role.id;selectedSpaceId=role.spaceId}}
        if(state.roles.none {it.id==selectedRoleId&&it.spaceId==selectedSpaceId}) {
            state.roles.firstOrNull()?.let {role->selectedRoleId=role.id;selectedSpaceId=role.spaceId}
        }
    }
    val role=state.roles.firstOrNull {it.id==selectedRoleId&&it.spaceId==selectedSpaceId}?:state.roles.firstOrNull()
    if(role==null) {
        Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.spacedBy(14.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.Event,null,Modifier.size(48.dp),tint=MaterialTheme.colorScheme.primary)
            Text("先创建一个角色",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold)
            Text("每个角色的日程、待办和纪念日分别保存。创建角色后，就可以在这里管理安排。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick=onManageRoles){Text("管理角色")}
            TextButton(onClick=onOpenChats){Text("返回聊天")}
            Spacer(Modifier.weight(1f))
        }
    } else {
        PlannerDialog(role,state,vm,asPage=true,onSelectRole={selectedRoleId=it.id;selectedSpaceId=it.spaceId},onDismiss=onOpenChats)
    }
}

@Composable internal fun PlannerDialog(
    role:RoleEntity,
    state:CompanionState,
    vm:CompanionViewModel,
    initialEntryId:String?=null,
    asPage:Boolean=false,
    onSelectRole:(RoleEntity)->Unit={},
    onDismiss:()->Unit
) {
    val scope=role.scope();val data by remember(scope){vm.planner(scope)}.collectAsStateWithLifecycle<PlannerData?>(null)
    var tab by remember(scope){mutableIntStateOf(0)}
    var editingPlanner by remember(scope){mutableStateOf<PlannerEntry?>(null)}
    var creatingPlanner by remember(scope){mutableStateOf(false)}
    var editingCycle by remember(scope){mutableStateOf<CycleRecord?>(null)}
    var creatingCycle by remember(scope){mutableStateOf(false)}
    var informationOpen by rememberSaveable(scope){mutableStateOf(false)}
    var selectedDay by rememberSaveable(scope){mutableLongStateOf(LocalDate.now().toEpochDay())}
    var monthExpanded by rememberSaveable(scope){mutableStateOf(false)}
    var listView by rememberSaveable(scope){mutableStateOf(false)}
    val zone=ZoneId.systemDefault()
    var today by remember {mutableStateOf(LocalDate.now(zone))}
    LaunchedEffect(zone) {
        // Refresh the date while this page stays open across midnight, using the actual local day boundary.
        while(true) {
            today=LocalDate.now(zone)
            val next=today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            kotlinx.coroutines.delay(maxOf(1L,next-System.currentTimeMillis()))
        }
    }
    val selectedDate=LocalDate.ofEpochDay(selectedDay)
    var deletingPlanner by remember(scope){mutableStateOf<PlannerEntry?>(null)}
    var deletingCycle by remember(scope){mutableStateOf<CycleRecord?>(null)}
    var focusedEntry by remember(scope,initialEntryId){mutableStateOf<PlannerEntry?>(null)}
    var initialEntryHandled by remember(scope,initialEntryId){mutableStateOf(initialEntryId==null)}
    var initialEntryUnavailable by remember(scope,initialEntryId){mutableStateOf(false)}
    var roleMenuExpanded by remember {mutableStateOf(false)}
    LaunchedEffect(initialEntryId,data?.entries) {
        if(!initialEntryHandled&&initialEntryId!=null&&data!=null) {
            focusedEntry=data?.entries?.firstOrNull {it.id==initialEntryId}
            initialEntryUnavailable=focusedEntry==null
            initialEntryHandled=true
        }
    }
    val plannerContent:@Composable ()->Unit={
        Surface(Modifier.fillMaxSize()) {Column(if(asPage)Modifier.fillMaxSize() else Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(8.dp),verticalAlignment=Alignment.CenterVertically) {
                IconButton(enabled=!state.working,onClick=onDismiss){Icon(Icons.AutoMirrored.Filled.ArrowBack,"返回")}
                Column(Modifier.weight(1f)) {
                    Text(if(asPage)"日程" else "日程与记录",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
                    if(asPage&&state.roles.size>1) {
                        Box {
                            TextButton(onClick={roleMenuExpanded=true},modifier=Modifier.heightIn(min=48.dp),contentPadding=PaddingValues(horizontal=0.dp,vertical=0.dp)) {
                                Text(role.name,maxLines=1,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodySmall)
                                Icon(Icons.Default.ExpandMore,null,Modifier.size(18.dp))
                            }
                            DropdownMenu(expanded=roleMenuExpanded,onDismissRequest={roleMenuExpanded=false}) {
                                state.roles.forEach {candidate->DropdownMenuItem(
                                    text={Text(candidate.name,maxLines=1,overflow=TextOverflow.Ellipsis)},
                                    onClick={roleMenuExpanded=false;onSelectRole(candidate)}
                                )}
                            }
                        }
                    } else Text(role.name,style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                }
                IconButton(onClick={informationOpen=true},enabled=data!=null&&data?.error==null){Icon(Icons.Default.Info,"分享与提醒")}
            }
            state.plannerError?.let {Text(it,Modifier.padding(horizontal=24.dp,vertical=6.dp),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
            ScrollableTabRow(selectedTabIndex=tab,edgePadding=16.dp,containerColor=MaterialTheme.colorScheme.surface) {
                listOf("日程","待办","纪念日","经期").forEachIndexed {index,label->
                    Tab(selected=tab==index,onClick={tab=index;listView=index==1},text={Text(label)})
                }
            }
            val loaded=data
            when {
                loaded==null->Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
                loaded.error!=null->Text(loaded.error,Modifier.padding(24.dp),color=MaterialTheme.colorScheme.error)
                else->Box(Modifier.weight(1f)) {
                    val add:()->Unit={if(!state.working){if(tab==3)creatingCycle=true else creatingPlanner=true}}
                    PlannerBoard(loaded,tab,selectedDate,today,zone,listView,monthExpanded,state.working,
                        onDate={selectedDay=it.toEpochDay()},onExpand={monthExpanded=!monthExpanded},onList={listView=!listView},onAdd=add,
                        onOpen={focusedEntry=it},onEdit={editingPlanner=it},onDelete={deletingPlanner=it},
                        onCompleted={entry,checked->vm.setPlannerCompleted(scope,entry,checked)},
                        onEditCycle={editingCycle=it},onDeleteCycle={deletingCycle=it})
                    FloatingActionButton(onClick=add,modifier=Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
                        Icon(Icons.Default.Add,"添加${listOf("日程","待办","纪念日","经期记录")[tab]}")
                    }
                }
            }
        }}
    }
    if(asPage) plannerContent() else Dialog(onDismissRequest={if(!state.working)onDismiss()},properties=DialogProperties(usePlatformDefaultWidth=false)){plannerContent()}
    data?.takeIf {it.error==null}?.let {loaded->if(informationOpen)PlannerInformation(role,loaded){informationOpen=false}}
    if(initialEntryHandled&&initialEntryUnavailable) AlertDialog(
        onDismissRequest=onDismiss,
        title={Text(if(data?.error!=null)"日程读取失败"else"记录暂不可用")},
        text={Text(data?.error?:"这条记录可能已删除，或不属于当前角色空间。")},
        confirmButton={TextButton(onClick=onDismiss){Text("关闭")}}
    )
    focusedEntry?.let {entry->
        val zone=ZoneId.of(entry.zoneId)
        val start=Instant.ofEpochMilli(entry.startsAt).atZone(zone).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
        AlertDialog(
            onDismissRequest={focusedEntry=null},
            title={Text(entry.title)},
            text={Column(Modifier.heightIn(max=adaptiveDialogContentMaxHeight(480.dp)).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("${kindLabel(entry.kind)} · $start · ${entry.zoneId}")
                entry.endsAt?.let {Text("结束：${Instant.ofEpochMilli(it).atZone(zone).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))}")}
                if(entry.note.isNotBlank())Text(entry.note)
                Text(if(entry.completedAt!=null)"已完成" else if(entry.shareWithRole)"已允许角色知道" else "仅自己可见",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }},
            confirmButton={TextButton(onClick={editingPlanner=entry;focusedEntry=null}){Text("编辑")}},
            dismissButton={TextButton(onClick={focusedEntry=null}){Text("关闭")}}
        )
    }
    val createKind=listOf("event","task","anniversary")[tab.coerceAtMost(2)]
    if(creatingPlanner||editingPlanner!=null)PlannerEditor(scope,editingPlanner,createKind,selectedDate,state,vm){creatingPlanner=false;editingPlanner=null}
    if(creatingCycle||editingCycle!=null)CycleEditor(scope,editingCycle,state,vm){creatingCycle=false;editingCycle=null}
    deletingPlanner?.let {entry->AlertDialog(onDismissRequest={deletingPlanner=null},title={Text("删除记录？")},text={Text("将删除这条记录并取消对应提醒，无法撤销。")},confirmButton={TextButton(onClick={deletingPlanner=null;vm.deletePlanner(scope,entry)}){Text("删除")}},dismissButton={TextButton(onClick={deletingPlanner=null}){Text("取消")}})}
    deletingCycle?.let {record->AlertDialog(onDismissRequest={deletingCycle=null},title={Text("删除经期记录？")},text={Text("这会从当前角色的加密空间永久删除该记录。")},confirmButton={TextButton(onClick={deletingCycle=null;vm.deleteCycle(scope,record)}){Text("删除")}},dismissButton={TextButton(onClick={deletingCycle=null}){Text("取消")}})}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun PlannerEditor(scope:RoleAccessScope,existing:PlannerEntry?,initialKind:String,selectedDate:LocalDate,state:CompanionState,vm:CompanionViewModel,onDismiss:()->Unit) {
    val context=LocalContext.current;val zone=remember(existing){ZoneId.of(existing?.zoneId?:ZoneId.systemDefault().id)}
    val original=remember(existing,selectedDate){existing?.let {Instant.ofEpochMilli(it.startsAt).atZone(zone)}?:selectedDate.atTime(9,0).atZone(zone)}
    var title by remember(existing){mutableStateOf(existing?.title.orEmpty())};var note by remember(existing){mutableStateOf(existing?.note.orEmpty())}
    var date by remember(existing){mutableStateOf(original.toLocalDate().toString())};var time by remember(existing){mutableStateOf(original.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")))}
    var endEnabled by remember(existing){mutableStateOf(existing?.endsAt!=null)}
    val originalEnd=remember(existing){existing?.endsAt?.let {Instant.ofEpochMilli(it).atZone(zone)}?:original.plusHours(1)}
    var endDate by remember(existing){mutableStateOf(originalEnd.toLocalDate().toString())};var endTime by remember(existing){mutableStateOf(originalEnd.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")))}
    var share by remember(existing){mutableStateOf(existing?.shareWithRole==true)};var reminder by remember(existing){mutableStateOf(existing?.reminderMinutesBefore)}
    var localError by remember(existing){mutableStateOf<String?>(null)}
    var notificationGranted by remember {mutableStateOf(Build.VERSION.SDK_INT<33||context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)==PackageManager.PERMISSION_GRANTED)}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){notificationGranted=it}
    val kind=existing?.kind?:initialKind
    AlertDialog(onDismissRequest={if(!state.working)onDismiss()},title={Text(if(existing==null)"新建${kindLabel(kind)}" else "编辑${kindLabel(kind)}")},text={Column(Modifier.heightIn(max=adaptiveDialogContentMaxHeight(560.dp)).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(title,{if(it.length<=PlannerRecords.TITLE_LIMIT)title=it},Modifier.fillMaxWidth(),label={Text("标题")},singleLine=true)
        OutlinedTextField(note,{if(it.length<=PlannerRecords.NOTE_LIMIT)note=it},Modifier.fillMaxWidth(),label={Text("备注（可选）")},minLines=2,maxLines=5)
        PlannerDateTimeFields(date,{date=it},if(kind=="task")"截止日期" else "日期",time,{time=it})
        Row(verticalAlignment=Alignment.CenterVertically){Checkbox(endEnabled,{endEnabled=it});Text("设置结束时间")}
        if(endEnabled)PlannerDateTimeFields(endDate,{endDate=it},"结束日期",endTime,{endTime=it})
        Text("时区：${zone.id}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text("提醒",fontWeight=FontWeight.SemiBold);FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){listOf(null to "关闭",0L to "到时",10L to "10 分钟",60L to "1 小时").forEach {(value,label)->FilterChip(reminder==value,{reminder=value;if(value!=null&&Build.VERSION.SDK_INT>=33&&!notificationGranted)permission.launch(Manifest.permission.POST_NOTIFICATIONS)},label={Text(label)})}}
        if(reminder!=null&&!notificationGranted)Text("系统通知权限未开启，记录会保存，但提醒无法显示。",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
        Row(verticalAlignment=Alignment.CenterVertically){Switch(share,{share=it});Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text("允许角色在聊天中知道");Text("从之后的新请求生效，默认关闭。",style=MaterialTheme.typography.bodySmall)}}
        localError?.let {Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
    }},confirmButton={TextButton(enabled=!state.working,onClick={
        try {
            val start=ZonedDateTime.of(LocalDate.parse(date),LocalTime.parse(time),zone).toInstant().toEpochMilli();val end=if(endEnabled)ZonedDateTime.of(LocalDate.parse(endDate),LocalTime.parse(endTime),zone).toInstant().toEpochMilli() else null
            val probe=PlannerEntry(existing?.id?:"preview",scope.roleId,scope.spaceId,kind,title.trim(),note,start,end,zone.id,existing?.completedAt,reminder,share,existing?.version?:1,existing?.createdAt?:0,existing?.updatedAt?:0);PlannerRecords.validatePlanner(probe)
            localError=null;vm.savePlanner(scope,existing,kind,title.trim(),note,start,end,zone.id,existing?.completedAt,reminder,share,onDismiss)
        }catch(_:Exception){localError=errorText("PLANNER_INPUT")}
    }){Text("保存")}},dismissButton={TextButton(enabled=!state.working,onClick=onDismiss){Text("取消")}})
}

@Composable private fun PlannerDateTimeFields(
    date: String,
    onDateChange: (String) -> Unit,
    dateLabel: String,
    time: String,
    onTimeChange: (String) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // 放大字号或弹窗较窄时改为竖排，避免日期标签和输入值被横向挤压。
        val stackFields = maxWidth < 360.dp || LocalDensity.current.fontScale >= 1.2f
        if (stackFields) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PlannerDateField(date,dateLabel,onDateChange,Modifier.fillMaxWidth())
                PlannerTimeField(time,onTimeChange,Modifier.fillMaxWidth())
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlannerDateField(date,dateLabel,onDateChange,Modifier.weight(1f))
                PlannerTimeField(time,onTimeChange,Modifier.width(132.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun CycleEditor(scope:RoleAccessScope,existing:CycleRecord?,state:CompanionState,vm:CompanionViewModel,onDismiss:()->Unit) {
    var start by remember(existing){mutableStateOf(existing?.startedOn?.let(LocalDate::ofEpochDay)?.toString()?:LocalDate.now().toString())}
    var endEnabled by remember(existing){mutableStateOf(existing?.endedOn!=null)};var end by remember(existing){mutableStateOf(existing?.endedOn?.let(LocalDate::ofEpochDay)?.toString()?:start)}
    var flow by remember(existing){mutableStateOf(existing?.flowLevel?:"unspecified")};var note by remember(existing){mutableStateOf(existing?.note.orEmpty())};var share by remember(existing){mutableStateOf(existing?.shareWithRole==true)};var localError by remember(existing){mutableStateOf<String?>(null)}
    AlertDialog(onDismissRequest={if(!state.working)onDismiss()},title={Text(if(existing==null)"新建经期记录" else "编辑经期记录")},text={Column(Modifier.heightIn(max=adaptiveDialogContentMaxHeight(560.dp)).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("这是敏感健康记录。默认仅自己可见；应用不预测周期、排卵或健康状态。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        PlannerDateField(start,"开始日期",{start=it},Modifier.fillMaxWidth())
        Row(verticalAlignment=Alignment.CenterVertically){Checkbox(endEnabled,{endEnabled=it});Text("记录结束日期")};if(endEnabled)PlannerDateField(end,"结束日期",{end=it},Modifier.fillMaxWidth())
        Text("流量",fontWeight=FontWeight.SemiBold);FlowRow(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){listOf("unspecified" to "未填写","light" to "少","medium" to "中","heavy" to "多").forEach {(value,label)->FilterChip(flow==value,{flow=value},label={Text(label)})}}
        OutlinedTextField(note,{if(it.length<=PlannerRecords.NOTE_LIMIT)note=it},Modifier.fillMaxWidth(),label={Text("备注（可选）")},minLines=2,maxLines=5)
        Row(verticalAlignment=Alignment.CenterVertically){Switch(share,{share=it});Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text("允许角色在聊天中知道");Text("会把这条敏感记录加入之后的角色模式请求。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)}}
        localError?.let {Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
    }},confirmButton={TextButton(enabled=!state.working,onClick={try {val s=LocalDate.parse(start).toEpochDay();val e=if(endEnabled)LocalDate.parse(end).toEpochDay() else null;PlannerRecords.validateCycle(CycleRecord(existing?.id?:"preview",scope.roleId,scope.spaceId,s,e,flow,note,share,existing?.version?:1,existing?.createdAt?:0,existing?.updatedAt?:0));localError=null;vm.saveCycle(scope,existing,s,e,flow,note,share,onDismiss)}catch(_:Exception){localError=errorText("PLANNER_INPUT")}}){Text("保存")}},dismissButton={TextButton(enabled=!state.working,onClick=onDismiss){Text("取消")}})
}

private fun kindLabel(kind:String)=when(kind){"task"->"待办";"anniversary"->"纪念日";else->"日程"}
