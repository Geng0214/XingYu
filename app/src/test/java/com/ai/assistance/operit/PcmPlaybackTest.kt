package com.ai.assistance.operit

import com.ai.assistance.operit.api.audio.PcmPlayback
import com.ai.assistance.operit.data.model.ChatFailure
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PcmPlaybackTest {
    @Test fun writtenBufferIsNotCompleteUntilPlaybackHeadReachesEveryFrame()=runTest {
        var played=0L
        val task=async{PcmPlayback.awaitDrain(24_000,{played},{},clock={testScheduler.currentTime})}
        runCurrent();assertFalse(task.isCompleted)
        played=23_999;advanceTimeBy(100);runCurrent();assertFalse(task.isCompleted)
        played=24_000;advanceTimeBy(21);runCurrent();task.await()
    }

    @Test fun focusLossDuringDrainFailsInsteadOfStartingTheNextRecording()=runTest {
        var lost=false
        val task=async{try {PcmPlayback.awaitDrain(24_000,{10_000},{if(lost)throw ChatFailure("AUDIO_FOCUS")});"unexpected"}catch(e:ChatFailure){e.code}}
        runCurrent();lost=true;advanceTimeBy(21);runCurrent()
        assertEquals("AUDIO_FOCUS",task.await())
    }

    @Test fun stalledHardwareHasBoundedWaitAndDoesNotReportSuccess()=runTest {
        try {PcmPlayback.awaitDrain(24_000,{0},{},clock={testScheduler.currentTime},stallTimeoutMs=100);fail()}
        catch(e:ChatFailure){assertEquals("AUDIO_PLAYBACK",e.code)}
        assertEquals(100,testScheduler.currentTime.toInt())
    }

    @Test fun cancellationDuringDrainCannotCompletePlayback()=runTest {
        var completed=false
        val task=launch{PcmPlayback.awaitDrain(24_000,{1},{},clock={testScheduler.currentTime});completed=true}
        runCurrent();task.cancelAndJoin();assertFalse(completed)
    }
}
