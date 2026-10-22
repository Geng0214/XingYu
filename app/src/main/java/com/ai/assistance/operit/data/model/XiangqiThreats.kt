package com.ai.assistance.operit.data.model

/** WXF 19–20. Rule verification history: evidence/E6_XIANGQI/acceptance.md. */
internal object XiangqiThreats {
    data class Threat(val attacker:Int,val victim:Int)
    enum class Nature { IDLE, CHASE, CHECK }
    data class ActionNature(val nature:Nature,val victims:Set<Int> = emptySet())

    fun captures(board:XiangqiBoard,color:XqColor):Set<Threat> = buildSet {
        board.pieces().filter {(_,p)->p.color==color&&p.kind!=XqKind.KING&&p.kind!=XqKind.PAWN}.forEach {(from,attacker)->
            board.pieces().filter {(_,p)->p.color!=color&&p.kind!=XqKind.KING}.forEach victimLoop@{(to,victim)->
                if(victim.kind==XqKind.PAWN&&!board.crossed(to,victim.color))return@victimLoop
                val move=XqMove(from,to)
                if(board.legal(move,color)&&!exchange(board,move)&&!protected(board,move))add(Threat(attacker.id,victim.id))
            }
        }
    }

    private fun exchange(board:XiangqiBoard,move:XqMove):Boolean {
        val attacker=checkNotNull(board[move.from]);val victim=checkNotNull(board[move.to])
        return attacker.kind==victim.kind&&board.legal(XqMove(move.to,move.from),victim.color)
    }

    /** Call only with a legal non-king capture. A geometric defender pinned to its king is a fake root. */
    fun protected(board:XiangqiBoard,capture:XqMove):Boolean {
        val attacker=checkNotNull(board[capture.from]);val victim=checkNotNull(board[capture.to])
        require(victim.kind!=XqKind.KING&&board.legal(capture,attacker.color))
        // WXF 20.6 explicitly treats horse/cannon chasing a rooted chariot as a chase.
        if(victim.kind==XqKind.CHARIOT&&attacker.kind in setOf(XqKind.HORSE,XqKind.CANNON))return false
        val after=board.relocate(capture,victim.color)
        return after.pieces().any {(from,defender)->
            if(defender.color!=victim.color)false else {
                // Roots of line pieces are determined BEFORE capture; otherwise removing the
                // attacker changes the cannon screen/rook line and misjudges WXF diagrams 40/41.
                val occupancy=if(defender.kind in setOf(XqKind.CHARIOT,XqKind.CANNON))board else after
                after.reaches(defender,from,capture.to,true,occupancy)&&
                    !after.relocate(XqMove(from,capture.to),attacker.color).inCheck(victim.color)
            }
        }
    }

    /** Classify the previous mover using the actual reply, never a hypothetical forced evasion. */
    fun answered(position:XiangqiBoard,reply:XqMove):ActionNature {
        require(position.legal(reply))
        if(position.inCheck(position.turn))return ActionNature(Nature.CHECK)
        val threats=captures(position,position.turn.other())
        if(threats.isEmpty())return ActionNature(Nature.IDLE)
        val after=position.move(reply)
        val victims=threats.filter {threat->
            val from=after.squareOf(threat.attacker);val to=after.squareOf(threat.victim)
            if(from<0||to<0)true else {
                val move=XqMove(from,to)
                !after.legal(move,position.turn.other())||exchange(after,move)||protected(after,move)
            }
        }.map {it.victim}.toSet()
        return if(victims.isEmpty())ActionNature(Nature.IDLE)else ActionNature(Nature.CHASE,victims)
    }
}
