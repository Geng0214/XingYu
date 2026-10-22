package com.ai.assistance.operit.api.audio

import com.ai.assistance.operit.data.model.ChatFailure
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Audio and video sample on one UI thread but their persistence calls can suspend independently. */
internal class MediaPositionPublisher(private val publish:suspend (Long)->Unit) {
    private val mutex=Mutex()
    private var lastUs=-1L
    suspend fun submit(positionUs:Long):Boolean=mutex.withLock {
        require(positionUs>=0)
        if(positionUs<lastUs)return@withLock false
        publish(positionUs/1000);lastUs=positionUs;true
    }
}

/** Owns at most one preroll codec slot. Every accepted slot is released exactly once. */
internal class SeekVideoFrames(private val startUs:Long,private val release:(Int,Boolean)->Unit,
    private val present:suspend (Long,()->Unit)->Unit):AutoCloseable {
    private var pending:Int?=null
    private var closed=false
    suspend fun accept(slot:Int,timeUs:Long) {
        var owned=true
        try {
            check(!closed)
            if(timeUs !in -21_600_000_000L..21_600_000_000L)throw ChatFailure("MUSIC_FORMAT")
            if(timeUs<startUs) {
                val old=pending;pending=null
                old?.let {release(it,false)}
                pending=slot;owned=false
            }else {finish();owned=false;deliver(slot,timeUs)}
        }finally {if(owned)release(slot,false)}
    }
    suspend fun finish() {
        check(!closed)
        val slot=pending?:return
        pending=null;deliver(slot,startUs)
    }
    private suspend fun deliver(slot:Int,timeUs:Long) {
        var released=false
        try {present(timeUs){released=true;release(slot,true)}}
        finally {if(!released)release(slot,false)}
    }
    override fun close() {
        if(closed)return
        closed=true
        val slot=pending;pending=null
        slot?.let {release(it,false)}
    }
}

/** The clock is sampled from AudioTrack while audio runs, then advances after its actual drain. */
internal class VideoPresentationClock(private val hasAudio:Boolean,private val startUs:Long) {
    private var anchorUs:Long?=null
    private var anchorNs=0L
    private var lastUs=startUs
    fun positionUs(audioUs:Long?,nowNs:Long):Long? {
        if(hasAudio&&anchorUs==null) {
            if(audioUs==null)return null
            if(audioUs<lastUs)throw ChatFailure("MUSIC_PLAYBACK")
            lastUs=audioUs;return audioUs
        }
        if(anchorUs==null){anchorUs=startUs;anchorNs=nowNs}
        if(nowNs<anchorNs)throw ChatFailure("MUSIC_PLAYBACK")
        val value=requireNotNull(anchorUs)+(nowNs-anchorNs)/1000
        if(value<lastUs)throw ChatFailure("MUSIC_PLAYBACK")
        lastUs=value;return value
    }
    fun audioDrained(positionUs:Long,nowNs:Long) {
        check(hasAudio&&anchorUs==null)
        if(positionUs<lastUs)throw ChatFailure("MUSIC_PLAYBACK")
        lastUs=positionUs;anchorUs=positionUs;anchorNs=nowNs
    }
}

/** Waiting is cancellable, and a stalled audio clock cannot retain a video buffer indefinitely. */
internal suspend fun awaitVideoFrame(timeUs:Long,startUs:Long,position:()->Long?,
    nowMs:()->Long={System.nanoTime()/1_000_000},check:()->Unit={},onProgress:suspend (Long)->Unit={}):Boolean {
    if(timeUs !in -21_600_000_000L..21_600_000_000L)throw ChatFailure("MUSIC_FORMAT")
    currentCoroutineContext().ensureActive();check()
    if(timeUs<startUs)return false
    var previous:Long?=null
    var lastProgress=nowMs()
    while(true) {
        currentCoroutineContext().ensureActive();check()
        val current=position()
        if(current!=null) {
            if(previous!=null&&current<previous)throw ChatFailure("MUSIC_PLAYBACK")
            if(current!=previous){previous=current;lastProgress=nowMs()}
            onProgress(current)
            currentCoroutineContext().ensureActive();check()
            // Persistence may suspend while the hardware keeps playing. Decide using a fresh
            // sample so slow storage cannot turn a previously on-time frame into a late render.
            val after=position()?:throw ChatFailure("MUSIC_PLAYBACK")
            if(after<current)throw ChatFailure("MUSIC_PLAYBACK")
            if(after!=previous){previous=after;lastProgress=nowMs()}
            if(timeUs<=after+10_000)return after-timeUs<=100_000
        }
        if(nowMs()-lastProgress>=10_000)throw ChatFailure("MUSIC_PLAYBACK")
        delay(5)
    }
}
