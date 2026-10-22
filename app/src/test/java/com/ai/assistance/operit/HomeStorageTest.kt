package com.ai.assistance.operit

import com.ai.assistance.operit.data.db.*
import com.ai.assistance.operit.data.model.*
import org.junit.Assert.*
import org.junit.Test

object HomeTestData {
    val a=RoleAccessScope("role-a","space-a")
    val b=RoleAccessScope("role-b","space-b")
    fun garden():Pair<HomeRow,List<HomeCommandRow>> {
        val initial=HomeState.create("共享家园",100,listOf(a,b))
        var state=initial;val records=mutableListOf<HomeCommandRow>()
        fun act(action:HomeAction,now:Long=state.updatedAt) {
            val change=state.apply(state.scope,state.version,now,action)
            records+=HomeCommandRow("event-${records.size}",state.scope.homeId,state.version,HomeActionCodec.encode(action),change.description,now)
            state=change.state
        }
        act(HomeAction.Plant(0,HomeCrop.CARROT));act(HomeAction.Plant(1,HomeCrop.TOMATO))
        act(HomeAction.Plant(2,HomeCrop.COTTON));act(HomeAction.Plant(3,HomeCrop.COTTON))
        act(HomeAction.Harvest(0),900_100);act(HomeAction.Harvest(1));act(HomeAction.Harvest(2));act(HomeAction.Harvest(3))
        act(HomeAction.Craft(HomeRecipe.CLOTH,3));act(HomeAction.Craft(HomeRecipe.APRON));act(HomeAction.Craft(HomeRecipe.SUN_HAT))
        act(HomeAction.Gift(a.roleId,HomeItem.APRON,1));act(HomeAction.Wear(HomeWearer.ROLE,HomeItem.APRON,a.roleId))
        act(HomeAction.Wear(HomeWearer.USER,HomeItem.SUN_HAT))
        return HomeRow.from(initial).changed(state) to records
    }
}

class HomeStorageTest {
    @Test fun replayPreservesSharedGoodsAndSeparateResidentGifts() {
        val (row,records)=HomeTestData.garden();HomeStorageValidation.validate(row,records)
        assertEquals(HomeItem.APRON,row.value().residents.getValue("role-a").outfit)
        assertTrue(row.value().residents.getValue("role-b").received.isEmpty())
        assertEquals(HomeItem.SUN_HAT,row.value().userOutfit)
    }
    @Test fun forgedStockAndMissingMovementOrMembershipEventsAreRejected() {
        val (row,records)=HomeTestData.garden()
        try {HomeStorageValidation.validate(row,records.dropLast(1));fail()}catch(_:IllegalArgumentException){}
        val forged=row.copy(stateJson=row.stateJson.replace("\"CARROT\":3","\"CARROT\":99"))
        try {HomeStorageValidation.validate(forged,records);fail()}catch(_:IllegalArgumentException){}
    }
    @Test fun residentActionIdsAndPositionsAreReboundWithTheLedger() {
        val (row,records)=HomeTestData.garden()
        val rebound=row.copy(initialJson=HomeCodec.remap(row.initialJson,{"new-$it"},{"new-$it"}),
            stateJson=HomeCodec.remap(row.stateJson,{"new-$it"},{"new-$it"}))
        val events=records.map {it.copy(actionJson=HomeActionCodec.encode(HomeActionCodec.remap(it.action(),{"new-$it"},{"new-$it"},{"new-$it"})))}
        HomeStorageValidation.validate(rebound,events)
        assertEquals(HomeItem.APRON,rebound.value().residents.getValue("new-role-a").outfit)
    }
}
