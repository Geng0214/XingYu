package com.ai.assistance.operit

import com.ai.assistance.operit.api.chat.*
import com.ai.assistance.operit.data.db.*
import com.ai.assistance.operit.data.model.*
import com.ai.assistance.operit.ui.main.mayInspectPlayReply
import com.ai.assistance.operit.ui.main.playSummary
import com.ai.assistance.operit.ui.main.visiblePlayComment
import com.ai.assistance.operit.ui.main.puzzleFieldErrors
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TablePlayIntegrationTest {
    @Test fun legalCardResponseCannotPublishUnplayedHandThroughComment() {
        val state=CardPlayState(FourColorGame.start(PlaySide.ROLE))
        val secret="我的未出手牌："+state.game.hand(PlaySide.ROLE).joinToString {"${it.color}:${it.face}"}
        val response=PlayGenerations.decode(state,JSONObject().put("schemaVersion",1).put("action","draw").put("comment",secret).toString())
        val next=state.apply(response.action)
        assertEquals(secret,response.comment);assertEquals("",visiblePlayComment(next,response.comment))
        assertFalse(mayInspectPlayReply(next))
    }
    @Test fun customPuzzleExplainsNormalizationDuplicatesMissingFieldsAndBounds() {
        assertNotNull(puzzleFieldErrors(WordPuzzle("",""))[0]);assertNotNull(puzzleFieldErrors(WordPuzzle("",""))[1])
        assertNotNull(puzzleFieldErrors(WordPuzzle("物品","ABC",listOf("ＡＢＣ")))[2])
        assertNotNull(puzzleFieldErrors(WordPuzzle("物品","答案",listOf("same","SAME")))[2])
        assertNotNull(puzzleFieldErrors(WordPuzzle("物品","答案",(1..9).map {it.toString()}))[2])
        assertNotNull(puzzleFieldErrors(WordPuzzle("物品","答案",listOf("a".repeat(81))))[2])
        val valid=WordPuzzle("物品","雨伞",listOf("伞"))
        assertTrue(puzzleFieldErrors(valid).all {it==null});WordGuessGame.start(PlaySide.USER,valid)
    }
    private fun invalid(block:()->Unit){try{block();fail("Expected rejection")}catch(_:IllegalArgumentException){}catch(_:ChatFailure){}}
    private fun prepared(owner:String,state:PlayState):PreparedPlayTurn {
        val role=RoleEntity("role-$owner","角色 $owner",(0..9).joinToString {"MARKER_${owner}_$it"},spaceId="space-$owner")
        val config=ModelConfigData("cfg","连接","openai","https://example.invalid/v1/chat/completions","model",60,"ref")
        val chat=ChatEntity("chat-$owner",role.id,config.id,"PRIVATE_CHAT")
        val s=PlaySession(PlayAccessScope(role.scope(),chat.id,"game-$owner"),"一起玩",state)
        return PlayGenerations.prepare(s,s.scope,s.version,role,chat,config)
    }
    @Test fun fiftyAlternatingFrozenRequestsExcludeOtherMarkersPrivateChatAndGameSecrets() {
        repeat(50) {i->
            val owner=if(i%2==0)"A"else"B";val other=if(owner=="A")"B"else"A"
            val state:PlayState=if(i%3==0)WordPlayState(WordGuessGame.start(PlaySide.USER,WordPuzzle("物品","TOP_SECRET_$i")).apply(WordMove.Clue(PlaySide.USER,"公开提示")))else CardPlayState(FourColorGame.start(PlaySide.ROLE))
            val p=prepared(owner,state);val text=p.frozen.snapshot.turns.joinToString {it.content}
            repeat(10){assertTrue(text.contains("MARKER_${owner}_$it"));assertFalse(text.contains("MARKER_${other}_$it"))}
            assertFalse(text.contains("PRIVATE_CHAT"));assertFalse(text.contains("TOP_SECRET"));assertFalse(text.contains("shuffleKey"));assertFalse(text.contains("\"deck\""))
            assertTrue(p.frozen.snapshot.tools.isEmpty())
            val source=JSONObject(p.frozen.snapshot.turns[1].content).getJSONObject("activity")
            assertEquals(PlayStorageValidation.canonical(state.observation()),PlayStorageValidation.canonical(source))
            assertEquals(state.encode(),PlayStates.decode(state.encode()).encode())
            invalid {PlaySessions.userAction(p.session,p.session.scope,p.session.version,PlayAction.Card(CardMove.Draw(PlaySide.ROLE)))}
        }
    }
    @Test fun completedAndInterruptedRequestsValidateWithoutLeakingPrivateSavesIntoContext() {
        val initial=WordPlayState(WordGuessGame.start(PlaySide.USER,WordPuzzle("物品","秘密")).apply(WordMove.Clue(PlaySide.USER,"第一条提示")))
        val p=prepared("A",initial);val response="""{"schemaVersion":1,"action":"guess","text":"猜错了","comment":"再来🙂"}"""
        val next=p.frozen.commit(p.session,PlayGenerations.decode(p.frozen,response))
        val request=PlayRequestRow.from(p.frozen,1).progress(PlayProgress(MessageState.WAITING))
            .progress(PlayProgress(MessageState.COMPLETE,response,1,2))
        val row=PlaySessionRow.from(p.session,1).changed(next,2);PlayStorageValidation.validate(row,listOf(request))
        assertFalse(playSummary(row).contains("秘密"))
        invalid {PlayStorageValidation.validate(row,listOf(request.copy(contextJson=request.contextJson.replace("物品","改写分类"))))}
        invalid {p.frozen.commit(next,PlayGenerations.decode(p.frozen,response))}
        val card=prepared("B",CardPlayState(FourColorGame.start(PlaySide.ROLE)))
        val recovered=PlaySessions.recover(card.session)
        PlayStorageValidation.validate(PlaySessionRow.from(card.session,1).changed(recovered,2),listOf(PlayRequestRow.from(card.frozen,1).interrupted()))
        assertFalse(card.frozen.accepts(recovered))
    }
    @Test fun repliesWithPossibleHiddenAnswersOrCardsStayClosedUntilGameOver() {
        val word=WordPlayState(WordGuessGame.start(PlaySide.ROLE,WordPuzzle("物品","SECRET")))
        assertFalse(mayInspectPlayReply(word));assertFalse(mayInspectPlayReply(CardPlayState(FourColorGame.start())))
        val finished=word.apply(PlayAction.Word(WordMove.Clue(PlaySide.ROLE,"一个提示"))).apply(PlayAction.Word(WordMove.GiveUp(PlaySide.USER)))
        assertTrue(mayInspectPlayReply(finished));assertTrue(playSummary(PlaySessionRow.from(PlaySession(PlayAccessScope(RoleAccessScope("r","s"),"c","a"),"题目",finished,status=PlayStatus.COMPLETE),1)).contains("SECRET"))
        val wrong=JSONObject(word.encode()).put("schemaVersion",2);invalid {PlayStates.decode(wrong.toString())}
        invalid {PlayStates.decode(JSONObject(word.encode()).put("kind","four_color").toString())}
    }
}
