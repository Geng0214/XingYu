package com.ai.assistance.operit

import com.ai.assistance.operit.api.chat.*
import com.ai.assistance.operit.data.db.*
import com.ai.assistance.operit.data.model.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PlayOutfitsTest {
    private val role=RoleEntity("role","伙伴","人设",spaceId="space")
    private val config=ModelConfigData("cfg","连接","openai","https://example.invalid/v1/chat/completions","model",60,"ref")
    private val chat=ChatEntity("chat",role.id,config.id,"会话")
    private val scope=PlayAccessScope(role.scope(),chat.id,"play")
    private val clothes=PlayOutfits(HomeItem.SUN_HAT,HomeItem.APRON)
    private fun reject(block:()->Unit){try{block();fail("Expected rejection")}catch(_:IllegalArgumentException){}catch(_:ChatFailure){}}
    private fun journey(mode:JourneyKind)=JourneyState.start(mode,"雨后散步","我","伙伴",listOf(JourneyPlace("公园","树荫"),JourneyPlace("海边","海风")))
    private fun ready(outfits:PlayOutfits=clothes)=DateStoryState.start("公园","散步").withOutfits(outfits).apply(PlayAction.Narrate(PlaySide.USER,"出发吧"))

    @Test fun legacyStatesKeepExactEncodingAndContextAfterLoadAndContinue() {
        val states=listOf(DateStoryState.start("旧地点","旧剧情"),journey(JourneyKind.CITY),journey(JourneyKind.SECRET))
        for(state in states) {
            val decoded=PlayStates.decode(state.encode())
            assertEquals(state.encode(),decoded.encode());assertEquals(state.observation().toString(),decoded.observation().toString())
            assertFalse(decoded.observation().has("outfits"))
            val advanced=decoded.apply(PlayAction.Narrate(PlaySide.USER,"旧对话"))
            assertFalse(advanced.observation().has("outfits"));assertTrue(JSONObject(advanced.encode()).getInt("schemaVersion")<6)
        }
    }
    @Test fun newDateAndBothJourneysKeepFrozenClothesAcrossTurnsMovesEndingAndReload() {
        val states=listOf(DateStoryState.start("公园","散步").withOutfits(clothes),journey(JourneyKind.CITY).withOutfits(clothes),journey(JourneyKind.SECRET).withOutfits(clothes))
        for(initial in states) {
            var current:PlayState=initial
            for(action in listOf(PlayAction.Narrate(PlaySide.USER,"走吧"),PlayAction.Narrate(PlaySide.ROLE,"好呀"))) {
                current=PlayStates.decode(current.apply(action).encode())
                assertEquals("太阳帽",current.observation().getJSONObject("outfits").getString("user"))
                assertEquals(6,JSONObject(current.encode()).getInt("schemaVersion"))
            }
            if(current is JourneyState)current=current.apply(PlayAction.Visit(1))
            current=current.apply(PlayAction.EndStory)
            assertEquals(clothes.encode().toString(),JSONObject(current.encode()).getJSONObject("outfits").toString())
        }
        assertNull(journey(JourneyKind.CITY).withOutfits(clothes).restart().outfits)
        reject {ready().withOutfits(PlayOutfits(null,null))}
    }
    @Test fun sharedWorldClothesAreCapturedForTheRequestedResident() {
        val home=HomeState.create("PRIVATE-HOME-NAME",0,listOf(role.scope(),RoleAccessScope("other","other-space")))
        repeat(50) {
            assertEquals(PlayOutfits(null,null),PlayOutfits.capture(role.scope(),home))
            reject {PlayOutfits.capture(RoleAccessScope(role.id,"different-space"),home)}
        }
        val encoded=PlayOutfits.capture(role.scope(),home).encode().toString()
        listOf("private-home","PRIVATE-HOME-NAME","CARROT_SEED","space").forEach {assertFalse(encoded.contains(it))}
        assertEquals(PlayOutfits(null,null),PlayOutfits.capture(role.scope(),null))
    }
    @Test fun twentyFrozenRequestsRejectChangedClothesAndKeepTheirOriginalPayload() {
        repeat(20) {i->
            val session=PlaySession(scope,"约会",ready())
            val p=PlayGenerations.prepare(session,scope,1,role,chat,config,"request-$i")
            val other=PlayGenerations.prepare(session.copy(state=ready(PlayOutfits(null,null))),scope,1,role,chat,config,"other-$i")
            assertFalse(p.frozen.sameApproval(other.frozen));assertFalse(p.frozen.accepts(other.session.copy(requestId=p.session.requestId)))
            val q=PlayRequestRow.from(p.frozen,100)
            PlayStorageValidation.validate(PlaySessionRow.from(p.session,100),listOf(q))
            val payload=p.frozen.snapshot.turns.last().content
            assertTrue(payload.contains("太阳帽"));assertTrue(payload.contains("围裙"));assertFalse(payload.contains("inventory"))
            val interrupted=PlaySessionRow.from(p.session,100).changed(PlaySessions.recover(p.session),200)
            PlayStorageValidation.validate(interrupted,listOf(q.interrupted()))
            reject {PlayStorageValidation.validate(interrupted.copy(stateJson=other.session.state.encode()),listOf(q.interrupted()))}
        }
    }
    @Test fun completedLedgerRejectsOutfitTamperingInConfirmedStateOrRequestContext() {
        val p=PlayGenerations.prepare(PlaySession(scope,"约会",ready()),scope,1,role,chat,config,"request")
        val response="""{"schemaVersion":1,"action":"narrate","text":"你的帽子很好看。"}"""
        val q=PlayRequestRow.from(p.frozen,100).progress(PlayProgress(MessageState.WAITING))
            .progress(PlayProgress(MessageState.GENERATING,response,firstTokenMs=0,elapsedMs=1))
            .progress(PlayProgress(MessageState.COMPLETE,response,0,2))
        val row=PlaySessionRow.from(p.session,100).changed(p.frozen.commit(p.session,PlayGenerations.decode(p.frozen,response)),101)
        PlayStorageValidation.validate(row,listOf(q))
        reject {PlayStorageValidation.validate(row.copy(stateJson=row.stateJson.replace("SUN_HAT","GARDEN_SUIT")),listOf(q))}
        reject {PlayStorageValidation.validate(row,listOf(q.copy(contextJson=q.contextJson.replace("太阳帽","田园服"))))}
    }
    @Test fun actualGardenClothesAreCapturedOnceAndLaterUnequipOnlyChangesNewSnapshots() {
        val (row,commands)=HomeTestData.garden();HomeStorageValidation.validate(row,commands)
        val original=row.value();val saved=PlayOutfits.capture(HomeTestData.a,original)
        assertEquals(clothes,saved)
        val undressedUser=original.apply(original.scope,original.version,original.updatedAt,HomeAction.Wear(HomeWearer.USER,null)).state
        val undressedBoth=undressedUser.apply(undressedUser.scope,undressedUser.version,undressedUser.updatedAt,HomeAction.Wear(HomeWearer.ROLE,null,HomeTestData.a.roleId)).state
        assertEquals(PlayOutfits(null,null),PlayOutfits.capture(HomeTestData.a,undressedBoth))
        assertEquals(clothes,saved)
        val encoded=DateStoryState.start("公园","约会").withOutfits(saved).encode()
        for(secret in listOf(original.scope.homeId,HomeTestData.a.roleId,original.name,"CARROT_SOUP"))assertFalse(encoded.contains(secret))
    }
    @Test fun malformedClothingAndVersionSpoofingCannotBecomeStates() {
        reject {PlayOutfits(HomeItem.CARROT,null)}
        val text=ready().encode()
        for(bad in listOf(text.replace("SUN_HAT","CARROT"),text.replace("SUN_HAT","https://example.invalid"),
            text.replace("\"schemaVersion\":6","\"schemaVersion\":1"),text.replace("\"user\":\"SUN_HAT\"","\"user\":7"),
            text.replace("\"user\":\"SUN_HAT\"","\"user\":\"SUN_HAT\",\"path\":\"file.txt\"")))reject {PlayStates.decode(bad)}
        val noOutfits=JSONObject(text).apply {remove("outfits")}.toString();reject {PlayStates.decode(noOutfits)}
    }
}
