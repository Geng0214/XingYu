package com.ai.assistance.operit

import com.ai.assistance.operit.data.model.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WordGuessGameTest {
    private fun invalid(block:()->Unit){try{block();fail("Expected rejection")}catch(_:IllegalArgumentException){}catch(_:ChatFailure){}}
    private fun start(setter:PlaySide=PlaySide.USER)=WordGuessGame.start(setter,WordPuzzle("物品","HiddenAnswer",listOf("别名")))
    @Test fun answerAndAliasesAreHiddenOnlyFromTheGuesserUntilTheRoundEnds() {
        val a=start();val b=WordGuessGame.start(PlaySide.USER,WordPuzzle("物品","OtherSecret",listOf("其它")))
        assertEquals(a.observation(PlaySide.ROLE).toString(),b.observation(PlaySide.ROLE).toString())
        assertFalse(a.observation(PlaySide.ROLE).toString().contains("HiddenAnswer"))
        assertFalse(a.observation(PlaySide.ROLE).has("aliases"));assertNull(a.revealedAnswer())
        assertEquals("HiddenAnswer",a.observation(PlaySide.USER).getString("answer"))
        val ended=a.apply(WordMove.Clue(PlaySide.USER,"It is a thing")).apply(WordMove.Guess(PlaySide.ROLE,"别名"))
        assertTrue(ended.solved);assertEquals(PlayResult.ROLE_WIN,ended.result);assertNull(ended.turn)
        assertEquals("HiddenAnswer",ended.revealedAnswer());assertTrue(ended.observation(PlaySide.ROLE).has("answer"))
    }
    @Test fun nfkcCaseAndTrimDefineCorrectnessWithoutSemanticGuessing() {
        val a=WordGuessGame.start(PlaySide.ROLE,WordPuzzle("ASCII","Café",listOf("ABC")))
        val clue=a.apply(WordMove.Clue(PlaySide.ROLE,"可以喝的东西"))
        assertTrue(clue.apply(WordMove.Guess(PlaySide.USER," Cafe\u0301 ")).solved)
        assertTrue(clue.apply(WordMove.Guess(PlaySide.USER,"ＡＢＣ")).solved)
        assertFalse(clue.apply(WordMove.Guess(PlaySide.USER,"咖啡")).solved)
        invalid {a.apply(WordMove.Clue(PlaySide.ROLE,"包含 ＡＢＣ 的提示"))}
        invalid {WordGuessGame.start(PlaySide.USER,WordPuzzle("x","ABC",listOf("ＡＢＣ")))}
    }
    @Test fun wrongSideOrderLeakingCluesAndMalformedTextAreRejected() {
        val a=start();invalid {a.apply(WordMove.Guess(PlaySide.ROLE,"x"))}
        invalid {a.apply(WordMove.Clue(PlaySide.ROLE,"x"))};invalid {a.apply(WordMove.GiveUp(PlaySide.USER))}
        invalid {a.apply(WordMove.Clue(PlaySide.USER,"The HIDDENANSWER is here"))}
        invalid {a.apply(WordMove.Clue(PlaySide.USER,"x".repeat(601)))}
        invalid {a.apply(WordMove.Clue(PlaySide.USER,"\uD800"))}
        invalid {a.apply(WordMove.Clue(PlaySide.USER,"x\u0000"))}
        val said=a.apply(WordMove.Clue(PlaySide.USER,"一个提示🙂"))
        invalid {said.apply(WordMove.Clue(PlaySide.USER,"另一个提示"))}
        invalid {said.apply(WordMove.Guess(PlaySide.USER,"x"))}
    }
    @Test fun eightWrongGuessesOrGiveUpFinishWithoutAnExtraPaidTurn() {
        var a=start()
        repeat(8){a=a.apply(WordMove.Clue(PlaySide.USER,"提示 $it")).apply(WordMove.Guess(PlaySide.ROLE,"猜测 $it"))}
        assertEquals(8,a.guesses);assertEquals(PlayResult.USER_WIN,a.result);assertNull(a.turn);assertFalse(a.solved)
        invalid {a.apply(WordMove.Clue(PlaySide.USER,"不再继续"))}
        val giveUp=start(PlaySide.ROLE).apply(WordMove.Clue(PlaySide.ROLE,"第一条提示")).apply(WordMove.GiveUp(PlaySide.USER))
        assertEquals(PlayResult.ROLE_WIN,giveUp.result);assertEquals("HiddenAnswer",giveUp.revealedAnswer())
    }
    @Test fun strictSaveRoundTripRejectsUnknownSchemaTypesAndTamperedOrder() {
        val a=start().apply(WordMove.Clue(PlaySide.USER,"提示"));assertEquals(a.encode(),WordGuessGame.decode(a.encode()).encode())
        assertTrue(start().isPrefixOf(a));assertFalse(a.isPrefixOf(start()))
        listOf(JSONObject(a.encode()).put("schemaVersion",2),JSONObject(a.encode()).put("kind","four_color"),
            JSONObject(a.encode()).put("extra","x"),JSONObject(a.encode()).put("source","url")).forEach {v->invalid {WordGuessGame.decode(v.toString())}}
        val bad=JSONObject(a.encode());bad.getJSONArray("moves").getJSONObject(0).put("side","role")
        invalid {WordGuessGame.decode(bad.toString())}
        val aliases=mutableListOf("another");val s=WordGuessGame.start(PlaySide.USER,WordPuzzle("thing","secret",aliases));aliases.clear()
        assertEquals(1,s.observation(PlaySide.USER).getJSONArray("aliases").length())
    }
    @Test fun localCatalogHasFrozenOriginalPuzzlesAndFiftyAlternatingHiddenViews() {
        assertEquals(24,WordPuzzleCatalog.size)
        repeat(50){i->val setter=if(i%2==0)PlaySide.USER else PlaySide.ROLE
            val a=WordPuzzleCatalog.start(setter);assertEquals("builtin-v1",a.source)
            assertFalse(a.observation(setter.other()).has("answer"));assertFalse(a.observation(setter.other()).has("aliases"))
            assertTrue(a.observation(setter).getString("answer").isNotBlank())
            assertEquals(a.encode(),WordGuessGame.decode(a.encode()).encode())
        }
    }
}
