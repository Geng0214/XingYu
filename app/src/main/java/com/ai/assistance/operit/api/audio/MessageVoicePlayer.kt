package com.ai.assistance.operit.api.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/** 聊天内语音播放走媒体音量，不占用通话路由，也不需要设备选择。 */
class MessageVoicePlayer {
    private var track:AudioTrack?=null
    private var worker:Thread?=null

    fun play(sampleRate:Int,pcm:ByteArray,onDone:()->Unit) {
        stop()
        val minBuffer=AudioTrack.getMinBufferSize(sampleRate,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT)
        val created=AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build())
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build())
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(maxOf(minBuffer,pcm.size.coerceAtMost(minBuffer*4)))
            .build()
        track=created
        worker=Thread {
            try {
                created.play()
                var offset=0
                while(offset<pcm.size&&track===created) {
                    val written=created.write(pcm,offset,pcm.size-offset)
                    if(written<=0)break
                    offset+=written
                }
                if(track===created)created.stop()
            } catch(_:IllegalStateException) {
            } finally {
                created.release()
                if(track===created)track=null
                onDone()
            }
        }.also {it.isDaemon=true;it.start()}
    }

    fun stop() {
        val current=track
        track=null
        worker=null
        try {current?.pause();current?.flush()} catch(_:IllegalStateException) {}
    }
}
