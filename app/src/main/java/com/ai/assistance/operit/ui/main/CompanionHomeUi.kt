package com.ai.assistance.operit.ui.main

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.db.HomeCommandRow
import com.ai.assistance.operit.data.model.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

private enum class CompanionPanel { CARE, JOURNAL }

@Composable
internal fun ColumnScope.CompanionHomeUi(
    home: HomeState,
    role: RoleEntity?,
    residents: List<RoleEntity>,
    vm: CompanionViewModel,
    ui: HomeUi,
    now: Long,
    enabled: Boolean,
    initialCommandId: String?,
    onRole: (String) -> Unit,
    onOpenYard: () -> Unit
) {
    val pet=home.pet
    val preview=pet?.let {HomePetRules.preview(it,now)}
    var panelName by rememberSaveable(home.scope.homeId) {mutableStateOf<String?>(null)}
    var adoptName by rememberSaveable(home.scope.homeId) {mutableStateOf("团子")}
    val panel=panelName?.let(CompanionPanel::valueOf)
    val petRecords=remember(ui.ledger.records) {ui.ledger.records.mapNotNull {row->
        runCatching {row.action()}.getOrNull()?.let {action->
            if(action is HomeAction.AdoptPet||action is HomeAction.CarePet||action is HomeAction.HandoffPet||action is HomeAction.ResolvePetHandoff)row to action else null
        }
    }}
    val canAct=enabled&&ui.pending==null
    LaunchedEffect(initialCommandId) {if(initialCommandId!=null)panelName=CompanionPanel.JOURNAL.name}
    LaunchedEffect(pet?.handoffRoleId,now/60_000) {
        if(pet!=null&&pet.handoffRoleId!=null&&HomePetRules.handoffDue(pet,now))vm.settlePetIfDue(ui.token)
    }
    BackHandler(enabled=panel!=null){panelName=null}
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
        val wide=maxWidth>=600.dp&&maxWidth>maxHeight
        val panelMaxHeight=if(wide)maxHeight-96.dp else maxHeight*.84f
        val journalMaxHeight=if(wide)maxHeight-96.dp else maxHeight*.9f
        RainWindowScene(pet,role?.name,now,Modifier.fillMaxSize().clipToBounds())
        SceneTopBar(home,pet,role,now,onJournal={panelName=CompanionPanel.JOURNAL.name},onYard=onOpenYard)
        if(role!=null&&panel==null)SceneSpeech(role,pet,preview,now,Modifier.align(Alignment.CenterEnd).padding(end=14.dp).offset(y=(-76).dp))
        if(!homeClockValid(home,now)) {
            Text("请校准手机时间",Modifier.align(Alignment.TopCenter).padding(top=76.dp),color=CompanionSceneColors.rose,style=MaterialTheme.typography.bodySmall)
        }
        val pendingError=ui.pending?.error
        if(pendingError!=null&&panel==null) {
            PendingBanner(pendingError,onRetry={vm.retryHome(ui.token)},onAbandon={vm.abandonHomeRetry(ui.token)},
                Modifier.align(Alignment.TopCenter).padding(top=76.dp))
        }
        if(pet!=null&&panel==null) {
            CareFab(pet.name,canAct,onClick={panelName=CompanionPanel.CARE.name},Modifier.align(Alignment.BottomCenter).padding(bottom=16.dp))
        }
        if(pet==null&&panel==null) {
            AdoptionCard(role,ui,adoptName,{if(it.length<=HomePetRules.NAME_LIMIT)adoptName=it},
                onAdopt={if(role!=null&&canAct)vm.homeAction(ui.token,HomeAction.AdoptPet(adoptName))},
                onRetry={vm.retryHome(ui.token)},onAbandon={vm.abandonHomeRetry(ui.token)},enabled=canAct,
                modifier=Modifier.align(Alignment.Center).padding(horizontal=24.dp).heightIn(max=maxHeight-32.dp))
        }
        if(panel==CompanionPanel.CARE&&pet!=null) {
            pet?.let {current->
                CareSheet(current,role,residents,petRecords,ui,now,canAct,wide,
                    {onRole(it)}, {vm.homeAction(ui.token,it)}, {vm.retryHome(ui.token)}, {vm.abandonHomeRetry(ui.token)},
                    {panelName=CompanionPanel.JOURNAL.name}, {panelName=null},
                    if(wide)Modifier.align(Alignment.BottomEnd).padding(12.dp).width(360.dp).heightIn(max=panelMaxHeight)
                    else Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max=panelMaxHeight))
            }
        }
        if(panel==CompanionPanel.JOURNAL) {
            JournalSheet(home,residents,petRecords,initialCommandId,now,wide,
                {panelName=null},
                if(wide)Modifier.align(Alignment.BottomEnd).padding(12.dp).width(360.dp).heightIn(max=journalMaxHeight)
                else Modifier.align(Alignment.BottomCenter).fillMaxWidth().heightIn(max=journalMaxHeight))
        }
    }
}

