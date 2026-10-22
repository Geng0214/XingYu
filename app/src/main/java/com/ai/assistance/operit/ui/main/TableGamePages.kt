package com.ai.assistance.operit.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.*

internal fun colorName(color:CardColor)=when(color){CardColor.RED->"红色";CardColor.YELLOW->"黄色";CardColor.GREEN->"绿色";CardColor.BLUE->"蓝色"}
internal fun cardName(card:ColorCard)=(card.color?.let {colorName(it)+" · "}?:"")+when(card.face){10->"跳过";11->"反转";12->"摸二";13->"万能";14->"万能摸四";else->card.face.toString()}

internal fun puzzleFieldErrors(puzzle:WordPuzzle):List<String?> {
    fun textError(value:String,label:String)=when {
        value.isBlank()->"请填写$label。"
        value.length>80->"${label}最多 80 字符。"
        runCatching {PlayJson.text(value,80)}.isFailure->"${label}含有无效字符，请重新输入。"
        else->null
    }
    val solutions=(listOf(puzzle.answer)+puzzle.aliases).map(WordGuessGame::normalize)
    val aliases=when {
        puzzle.aliases.size>8->"最多填写 8 个别名，每行一个。"
        puzzle.aliases.any {textError(it,"别名")!=null}->"每个别名须为 1～80 字符，请检查是否含有无效字符。"
        solutions.distinct().size!=solutions.size->"别名不能重复或与答案相同；大小写、全角与半角按同一文字处理。"
        else->null
    }
    return listOf(textError(puzzle.category,"分类"),textError(puzzle.answer,"答案"),aliases)
}

