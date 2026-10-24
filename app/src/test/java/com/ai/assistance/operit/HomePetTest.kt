package com.ai.assistance.operit

import com.ai.assistance.operit.data.db.HomeCommandRow
import com.ai.assistance.operit.data.db.HomeRow
import com.ai.assistance.operit.data.db.HomeStorageValidation
import com.ai.assistance.operit.data.model.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HomePetTest {
    private fun start()=HomeState.create("家园",100,listOf(HomeTestData.a,HomeTestData.b))
    private fun HomeState.act(a:HomeAction,now:Long=updatedAt)=apply(scope,version,now,a).state
    private fun rejected(code:String,block:()->Unit) {try{block();fail("Expected $code")}catch(e:ChatFailure){assertEquals(code,e.code)}}
    private val hour=3_600_000L
    private val day=24*hour

    @Test fun adoptCareAndOfflinePreview() {
        rejected("HOME_PET_REQUIRED"){start().act(HomeAction.CarePet(HomePetCare.FEED))}
        var h=start().act(HomeAction.AdoptPet(" 团子 "))
        assertEquals("团子",h.pet!!.name)
        assertEquals(HomePetStage.KITTEN,HomePetRules.stage(h.pet!!,100))
        h=h.act(HomeAction.CarePet(HomePetCare.FEED))
        assertEquals(100,h.pet!!.hunger);assertEquals(74,h.pet!!.mood);assertEquals(3,h.pet!!.bond)
        assertEquals(1,h.pet!!.userCareCount);assertEquals(0,h.pet!!.roleCareCount)
        val later=100+8*hour
        val preview=HomePetRules.preview(h.pet!!,later)
        assertEquals(84,preview.hunger);assertEquals(58,preview.mood);assertEquals(72,preview.energy);assertEquals(64,preview.hygiene)
        h=h.act(HomeAction.CarePet(HomePetCare.PLAY),later)
        assertEquals(86,h.pet!!.mood);assertEquals(66,h.pet!!.energy);assertEquals(5,h.pet!!.bond)
        assertEquals(later,h.pet!!.lastTickAt);assertEquals(2,h.pet!!.userCareCount)
        assertEquals(HomePetStage.YOUNG,HomePetRules.stage(h.pet!!,100+7*day))
        assertEquals(HomePetStage.ADULT,HomePetRules.stage(h.pet!!,100+30*day))
    }

    @Test fun offlineIsCappedAndClockRollbackDoesNotDecay() {
        val h=start().act(HomeAction.AdoptPet("团子"))
        val preview=HomePetRules.preview(h.pet!!,100+20*day)
        assertEquals(10,preview.hunger);assertEquals(10,preview.mood);assertEquals(10,preview.energy);assertEquals(10,preview.hygiene)
        assertEquals(h.pet,HomePetRules.preview(h.pet!!,50))
    }

    @Test fun handoffIsDelegatedAndResolvedOnce() {
        var h=start().act(HomeAction.AdoptPet("团子")).act(HomeAction.HandoffPet("role-a"))
        assertEquals("role-a",h.pet!!.handoffRoleId)
        rejected("HOME_PET_HANDOFF_EARLY"){h.act(HomeAction.ResolvePetHandoff("role-a"),100+HomePetRules.HANDOFF_DELAY_MS-1)}
        h=h.act(HomeAction.ResolvePetHandoff("role-a"),100+HomePetRules.HANDOFF_DELAY_MS)
        assertNull(h.pet!!.handoffRoleId);assertEquals("role-a",h.pet!!.lastCareBy)
        assertEquals(1,h.pet!!.roleCareCount);assertEquals(0,h.pet!!.userCareCount)
        assertEquals(98,h.pet!!.mood);assertEquals(76,h.pet!!.energy);assertEquals(4,h.pet!!.bond)
        rejected("HOME_PET_HANDOFF_EMPTY"){h.act(HomeAction.ResolvePetHandoff("role-a"),100+2*HomePetRules.HANDOFF_DELAY_MS)}
    }

    @Test fun leavingRoleClearsHandoffAndLastCarer() {
        var h=start().act(HomeAction.AdoptPet("团子")).act(HomeAction.HandoffPet("role-a"))
        h=h.act(HomeAction.Leave("role-a"))
        assertFalse("role-a" in h.residents);assertNull(h.pet!!.handoffRoleId);assertNull(h.pet!!.lastCareBy)
    }

    @Test fun codecRoundTripsPetAndReadsLegacyFormat3() {
        val h=start().act(HomeAction.AdoptPet("团子")).act(HomeAction.CarePet(HomePetCare.BRUSH))
        assertEquals(h.pet,HomeState.decode(h.encode()).pet)
        val legacy=JSONObject(h.encode()).put("schemaVersion",3).apply {remove("pet")}
        val old=HomeState.decode(legacy.toString())
        assertNull(old.pet);assertEquals(h.residents,old.residents)
        assertEquals(4,JSONObject(old.encode()).getInt("schemaVersion"))
    }

    @Test fun promptSummaryIsOneLineAndDoesNotExposeNumbers() {
        val h=start().act(HomeAction.AdoptPet("团子"))
        val fresh=HomePetRules.promptSummary(h.pet!!,100+8*hour)
        assertTrue(fresh.contains("团子"));assertTrue(fresh.contains("刚把"));assertFalse(fresh.any {it.isDigit()})
        val grown=HomePetRules.promptSummary(h.pet!!,100+20*day)
        assertTrue(grown.contains("少年猫"));assertFalse(grown.any {it.isDigit()})
    }

    @Test fun promptSummaryOnlyClaimsHandoffForItsRole() {
        var h=start().act(HomeAction.AdoptPet("团子")).act(HomeAction.HandoffPet("role-a"))
        val forA=HomePetRules.promptSummary(h.pet!!,100+HomePetRules.HANDOFF_DELAY_MS,"role-a")
        val forB=HomePetRules.promptSummary(h.pet!!,100+HomePetRules.HANDOFF_DELAY_MS,"role-b")
        assertTrue(forA.contains("交给了你"));assertFalse(forB.contains("交给了你"))
        assertFalse(forA.any {it.isDigit()});assertFalse(forB.any {it.isDigit()})
    }

    @Test fun remapMovesPetRoleReferences() {
        val h=start().act(HomeAction.AdoptPet("团子")).act(HomeAction.HandoffPet("role-a"))
        val remapped=HomeCodec.remap(h.encode(),{"new-$it"},{"new-$it"})
        val value=HomeState.decode(remapped)
        assertEquals("new-role-a",value.pet!!.handoffRoleId)
    }

    @Test fun ledgerReplaysPetActions() {
        val initial=HomeState.create("家园",100,listOf(HomeTestData.a,HomeTestData.b))
        var state=initial;val records=mutableListOf<HomeCommandRow>()
        fun act(action:HomeAction,now:Long=state.updatedAt) {
            val change=state.apply(state.scope,state.version,now,action)
            records+=HomeCommandRow("pet-${records.size}",state.scope.homeId,state.version,HomeActionCodec.encode(action),change.description,now)
            state=change.state
        }
        act(HomeAction.AdoptPet("团子"));act(HomeAction.CarePet(HomePetCare.FEED),200)
        act(HomeAction.HandoffPet("role-a"),200);act(HomeAction.ResolvePetHandoff("role-a"),200+HomePetRules.HANDOFF_DELAY_MS)
        val row=HomeRow.from(initial).changed(state)
        HomeStorageValidation.validate(row,records)
        assertEquals("团子",row.value().pet!!.name)
    }

    @Test fun actionCodecRoundTripsPetActions() {
        listOf(HomeAction.AdoptPet("团子"),HomeAction.CarePet(HomePetCare.BRUSH),HomeAction.HandoffPet("role-a"),HomeAction.ResolvePetHandoff("role-a"))
            .forEach {assertEquals(it,HomeActionCodec.decode(HomeActionCodec.encode(it)))}
    }
}
