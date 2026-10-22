package com.ai.assistance.operit.ui.main

import com.ai.assistance.operit.ui.main.ThemedIcon as Icon

import androidx.activity.compose.BackHandler
import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.db.*
import com.ai.assistance.operit.data.model.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable internal fun ColumnScope.PlayPage(role:RoleEntity,chat:ChatEntity,state:CompanionState,vm:CompanionViewModel,
    draftSpace:Int,onDraft:(String)->Unit,onClose:()->Unit,initialActivityId:String?=null) {
    var token by remember(role.id,role.spaceId,chat.id){mutableStateOf<String?>(null)}
    val context=LocalContext.current
    val activity=remember(context){generateSequence(context){(it as? ContextWrapper)?.baseContext}.filterIsInstance<Activity>().first()}
    DisposableEffect(role.id,role.spaceId,chat.id) {
        val opened=vm.openPlay(role.scope(),chat.id);token=opened
        onDispose {if(!activity.isChangingConfigurations)vm.closePlay(opened)}
    }
    val ui=state.play?.takeIf {it.token==token&&it.role==role.scope()&&it.chatId==chat.id}
    var initialSelectionApplied by remember(initialActivityId,role.id,role.spaceId,chat.id){mutableStateOf(false)}
    LaunchedEffect(initialActivityId,ui?.token,ui?.activities) {
        if(initialActivityId!=null&&!initialSelectionApplied) {
            val current=ui?:return@LaunchedEffect
            if(current.activities.any {it.id==initialActivityId}) {
                vm.selectPlay(current.token,initialActivityId)
                initialSelectionApplied=true
            }
        }
    }
    val row=ui?.view?.activity
    val session=remember(row?.stateJson,row?.version){row?.value()}
    val storyDraft=ui?.draft.orEmpty()
    fun setStoryDraft(text:String){if(ui!=null)vm.setPlayDraft(ui.token,row?.id,text)}
    var allStory by remember(row?.id){mutableStateOf(false)}
    var storyQuery by remember(row?.id){mutableStateOf("")}
    var createJourney by remember(role.scope(),chat.id){mutableStateOf<JourneyKind?>(null)}
    var createTable by remember(role.scope(),chat.id){mutableStateOf<Boolean?>(null)}
    var createChess by remember(role.scope(),chat.id){mutableStateOf(false)}
    var travel by remember(row?.id){mutableStateOf<Pair<PlaySessionRow,Int>?>(null)}
    val selected=ui?.selectedId!=null
    var create by remember {mutableStateOf(false)}
    var deleting by remember(row?.id){mutableStateOf(false)}
    var summary by remember(row?.id){mutableStateOf<PlaySessionRow?>(null)}
    var records by remember(row?.id){mutableStateOf(false)}
    var ending by remember(row?.id){mutableStateOf(false)}
    var leave by remember(row?.id){mutableStateOf<(() -> Unit)?>(null)}
    fun navigate(action:()->Unit){if(storyDraft.isNotBlank())leave=action else action()}
    val back:()->Unit={navigate {if(selected)vm.selectPlay(requireNotNull(ui).token,null)else onClose()}}
    BackHandler(onBack=back)
    Column(Modifier.weight(1f).fillMaxWidth().imePadding()) {
        TopAppBar(title={Column {
            Text(row?.title?:"约会与小游戏",maxLines=1,overflow=TextOverflow.Ellipsis)
            Text("和 ${role.name}",style=MaterialTheme.typography.labelMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
        }},navigationIcon={IconButton(onClick=back){Icon(Icons.AutoMirrored.Filled.ArrowBack,"返回")}},actions={
            if(!selected)TextButton(enabled=ui!=null&&!state.working,onClick={create=true}){Text("新活动")}
            else IconButton(enabled=row!=null&&!state.working,onClick={deleting=true}){Icon(Icons.Default.DeleteOutline,"删除本次活动")}
        })
        if(ui==null) {
            Text(if(token==null)"正在打开活动…"else"活动页面已关闭，请返回后重新打开。",Modifier.padding(24.dp))
        } else if(ui.error!=null) {
            Text(ui.error,Modifier.padding(24.dp),color=MaterialTheme.colorScheme.error)
            TextButton(onClick=back){Text("返回")}
        } else if(!selected) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth(),contentPadding=PaddingValues(bottom=24.dp)) {
                item {
                    Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text("把相处变成一段共同经历",style=MaterialTheme.typography.headlineSmall)
                        Text("写一场虚构约会，或一起玩一局。每次邀请角色行动前，都可以确认模型与服务地址。",color=MaterialTheme.colorScheme.onSurfaceVariant)
                        if(ui.activities.isEmpty())Button(onClick={create=true},enabled=!state.working){Text("开始第一段活动")}
                    }
                }
                items(ui.activities,key={it.id}) {activity->
                    ListItem(headlineContent={Text(activity.title,maxLines=2,overflow=TextOverflow.Ellipsis)},
                        supportingContent={Text(playStatus(activity.value()))},
                        leadingContent={Icon(when(activity.value().state){is DateStoryState,is JourneyState->Icons.Default.FavoriteBorder;else->Icons.Default.SportsEsports},null)},
                        trailingContent={Icon(Icons.Default.ChevronRight,null)},
                        modifier=Modifier.clickable {vm.selectPlay(ui.token,activity.id)})
                }
            }
        } else if(row==null||session==null) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else {
            val enabled=!state.working&&session.status==PlayStatus.ACTIVE&&session.requestId==null
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal=12.dp),
                horizontalArrangement=Arrangement.spacedBy(4.dp),
                verticalArrangement=Arrangement.spacedBy(2.dp)
            ) {
                Text(playStatus(session),style=MaterialTheme.typography.labelLarge,modifier=Modifier.padding(8.dp))
                if(session.requestId!=null)TextButton(onClick={vm.stopPlay()}){Text("停止")}
                else if(session.status==PlayStatus.PAUSED||session.status==PlayStatus.INTERRUPTED)
                    TextButton(enabled=!state.working,onClick={vm.playMode(ui.token,row,true)}){Text("恢复活动")}
                else if(session.status==PlayStatus.ACTIVE)TextButton(enabled=!state.working,onClick={vm.playMode(ui.token,row,false)}){Text("暂停")}
                TextButton(onClick={records=true}){Text("调用记录 ${ui.view.requestCount}")}
                TextButton(onClick={summary=row}){Text("带回聊天")}
                if(session.status in setOf(PlayStatus.COMPLETE,PlayStatus.PAUSED))TextButton(enabled=!state.working,onClick={
                    when(val game=session.state) {
                        is WordPlayState->navigate {createTable=false}
                        is CardPlayState->navigate {createTable=true}
                        is XiangqiPlayState->navigate {createChess=true}
                        is AdventurePlayState->navigate {vm.setAdventureForm(ui.token,AdventureSetup(partner=role.name))}
                        else->{val fresh=when(game){is DateStoryState->DateStoryState.start(game.place,game.premise);is GomokuState->GomokuState.start(game.first);is MemoryPairsState->MemoryPairsState.start(game.size/2,game.first);is JourneyState->game.restart();else->error("Unknown activity")}
                            navigate {vm.createPlay(ui.token,row.title,fresh)}}
                    }
                }){Text("再开一局")}
            }
            if(session.requestId!=null)LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(Modifier.weight(1f).fillMaxWidth(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                when(val game=session.state) {
                    is AdventurePlayState->item {key(row.id){Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text("家园纪念物",style=MaterialTheme.typography.titleMedium)
                        Text("本局获得后可领取到共享家园，每种仅一次；不会消耗本局纪念物。",style=MaterialTheme.typography.bodySmall)
                        HomeAdventureReward.entries.forEach {reward->
                            OutlinedButton(enabled=!state.working&&ui.reward==null&&reward.earned(game.game),
                                onClick={vm.previewAdventureReward(ui.token,row,reward)}) {
                                Text("${reward.item.title} · ${if(reward.earned(game.game))"查看领取"else"尚未获得"}")
                            }
                        }
                        AdventureBoard(game.game,enabled,storyDraft,{setStoryDraft(it)}) {move->
                        vm.playAction(ui.token,row,PlayAction.Adventure(move)){if(move is AdventureAction.Say||move==AdventureAction.Finish)setStoryDraft("")}
                    }}}}
                    is WordPlayState->item {WordBoard(game.game,enabled,storyDraft,{setStoryDraft(it)}){move->vm.playAction(ui.token,row,PlayAction.Word(move)){setStoryDraft("")}}}
                    is CardPlayState->item {CardsBoard(game.game,enabled){vm.playAction(ui.token,row,PlayAction.Card(it))}}
                    is XiangqiPlayState->item {key(row.id){XiangqiBoardView(game,enabled){vm.playAction(ui.token,row,it)}}}
                    is GomokuState->item {GomokuBoard(game,enabled&&game.turn==PlaySide.USER){vm.playAction(ui.token,row,it)}}
                    is MemoryPairsState->item {MemoryBoard(game,enabled){vm.playAction(ui.token,row,it)}}
                    is JourneyState->{
                        game.outfits?.let {outfits->item {Text(outfits.description(),style=MaterialTheme.typography.bodyMedium)}}
                        item {JourneyRoute(game,enabled){travel=row to it}}
                        item {
                            TextButton(onClick={allStory=!allStory;storyQuery=""}){Text(if(allStory)"只看本次到访"else"查看全部剧情")}
                            if(allStory)OutlinedTextField(storyQuery,{if(it.length<=120)storyQuery=it},label={Text("搜索地点或对白")},modifier=Modifier.fillMaxWidth())
                        }
                        val visit=game.records.last().visit
                        val shown=game.records.withIndex().filter {(_,record)->(allStory||record.visit==visit)&&
                            (storyQuery.isBlank()||record.text.contains(storyQuery,true)||game.places[record.place].name.contains(storyQuery,true))}
                        if(shown.isEmpty())item {Text("没有匹配的剧情记录。",color=MaterialTheme.colorScheme.onSurfaceVariant)}
                        items(shown,key={it.index}) {(_,record)->
                            if(record.side==null)Text("到访 ${record.visit+1} · ${game.places[record.place].name}",style=MaterialTheme.typography.titleMedium)
                            else Row(Modifier.fillMaxWidth(),horizontalArrangement=if(record.side==PlaySide.USER)Arrangement.End else Arrangement.Start) {
                                Column(Modifier.fillMaxWidth(.9f).background(if(record.side==PlaySide.USER)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,RoundedCornerShape(16.dp)).padding(14.dp)) {
                                    Text(if(record.side==PlaySide.USER)game.userName else game.roleName,style=MaterialTheme.typography.labelMedium)
                                    Spacer(Modifier.height(6.dp));Text(record.text)
                                }
                            }
                        }
                        if(game.result==PlayResult.ACTIVE)item {
                            OutlinedTextField(storyDraft,{if(it.length<=4000)setStoryDraft(it)},enabled=enabled&&game.turn==PlaySide.USER,
                                label={Text("在这里，你想说或做什么")},minLines=2,maxLines=6,modifier=Modifier.fillMaxWidth(),supportingText={Text("${storyDraft.length} / 4000 · 虚构互动")})
                            Button(enabled=enabled&&game.turn==PlaySide.USER&&storyDraft.isNotBlank(),onClick={vm.playAction(ui.token,row,PlayAction.Narrate(PlaySide.USER,storyDraft)){setStoryDraft("")}}){Text("写入剧情")}
                            TextButton(enabled=enabled,onClick={ending=true}){Text("结束这次活动")}
                        }
                    }
                    is DateStoryState->{
                        game.outfits?.let {outfits->item {Text(outfits.description(),style=MaterialTheme.typography.bodyMedium)}}
                        item {Text(game.place,style=MaterialTheme.typography.titleLarge);Text(game.premise,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                        items(game.turns.withIndex().toList(),key={it.index}) {(_,turn)->
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=if(turn.side==PlaySide.USER)Arrangement.End else Arrangement.Start) {
                                Column(Modifier.fillMaxWidth(.9f).background(if(turn.side==PlaySide.USER)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(16.dp)).padding(14.dp)) {
                                    Text(if(turn.side==PlaySide.USER)"我"else role.name,style=MaterialTheme.typography.labelMedium)
                                    Spacer(Modifier.height(6.dp));Text(turn.text)
                                }
                            }
                        }
                        if(game.result==PlayResult.ACTIVE)item {
                            OutlinedTextField(value=storyDraft,onValueChange={if(it.length<=4000)setStoryDraft(it)},label={Text("你的行动或对白")},
                                enabled=enabled&&game.turn==PlaySide.USER,modifier=Modifier.fillMaxWidth(),minLines=2,maxLines=6,
                                supportingText={Text("${storyDraft.length} / 4000 · 虚构剧情")})
                            Button(enabled=enabled&&game.turn==PlaySide.USER&&storyDraft.isNotBlank(),onClick={vm.playAction(ui.token,row,PlayAction.Narrate(PlaySide.USER,storyDraft)){setStoryDraft("")}}){Text("写入剧情")}
                            TextButton(enabled=enabled,onClick={ending=true}){Text("结束这次约会")}
                        }
                    }
                }
                val last=ui.view.recent.firstOrNull()
                if(session.state is JourneyState&&session.state.turn==PlaySide.ROLE&&!PlaySessions.canRequest(session.state))item {
                    Text("这段剧情已达到容量上限，无法再邀请角色。可以结束活动，再用相同安排创建新活动。",color=MaterialTheme.colorScheme.error)
                }
                if(last?.state in setOf(MessageState.FAILED,MessageState.CANCELLED))item {
                    requireNotNull(last)
                    Text(if(last.state==MessageState.CANCELLED)"上次邀请已停止，已确认进度保留。"else "上次邀请未完成：${errorText(last.errorCode)}。可以重新邀请。",color=MaterialTheme.colorScheme.error)
                }
                if(last?.state==MessageState.COMPLETE&&session.state !is DateStoryState&&session.state !is JourneyState)item {
                    val response=remember(last.requestId){com.ai.assistance.operit.api.chat.PlayGenerations.decode(PlayStates.decode(last.beforeState),last.content)}
                    val comment=visiblePlayComment(session.state,response.comment)
                    if(comment.isNotBlank())Text("${role.name}：$comment",style=MaterialTheme.typography.bodyLarge)
                }
            }
            if(enabled&&PlaySessions.canRequest(session.state))Button(onClick={vm.previewPlay(ui.token,row)},modifier=Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp)) {
                Text("邀请 ${role.name} 继续",maxLines=2,overflow=TextOverflow.Ellipsis)
            }
        }
    }
    if(create&&ui!=null)CreatePlayDialog(state.working,onDismiss={create=false},onJourney={create=false;createJourney=it},onTable={create=false;createTable=it},onChess={create=false;createChess=true},onAdventure={create=false;vm.setAdventureForm(ui.token,AdventureSetup(partner=role.name))}) {title,initial->vm.createPlay(ui.token,title,initial);create=false}
    if(ui?.adventureForm!=null)key(ui.token){CreateAdventureDialog(ui.adventureForm,state.working,onChange={vm.setAdventureForm(ui.token,it)},onClose={vm.setAdventureForm(ui.token,null)}) {title,initial->vm.createPlay(ui.token,title,initial){vm.setAdventureForm(ui.token,null)}}}
    if(createChess&&ui!=null)key(ui.token){CreateXiangqiDialog(state.working,onClose={createChess=false}) {title,initial->vm.createPlay(ui.token,title,initial){createChess=false}}}
    createTable?.let {cards->if(ui!=null)key(ui.token,cards){CreateTableDialog(cards,state.working,onClose={createTable=null}) {title,initial->vm.createPlay(ui.token,title,initial){createTable=null}}}}
    createJourney?.let {mode->if(ui!=null)key(ui.token,mode){CreateJourneyDialog(mode,role,state.working,onClose={createJourney=null}) {title,initial->
        vm.createPlay(ui.token,title,initial){createJourney=null}
    }}}
    travel?.let {(frozen,index)->if(ui!=null) {
        val game=frozen.value().state as JourneyState
        AlertDialog(onDismissRequest={travel=null},title={Text("前往 ${game.places[index].name}？")},text={Text("到访会写入剧情记录，不调用模型。当前已确认对白保留，之后由你邀请角色回应。"+if(storyDraft.isNotBlank())"\n\n输入框中的对白尚未写入，前往后会丢失。"else "")},
            confirmButton={TextButton(enabled=!state.working,onClick={vm.playAction(ui.token,frozen,PlayAction.Visit(index)){travel=null;setStoryDraft("");allStory=false;storyQuery=""}}){Text("前往")}},dismissButton={TextButton(onClick={travel=null}){Text("留下")}})
    }}
    leave?.let {action->AlertDialog(onDismissRequest={leave=null},title={Text("放弃尚未写入的对白？")},
        text={Text("输入框中的文字还没有写入剧情。继续离开会丢失这些文字，已确认剧情保留。")},
        confirmButton={TextButton(onClick={leave=null;setStoryDraft("");action()}){Text("放弃并继续")}},dismissButton={TextButton(onClick={leave=null}){Text("继续编辑")}})}
    if(ending&&row!=null&&ui!=null)AlertDialog(onDismissRequest={ending=false},title={Text("结束这次活动？")},text={Text("已确认剧情会保留，结束后不再续写。以后可以用相同设定创建新活动。"+if(storyDraft.isNotBlank())"\n\n输入框中尚未写入的对白会丢失。"else "")},
        confirmButton={TextButton(enabled=!state.working,onClick={vm.playAction(ui.token,row,PlayAction.EndStory){ending=false;setStoryDraft("")}}){Text("结束活动")}},dismissButton={TextButton(onClick={ending=false}){Text("继续")}})
    if(deleting&&row!=null&&ui!=null)AlertDialog(onDismissRequest={deleting=false},title={Text("删除这次活动？")},
        text={Text("将停止本次邀请，并删除活动进度与 ${ui.view?.requestCount} 条调用记录。已带回聊天的消息保留。")},
        confirmButton={TextButton(enabled=!state.working,onClick={vm.deletePlay(ui.token,row);deleting=false}){Text("删除")}},dismissButton={TextButton(onClick={deleting=false}){Text("保留")}})
    ui?.reward?.let {pending->
        val preview=pending.preview
        AlertDialog(onDismissRequest={vm.dismissAdventureReward(ui.token)},title={Text(if(preview.claimed)"本局纪念物已领取"else"领取到家园")},
            text={Column(Modifier.heightIn(max=300.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("来源：${preview.sourceTitle}\n纪念物：${preview.action.reward.item.title} × 1\n领取到：共享家园背包")
                Text(if(preview.claimed)"领取记录已保留，即使物品已经赠送或丢弃，也不会再次发放。"else"领取不调用模型。你可以在家园背包中查看或赠送纪念物。")
                pending.error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
            }},confirmButton={if(!preview.claimed)TextButton(enabled=!state.working,onClick={vm.claimAdventureReward(ui.token,pending)}){Text(if(pending.error==null)"确认领取"else"重试同一操作")}},
            dismissButton={TextButton(enabled=!state.working,onClick={vm.dismissAdventureReward(ui.token)}){Text("关闭")}})
    }
    ui?.approval?.let {approval->
        val config=approval.snapshot.config
        AlertDialog(onDismissRequest={vm.dismissPlayApproval(ui.token)},title={Text("邀请角色行动一次")},
            text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("${config.name} · ${config.model}")
                Text("服务地址：${config.endpoint}")
                Text("发送当前活动和角色设定。服务商可能计费；停止或回复无效也可能产生费用。")
            }},confirmButton={TextButton(enabled=!state.working,onClick={vm.startPlay(ui.token,approval)}){Text("确认邀请")}},
            dismissButton={TextButton(onClick={vm.dismissPlayApproval(ui.token)}){Text("取消")}})
    }
    summary?.let {frozen->
        val text=remember(frozen){playSummary(frozen)}
        AlertDialog(onDismissRequest={summary=null},title={Text("带回聊天的内容")},
            text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState())) {Text(text);Spacer(Modifier.height(16.dp));Text(if(text.length>draftSpace)"草稿空间不足，请先整理草稿。"else"只加入草稿，由你编辑并发送。")}},
            confirmButton={TextButton(enabled=ui!=null&&!state.working&&text.length<=draftSpace,onClick={navigate {ui?.let {vm.playDraft(it.token,frozen,text){draft->onDraft(draft);summary=null;onClose()}}}}){Text("加入草稿")}},
            dismissButton={TextButton(onClick={summary=null}){Text("取消")}})
    }
    if(records&&ui?.view!=null)AlertDialog(onDismissRequest={records=false},title={Text("最近的模型调用")},
        text={Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            Text("共 ${ui.view.requestCount} 条，显示最近 20 条。失败和取消的回合也保留状态。")
            ui.view.recent.forEach {request->
                Text("${request.model} · ${when(request.state){MessageState.COMPLETE->"完成";MessageState.CANCELLED->"已停止";MessageState.FAILED->"失败";else->"进行中"}}")
                if(request.errorCode.isNotBlank())Text(errorText(request.errorCode),color=MaterialTheme.colorScheme.error)
                if(request.state!=MessageState.COMPLETE&&request.content.isNotEmpty()) {
                    if(session!=null&&!mayInspectPlayReply(session.state))Text("本局结束后可查看未确认回复，避免提前看到答案或手牌。")
                    else {
                    var expanded by remember(request.requestId){mutableStateOf(false)}
                    TextButton(onClick={expanded=!expanded}){Text(if(expanded)"收起未确认回复"else"查看未确认回复")}
                    if(expanded)Text(request.content)
                    }
                }
                HorizontalDivider()
            }
        }},confirmButton={TextButton(onClick={records=false}){Text("关闭")}})
}

