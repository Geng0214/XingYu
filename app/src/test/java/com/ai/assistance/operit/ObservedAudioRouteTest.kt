package com.ai.assistance.operit

import com.ai.assistance.operit.api.audio.*
import com.ai.assistance.operit.data.model.ChatFailure
import org.junit.Assert.*
import org.junit.Test

class ObservedAudioRouteTest {
    private val headset=CommunicationDevice(4,7,"headset")
    private val speaker=CommunicationDevice(5,2,"speaker")
    private fun error(block:()->Unit)=try {block();"NONE"}catch(e:ChatFailure){e.code}

    @Test fun preferenceOrAnotherDeviceCannotConfirmActualAudio() {
        val route=ObservedAudioRoute(headset)
        assertFalse(route.ready())
        route.observe(emptyList());route.observe(listOf(speaker))
        route.observe(listOf(headset.copy(id=99)))
        route.observe(listOf(headset.copy(type=2)))
        assertFalse(route.ready())
        assertEquals("AUDIO_ROUTE_UNAVAILABLE",error{route.requireReady()})
        route.observe(listOf(headset.copy(name="renamed")))
        route.requireReady()
    }

    @Test fun simultaneousUnselectedOutputNeverConfirmsOrResumes() {
        val route=ObservedAudioRoute(headset)
        route.observe(listOf(headset,speaker));assertFalse(route.ready())
        route.observe(listOf(headset));route.requireReady()
        route.observe(listOf(headset,speaker));route.observe(listOf(headset))
        assertEquals("AUDIO_ROUTE_CHANGED",error{route.requireReady()})
    }

    @Test fun twentyDisconnectReconnectAndClosedCallbackSequencesStayIsolated() {
        repeat(20) {round->
            val old=ObservedAudioRoute(headset)
            old.observe(listOf(headset));old.requireReady()
            old.observe(if(round%2==0)emptyList() else listOf(speaker))
            old.observe(listOf(headset))
            assertEquals("AUDIO_ROUTE_CHANGED",error{old.requireReady()})
            old.close()
            val next=ObservedAudioRoute(headset)
            next.observe(listOf(headset))
            old.observe(listOf(speaker));old.lost("AUDIO_FOCUS")
            next.requireReady()
        }
    }

    @Test fun RemovalBeforeFirstConfirmationAndFocusFailureAreSticky() {
        val removed=ObservedAudioRoute(headset)
        removed.lost("AUDIO_ROUTE_CHANGED");removed.observe(listOf(headset))
        assertEquals("AUDIO_ROUTE_CHANGED",error{removed.ready()})
        val focus=ObservedAudioRoute(headset)
        focus.observe(listOf(headset));focus.lost("AUDIO_FOCUS");focus.lost("AUDIO_ROUTE_CHANGED")
        assertEquals("AUDIO_FOCUS",error{focus.ready()})
        val closed=ObservedAudioRoute(headset)
        closed.observe(listOf(headset));closed.close();closed.observe(listOf(headset))
        assertEquals("AUDIO_ROUTE_UNAVAILABLE",error{closed.ready()})
    }
}
