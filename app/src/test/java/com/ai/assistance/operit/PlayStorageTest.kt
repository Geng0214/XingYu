package com.ai.assistance.operit

import com.ai.assistance.operit.api.chat.*
import com.ai.assistance.operit.data.db.*
import com.ai.assistance.operit.data.model.*
import org.junit.Assert.*
import org.junit.Test

class PlayStorageTest {
    private val role=RoleEntity("role","同名","人设",spaceId="space")
    private val config=ModelConfigData("cfg","连接","openai","https://example.invalid/v1/chat/completions","model",60,"ref")
    private val chat=ChatEntity("chat",role.id,config.id,"会话")
    private val scope=PlayAccessScope(role.scope(),chat.id,"play")
    private val json="""{"schemaVersion":1,"action":"place","row":7,"column":7,"comment":"轮到你🙂"}"""
    private fun prepare()=PlayGenerations.prepare(PlaySession(scope,"棋局",GomokuState.start(PlaySide.ROLE)),scope,1,role,chat,config,"request")
    private fun rejected(block:()->Unit){try{block();fail("Expected invalid storage data")}catch(_:IllegalArgumentException){}catch(_:ChatFailure){}}
    @Test fun completedHistoryMatchesConfirmedBoardAndDetectsMissingOrForgedHistory() {
        val p=prepare();val pending=PlaySessionRow.from(p.session,100)
        var q=PlayRequestRow.from(p.frozen,100);PlayStorageValidation.validate(pending,listOf(q))
        q=q.progress(PlayProgress(MessageState.WAITING)).progress(PlayProgress(MessageState.GENERATING,json,firstTokenMs=2,elapsedMs=3))
        val complete=q.progress(PlayProgress(MessageState.COMPLETE,json,2,4))
        val row=pending.changed(p.frozen.commit(p.session,PlayGenerations.decode(p.frozen,json)),104)
        PlayStorageValidation.validate(row,listOf(complete))
        rejected {PlayStorageValidation.validate(row,emptyList())}
        rejected {PlayStorageValidation.validate(row,listOf(complete.copy(beforeState=GomokuState.start(PlaySide.USER).encode())))}
        rejected {PlayStorageValidation.validate(row,listOf(complete.copy(content=json.replace("\"row\":7","\"row\":8"))))}
        rejected {complete.progress(PlayProgress(MessageState.GENERATING,json+"late",firstTokenMs=2,elapsedMs=5))}
    }
    @Test fun partialAndFailedRecordsPreserveTextAndMonotonicTiming() {
        val p=prepare();var q=PlayRequestRow.from(p.frozen,100).progress(PlayProgress(MessageState.WAITING))
        q=q.progress(PlayProgress(MessageState.GENERATING,"invalid",2,5))
        rejected {q.progress(PlayProgress(MessageState.GENERATING,"invalid",3,6))}
        rejected {q.progress(PlayProgress(MessageState.GENERATING,"edited",2,6))}
        val failed=q.progress(PlayProgress(MessageState.FAILED,"invalid",2,6,"PLAY_RESPONSE"))
        val row=PlaySessionRow.from(p.session,100).changed(p.frozen.fail(p.session,false),106)
        PlayStorageValidation.validate(row,listOf(failed))
        assertTrue(q.matches(p.frozen))
    }
    @Test fun recoveryClearsOnlyPendingTokenAndRetainsPartialTextAndExactSave() {
        val p=prepare();val row=PlaySessionRow.from(p.session,100)
        val q=PlayRequestRow.from(p.frozen,100).progress(PlayProgress(MessageState.WAITING))
            .progress(PlayProgress(MessageState.GENERATING,"{\"schemaVersion\":1",firstTokenMs=0,elapsedMs=4))
        val restored=row.changed(PlaySessions.recover(row.value()),200)
        val interrupted=q.interrupted();PlayStorageValidation.validate(restored,listOf(interrupted))
        assertNull(restored.requestId);assertEquals(row.stateJson,restored.stateJson);assertEquals(q.content,interrupted.content)
        assertEquals("INTERRUPTED",interrupted.errorCode);assertFalse(p.frozen.accepts(restored.value()))
    }
    @Test fun malformedIdentifiersAndUnicodeCannotBecomeStoredRows() {
        val p=prepare();val q=PlayRequestRow.from(p.frozen,100)
        rejected {q.copy(roleId="\uD800").validate()}
        rejected {q.copy(contextJson="[]").validate()}
        rejected {q.copy(state=MessageState.GENERATING,content="\uD800",firstTokenMs=0).validate()}
    }
}
