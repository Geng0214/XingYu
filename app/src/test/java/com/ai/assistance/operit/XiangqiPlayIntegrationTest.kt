package com.ai.assistance.operit

import com.ai.assistance.operit.api.chat.*
import com.ai.assistance.operit.data.db.*
import com.ai.assistance.operit.data.model.*
import com.ai.assistance.operit.ui.main.chessCoordinate
import com.ai.assistance.operit.ui.main.chessHit
import com.ai.assistance.operit.ui.main.playSummary
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class XiangqiPlayIntegrationTest {
    private fun invalid(block:()->Unit){try {block();fail("Expected rejection")}catch(_:IllegalArgumentException){}catch(_:ChatFailure){}}
    private fun prepared(owner:String,state:XiangqiPlayState):PreparedPlayTurn {
        val role=RoleEntity("role-$owner","同名",(0..9).joinToString {"CHESS_${owner}_$it"},spaceId="space-$owner",userPersona="PRIVATE_PERSONA",personaEnabled=true)
        val config=ModelConfigData("cfg","连接","openai","https://example.invalid/v1/chat/completions","model",60,"ref")
        val chat=ChatEntity("chat-$owner",role.id,config.id,"PRIVATE_CHAT")
        val s=PlaySession(PlayAccessScope(role.scope(),chat.id,"game-$owner"),"象棋",state)
        return PlayGenerations.prepare(s,s.scope,s.version,role,chat,config)
    }
    private fun step(side:PlaySide,move:String)=PlayAction.Chess(side,XqMove.parse(move))
    private fun response(move:String)=JSONObject().put("schemaVersion",1).put("action","move").put("move",move).put("comment","轮到你了🙂").toString()
    @Test fun colorBindingControlsTurnsResignationAndRoleActionCount() {
        val red=XiangqiPlayState.start();assertEquals(PlaySide.USER,red.turn)
        invalid {red.apply(step(PlaySide.ROLE,"a3a4"))}
        val after=red.apply(step(PlaySide.USER,"a3a4")).apply(step(PlaySide.ROLE,"a6a5"))
        assertEquals(1,after.roleActionCount);assertEquals(PlaySide.USER,after.turn)
        val ended=after.apply(PlayAction.ResignChess(PlaySide.USER))
        assertEquals(PlayResult.ROLE_WIN,ended.result);assertEquals(1,ended.roleActionCount)
        assertEquals(ended.encode(),PlayStates.decode(ended.encode()).encode())
        var black=XiangqiPlayState.start(XqColor.BLACK);assertEquals(PlaySide.ROLE,black.turn)
        black=black.apply(step(PlaySide.ROLE,"a3a4")).apply(step(PlaySide.USER,"a6a5")).apply(PlayAction.ResignChess(PlaySide.ROLE))
        assertEquals(PlayResult.USER_WIN,black.result);assertEquals(2,black.roleActionCount)
        invalid {black.apply(step(PlaySide.ROLE,"c3c4"))}
        assertFalse(after.isPrefixOf(after.copy(userColor=XqColor.BLACK)))
    }
    @Test fun fiftyAlternatingRequestsFreezeOnlyOwnedBoardAndDeclaredRolePersona() {
        repeat(50){i->
            val owner=if(i%2==0)"A"else"B";val other=if(owner=="A")"B"else"A"
            val state=if(i%2==0)XiangqiPlayState.start().apply(step(PlaySide.USER,"a3a4"))else XiangqiPlayState.start(XqColor.BLACK)
            val p=prepared(owner,state);val text=p.frozen.snapshot.turns.joinToString {it.content}
            repeat(10){assertTrue(text.contains("CHESS_${owner}_$it"));assertFalse(text.contains("CHESS_${other}_$it"))}
            assertFalse(text.contains("PRIVATE_CHAT"));assertFalse(text.contains("PRIVATE_PERSONA"));assertTrue(p.frozen.snapshot.tools.isEmpty())
            val projection=JSONObject(p.frozen.snapshot.turns[1].content).getJSONObject("activity")
            assertEquals(PlayStorageValidation.canonical(state.observation()),PlayStorageValidation.canonical(projection))
            assertFalse(p.frozen.accepts(p.session.copy(state=state.copy(userColor=state.userColor.other()))))
            invalid {PlaySessions.userAction(p.session,p.session.scope,p.session.version,step(PlaySide.ROLE,"a3a4"))}
        }
    }
    @Test fun strictModelReplyRejectsInventedStateOutOfTurnAndIllegalMoves() {
        val state=XiangqiPlayState.start(XqColor.BLACK)
        val good=response("a3a4");assertEquals(step(PlaySide.ROLE,"a3a4"),PlayGenerations.decode(state,good).action)
        listOf("a0a9","a0a0","j0a0","a6a5").forEach {invalid {PlayGenerations.decode(state,response(it))}}
        for(field in listOf("side","winner","roleId","spaceId","tools"))invalid {PlayGenerations.decode(state,JSONObject(good).put(field,"foreign").toString())}
        invalid {PlayGenerations.decode(XiangqiPlayState.start(),good)}
        invalid {PlayStates.decode(JSONObject(state.encode()).put("schemaVersion",3).toString())}
    }
    @Test fun persistedMoveOrResignationNeedsExactlyItsCompletedRequestAndCannotCommitTwice() {
        for(body in listOf(response("a3a4"),"""{"schemaVersion":1,"action":"resign","comment":"这局我认输"}""")) {
            val p=prepared("A",XiangqiPlayState.start(XqColor.BLACK))
            val next=p.frozen.commit(p.session,PlayGenerations.decode(p.frozen,body))
            val request=PlayRequestRow.from(p.frozen,1).progress(PlayProgress(MessageState.WAITING))
                .progress(PlayProgress(MessageState.COMPLETE,body,1,2))
            val row=PlaySessionRow.from(p.session,1).changed(next,2)
            PlayStorageValidation.validate(row,listOf(request))
            assertTrue(playSummary(row).contains("中国象棋"))
            invalid {PlayStorageValidation.validate(row,emptyList())}
            invalid {PlayStorageValidation.validate(row,listOf(request.copy(contextJson=request.contextJson.replace("BLACK","RED"))))}
            invalid {p.frozen.commit(next,PlayGenerations.decode(p.frozen,body))}
        }
        val pending=prepared("B",XiangqiPlayState.start(XqColor.BLACK));val recovered=PlaySessions.recover(pending.session)
        PlayStorageValidation.validate(PlaySessionRow.from(pending.session,1).changed(recovered,2),listOf(PlayRequestRow.from(pending.frozen,1).interrupted()))
        assertFalse(pending.frozen.accepts(recovered))
    }
    @Test fun boardTouchUsesAllNinetyIntersectionsForBothOrientations() {
        for(color in XqColor.entries)for(row in 0..9)for(col in 0..8) {
            val square=if(color==XqColor.RED)row*9+col else 89-row*9-col
            assertEquals(square,chessHit((col+1)*32f,(row+1)*32f,320f,color))
            assertEquals(square,XqFixtures.square(chessCoordinate(square)))
        }
        assertNull(chessHit(0f,0f,320f,XqColor.RED));assertNull(chessHit(999f,99f,320f,XqColor.RED))
    }
}
