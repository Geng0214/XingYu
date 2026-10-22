package com.ai.assistance.operit

import com.ai.assistance.operit.data.model.*
import org.junit.Assert.*
import org.junit.Test

/** Positions transcribed from WXF 2018; each test names its official diagram.
 * Only factual board placements/move outcomes are used; no third-party engine/test code. */
class XiangqiWxfExamplesTest {
    private fun repeated(board:XiangqiBoard,entry:String,cycle:List<String>):XiangqiLine {
        var line=XiangqiLine.start(board).append(XqMove.parse(entry))
        repeat(4){cycle.forEach {text->
            assertTrue("$text in ${line.board.fen()}",line.board.legal(XqMove.parse(text)))
            line=line.append(XqMove.parse(text))
        }}
        return line
    }
    @Test fun diagram4SimultaneousCrossChecksAreEqualViolations() {
        val board=XqFixtures.board("e9k","d9a","f9r","f8c","e7e","f0K","e0C","e6R","b7P")
        val line=repeated(board,"e6e7",listOf("f8e8","e7f7","e8f8","f7e7"))
        assertEquals(XiangqiLine.Repetition(XiangqiLine.Violation.CHECK,XiangqiLine.Violation.CHECK),line.repetition(4))
        assertNull(line.repetition(4)!!.offender)
    }
    @Test fun diagram34PerpetualMatingThreatDoesNotExcuseRookChasingCannon() {
        val board=XqFixtures.board("e9k","d9a","f9a","h7c","g0p","e0K","e1A","d0A","g8R")
        val line=repeated(board,"g8h8",listOf("h7c7","h8c8","c7h7","c8h8"))
        assertEquals(XiangqiLine.Repetition(XiangqiLine.Violation.CHASE,XiangqiLine.Violation.IDLE),line.repetition(4))
        assertEquals(XqColor.RED,line.repetition(4)!!.offender)
    }
    @Test fun diagram36BlackAlternatesHorseVictimsWhileRedChasesOneCannon() {
        val board=XqFixtures.board(
            "e9k","d9a","f9a","c9e","g9e","b9r","c7c","i7h","e6p","i6p","a5p",
            "e0K","d0A","e1A","c0E","g0E","c2H","g2H","h4R","a3P","c3P","e3P","g3P","i3P")
        val line=repeated(board,"h4c4",listOf("c7g7","c4g4","g7c7","g4c4"))
        assertEquals(XiangqiLine.Repetition(XiangqiLine.Violation.CHASE,XiangqiLine.Violation.IDLE),line.repetition(4))
    }
    @Test fun diagram40CannonAndRookChasePinnedRookWithoutReciprocalChase() {
        var board=XqFixtures.board(
            "e9k","d9a","e8a","c9e","e7e","f8r","g7h","c6p","i6p","g5p","e3p","i3c",
            "e0K","e1A","f0A","e2E","g0E","a8R","b9C","g8H","c4P","g3P")
        // Enter the same repeating central segment shown in diagram 40.
        board=board.move(XqMove.parse("b9b8"))
        val blackRook=board[XqFixtures.square("f8")]!!.id
        val seen=mutableListOf<XiangqiThreats.ActionNature>()
        listOf("e8f9","b8b7","f9e8","b7b8","e8f9","b8b7","f9e8","b7b8","e8f9").forEach {text->
            val reply=XqMove.parse(text);assertTrue(text,board.legal(reply))
            seen+=XiangqiThreats.answered(board,reply);board=board.move(reply)
        }
        // Every Red action chases the SAME black chariot, with either a cannon or a rook.
        seen.filterIndexed {i,_->i%2==0}.forEach {assertEquals(XiangqiThreats.Nature.CHASE,it.nature);assertTrue(blackRook in it.victims)}
        // When the cannon protects the horse, vacating the rook's square cannot remove that root.
        // On the other half-turn Red does not resolve the potential capture, so it is also idle.
        seen.filterIndexed {i,_->i%2==1}.forEach {assertEquals(XiangqiThreats.Nature.IDLE,it.nature)}
    }
    @Test fun diagram41HorseAndCannonChaseTheSameRookAcrossDifferentHorseSquares() {
        var board=XqFixtures.board(
            "e9k","d9a","f9a","c9e","e7e","f8r","g7h","c5p","g5p","i6p",
            "e0K","e1A","f0A","a8C","a6H","g8H","c3P","g3P","i4P")
        board=board.move(XqMove.parse("a6b8"))
        val victim=board[XqFixtures.square("f8")]!!.id
        listOf("f9e8","b8c6","e8f9","c6d8","f9e8","d8c6","e8f9","c6b8","f9e8").forEachIndexed {i,text->
            val reply=XqMove.parse(text);assertTrue(text,board.legal(reply))
            val nature=XiangqiThreats.answered(board,reply)
            if(i%2==0){assertEquals(text,XiangqiThreats.Nature.CHASE,nature.nature);assertTrue(text,victim in nature.victims)}
            else assertEquals(text,XiangqiThreats.Nature.IDLE,nature.nature)
            board=board.move(reply)
        }
    }
}
