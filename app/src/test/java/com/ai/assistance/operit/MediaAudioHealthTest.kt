package com.ai.assistance.operit

import com.ai.assistance.operit.api.audio.*
import com.ai.assistance.operit.data.model.ChatFailure
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MediaAudioHealthTest {
    @Test fun lossInterruptsPendingPlatformCallbackAndJoinsCleanupWithoutWaitingForTimeout()=runTest {
        val failure=CompletableDeferred<String>();var stopped=false
        val result=async {
            try {withMediaAudioHealth(failure,{}) {
                try {withTimeout(10_000){awaitCancellation()}}
                finally {withContext(NonCancellable){delay(20);stopped=true}}
            }}catch(e:ChatFailure){e.code}
        }
        runCurrent();failure.complete("MUSIC_ROUTE_CHANGED");runCurrent()
        assertFalse(result.isCompleted)
        advanceTimeBy(21);runCurrent()
        assertEquals("MUSIC_ROUTE_CHANGED",result.await());assertTrue(stopped)
        assertEquals(21,testScheduler.currentTime.toInt())
    }
    @Test fun routeFailureWinsOverPlatformErrorTriggeredByStoppingPlayer()=runTest {
        val failure=CompletableDeferred<String>();var unhealthy=false
        try {
            withMediaAudioHealth(failure,{if(unhealthy)throw ChatFailure("MUSIC_FOCUS")}) {
                unhealthy=true;failure.complete("MUSIC_FOCUS");throw IllegalStateException("Released player")
            }
            fail()
        }catch(e:ChatFailure){assertEquals("MUSIC_FOCUS",e.code)}
    }
    @Test fun originalPlatformErrorAndCallerCancellationKeepTheirCategories()=runTest {
        val failure=CompletableDeferred<String>()
        val expected=IllegalStateException("Invalid media")
        try {withMediaAudioHealth(failure,{}){throw expected};fail()}
        // Coroutine debug stack recovery can copy Throwable; type and diagnostic stay unchanged.
        catch(e:IllegalStateException){assertEquals(expected.javaClass,e.javaClass);assertEquals(expected.message,e.message)}
        var cleaned=false
        try {withTimeout(20){withMediaAudioHealth(failure,{}){try {awaitCancellation()}finally {cleaned=true}}};fail()}
        catch(_:TimeoutCancellationException){}
        assertTrue(cleaned);assertFalse(failure.isCompleted)
    }
    @Test fun healthyPlaybackReturnsItsResultWithoutCancellingSharedFailureSignal()=runTest {
        val failure=CompletableDeferred<String>();var checks=0
        assertEquals(42,withMediaAudioHealth(failure,{checks++}){delay(5);42})
        assertEquals(2,checks);assertFalse(failure.isCancelled)
    }
}
