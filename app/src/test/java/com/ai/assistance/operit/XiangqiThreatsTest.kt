package com.ai.assistance.operit

import com.ai.assistance.operit.data.model.*
import org.junit.Assert.*
import org.junit.Test

/** Named intersections keep sparse rule fixtures readable and avoid reversed FEN ranks. */
internal object XqFixtures {
    fun square(coordinate:String)=(9-coordinate[1].digitToInt())*9+(coordinate[0]-'a')
    fun board(vararg pieces:String,turn:XqColor=XqColor.RED):XiangqiBoard {
        val cells=CharArray(90){'.'}
        pieces.forEach {require(it.length==3);val s=square(it);require(cells[s]=='.');cells[s]=it[2]}
        val fen=buildString {
            repeat(10){r->if(r>0)append('/');var empty=0
                repeat(9){c->val piece=cells[r*9+c];if(piece=='.')empty++else {if(empty>0){append(empty);empty=0};append(piece)}}
                if(empty>0)append(empty)
            };append(if(turn==XqColor.RED)" w"else" b")
        }
        return XiangqiBoard.parse(fen)
    }
    fun move(text:String)=XqMove.parse(text)
    fun threat(board:XiangqiBoard,from:String,to:String)=XiangqiThreats.Threat(board[square(from)]!!.id,board[square(to)]!!.id)
}

class XiangqiThreatsTest {
    private fun b(vararg pieces:String,turn:XqColor=XqColor.RED)=XqFixtures.board(*pieces,turn=turn)
    private fun m(text:String)=XqFixtures.move(text)
    private fun t(board:XiangqiBoard,from:String,to:String)=XqFixtures.threat(board,from,to)

    @Test fun realRootProtectsButDefenderPinnedToKingDoesNot() {
        val real=b("e9k","e0K","e5p","a4R","b4h","b8r")
        assertTrue(real.legal(m("a4b4")));assertTrue(XiangqiThreats.protected(real,m("a4b4")))
        assertFalse(t(real,"a4","b4") in XiangqiThreats.captures(real,XqColor.RED))
        val fake=b("e9k","e0K","a4R","b4h","e4r","e2R")
        assertTrue(fake.legal(m("a4b4")));assertFalse(XiangqiThreats.protected(fake,m("a4b4")))
        assertTrue(t(fake,"a4","b4") in XiangqiThreats.captures(fake,XqColor.RED))
    }
    @Test fun horseAndCannonCanChaseProtectedChariot() {
        val horse=b("e9k","e0K","e5p","b2H","c4r","c8r")
        assertTrue(horse.legal(m("b2c4")));assertFalse(XiangqiThreats.protected(horse,m("b2c4")))
        assertTrue(t(horse,"b2","c4") in XiangqiThreats.captures(horse,XqColor.RED))
        val cannon=b("e9k","e0K","e5p","a2C","a4P","a6r","c6r")
        assertFalse(XiangqiThreats.protected(cannon,m("a2a6")))
    }
    @Test fun exchangeRequiresLegalReciprocalCaptureIncludingHorseLeg() {
        val mutual=b("e9k","e0K","e5p","a4R","b4r")
        assertFalse(t(mutual,"a4","b4") in XiangqiThreats.captures(mutual,XqColor.RED))
        val pinned=b("e9k","e0K","a4R","e4r","e2R")
        assertTrue(t(pinned,"a4","e4") in XiangqiThreats.captures(pinned,XqColor.RED))
        val blocked=b("e9k","e0K","e5p","b2H","c4h","c3p")
        assertTrue(t(blocked,"b2","c4") in XiangqiThreats.captures(blocked,XqColor.RED))
        val clear=b("e9k","e0K","e5p","b2H","c4h")
        assertFalse(t(clear,"b2","c4") in XiangqiThreats.captures(clear,XqColor.RED))
    }
    @Test fun kingPawnAttackersAndUncrossedPawnVictimsAreExempt() {
        val exempt=b("e9k","e1K","e5p","e2h","a5P","a6h")
        assertTrue(XiangqiThreats.captures(exempt,XqColor.RED).isEmpty())
        val home=b("e9k","e0K","e5p","b2R","b6p")
        assertFalse(t(home,"b2","b6") in XiangqiThreats.captures(home,XqColor.RED))
        val crossed=b("e9k","e0K","e5p","b2R","b4p")
        assertTrue(t(crossed,"b2","b4") in XiangqiThreats.captures(crossed,XqColor.RED))
    }
    @Test fun chaseNeedsActualEvasionBlockingOrProtection() {
        val position=b("e9k","e0K","e5p","a4R","b4c",turn=XqColor.BLACK)
        assertEquals(XiangqiThreats.Nature.IDLE,XiangqiThreats.answered(position,m("e9d9")).nature)
        val evaded=XiangqiThreats.answered(position,m("b4b5"))
        assertEquals(XiangqiThreats.Nature.CHASE,evaded.nature)
        assertEquals(setOf(position[XqFixtures.square("b4")]!!.id),evaded.victims)
        // Moving along the rook's rank still leaves the same physical victim freely capturable.
        assertEquals(XiangqiThreats.Nature.IDLE,XiangqiThreats.answered(position,m("b4c4")).nature)
        val block=b("e9k","e0K","e5p","a4R","d4h","c5c",turn=XqColor.BLACK)
        assertEquals(XiangqiThreats.Nature.CHASE,XiangqiThreats.answered(block,m("c5c4")).nature)
        val protect=b("e9k","e0K","e5p","a4R","b4h","c6r",turn=XqColor.BLACK)
        assertEquals(XiangqiThreats.Nature.CHASE,XiangqiThreats.answered(protect,m("c6b6")).nature)
    }
    @Test fun checkTakesPrecedenceAndOriginalPositionStaysImmutable() {
        val position=b("e9k","e0K","e5p","a9R","a4c",turn=XqColor.BLACK)
        val original=position.fen()
        assertEquals(XiangqiThreats.Nature.CHECK,XiangqiThreats.answered(position,m("e9e8")).nature)
        assertEquals(original,position.fen())
    }
}
