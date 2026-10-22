package com.ai.assistance.operit.api.audio

import com.ai.assistance.operit.data.model.ChatFailure
import com.ai.assistance.operit.data.model.SpeechServices
import kotlin.math.*

/** Mono PCM16 LE conversion; never relabel 8 kHz microphone or 24 kHz service bytes as 16 kHz. */
internal object CommunicationPcm {
    private const val RADIUS=24

    fun capture(pcm:ByteArray,check:()->Unit)=convert(pcm,8_000,16_000,SpeechServices.MAX_RECORD_BYTES/2,check)
    fun playback(pcm:ByteArray,check:()->Unit)=convert(pcm,24_000,16_000,SpeechServices.MAX_SPEECH_BYTES,check)

    private fun convert(pcm:ByteArray,inputRate:Int,outputRate:Int,maxBytes:Int,check:()->Unit):ByteArray {
        if(pcm.isEmpty()||pcm.size%2!=0||pcm.size>maxBytes)throw ChatFailure("SPEECH_PROTOCOL")
        check()
        val frames=pcm.size/2
        val outputFrames=((frames.toLong()*outputRate+inputRate/2)/inputRate).toInt()
        val result=ByteArray(outputFrames*2)
        // Both supported ratios have two fractional phases. Windowed sinc suppresses frequencies
        // above the new Nyquist limit before downsampling; stride-skipping would alias speech.
        val cutoff=0.47*minOf(1.0,outputRate.toDouble()/inputRate)
        val coefficients=Array(2) {phase->
            DoubleArray(RADIUS*2) {index->
                val distance=index-(RADIUS-1)-phase/2.0
                val sinc=if(distance==0.0)2*cutoff else sin(2*PI*cutoff*distance)/(PI*distance)
                sinc*(0.5+0.5*cos(PI*distance/RADIUS))
            }.also {weights->val sum=weights.sum();for(i in weights.indices)weights[i]/=sum}
        }
        fun sample(frame:Int):Int {
            val offset=frame.coerceIn(0,frames-1)*2
            return ((pcm[offset].toInt() and 255) or (pcm[offset+1].toInt() shl 8)).toShort().toInt()
        }
        try {
            for(frame in 0 until outputFrames) {
                if(frame%1024==0)check()
                val position=frame.toLong()*inputRate
                val center=(position/outputRate).toInt()
                val phase=((position%outputRate)*2/outputRate).toInt()
                val weights=coefficients[phase]
                var value=0.0
                for(i in weights.indices)value+=sample(center+i-(RADIUS-1))*weights[i]
                val rounded=value.roundToInt().coerceIn(Short.MIN_VALUE.toInt(),Short.MAX_VALUE.toInt())
                result[frame*2]=rounded.toByte();result[frame*2+1]=(rounded shr 8).toByte()
            }
            check();return result
        } catch(e:Throwable) {result.fill(0);throw e}
    }
}
