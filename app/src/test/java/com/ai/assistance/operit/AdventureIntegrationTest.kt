package com.ai.assistance.operit

import com.ai.assistance.operit.api.chat.*
import com.ai.assistance.operit.data.db.*
import com.ai.assistance.operit.data.model.*
import com.ai.assistance.operit.ui.main.adventureViewport
import com.ai.assistance.operit.ui.main.playSummary
import com.ai.assistance.operit.ui.main.PlayUi
import com.ai.assistance.operit.ui.main.AdventureSetup
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AdventureIntegrationTest {
    @Test fun retainedEditorStateCannotApplyLateDraftToAnotherActivity() {
        val role=RoleAccessScope("a","space-a")
        val initial=PlayUi("page-a",role,"chat-a",selectedId="activity-a")
        val form=AdventureSetup(partner="同行者").copy(title="未提交的冒险")
        val editing=initial.withDraft("activity-a","未写入的对白").copy(adventureForm=form)
        val refreshed=editing.copy(activities=emptyList())
        assertEquals("未写入的对白",refreshed.draft);assertEquals(form,refreshed.adventureForm);assertTrue(form.dirty)
        val other=refreshed.selected("activity-b")
        assertEquals("",other.draft);assertSame(other,other.withDraft("activity-a","旧回调"))
        val newRole=PlayUi("page-b",RoleAccessScope("b","space-b"),"chat-b")
        assertEquals("",newRole.draft);assertNull(newRole.adventureForm)
    }
    private val answer="""{"schemaVersion":1,"action":"say","text":"我陪你一起探索🙂"}"""
    private fun invalid(block:()->Unit){try{block();fail("Expected rejection")}catch(_:ChatFailure){}catch(_:IllegalArgumentException){}}
    private fun prepare(owner:String):PreparedPlayTurn {
        val role=RoleEntity("role-$owner","同名",(0..9).joinToString {"MARK_${owner}_$it"},spaceId="space-$owner",userPersona="PRIVATE_PERSONA",personaEnabled=true)
        val cfg=ModelConfigData("cfg","连接","openai","https://example.invalid/v1/chat/completions","model",60,"ref")
        val chat=ChatEntity("chat-$owner",role.id,cfg.id,"PRIVATE_CHAT")
        val game=AdventureGame.start("USER-$owner","同名").apply(AdventureAction.Interact("wood_a")).apply(AdventureAction.Say(PlaySide.USER,"一起走🙂"))
        val s=PlaySession(PlayAccessScope(role.scope(),chat.id,"game-$owner"),"冒险",AdventurePlayState(game))
        return PlayGenerations.prepare(s,s.scope,s.version,role,chat,cfg)
    }
    @Test fun fiftyActualRequestContextsFreezeOnlyOwnedDiscoveredWorld() {
        repeat(50){n->
            val owner=if(n%2==0)"A"else"B";val other=if(owner=="A")"B"else"A";val p=prepare(owner)
            val content=p.frozen.snapshot.turns.joinToString {it.content}
            repeat(10){assertTrue(content.contains("MARK_${owner}_$it"));assertFalse(content.contains("MARK_${other}_$it"))}
            assertFalse(content.contains("PRIVATE_CHAT"));assertFalse(content.contains("PRIVATE_PERSONA"));assertTrue(p.frozen.snapshot.tools.isEmpty())
            assertFalse(content.contains("星光灯芯"));assertFalse(content.contains("遗落的灯罩"))
            val projected=JSONObject(p.frozen.snapshot.turns.last().content).getJSONObject("activity")
            assertEquals(PlayStorageValidation.canonical(p.session.state.observation()),PlayStorageValidation.canonical(projected))
            invalid {PlaySessions.userAction(p.session,p.session.scope,p.session.version,PlayAction.Adventure(AdventureAction.Move(AdventureDirection.EAST)))}
            assertFalse(p.frozen.accepts(p.session.copy(scope=p.session.scope.copy(role=RoleAccessScope("other","space-other")))))
        }
    }
    @Test fun completedDialogueRequiresExactlyItsOwnRequestAndWorldDoesNotMove() {
        val p=prepare("A");val initial=(p.session.state as AdventurePlayState).game
        val response=PlayGenerations.decode(p.frozen,answer);val next=p.frozen.commit(p.session,response)
        val q=PlayRequestRow.from(p.frozen,1).progress(PlayProgress(MessageState.WAITING))
            .progress(PlayProgress(MessageState.COMPLETE,answer,1,2))
        val row=PlaySessionRow.from(p.session,1).changed(next,2);PlayStorageValidation.validate(row,listOf(q))
        val game=(next.state as AdventurePlayState).game
        assertEquals(initial.position,game.position);assertEquals(initial.inventory,game.inventory);assertEquals(1,game.roleActionCount)
        assertTrue(playSummary(row).contains("像素冒险"))
        invalid {PlayStorageValidation.validate(row,emptyList())}
        invalid {PlayStorageValidation.validate(row,listOf(q.copy(contextJson=q.contextJson.replace("USER-A","USER-B"))))}
        invalid {p.frozen.commit(next,response)}
        invalid {p.frozen.commit(p.session,PlayResponse(PlayAction.Adventure(AdventureAction.Interact("rope")),""))}
        invalid {PlayGenerations.decode(p.frozen,JSONObject(answer).put("inventory","forged").toString())}
    }
    @Test fun interruptedDialogueCanBeExplicitlySkippedButCannotFabricateRoleProgress() {
        val p=prepare("A");var current=PlaySessions.recover(p.session)
        val failed=PlayRequestRow.from(p.frozen,1).interrupted()
        current=PlaySessions.resume(current,current.scope,current.version)
        current=PlaySessions.userAction(current,current.scope,current.version,PlayAction.Adventure(AdventureAction.SkipDialogue))
        current=PlaySessions.userAction(current,current.scope,current.version,PlayAction.Adventure(AdventureAction.Move(AdventureDirection.EAST)))
        val row=PlaySessionRow.from(p.session,1).changed(current,2);PlayStorageValidation.validate(row,listOf(failed))
        assertEquals(0,(current.state as AdventurePlayState).game.roleActionCount)
        assertFalse(p.frozen.accepts(current));invalid {p.frozen.commit(current,PlayGenerations.decode(p.frozen,answer))}
        assertEquals(current.state.encode(),PlayStates.decode(current.state.encode()).encode())
        invalid {PlaySessions.userAction(current,current.scope,current.version,PlayAction.Adventure(AdventureAction.Say(PlaySide.ROLE,"假的回应")))}
        invalid {PlayStates.decode(JSONObject(current.state.encode()).put("schemaVersion",4).toString())}
    }
    @Test fun cameraAlwaysIncludesBothFollowersAndStaysInsideAllThreeMaps() {
        for(area in AdventureArea.entries)for(y in 1..9)for(x in 1..11) {
            val p=AdventurePosition(area,x,y);val v=adventureViewport(p,false)
            assertTrue(p.x in v.x until v.x+v.width&&p.y in v.y until v.y+v.height)
            assertTrue(v.x>=0&&v.y>=0&&v.x+v.width<=13&&v.y+v.height<=11)
            AdventureDirection.entries.forEach {d->val neighbor=p.step(d);assertTrue(neighbor.x in v.x until v.x+v.width&&neighbor.y in v.y until v.y+v.height)}
            val full=adventureViewport(p,true);assertEquals(13,full.width);assertEquals(11,full.height)
        }
    }
}
