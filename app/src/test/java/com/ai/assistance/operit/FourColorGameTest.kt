package com.ai.assistance.operit

import com.ai.assistance.operit.data.model.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.security.SecureRandom
import java.util.Random

class FourColorGameTest {
    private val U=PlaySide.USER;private val R=PlaySide.ROLE
    private class FixedRandom(seed:Long):SecureRandom() {
        private val source=Random(seed)
        override fun nextInt(bound:Int)=source.nextInt(bound)
        override fun nextBytes(bytes:ByteArray)=source.nextBytes(bytes)
    }
    private fun invalid(block:()->Unit){try{block();fail("Expected rejection")}catch(_:IllegalArgumentException){}catch(_:ChatFailure){}}
    private fun game(user:List<Int>,top:Int=0,following:List<Int> = emptyList(),role:List<Int>?=null):FourColorGame {
        require(user.size==7)
        val opponent=role?:((0..107).toList()-user-top-following.toSet()).take(7)
        val dealt=(0..6).flatMap {listOf(user[it],opponent[it])}
        val deck=dealt+top+following+((0..107).toList()-dealt.toSet()-top-following.toSet())
        return FourColorGame.decode(JSONObject().put("schemaVersion",1).put("kind","four_color").put("rules","duel-v1")
            .put("deck",JSONArray(deck)).put("shuffleKey","ab".repeat(32)).put("first","user").put("moves",JSONArray()).toString())
    }
    @Test fun fullDeckCompositionAndFaceScoringAreExact() {
        val all=(0..107).map(FourColorGame::card)
        CardColor.entries.forEach {color->
            val cards=all.filter {it.color==color};assertEquals(25,cards.size)
            assertEquals(1,cards.count {it.face==0});(1..12).forEach {face->assertEquals(2,cards.count {it.face==face})}
        }
        assertEquals(4,all.count {it.face==13});assertEquals(4,all.count {it.face==14})
        assertEquals(9,FourColorGame.card(17).points);assertEquals(20,FourColorGame.card(23).points);assertEquals(50,FourColorGame.card(104).points)
    }
    @Test fun dealAndProjectionNeverExposeOtherHandDeckOrReshuffleSecret() {
        val u=listOf(1,3,5,7,9,11,13);val a=game(u,role=listOf(25,26,27,28,29,30,31))
        val b=game(u,role=listOf(50,51,52,53,54,55,56))
        assertEquals(a.observation(U).toString(),b.observation(U).toString())
        assertEquals(7,a.handCount(U));assertEquals(7,a.handCount(R));assertEquals(93,a.drawCount);assertEquals(1,a.discardCount)
        val visible=a.observation(U);assertFalse(visible.has("deck"));assertFalse(visible.has("shuffleKey"));assertFalse(visible.has("opponentHand"))
        assertFalse(visible.toString().contains("ab".repeat(32)))
    }
    @Test fun colorOrFaceMatchAndWrongSideOrForeignCardAreChecked() {
        val a=game(listOf(1,25,27,50,75,100,104))
        assertTrue(a.legalMoves(U).contains(CardMove.Play(U,1)))
        assertTrue(a.legalMoves(U).contains(CardMove.Play(U,25)))
        invalid {a.apply(CardMove.Play(U,27))};invalid {a.apply(CardMove.Play(R,1))};invalid {a.apply(CardMove.Play(U,2))}
        invalid {a.apply(CardMove.Play(U,1,CardColor.BLUE))};invalid {a.apply(CardMove.Play(U,100))}
        assertEquals(R,a.apply(CardMove.Play(U,1)).turn)
    }
    @Test fun skipReverseAndDrawTwoRetainTurnForTwoPlayersWithoutStacking() {
        listOf(19,21,23).forEach {id->
            val a=game(listOf(id,1,3,5,25,50,75));val b=a.apply(CardMove.Play(U,id))
            assertEquals(U,b.turn);assertEquals(6,b.handCount(U));assertEquals(if(id==23)9 else 7,b.handCount(R))
            assertEquals(if(id==23)2 else 0,b.history.last().drawn)
            invalid {b.apply(CardMove.Draw(R))}
        }
    }
    @Test fun wildDrawFourRequiresNoMatchingColorAndSetsDeclaredColor() {
        val a=game(listOf(104,100,25,27,50,75,1))
        assertFalse(a.legalMoves(U).any {it is CardMove.Play&&it.card==104})
        invalid {a.apply(CardMove.Play(U,104,CardColor.BLUE))}
        val b=game(listOf(104,100,25,27,50,75,77)).apply(CardMove.Play(U,104,CardColor.GREEN))
        assertEquals(CardColor.GREEN,b.color);assertEquals(U,b.turn);assertEquals(11,b.handCount(R))
        val c=a.apply(CardMove.Play(U,100,CardColor.YELLOW));assertEquals(R,c.turn);assertEquals(CardColor.YELLOW,c.color)
    }
    @Test fun drawingOnlyAllowsNewCardOrPassAndUnplayableDrawEndsTurn() {
        val a=game(listOf(1,3,5,7,9,11,13),following=listOf(15))
        val drawn=a.apply(CardMove.Draw(U));assertEquals(U,drawn.turn);assertEquals(8,drawn.handCount(U))
        assertEquals(listOf(CardMove.Play(U,15),CardMove.Pass(U)),drawn.legalMoves(U))
        invalid {drawn.apply(CardMove.Play(U,1))};invalid {drawn.apply(CardMove.Draw(U))}
        assertEquals(R,drawn.apply(CardMove.Pass(U)).turn);assertEquals(R,drawn.apply(CardMove.Play(U,15)).turn)
        assertEquals(R,game(listOf(1,3,5,7,9,11,13),following=listOf(27)).apply(CardMove.Draw(U)).turn)
        assertTrue(drawn.observation(R).isNull("drawnCard"));assertEquals(15,drawn.observation(U).getInt("drawnCard"))
    }
    @Test fun lastPenaltyCardDrawsBeforeScoringAndEndedGamesRejectMoreMoves() {
        // Six skip/reverse cards keep the user at the table until the last draw-two card.
        val ids=listOf(19,20,21,22,44,45,48)
        var a=game(ids);listOf(21,22,19,20,44,45,48).forEach {a=a.apply(CardMove.Play(U,it))}
        assertEquals(PlayResult.USER_WIN,a.result);assertNull(a.turn);assertEquals(0,a.handCount(U));assertEquals(9,a.handCount(R))
        assertEquals(a.hand(R).sumOf {it.points},a.score);assertEquals(2,a.history.last().drawn)
        invalid {a.apply(CardMove.Draw(R))}
        var wild=game(listOf(19,20,21,22,44,45,104))
        listOf(21,22,19,20,44,45).forEach {wild=wild.apply(CardMove.Play(U,it))}
        wild=wild.apply(CardMove.Play(U,104,CardColor.BLUE))
        assertEquals(PlayResult.USER_WIN,wild.result);assertEquals(11,wild.handCount(R))
        assertEquals(wild.hand(R).sumOf {it.points},wild.score);assertEquals(4,wild.history.last().drawn)
    }
    @Test fun fullReplaysConserveCardsReshuffleDeterministicallyAndReachAResult() {
        var reshuffled=false
        repeat(20) {round->
            var a=FourColorGame.start(if(round%2==0)U else R,FixedRandom(round.toLong()))
            var steps=0
            while(a.result==PlayResult.ACTIVE&&steps<FourColorGame.MAX_MOVES) {
                val side=requireNotNull(a.turn);val legal=a.legalMoves(side);assertTrue(legal.isNotEmpty())
                // Mix deliberate draws with legal play to exercise recycling, penalties and pending draws.
                val move=if(steps%5==0)legal.last()else legal.first()
                a=a.apply(move);steps++
                assertEquals(108,a.handCount(U)+a.handCount(R)+a.drawCount+a.discardCount)
                assertEquals(a.encode(),FourColorGame.decode(a.encode()).encode())
                if(a.reshuffles>0)reshuffled=true
            }
            assertNotEquals(PlayResult.ACTIVE,a.result);assertNull(a.turn);assertTrue(a.score>=0)
        }
        assertTrue("Mixed rounds should recycle discards",reshuffled)
    }
    @Test fun maxHistoryClosesLocallyAndStrictSaveRejectsTampering() {
        var a=game(listOf(1,3,5,7,9,11,13))
        while(a.drawCount>0||a.legalMoves(requireNotNull(a.turn)).last() is CardMove.Pass) {
            a=a.apply(a.legalMoves(requireNotNull(a.turn)).last())
        }
        val v=JSONObject(a.encode());val moves=v.getJSONArray("moves");var next=requireNotNull(a.turn)
        while(moves.length()<FourColorGame.MAX_MOVES){moves.put(JSONArray().put("draw").put(next.wire));next=next.other()}
        val full=FourColorGame.decode(v.toString());assertEquals(PlayResult.DRAW,full.result);assertNull(full.turn)
        invalid {full.apply(CardMove.Draw(U))}
        assertTrue(full.encode().toByteArray(Charsets.UTF_8).size<131072)
        listOf(JSONObject(a.encode()).put("schemaVersion",2),JSONObject(a.encode()).put("rules","stacking"),
            JSONObject(a.encode()).put("shuffleKey","public"),JSONObject(a.encode()).put("extra",true)).forEach {invalid {FourColorGame.decode(it.toString())}}
        val bad=JSONObject(a.encode());bad.getJSONArray("deck").put(0,bad.getJSONArray("deck").getInt(1));invalid {FourColorGame.decode(bad.toString())}
    }
}
