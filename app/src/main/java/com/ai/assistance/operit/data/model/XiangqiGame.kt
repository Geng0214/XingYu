package com.ai.assistance.operit.data.model

import org.json.JSONArray
import org.json.JSONObject

enum class XqEnd { CHECKMATE, STALEMATE, PERPETUAL_CHECK, PERPETUAL_CHASE, REPETITION, NATURAL_LIMIT, RESIGNATION }
data class XqOutcome(val reason:XqEnd,val winner:XqColor?)
data class XqWarning(val offender:XqColor,val reason:XqEnd)

/** Immutable game; saves contain moves, never trusted board state or a claimed winner. */
class XiangqiGame private constructor(private val line:XiangqiLine,val outcome:XqOutcome?,val resigned:XqColor?) {
    val board get()=line.board
    val moves get()=line.moves
    val pliesSinceCapture get()=line.pliesSinceCapture
    val turn:XqColor? get()=if(outcome==null)board.turn else null
    val warning:XqWarning? get() {
        if(outcome!=null)return null
        val repeated=line.repetition(3)?:return null
        val offender=repeated.offender?:return null
        val violation=if(offender==XqColor.RED)repeated.red else repeated.black
        return XqWarning(offender,if(violation==XiangqiLine.Violation.CHECK)XqEnd.PERPETUAL_CHECK else XqEnd.PERPETUAL_CHASE)
    }
    fun move(move:XqMove):XiangqiGame {
        require(outcome==null&&moves.size<MAX_PLIES)
        val next=line.append(move)
        return XiangqiGame(next,adjudicate(next),null)
    }
    fun resign(color:XqColor):XiangqiGame {
        require(outcome==null)
        return XiangqiGame(line,XqOutcome(XqEnd.RESIGNATION,color.other()),color)
    }
    fun isPrefixOf(other:XiangqiGame)=line.initialPosition==other.line.initialPosition&&other.moves.take(moves.size)==moves&&
        (outcome==null||other.moves==moves&&other.outcome==outcome&&other.resigned==resigned)
    fun encode():String {
        require(line.initialPosition==XiangqiBoard.start().positionKey())
        return JSONObject().put("schemaVersion",1).put("ruleSet",RULE_SET)
            .put("moves",JSONArray(moves.map {it.coordinate()})).put("resigned",resigned?.name?:JSONObject.NULL)
            .toString().also {require(it.toByteArray(Charsets.UTF_8).size<=PlayJson.MAX_BYTES)}
    }

    companion object {
        const val RULE_SET="wxf2018-app1"
        const val MAX_PLIES=4096
        fun start()=XiangqiGame(XiangqiLine.start(),null,null)
        /** Only rule tests can start a sparse position; production imports always replay the opening. */
        internal fun fixture(board:XiangqiBoard):XiangqiGame {
            val line=XiangqiLine.start(board)
            return XiangqiGame(line,adjudicate(line),null)
        }
        fun decode(text:String):XiangqiGame {
            val value=PlayJson.objectValue(text)
            PlayJson.keys(value,"schemaVersion","ruleSet","moves","resigned")
            require(PlayJson.integer(value,"schemaVersion",1..1)==1&&PlayJson.string(value,"ruleSet")==RULE_SET)
            val moves=PlayJson.array(value,"moves");require(moves.length()<=MAX_PLIES)
            var game=start()
            repeat(moves.length()){i->val raw=moves.get(i);require(raw is String);game=game.move(XqMove.parse(raw))}
            val resigned=value.get("resigned")
            if(resigned!=JSONObject.NULL){require(resigned is String);game=game.resign(XqColor.valueOf(resigned))}
            return game
        }
        private fun adjudicate(line:XiangqiLine):XqOutcome? {
            val board=line.board
            // Mate/stalemate wins take precedence over a draw counter reached by the same move.
            if(board.legalMoves().isEmpty())return XqOutcome(
                if(board.inCheck(board.turn))XqEnd.CHECKMATE else XqEnd.STALEMATE,board.turn.other())
            val repeated=line.repetition(4)
            if(repeated!=null) {
                val offender=repeated.offender?:return XqOutcome(XqEnd.REPETITION,null)
                val violation=if(offender==XqColor.RED)repeated.red else repeated.black
                return XqOutcome(if(violation==XiangqiLine.Violation.CHECK)XqEnd.PERPETUAL_CHECK else XqEnd.PERPETUAL_CHASE,offender.other())
            }
            if(line.pliesSinceCapture>=100)return XqOutcome(XqEnd.NATURAL_LIMIT,null)
            return null
        }
    }
}
