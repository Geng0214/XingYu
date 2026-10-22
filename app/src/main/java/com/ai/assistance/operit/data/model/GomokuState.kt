package com.ai.assistance.operit.data.model

import org.json.JSONArray
import org.json.JSONObject

/** 15x15 freestyle: five or more wins, no forbidden-move rules. Results are always computed locally. */
class GomokuState private constructor(val first:PlaySide,moves:List<PlayAction.Place>):PlayState {
    override val kind="gomoku"
    val moves=PlayJson.frozen(moves)
    private val board=IntArray(SIZE*SIZE)
    override val result:PlayResult
    override val turn:PlaySide?
    init {
        require(moves.size<=SIZE*SIZE)
        var next=first;var outcome=PlayResult.ACTIVE
        for(move in this.moves) {
            require(outcome==PlayResult.ACTIVE&&move.side==next&&move.row in 0 until SIZE&&move.column in 0 until SIZE)
            val at=move.row*SIZE+move.column;require(board[at]==0)
            val stone=if(move.side==PlaySide.USER)1 else 2;board[at]=stone
            if(DIRECTIONS.any {(dr,dc)->1+count(move.row,move.column,dr,dc,stone)+count(move.row,move.column,-dr,-dc,stone)>=5})
                outcome=if(move.side==PlaySide.USER)PlayResult.USER_WIN else PlayResult.ROLE_WIN
            next=next.other()
        }
        result=if(outcome==PlayResult.ACTIVE&&moves.size==SIZE*SIZE)PlayResult.DRAW else outcome
        turn=if(result==PlayResult.ACTIVE)next else null
    }
    private fun count(row:Int,column:Int,dr:Int,dc:Int,stone:Int):Int {
        var r=row+dr;var c=column+dc;var n=0
        while(r in 0 until SIZE&&c in 0 until SIZE&&board[r*SIZE+c]==stone){n++;r+=dr;c+=dc}
        return n
    }
    fun cell(row:Int,column:Int):PlaySide? {
        require(row in 0 until SIZE&&column in 0 until SIZE)
        return when(board[row*SIZE+column]){1->PlaySide.USER;2->PlaySide.ROLE;else->null}
    }
    override fun apply(action:PlayAction):GomokuState {
        if(action !is PlayAction.Place)throw ChatFailure("PLAY_ACTION")
        return try {GomokuState(first,moves+action)}catch(_:IllegalArgumentException){throw ChatFailure("PLAY_ACTION")}
    }
    override fun encode():String=PlayJson.base(kind).put("first",first.wire).put("moves",JSONArray().apply {
        moves.forEach {put(JSONObject().put("side",it.side.wire).put("row",it.row).put("column",it.column))}
    }).toString()
    override fun observation():JSONObject=JSONObject().put("game",kind).put("size",SIZE).put("rule","freestyle-five-or-more")
        .put("first",first.wire).put("turn",turn?.wire?:JSONObject.NULL).put("result",result.name)
        .put("board",JSONArray().apply {for(row in 0 until SIZE)put((0 until SIZE).joinToString(""){column->when(cell(row,column)){PlaySide.USER->"U";PlaySide.ROLE->"R";null->"."}})})
        .put("movesPlayed",moves.size)
    companion object {
        const val SIZE=15
        private val DIRECTIONS=listOf(1 to 0,0 to 1,1 to 1,1 to -1)
        fun start(first:PlaySide=PlaySide.USER)=GomokuState(first,emptyList())
        internal fun restore(value:JSONObject):GomokuState {
            PlayJson.keys(value,"schemaVersion","kind","first","moves")
            val list=PlayJson.array(value,"moves");require(list.length()<=SIZE*SIZE)
            return GomokuState(PlayJson.side(PlayJson.string(value,"first")),(0 until list.length()).map {i->
                val move=list.getJSONObject(i);PlayJson.keys(move,"side","row","column")
                PlayAction.Place(PlayJson.side(PlayJson.string(move,"side")),PlayJson.integer(move,"row",0 until SIZE),PlayJson.integer(move,"column",0 until SIZE))
            })
        }
    }
}