@Composable private fun CreatePlayDialog(working:Boolean,onDismiss:()->Unit,onJourney:(JourneyKind)->Unit,onTable:(Boolean)->Unit,onChess:()->Unit,onAdventure:()->Unit,onCreate:(String,PlayState)->Unit) {
    var kind by remember {mutableStateOf("date")};var title by remember {mutableStateOf("")}
    var place by remember {mutableStateOf("街角咖啡店")};var premise by remember {mutableStateOf("一个悠闲的下午，我们约好一起喝咖啡。")}
    var pendingLeave by remember {mutableStateOf<(() -> Unit)?>(null)}
    val dirty=title.isNotEmpty()||kind!="date"||place!="街角咖啡店"||premise!="一个悠闲的下午，我们约好一起喝咖啡。"
    val leave:((()->Unit)->Unit)={action->if(!working){if(dirty)pendingLeave=action else action()}}
    AlertDialog(onDismissRequest={leave(onDismiss)},title={Text("一起做点什么")},text={Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        listOf("date" to "约会剧情","gomoku" to "五子棋","memory" to "翻牌配对").forEach {(id,name)->
            Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable {kind=id},verticalAlignment=Alignment.CenterVertically) {RadioButton(selected=kind==id,onClick=null);Spacer(Modifier.width(8.dp));Text(name)}
        }
        TextButton(enabled=!working,onClick={leave {onJourney(JourneyKind.CITY)}}){Icon(Icons.Default.Explore,null);Spacer(Modifier.width(8.dp));Text("安排城市漫游")}
        TextButton(enabled=!working,onClick={leave {onJourney(JourneyKind.SECRET)}}){Icon(Icons.Default.FavoriteBorder,null);Spacer(Modifier.width(8.dp));Text("安排隐秘之约")}
        TextButton(enabled=!working,onClick={leave {onTable(false)}}){Text("一起猜词")}
        TextButton(enabled=!working,onClick={leave {onTable(true)}}){Text("玩四色卡牌")}
        TextButton(enabled=!working,onClick={leave(onChess)}){Text("下一局象棋")}
        TextButton(enabled=!working,onClick={leave(onAdventure)}){Text("一起去像素冒险")}
        OutlinedTextField(title,{if(it.length<=80)title=it},label={Text("活动名称")},singleLine=true)
        if(kind=="date") {
            Text("创建时保存双方在家园选择的穿搭，之后换装不改变本次剧情。",style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(place,{if(it.length<=120)place=it},label={Text("地点")})
            OutlinedTextField(premise,{if(it.length<=2000)premise=it},label={Text("开场设定")},minLines=3,maxLines=6)
        } else Text(if(kind=="gomoku")"你先手，15 × 15 棋盘，五子或以上连线获胜。"else"共 8 对牌。配对后继续，未配对则翻回并换人。")
        Text("创建不调用模型。角色行动时单独确认。",style=MaterialTheme.typography.bodySmall)
    }},confirmButton={TextButton(enabled=!working&&title.isNotBlank()&&(kind!="date"||place.isNotBlank()&&premise.isNotBlank()),onClick={
        onCreate(title,when(kind){"date"->DateStoryState.start(place,premise);"gomoku"->GomokuState.start();"memory"->MemoryPairsState.start();else->error("Unknown activity")})
    }){Text("创建活动")}},dismissButton={TextButton(enabled=!working,onClick={leave(onDismiss)}){Text("取消")}})
    pendingLeave?.let {action->AlertDialog(onDismissRequest={pendingLeave=null},title={Text("放弃未保存的活动设置？")},
        text={Text("当前活动尚未创建。切换类型或离开会丢失已填写的名称、地点和开场设定。")},
        confirmButton={TextButton(enabled=!working,onClick={pendingLeave=null;action()}){Text("放弃并继续")}},
        dismissButton={TextButton(onClick={pendingLeave=null}){Text("继续编辑")}})}
}

