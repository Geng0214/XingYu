package com.ai.assistance.operit

import com.ai.assistance.operit.data.model.*
import org.junit.Assert.*
import org.junit.Test

class XiangqiLineTest {
    private fun line(vararg pieces:String,turn:XqColor=XqColor.RED)=XiangqiLine.start(XqFixtures.board(*pieces,turn=turn))
    private fun XiangqiLine.play(vararg moves:String)=moves.fold(this){l,m->
        assertTrue("Illegal fixture move $m in ${l.board.fen()}",l.board.legal(XqMove.parse(m)))
        l.append(XqMove.parse(m))
    }

    @Test fun harmlessKnightCycleNeedsBothPhasesAndKeepsOriginalLine() {
        val initial=XiangqiLine.start()
        var current=initial
        repeat(2){current=current.play("b0c2","b9c7","c2b0","c7b9")}
        assertNull(current.repetition(3)) // The phase after Red's move has only two occurrences.
        current=current.play("b0c2")
        assertEquals(XiangqiLine.Repetition(XiangqiLine.Violation.IDLE,XiangqiLine.Violation.IDLE),current.repetition(3))
        assertNull(current.repetition(4));assertEquals(0,initial.moves.size)
        current=current.play("b9c7","c2b0","c7b9","b0c2")
        assertNotNull(current.repetition(4));assertEquals(13,current.pliesSinceCapture)
    }
    @Test fun perpetualCheckOffenderIsCorrectForEitherColor() {
        var red=line("e9k","e0K","e5p","a8R")
        repeat(3){red=red.play("a8e8","e9d9","e8d8","d9e9","d8e8","e9d9","e8a8","d9e9")}
        // This deliberately breaks checking on a8: alternating checks/idle is permitted.
        assertEquals(XiangqiLine.Violation.IDLE,red.repetition(3)!!.red)
        var checking=line("e9k","e0K","e5p","d8R")
        repeat(3){checking=checking.play("d8e8","e9d9","e8d8","d9e9")}
        checking=checking.play("d8e8")
        assertEquals(XqColor.RED,checking.repetition(3)!!.offender)
        assertEquals(XiangqiLine.Violation.CHECK,checking.repetition(3)!!.red)
        var black=line("e9k","e0K","e5p","d1r",turn=XqColor.BLACK)
        repeat(3){black=black.play("d1e1","e0d0","e1d1","d0e0")}
        black=black.play("d1e1")
        assertEquals(XqColor.BLACK,black.repetition(3)!!.offender)
        assertEquals(XiangqiLine.Violation.CHECK,black.repetition(3)!!.black)
    }
    @Test fun samePhysicalCannonIsChasedAcrossAlternatingFiles() {
        var game=line("e9k","e0K","e5p","a4R","b6c")
        repeat(3){game=game.play("a4b4","b6a6","b4a4","a6b6")}
        game=game.play("a4b4")
        assertEquals(XiangqiLine.Repetition(XiangqiLine.Violation.CHASE,XiangqiLine.Violation.IDLE),game.repetition(3))
        assertEquals(XqColor.RED,game.repetition(4)!!.offender)
    }
    @Test fun advancingPawnBreaksRepetitionButDoesNotResetCaptureClock() {
        var game=XiangqiLine.start()
        repeat(2){game=game.play("b0c2","b9c7","c2b0","c7b9")}
        game=game.play("a3a4")
        assertNull(game.repetition(2));assertEquals(9,game.pliesSinceCapture)
        val captured=XiangqiLine.start().play("b2b9")
        assertEquals(0,captured.pliesSinceCapture);assertNull(captured.repetition(2))
    }
    @Test fun alternatingVictimsDoesNotBecomePerpetualChase() {
        var game=line("e9k","e0K","e5p","c4R","a6h","f6h")
        repeat(3){game=game.play("c4f4","f6h5","f4a4","a6c5","a4h4","h5f6","h4c4","c5a6")}
        game=game.play("c4f4")
        assertEquals(XiangqiLine.Violation.IDLE,game.repetition(3)!!.red)
        assertNull(game.repetition(3)!!.offender)
    }
}
