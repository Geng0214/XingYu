package com.ai.assistance.operit.api.audio

import android.content.Context
import android.media.*
import android.os.*
import com.ai.assistance.operit.data.model.ChatFailure
import kotlinx.coroutines.*

/** API 26–27 media uses its actual AudioTrack; video frames follow that track's media clock. */
internal class LegacyLocalMediaPlayer(private val context:Context) {
    suspend fun play(source:AndroidMediaSource,startMs:Long,onPosition:suspend (Long)->Unit,surface:android.view.Surface?=null):Boolean=withContext(Dispatchers.Main.immediate) {
        val attributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        var track:AudioTrack?=null
        var listener:AudioRouting.OnRoutingChangedListener?=null
        var released=false
        var active=true
        var videoJob:Deferred<Unit>?=null
        var audioDrained=false
        val videoReady=CompletableDeferred<Unit>()
        fun releaseTrack() {
            if(released)return
            released=true
            track?.let {owned->
                try {listener?.let {owned.removeOnRoutingChangedListener(it)}}finally {
                    try {if(owned.playState==AudioTrack.PLAYSTATE_PLAYING){owned.pause();owned.flush()}}finally {owned.release()}
                }
            }
        }
        fun stopPlayback(){active=false;videoJob?.cancel();releaseTrack()}
        val session=AndroidMediaAudioSession(context,attributes,::stopPlayback)
        fun observe() {
            session.requireHealthy()
            val owned=track?:return
            session.observe(listOfNotNull(owned.routedDevice).map {CommunicationDevice(it.id,it.type,"")})
        }
        var timeline:MediaPcmTimeline?=null
        var rate=0;var channels=0
        var lastReport=0L
        val positions=MediaPositionPublisher(onPosition)
        fun audioPositionUs():Long? {
            if(audioDrained)return null
            val owned=track?:return null
            return timeline?.positionUs(owned.playbackHeadPosition.toLong() and 0xffffffffL)
        }
        suspend fun report(positionUs:Long,force:Boolean=false) {
            val now=SystemClock.elapsedRealtime()
            if((force||now-lastReport>=250)&&positions.submit(positionUs))lastReport=SystemClock.elapsedRealtime()
        }
        suspend fun progress() {
            currentCoroutineContext().ensureActive();observe()
            val owned=track?:return
            val value=timeline?:return
            report(value.positionUs(owned.playbackHeadPosition.toLong() and 0xffffffffL))
        }
        suspend fun write(bytes:ByteArray,start:Int,frameBytes:Int) {
            var offset=start;var lastWrite=SystemClock.elapsedRealtime()
            while(offset<bytes.size) {
                currentCoroutineContext().ensureActive();observe()
                val owned=track?:throw ChatFailure("MUSIC_PLAYBACK")
                val wanted=minOf(4096*frameBytes,bytes.size-offset)
                val count=owned.write(bytes,offset,wanted,AudioTrack.WRITE_NON_BLOCKING)
                session.requireHealthy()
                if(count<0||count>wanted||count%frameBytes!=0)throw ChatFailure("MUSIC_PLAYBACK")
                if(count>0){offset+=count;lastWrite=SystemClock.elapsedRealtime()}
                else {
                    if(SystemClock.elapsedRealtime()-lastWrite>=5_000)throw ChatFailure("MUSIC_PLAYBACK")
                    delay(10)
                }
                // Silence is deliberately not registered on the media timeline.
                if(timeline!=null)progress()
            }
        }
        try {
            require(source.kind in setOf("audio","video")&&startMs in 0..com.ai.assistance.operit.data.model.LocalMusic.MAX_DURATION_MS)
            val streams=if(source.kind=="video") {
                if(surface?.isValid!=true)throw ChatFailure("VIDEO_SURFACE")
                AndroidMediaVideoDecoder.streams(source)
            }else null
            if(streams!=null&&startMs*1000>=streams.durationUs){report(streams.durationUs,true);return@withContext true}
            val hasAudio=streams==null||streams.audioDurationUs?.let {startMs*1000<it}==true
            val videoClock=VideoPresentationClock(hasAudio,startMs*1000)
            session.install();session.requestFocus()
            session.whileHealthy {coroutineScope {
                if(streams!=null)videoJob=async(Dispatchers.IO) {
                    AndroidMediaVideoDecoder.decode(source,startMs*1000,requireNotNull(surface)) {timeUs,render->
                        withContext(Dispatchers.Main.immediate) {
                            fun checkFrame() {
                                if(!active)throw CancellationException("Playback stopped")
                                if(!surface.isValid)throw ChatFailure("VIDEO_SURFACE")
                                observe()
                            }
                            checkFrame()
                            if(timeUs>=startMs*1000)videoReady.complete(Unit)
                            val show=awaitVideoFrame(timeUs,startMs*1000,
                                {videoClock.positionUs(audioPositionUs(),System.nanoTime())},check=::checkFrame,onProgress={report(it)})
                            currentCoroutineContext().ensureActive();checkFrame()
                            if(show)render()
                        }
                    }
                    if(!videoReady.isCompleted)throw ChatFailure("MUSIC_PLAYBACK")
                }
                try {
                var queuedUs=startMs*1000
                if(hasAudio)withDecodedMediaPcm({emit->AndroidMediaPcmDecoder.decode(source,startMs*1000,emit)},::stopPlayback) {pcm->
                    val skipped=pcm.skippedFrames(startMs*1000)
                    if(skipped<pcm.bytes.size/pcm.frameBytes) {
                        if(track==null) {
                            rate=pcm.rate;channels=pcm.channels
                            val format=AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate)
                                .setChannelIndexMask((1 shl channels)-1).build()
                            val owned=AudioTrack.Builder().setAudioAttributes(attributes).setAudioFormat(format)
                                .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(rate*pcm.frameBytes/2).build().also {track=it}
                            if(owned.state!=AudioTrack.STATE_INITIALIZED)throw ChatFailure("MUSIC_PLAYBACK")
                            listener=AudioRouting.OnRoutingChangedListener {
                                if(!released)try {observe()}catch(_:Exception){session.fail("MUSIC_ROUTE_CHANGED")}
                            }.also {owned.addOnRoutingChangedListener(it,Handler(Looper.getMainLooper()))}
                            owned.play()
                            val silence=ByteArray((rate/50)*pcm.frameBytes)
                            var silenceFrames=0L
                            val deadline=SystemClock.elapsedRealtime()+5_000
                            // Only this track's silence can confirm where this track actually plays.
                            do {
                                write(silence,0,pcm.frameBytes);silenceFrames+=silence.size/pcm.frameBytes
                                observe()
                                if(SystemClock.elapsedRealtime()>=deadline&&!session.routeReady())throw ChatFailure("MUSIC_ROUTE_UNAVAILABLE")
                                if(!session.routeReady())delay(10)
                            }while(!session.routeReady())
                            timeline=MediaPcmTimeline(rate,silenceFrames,startMs*1000)
                            if(streams!=null)withTimeout(15_000){videoReady.await()}
                        }
                        if(pcm.rate!=rate||pcm.channels!=channels)throw ChatFailure("MUSIC_FORMAT")
                        val frames=pcm.bytes.size/pcm.frameBytes-skipped
                        val clock=requireNotNull(timeline)
                        val firstUs=pcm.timeUs+skipped*1_000_000L/rate
                        // Timestamp gaps are real media silence, unlike route-preparation silence.
                        // Keeping them on the audio clock prevents video jumping to a late audio start.
                        var gapFrames=(firstUs-queuedUs).coerceAtLeast(0)*rate/1_000_000
                        while(gapFrames>0) {
                            val count=minOf(gapFrames,rate/10L).toInt()
                            val silence=ByteArray(count*pcm.frameBytes)
                            clock.append(count,queuedUs);write(silence,0,pcm.frameBytes)
                            queuedUs+=count*1_000_000L/rate;gapFrames-=count
                        }
                        clock.append(frames,firstUs)
                        write(pcm.bytes,skipped*pcm.frameBytes,pcm.frameBytes)
                        queuedUs=pcm.timeUs+pcm.bytes.size/pcm.frameBytes*1_000_000L/rate
                    }
                }
                if(hasAudio) {
                    val owned=track?:throw ChatFailure("MUSIC_PLAYBACK")
                    val clock=timeline?:throw ChatFailure("MUSIC_PLAYBACK")
                    PcmPlayback.awaitDrain(clock.submittedFrames,{owned.playbackHeadPosition.toLong() and 0xffffffffL},{observe()})
                    session.requireHealthy()
                    val endUs=clock.positionUs(owned.playbackHeadPosition.toLong() and 0xffffffffL)
                    videoClock.audioDrained(endUs,System.nanoTime());audioDrained=true
                    report(endUs,true)
                }
                videoJob?.await()
                if(streams!=null) {
                    awaitVideoFrame(streams.durationUs,startMs*1000,{videoClock.positionUs(null,System.nanoTime())},
                        check={session.requireHealthy();if(surface?.isValid!=true)throw ChatFailure("VIDEO_SURFACE")},onProgress={report(it)})
                    report(streams.durationUs,true)
                }
                true
                }finally {stopPlayback()}
            }}
        }finally {try {stopPlayback()}finally {try {session.close()}finally {source.close()}}}
    }
}
