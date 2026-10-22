package com.ai.assistance.operit

import com.ai.assistance.operit.data.model.*
import org.junit.Assert.*
import org.junit.Test

class HomePersonalityTest {
    @Test fun readerSpendsMoreTimeWithBooks() {
        val reader=HomePersonality.from("安静内向，喜欢阅读和文学")
        val neutral=HomePersonality.from("居民")
        val reading=(1..200).count {reader.intention("reader",it)==HomeIntent.READ}
        val ordinary=(1..200).count {neutral.intention("reader",it)==HomeIntent.READ}
        assertTrue(reading>ordinary*2)
    }
    @Test fun outgoingResidentInitiatesSoonerAndWalksFaster() {
        val outgoing=HomePersonality.from("热情外向，喜欢探索")
        val quiet=HomePersonality.from("安静害羞，喜欢独处")
        assertTrue(outgoing.initiativeSeconds<quiet.initiativeSeconds)
        assertTrue(outgoing.paceMillis<quiet.paceMillis)
    }
    @Test fun negatedOutgoingTraitDoesNotMakeResidentOutgoing() {
        val neutral=HomePersonality.from("居民")
        assertEquals(neutral.sociability,HomePersonality.from("并不外向，not outgoing").sociability)
    }
    @Test fun allWanderDestinationsAreWalkableUnoccupiedAndReachable() {
        val personality=HomePersonality.from("好奇，喜欢园艺、钓鱼和美食")
        HomeIntent.entries.forEach {intent->
            repeat(20) {turn->
                val destination=personality.destination(intent,"person",turn,setOf(HomeWorld.spawn))
                assertNotNull(destination)
                assertTrue(HomeWorld.walkable(requireNotNull(destination)))
                assertNotEquals(HomeWorld.spawn,destination)
                assertTrue(HomeWorld.route(HomeWorld.spawn,destination).isNotEmpty())
            }
        }
    }
    @Test fun movingPlayerIsNotInterruptedAndGreetingHasCooldown() {
        val brain=HomeResidentBrain(HomePersonality.from("热情黏人"),"resident")
        assertTrue(brain.decide(100_000,true,0));assertFalse(brain.approaching)
        val now=brain.nextDecisionAt
        brain.greeted(now)
        assertFalse(brain.decide(now+1000,false,0))
        assertFalse(brain.approaching)
    }
    @Test fun residentEventuallyApproachesStationaryPlayer() {
        val brain=HomeResidentBrain(HomePersonality.from("温柔开朗，喜欢照顾我"),"resident")
        var approached=false
        for(time in 100_000L..220_000L step 1000) {
            if(brain.decide(time,false,0)&&brain.approaching){approached=true;break}
        }
        assertTrue(approached)
    }
    @Test fun liveRouteAvoidsPeopleAndCannotEndInTheirCell() {
        val start=HomeTile(14,14);val resident=HomeTile(14,13);val end=HomeTile(14,12)
        val route=HomeWorld.route(start,end,occupied=setOf(resident))
        assertTrue(route.isNotEmpty());assertEquals(end,route.last())
        assertFalse(resident in route)
        assertTrue(HomeWorld.route(start,resident,occupied=setOf(resident)).isEmpty())
    }
    @Test fun interactingWithResidentStopsInVacantAdjacentCell() {
        val resident=HomeTile(14,13);val occupied=setOf(resident,HomeTile(14,14))
        val route=HomeWorld.route(HomeTile(14,16),resident,true,occupied)
        assertTrue(route.isNotEmpty());assertEquals(1,route.last().distance(resident))
        assertTrue(route.none {it in occupied})
    }
    @Test fun savedOverlappingResidentsAreSeparatedWithoutMovingUnoccupiedPeople() {
        val saved=HomeWorld.spawn
        val first=HomeWorld.residentTile(saved,setOf(saved))
        val second=HomeWorld.residentTile(saved,setOf(saved,first))
        assertNotEquals(saved,first);assertNotEquals(first,second)
        assertTrue(HomeWorld.walkable(first));assertTrue(HomeWorld.walkable(second))
        assertTrue(HomeWorld.route(saved,second).isNotEmpty())
        assertEquals(first,HomeWorld.residentTile(first,setOf(saved,second)))
    }
}
