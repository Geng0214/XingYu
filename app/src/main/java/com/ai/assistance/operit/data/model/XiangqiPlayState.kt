package com.ai.assistance.operit.data.model

import org.json.JSONArray
import org.json.JSONObject

/** Side binding belongs to the activity and cannot change when a model request is in flight. */
data class XiangqiPlayState(val userColor:XqColor,val game:XiangqiGame):PlayState {
    override val kind="xiangqi"
    fun side(color:XqColor)=if(color==userColor)PlaySide.USER else PlaySide.ROLE
    fun color(side:PlaySide)=if(side==PlaySide.USER)userColor else userColor.other()
    override val turn get()=game.turn?.let(::side)
    override val result get()=game.outcome?.let {outcome->
        outcome.winner?.let {if(side(it)==PlaySide.USER)PlayResult.USER_WIN else PlayResult.ROLE_WIN}?:PlayResult.DRAW
    }?:PlayResult.ACTIVE
    val roleActionCount get()=(game.moves.size+if(userColor==XqColor.BLACK)1 else 0)/2+
        if(game.resigned==userColor.other())1 else 0
    override fun encode()=JSONObject().put("schemaVersion",4).put("kind",kind).put("userColor",userColor.name)
        .put("game",JSONObject(game.encode())).toString().also {require(it.toByteArray(Charsets.UTF_8).size<=PlayJson.MAX_BYTES)}
    override fun observation()=JSONObject().put("game",kind).put("rules",XiangqiGame.RULE_SET)
        .put("userColor",userColor.name).put("roleColor",userColor.other().name)
        .put("turn",turn?.wire?:JSONObject.NULL).put("result",result.name).put("fen",game.board.fen())
        .put("moves",JSONArray(game.moves.map {it.coordinate()}))
        .put("legalMoves",JSONArray(if(result==PlayResult.ACTIVE)game.board.legalMoves().map {it.coordinate()}else emptyList<String>()))
        .put("pliesSinceCapture",game.pliesSinceCapture)
        .put("outcome",game.outcome?.let {JSONObject().put("reason",it.reason.name).put("winner",it.winner?.let(::side)?.wire?:JSONObject.NULL)}?:JSONObject.NULL)
        .put("warning",game.warning?.let {JSONObject().put("reason",it.reason.name).put("offender",side(it.offender).wire)}?:JSONObject.NULL)
    override fun apply(action:PlayAction):XiangqiPlayState=try {
        val next=when(action) {
            is PlayAction.Chess->{require(turn==action.side);game.move(action.move)}
            is PlayAction.ResignChess->{require(action.side==PlaySide.USER||turn==PlaySide.ROLE);game.resign(color(action.side))}
            else->throw IllegalArgumentException()
        }
        copy(game=next)
    }catch(_:IllegalArgumentException){throw ChatFailure("PLAY_ACTION")}
    fun isPrefixOf(other:XiangqiPlayState)=userColor==other.userColor&&game.isPrefixOf(other.game)
    companion object {fun start(userColor:XqColor=XqColor.RED)=XiangqiPlayState(userColor,XiangqiGame.start())}
}
