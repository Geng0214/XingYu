package com.ai.assistance.operit

import com.ai.assistance.operit.api.audio.*
import com.ai.assistance.operit.data.model.ChatFailure
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class DecodedMediaPcmTest {
    private fun pcm(timeUs:Long=0,rate:Int=48000,channels:Int=2,frames:Int=480)=
        DecodedMediaPcm(rate,channels,timeUs,ByteArray(frames*channels*2){17})
    @Test fun seekingDropsWholeInterleavedFramesAndKeepsExactBoundary() {
        val p=pcm(timeUs=100_000)
        assertEquals(0,p.skippedFrames(99_999));assertEquals(0,p.skippedFrames(100_000))
        assertEquals(1,p.skippedFrames(100_001));assertEquals(240,p.skippedFrames(105_000))
        assertEquals(480,p.skippedFrames(200_000))
        assertEquals(1,pcm(rate=44100).skippedFrames(22))
        assertEquals(2,pcm(rate=44100).skippedFrames(23))
    }
    @Test fun negativePrerollAndInvalidPcmAreBounded() {
        assertEquals(480,pcm(timeUs=-10_000).skippedFrames(0))
        for(p in listOf(pcm().copy(rate=0),pcm().copy(channels=0),pcm().copy(channels=9),
            pcm().copy(bytes=ByteArray(3)),pcm().copy(bytes=ByteArray(512*1024+4)),pcm().copy(timeUs=Long.MIN_VALUE))) {
            try{p.validate();fail()}catch(e:ChatFailure){assertEquals("MUSIC_FORMAT",e.code)}
        }
    }
    @Test fun hardwareHeadExcludesSilenceAndQueuedButUnheardAudio() {
        val clock=MediaPcmTimeline(48000,960,5_000_000)
        clock.append(4800,5_000_000)
        assertEquals(5000,clock.positionMs(0));assertEquals(5000,clock.positionMs(960))
        assertEquals(5050,clock.positionMs(3360));assertEquals(5100,clock.positionMs(5760))
    }
    @Test fun segmentTimestampsPreserveGapsWithoutGoingBackwards() {
        val clock=MediaPcmTimeline(48000,0,0)
        clock.append(480,0);clock.append(480,50_000);clock.append(480,40_000)
        assertEquals(10,clock.positionMs(480));assertEquals(55,clock.positionMs(720))
        assertEquals(60,clock.positionMs(960));assertEquals(60,clock.positionMs(1440))
        try{clock.positionMs(1441);fail()}catch(e:ChatFailure){assertEquals("MUSIC_PLAYBACK",e.code)}
        try{clock.positionMs(100);fail()}catch(e:ChatFailure){assertEquals("MUSIC_PLAYBACK",e.code)}
    }
    @Test fun sixHourHighRateHeadUsesUnsignedLongRange() {
        val clock=MediaPcmTimeline(192000,0,0)
        repeat(24){clock.append(172800000,it*900_000_000L)}
        assertEquals(4_147_200_000L,clock.submittedFrames)
        assertEquals(21_600_000L,clock.positionMs(4_147_200_000L))
    }
    @Test fun orderedBoundedHandoffErasesAllDeliveredBuffers()=runBlocking {
        val buffers=List(5){pcm(timeUs=it*10_000L)};val observed=mutableListOf<Long>()
        withDecodedMediaPcm({emit->for(p in buffers)emit(p)}) {p->assertTrue(p.bytes.any {it!=0.toByte()});observed.add(p.timeUs);delay(2)}
        assertEquals(buffers.map {it.timeUs},observed);assertTrue(buffers.all {p->p.bytes.all {it==0.toByte()}})
    }
    @Test fun twentyCancellationRoundsEraseConsumerQueueAndUnpublishedBufferAndJoinDecoder()=runBlocking {
        repeat(20) {
            val buffers=List(3){pcm()};val consuming=CompletableDeferred<Unit>();val blocked=CompletableDeferred<Unit>()
            var decoderClosed=false
            val job=launch {
                withDecodedMediaPcm({emit->try {
                    emit(buffers[0]);consuming.await();emit(buffers[1]);blocked.complete(Unit);emit(buffers[2])
                }finally {decoderClosed=true}}) {consuming.complete(Unit);awaitCancellation()}
            }
            blocked.await();job.cancelAndJoin()
            assertTrue(decoderClosed);assertTrue(buffers.all {p->p.bytes.all {it==0.toByte()}})
        }
    }
    @Test fun decoderFailureIsNotNormalEndOfStream()=runBlocking {
        val p=pcm()
        try {withDecodedMediaPcm({emit->emit(p);throw ChatFailure("MUSIC_CHANGED")}) {};fail()}
        catch(e:ChatFailure){assertEquals("MUSIC_CHANGED",e.code)}
        assertTrue(p.bytes.all {it==0.toByte()})
    }
    @Test fun consumerRouteLossCancelsDecoderBeforeReturning()=runBlocking {
        val p=pcm();var closed=false
        try {withDecodedMediaPcm({emit->try {emit(p);awaitCancellation()}finally{closed=true}}) {throw ChatFailure("MUSIC_ROUTE_CHANGED")};fail()}
        catch(e:ChatFailure){assertEquals("MUSIC_ROUTE_CHANGED",e.code)}
        assertTrue(closed);assertTrue(p.bytes.all {it==0.toByte()})
    }
    @Test fun cancellationStopsNativeConsumerBeforeWaitingForSlowDecoderCleanup()=runBlocking {
        val consuming=CompletableDeferred<Unit>();val stopped=CompletableDeferred<Unit>()
        val order=java.util.Collections.synchronizedList(mutableListOf<String>())
        val job=launch {
            withDecodedMediaPcm({emit->try {emit(pcm());awaitCancellation()}finally {
                withContext(NonCancellable){stopped.await();order.add("decoder closed")}
            }},{order.add("native stopped");stopped.complete(Unit)}) {consuming.complete(Unit);awaitCancellation()}
        }
        consuming.await();job.cancel()
        try {withTimeout(5000){job.join()};assertEquals(listOf("native stopped","decoder closed"),order)}
        finally {stopped.complete(Unit);job.cancelAndJoin()}
    }
    @Test fun normalDecodeCompletionLeavesNativeConsumerAliveForHardwareDrain()=runBlocking {
        var stopped=false;var decoded=false
        withDecodedMediaPcm({emit->emit(pcm());decoded=true},{stopped=true}) {}
        assertTrue(decoded);assertFalse(stopped)
    }
}
