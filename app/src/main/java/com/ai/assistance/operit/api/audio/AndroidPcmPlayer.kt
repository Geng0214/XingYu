package com.ai.assistance.operit.api.audio

import android.content.Context
import android.media.*
import com.ai.assistance.operit.data.model.ChatFailure
import com.ai.assistance.operit.data.model.SpeechServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicReference

class AndroidPcmPlayer(context:Context) {
    private val audioManager=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private class Playback {
        @Volatile var stopRequested=false
        @Volatile var track:AudioTrack?=null
        fun requirePlaying() {
            if(stopRequested)throw CancellationException("Playback stopped")
        }
        fun stop(){stopRequested=true;try {track?.pause()}catch(_:Exception){}}
    }
    private val active=AtomicReference<Playback?>()

    suspend fun play(pcm:ByteArray,binding:CommunicationPlaybackBinding,onReady:()->Unit={})=withContext(Dispatchers.IO) {
        if(pcm.isEmpty()||pcm.size>SpeechServices.MAX_SPEECH_BYTES||pcm.size%2!=0)throw ChatFailure("SPEECH_PROTOCOL")
        currentCoroutineContext().ensureActive()
        val playback=Playback()
        if(!active.compareAndSet(null,playback))throw ChatFailure("SPEECH_BUSY")
        val attributes=AudioAttributes.Builder().setLegacyStreamType(AudioManager.STREAM_VOICE_CALL)
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        var converted:ByteArray?=null
        try {
            val context=currentCoroutineContext()
            fun checkOwner(){context.ensureActive();binding.requireLease();playback.requirePlaying()}
            checkOwner()
            // The caller's lease owns focus throughout synthesis and playback.
            converted=CommunicationPcm.playback(pcm,::checkOwner)
            val bytes=requireNotNull(converted)
            val sampleRate=16_000
            val format=AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
            val minimum=AudioTrack.getMinBufferSize(sampleRate,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT)
            if(minimum<=0)throw ChatFailure("AUDIO_PLAYBACK")
            val track=try {AudioTrack.Builder().setAudioAttributes(attributes).setAudioFormat(format).setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(maxOf(minimum,8192)).build()}
                catch(_:Exception){throw ChatFailure("AUDIO_PLAYBACK")}
            playback.track=track
            val observer=AndroidObservedAudioRoute(audioManager,track,binding.output,false,binding.requireLease) {
                // This callback captures this track, never the next playback instance.
                try {track.pause()}catch(_:Exception){}
            }
            try {
                checkOwner();observer.install()
                track.play()
                var leadingFrames=0L
                run {
                    // Start with silence: getRoutedDevice is meaningful only during playback.
                    val silence=ByteArray(2048)
                    withTimeoutOrNull(30_000) {
                        while(!observer.poll()) {
                            checkOwner()
                            val count=track.write(silence,0,silence.size,AudioTrack.WRITE_NON_BLOCKING)
                            if(count<0||count%2!=0)throw ChatFailure("AUDIO_PLAYBACK")
                            leadingFrames+=count/2
                            delay(10)
                        }
                        true
                    }?:throw ChatFailure("AUDIO_ROUTE_TIMEOUT")
                }
                fun checkRoute(){checkOwner();observer.requireReady()}
                checkRoute();onReady();checkRoute()
                suspend fun write(bytes:ByteArray)=PcmTransfer.write(bytes,
                    {offset,count->track.write(bytes,offset,count,AudioTrack.WRITE_NON_BLOCKING)},::checkRoute)
                write(bytes)
                // Streaming starts only after the device's buffer threshold is filled. Pad short
                // utterances with silence, but wait for the original speech frames, not an estimate.
                var missing=(track.bufferCapacityInFrames*2L-bytes.size).coerceAtLeast(0)
                while(missing>0) {
                    val silence=ByteArray(minOf(8192L,missing).toInt())
                    write(silence);missing-=silence.size
                }
                PcmPlayback.awaitDrain(leadingFrames+bytes.size/2L,{track.playbackHeadPosition.toLong() and 0xffffffffL},::checkRoute)
            } finally {
                try {observer.close()}finally {
                    try {track.pause();track.flush();track.stop()}catch(_:Exception){}
                    try {track.release()}finally{playback.track=null}
                }
            }
        } finally {
            converted?.fill(0)
            active.compareAndSet(playback,null)
        }
    }

    fun stop(){active.get()?.stop()}
}
