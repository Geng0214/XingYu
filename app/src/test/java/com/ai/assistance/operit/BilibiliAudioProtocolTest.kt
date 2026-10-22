package com.ai.assistance.operit

import com.ai.assistance.operit.api.audio.*
import com.ai.assistance.operit.data.model.ChatFailure
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

@OptIn(ExperimentalCoroutinesApi::class)
class BilibiliAudioProtocolTest {
    private val destination="https://player.bilibili.com/player.html?bvid=BV1B7411m7LV&p=1&autoplay=0"
    private fun state(epoch:Int,playing:Boolean=true,rate:Int=48000)=JSONObject().put("kind","state").put("epoch",epoch).put("playing",playing).put("rate",rate).toString()
    private fun pcm(epoch:Int=1,sequence:Int=1,bytes:ByteArray=ByteArray(4096){17})=JSONObject().put("kind","pcm").put("epoch",epoch)
        .put("sequence",sequence).put("frames",1024).put("pcm",Base64.getEncoder().encodeToString(bytes)).toString()
    private fun invalid(raw:String,inbox:BilibiliAudioInbox=BilibiliAudioInbox()) {
        try{inbox.accept(raw);fail()}catch(e:ChatFailure){assertEquals("BILI_AUDIO",e.code)}
    }
    @Test fun acceptsOnlyOrderedFixedStereoBlocksFromFrozenEpochRate() {
        val inbox=BilibiliAudioInbox()
        assertEquals(BilibiliAudioEvent.STATE,inbox.accept(state(1,rate=44100)))
        inbox.accept(pcm());val p=inbox.take()!!
        assertEquals(44100,p.rate);assertEquals(1024,p.frames);assertEquals(4096,p.bytes.size);assertTrue(inbox.current(p))
        invalid(pcm(),inbox);invalid(pcm(sequence=3),inbox)
        inbox.accept(pcm(sequence=2));p.bytes.fill(0);inbox.close()
    }
    @Test fun pauseInvalidatesTakenPcmAndOldPacketsCannotEnterNewGeneration() {
        val inbox=BilibiliAudioInbox();inbox.accept(state(1));inbox.accept(pcm());val old=inbox.take()!!
        inbox.accept(pcm(sequence=2));inbox.accept(state(2,false));assertNull(inbox.take());assertFalse(inbox.current(old))
        assertEquals(BilibiliAudioEvent.STALE,inbox.accept(pcm()))
        inbox.accept(state(3));invalid(pcm(epoch=4),inbox);inbox.accept(pcm(epoch=3));old.bytes.fill(0);inbox.close()
    }
    @Test fun unknownFieldsIdentifiersUrlsScriptsAndNestedOrOversizedPayloadsAreRejected() {
        for(raw in listOf(
            JSONObject(state(1)).put("key","private").toString(),JSONObject(state(1)).put("url","https://example.invalid").toString(),
            JSONObject(state(1)).put("script","run()").toString(),JSONObject(state(1)).put("roleId","other").toString(),
            "{\"kind\":\"state\",\"kind\":\"pcm\"}","{kind:'state'}","[".repeat(30)+"0"+"]".repeat(30),"x".repeat(8193),state(1)+" {}",
            JSONObject(state(1)).put("playing","true").toString(),JSONObject(state(1)).put("rate",48000.5).toString(),state(2),state(1,rate=0)
        ))invalid(raw)
    }
    @Test fun queueCapacityAndPcmSizeAreBounded() {
        val inbox=BilibiliAudioInbox();inbox.accept(state(1))
        invalid(pcm(bytes=ByteArray(4100)),inbox);invalid(pcm(bytes=ByteArray(4095)),inbox)
        for(n in 1..8)inbox.accept(pcm(sequence=n))
        invalid(pcm(sequence=9),inbox)
        val p=inbox.take()!!;p.bytes.fill(0);inbox.accept(pcm(sequence=9));inbox.close();assertNull(inbox.take())
    }
    @Test fun acknowledgmentsFollowNativeWriteAndConsumedPcmIsErased()=runTest {
        val entered=CompletableDeferred<Unit>();val finish=CompletableDeferred<Unit>();val ack=mutableListOf<Pair<Long,Long>>()
        var captured:ByteArray?=null
        val stream=BilibiliAudioStream(destination,{packet,current->captured=packet.bytes;entered.complete(Unit);finish.await();current()},{},{e,s->ack.add(e to s)})
        stream.receive(state(1),"https://player.bilibili.com",true,destination);stream.receive(pcm(),"https://player.bilibili.com",true,destination)
        val job=launch {stream.run()};entered.await();assertTrue(ack.isEmpty());finish.complete(Unit);runCurrent()
        assertEquals(listOf(1L to 1L),ack);assertTrue(captured!!.all {it==0.toByte()});job.cancelAndJoin()
    }
    @Test fun twentySeekAndPauseInterleavingsDoNotAcknowledgeOldAudio()=runTest {
        repeat(20) {
            val entered=CompletableDeferred<Unit>();val finish=CompletableDeferred<Unit>();val ack=mutableListOf<Pair<Long,Long>>()
            val arrays=mutableListOf<ByteArray>();var resets=0
            val stream=BilibiliAudioStream(destination,{p,current->arrays.add(p.bytes);if(p.epoch==1L){entered.complete(Unit);finish.await()};current()},{resets++},{e,s->ack.add(e to s)})
            fun receive(raw:String)=stream.receive(raw,"https://player.bilibili.com",true,destination)
            receive(state(1));receive(pcm());val job=launch {stream.run()};entered.await()
            receive(state(2,false));receive(pcm());receive(state(3));receive(pcm(epoch=3));finish.complete(Unit);runCurrent()
            assertEquals(listOf(3L to 1L),ack);assertTrue(arrays.all {a->a.all {it==0.toByte()}});assertTrue(resets>=3)
            job.cancelAndJoin()
        }
    }
    @Test fun foreignOriginSubframeAndChangedDocumentCannotStartNativePlayback()=runTest {
        for((origin,main,url) in listOf(Triple("https://example.invalid",true,destination),Triple("https://player.bilibili.com",false,destination),Triple("https://player.bilibili.com",true,destination+"&other=1"))) {
            var stopped=false
            val stream=BilibiliAudioStream(destination,{_,_->error("must not play")},{stopped=true},{_,_->fail()})
            stream.receive(state(1),origin,main,url)
            try{stream.run();fail()}catch(e:ChatFailure){assertEquals("BILI_NAVIGATION",e.code)}
            assertTrue(stopped)
        }
    }
    @Test fun cancellationErasesInFlightPcmAndStopsNativeOutput()=runTest {
        val entered=CompletableDeferred<Unit>();var bytes:ByteArray?=null;var resets=0
        val stream=BilibiliAudioStream(destination,{p,_->bytes=p.bytes;entered.complete(Unit);awaitCancellation()},{resets++},{_,_->fail()})
        stream.receive(state(1),"https://player.bilibili.com",true,destination);stream.receive(pcm(),"https://player.bilibili.com",true,destination)
        val job=launch {stream.run()};entered.await();job.cancelAndJoin()
        assertTrue(bytes!!.all {it==0.toByte()});assertEquals(2,resets)
        stream.receive(pcm(),"https://player.bilibili.com",true,destination);assertEquals(2,resets)
    }
    @Test fun movingVideoWithoutPcmTimesOutButIdleOrSeekingDoesNot() {
        val stream=BilibiliAudioStream(destination,{_,_->true},{},{_,_->})
        stream.requireFlow(false,0);stream.requireFlow(false,60_000)
        stream.requireFlow(true,60_000);stream.requireFlow(true,65_000)
        try{stream.requireFlow(true,65_001);fail()}catch(e:ChatFailure){assertEquals("BILI_AUDIO",e.code)}
        stream.requireFlow(false,66_000);stream.requireFlow(true,100_000);stream.requireFlow(true,105_000)
        stream.close()
    }
    @Test fun messageCallbackCleanupFailureIsReportedThroughTheAudioJob()=runTest {
        val stream=BilibiliAudioStream(destination,{_,_->true},{throw IllegalStateException("synthetic")},{_,_->})
        stream.receive("invalid","https://player.bilibili.com",true,destination)
        try{stream.run();fail()}catch(e:ChatFailure){assertEquals("AUDIO_ROUTE_CLEANUP",e.code)}
    }
}