@Composable
private fun SceneTopBar(home:HomeState,pet:HomePet?,role:RoleEntity?,now:Long,onJournal:()->Unit,onYard:()->Unit) {
    val timeFormat=remember {SimpleDateFormat("HH:mm",Locale.getDefault())}
    val dateFormat=remember {SimpleDateFormat("M月d日",Locale.getDefault())}
    val time=remember(now/60_000) {timeFormat.format(Date(now))}
    val date=remember(now/60_000) {dateFormat.format(Date(now))}
    val large=LocalDensity.current.fontScale>=1.5f
    val start=pet?.adoptedAt?:home.createdAt
    val days=(((now.coerceAtLeast(start)-start)/86_400_000L)+1).coerceAtLeast(1L).toInt()
    Row(Modifier.fillMaxWidth().padding(start=18.dp,top=10.dp,end=10.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text("$time  $date",color=CompanionSceneColors.muted,style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
            Text("小雨",color=CompanionSceneColors.moonlight,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Medium)
            Text("共处第 $days 天",color=CompanionSceneColors.muted,style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            CompanionAvatar("你",CompanionSceneColors.sage,25.dp)
            CompanionAvatar(role?.name?:"伴",CompanionSceneColors.rose,25.dp)
            if(large)RoundIconButton(Icons.Default.Yard,"院子",onYard)else YardButton(onYard)
            RoundIconButton(Icons.AutoMirrored.Filled.MenuBook,"共同手账",onJournal)
        }
    }
}

@Composable
private fun YardButton(onClick:()->Unit) {
    Surface(onClick=onClick,shape=CircleShape,color=Color(0x66171C1D),border=BorderStroke(1.dp,Color.White.copy(alpha=.14f))) {
        Row(Modifier.padding(horizontal=10.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Default.Yard,null,tint=CompanionSceneColors.moonlight,modifier=Modifier.size(16.dp))
            Text("院子",color=CompanionSceneColors.moonlight,style=MaterialTheme.typography.labelSmall,maxLines=1)
        }
    }
}

@Composable
private fun RoundIconButton(icon:ImageVector,label:String,onClick:()->Unit) {
    Surface(onClick=onClick,shape=CircleShape,color=Color(0x66171C1D),border=BorderStroke(1.dp,Color.White.copy(alpha=.14f))) {
        Box(Modifier.size(40.dp),contentAlignment=Alignment.Center) {
            Icon(icon,label,tint=CompanionSceneColors.moonlight,modifier=Modifier.size(19.dp))
        }
    }
}

@Composable
private fun SceneSpeech(role:RoleEntity,pet:HomePet?,preview:HomePet?,now:Long,modifier:Modifier) {
    val line=when {
        pet==null->"我们养一只猫吧？"
        pet.handoffRoleId!=null->"今晚我来照看它。"
        preview!=null&&preview.hunger<=35->"它刚才一直看着碗。"
        preview!=null&&preview.mood<=35->"它把球叼到你脚边了。"
        HomePetRules.stage(pet,now)==HomePetStage.KITTEN->"它今天一直趴在窗边等你。"
        else->"它靠在我旁边睡着了。"
    }
    Surface(modifier.widthIn(max=200.dp),shape=RoundedCornerShape(14.dp),color=Color(0xCC161B1C),border=BorderStroke(1.dp,Color.White.copy(alpha=.12f))) {
        Column(Modifier.padding(horizontal=12.dp,vertical=9.dp),verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Text(line,color=CompanionSceneColors.moonlight,style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=TextOverflow.Ellipsis)
            Text(role.name,color=CompanionSceneColors.muted,style=MaterialTheme.typography.labelSmall,maxLines=1)
        }
    }
}

@Composable
private fun CareFab(name:String,enabled:Boolean,onClick:()->Unit,modifier:Modifier) {
    Surface(onClick=onClick,enabled=enabled,modifier=modifier,shape=RoundedCornerShape(999.dp),color=Color(0xE6171C1D),
        border=BorderStroke(1.dp,Color.White.copy(alpha=.16f)),shadowElevation=8.dp) {
        Row(Modifier.padding(horizontal=20.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.Pets,null,tint=CompanionSceneColors.gold,modifier=Modifier.size(18.dp))
            Text("照顾$name",color=CompanionSceneColors.moonlight,style=MaterialTheme.typography.labelLarge,maxLines=1)
            Box(Modifier.size(6.dp).clip(CircleShape).background(CompanionSceneColors.gold))
        }
    }
}

@Composable
private fun AdoptionCard(role:RoleEntity?,ui:HomeUi,name:String,onName:(String)->Unit,onAdopt:()->Unit,
    onRetry:()->Unit,onAbandon:()->Unit,enabled:Boolean,modifier:Modifier) {
    val paper=Color(0xFFF4EDE1);val ink=Color(0xFF26221E);val muted=Color(0xFF6F675C)
    Surface(modifier.widthIn(max=320.dp),shape=RoundedCornerShape(24.dp),color=paper,contentColor=ink,shadowElevation=16.dp) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                PetAvatar(60.dp)
                Column(Modifier.weight(1f)) {
                    Text("一起养一只猫",color=ink,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                    Text("它会住在窗边，和你们一起生活。",color=muted,style=MaterialTheme.typography.bodySmall)
                }
            }
            OutlinedTextField(name,onName,Modifier.fillMaxWidth(),singleLine=true,enabled=enabled,label={Text("名字")},
                colors=OutlinedTextFieldDefaults.colors(focusedTextColor=ink,unfocusedTextColor=ink,
                    cursorColor=CompanionSceneColors.gold,focusedBorderColor=CompanionSceneColors.gold,unfocusedBorderColor=Color(0xFFBFB3A2),
                    focusedLabelColor=CompanionSceneColors.gold,unfocusedLabelColor=muted))
            Text("最多 ${HomePetRules.NAME_LIMIT} 个字",color=muted,style=MaterialTheme.typography.labelSmall)
            Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("团子","小满","雨点").forEach {suggested->
                    Surface(onClick={onName(suggested)},enabled=enabled,shape=CircleShape,color=Color(0xFFE9DFCF)) {
                        Text(suggested,Modifier.padding(horizontal=10.dp,vertical=6.dp),color=ink,style=MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Text(if(role==null)"先让一个角色搬进来，再一起领养。"else"领养后，${role.name}会和你一起照顾它。",
                color=if(role==null)CompanionSceneColors.rose else muted,style=MaterialTheme.typography.bodySmall)
            Button(onClick=onAdopt,enabled=enabled&&role!=null&&name.isNotBlank(),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),
                shape=RoundedCornerShape(14.dp),colors=ButtonDefaults.buttonColors(containerColor=ink,contentColor=paper,disabledContainerColor=Color(0xFFB9AE9C),disabledContentColor=Color(0xFFF4EDE1))) {
                Icon(Icons.Default.Pets,null,modifier=Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if(role==null)"需要一位同伴" else "和${role.name}一起领养",maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            val adoptError=ui.pending?.error
            if(adoptError!=null) {
                Text(adoptError,color=CompanionSceneColors.rose,style=MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick=onRetry,colors=ButtonDefaults.textButtonColors(contentColor=ink)){Text("重试")}
                    TextButton(onClick=onAbandon,colors=ButtonDefaults.textButtonColors(contentColor=muted)){Text("放弃")}
                }
            }
        }
    }
}

@Composable
private fun CareSheet(pet:HomePet,role:RoleEntity?,residents:List<RoleEntity>,records:List<Pair<HomeCommandRow,HomeAction>>,
    ui:HomeUi,now:Long,enabled:Boolean,wide:Boolean,onRole:(String)->Unit,onAction:(HomeAction)->Unit,
    onRetry:()->Unit,onAbandon:()->Unit,onJournal:()->Unit,onClose:()->Unit,modifier:Modifier) {
    val preview=HomePetRules.preview(pet,now)
    val recommended=HomePetRules.careChoice(pet,now)
    val large=LocalDensity.current.fontScale>=1.5f
    val stage=HomePetRules.stage(pet,now)
    val days=(((now.coerceAtLeast(pet.adoptedAt)-pet.adoptedAt)/86_400_000L)+1).coerceAtLeast(1L).toInt()
    val delegated=pet.handoffRoleId
    val delegatedName=roleNameOf(delegated,residents)
    Surface(modifier,shape=RoundedCornerShape(topStart=28.dp,topEnd=28.dp,bottomStart=if(wide)28.dp else 0.dp,bottomEnd=if(wide)28.dp else 0.dp),
        color=CompanionSceneColors.panel.copy(alpha=.97f),border=BorderStroke(1.dp,Color.White.copy(alpha=.1f)),shadowElevation=12.dp) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Box(Modifier.width(38.dp).height(4.dp).clip(CircleShape).background(CompanionSceneColors.moonlight.copy(alpha=.22f)).align(Alignment.CenterHorizontally))
            Row(verticalAlignment=Alignment.CenterVertically) {
                PetAvatar(46.dp);Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(pet.name,color=CompanionSceneColors.moonlight,style=MaterialTheme.typography.headlineSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                    Text("${stage.title}，到家第 $days 天",color=CompanionSceneColors.muted,style=MaterialTheme.typography.labelSmall,maxLines=1)
                }
                IconButton(onClick=onClose,modifier=Modifier.size(40.dp)){Icon(Icons.Default.Close,"收起照顾面板",tint=CompanionSceneColors.muted)}
            }
            if(residents.size>1)RolePicker(residents,role,onRole)
            RoleNote(role,residents,pet,preview,now)
            Column(verticalArrangement=Arrangement.spacedBy(7.dp)) {
                NeedRow(HomePetNeed.HUNGER,preview.hunger)
                NeedRow(HomePetNeed.MOOD,preview.mood)
                NeedRow(HomePetNeed.ENERGY,preview.energy)
                NeedRow(HomePetNeed.HYGIENE,preview.hygiene)
            }
            if(large) {
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {CareTile(HomePetCare.FEED,recommended,enabled,onAction,Modifier.weight(1f));CareTile(HomePetCare.PLAY,recommended,enabled,onAction,Modifier.weight(1f))}
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {CareTile(HomePetCare.BRUSH,recommended,enabled,onAction,Modifier.weight(1f));CareTile(HomePetCare.SLEEP,recommended,enabled,onAction,Modifier.weight(1f))}
                }
            } else {
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    HomePetCare.entries.forEach {care->CareTile(care,recommended,enabled,onAction,Modifier.weight(1f))}
                }
            }
            Button(onClick={role?.let {onAction(HomeAction.HandoffPet(it.id))}},enabled=enabled&&role!=null&&delegated==null,
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),shape=RoundedCornerShape(14.dp),
                colors=ButtonDefaults.buttonColors(containerColor=CompanionSceneColors.gold,contentColor=Color(0xFF211D17),
                    disabledContainerColor=CompanionSceneColors.panelRaised,disabledContentColor=CompanionSceneColors.muted)) {
                Icon(Icons.Default.Bedtime,null,modifier=Modifier.size(18.dp));Spacer(Modifier.width(8.dp))
                Text(if(delegated!=null)"已交给$delegatedName" else if(role==null)"先邀请一位同伴" else "让${role.name}照看它",maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            if(delegated!=null) {
                val minutes=((pet.handoffAt+HomePetRules.HANDOFF_DELAY_MS-now).coerceAtLeast(0L)/60_000+1).toInt()
                Text("$delegatedName 大约 $minutes 分钟内会照看它。",color=CompanionSceneColors.muted,style=MaterialTheme.typography.labelSmall)
            }
            val careError=ui.pending?.error
            if(careError!=null) {
                Text(careError,color=CompanionSceneColors.rose,style=MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick=onRetry,colors=ButtonDefaults.textButtonColors(contentColor=CompanionSceneColors.gold)){Text("重试")}
                    TextButton(onClick=onAbandon,colors=ButtonDefaults.textButtonColors(contentColor=CompanionSceneColors.muted)){Text("放弃")}
                }
            }
            BondRow(pet)
            TodayCareRow(records,now)
            records.firstOrNull()?.let{(row,action)->
                Text("刚刚：${journalText(row,action,pet.name,residents)}",color=CompanionSceneColors.muted,style=MaterialTheme.typography.labelSmall,maxLines=2,overflow=TextOverflow.Ellipsis)}
            TextButton(onClick=onJournal,modifier=Modifier.fillMaxWidth()) {
                Text("共同手账（${records.size} 条）",color=CompanionSceneColors.gold,style=MaterialTheme.typography.labelLarge)
                Icon(Icons.Default.ChevronRight,null,tint=CompanionSceneColors.gold,modifier=Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun RolePicker(residents:List<RoleEntity>,role:RoleEntity?,onRole:(String)->Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        residents.forEach {person->
            val selected=person.id==role?.id
            Surface(onClick={onRole(person.id)},shape=CircleShape,color=if(selected)CompanionSceneColors.gold.copy(alpha=.18f)else CompanionSceneColors.panelRaised,
                border=BorderStroke(1.dp,if(selected)CompanionSceneColors.gold.copy(alpha=.5f)else Color.White.copy(alpha=.08f))) {
                Row(Modifier.padding(start=4.dp,end=10.dp,top=4.dp,bottom=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                    CompanionAvatar(person.name,CompanionSceneColors.rose,22.dp)
                    Text(person.name,color=if(selected)CompanionSceneColors.gold else CompanionSceneColors.moonlight,style=MaterialTheme.typography.labelSmall,maxLines=1)
                }
            }
        }
    }
}

@Composable
private fun RoleNote(role:RoleEntity?,residents:List<RoleEntity>,pet:HomePet,preview:HomePet,now:Long) {
    val delegated=pet.handoffRoleId
    val actor=if(delegated!=null)roleNameOf(delegated,residents)else role?.name
    val line=when {
        delegated!=null->"今晚由${actor}照看它。"
        actor==null->"先让一个角色搬进来，再一起照顾它。"
        preview.hunger<=35->"它刚才一直看着碗。"
        preview.hygiene<=35->"该给它梳梳毛了。"
        preview.mood<=35->"它把球叼到你脚边了。"
        HomePetRules.stage(pet,now)==HomePetStage.KITTEN->"它今天一直趴在窗边等你。"
        else->"它靠在我旁边睡着了。"
    }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CompanionSceneColors.panelRaised).padding(12.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        CompanionAvatar(actor?:"伴",CompanionSceneColors.rose,28.dp)
        Column(Modifier.weight(1f)) {
            Text(actor?:"伙伴",color=CompanionSceneColors.moonlight,style=MaterialTheme.typography.labelMedium,maxLines=1)
            Text(line,color=CompanionSceneColors.muted,style=MaterialTheme.typography.bodySmall,maxLines=3,overflow=TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun NeedRow(need:HomePetNeed,value:Int) {
    val low=value<=35
    val color=if(low)CompanionSceneColors.rose else CompanionSceneColors.gold
    val labelWidth=if(LocalDensity.current.fontScale>=1.5f)58.dp else 30.dp
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Icon(needIcon(need),null,tint=if(low)CompanionSceneColors.rose else CompanionSceneColors.muted,modifier=Modifier.size(16.dp))
        Text(needLabel(need),color=CompanionSceneColors.muted,style=MaterialTheme.typography.labelSmall,modifier=Modifier.width(labelWidth),maxLines=1)
        LinearProgressIndicator(progress={value/100f},modifier=Modifier.weight(1f).height(5.dp).clip(CircleShape),
            color=color,trackColor=CompanionSceneColors.panelLine)
        Text(HomePetRules.descriptor(need,value),color=if(low)CompanionSceneColors.rose else CompanionSceneColors.muted,
            style=MaterialTheme.typography.labelSmall,modifier=Modifier.widthIn(min=64.dp),textAlign=TextAlign.End,maxLines=1,overflow=TextOverflow.Ellipsis)
    }
}

@Composable
private fun BondRow(pet:HomePet) {
    val lit=min(5,1+pet.bond/5)
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("默契",color=CompanionSceneColors.muted,style=MaterialTheme.typography.labelSmall)
        repeat(5){index->Box(Modifier.width(22.dp).height(5.dp).clip(CircleShape).background(if(index<lit)CompanionSceneColors.gold else CompanionSceneColors.panelLine))}
        Spacer(Modifier.weight(1f))
        Text("一起照顾 ${pet.userCareCount+pet.roleCareCount} 次",color=CompanionSceneColors.muted,style=MaterialTheme.typography.labelSmall,maxLines=1)
    }
}

@Composable
private fun TodayCareRow(records:List<Pair<HomeCommandRow,HomeAction>>,now:Long) {
    val today=records.filter {HomeLife.dayAt(it.first.createdAt)==HomeLife.dayAt(now)}
    val user=today.count {it.second is HomeAction.CarePet}
    val partner=today.count {it.second is HomeAction.ResolvePetHandoff}
    Text(if(user+partner==0)"今天还没有人照顾它。" else "今天：你 $user 次 · 同伴 $partner 次",
        color=CompanionSceneColors.muted,style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
}

@Composable
private fun CareTile(care:HomePetCare,recommended:HomePetCare,enabled:Boolean,onAction:(HomeAction)->Unit,modifier:Modifier) {
    val selected=care==recommended
    Surface(onClick={onAction(HomeAction.CarePet(care))},enabled=enabled,modifier=modifier.heightIn(min=64.dp),
        shape=RoundedCornerShape(14.dp),color=if(selected)CompanionSceneColors.gold.copy(alpha=.16f)else CompanionSceneColors.panelRaised,
        border=BorderStroke(1.dp,if(selected)CompanionSceneColors.gold.copy(alpha=.45f)else Color.White.copy(alpha=.06f))) {
        Column(Modifier.padding(vertical=9.dp,horizontal=4.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Icon(needIcon(care.need),null,tint=if(selected)CompanionSceneColors.gold else CompanionSceneColors.muted,modifier=Modifier.size(19.dp))
            Text(care.title,color=if(selected)CompanionSceneColors.gold else CompanionSceneColors.moonlight,style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun JournalSheet(home:HomeState,residents:List<RoleEntity>,records:List<Pair<HomeCommandRow,HomeAction>>,focusedId:String?,
    now:Long,wide:Boolean,onClose:()->Unit,modifier:Modifier) {
    val pet=home.pet
    val start=pet?.adoptedAt?:home.createdAt
    val days=(((now.coerceAtLeast(start)-start)/86_400_000L)+1).coerceAtLeast(1L).toInt()
    val timeFormat=remember {SimpleDateFormat("HH:mm",Locale.getDefault())}
    val dateFormat=remember {SimpleDateFormat("M月d日",Locale.getDefault())}
    val timeWidth=if(LocalDensity.current.fontScale>=1.5f)64.dp else 42.dp
    Surface(modifier,shape=RoundedCornerShape(topStart=28.dp,topEnd=28.dp,bottomStart=if(wide)28.dp else 0.dp,bottomEnd=if(wide)28.dp else 0.dp),
        color=CompanionSceneColors.panel.copy(alpha=.97f),border=BorderStroke(1.dp,Color.White.copy(alpha=.1f)),shadowElevation=12.dp) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Box(Modifier.width(38.dp).height(4.dp).clip(CircleShape).background(CompanionSceneColors.moonlight.copy(alpha=.22f)).align(Alignment.CenterHorizontally))
            Row(verticalAlignment=Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("共同手账",color=CompanionSceneColors.moonlight,style=MaterialTheme.typography.headlineSmall)
                    Text(if(pet==null)"还没有领养" else "和${pet.name}一起生活的第 $days 天",color=CompanionSceneColors.muted,style=MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick=onClose,modifier=Modifier.size(40.dp)){Icon(Icons.Default.Close,"收起手账",tint=CompanionSceneColors.muted)}
            }
            if(pet!=null) {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CompanionSceneColors.panelRaised).padding(12.dp),
                    verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    PetAvatar(38.dp)
                    Column(Modifier.weight(1f)) {
                        Text("${dateFormat.format(Date(pet.adoptedAt))}，你们一起领养了${pet.name}",color=CompanionSceneColors.moonlight,style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=TextOverflow.Ellipsis)
                        Text("它住进了窗边的位置。",color=CompanionSceneColors.muted,style=MaterialTheme.typography.labelSmall)
                    }
                }
            }
            val entries=records.filterNot {it.second is HomeAction.AdoptPet}
            if(entries.isEmpty()) {
                Text(if(pet==null)"领养之后，照顾和重要时刻都会写在这里。"else"还没有照顾记录。",
                    color=CompanionSceneColors.muted,style=MaterialTheme.typography.bodySmall)
            } else {
                entries.forEach { (row,action)->
                    val focused=row.commandId==focusedId
                    val actor=journalActor(action,residents)
                    val text=journalText(row,action,pet?.name?:"它",residents)
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(if(focused)CompanionSceneColors.gold.copy(alpha=.1f)else Color.Transparent)
                        .padding(horizontal=if(focused)8.dp else 0.dp,vertical=8.dp),verticalAlignment=Alignment.Top,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        Text(timeFormat.format(Date(row.createdAt)),color=CompanionSceneColors.muted,style=MaterialTheme.typography.labelSmall,modifier=Modifier.width(timeWidth),maxLines=1)
                        Box(Modifier.padding(top=5.dp).size(7.dp).clip(CircleShape).background(if(actor=="你"||actor=="你们")CompanionSceneColors.gold else CompanionSceneColors.rose))
                        Column(Modifier.weight(1f)) {
                            Text(text,color=CompanionSceneColors.moonlight,style=MaterialTheme.typography.bodySmall,maxLines=4,overflow=TextOverflow.Ellipsis)
                            Text(actor,color=CompanionSceneColors.muted,style=MaterialTheme.typography.labelSmall,maxLines=1)
                        }
                    }
                }
            }
            HorizontalDivider(color=Color.White.copy(alpha=.08f))
            Text(if(pet==null)"窗边还空着。"else"第 $days 天，窗边一直亮着灯。",color=CompanionSceneColors.muted,
                style=MaterialTheme.typography.labelSmall,textAlign=TextAlign.Center,modifier=Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun PendingBanner(error:String,onRetry:()->Unit,onAbandon:()->Unit,modifier:Modifier) {
    Surface(modifier.widthIn(max=340.dp),shape=RoundedCornerShape(14.dp),color=Color(0xEE171C1D),border=BorderStroke(1.dp,CompanionSceneColors.rose.copy(alpha=.4f))) {
        Row(Modifier.padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(error,color=CompanionSceneColors.rose,style=MaterialTheme.typography.bodySmall,modifier=Modifier.weight(1f),maxLines=2,overflow=TextOverflow.Ellipsis)
            TextButton(onClick=onRetry,colors=ButtonDefaults.textButtonColors(contentColor=CompanionSceneColors.gold)){Text("重试")}
            TextButton(onClick=onAbandon,colors=ButtonDefaults.textButtonColors(contentColor=CompanionSceneColors.muted)){Text("放弃")}
        }
    }
}

private fun needLabel(need:HomePetNeed)=when(need){HomePetNeed.HUNGER->"饱食";HomePetNeed.MOOD->"心情";HomePetNeed.ENERGY->"精力";HomePetNeed.HYGIENE->"清洁"}
private fun needIcon(need:HomePetNeed):ImageVector=when(need){HomePetNeed.HUNGER->Icons.Default.Restaurant;HomePetNeed.MOOD->Icons.Default.SportsEsports;HomePetNeed.ENERGY->Icons.Default.Bedtime;HomePetNeed.HYGIENE->Icons.Default.Brush}
private fun roleNameOf(id:String?,residents:List<RoleEntity>)=residents.firstOrNull {it.id==id}?.name?:"TA"
private fun journalActor(action:HomeAction,residents:List<RoleEntity>)=when(action){
    is HomeAction.CarePet->"你";is HomeAction.AdoptPet->"你们"
    is HomeAction.HandoffPet->roleNameOf(action.roleId,residents);is HomeAction.ResolvePetHandoff->roleNameOf(action.roleId,residents)
    else->"TA"
}
private fun journalText(row:HomeCommandRow,action:HomeAction,petName:String,residents:List<RoleEntity>)=when(action){
    is HomeAction.HandoffPet->"把${petName}交给${roleNameOf(action.roleId,residents)}照看。"
    is HomeAction.ResolvePetHandoff->row.description.replaceFirst("TA ",roleNameOf(action.roleId,residents))
    is HomeAction.AdoptPet->"你们一起领养了$petName。"
    else->row.description
}
