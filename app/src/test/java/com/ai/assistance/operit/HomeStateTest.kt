package com.ai.assistance.operit

import com.ai.assistance.operit.data.model.*
import org.junit.Assert.*
import org.junit.Test

class HomeStateTest {
    private fun start()=HomeState.create("家园",100,listOf(HomeTestData.a,HomeTestData.b))
    private fun HomeState.act(a:HomeAction,now:Long=updatedAt)=apply(scope,version,now,a).state
    private fun rejected(code:String,block:()->Unit) {try{block();fail("Expected $code")}catch(e:ChatFailure){assertEquals(code,e.code)}}
    @Test fun joinAndDeleteKeepTheSameWorldAndCancelOnlyTheDepartingPartner() {
        var h=start().act(HomeAction.Plant(0,HomeCrop.CARROT))
        h=h.act(HomeAction.Join(RoleAccessScope("role-c","space-c")))
        h=h.act(HomeAction.Follow("role-a")).act(HomeAction.StartTogether("role-a",HomeTogetherKind.TEA))
        val bag=h.inventory;val plots=h.plots;val furniture=h.life.placed
        h=h.act(HomeAction.Leave("role-a"))
        assertEquals(HomeScope(),h.scope);assertEquals(bag,h.inventory);assertEquals(plots,h.plots);assertEquals(furniture,h.life.placed)
        assertEquals(setOf("role-b","role-c"),h.residents.keys);assertNull(h.followingRoleId);assertNull(h.life.together)
    }
    @Test fun giftAndTogetherRewardsAffectTheSelectedPersonOnly() {
        var h=start().act(HomeAction.Plant(0,HomeCrop.CARROT)).act(HomeAction.Harvest(0),300_100)
        h=h.act(HomeAction.Gift("role-b",HomeItem.CARROT,2))
        assertEquals(2,h.residents.getValue("role-b").received[HomeItem.CARROT]);assertTrue(h.residents.getValue("role-a").received.isEmpty())
        h=h.act(HomeAction.StartTogether("role-b",HomeTogetherKind.TEA)).act(HomeAction.FinishTogether,320_100)
        assertEquals(7,h.residents.getValue("role-b").friendship);assertEquals(0,h.residents.getValue("role-a").friendship)
        assertEquals(HomeTogetherKind.TEA.response,h.residents.getValue("role-b").response)
    }
    @Test fun collectionRequiresReachEnergyAndCooldownAndCanFundFurniture() {
        var h=start()
        fun collect(index:Int,time:Long) {
            val resource=HomeWorld.resources[index]
            val near=HomeWorld.neighbors(resource.tile).first()
            h=h.act(HomeAction.Move(near,h.residents.mapValues {it.value.tile}),time)
            h=h.act(HomeAction.Gather(index),time)
        }
        rejected("HOME_DISTANCE"){h.act(HomeAction.Gather(0))}
        collect(0,100);rejected("HOME_RESOURCE_WAIT"){h.act(HomeAction.Gather(0))}
        collect(1,100);collect(4,100)
        h=h.act(HomeAction.Decorate(HomeFurniture.HONEY_SOFA))
        assertEquals(HomeFurniture.HONEY_SOFA,h.life.placed[HomeFurnitureSlot.SOFA]);assertEquals(55,h.life.coins)
        assertEquals(1,h.inventory[HomeItem.WOOD]);assertEquals(1,h.inventory[HomeItem.STONE])
        h=h.act(HomeAction.Sell(HomeItem.WOOD,1));assertEquals(59,h.life.coins)
        assertEquals(h.encode(),HomeState.decode(h.encode()).encode())
    }
    @Test fun aFailedMealOrStaleVersionDoesNotConsumeMaterials() {
        val h=start();val saved=h.encode()
        rejected("HOME_MATERIAL"){h.act(HomeAction.StartTogether("role-b",HomeTogetherKind.MEAL,HomeItem.SALAD))}
        rejected("HOME_CHANGED"){h.apply(h.scope,2,100,HomeAction.Plant(0,HomeCrop.CARROT))}
        assertEquals(saved,h.encode())
    }
    @Test fun savedPositionsAndFollowerSurviveReload() {
        var h=start().act(HomeAction.Move(HomeTile(14,15),start().residents.mapValues {it.value.tile})).act(HomeAction.Follow("role-b"))
        h=HomeState.decode(h.encode())
        assertEquals(HomeTile(14,15),h.player);assertEquals("role-b",h.followingRoleId)
        rejected("HOME_MOVE"){h.act(HomeAction.Move(HomeTile(14,6),h.residents.mapValues {it.value.tile}))}
    }
}