@Composable private fun GomokuBoard(game:GomokuState,enabled:Boolean,onAction:(PlayAction)->Unit) {
    var row by remember {mutableIntStateOf(7)};var column by remember {mutableIntStateOf(7)}
    var enlarged by remember {mutableStateOf(false)}
    var history by remember {mutableStateOf(false)}
    val ink=MaterialTheme.colorScheme.onSurface;val paper=MaterialTheme.colorScheme.surfaceContainer
    Canvas(Modifier.fillMaxWidth().aspectRatio(1f).background(paper,RoundedCornerShape(12.dp)).semantics {contentDescription="五子棋棋盘，共 ${game.moves.size} 手。使用下方行列控件选择落子位置。"}) {
        val cell=size.minDimension/16;val origin=cell
        repeat(15) {i->val p=origin+i*cell
            drawLine(ink.copy(alpha=.3f),Offset(origin,p),Offset(origin+14*cell,p))
            drawLine(ink.copy(alpha=.3f),Offset(p,origin),Offset(p,origin+14*cell))
        }
        game.moves.forEach {move->val center=Offset(origin+move.column*cell,origin+move.row*cell)
            if(move.side==PlaySide.USER)drawCircle(ink,cell*.4f,center)
            else {drawCircle(paper,cell*.4f,center);drawCircle(ink,cell*.4f,center,style=Stroke(2.dp.toPx()))}
        }
        if(enabled)drawCircle(ink,cell*.48f,Offset(origin+column*cell,origin+row*cell),style=Stroke(1.dp.toPx()))
    }
    Text("实心是你，空心是角色。",Modifier.padding(vertical=8.dp),style=MaterialTheme.typography.bodySmall)
    TextButton(onClick={enlarged=!enlarged}){Text(if(enlarged)"收起放大棋盘"else"放大棋盘选位置")}
    if(enlarged)Box(Modifier.fillMaxWidth().height(320.dp).horizontalScroll(rememberScrollState()).verticalScroll(rememberScrollState())) {
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            repeat(15){r->Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                repeat(15){c->val stone=game.cell(r,c)
                    OutlinedButton(onClick={row=r;column=c},modifier=Modifier.size(48.dp),contentPadding=PaddingValues(0.dp),shape=RoundedCornerShape(4.dp)) {
                        Text(when(stone){PlaySide.USER->"●";PlaySide.ROLE->"○";null->if(r==row&&c==column)"+"else"·"},
                            modifier=Modifier.semantics {contentDescription="第 ${r+1} 行，第 ${c+1} 列，${when(stone){PlaySide.USER->"你的棋子";PlaySide.ROLE->"角色棋子";null->"空位"}}"})
                    }
                }
            }}
        }
    }
    CoordinateControl("行",row,true){row=it}
    CoordinateControl("列",column,true){column=it}
    val occupant=game.cell(row,column)
    Text("第 ${row+1} 行，第 ${column+1} 列：${when(occupant){PlaySide.USER->"你的棋子";PlaySide.ROLE->"角色的棋子";null->"空位"}}")
    Button(enabled=enabled&&occupant==null,onClick={onAction(PlayAction.Place(PlaySide.USER,row,column))}){Text("确认落子")}
    game.moves.lastOrNull {it.side==PlaySide.ROLE}?.let {Text("角色最近落子：第 ${it.row+1} 行，第 ${it.column+1} 列。")}
    TextButton(onClick={history=!history}){Text(if(history)"收起落子记录"else"查看全部落子记录")}
    if(history)Column(Modifier.heightIn(max=240.dp).verticalScroll(rememberScrollState())) {
        game.moves.forEachIndexed {i,move->Text("第 ${i+1} 手 · ${if(move.side==PlaySide.USER)"我"else"角色"} · 第 ${move.row+1} 行，第 ${move.column+1} 列",Modifier.padding(vertical=4.dp))}
    }
}
@Composable private fun CoordinateControl(label:String,value:Int,enabled:Boolean,onChange:(Int)->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("$label ${value+1}",Modifier.weight(1f))
        IconButton(enabled=enabled&&value>0,onClick={onChange(value-1)}){Icon(Icons.Default.Remove,"上一$label")}
        IconButton(enabled=enabled&&value<14,onClick={onChange(value+1)}){Icon(Icons.Default.Add,"下一$label")}
    }
}
@Composable private fun MemoryBoard(game:MemoryPairsState,enabled:Boolean,onAction:(PlayAction)->Unit) {
    Text("你 ${game.userPairs} 对 · 角色 ${game.rolePairs} 对",style=MaterialTheme.typography.titleMedium)
    Column(Modifier.padding(vertical=16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        (0 until game.size).chunked(4).forEach {indices->Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            indices.forEach {index->
                val face=game.face(index);val found=index in game.matched
                if(face!=null)Surface(modifier=Modifier.weight(1f).heightIn(min=64.dp),color=MaterialTheme.colorScheme.secondaryContainer,
                    contentColor=MaterialTheme.colorScheme.onSecondaryContainer,shape=RoundedCornerShape(12.dp)) {
                    Box(contentAlignment=Alignment.Center,modifier=Modifier.padding(4.dp)) {
                        Text("${('A'.code+face).toChar()}${if(found)" ✓"else""}",modifier=Modifier.semantics {contentDescription="第 ${index+1} 张，牌面 ${('A'.code+face).toChar()}${if(found)"，已配对"else"，已翻开"}"})
                    }
                } else FilledTonalButton(modifier=Modifier.weight(1f).heightIn(min=64.dp),contentPadding=PaddingValues(4.dp),shape=RoundedCornerShape(12.dp),
                    enabled=enabled&&game.turn==PlaySide.USER&&game.open.size<2,
                    onClick={onAction(PlayAction.Flip(PlaySide.USER,index))}) {
                    Text("${index+1}",modifier=Modifier.semantics {contentDescription="第 ${index+1} 张，未翻开"})
                }
            }
        }}
    }
    if(game.open.size==2)Button(enabled=enabled,onClick={onAction(PlayAction.HidePair)}){Text("记住了，翻回并换人")}
}
