package com.ai.assistance.operit.api.audio

import com.ai.assistance.operit.data.model.ChatFailure
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

internal data class DecodedMediaPcm(val rate:Int,val channels:Int,val timeUs:Long,val bytes:ByteArray) {
    val frameBytes:Int get()=channels*2
    fun validate() {
        if(rate !in 8000..192000||channels !in 1..8||timeUs !in -21_600_000_000L..21_600_000_000L||bytes.isEmpty()||bytes.size>512*1024||bytes.size%frameBytes!=0)
            throw ChatFailure("MUSIC_FORMAT")
    }
    // Seek to an earlier compressed sample, then remove only whole decoded frames before the target.
    fun skippedFrames(startUs:Long):Int {
        validate()
        require(startUs in 0..21_600_000_000L)
        val delta=(startUs-timeUs).coerceAtLeast(0)
        val frames=bytes.size/frameBytes
        if(delta>frames*1_000_000L/rate+1)return frames
        return minOf(frames.toLong(),(delta*rate+999_999)/1_000_000).toInt()
    }
}

/** A bounded PCM handoff. Exactly one owner erases each buffer, including cancelled receives. */
internal suspend fun withDecodedMediaPcm(
    decode:suspend (suspend (DecodedMediaPcm)->Unit)->Unit,
    stopConsumerOnFailure:()->Unit={},
    consume:suspend (DecodedMediaPcm)->Unit,
)=coroutineScope {
    val queue=Channel<DecodedMediaPcm>(1,onUndeliveredElement={it.bytes.fill(0)})
    val producer=launch(Dispatchers.IO) {
        try {
            decode {pcm->
                var transferred=false
                try {
                    pcm.validate()
                    while(!transferred) {
                        currentCoroutineContext().ensureActive()
                        val sent=queue.trySend(pcm)
                        if(sent.isClosed)throw CancellationException("PCM consumer closed")
                        if(sent.isSuccess)transferred=true else delay(5)
                    }
                }finally {if(!transferred)pcm.bytes.fill(0)}
            }
        }catch(e:CancellationException){throw e}
        catch(e:Exception){queue.close(e)}
        finally {queue.close()}
    }
    var consumed=false
    try {for(pcm in queue)try {consume(pcm)}finally {pcm.bytes.fill(0)};consumed=true}
    finally {withContext(NonCancellable){
        // Stop queued native audio immediately; a decoder's native release may take longer.
        try {if(!consumed)stopConsumerOnFailure()}
        finally {try {producer.cancelAndJoin()}finally {queue.cancel()}}
    }}
}

/** Maps the actual playback head to media timestamps, excluding route-confirmation silence. */
internal class MediaPcmTimeline(private val rate:Int,initialFrames:Long,startUs:Long) {
    private data class Span(val first:Long,val count:Long,val timeUs:Long)
    private val spans=ArrayDeque<Span>()
    var submittedFrames:Long=initialFrames
        private set
    private var positionUs=startUs
    private var previousHead=0L
    init {require(rate in 8000..192000&&initialFrames>=0&&startUs>=0)}
    fun append(frames:Int,timeUs:Long) {
        require(frames>0)
        if(submittedFrames+frames>(21_600L+5)*rate)throw ChatFailure("MUSIC_LIMIT")
        spans.addLast(Span(submittedFrames,frames.toLong(),timeUs))
        submittedFrames+=frames
    }
    fun positionMs(head:Long):Long=positionUs(head)/1000
    fun positionUs(head:Long):Long {
        if(head<previousHead||head>submittedFrames)throw ChatFailure("MUSIC_PLAYBACK")
        previousHead=head
        while(spans.isNotEmpty()) {
            val span=spans.first()
            if(head<=span.first)break
            val played=minOf(span.count,head-span.first)
            positionUs=maxOf(positionUs,span.timeUs+played*1_000_000/rate)
            if(played<span.count)break
            spans.removeFirst()
        }
        return positionUs
    }
}
