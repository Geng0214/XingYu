package com.ai.assistance.operit.api.audio

import android.media.*
import android.os.*
import com.ai.assistance.operit.data.model.ChatFailure
import kotlinx.coroutines.*

private class ObsoleteBilibiliAudio:Exception()

/** Main-thread-only native output. Every seek/pause invalidates and releases its old track. */
internal class BilibiliPcmOutput(private val session:AndroidMediaAudioSession) {
    private var track:AudioTrack?=null
    private var listener:AudioRouting.OnRoutingChangedListener?=null
    private var epoch=0L
    private var confirmed=false
    fun reset() {
        val old=track;track=null;epoch=0;confirmed=false
        val callback=listener;listener=null
        if(old!=null)try {callback?.let {old.removeOnRoutingChangedListener(it)}}finally {
            try {if(old.playState==AudioTrack.PLAYSTATE_PLAYING){old.pause();old.flush()}}finally {old.release()}
        }
    }
    private fun observe(owned:AudioTrack) {
        session.requireHealthy()
        val devices=if(Build.VERSION.SDK_INT>=36)owned.routedDevices else listOfNotNull(owned.routedDevice)
        if(devices.isEmpty()&&!confirmed)return
        session.observe(devices.map {CommunicationDevice(it.id,it.type,"")})
        confirmed=devices.isNotEmpty()
    }
    suspend fun play(packet:BilibiliPcm,current:()->Boolean):Boolean {
        fun checkPacket() {
            session.requireHealthy()
            if(!current())throw ObsoleteBilibiliAudio()
            track?.let {observe(it)}
        }
        try {
            checkPacket()
            if(track==null) {
                val minimum=AudioTrack.getMinBufferSize(packet.rate,AudioFormat.CHANNEL_OUT_STEREO,AudioFormat.ENCODING_PCM_16BIT)
                if(minimum<=0)throw ChatFailure("BILI_AUDIO")
                val owned=AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
                    .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(packet.rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                    .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes(maxOf(minimum,packet.rate*4/20)).build().also {track=it;epoch=packet.epoch}
                if(owned.state!=AudioTrack.STATE_INITIALIZED)throw ChatFailure("BILI_AUDIO")
                listener=AudioRouting.OnRoutingChangedListener {
                    if(track===owned)try {observe(owned)}catch(_:Exception){session.fail("MUSIC_ROUTE_CHANGED")}
                }.also {owned.addOnRoutingChangedListener(it,Handler(Looper.getMainLooper()))}
                owned.play()
                val silence=ByteArray(packet.rate/100*4)
                val deadline=SystemClock.elapsedRealtime()+5000
                do {
                    PcmTransfer.write(silence,{offset,count->owned.write(silence,offset,count,AudioTrack.WRITE_NON_BLOCKING)},::checkPacket)
                    if(SystemClock.elapsedRealtime()>=deadline&&!confirmed)throw ChatFailure("MUSIC_ROUTE_UNAVAILABLE")
                    if(!confirmed)delay(5)
                }while(!confirmed)
            }
            if(epoch!=packet.epoch)throw ObsoleteBilibiliAudio()
            val owned=track?:throw ObsoleteBilibiliAudio()
            PcmTransfer.write(packet.bytes,{offset,count->owned.write(packet.bytes,offset,count,AudioTrack.WRITE_NON_BLOCKING)},::checkPacket)
            checkPacket();return true
        }catch(_:ObsoleteBilibiliAudio){return false}
    }
}
