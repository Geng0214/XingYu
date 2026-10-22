package com.ai.assistance.operit

import com.ai.assistance.operit.data.security.PrivacyGate
import com.ai.assistance.operit.data.model.ChatFailure
import org.junit.Assert.*
import org.junit.Test

class ForegroundPublicationTest {
    @Test fun disabledLockStillRejectsPublishingRecordedAudioAfterLeavingForeground() {
        val gate=PrivacyGate();gate.initialize(false);gate.resumed(true)
        val permit=gate.permit();assertEquals("audio",gate.withForegroundPermit(permit){"audio"})
        gate.stopped();assertTrue(gate.state.value.canAccess)
        var published=false
        assertThrows(ChatFailure::class.java){gate.withForegroundPermit(permit){published=true}}
        assertFalse(published)
    }
    @Test fun storageFailureAndChangedEpochRejectOldPublication() {
        val gate=PrivacyGate();gate.initialize(false);gate.resumed(true)
        val permit=gate.permit();gate.storageFailed()
        var published=false
        assertThrows(ChatFailure::class.java){gate.withForegroundPermit(permit){published=true}}
        assertFalse(published)
    }
}
