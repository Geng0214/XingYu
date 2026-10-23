package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.security.MessageDigest

data class PromptVoice(
    val sampleRate:Int,
    val durationMs:Long,
    val waveform:ByteArray,
    val sha256:String,
    val data:ByteArray
) {
    fun validate() {
        require(sampleRate==MessageVoices.SAMPLE_RATE)
        require(durationMs in MessageVoices.MIN_DURATION_MS..MessageVoices.MAX_DURATION_MS)
        require(waveform.size==MessageVoices.WAVEFORM_POINTS)
        require(data.isNotEmpty()&&data.size<=MessageVoices.MAX_STORED&&data.size%2==0)
        require(sha256==MessageVoices.sha256(data))
    }
    fun label()="[用户发送了语音 ${MessageVoices.secondsText(durationMs)}]"
}

@Entity(
    tableName="message_voices",
    foreignKeys=[ForeignKey(entity=MessageEntity::class,parentColumns=["id"],childColumns=["messageId"],onDelete=ForeignKey.CASCADE)],
    indices=[Index("chatId"),Index(value=["messageId","chatId","requestId"],unique=true)]
)
data class MessageVoice(
    @PrimaryKey val messageId:String,
    val chatId:String,
    val requestId:String,
    val sampleRate:Int,
    val durationMs:Long,
    val waveform:ByteArray,
    val sha256:String,
    val data:ByteArray
) {
    fun promptVoice()=PromptVoice(sampleRate,durationMs,waveform.copyOf(),sha256,data.copyOf()).also(PromptVoice::validate)
}

object MessageVoices {
    const val SAMPLE_RATE=16000
    const val MIN_DURATION_MS=250L
    const val MAX_DURATION_MS=30_000L
    const val WAVEFORM_POINTS=32
    const val MAX_STORED=1024*1024
    const val HISTORY_VOICES=2

    fun sha256(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}

    fun durationMs(pcm:ByteArray)=pcm.size.toLong()*1000/(SAMPLE_RATE*2)

    fun secondsText(durationMs:Long)=if(durationMs%1000==0L) "${durationMs/1000} 秒" else "%.1f 秒".format(durationMs/1000.0)

    /** PCM16 单声道降采样成固定点数的 RMS 包络，每点取值 0..255。 */
    fun waveform(pcm:ByteArray,points:Int=WAVEFORM_POINTS):ByteArray {
        require(points>0)
        val out=ByteArray(points)
        val samples=pcm.size/2
        if(samples==0)return out
        for(i in 0 until points) {
            val from=(i.toLong()*samples/points).toInt()
            val to=((i+1).toLong()*samples/points).toInt().coerceAtLeast(from+1).coerceAtMost(samples)
            var sum=0.0
            var count=0
            for(j in from until to) {
                val lo=pcm[j*2].toInt() and 0xFF
                val hi=pcm[j*2+1].toInt()
                val value=(hi shl 8) or lo
                sum+=value.toDouble()*value
                count++
            }
            val rms=if(count==0)0.0 else kotlin.math.sqrt(sum/count)
            out[i]=(rms/32768.0*255).toInt().coerceIn(0,255).toByte()
        }
        return out
    }
}
