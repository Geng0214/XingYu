package com.ai.assistance.operit

import com.ai.assistance.operit.data.model.*
import org.junit.Assert.*
import org.junit.Test

class XiangqiBoardTest {
    private fun board(fen:String)=XiangqiBoard.parse(fen)
    private fun legal(board:XiangqiBoard,move:String)=board.legal(XqMove.parse(move))
    private fun perft(board:XiangqiBoard,depth:Int):Long=if(depth==0)1 else board.legalMoves().sumOf {perft(board.move(it),depth-1)}
    @Test fun initialPositionPerftAndCoordinateRoundTrip() {
        val b=XiangqiBoard.start();assertEquals(32,b.pieces().size);assertEquals(XqColor.RED,b.turn)
        assertEquals(XiangqiBoard.INITIAL,b.fen());assertFalse(b.inCheck(XqColor.RED));assertFalse(b.inCheck(XqColor.BLACK))
        assertEquals(44,perft(b,1));assertEquals(1920,perft(b,2));assertEquals(79666,perft(b,3))
        b.legalMoves().forEach {assertEquals(it,XqMove.parse(it.coordinate()))}
    }
    @Test fun horseLegElephantEyeRiverPalaceAndPawnDirection() {
        val b=XiangqiBoard.start()
        assertTrue(legal(b,"b0c2"));assertFalse(legal(b,"b0d1"));assertFalse(legal(b,"c0c2"))
        assertTrue(legal(b,"c0e2"));assertFalse(legal(b,"d0c1"));assertTrue(legal(b,"d0e1"))
        assertFalse(legal(b,"e0e2"));assertFalse(legal(b,"a3a2"));assertFalse(legal(b,"a3b3"));assertTrue(legal(b,"a3a4"))
        val blocked=board("4k4/9/9/9/4p4/9/3P5/4E4/3P5/2E1K4 w")
        assertFalse(legal(blocked,"c0e2"));assertFalse(legal(blocked,"e2c4")) // own piece/eye
        val crossed=board("4k4/9/9/9/4P4/9/9/9/9/3K5 w")
        assertTrue(legal(crossed,"e5d5"));assertTrue(legal(crossed,"e5e6"));assertFalse(legal(crossed,"e5e4"))
        val elephant=board("4k4/9/9/9/4p4/2E6/9/9/9/4K4 w")
        assertFalse(legal(elephant,"c4e6"))
    }
    @Test fun cannonNeedsExactlyOneScreenAndRookCannotJump() {
        val b=XiangqiBoard.start()
        assertTrue(legal(b,"b2b9"));assertFalse(legal(b,"b2b7"));assertTrue(legal(b,"b2b6"));assertFalse(legal(b,"a0a4"))
        val two=board("4k4/9/1r7/1p7/1p2p4/9/9/1C7/9/4K4 w")
        assertFalse(legal(two,"b2b7"));assertTrue(legal(two,"b2b6"))
    }
    @Test fun pinnedPiecesCannotExposeGeneralsAndCheckMustBeAnswered() {
        val pin=board("4k4/9/9/9/4R4/9/9/9/9/4K4 w")
        assertFalse(legal(pin,"e5d5"));assertTrue(legal(pin,"e5e6"));assertFalse(legal(pin,"e5e9"))
        // Put the rook on the king's rank: an unrelated move must not resolve check.
        val check=board("4k4/9/9/9/4p4/9/9/9/4R4/r2K5 w")
        assertTrue(check.inCheck(XqColor.RED));assertFalse(legal(check,"e1e2"));assertTrue(legal(check,"d0d1"))
    }
    @Test fun hypotheticalMovesKeepOriginalBoardAndPhysicalPieceIdentity() {
        val b=XiangqiBoard.start();val m=XqMove.parse("b2b9");val id=b[m.from]!!.id
        val after=b.move(m)
        assertEquals(32,b.pieces().size);assertEquals(31,after.pieces().size)
        assertEquals(id,after[m.to]!!.id);assertEquals(m.to,after.squareOf(id));assertEquals(XiangqiBoard.INITIAL,b.fen())
        assertEquals(after.positionKey(),board(after.fen()).positionKey())
        try {XqMove.parse("a0j0");fail()}catch(_:IllegalArgumentException){}
        try {board("9/9/9/9/9/9/9/9/9/9 w");fail()}catch(_:IllegalArgumentException){}
    }
}
