package com.ai.assistance.operit.ui.main

import com.ai.assistance.operit.api.chat.FrozenPlayTurn
import com.ai.assistance.operit.data.db.*
import com.ai.assistance.operit.data.model.*

data class PlayUi(val token:String,val role:RoleAccessScope,val chatId:String,
    val activities:List<PlaySessionRow> = emptyList(),val selectedId:String?=null,val view:PlayView?=null,
    val approval:FrozenPlayTurn?=null,val error:String?=null,val draft:String="",val adventureForm:AdventureSetup?=null,
    val reward:PlayRewardPending?=null) {
    fun selected(id:String?)=copy(selectedId=id,view=null,approval=null,error=null,draft="",reward=null)
    fun withDraft(id:String?,text:String)=if(id!=null&&id==selectedId)copy(draft=text)else this
}
data class PlayRewardPending(val id:String,val preview:HomeRewardPreview,val error:String?=null)

// Private text lives only in the ViewModel. Do not put it in a plaintext saved-state Bundle.
data class AdventureSetup(val title:String="把灯带回海边",val user:String="我",val partner:String,val originalPartner:String=partner) {
    val dirty get()=title!="把灯带回海边"||user!="我"||partner!=originalPartner
}

internal fun playSummary(row:PlaySessionRow):String {
    val state=row.value().state
    val detail=when(state) {
        is AdventurePlayState->"像素冒险 · ${state.game.userName} 与 ${state.game.roleName}\n当前：${state.game.position.area.title}\n${state.game.objective}\n"+
            state.game.inventory.entries.joinToString {"${it.key.title} × ${it.value}"}+"\n"+
            state.game.records.takeLast(4).joinToString("\n"){it.text}
        is XiangqiPlayState->"中国象棋 · 我执${chessColor(state.userColor)}，共 ${state.game.moves.size} 手。"+
            state.game.outcome?.let {"结束原因：${chessReason(it.reason)}。"}.orEmpty()
        is WordPlayState->"猜词 · ${state.game.category}，已猜 ${state.game.guesses} 次。"+state.game.revealedAnswer()?.let {"答案：$it。"}.orEmpty()
        is CardPlayState->"四色卡牌 · 我剩 ${state.game.handCount(PlaySide.USER)} 张，角色剩 ${state.game.handCount(PlaySide.ROLE)} 张，本局得分 ${state.game.score}。"
        is DateStoryState->"地点：${state.place}\n"+state.turns.takeLast(6).joinToString("\n") {"${if(it.side==PlaySide.USER)"我"else"角色"}：${it.text}"}
        is GomokuState->"五子棋，共 ${state.moves.size} 手。"+state.moves.lastOrNull()?.let {"最后一手：第 ${it.row+1} 行，第 ${it.column+1} 列。"}.orEmpty()
        is MemoryPairsState->"翻牌：我配对 ${state.userPairs} 组，角色配对 ${state.rolePairs} 组。"
        is JourneyState->"${if(state.mode==JourneyKind.CITY)"城市漫游"else"隐秘之约"} · ${state.userName} 与 ${state.roleName}\n当前：${state.places[state.current].name} · 已到访 ${state.visited.size}/${state.places.size} 处\n"+
            state.records.filter {it.side!=null}.takeLast(6).joinToString("\n") {"${state.places[it.place].name} · ${if(it.side==PlaySide.USER)state.userName else state.roleName}：${it.text}"}
    }
    return "【共同活动：${row.title}】\n${playStatus(row.value())}\n$detail"
}
internal fun mayInspectPlayReply(state:PlayState)=state.result!=PlayResult.ACTIVE||when(state) {
    is WordPlayState->state.game.setter==PlaySide.USER
    is CardPlayState->false
    else->true
}
// A legal card move does not make free-form text safe: it may name the role's unplayed cards.
internal fun visiblePlayComment(state:PlayState,comment:String)=if(state is CardPlayState&&state.result==PlayResult.ACTIVE)""else comment
internal fun playStatus(session:PlaySession):String=when {
    session.requestId!=null->"对方正在思考…"
    session.status==PlayStatus.INTERRUPTED->"已中断 · 进度已保留"
    session.status==PlayStatus.PAUSED->"已暂停"
    session.state.result==PlayResult.USER_WIN->"你赢了"
    session.state.result==PlayResult.ROLE_WIN->"角色获胜"
    session.state.result==PlayResult.DRAW->"平局"
    session.state.result==PlayResult.ENDED->"活动已结束"
    session.state is MemoryPairsState&&session.state.open.size==2->"记住这两张，再翻回"
    session.state.turn==PlaySide.USER->"轮到你了"
    else->"轮到对方 · 点击邀请继续"
}
