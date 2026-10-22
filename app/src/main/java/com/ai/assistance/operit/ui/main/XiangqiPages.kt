package com.ai.assistance.operit.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.*
import kotlin.math.roundToInt

internal fun chessColor(color:XqColor)=if(color==XqColor.RED)"红方"else"黑方"
internal fun chessCoordinate(square:Int)="${('a'.code+square%9).toChar()}${9-square/9}"
internal fun chessPiece(piece:XqPiece)=when(piece.kind) {
    XqKind.KING->if(piece.color==XqColor.RED)"帅"else"将"
    XqKind.ADVISOR->if(piece.color==XqColor.RED)"仕"else"士"
    XqKind.ELEPHANT->if(piece.color==XqColor.RED)"相"else"象"
    XqKind.HORSE->"马";XqKind.CHARIOT->"车";XqKind.CANNON->"炮";XqKind.PAWN->if(piece.color==XqColor.RED)"兵"else"卒"
}
internal fun chessReason(reason:XqEnd)=when(reason) {
    XqEnd.CHECKMATE->"将死";XqEnd.STALEMATE->"困毙，无合法着法";XqEnd.PERPETUAL_CHECK->"持续长将"
    XqEnd.PERPETUAL_CHASE->"持续长捉";XqEnd.REPETITION->"重复局面";XqEnd.NATURAL_LIMIT->"连续 50 回合无吃子";XqEnd.RESIGNATION->"认输"
}
/** The same fixed ICCS coordinates are used by touch, accessibility controls and model actions. */
internal fun chessHit(x:Float,y:Float,width:Float,userColor:XqColor):Int? {
    if(width<=0)return null
    val cell=width/10f;val c=(x/cell-1).roundToInt();val r=(y/cell-1).roundToInt()
    if(c !in 0..8||r !in 0..9||kotlin.math.abs(x/cell-1-c)>.5f||kotlin.math.abs(y/cell-1-r)>.5f)return null
    return if(userColor==XqColor.RED)r*9+c else 89-r*9-c
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun XiangqiBoardView(state:XiangqiPlayState,enabled:Boolean,onAction:(PlayAction)->Unit) {
    val game=state.game;val interactive=enabled&&state.turn==PlaySide.USER
    var from by remember(game.encode(),interactive){mutableIntStateOf(-1)}
    var to by remember(game.encode(),interactive){mutableIntStateOf(-1)}
    var resign by remember(game.encode()){mutableStateOf(false)}
    var history by remember {mutableStateOf(false)}
    var position by remember {mutableStateOf(false)}
    var page by remember(game.moves.size){mutableIntStateOf((game.moves.size-1).coerceAtLeast(0)/20)}
    val legal=remember(game.encode()){if(game.outcome==null)game.board.legalMoves()else emptyList()}
    val destinations=legal.filter {it.from==from}.map {it.to}
    fun select(square:Int) {
        if(!interactive)return
        if(game.board[square]?.color==state.userColor){from=square;to=-1}
        else if(square in destinations)to=square
    }
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("中国象棋",style=MaterialTheme.typography.titleLarge)
        Text("你执${chessColor(state.userColor)} · 角色执${chessColor(state.userColor.other())} · 已走 ${game.moves.size} 手")
        game.outcome?.let {Text("本局结束：${chessReason(it.reason)}。",style=MaterialTheme.typography.titleMedium)}
        if(game.outcome==null&&game.board.inCheck(game.board.turn))Text("${chessColor(game.board.turn)}被将军，必须先应将。",color=MaterialTheme.colorScheme.error)
        game.warning?.let {Text("${chessColor(it.offender)}${chessReason(it.reason)}：请更换走法，继续重复将判负。",color=MaterialTheme.colorScheme.error)}
        ChessDiagram(state,from,to,destinations,interactive,::select)
        Text("红方棋子为双圈，黑方为单圈。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick={position=!position},modifier=Modifier.heightIn(min=48.dp)){Text(if(position)"收起文字棋局"else"查看完整棋局（文字）")}
        if(position) {
            XqColor.entries.forEach {side->
                Text(chessColor(side),style=MaterialTheme.typography.titleMedium)
                game.board.pieces().filter {(_,piece)->piece.color==side}.forEach {(square,piece)->
                    Text("${chessColor(side)} · ${chessPiece(piece)} · ${chessCoordinate(square)}")
                }
            }
        }
        Text("点选你的棋子，再选标出的落点；确认后才会走棋。也可以使用下方大按钮。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(interactive) {
            Text("选择棋子",style=MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                game.board.pieces().filter {(_,p)->p.color==state.userColor}.forEach {(square,piece)->
                    FilterChip(selected=from==square,onClick={select(square)},label={Text("${chessPiece(piece)} ${chessCoordinate(square)}")},modifier=Modifier.heightIn(min=48.dp))
                }
            }
            if(from>=0) {
                Text("${chessPiece(requireNotNull(game.board[from]))} ${chessCoordinate(from)} 的合法落点",style=MaterialTheme.typography.titleMedium)
                if(destinations.isEmpty())Text("这枚棋子当前不能移动，请选择其他棋子。")
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    destinations.forEach {square->FilterChip(selected=to==square,onClick={to=square},
                        label={Text(chessCoordinate(square)+game.board[square]?.let {" · 吃${chessPiece(it)}"}.orEmpty())},modifier=Modifier.heightIn(min=48.dp))}
                }
            }
            val chosen=if(from>=0&&to in destinations)XqMove(from,to)else null
            Button(enabled=chosen!=null,onClick={chosen?.let {onAction(PlayAction.Chess(PlaySide.USER,it))}},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                Text(chosen?.let {"确认 ${chessCoordinate(it.from)} → ${chessCoordinate(it.to)}"}?:"先选择棋子和落点")
            }
        }
        game.moves.lastOrNull()?.let {Text("最近一手：${it.coordinate().take(2)} → ${it.coordinate().takeLast(2)}")}
        Text("红方底线为 0，黑方底线为 9；执黑时棋盘翻转，坐标保持不变。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick={history=!history},modifier=Modifier.heightIn(min=48.dp)){Text(if(history)"收起棋谱"else"查看棋谱与规则")}
        if(history) {
            Text("将死或困毙判负。重复局面区分长将、长捉和允许的循环；违例先提示，继续重复判负。连续 50 回合没有吃子判和。",style=MaterialTheme.typography.bodyMedium)
            if(game.moves.isEmpty())Text("还没有走棋。")
            else {
                game.moves.withIndex().drop(page*20).take(20).forEach {(index,move)->Text("第 ${index+1} 手 · ${if(index%2==0)"红方"else"黑方"} · ${move.coordinate().take(2)} → ${move.coordinate().takeLast(2)}")}
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    TextButton(enabled=page>0,onClick={page--},modifier=Modifier.heightIn(min=48.dp)){Text("前 20 手")}
                    TextButton(enabled=(page+1)*20<game.moves.size,onClick={page++},modifier=Modifier.heightIn(min=48.dp)){Text("后 20 手")}
                }
            }
        }
        if(game.outcome==null)TextButton(enabled=enabled,onClick={resign=true},modifier=Modifier.heightIn(min=48.dp)){Text("认输并结束本局")}
    }
    if(resign)AlertDialog(onDismissRequest={resign=false},title={Text("确认认输？")},text={Text("本局将判角色获胜，已走的棋谱仍会保存。")},
        confirmButton={TextButton(enabled=enabled,onClick={onAction(PlayAction.ResignChess(PlaySide.USER));resign=false}){Text("确认认输")}},
        dismissButton={TextButton(onClick={resign=false}){Text("继续下棋")}})
}

