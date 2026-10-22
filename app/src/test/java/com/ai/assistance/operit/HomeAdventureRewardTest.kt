package com.ai.assistance.operit

import com.ai.assistance.operit.data.model.*
import com.ai.assistance.operit.data.db.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class HomeAdventureRewardTest {
    private fun rejected(block:()->Unit){try{block();fail("Expected rejection")}catch(_:IllegalArgumentException){}catch(_:ChatFailure){}}
    private fun proof(side:Boolean=true)=HomeAdventureProof.from(AdventureGameTest().completed(side))
    private fun home()=HomeState.create("家园",100,listOf(RoleAccessScope("role","space")))
    @Test fun normalizedProofReplaysBothRewardsWithoutNamesDialogueOrFinish() {
        var game=AdventureGameTest().completed(true)
        game=game.apply(AdventureAction.Say(PlaySide.USER,"PRIVATE-USER-CHAT"))
            .apply(AdventureAction.Say(PlaySide.ROLE,"PRIVATE-ROLE-CHAT")).apply(AdventureAction.Finish)
        val p=HomeAdventureProof.from(game)
        for(reward in HomeAdventureReward.entries)assertTrue(p.earned(reward))
        assertEquals(p,HomeAdventureProof.decode(p.json));assertFalse(p.game.ended)
        assertFalse(p.json.contains("PRIVATE"));assertTrue(p.game.events.all {it is AdventureAction.Move||it is AdventureAction.Interact})
        assertEquals(proof(),p)
    }
    @Test fun unearnedOrForgedProofsCannotGrantItems() {
        val home=home();val before=home.encode()
        rejected {home.apply(home.scope,home.version,100,HomeAction.AdventureReward("source",HomeAdventureReward.LIGHTHOUSE,HomeAdventureProof.from(AdventureGame.start()),RoleAccessScope("role","space")))}
        assertFalse(proof(false).earned(HomeAdventureReward.SCARF))
        rejected {HomeAdventureProof.decode(AdventureGame.start("PRIVATE-NAME","PRIVATE-ROLE").encode())}
        rejected {HomeAdventureProof.decode(proof().json.replace("\"move\"","\"execute\""))}
        val dialogue=proof().game.apply(AdventureAction.Say(PlaySide.USER,"private"))
        rejected {HomeAdventureProof.decode(dialogue.encode())};assertEquals(before,home.encode())
    }
    @Test fun rewardCommandRequiresItsOwnVersionAndStrictFieldsAndIdentity() {
        val action=HomeAction.AdventureReward("source",HomeAdventureReward.LIGHTHOUSE,proof(),RoleAccessScope("role","space"))
        val json=HomeActionCodec.encode(action);assertEquals(action,HomeActionCodec.decode(json))
        for(bad in listOf(json.replace("\"schemaVersion\":4","\"schemaVersion\":3"),
            JSONObject(json).put("count",2).toString(),JSONObject(json).put("sourceId","").toString(),
            JSONObject(json).put("proof",JSONObject()).toString(),JSONObject(json).put("reward","CARROT").toString()))rejected {HomeActionCodec.decode(bad)}
        rejected {HomeActionCodec.decode(HomeActionCodec.encode(HomeAction.Rename("家园")).replace("\"schemaVersion\":4","\"schemaVersion\":3"))}
    }
    @Test fun ledgerRejectsDuplicateOriginEvenAfterDiscardAndSeparatesIndependentAdventures() {
        val initial=home();var state=initial;val records=mutableListOf<HomeCommandRow>();val p=proof()
        fun act(action:HomeAction){val change=state.apply(state.scope,state.version,100,action)
            records+=HomeCommandRow("command-${state.version}",state.scope.homeId,state.version,HomeActionCodec.encode(action),change.description,100)
            state=change.state}
        val first=HomeAction.AdventureReward("source",HomeAdventureReward.LIGHTHOUSE,p,RoleAccessScope("role","space"))
        act(first);act(HomeAction.Discard(HomeItem.HARBOR_BADGE,1));act(HomeAction.AdventureReward("source",HomeAdventureReward.SCARF,p,RoleAccessScope("role","space")))
        act(HomeAction.Gift("role",HomeItem.SEAGULL_PIN,1));HomeStorageValidation.validate(HomeRow.from(initial).changed(state),records)
        act(HomeAction.AdventureReward("source-2",HomeAdventureReward.LIGHTHOUSE,p,RoleAccessScope("role","space")))
        HomeStorageValidation.validate(HomeRow.from(initial).changed(state),records)
        assertEquals(1,state.inventory[HomeItem.HARBOR_BADGE]);assertEquals(1,state.residents.getValue("role").received[HomeItem.SEAGULL_PIN])
        act(first);rejected {HomeStorageValidation.validate(HomeRow.from(initial).changed(state),records)}
    }
    @Test fun proofAndEverySharedCommandHaveTheCurrentStrictEncoding() {
        val p=proof();assertTrue(p.json.toByteArray().size<HomeActionCodec.MAX_BYTES)
        for(action in listOf(HomeAction.Rename("家园"),HomeAction.Gift("role",HomeItem.CARROT,1),HomeAction.Wear(HomeWearer.USER,null))) {
            val json=HomeActionCodec.encode(action);assertEquals(4,JSONObject(json).getInt("schemaVersion"));assertEquals(action,HomeActionCodec.decode(json))
        }
        rejected {HomeActionCodec.decode(" ".repeat(HomeActionCodec.MAX_BYTES+1))}
    }
}
