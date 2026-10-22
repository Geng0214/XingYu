package com.ai.assistance.operit

import com.ai.assistance.operit.data.security.*
import com.ai.assistance.operit.data.model.ChatFailure
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class PrivacyGateTest {
    private fun gate(enabled:Boolean=true,onClosed:()->Unit={}):PrivacyGate=PrivacyGate(onClosed).apply {resumed(true);initialize(enabled)}
    private suspend fun unlock(gate:PrivacyGate) {
        val ticket=gate.begin(PrivacyPurpose.UNLOCK)!!
        assertTrue(gate.authenticated(ticket.id){fail("Unlock must not change persistent policy")})
    }
    @Test fun coldStartNeverRestoresAnUnlockedSession()=runBlocking {
        val gate=gate();assertFalse(gate.state.value.canAccess)
        unlock(gate);assertTrue(gate.state.value.canAccess)
        assertFalse(gate(true).state.value.canAccess)
        val off=gate(false);assertTrue(off.state.value.canAccess)
        off.stopped();assertTrue("Disabled lock must not cancel ordinary background generation",off.state.value.canAccess)
    }
    @Test fun enablingAndDisablingRequireDistinctFreshAuthentication()=runBlocking {
        val gate=gate(false);val writes=mutableListOf<Boolean>()
        val enable=gate.begin(PrivacyPurpose.ENABLE)!!
        assertFalse(gate.state.value.canAccess);assertFalse(gate.state.value.enabled)
        assertTrue(gate.authenticated(enable.id){writes+=it});assertTrue(gate.state.value.enabled)
        val disable=gate.begin(PrivacyPurpose.DISABLE)!!
        assertNotEquals(enable.id,disable.id)
        assertFalse(gate.authenticated(enable.id){fail()})
        assertTrue(gate.authenticated(disable.id){writes+=it})
        assertEquals(listOf(true,false),writes);assertTrue(gate.state.value.canAccess);assertFalse(gate.state.value.enabled)
    }
    @Test fun cancelledDisabledPromptKeepsProtectionAndRejectsLateSuccess()=runBlocking {
        val gate=gate();unlock(gate)
        val ticket=gate.begin(PrivacyPurpose.DISABLE)!!
        gate.cancel(ticket.id)
        assertTrue(gate.state.value.enabled);assertFalse(gate.state.value.canAccess)
        assertFalse(gate.authenticated(ticket.id){fail()})
        assertNull(gate.begin(PrivacyPurpose.DISABLE))
    }
    @Test fun twentyBackgroundAndFreshTicketRoundsInvalidateOperations()=runBlocking {
        var closed=0;val gate=gate(onClosed={closed++});var old="stale"
        repeat(20) {
            val ticket=gate.begin(PrivacyPurpose.UNLOCK)!!
            assertFalse(gate.authenticated(old){fail()});assertTrue(gate.authenticated(ticket.id){fail()})
            val permit=gate.permit();gate.stopped();assertFalse(gate.state.value.canAccess)
            gate.resumed(true);assertFalse(gate.state.value.canAccess)
            try {gate.requirePermit(permit);fail()}catch(e:ChatFailure){assertEquals("LOCKED",e.code)}
            assertFalse(gate.authenticated(ticket.id){fail()});old=ticket.id
        }
        assertEquals(20,closed)
    }
    @Test fun systemCredentialActivityCanFinishOnlyAfterResume()=runBlocking {
        val gate=gate();val ticket=gate.begin(PrivacyPurpose.UNLOCK)!!
        gate.stopped()
        assertFalse(gate.authenticated(ticket.id){fail()})
        gate.resumed(true)
        assertTrue(gate.authenticated(ticket.id){fail()});assertTrue(gate.state.value.canAccess)
    }
    @Test fun noDeviceCredentialNeverDisablesStoredProtection()=runBlocking {
        val gate=gate();gate.resumed(false)
        assertNull(gate.begin(PrivacyPurpose.UNLOCK));assertTrue(gate.state.value.enabled);assertFalse(gate.state.value.canAccess)
        gate.resumed(true);unlock(gate)
        gate.resumed(false);assertFalse(gate.state.value.canAccess)
    }
    @Test fun persistenceFailureCannotOpenUiOrClearExistingPolicy()=runBlocking {
        for(enabled in listOf(false,true)) {
            val gate=gate(enabled);if(enabled)unlock(gate)
            val ticket=gate.begin(if(enabled)PrivacyPurpose.DISABLE else PrivacyPurpose.ENABLE)!!
            assertFalse(gate.authenticated(ticket.id){throw IllegalStateException("synthetic-disk-error")})
            assertEquals(enabled,gate.state.value.enabled);assertEquals("STORAGE",gate.state.value.error)
            assertFalse(gate.state.value.canAccess);assertNull(gate.begin(PrivacyPurpose.UNLOCK))
        }
    }
    @Test fun backgroundDuringAtomicWriteDoesNotReopenSession()=runBlocking {
        val gate=gate(false);val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>()
        val ticket=gate.begin(PrivacyPurpose.ENABLE)!!
        val save=launch {gate.authenticated(ticket.id){entered.complete(Unit);release.await()}}
        entered.await();gate.stopped();gate.resumed(true);save.cancel();release.complete(Unit);save.join()
        assertTrue(gate.state.value.enabled);assertFalse(gate.state.value.canAccess)
        assertFalse(gate.state.value.saving);unlock(gate)
    }
    @Test fun manualLockAndHostDestructionRejectOldApprovalEpoch()=runBlocking {
        val gate=gate();unlock(gate);val permit=gate.permit()
        gate.lockNow();val ticket=gate.begin(PrivacyPurpose.UNLOCK)!!;gate.cancel()
        assertFalse(gate.authenticated(ticket.id){fail()});unlock(gate)
        try {gate.requirePermit(permit);fail()}catch(e:ChatFailure){assertEquals("LOCKED",e.code)}
    }
}
