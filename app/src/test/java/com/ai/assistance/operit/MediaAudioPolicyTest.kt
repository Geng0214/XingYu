package com.ai.assistance.operit

import com.ai.assistance.operit.api.audio.*
import com.ai.assistance.operit.data.model.ChatFailure
import org.junit.Assert.*
import org.junit.Test

class MediaAudioPolicyTest {
    private val speaker=CommunicationDevice(1,2,"speaker")
    private val headset=CommunicationDevice(2,8,"headset")
    private val outputs=listOf(speaker,headset)

    @Test fun initialDirectoryCallbackAndNameOrOrderingChangesDoNotStopPlayback() {
        val policy=MediaAudioPolicy(outputs){fail()}
        policy.outputsChanged(outputs.reversed().map {it.copy(name="new name")})
        policy.observe(emptyList());assertFalse(policy.routeReady())
        policy.observe(listOf(headset));assertTrue(policy.routeReady())
        policy.observe(listOf(headset.copy(name="renamed")));policy.requireHealthy()
    }
    @Test fun actualRouteSwitchOrAdditionalOutputStopsOwnedPlayerOnce() {
        for(actual in listOf(emptyList(),listOf(speaker),listOf(headset,speaker))) {
            val stopped=mutableListOf<String>();val policy=MediaAudioPolicy(outputs){stopped+=it}
            policy.observe(listOf(headset));policy.observe(actual);policy.observe(listOf(headset))
            assertEquals(listOf("MUSIC_ROUTE_CHANGED"),stopped)
            assertEquals("MUSIC_ROUTE_CHANGED",assertThrows(ChatFailure::class.java){policy.requireHealthy()}.code)
        }
    }
    @Test fun twentyQuickRemovalsAndReconnectsNeverResumeAndOldCallbacksCannotStopNewPlayer() {
        repeat(20) {
            var oldStops=0;var newStops=0
            val old=MediaAudioPolicy(outputs){oldStops++}
            old.removed(listOf(headset));old.outputsChanged(outputs);old.observe(listOf(headset))
            old.fail("MUSIC_FOCUS");assertEquals(1,oldStops)
            assertEquals("MUSIC_ROUTE_CHANGED",assertThrows(ChatFailure::class.java){old.routeReady()}.code)
            old.close()
            val next=MediaAudioPolicy(outputs){newStops++}
            next.observe(listOf(headset))
            old.removed(outputs);old.fail("MUSIC_FOCUS");old.outputsChanged(emptyList())
            next.requireHealthy();assertEquals(0,newStops)
        }
    }
    @Test fun focusLossAndNoisySignalStopSynchronouslyAndCannotBeOverwritten() {
        for(code in listOf("MUSIC_FOCUS","MUSIC_ROUTE_CHANGED")) {
            var stopped=false
            val policy=MediaAudioPolicy(outputs){stopped=true}
            policy.fail(code);assertTrue(stopped)
            policy.fail("MUSIC_PLAYBACK");policy.outputsChanged(outputs)
            assertEquals(code,assertThrows(ChatFailure::class.java){policy.requireHealthy()}.code)
        }
    }
    @Test fun changedDirectoryStopsBeforeRouteHasBeenConfirmed() {
        for(devices in listOf(listOf(speaker),outputs+headset.copy(id=3),outputs.map {it.copy(type=99)})) {
            var stopped=false;val policy=MediaAudioPolicy(outputs){stopped=true}
            policy.outputsChanged(devices);assertTrue(stopped)
            assertThrows(ChatFailure::class.java){policy.requireHealthy()}
        }
        val healthy=MediaAudioPolicy(outputs){fail()}
        healthy.removed(listOf(CommunicationDevice(99,15,"unrelated input")))
        healthy.requireHealthy()
    }
    @Test fun stopCallbackCanCloseItsOwnSessionWithoutClearingTheReason() {
        lateinit var policy:MediaAudioPolicy
        policy=MediaAudioPolicy(outputs){policy.close()}
        policy.fail("MUSIC_FOCUS")
        assertEquals("MUSIC_FOCUS",assertThrows(ChatFailure::class.java){policy.requireHealthy()}.code)
    }
}
