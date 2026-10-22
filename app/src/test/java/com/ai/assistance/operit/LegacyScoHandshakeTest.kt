package com.ai.assistance.operit

import com.ai.assistance.operit.api.audio.*
import com.ai.assistance.operit.data.model.ChatFailure
import org.junit.Assert.*
import org.junit.Test

class LegacyScoHandshakeTest {
    private fun error(block:()->Unit)=try {block();"NONE"}catch(e:ChatFailure){e.code}

    @Test fun stickyConnectedStillRequiresOurOwnRequestAndActualOutputConfirmation() {
        val sco=LegacyScoHandshake()
        sco.changed(ScoLinkState.CONNECTED);assertFalse(sco.ready())
        sco.request();assertTrue(sco.ready())
        val chosen=CommunicationDevice(12,7,"Bluetooth")
        val output=ObservedAudioRoute(chosen)
        output.observe(listOf(CommunicationDevice(13,2,"Speaker")))
        assertFalse(output.ready())
        output.observe(listOf(chosen));output.requireReady()
    }

    @Test fun disconnectedStickyThenConnectingThenConnectedCanBecomeReadyOnce() {
        val sco=LegacyScoHandshake()
        sco.changed(ScoLinkState.DISCONNECTED);sco.request()
        sco.changed(ScoLinkState.DISCONNECTED);assertFalse(sco.ready())
        sco.changed(ScoLinkState.CONNECTING);assertFalse(sco.ready())
        sco.changed(ScoLinkState.CONNECTED);assertTrue(sco.ready())
        sco.changed(ScoLinkState.CONNECTED);assertTrue(sco.ready())
        assertEquals("AUDIO_ROUTE_UNAVAILABLE",error{sco.request()})
    }

    @Test fun connectionFailureCannotBeOverwrittenByLateConnectedEvent() {
        val sco=LegacyScoHandshake();sco.request()
        sco.changed(ScoLinkState.CONNECTING);sco.changed(ScoLinkState.DISCONNECTED)
        sco.changed(ScoLinkState.CONNECTED)
        assertEquals("AUDIO_ROUTE_UNAVAILABLE",error{sco.ready()})
        val bad=LegacyScoHandshake();bad.request();bad.changed(ScoLinkState.ERROR)
        bad.changed(ScoLinkState.CONNECTED)
        assertEquals("AUDIO_ROUTE_UNAVAILABLE",error{bad.ready()})
    }

    @Test fun twentyLinkLossReconnectAndLateOldReceiverSequencesNeverResumeOldRequest() {
        repeat(20) {round->
            val old=LegacyScoHandshake();old.request();old.changed(ScoLinkState.CONNECTED)
            old.changed(when(round%3){0->ScoLinkState.DISCONNECTED;1->ScoLinkState.CONNECTING;else->ScoLinkState.ERROR})
            old.changed(ScoLinkState.CONNECTED)
            assertEquals("AUDIO_ROUTE_CHANGED",error{old.ready()})
            old.close()
            val next=LegacyScoHandshake();next.request();next.changed(ScoLinkState.CONNECTED)
            old.changed(ScoLinkState.DISCONNECTED);old.changed(ScoLinkState.CONNECTED)
            assertTrue(next.ready())
        }
    }

    @Test fun closedHandshakeIgnoresReceiverAndCannotBeReused() {
        val sco=LegacyScoHandshake();sco.request();sco.changed(ScoLinkState.CONNECTED)
        sco.close();sco.changed(ScoLinkState.CONNECTED)
        assertEquals("AUDIO_ROUTE_UNAVAILABLE",error{sco.ready()})
        assertEquals("AUDIO_ROUTE_UNAVAILABLE",error{sco.request()})
    }
}
