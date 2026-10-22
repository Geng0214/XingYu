package com.ai.assistance.operit

import com.ai.assistance.operit.data.model.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class XiangqiGameTest {
    private fun XiangqiGame.play(vararg moves:String)=moves.fold(this){game,move->game.move(XqMove.parse(move))}
    private fun reject(block:()->Unit){try {block();fail("Must reject invalid game data")}catch(_:IllegalArgumentException){}}

    @Test fun checkmateAndStalemateBothLoseButHaveDifferentReasons() {
        // The advanced pawn checks; both rooks cover the escape files and protect that pawn.
        val mate=XiangqiGame.fixture(XqFixtures.board("e9k","e0K","e5p","d8R","f8R","e8P",turn=XqColor.BLACK))
        assertEquals(XqOutcome(XqEnd.CHECKMATE,XqColor.RED),mate.outcome)
        val stalemate=XiangqiGame.fixture(XqFixtures.board("e9k","e0K","e5P","d8R","f8R",turn=XqColor.BLACK))
        assertFalse(stalemate.board.inCheck(XqColor.BLACK))
        assertEquals(XqOutcome(XqEnd.STALEMATE,XqColor.RED),stalemate.outcome)
        reject {stalemate.move(XqMove.parse("e9e8"))}
        reject {mate.resign(XqColor.RED)}
        reject {mate.encode()}
    }
    @Test fun thirdRepetitionWarnsAndContinuingToFourthLoses() {
        var game=XiangqiGame.fixture(XqFixtures.board("e9k","e0K","e5p","d8R"))
        repeat(2){game=game.play("d8e8","e9d9","e8d8","d9e9")}
        game=game.play("d8e8")
        assertNull(game.outcome);assertEquals(XqWarning(XqColor.RED,XqEnd.PERPETUAL_CHECK),game.warning)
        val changed=game.play("e9d9","e8a8")
        assertNull(changed.outcome);assertNull(changed.warning)
        game=game.play("e9d9","e8d8","d9e9","d8e8")
        assertEquals(XqOutcome(XqEnd.PERPETUAL_CHECK,XqColor.BLACK),game.outcome)
        assertNull(game.turn);assertNull(game.warning)
        reject {game.play("e9d9")}
    }
    @Test fun quietRepetitionDrawSurvivesStrictReplay() {
        var game=XiangqiGame.start()
        repeat(3){game=game.play("b0c2","b9c7","c2b0","c7b9")}
        game=game.play("b0c2")
        assertEquals(XqOutcome(XqEnd.REPETITION,null),game.outcome)
        val restored=XiangqiGame.decode(game.encode())
        assertEquals(game.encode(),restored.encode());assertEquals(game.outcome,restored.outcome)
        assertEquals(game.board.positionKey(),restored.board.positionKey())
        game.board.pieces().forEach {(square,piece)->assertEquals(piece,restored.board[square])}
    }
    @Test fun saveRejectsUnknownRulesIdentityWinnerIllegalMoveAndPostGameMove() {
        val base=JSONObject(XiangqiGame.start().encode())
        reject {XiangqiGame.decode(JSONObject(base.toString()).put("ruleSet","unknown").toString())}
        reject {XiangqiGame.decode(JSONObject(base.toString()).put("winner","RED").toString())}
        reject {XiangqiGame.decode(JSONObject(base.toString()).put("spaceId","foreign").toString())}
        reject {XiangqiGame.decode(JSONObject(base.toString()).put("moves",JSONArray().put("a0a9")).toString())}
        reject {XiangqiGame.decode(JSONObject(base.toString()).put("moves",JSONArray().put(42)).toString())}
        reject {XiangqiGame.decode(JSONObject(base.toString()).put("resigned","blue").toString())}
        reject {XiangqiGame.decode(" ".repeat(PlayJson.MAX_BYTES+1))}
        var ended=XiangqiGame.start()
        repeat(3){ended=ended.play("b0c2","b9c7","c2b0","c7b9")}
        ended=ended.play("b0c2")
        val extra=JSONObject(ended.encode());extra.getJSONArray("moves").put("b9c7")
        reject {XiangqiGame.decode(extra.toString())}
        reject {XiangqiGame.decode(JSONObject(ended.encode()).put("resigned","BLACK").toString())}
    }
    @Test fun resignationIsExplicitAndRecomputedOnRestore() {
        val game=XiangqiGame.start().play("a3a4").resign(XqColor.RED)
        assertEquals(XqOutcome(XqEnd.RESIGNATION,XqColor.BLACK),XiangqiGame.decode(game.encode()).outcome)
        reject {game.resign(XqColor.BLACK)}
    }
    @Test fun oneHundredNonCapturingPliesDrawEvenWhenPawnsHaveAdvanced() {
        val random=Random(73017);val seen=mutableSetOf<String>();var game=XiangqiGame.start()
        seen+=game.board.positionKey()
        // A deterministic legal line with no repeated position isolates the natural limit.
        repeat(100){index->
            val choices=game.board.legalMoves().shuffled(random)
            val move=choices.first {move->
                game.board[move.to]==null&&game.board.move(move).let {next->
                    next.positionKey() !in seen&&!next.inCheck(next.turn)&&next.legalMoves().isNotEmpty()
                }
            }
            game=game.move(move);seen+=game.board.positionKey()
            if(index<99)assertNull(game.outcome)
        }
        assertEquals(XqOutcome(XqEnd.NATURAL_LIMIT,null),game.outcome)
        assertEquals(100,game.pliesSinceCapture)
        assertTrue(game.board.pieces().any {(square,piece)->piece.kind==XqKind.PAWN&&XiangqiBoard.start().squareOf(piece.id)!=square})
        assertEquals(game.outcome,XiangqiGame.decode(game.encode()).outcome)
    }
}
