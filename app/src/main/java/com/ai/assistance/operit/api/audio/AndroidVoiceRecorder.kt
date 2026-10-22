package com.ai.assistance.operit.api.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.ai.assistance.operit.data.model.ChatFailure
import com.ai.assistance.operit.data.model.RecordedVoice
import com.ai.assistance.operit.data.model.SpeechServices
import java.io.ByteArrayOutputStream
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.delay

class AndroidVoiceRecorder(private val context:Context) {
    private val audioManager=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    @Volatile private var stopRequested=false
    @Volatile private var cancelRequested=false
    private val active=AtomicReference<AudioRecord?>()
    private class WipingBuffer:ByteArrayOutputStream() {fun wipe(){buf.fill(0);reset()}}

    fun prepare(){stopRequested=false;cancelRequested=false}

    suspend fun record(binding:CommunicationAudioBinding,onReady:()->Unit={}):RecordedVoice {
        var produced:RecordedVoice?=null
        try {return withContext(Dispatchers.IO) {
        if(ContextCompat.checkSelfPermission(context,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED)throw ChatFailure("MICROPHONE_PERMISSION")
        val channel=AudioFormat.CHANNEL_IN_MONO;val encoding=AudioFormat.ENCODING_PCM_16BIT
        val rate=if(binding.input.type==AudioDeviceInfo.TYPE_BLUETOOTH_SCO)8_000 else SpeechServices.RECORD_SAMPLE_RATE
        val limit=rate*2*SpeechServices.MAX_RECORD_SECONDS
        val minimum=AudioRecord.getMinBufferSize(rate,channel,encoding)
        if(minimum<=0)throw ChatFailure("MICROPHONE_UNAVAILABLE")
        val buffer=ByteArray(maxOf(minimum,rate/5*2))
        val recorder=try {AudioRecord.Builder().setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            .setAudioFormat(AudioFormat.Builder().setEncoding(encoding).setSampleRate(rate).setChannelMask(channel).build())
            .setBufferSizeInBytes(buffer.size*2).build()} catch(_:SecurityException){throw ChatFailure("MICROPHONE_PERMISSION")}
          catch(_:Exception){throw ChatFailure("MICROPHONE_UNAVAILABLE")}
        if(!active.compareAndSet(null,recorder)){recorder.release();throw ChatFailure("SPEECH_BUSY")}
        val out=WipingBuffer()
        val observer=AndroidObservedAudioRoute(audioManager,recorder,binding.input,true,binding.requireLease) {
            try {recorder.stop()}catch(_:Exception){}
        }
        try {
            if(recorder.state!=AudioRecord.STATE_INITIALIZED)throw ChatFailure("MICROPHONE_UNAVAILABLE")
            val coroutine=currentCoroutineContext()
            fun checkOwner(){
                coroutine.ensureActive();binding.requireLease()
                if(cancelRequested)throw CancellationException("Recording cancelled")
            }
            checkOwner();observer.install()
            recorder.startRecording()
            run {
                // Discard samples captured before the actual input is confirmed.
                withTimeoutOrNull(30_000) {
                    while(!observer.poll()) {
                        checkOwner()
                        if(stopRequested)throw ChatFailure("RECORDING_TOO_SHORT")
                        val read=recorder.read(buffer,0,buffer.size,AudioRecord.READ_NON_BLOCKING)
                        buffer.fill(0)
                        if(read<0){observer.requireReady();throw ChatFailure("MICROPHONE_UNAVAILABLE")}
                        delay(10)
                    }
                    // Discard a full native buffer after confirmation, including any frames queued
                    // on a previous input. A single read need not empty AudioRecord's larger buffer.
                    var remaining=recorder.bufferSizeInFrames.toLong()*2
                    if(remaining<=0||remaining>rate*4L)throw ChatFailure("MICROPHONE_UNAVAILABLE")
                    while(remaining>0) {
                        checkOwner();observer.requireReady()
                        if(stopRequested)throw ChatFailure("RECORDING_TOO_SHORT")
                        val queued=recorder.read(buffer,0,minOf(buffer.size.toLong(),remaining).toInt(),AudioRecord.READ_NON_BLOCKING)
                        buffer.fill(0);observer.requireReady()
                        if(queued<0)throw ChatFailure("MICROPHONE_UNAVAILABLE")
                        remaining-=queued
                        if(queued==0)delay(10)
                    }
                    true
                }?:throw ChatFailure("AUDIO_ROUTE_TIMEOUT")
            }
            fun checkRecording(){checkOwner();observer.requireReady()}
            checkRecording();onReady();checkRecording()
            while(!stopRequested&&out.size()<limit) {
                checkRecording()
                val requested=minOf(buffer.size,limit-out.size())
                val read=PcmTransfer.read(requested,{count->recorder.read(buffer,0,count,AudioRecord.READ_NON_BLOCKING)},::checkRecording,{stopRequested})
                checkRecording()
                if(read>0)out.write(buffer,0,read)
            }
            checkOwner();observer.requireReady()
            val raw=out.toByteArray()
            var pcm:ByteArray?=null
            try {
                fun checkCapture(){checkOwner();observer.requireReady()}
                pcm=if(rate==8_000)CommunicationPcm.capture(raw,::checkCapture) else if(raw.size%2==0)raw else raw.copyOf(raw.size-1)
                val result=RecordedVoice(pcm,pcm.size*1000L/(SpeechServices.RECORD_SAMPLE_RATE*2)).also {produced=it}
                SpeechServices.validateRecording(result);checkCapture();result
            } catch(e:Throwable){pcm?.fill(0);throw e}
            finally {if(pcm!==raw)raw.fill(0)}
        } catch(e:IllegalArgumentException){throw ChatFailure("RECORDING_TOO_SHORT")}
          finally {
            buffer.fill(0);out.wipe()
            try {observer.close()}finally {
                try {if(recorder.recordingState==AudioRecord.RECORDSTATE_RECORDING)recorder.stop()}catch(_:Exception){}
                try {recorder.release()}finally{active.compareAndSet(recorder,null)}
            }
        }
        }}catch(e:Throwable){
            // Cancellation on the IO-to-caller handoff or failed cleanup must not abandon PCM.
            produced?.pcm?.fill(0);throw e
        }
    }

    fun stop(){stopRequested=true}
    fun cancel(){cancelRequested=true;stopRequested=true;try {active.get()?.stop()}catch(_:Exception){}}
}
