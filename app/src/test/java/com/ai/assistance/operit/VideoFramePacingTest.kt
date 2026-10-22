package com.ai.assistance.operit

import com.ai.assistance.operit.api.audio.*
import com.ai.assistance.operit.data.model.ChatFailure
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VideoFramePacingTest {
    @Test fun audioMasterWaitsForActualTrackAndDoesNotAdvanceWithWallTime() {
        val clock=VideoPresentationClock(true,1_000_000)
        assertNull(clock.positionUs(null,0));assertNull(clock.positionUs(null,5_000_000_000))
        assertEquals(1_000_000L,clock.positionUs(1_000_000,6_000_000_000))
        assertEquals(1_020_000L,clock.positionUs(1_020_000,10_000_000_000))
        assertEquals(1_020_000L,clock.positionUs(1_020_000,20_000_000_000))
    }
    @Test fun videoTailAdvancesOnlyAfterHardwareAudioDrain() {
        val clock=VideoPresentationClock(true,0)
        assertEquals(950_000L,clock.positionUs(950_000,0))
        clock.audioDrained(1_000_000,2_000_000_000)
        assertEquals(1_200_000L,clock.positionUs(null,2_200_000_000))
    }
    @Test fun silentVideoStartsItsClockWhenFirstFrameIsReady() {
        val clock=VideoPresentationClock(false,5_000_000)
        assertEquals(5_000_000L,clock.positionUs(null,100_000_000_000))
        assertEquals(5_040_000L,clock.positionUs(null,100_040_000_000))
    }
    @Test fun regressedAudioOrMonotonicClockIsRejected() {
        val clock=VideoPresentationClock(true,0)
        clock.positionUs(1000,0)
        try{clock.positionUs(999,1);fail()}catch(e:ChatFailure){assertEquals("MUSIC_PLAYBACK",e.code)}
        clock.audioDrained(1000,100)
        try{clock.positionUs(null,99);fail()}catch(e:ChatFailure){assertEquals("MUSIC_PLAYBACK",e.code)}
    }
    @Test fun initialMediaSilenceAdvancesVideoButRouteSilenceDoesNot() {
        val audio=MediaPcmTimeline(48000,960,0)
        audio.append(48000,0);audio.append(4800,1_000_000)
        val video=VideoPresentationClock(true,0)
        assertEquals(0L,video.positionUs(audio.positionUs(960),0))
        assertEquals(500_000L,video.positionUs(audio.positionUs(24960),1))
        assertEquals(1_050_000L,video.positionUs(audio.positionUs(51360),2))
    }
    @Test fun futureFrameWaitsForAudioPositionWithinPresentationTolerance()=runTest {
        assertTrue(awaitVideoFrame(100_000,0,{testScheduler.currentTime*1000},nowMs={testScheduler.currentTime}))
        assertEquals(90,testScheduler.currentTime)
    }
    @Test fun lateFramesAndPrerollAreDroppedWithoutWaiting()=runTest {
        assertFalse(awaitVideoFrame(1_000,0,{200_000},nowMs={testScheduler.currentTime}))
        assertFalse(awaitVideoFrame(1_000,2000,{error("must not read clock for preroll")},nowMs={testScheduler.currentTime}))
        assertEquals(0,testScheduler.currentTime)
    }
    @Test fun progressingClockCanWaitForLongTimestampGapWithoutFalseStall()=runTest {
        assertTrue(awaitVideoFrame(20_000_000,0,{testScheduler.currentTime*1000},nowMs={testScheduler.currentTime}))
        assertEquals(19_990,testScheduler.currentTime)
    }
    @Test fun unreadyOrStalledAudioClockCannotHoldFrameForever()=runTest {
        for(position in listOf<()->Long?>({null},{0})) {
            val start=testScheduler.currentTime
            try{awaitVideoFrame(100_000,0,position,nowMs={testScheduler.currentTime});fail()}
            catch(e:ChatFailure){assertEquals("MUSIC_PLAYBACK",e.code)}
            assertEquals(10_000,testScheduler.currentTime-start)
        }
    }
    @Test fun twentyCancellationRoundsDoNotReleaseLateFramesForDisplay()=runTest {
        repeat(20) {
            var rendered=false
            val job=launch(start=CoroutineStart.UNDISPATCHED) {
                if(awaitVideoFrame(1_000_000,0,{0},nowMs={testScheduler.currentTime}))rendered=true
            }
            job.cancelAndJoin();advanceUntilIdle();assertFalse(rendered)
        }
    }
    @Test fun permissionRevokedDuringProgressDeliveryIsCheckedBeforeRender()=runTest {
        var allowed=true
        try {
            awaitVideoFrame(0,0,{0},check={if(!allowed)throw ChatFailure("LOCKED")},onProgress={allowed=false});fail()
        }catch(e:ChatFailure){assertEquals("LOCKED",e.code)}
    }
    @Test fun seekPresentsOnlyLatestPrerollThenTargetFramesAndReleasesEverySlotOnce()=runTest {
        val releases=mutableListOf<Pair<Int,Boolean>>();val times=mutableListOf<Long>()
        val queue=SeekVideoFrames(100_000,{slot,show->releases.add(slot to show)}) {time,render->times.add(time);render()}
        queue.accept(1,0);queue.accept(2,90_000);queue.accept(3,120_000);queue.finish();queue.close()
        assertEquals(listOf(1 to false,2 to true,3 to true),releases)
        assertEquals(listOf(100_000L,120_000L),times)
    }
    @Test fun seekingWithinLastFrameDisplaysItAtTargetEvenWithoutNewerOutput()=runTest {
        val releases=mutableListOf<Pair<Int,Boolean>>();var shown:Long?=null
        val queue=SeekVideoFrames(990_000,{slot,show->releases.add(slot to show)}) {time,render->shown=time;render()}
        queue.accept(4,966_000);queue.finish();queue.close();queue.close()
        assertEquals(990_000L,shown);assertEquals(listOf(4 to true),releases)
    }
    @Test fun twentyCancelledPrerollHandoffsReleaseRetainedAndCurrentSlotsOnce()=runTest {
        repeat(20) {
            val releases=mutableListOf<Pair<Int,Boolean>>()
            val queue=SeekVideoFrames(100_000,{slot,show->releases.add(slot to show)}) {_,_->awaitCancellation()}
            queue.accept(1,90_000)
            val job=launch(start=CoroutineStart.UNDISPATCHED){queue.accept(2,110_000)}
            job.cancelAndJoin();queue.close()
            assertEquals(listOf(1 to false,2 to false),releases)
        }
    }
    @Test fun invalidTimestampAndAbandonedPrerollAreReleasedWithoutRendering()=runTest {
        val releases=mutableListOf<Pair<Int,Boolean>>()
        val queue=SeekVideoFrames(100_000,{slot,show->releases.add(slot to show)}) {_,_->fail()}
        queue.accept(1,90_000)
        try{queue.accept(2,Long.MAX_VALUE);fail()}catch(e:ChatFailure){assertEquals("MUSIC_FORMAT",e.code)}
        queue.close();assertEquals(listOf(2 to false,1 to false),releases)
    }
    @Test fun concurrentAudioVideoProgressWritesAreSerializedAndLatePositionsAreIgnored()=runTest {
        val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>();val written=mutableListOf<Long>()
        val publisher=MediaPositionPublisher {position->if(position==1000L){entered.complete(Unit);release.await()};written.add(position)}
        val audio=launch {publisher.submit(1_000_000)}
        entered.await()
        val video=launch(start=CoroutineStart.UNDISPATCHED){publisher.submit(2_000_000)}
        assertTrue(written.isEmpty());release.complete(Unit);audio.join();video.join()
        assertFalse(publisher.submit(1_500_000));assertEquals(listOf(1000L,2000L),written)
    }
    @Test fun cancelledProgressWaitingForPriorDatabaseWriteNeverPublishes()=runTest {
        val entered=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>();val written=mutableListOf<Long>()
        val publisher=MediaPositionPublisher {position->entered.complete(Unit);release.await();written.add(position)}
        val first=launch {publisher.submit(1_000_000)};entered.await()
        val waiting=launch(start=CoroutineStart.UNDISPATCHED){publisher.submit(2_000_000)}
        waiting.cancelAndJoin();release.complete(Unit);first.join();assertEquals(listOf(1000L),written)
    }
    @Test fun slowProgressPersistenceCannotRenderUsingStaleAudioTime()=runTest {
        assertFalse(awaitVideoFrame(100_000,0,{90_000+testScheduler.currentTime*1000},nowMs={testScheduler.currentTime},onProgress={delay(200)}))
        assertEquals(200,testScheduler.currentTime)
    }
}
