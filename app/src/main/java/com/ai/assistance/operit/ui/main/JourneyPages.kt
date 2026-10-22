package com.ai.assistance.operit.ui.main

import com.ai.assistance.operit.ui.main.ThemedIcon as Icon

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ai.assistance.operit.data.model.*
import java.util.UUID

@Composable internal fun JourneyRoute(game:JourneyState,enabled:Boolean,onVisit:(Int)->Unit) {
    val place=game.places[game.current]
    Text(place.name,style=MaterialTheme.typography.headlineSmall)
    Text("${game.userName} 与 ${game.roleName}",Modifier.padding(vertical=8.dp),style=MaterialTheme.typography.labelLarge)
    Text(place.scene,style=MaterialTheme.typography.bodyLarge)
    Spacer(Modifier.height(12.dp))
    Text(if(game.mode==JourneyKind.CITY)"已到访 ${game.visited.size} / ${game.places.size} 处"else"第 ${game.current+1} / ${game.places.size} 幕",style=MaterialTheme.typography.titleMedium)
    if(game.mode==JourneyKind.SECRET&&game.result==PlayResult.ACTIVE&&!game.canAdvance&&game.current<game.places.lastIndex)
        Text("本幕完成一轮交流后，可以前往下一幕。",Modifier.padding(vertical=8.dp),style=MaterialTheme.typography.bodySmall)
    game.places.forEachIndexed {index,p->
        val reachable=enabled&&game.turn==PlaySide.USER&&index!=game.current&&
            (game.mode==JourneyKind.CITY||game.canAdvance&&index==game.current+1)
        ListItem(headlineContent={Text("${index+1} · ${p.name}",maxLines=2,overflow=TextOverflow.Ellipsis)},
            supportingContent={Text(when {index==game.current->"当前地点";index in game.visited->"已到访";game.mode==JourneyKind.SECRET&&index>game.current+1->"尚未开启";game.mode==JourneyKind.SECRET->"下一幕";else->"未到访"})},
            leadingContent={Icon(if(index in game.visited)Icons.Default.CheckCircleOutline else Icons.Default.Place,null)},
            trailingContent={if(reachable)Icon(Icons.Default.ChevronRight,"前往")},modifier=Modifier.clickable(enabled=reachable){onVisit(index)})
    }
}

private fun defaultJourneyPlaces(mode:JourneyKind)=if(mode==JourneyKind.CITY)listOf(
    JourneyPlace("街角咖啡馆","从一杯热饮开始今天的散步，商量想去的地方。"),
    JourneyPlace("河岸步道","沿着虚构城市的河岸慢慢走，聊聊路上让你在意的小事。"),
    JourneyPlace("旧书店","一起翻看书架，分享一本想推荐给对方的书。"),
    JourneyPlace("屋顶花园","在安静的花园歇一会，回顾今天最喜欢的一刻。"))else listOf(
    JourneyPlace("门口的约定","两人赴约，先聊聊今晚的心情和彼此愿意的相处方式。"),
    JourneyPlace("窗边相谈","在安静的窗边互相提一个一直想问的问题，由对方自己决定怎样回答。"),
    JourneyPlace("离别之前","回顾这次约会，谈谈想留下的回忆与下次见面的约定。"))
private data class JourneyDraftPlace(val value:JourneyPlace,val id:String=UUID.randomUUID().toString())

