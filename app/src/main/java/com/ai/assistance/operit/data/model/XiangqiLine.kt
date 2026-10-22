package com.ai.assistance.operit.data.model

/** Legal position history with WXF repetition proofs. No model-supplied status or victim IDs. */
internal class XiangqiLine private constructor(private val frames:List<Frame>) {
    enum class Violation { IDLE, CHASE, CHECK }
    data class Repetition(val red:Violation,val black:Violation) {
        val offender:XqColor? get()=when {
            red.ordinal>black.ordinal->XqColor.RED
            black.ordinal>red.ordinal->XqColor.BLACK
            else->null
        }
    }
    private data class Frame(
        val board:XiangqiBoard,
        val arrivedBy:XqMove?,
        val irreversible:Boolean,
        val replyNature:XiangqiThreats.ActionNature?=null,
    )
    val board get()=frames.last().board
    val initialPosition get()=frames.first().board.positionKey()
    val moves:List<XqMove> get()=frames.mapNotNull {it.arrivedBy}
    val pliesSinceCapture:Int get() {
        for(i in frames.lastIndex downTo 1)if(frames[i-1].board[checkNotNull(frames[i].arrivedBy).to]!=null)return frames.lastIndex-i
        return frames.lastIndex
    }
    fun append(move:XqMove):XiangqiLine {
        require(board.legal(move))
        val pawnForward=board[move.from]!!.kind==XqKind.PAWN&&move.from/9!=move.to/9
        val frame=Frame(board.move(move),move,board[move.to]!=null||pawnForward)
        // The previous mover's chase is known only after this actual reply. Do not mark the
        // pending last move as idle: that would let a perpetual chaser evade adjudication.
        val answered=frames.last().copy(replyNature=XiangqiThreats.answered(board,move))
        return XiangqiLine(frames.dropLast(1)+answered+frame)
    }

    /** Both move phases must have repeated. The current un-answered frame is never classified. */
    fun repetition(occurrences:Int):Repetition? {
        require(occurrences>=2)
        if(frames.size<3)return null
        val last=frames.lastIndex
        val previousMover=level(last,occurrences)?:return null
        val currentMover=level(last-1,occurrences)?:return null
        return if(board.turn==XqColor.RED)Repetition(currentMover,previousMover)else Repetition(previousMover,currentMover)
    }

    private fun level(index:Int,occurrences:Int):Violation? {
        if(frames[index].irreversible)return null
        val key=frames[index].board.positionKey()
        var matches=1;var allCheck=true;var allChase=true;var victims:Set<Int>?=null
        for(i in index-2 downTo 0 step 2) {
            if(frames[i].irreversible||frames[i+1].irreversible)break
            val nature=checkNotNull(frames[i].replyNature)
            allCheck=allCheck&&nature.nature==XiangqiThreats.Nature.CHECK
            allChase=allChase&&nature.nature==XiangqiThreats.Nature.CHASE
            if(allChase)victims=victims?.intersect(nature.victims)?:nature.victims
            if(frames[i].board.positionKey()==key&&++matches>=occurrences)return when {
                allCheck->Violation.CHECK
                allChase&&checkNotNull(victims).isNotEmpty()->Violation.CHASE
                else->Violation.IDLE
            }
        }
        return null
    }

    companion object {
        fun start(board:XiangqiBoard=XiangqiBoard.start())=XiangqiLine(listOf(Frame(board,null,false)))
    }
}