@Composable internal fun WordBoard(game:WordGuessGame,enabled:Boolean,draft:String,onDraft:(String)->Unit,onMove:(WordMove)->Unit) {
    val userSets=game.setter==PlaySide.USER
    Text("${game.category} · ${if(userSets)"你来描述"else"你来猜词"}",style=MaterialTheme.typography.titleLarge)
    Text("剩余 ${WordGuessGame.MAX_GUESSES-game.guesses} 次猜测 · ${if(game.source=="builtin-v1")"本机题库"else"自定义题目"}",Modifier.padding(vertical=8.dp))
    if(userSets||game.result!=PlayResult.ACTIVE) {
        val visible=game.observation(PlaySide.USER)
        Text("答案：${visible.getString("answer")}",style=MaterialTheme.typography.titleMedium)
        val aliases=visible.getJSONArray("aliases")
        if(aliases.length()>0)Text("也接受："+(0 until aliases.length()).joinToString("、"){aliases.getString(it)})
    }
    game.moves.forEachIndexed {index,move->
        val label=if(move.side==PlaySide.USER)"我"else"角色"
        val text=when(move){is WordMove.Clue->"提示：${move.text}";is WordMove.Guess->"猜测：${move.text}";is WordMove.GiveUp->"放弃本题"}
        Text("${index+1} · $label · $text",Modifier.padding(vertical=8.dp))
    }
    if(game.result==PlayResult.ACTIVE&&game.turn==PlaySide.USER) {
        val limit=if(userSets)600 else 80
        val move=if(userSets)WordMove.Clue(PlaySide.USER,draft)else WordMove.Guess(PlaySide.USER,draft)
        val valid=remember(game,draft){runCatching {game.apply(move)}.isSuccess}
        OutlinedTextField(draft,{if(it.length<=limit)onDraft(it)},enabled=enabled,label={Text(if(userSets)"描述这个词"else"输入一个答案")},
            supportingText={Text(if(userSets)"提示不能直接包含答案或别名。"else"按文字和别名判定，最多 80 字符。")},modifier=Modifier.fillMaxWidth(),maxLines=5)
        Button(enabled=enabled&&valid,onClick={onMove(move)}){Text(if(userSets)"提交提示"else"确认猜测")}
        if(!userSets) {
            var giveUp by remember(game){mutableStateOf(false)}
            TextButton(enabled=enabled,onClick={giveUp=true}){Text("放弃并揭晓")}
            if(giveUp)AlertDialog(onDismissRequest={giveUp=false},title={Text("放弃这道题？")},text={Text("本题将结束并显示答案，已发生的提示和猜测保留。"+if(draft.isNotBlank())"尚未提交的答案会丢失。"else "")},
                confirmButton={TextButton(enabled=enabled,onClick={onMove(WordMove.GiveUp(PlaySide.USER));giveUp=false}){Text("揭晓答案")}},dismissButton={TextButton(onClick={giveUp=false}){Text("再想想")}})
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun CardsBoard(game:FourColorGame,enabled:Boolean,onMove:(CardMove)->Unit) {
    var selected by remember(game){mutableStateOf<Int?>(null)}
    var chosenColor by remember(game,selected){mutableStateOf<CardColor?>(null)}
    val legal=game.legalMoves(PlaySide.USER)
    Text("${cardName(game.top)}",style=MaterialTheme.typography.headlineSmall)
    Text("当前颜色：${colorName(game.color)}",Modifier.padding(vertical=8.dp),style=MaterialTheme.typography.titleMedium)
    Text("角色 ${game.handCount(PlaySide.ROLE)} 张 · 你 ${game.handCount(PlaySide.USER)} 张 · 牌堆 ${game.drawCount} 张")
    if(game.handCount(PlaySide.USER)==1||game.handCount(PlaySide.ROLE)==1)Text("${if(game.handCount(PlaySide.USER)==1)"你"else"角色"}只剩一张牌了",Modifier.padding(vertical=8.dp),color=MaterialTheme.colorScheme.primary)
    if(game.result!=PlayResult.ACTIVE)Text("本局得分：${game.score}",Modifier.padding(vertical=12.dp),style=MaterialTheme.typography.titleLarge)
    Text("你的手牌",Modifier.padding(top=16.dp,bottom=8.dp),style=MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        game.hand(PlaySide.USER).forEach {card->
            val playable=legal.any {it is CardMove.Play&&it.card==card.id}
            FilterChip(selected=selected==card.id,onClick={selected=card.id},
                label={Text(cardName(card)+if(playable)" · 可出"else "")},modifier=Modifier.heightIn(min=48.dp))
        }
    }
    selected?.let {id->
        val card=FourColorGame.card(id)
        Text("准备出：${cardName(card)}",Modifier.padding(top=12.dp))
        if(card.color==null)FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            CardColor.entries.forEach {color->FilterChip(selected=chosenColor==color,onClick={chosenColor=color},enabled=enabled,label={Text(colorName(color))},modifier=Modifier.heightIn(min=48.dp))}
        }
        val move=CardMove.Play(PlaySide.USER,id,chosenColor)
        Button(enabled=enabled&&move in legal,onClick={onMove(move)}){Text("确认出牌")}
    }
    if(game.result==PlayResult.ACTIVE&&game.turn==PlaySide.USER) {
        val draw=CardMove.Draw(PlaySide.USER);val pass=CardMove.Pass(PlaySide.USER)
        if(draw in legal)OutlinedButton(enabled=enabled,onClick={onMove(draw)}){Text("摸一张")}
        if(pass in legal) {
            Text("刚摸到的牌可以出。也可以保留它并结束回合。",Modifier.padding(vertical=8.dp))
            OutlinedButton(enabled=enabled,onClick={onMove(pass)}){Text("保留并结束回合")}
        }
    }
    Text("双人跳过、反转和罚摸后本方继续；不叠加罚摸。万能摸四要求没有当前颜色的手牌。",Modifier.padding(vertical=12.dp),style=MaterialTheme.typography.bodySmall)
    var history by remember {mutableStateOf(false)}
    TextButton(onClick={history=!history}){Text(if(history)"收起出牌记录"else"查看最近 20 步")}
    if(history)game.history.takeLast(20).forEach {e->Text("${if(e.side==PlaySide.USER)"我"else"角色"}："+when(e.action){
        "play"->"出 ${cardName(FourColorGame.card(requireNotNull(e.card)))} · ${colorName(requireNotNull(e.color))}"+if(e.drawn>0)"，对方摸 ${e.drawn} 张"else ""
        "draw"->"摸 ${e.drawn} 张";else->"结束回合"
    },Modifier.padding(vertical=6.dp))}
}

@Composable internal fun CreateTableDialog(cards:Boolean,working:Boolean,onClose:()->Unit,onCreate:(String,PlayState)->Unit) {
    var title by remember {mutableStateOf(if(cards)"一起玩四色卡牌"else"一起猜词")}
    var userFirst by remember {mutableStateOf(true)}
    var custom by remember {mutableStateOf(false)}
    var category by remember {mutableStateOf("")};var answer by remember {mutableStateOf("")};var aliases by remember {mutableStateOf("")}
    var discard by remember {mutableStateOf(false)}
    val dirty=title!=(if(cards)"一起玩四色卡牌"else"一起猜词")||!userFirst||custom||category.isNotEmpty()||answer.isNotEmpty()||aliases.isNotEmpty()
    val close:()->Unit={if(!working){if(dirty)discard=true else onClose()}}
    val puzzle=remember(category,answer,aliases){WordPuzzle(category,answer,aliases.lines().map(String::trim).filter(String::isNotEmpty))}
    val errors=remember(puzzle){puzzleFieldErrors(puzzle)}
    val titleError=if(runCatching {PlayJson.text(title,120)}.isFailure)"请填写 1～120 字符的活动名称，避免无效字符。"else null
    val valid=titleError==null&&(!custom||!userFirst||cards||errors.all {it==null})
    AlertDialog(onDismissRequest=close,title={Text(if(cards)"新建四色卡牌"else"新建猜词")},text={Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(title,{if(it.length<=120)title=it},enabled=!working,label={Text("活动名称")},isError=titleError!=null,supportingText={titleError?.let {Text(it)}})
        listOf(true to if(cards)"你先手"else"你描述，角色猜",false to if(cards)"角色先手"else"角色描述，你猜").forEach {(value,label)->
            Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable(enabled=!working){userFirst=value},verticalAlignment=Alignment.CenterVertically){RadioButton(selected=userFirst==value,onClick=null);Text(label)}
        }
        if(!cards&&userFirst) {
            Row(verticalAlignment=Alignment.CenterVertically){Checkbox(custom,{custom=it},enabled=!working);Text("使用自定义题目")}
            if(custom) {
                OutlinedTextField(category,{if(it.length<=80)category=it},enabled=!working,label={Text("分类")},isError=errors[0]!=null,supportingText={errors[0]?.let {Text(it)}})
                OutlinedTextField(answer,{if(it.length<=80)answer=it},enabled=!working,label={Text("答案")},isError=errors[1]!=null,supportingText={errors[1]?.let {Text(it)}})
                OutlinedTextField(aliases,{if(it.length<=648)aliases=it},enabled=!working,label={Text("可接受别名，每行一个，最多 8 个")},maxLines=5,isError=errors[2]!=null,supportingText={errors[2]?.let {Text(it)}})
            }
        }
        Text(if(cards)"双方各 7 张，先出完获胜。单局计分，应用自动提示剩一张，不抢喊处罚。"else"本机题库共 ${WordPuzzleCatalog.size} 个词。每题最多 8 次猜测；角色猜词时不会收到答案。")
        Text("创建不调用模型；每次邀请角色前单独确认。")
    }},confirmButton={TextButton(enabled=!working&&valid,onClick={
        val state=if(cards)CardPlayState(FourColorGame.start(if(userFirst)PlaySide.USER else PlaySide.ROLE))else
            WordPlayState(if(custom&&userFirst)WordGuessGame.start(PlaySide.USER,puzzle)else WordPuzzleCatalog.start(if(userFirst)PlaySide.USER else PlaySide.ROLE))
        onCreate(title,state)
    }){Text("创建")}},dismissButton={TextButton(enabled=!working,onClick=close){Text("返回")}})
    if(discard)AlertDialog(onDismissRequest={discard=false},title={Text("放弃未保存的设置？")},text={Text("活动还没有创建，返回会丢失这些设置。")},
        confirmButton={TextButton(onClick=onClose){Text("放弃")}},dismissButton={TextButton(onClick={discard=false}){Text("继续编辑")}})
}