/** Protected editing focus: drafts are memory-only and disappear with their owning Play page. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun CreateJourneyDialog(mode:JourneyKind,role:RoleEntity,working:Boolean,onClose:()->Unit,onCreate:(String,JourneyState)->Unit) {
    var title by remember {mutableStateOf(if(mode==JourneyKind.CITY)"一起漫游"else"隐秘之约")}
    var userName by remember {mutableStateOf(if(role.personaEnabled&&role.userName.isNotBlank())role.userName else "我")}
    var roleName by remember {mutableStateOf(role.name)}
    var premise by remember {mutableStateOf(if(mode==JourneyKind.CITY)"在一座虚构的城市里，按照我们的心情选择去处。"else"这是一场两人约好的虚构约会。我们都可以提出想法，也可以表达不愿意。")}
    var drafts by remember {mutableStateOf(defaultJourneyPlaces(mode).map(::JourneyDraftPlace))}
    val places=drafts.map {it.value}
    val initial=remember {listOf(title,userName,roleName,premise,places)}
    val dirty=initial!=listOf(title,userName,roleName,premise,places)
    var discard by remember {mutableStateOf(false)}
    val close:()->Unit={if(!working){if(dirty)discard=true else onClose()}}
    val ready=remember(title,userName,roleName,premise,places) {
        runCatching {PlayJson.text(title,120);JourneyState.start(mode,premise,userName,roleName,places)}
    }
    fun replace(index:Int,value:JourneyPlace){drafts=drafts.mapIndexed {i,p->if(i==index)p.copy(value=value)else p}}
    fun move(index:Int,to:Int){drafts=drafts.toMutableList().apply {add(to,removeAt(index))}.toList()}
    Dialog(onDismissRequest=close,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
                TopAppBar(title={Text(if(mode==JourneyKind.CITY)"安排城市漫游"else"安排隐秘之约")},
                    navigationIcon={IconButton(enabled=!working,onClick=close){Icon(Icons.AutoMirrored.Filled.ArrowBack,"返回")}},
                    actions={TextButton(enabled=!working&&ready.isSuccess,onClick={onCreate(title,ready.getOrThrow())}){Text("创建")}})
                LazyColumn(Modifier.weight(1f).widthIn(max=720.dp).fillMaxWidth().align(Alignment.CenterHorizontally),
                    contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    item {
                        Text("地点和开场都可以修改。创建不调用模型，角色回应时逐次确认邀请。",color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("创建时保存双方在家园选择的穿搭，之后换装不改变本次活动。",style=MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(title,{if(it.length<=120)title=it},enabled=!working,label={Text("活动名称")},modifier=Modifier.fillMaxWidth())
                    }
                    item {OutlinedTextField(userName,{if(it.length<=40)userName=it},enabled=!working,label={Text("剧情中你的称呼")},modifier=Modifier.fillMaxWidth())}
                    item {OutlinedTextField(roleName,{if(it.length<=40)roleName=it},enabled=!working,label={Text("剧情中角色的称呼")},supportingText={Text("参与角色仍是 ${role.name}，称呼只用于这段剧情。")},modifier=Modifier.fillMaxWidth())}
                    item {OutlinedTextField(premise,{if(it.length<=2000)premise=it},enabled=!working,label={Text("氛围与约定")},minLines=3,maxLines=6,modifier=Modifier.fillMaxWidth())}
                    item {Text(if(mode==JourneyKind.CITY)"你的路线 · ${places.size} 个地点"else"分幕安排 · ${places.size} 幕",style=MaterialTheme.typography.titleLarge)}
                    itemsIndexed(drafts,key={_,draft->draft.id}) {index,draft->
                        val p=draft.value
                        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                                Text("${index+1}",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                                IconButton(enabled=!working&&index>0,onClick={move(index,index-1)}){Icon(Icons.Default.ArrowUpward,"向前移动第 ${index+1} 个场景")}
                                IconButton(enabled=!working&&index<places.lastIndex,onClick={move(index,index+1)}){Icon(Icons.Default.ArrowDownward,"向后移动第 ${index+1} 个场景")}
                                IconButton(enabled=!working&&places.size>2,onClick={drafts=drafts.filterIndexed {i,_->i!=index}}){Icon(Icons.Default.DeleteOutline,"移除第 ${index+1} 个场景")}
                            }
                            OutlinedTextField(p.name,{if(it.length<=80)replace(index,p.copy(name=it))},enabled=!working,label={Text("地点名称")},modifier=Modifier.fillMaxWidth())
                            OutlinedTextField(p.scene,{if(it.length<=800)replace(index,p.copy(scene=it))},enabled=!working,label={Text("到达这里时的场景")},minLines=2,maxLines=5,modifier=Modifier.fillMaxWidth())
                            HorizontalDivider(Modifier.padding(top=8.dp))
                        }
                    }
                    item {
                        OutlinedButton(enabled=!working&&places.size<12,onClick={drafts=drafts+JourneyDraftPlace(JourneyPlace("",""))}){Text("添加场景")}
                        Text("至少 2 个、最多 12 个场景。城市可重访；隐秘之约按顺序推进。",Modifier.padding(vertical=8.dp),style=MaterialTheme.typography.bodySmall)
                        if(ready.isFailure)Text("请填写名称、双方称呼、约定和每个场景；内容须为完整文本，不能含空字符。",color=MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
    if(discard)AlertDialog(onDismissRequest={discard=false},title={Text("放弃未保存的安排？")},
        text={Text("这次修改的称呼、约定和场景尚未创建。返回后这些修改会丢失。")},
        confirmButton={TextButton(onClick=onClose){Text("放弃修改")}},dismissButton={TextButton(onClick={discard=false}){Text("继续编辑")}})
}