@Composable private fun ChessDiagram(state:XiangqiPlayState,from:Int,to:Int,destinations:List<Int>,enabled:Boolean,onSelect:(Int)->Unit) {
    val colors=MaterialTheme.colorScheme;val measurer=rememberTextMeasurer()
    val pieceStyle=MaterialTheme.typography.titleMedium.copy(fontWeight=FontWeight.Bold)
    val labelStyle=MaterialTheme.typography.labelSmall
    Canvas(Modifier.fillMaxWidth().aspectRatio(10f/11f).background(colors.surfaceContainer,RoundedCornerShape(12.dp))
        .semantics {contentDescription="象棋棋盘，${chessColor(state.userColor)}在下方，共 ${state.game.moves.size} 手。下方文字棋局可查看双方全部棋子；轮到你时，大按钮可选择合法着法。"}
        .pointerInput(state.game.encode(),enabled,from) {if(enabled)detectTapGestures {point->chessHit(point.x,point.y,size.width.toFloat(),state.userColor)?.let(onSelect)}}) {
        val cell=size.width/10f
        fun point(square:Int):Offset {val s=if(state.userColor==XqColor.RED)square else 89-square;return Offset(cell*(1+s%9),cell*(1+s/9))}
        fun label(text:String,center:Offset,style:TextStyle,color:Color,maxWidth:Float) {
            val measured=measurer.measure(text,style,softWrap=false,maxLines=1)
            val factor=minOf(1f,maxWidth/measured.size.width.coerceAtLeast(1))
            scale(factor,pivot=center){drawText(measured,color,topLeft=center-Offset(measured.size.width/2f,measured.size.height/2f))}
        }
        repeat(10){r->drawLine(colors.outline,point(r*9),point(r*9+8))}
        repeat(9){c->
            if(c==0||c==8)drawLine(colors.outline,point(c),point(81+c))
            else {drawLine(colors.outline,point(c),point(36+c));drawLine(colors.outline,point(45+c),point(81+c))}
        }
        for(r in listOf(0,7)){drawLine(colors.outline,point(r*9+3),point((r+2)*9+5));drawLine(colors.outline,point(r*9+5),point((r+2)*9+3))}
        label("楚河",Offset(cell*3,cell*5.5f),labelStyle,colors.onSurfaceVariant,cell*2)
        label("汉界",Offset(cell*7,cell*5.5f),labelStyle,colors.onSurfaceVariant,cell*2)
        repeat(9){c->val file=if(state.userColor==XqColor.RED)c else 8-c;label(('a'.code+file).toChar().toString(),Offset(cell*(c+1),cell*10.6f),labelStyle,colors.onSurfaceVariant,cell*.7f)}
        repeat(10){r->label((if(state.userColor==XqColor.RED)9-r else r).toString(),Offset(cell*.3f,cell*(r+1)),labelStyle,colors.onSurfaceVariant,cell*.5f)}
        state.game.board.pieces().forEach {(square,piece)->
            val red=piece.color==XqColor.RED;val center=point(square)
            drawCircle(if(red)colors.errorContainer else colors.surfaceContainerHighest,cell*.43f,center)
            drawCircle(if(red)colors.onErrorContainer else colors.onSurface,cell*.43f,center,style=Stroke(1.dp.toPx()))
            if(red)drawCircle(colors.onErrorContainer,cell*.35f,center,style=Stroke(1.dp.toPx()))
            label(chessPiece(piece),center,pieceStyle,if(red)colors.onErrorContainer else colors.onSurface,cell*.65f)
        }
        destinations.forEach {square->drawCircle(colors.primary,cell*.12f,point(square))}
        if(from>=0)drawCircle(colors.primary,cell*.47f,point(from),style=Stroke(3.dp.toPx()))
        if(to>=0)drawCircle(colors.primary,cell*.47f,point(to),style=Stroke(3.dp.toPx()))
    }
}

