package com.ai.assistance.operit

import com.ai.assistance.operit.api.chat.TableGameResponses
import com.ai.assistance.operit.data.model.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TableGameResponsesTest {
    private fun invalid(block:()->Unit){try{block();fail("Expected rejection")}catch(_:ChatFailure){}}
    @Test fun roleClueCannotRevealAnswerOrInjectForeignFields() {
        val a=WordGuessGame.start(PlaySide.ROLE,WordPuzzle("物品","密钥",listOf("secret")))
        val json="""{"schemaVersion":1,"action":"clue","text":"开门的一件东西","comment":"来试试🙂"}"""
        val reply=TableGameResponses.word(a,json);assertEquals(PlaySide.ROLE,reply.move.side)
        assertEquals(PlaySide.USER,a.apply(reply.move).turn);assertTrue(a.moves.isEmpty())
        invalid {TableGameResponses.word(a,json.replace("开门的一件东西","SECRET"))}
        invalid {TableGameResponses.word(a,json.replace("来试试🙂","答案是密钥"))}
        invalid {TableGameResponses.word(a,JSONObject(json).put("tool","fetch").toString())}
        invalid {TableGameResponses.word(a,json.replace("clue","guess"))}
        invalid {TableGameResponses.word(a,json.replace("来试试🙂","x".repeat(1201)))}
        invalid {TableGameResponses.word(a,"```json\n$json\n```")}
    }
    @Test fun roleGuessAndGiveUpUseLocalResultAndNeverTrustSelfReportedVictory() {
        val a=WordGuessGame.start(PlaySide.USER,WordPuzzle("物品","秘密答案")).apply(WordMove.Clue(PlaySide.USER,"一个物品"))
        val wrong="""{"schemaVersion":1,"action":"guess","text":"不正确","comment":"我赢了"}"""
        assertEquals(PlayResult.ACTIVE,a.apply(TableGameResponses.word(a,wrong).move).result)
        invalid {TableGameResponses.word(a,JSONObject(wrong).put("correct",true).toString())}
        val quit=TableGameResponses.word(a,"""{"schemaVersion":1,"action":"give_up","comment":"这次没猜到"}""")
        assertEquals(PlayResult.USER_WIN,a.apply(quit.move).result)
        assertFalse(a.observation(PlaySide.ROLE).toString().contains("秘密答案"))
    }
    @Test fun onlyLegalCardActionsCanPassTheModelDecoder() {
        val a=FourColorGame.decode(JSONObject(FourColorGame.start(PlaySide.ROLE).encode()).put("deck",org.json.JSONArray((0..107).toList())).toString())
        val draw="""{"schemaVersion":1,"action":"draw","comment":"摸一张🙂"}"""
        assertEquals(CardMove.Draw(PlaySide.ROLE),TableGameResponses.cards(a,draw).move)
        invalid {TableGameResponses.cards(a,draw.replace("draw","pass"))}
        invalid {TableGameResponses.cards(a,JSONObject(draw).put("side","user").toString())}
        val foreign=a.hand(PlaySide.USER).first().id
        invalid {TableGameResponses.cards(a,"""{"schemaVersion":1,"action":"play","card":$foreign,"color":null,"comment":""}""")}
        invalid {TableGameResponses.cards(a,draw.replace("schemaVersion\":1","schemaVersion\":2"))}
        val chosen=a.legalMoves(PlaySide.ROLE).filterIsInstance<CardMove.Play>().first()
        run {
            val json=JSONObject().put("schemaVersion",1).put("action","play").put("card",chosen.card).put("color",chosen.color?.wire?:JSONObject.NULL).put("comment","")
            assertEquals(chosen,TableGameResponses.cards(a,json.toString()).move)
            invalid {TableGameResponses.cards(a,json.put("card",chosen.card+0.5).toString())}
        }
    }
}
