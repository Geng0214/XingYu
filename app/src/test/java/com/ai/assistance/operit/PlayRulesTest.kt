package com.ai.assistance.operit

import com.ai.assistance.operit.data.model.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PlayRulesTest {
    private fun rejected(code:String="PLAY_ACTION",block:()->Unit) {
        try {block();fail("Expected $code")}catch(e:ChatFailure){assertEquals(code,e.code)}
    }
    private fun roundTrip(state:PlayState) {
        val restored=PlayStates.decode(state.encode())
        assertEquals(state.encode(),restored.encode());assertEquals(state.turn,restored.turn);assertEquals(state.result,restored.result)
        assertEquals(state.observation().toString(),restored.observation().toString())
    }
    private fun memory(deck:List<Int>,first:PlaySide=PlaySide.USER)=PlayStates.decode(
        JSONObject().put("schemaVersion",1).put("kind","memory").put("first",first.wire).put("deck",JSONArray(deck)).put("actions",JSONArray()).toString()
    ) as MemoryPairsState

    @Test fun fiveInEachDirectionIsLocalTerminalAndCannotBeOverwritten() {
        listOf(1 to 0,0 to 1,1 to 1,1 to -1).forEach {(dr,dc)->
            var state=GomokuState.start()
            repeat(5){i->
                state=state.apply(PlayAction.Place(PlaySide.USER,5+i*dr,7+i*dc))
                if(i<4)state=state.apply(PlayAction.Place(PlaySide.ROLE,0,i*2))
                roundTrip(state)
            }
            assertEquals(PlayResult.USER_WIN,state.result);assertNull(state.turn)
            rejected {state.apply(PlayAction.Place(PlaySide.ROLE,14,14))}
        }
    }
    @Test fun bridgeCanWinWithSixAndRoleCanStartAndWin() {
        var state=GomokuState.start(PlaySide.ROLE)
        listOf(0,1,2,4,5,3).forEachIndexed {i,c->
            state=state.apply(PlayAction.Place(PlaySide.ROLE,7,c))
            if(i<5)state=state.apply(PlayAction.Place(PlaySide.USER,i*2,14))
        }
        assertEquals(PlayResult.ROLE_WIN,state.result);roundTrip(state)
    }
    @Test fun fullBoardWithoutFiveIsDrawIncludingBoundaryDiagonals() {
        val user=mutableListOf<Pair<Int,Int>>();val role=mutableListOf<Pair<Int,Int>>()
        for(r in 0..14)for(c in 0..14)if((r+c/2)%2==0)user+=r to c else role+=r to c
        var state=GomokuState.start()
        repeat(225){i->val side=if(i%2==0)PlaySide.USER else PlaySide.ROLE
            val at=if(side==PlaySide.USER)user[i/2]else role[i/2]
            state=state.apply(PlayAction.Place(side,at.first,at.second))
            if(i<224)assertEquals(PlayResult.ACTIVE,state.result)
        }
        assertEquals(PlayResult.DRAW,state.result);roundTrip(state)
    }
    @Test fun illegalMovesLeaveOriginalUntouchedAndSavedHistoryIsImmutable() {
        val original=GomokuState.start().apply(PlayAction.Place(PlaySide.USER,3,3))
        val before=original.encode()
        listOf(PlayAction.Place(PlaySide.USER,3,4),PlayAction.Place(PlaySide.ROLE,3,3),
            PlayAction.Place(PlaySide.ROLE,-1,0),PlayAction.Place(PlaySide.ROLE,0,15),PlayAction.Flip(PlaySide.ROLE,0)).forEach {rejected {original.apply(it)}}
        assertEquals(before,original.encode())
        try {(original.moves as MutableList<PlayAction.Place>).clear();fail()}catch(_:UnsupportedOperationException){}
        val fork=original.apply(PlayAction.Place(PlaySide.ROLE,3,4))
        assertNull(original.cell(3,4));assertEquals(PlaySide.ROLE,fork.cell(3,4))
    }
    @Test fun memoryMismatchMustBeSeenBeforeTurnChangesAndMatchingKeepsTurn() {
        val original=memory(listOf(0,1,0,1))
        assertTrue(original.seen.isEmpty());assertNull(original.face(0))
        var state=original.apply(PlayAction.Flip(PlaySide.USER,0))
        rejected {state.apply(PlayAction.Flip(PlaySide.USER,0))}
        rejected {state.apply(PlayAction.HidePair)}
        state=state.apply(PlayAction.Flip(PlaySide.USER,1))
        assertEquals(2,state.open.size);assertEquals(PlaySide.USER,state.turn)
        rejected {state.apply(PlayAction.Flip(PlaySide.ROLE,2))}
        rejected {state.apply(PlayAction.Flip(PlaySide.USER,2))}
        roundTrip(state)
        state=state.apply(PlayAction.HidePair)
        assertEquals(PlaySide.ROLE,state.turn);assertNull(state.face(0));assertEquals(mapOf(0 to 0,1 to 1),state.seen)
        state=state.apply(PlayAction.Flip(PlaySide.ROLE,0)).apply(PlayAction.Flip(PlaySide.ROLE,2))
        assertEquals(1,state.rolePairs);assertEquals(PlaySide.ROLE,state.turn)
        rejected {state.apply(PlayAction.Flip(PlaySide.ROLE,0))}
        state=state.apply(PlayAction.Flip(PlaySide.ROLE,1)).apply(PlayAction.Flip(PlaySide.ROLE,3))
        assertEquals(PlayResult.ROLE_WIN,state.result);assertNull(state.turn)
        rejected {state.apply(PlayAction.HidePair)};roundTrip(state)
        assertTrue(original.seen.isEmpty());assertTrue(original.matched.isEmpty())
    }
    @Test fun memoryCanTieAndRestoreDoesNotReshuffle() {
        var state=memory(listOf(0,0,1,1,2,2,3,3))
        for(i in 0..3)state=state.apply(PlayAction.Flip(PlaySide.USER,i))
        state=state.apply(PlayAction.Flip(PlaySide.USER,4)).apply(PlayAction.Flip(PlaySide.USER,6)).apply(PlayAction.HidePair)
        for(i in 4..7)state=state.apply(PlayAction.Flip(PlaySide.ROLE,i))
        assertEquals(PlayResult.DRAW,state.result);assertEquals(2,state.userPairs);assertEquals(2,state.rolePairs);roundTrip(state)
        repeat(20){val shuffled=MemoryPairsState.start();roundTrip(shuffled);assertTrue(shuffled.seen.isEmpty())}
    }
    @Test fun hiddenPermutationsHaveIdenticalObservationsAndOnlySeenFacesBecomePublic() {
        val a=memory(listOf(0,1,0,1,2,2));val b=memory(listOf(0,1,2,2,0,1))
        assertNotEquals(a.encode(),b.encode());assertEquals(a.observation().toString(),b.observation().toString())
        val observedA=a.apply(PlayAction.Flip(PlaySide.USER,0));val observedB=b.apply(PlayAction.Flip(PlaySide.USER,0))
        assertEquals(observedA.observation().toString(),observedB.observation().toString())
        assertFalse(observedA.observation().has("deck"));assertEquals(setOf(0),observedA.seen.keys)
        try {(observedA.seen as MutableMap<Int,Int>).put(5,2);fail()}catch(_:UnsupportedOperationException){}
    }
    @Test fun malformedSavesRejectUnknownFieldsCoercionsDuplicatesAndIllegalHistory() {
        val normal=GomokuState.start().encode()
        listOf(normal.replace("\"schemaVersion\":1","\"schemaVersion\":2"),
            normal.replace("\"schemaVersion\":1","\"schemaVersion\":\"1\""),
            normal.replace("\"schemaVersion\":1","\"schemaVersion\":1,\"schemaVersion\":1"),
            JSONObject(normal).put("url","https://evil.invalid").toString(),
            JSONObject(normal).put("moves",JSONArray().put(JSONObject().put("side","role").put("row",0).put("column",0))).toString(),
            JSONObject(normal).put("moves",JSONArray().put(JSONObject().put("side","user").put("row",0.5).put("column",0))).toString(),
            "[${normal}]",normal+"trailing"," ".repeat(131073),"[".repeat(17)+"0"+"]".repeat(17),
            memory(listOf(0,0,1,1)).encode().replace("[0,0,1,1]","[0,0,0,1]")
        ).forEach {bad->rejected("PLAY_FORMAT"){PlayStates.decode(bad)}}
    }
    @Test fun storyPreservesChineseEmojiOnlyConfirmedTurnsAndExplicitEnding() {
        val start=DateStoryState.start("海边🌊","虚构的傍晚约会")
        val user=start.apply(PlayAction.Narrate(PlaySide.USER,"我把伞递给你。🙂"))
        rejected {user.apply(PlayAction.Narrate(PlaySide.USER,"替你说话"))}
        val response=user.apply(PlayAction.Narrate(PlaySide.ROLE,"我接过伞，与你走向海边。"))
        roundTrip(response);assertEquals(0,start.turns.size);assertEquals(1,user.turns.size)
        val end=response.apply(PlayAction.EndStory);assertEquals(PlayResult.ENDED,end.result);roundTrip(end)
        rejected {end.apply(PlayAction.Narrate(PlaySide.USER,"继续"))}
        rejected {start.apply(PlayAction.Place(PlaySide.USER,0,0))}
        rejected {user.apply(PlayAction.Narrate(PlaySide.ROLE,"\uD800"))}
    }
    @Test fun escapedStoryCannotCreateAnUnreadableOversizedSave() {
        var state=DateStoryState.start("场景","主题")
        state=state.apply(PlayAction.Narrate(PlaySide.USER,"\u0001".repeat(4000)))
            .apply(PlayAction.Narrate(PlaySide.ROLE,"\u0001".repeat(8000)))
            .apply(PlayAction.Narrate(PlaySide.USER,"\u0001".repeat(4000)))
        roundTrip(state)
        rejected {state.apply(PlayAction.Narrate(PlaySide.ROLE,"\u0001".repeat(8000)))}
    }
}