@Composable internal fun CreateXiangqiDialog(working:Boolean,onClose:()->Unit,onCreate:(String,PlayState)->Unit) {
    var title by remember {mutableStateOf("一起下象棋")};var color by remember {mutableStateOf(XqColor.RED)}
    var discard by remember {mutableStateOf(false)}
    fun close(){if(!working){if(title!="一起下象棋"||color!=XqColor.RED)discard=true else onClose()}}
    AlertDialog(onDismissRequest=::close,title={Text("新开一局象棋")},text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(title,{if(it.length<=80)title=it},enabled=!working,label={Text("本局名称")},modifier=Modifier.fillMaxWidth())
        Text("你想执哪一方？",style=MaterialTheme.typography.titleMedium)
        XqColor.entries.forEach {choice->OutlinedButton(enabled=!working,onClick={color=choice},modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
            RadioButton(selected=color==choice,onClick=null);Spacer(Modifier.width(8.dp));Text(if(choice==XqColor.RED)"红方 · 你先走"else"黑方 · 角色先走")
        }}
        Text("创建不调用模型。轮到角色时，点击邀请并确认模型与服务地址；所有着法由本机规则校验。")
    }},confirmButton={TextButton(enabled=!working&&title.isNotBlank(),onClick={onCreate(title,XiangqiPlayState.start(color))}){Text("开始本局")}},
        dismissButton={TextButton(enabled=!working,onClick=::close){Text("取消")}})
    if(discard)AlertDialog(onDismissRequest={discard=false},title={Text("放弃本局设置？")},text={Text("本局尚未创建，已填写的名称和执色会丢失。")},
        confirmButton={TextButton(enabled=!working,onClick=onClose){Text("放弃设置")}},dismissButton={TextButton(onClick={discard=false}){Text("继续设置")}})
}
