package com.ai.assistance.operit

import com.ai.assistance.operit.api.audio.*
import com.ai.assistance.operit.data.model.ChatFailure
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CommunicationRoutesTest {
    private val headset=CommunicationDevice(10,7,"headset")
    private val mic=CommunicationDevice(11,7,"microphone")
    private val speaker=CommunicationDevice(20,2,"speaker")
    private inner class Port:CommunicationRoutePort {
        var devices=listOf(headset,speaker)
        var inputs=listOf(mic)
        var selectedInput:CommunicationDevice?=null
        var accepted=true
        var actual:CommunicationDevice?=null
        var openDelay=0L
        var closed=0
        var requested=0
        var cleanupError=false
        var opened=0
        var inputQueries=0
        var onClose:suspend ()->Unit={}
        val changes=mutableListOf<(CommunicationDevice?)->Unit>()
        val failures=mutableListOf<(String)->Unit>()
        override suspend fun devices()=devices
        override suspend fun inputs():List<CommunicationDevice>{inputQueries++;return inputs}
        override suspend fun open(device:CommunicationDevice,input:CommunicationDevice?,changed:(CommunicationDevice?)->Unit,lost:(String)->Unit):CommunicationConnection {
            opened++;selectedInput=input;changes+=changed;failures+=lost
            delay(openDelay)
            return object:CommunicationConnection {
                override suspend fun request():Boolean {requested++;return accepted}
                override suspend fun current()=actual
                override suspend fun close(){onClose();closed++;if(cleanupError)throw ChatFailure("AUDIO_ROUTE_CLEANUP")}
            }
        }
    }
    private suspend fun result(block:suspend ()->Unit):String=try {block();"COMPLETE"}catch(e:ChatFailure){e.code}

    @Test fun outputOnlySpeechNeverEnumeratesOrSelectsAMicrophone()=runTest {
        val port=Port();port.actual=headset;port.inputs=emptyList()
        val routes=CommunicationRoutes(port)
        assertEquals("spoken",routes.withOutputRoute(headset){check->check();"spoken"})
        assertNull(port.selectedInput);assertEquals(0,port.inputQueries)
        assertEquals(1,port.closed)
    }

    @Test fun outputLossDuringSynthesisCancelsBeforePlaybackAndRetainsOneRouteOwner()=runTest {
        val port=Port();port.actual=headset;val routes=CommunicationRoutes(port)
        var synthesisStopped=false;var playbackStarted=false
        val speech=async {result {routes.withOutputRoute(headset){
            try {delay(5_000);playbackStarted=true}finally {synthesisStopped=true}
        }}}
        runCurrent()
        assertEquals("AUDIO_ROUTE_BUSY",result{routes.withRoute(headset,mic){fail()}})
        port.failures.last()("AUDIO_ROUTE_CHANGED");runCurrent()
        assertEquals("AUDIO_ROUTE_CHANGED",speech.await());assertTrue(synthesisStopped);assertFalse(playbackStarted)
        assertEquals(1,port.closed);assertEquals(0,port.inputQueries)
        routes.withRoute(headset,mic){it()};assertEquals(mic,port.selectedInput)
    }

    @Test fun interruptDuringLossCleanupCannotEraseFailureOrReopenUntilExplicitReselection()=runTest {
        repeat(20) {
            val port=Port();port.actual=headset;val routes=CommunicationRoutes(port)
            val failure=CommunicationRouteFailure()
            var published=false
            val old=launch {
                try {routes.withRoute(headset,mic,failure){
                    try {awaitCancellation()}finally {withContext(NonCancellable){delay(20)}}
                }} catch(e:ChatFailure){currentCoroutineContext().ensureActive();published=true}
            }
            runCurrent()
            val oldLoss=port.failures.last()
            oldLoss("AUDIO_ROUTE_CHANGED");runCurrent()
            // Interrupt while loss handling is joining the old audio, then rapidly reconnect.
            old.cancel();port.changes.last()(headset)
            advanceTimeBy(21);runCurrent();old.join()
            assertFalse(published);assertNull(routes.status.value)
            assertEquals("AUDIO_ROUTE_CHANGED",failure.code)
            assertEquals("AUDIO_ROUTE_CHANGED",result {routes.withRoute(headset,mic,failure){fail()}})
            assertEquals(1,port.opened)
            val reselection=CommunicationRouteFailure()
            routes.withRoute(headset,mic,reselection){guard->oldLoss("AUDIO_FOCUS");guard()}
            assertNull(reselection.code);assertEquals(2,port.closed)
        }
    }

    @Test fun cancelledCleanupFailureIsRetainedOutsideTheCancelledJob()=runTest {
        val port=Port();port.actual=headset;port.cleanupError=true;port.onClose={delay(20)}
        val failure=CommunicationRouteFailure();val routes=CommunicationRoutes(port)
        val old=launch {
            try {routes.withRoute(headset,mic,failure){}}
            catch(e:ChatFailure){currentCoroutineContext().ensureActive();fail("Job should already be cancelled")}
        }
        runCurrent();old.cancel();advanceTimeBy(21);runCurrent();old.join()
        assertEquals("AUDIO_ROUTE_CLEANUP",failure.code);assertNull(routes.status.value)
        assertEquals(1,port.closed)
    }

    @Test fun legacyScoMicrophoneRequiresScoOutputButOtherPairsStillNeedRuntimeConfirmation() {
        for(api in 26..30) {
            assertFalse(communicationPairSupported(mic,speaker,api))
            assertTrue(communicationPairSupported(mic,headset,api))
            assertTrue(communicationPairSupported(mic.copy(type=15),speaker,api))
        }
        assertTrue(communicationPairSupported(mic,speaker,31))
    }

    @Test fun missingOrChangedMicrophoneIdentityRejectsBeforeOpeningAnyPlatformResource()=runTest {
        val port=Port();port.actual=headset;val routes=CommunicationRoutes(port)
        for(inputs in listOf(emptyList(),listOf(mic.copy(id=99)),listOf(mic.copy(type=15)))) {
            port.inputs=inputs
            assertEquals("AUDIO_ROUTE_UNAVAILABLE",result{routes.withRoute(headset,mic){fail()}})
            assertEquals(0,port.opened)
        }
        port.inputs=listOf(mic.copy(name="renamed"))
        routes.withRoute(headset,mic){it()}
        assertEquals(mic,port.selectedInput);assertEquals(1,port.closed)
    }

    @Test fun microphoneRemovalDuringNetworkWaitCancelsAndJoinsTheWholeRound()=runTest {
        val port=Port();port.actual=headset;val routes=CommunicationRoutes(port)
        var stopped=false
        port.onClose={assertTrue(stopped)}
        val task=async {result {routes.withRoute(headset,mic){
            assertEquals(mic,port.selectedInput)
            try {awaitCancellation()}finally {withContext(NonCancellable){delay(20);stopped=true}}
        }}}
        runCurrent()
        port.inputs=emptyList();port.failures.last()("AUDIO_ROUTE_CHANGED")
        port.inputs=listOf(mic);runCurrent()
        assertFalse(task.isCompleted)
        advanceTimeBy(21);runCurrent()
        assertEquals("AUDIO_ROUTE_CHANGED",task.await());assertEquals(1,port.closed)
    }

    @Test fun asynchronousDeviceConfirmationIsRequiredBeforeAnyAudioWork()=runTest {
        val port=Port();val routes=CommunicationRoutes(port,100)
        var started=false
        val job=async {routes.withRoute(headset,mic){check->check();started=true;"ok"}}
        runCurrent();assertEquals(1,port.requested);assertFalse(started)
        port.changes.last()(speaker);runCurrent();assertFalse(started)
        port.actual=headset;port.changes.last()(headset);runCurrent()
        assertEquals("ok",job.await());assertEquals(1,port.closed);assertNull(routes.status.value)
    }

    @Test fun refusedMissingAndTimedOutRoutesNeverStartWorkOrChooseAnotherDevice()=runTest {
        val port=Port();val routes=CommunicationRoutes(port,100)
        port.devices=listOf(speaker)
        assertEquals("AUDIO_ROUTE_UNAVAILABLE",result{routes.withRoute(headset,mic){fail()}})
        assertEquals(0,port.opened)
        port.devices=listOf(headset,speaker);port.accepted=false
        assertEquals("AUDIO_ROUTE_UNAVAILABLE",result{routes.withRoute(headset,mic){fail()}})
        assertEquals(1,port.closed)
        port.accepted=true;port.actual=speaker
        assertEquals("AUDIO_ROUTE_TIMEOUT",result{routes.withRoute(headset,mic){fail()}})
        assertEquals(2,port.closed);assertEquals(100,testScheduler.currentTime.toInt())
    }

    @Test fun twentyDisconnectReconnectCyclesAreStickyAndOldCallbacksCannotStopNewSession()=runTest {
        val port=Port();port.actual=headset
        val routes=CommunicationRoutes(port)
        repeat(20) {
            var stopped=false
            port.onClose={assertTrue(stopped)}
            val first=async {result{routes.withRoute(headset,mic){try {awaitCancellation()}finally {withContext(NonCancellable){delay(25);stopped=true}}}}}
            runCurrent()
            val oldChange=port.changes.last();val oldFailure=port.failures.last()
            oldChange(speaker);oldChange(headset)
            runCurrent();assertFalse(first.isCompleted)
            advanceTimeBy(26);runCurrent()
            assertEquals("AUDIO_ROUTE_CHANGED",first.await())
            assertTrue(stopped)
            port.onClose={}
            val next=async {routes.withRoute(headset,mic){guard->delay(50);guard();"new"}}
            runCurrent();oldChange(null);oldFailure("AUDIO_FOCUS")
            advanceTimeBy(51);runCurrent();assertEquals("new",next.await())
        }
        assertEquals(40,port.closed)
    }

    @Test fun focusLossCancelsBlockAndWaitsForCleanupBeforeClearingRoute()=runTest {
        val port=Port();port.actual=headset
        val routes=CommunicationRoutes(port)
        val events=mutableListOf<String>()
        port.onClose={events+="route-closed"}
        val first=async {result {routes.withRoute(headset,mic){try {awaitCancellation()}finally {withContext(NonCancellable){delay(10);events+="audio-stopped"}}}}}
        runCurrent()
        assertEquals("AUDIO_ROUTE_BUSY",result{routes.withRoute(speaker,mic){fail()}})
        port.failures.last()("AUDIO_FOCUS")
        advanceTimeBy(11);runCurrent()
        assertEquals("AUDIO_FOCUS",first.await())
        assertEquals(listOf("audio-stopped","route-closed"),events)
    }

    @Test fun cancellationDuringResourceAcquisitionStillClosesExactlyOnce()=runTest {
        val port=Port();port.openDelay=50;port.actual=headset
        val routes=CommunicationRoutes(port)
        val job=launch {routes.withRoute(headset,mic){fail("Must not start after cancellation")}}
        runCurrent();assertEquals(1,port.opened)
        job.cancel();advanceTimeBy(51);runCurrent();job.join()
        assertEquals(1,port.closed);assertEquals(0,port.requested);assertNull(routes.status.value)
    }

    @Test fun callerDeadlineRemainsCancellationAndCleanupFailureIsExplicit()=runTest {
        val port=Port();val routes=CommunicationRoutes(port,100)
        try {withTimeout(20){routes.withRoute(headset,mic){fail()}};fail()}
        catch(_:TimeoutCancellationException){}
        assertEquals(1,port.closed)
        port.actual=headset;port.cleanupError=true
        assertEquals("AUDIO_ROUTE_CLEANUP",result{routes.withRoute(headset,mic){}})
        assertEquals(2,port.closed);assertNull(routes.status.value)
    }

    @Test fun lossBeforeConfirmationCannotBeOverwrittenBySuccessfulCallback()=runTest {
        val port=Port();val routes=CommunicationRoutes(port)
        val job=async {result{routes.withRoute(headset,mic){fail()}}}
        runCurrent();port.failures.last()("AUDIO_FOCUS");port.changes.last()(headset)
        assertEquals("AUDIO_FOCUS",job.await());assertEquals(1,port.closed)
    }
}
