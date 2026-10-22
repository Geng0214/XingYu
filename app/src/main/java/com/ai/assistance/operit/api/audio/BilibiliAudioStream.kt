package com.ai.assistance.operit.api.audio

import com.ai.assistance.operit.data.model.ChatFailure
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** Main-thread bridge coordinator, separate from WebView and native audio for lifecycle testing. */
internal class BilibiliAudioStream(
    private val destination:String,
    private val play:suspend (BilibiliPcm,()->Boolean)->Boolean,
    private val reset:()->Unit,
    private val acknowledge:(Long,Long)->Unit,
) {
    private val inbox=BilibiliAudioInbox()
    private val wake=Channel<Unit>(Channel.CONFLATED)
    private var closed=false
    private var failure:String?=null
    private var movingSince:Long?=null
    private var lastPacket:Long?=null
    fun requireFlow(moving:Boolean,now:Long=System.nanoTime()/1_000_000) {
        if(!moving){movingSince=null;return}
        val since=movingSince?:now.also {movingSince=it}
        if(now-maxOf(since,lastPacket?:since)>5000)throw ChatFailure("BILI_AUDIO")
    }
    fun receive(raw:String,origin:String,isMainFrame:Boolean,currentUrl:String?) {
        if(closed)return
        try {
            if(origin!="https://player.bilibili.com"||!isMainFrame||currentUrl!=destination)throw ChatFailure("BILI_NAVIGATION")
            when(inbox.accept(raw)) {
                BilibiliAudioEvent.STATE->reset()
                BilibiliAudioEvent.PCM->lastPacket=System.nanoTime()/1_000_000
                BilibiliAudioEvent.STALE->Unit
            }
            wake.trySend(Unit)
        }catch(e:Exception){
            failure=if(e is ChatFailure)e.code else "BILI_AUDIO"
            // run() reports both protocol and cleanup failures to the structured parent job.
            try {close()}catch(_:ChatFailure){}
        }
    }
    suspend fun run() {
        try {
            while(true) {
                currentCoroutineContext().ensureActive()
                failure?.let {throw ChatFailure(it)}
                if(closed)throw CancellationException("Web audio closed")
                wake.receive()
                while(true) {
                    val packet=inbox.take()?:break
                    try {
                        currentCoroutineContext().ensureActive()
                        if(inbox.current(packet)&&play(packet){inbox.current(packet)}) {
                            currentCoroutineContext().ensureActive()
                            if(inbox.current(packet))acknowledge(packet.epoch,packet.sequence)
                        }
                    }finally {packet.bytes.fill(0)}
                }
            }
        }catch(e:Exception){failure?.let {throw ChatFailure(it)};throw e}
        finally {close()}
    }
    fun close() {
        if(closed)return
        closed=true;inbox.close()
        try {reset()}catch(e:Exception){failure="AUDIO_ROUTE_CLEANUP";throw ChatFailure("AUDIO_ROUTE_CLEANUP")}
        finally {wake.cancel()}
    }
}
