package com.ai.assistance.operit.api.audio

import com.ai.assistance.operit.data.model.ChatFailure
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** AudioTrack writes enqueue frames; completion requires the hardware playback head to catch up. */
internal object PcmPlayback {
    suspend fun awaitDrain(totalFrames:Long,playedFrames:()->Long,requirePlaying:()->Unit,
        clock:()->Long={System.nanoTime()/1_000_000},stallTimeoutMs:Long=5_000) {
        require(totalFrames>0&&stallTimeoutMs>0)
        var previous=0L
        var lastProgress=clock()
        while(true) {
            currentCoroutineContext().ensureActive()
            requirePlaying()
            val played=playedFrames()
            if(played<previous)throw ChatFailure("AUDIO_PLAYBACK")
            if(played>=totalFrames)return
            val now=clock()
            if(played>previous){previous=played;lastProgress=now}
            if(now-lastProgress>=stallTimeoutMs)throw ChatFailure("AUDIO_PLAYBACK")
            delay(20)
        }
    }
}
