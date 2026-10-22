package com.ai.assistance.operit.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.R
import com.ai.assistance.operit.data.model.*
import kotlin.math.roundToInt

internal fun adventureCoordinate(p:AdventurePosition)="第 ${p.y+1} 行，第 ${p.x+1} 列"
internal data class AdventureViewport(val x:Int,val y:Int,val width:Int,val height:Int)
internal fun adventureViewport(p:AdventurePosition,full:Boolean)=if(full)AdventureViewport(0,0,13,11)
    else AdventureViewport((p.x-3).coerceIn(0,6),(p.y-3).coerceIn(0,4),7,7)

@Composable internal fun AdventureBoard(game:AdventureGame,enabled:Boolean,draft:String,onDraft:(String)->Unit,onAction:(AdventureAction)->Unit) {
    var full by remember {mutableStateOf(false)}
    var textual by remember {mutableStateOf(false)}
    var tilePage by remember(game.position.area){mutableIntStateOf(0)}
    var history by remember {mutableStateOf(false)}
    var historyPage by remember(game.records.size){mutableIntStateOf((game.records.size-1)/20)}
    var finish by remember {mutableStateOf(false)}
    var skip by remember {mutableStateOf(false)}
    val control=enabled&&game.turn==PlaySide.USER
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(game.position.area.title,style=MaterialTheme.typography.titleLarge)
        Text(game.objective,style=MaterialTheme.typography.bodyLarge)
        PixelMap(game,full)
        Text("我：${game.userName} · ${adventureCoordinate(game.position)}\n同行：${game.roleName} · ${adventureCoordinate(game.companion)}",style=MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            TextButton(onClick={full=!full},modifier=Modifier.heightIn(min=48.dp)){Text(if(full)"回到近景"else"查看已探索全图")}
            TextButton(onClick={textual=!textual},modifier=Modifier.heightIn(min=48.dp)){Text(if(textual)"收起文字地图"else"文字地图")}
        }
        if(textual) {
            val tiles=game.explored.getValue(game.position.area).sorted()
            Text("已探索 ${tiles.size} 格；未探索区域不显示物件。",style=MaterialTheme.typography.bodySmall)
            tiles.drop(tilePage*24).take(24).forEach {index->
                val p=AdventurePosition(game.position.area,index%13,index/13)
                val objects=AdventureWorld.objects.filter {it.position==p&&game.visible(it)}.joinToString {game.objectLabel(it)}
                Text("${adventureCoordinate(p)} · ${AdventureWorld.groundLabel(p,game.flags)}"+
                    (if(objects.isNotEmpty())" · $objects"else "")+(if(p==game.position)" · 我"else "")+(if(p==game.companion)" · 同行"else ""))
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                TextButton(enabled=tilePage>0,onClick={tilePage--},modifier=Modifier.heightIn(min=48.dp)){Text("前 24 格")}
                TextButton(enabled=(tilePage+1)*24<tiles.size,onClick={tilePage++},modifier=Modifier.heightIn(min=48.dp)){Text("后 24 格")}
            }
        }
        if(!game.ended) {
            Text("移动",style=MaterialTheme.typography.titleMedium)
            Column(Modifier.fillMaxWidth(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
                AdventureMoveButton(AdventureDirection.NORTH,control&&game.canMove(AdventureDirection.NORTH),onAction)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    AdventureMoveButton(AdventureDirection.WEST,control&&game.canMove(AdventureDirection.WEST),onAction)
                    AdventureMoveButton(AdventureDirection.SOUTH,control&&game.canMove(AdventureDirection.SOUTH),onAction)
                    AdventureMoveButton(AdventureDirection.EAST,control&&game.canMove(AdventureDirection.EAST),onAction)
                }
            }
            Text("每按一次走一格，移动与物品交互不调用模型。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(game.events.size>=AdventureGame.MAX_EVENTS)Text("本局动作记录已满，可以查看或删除存档，并新建冒险。",color=MaterialTheme.colorScheme.error)
            Text("身边可交互的物件",style=MaterialTheme.typography.titleMedium)
            val nearby=game.nearby()
            if(nearby.isEmpty())Text("向四周探索，走到物件旁边就能交互。")
            nearby.forEach {option->
                OutlinedButton(enabled=control&&option.enabled,onClick={onAction(AdventureAction.Interact(option.target.id))},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                    Text(option.target.title+" · 交互")
                }
                if(option.reason.isNotBlank())Text(option.reason,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("本局背包",style=MaterialTheme.typography.titleMedium)
        if(game.inventory.isEmpty())Text("还没有物品，试着拾取身边的材料。")
        game.inventory.forEach {(item,count)->Text("${item.title} × $count")}
        Text("纪念物留在本次冒险中。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text("同行记录",style=MaterialTheme.typography.titleMedium)
        val shown=if(history)game.records.drop(historyPage*20).take(20)else game.records.takeLast(6)
        shown.forEach {record->
            if(record.side==null)Text(record.text,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            else Surface(color=if(record.side==PlaySide.USER)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,shape=RoundedCornerShape(12.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(if(record.side==PlaySide.USER)game.userName else game.roleName,style=MaterialTheme.typography.labelMedium)
                    Text(record.text)
                }
            }
        }
        TextButton(onClick={history=!history},modifier=Modifier.heightIn(min=48.dp)){Text(if(history)"只看最近记录"else"查看全部记录")}
        if(history)Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            TextButton(enabled=historyPage>0,onClick={historyPage--},modifier=Modifier.heightIn(min=48.dp)){Text("前 20 条")}
            TextButton(enabled=(historyPage+1)*20<game.records.size,onClick={historyPage++},modifier=Modifier.heightIn(min=48.dp)){Text("后 20 条")}
        }
        if(!game.ended) {
            val sayReason=remember(game,draft){if(game.turn==PlaySide.USER&&draft.isNotBlank())game.capacityReason(AdventureAction.Say(PlaySide.USER,draft))else ""}
            val skipReason=remember(game){if(game.turn==PlaySide.ROLE)game.capacityReason(AdventureAction.SkipDialogue)else ""}
            val finishReason=remember(game){if(game.turn==PlaySide.USER&&AdventureFlag.LIT in game.flags)game.capacityReason(AdventureAction.Finish)else ""}
            OutlinedTextField(draft,{if(it.length<=AdventureGame.MAX_USER_TEXT)onDraft(it)},enabled=control,label={Text("想对同行的 TA 说什么")},
                modifier=Modifier.fillMaxWidth(),minLines=2,maxLines=5,supportingText={Text("${draft.length} / ${AdventureGame.MAX_USER_TEXT} · 写入后再确认模型邀请")})
            Button(enabled=control&&draft.isNotBlank()&&sayReason.isEmpty(),onClick={onAction(AdventureAction.Say(PlaySide.USER,draft))},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){Text("写入这句对白")}
            if(game.turn==PlaySide.ROLE)TextButton(enabled=enabled&&skipReason.isEmpty(),onClick={skip=true},modifier=Modifier.heightIn(min=48.dp)){Text("暂不等回复，继续探索")}
            if(AdventureFlag.LIT in game.flags)TextButton(enabled=control&&finishReason.isEmpty(),onClick={finish=true},modifier=Modifier.heightIn(min=48.dp)){Text("结束这次冒险")}
            listOf(sayReason,skipReason,finishReason).filter {it.isNotEmpty()}.distinct().forEach {Text(it,color=MaterialTheme.colorScheme.error)}
        }
    }
    if(skip)AlertDialog(onDismissRequest={skip=false},title={Text("不等待这句对白的回复？")},text={Text("已写入的对白保留，继续探索不会生成或补造角色回答。")},
        confirmButton={TextButton(enabled=enabled,onClick={onAction(AdventureAction.SkipDialogue);skip=false}){Text("继续探索")}},dismissButton={TextButton(onClick={skip=false}){Text("继续等待")}})
    if(finish)AlertDialog(onDismissRequest={finish=false},title={Text("结束这次冒险？")},text={Text("进度、纪念物和同行记录保留，结束后不能继续移动。"+if(draft.isNotBlank())"尚未写入的对白将丢失。"else "")},
        confirmButton={TextButton(enabled=control,onClick={onAction(AdventureAction.Finish);finish=false}){Text("结束并保留记录")}},dismissButton={TextButton(onClick={finish=false}){Text("继续探索")}})
}

@Composable private fun AdventureMoveButton(direction:AdventureDirection,enabled:Boolean,onAction:(AdventureAction)->Unit) {
    OutlinedButton(enabled=enabled,onClick={onAction(AdventureAction.Move(direction))},modifier=Modifier.widthIn(min=80.dp).heightIn(min=48.dp)){Text(direction.title)}
}

private fun DrawScope.atlas(image:ImageBitmap,index:Int,at:Offset,side:Float,terrain:Boolean=false) {
    val left=(index%4*image.width/4f).roundToInt();val top=(index/4*image.height/4f).roundToInt()
    val width=((index%4+1)*image.width/4f).roundToInt()-left;val height=((index/4+1)*image.height/4f).roundToInt()-top
    val inset=if(terrain)(minOf(width,height)*.18f).roundToInt()else 0
    drawImage(image,IntOffset(left+inset,top+inset),IntSize(width-inset*2,height-inset*2),IntOffset(at.x.roundToInt(),at.y.roundToInt()),
        IntSize(side.roundToInt().coerceAtLeast(1),side.roundToInt().coerceAtLeast(1)),filterQuality=FilterQuality.None)
}
@Composable private fun PixelMap(game:AdventureGame,full:Boolean) {
    val image=ImageBitmap.imageResource(R.drawable.adventure_atlas);val colors=MaterialTheme.colorScheme
    val viewport=adventureViewport(game.position,full);val visible=game.explored.getValue(game.position.area)
    val measurer=rememberTextMeasurer();val labelStyle=MaterialTheme.typography.labelSmall
    Canvas(Modifier.fillMaxWidth().aspectRatio(viewport.width.toFloat()/viewport.height).background(colors.surfaceContainerHighest)
        .semantics {contentDescription="${game.position.area.title}像素地图。我在${adventureCoordinate(game.position)}，同行在${adventureCoordinate(game.companion)}。使用下方方向按钮移动，文字地图可查看所有已探索格子。"}) {
        val cell=size.width/viewport.width
        fun point(p:AdventurePosition)=Offset((p.x-viewport.x)*cell,(p.y-viewport.y)*cell)
        clipRect {
            repeat(viewport.height){y->repeat(viewport.width){x->
                val p=AdventurePosition(game.position.area,viewport.x+x,viewport.y+y);val at=point(p)
                if(p.index in visible) {
                    val tile=AdventureWorld.tile(p)
                    val index=when(tile){'~'->1;'#'->if(p.area==AdventureArea.TOWER)3 else 2;else->0}
                    atlas(image,0,at,cell+1,true)
                    if(index!=0)atlas(image,index,at,cell+1,index==1||index==3)
                    if(tile=='.'&&!AdventureWorld.groundOpen(p,game.flags))atlas(image,if(p.area==AdventureArea.TOWER)3 else 1,at,cell+1,true)
                    if(p==AdventurePosition(AdventureArea.VILLAGE,6,5)&&AdventureFlag.BRIDGE in game.flags)atlas(image,8,at,cell)
                }else drawRect(colors.surfaceContainerHighest,at,Size(cell+1,cell+1))
            }}
            AdventureWorld.objects.filter {it.position.area==game.position.area&&it.position.index in visible&&game.visible(it)}.forEach {target->
                val index=when(target.kind) {
                    AdventureObjectKind.KEEPER->6;AdventureObjectKind.BRIDGE->8;AdventureObjectKind.WORKBENCH->10
                    AdventureObjectKind.SWITCH,AdventureObjectKind.PORTAL->11;AdventureObjectKind.LIGHTHOUSE->7
                    AdventureObjectKind.PICKUP->when(requireNotNull(target.item)) {
                        AdventureItem.PLANK->8;AdventureItem.ROPE->9;AdventureItem.GLASS->12;AdventureItem.EMBER->13;AdventureItem.CRYSTAL->14;AdventureItem.SCARF->15
                        else->error("No pickup sprite")
                    }
                }
                atlas(image,index,point(target.position),cell)
            }
            fun actor(p:AdventurePosition,index:Int,label:String,shift:Float) {
                val at=point(p)+Offset(shift,0f);atlas(image,index,at,cell)
                val text=measurer.measure(label,labelStyle)
                val center=at+Offset(cell/2,cell*.82f)
                val top=center-Offset(text.size.width/2f,0f)
                drawRect(colors.surface,top,Size(text.size.width.toFloat(),text.size.height.toFloat()))
                drawText(text,colors.onSurface,top)
            }
            actor(game.companion,5,"伴",if(game.companion==game.position)cell*.24f else 0f)
            actor(game.position,4,"我",if(game.companion==game.position)-cell*.24f else 0f)
        }
    }
}

@Composable internal fun CreateAdventureDialog(form:AdventureSetup,working:Boolean,onChange:(AdventureSetup)->Unit,onClose:()->Unit,onCreate:(String,PlayState)->Unit) {
    val title=form.title;val user=form.user;val partner=form.partner
    var discard by remember {mutableStateOf(false)}
    fun close(){if(!working){if(form.dirty)discard=true else onClose()}}
    AlertDialog(onDismissRequest=::close,title={Text("一起开始像素冒险")},text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("海边灯塔熄灭了。和 TA 一起寻找材料、探索林地，把灯带回海边。")
        OutlinedTextField(title,{if(it.length<=80)onChange(form.copy(title=it))},enabled=!working,label={Text("冒险名称")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(user,{if(it.length<=40)onChange(form.copy(user=it))},enabled=!working,label={Text("你的称呼")},modifier=Modifier.fillMaxWidth())
        OutlinedTextField(partner,{if(it.length<=40)onChange(form.copy(partner=it))},enabled=!working,label={Text("同行者称呼")},modifier=Modifier.fillMaxWidth())
        Text("移动和物品交互在本机完成。邀请角色回应对白时才使用模型，每次都需确认邀请。")
    }},confirmButton={TextButton(enabled=!working&&listOf(title,user,partner).all {it.isNotBlank()},onClick={onCreate(title,AdventurePlayState(AdventureGame.start(user,partner)))}){Text("开始冒险")}},
        dismissButton={TextButton(enabled=!working,onClick=::close){Text("取消")}})
    if(discard)AlertDialog(onDismissRequest={discard=false},title={Text("放弃尚未保存的设置？")},text={Text("冒险还未创建，已填写的名称和称呼会丢失。")},
        confirmButton={TextButton(enabled=!working,onClick=onClose){Text("放弃设置")}},dismissButton={TextButton(onClick={discard=false}){Text("继续填写")}})
}
