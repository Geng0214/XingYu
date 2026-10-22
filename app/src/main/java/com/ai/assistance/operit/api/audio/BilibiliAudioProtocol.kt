package com.ai.assistance.operit.api.audio

import com.ai.assistance.operit.data.model.ChatFailure
import com.ai.assistance.operit.data.model.StrictJson
import org.json.JSONObject
import org.json.JSONTokener
import java.util.Base64

internal data class BilibiliPcm(val epoch:Long,val sequence:Long,val rate:Int,val frames:Int,val bytes:ByteArray)
internal enum class BilibiliAudioEvent { STATE, PCM, STALE }

/** Only untrusted stereo PCM and lifecycle counters cross this bridge; no app objects or data. */
internal class BilibiliAudioInbox {
    var epoch=0L;private set
    var playing=false;private set
    private var rate=0
    private var sequence=0L
    private var closed=false
    private val queued=ArrayDeque<BilibiliPcm>()
    fun current(packet:BilibiliPcm)=!closed&&playing&&packet.epoch==epoch
    fun take():BilibiliPcm?=queued.removeFirstOrNull()
    fun clear(){while(queued.isNotEmpty())queued.removeFirst().bytes.fill(0)}
    fun close(){closed=true;playing=false;clear()}
    fun accept(raw:String):BilibiliAudioEvent {
        if(closed)throw ChatFailure("BILI_AUDIO")
        try {
            require(raw.length in 1..8192)
            StrictJson(raw).validate()
            val tokener=JSONTokener(raw)
            val value=tokener.nextValue() as? JSONObject?:error("object")
            require(tokener.nextClean()=='\u0000')
            fun number(key:String):Long {
                val n=value.get(key)
                require(n is Int||n is Long)
                return (n as Number).toLong()
            }
            fun fields(vararg keys:String){require(value.keys().asSequence().toSet()==keys.toSet())}
            val messageEpoch=number("epoch")
            require(messageEpoch in 1..1_000_000_000)
            return when(value.getString("kind")) {
                "state"->{
                    fields("kind","epoch","playing","rate")
                    require(messageEpoch==epoch+1)
                    val nextPlaying=value.get("playing");require(nextPlaying is Boolean)
                    val nextRate=number("rate");require(nextRate in 8000..192000)
                    clear();epoch=messageEpoch;playing=nextPlaying;rate=nextRate.toInt();sequence=0
                    BilibiliAudioEvent.STATE
                }
                "pcm"->{
                    fields("kind","epoch","sequence","frames","pcm")
                    if(messageEpoch<epoch)return BilibiliAudioEvent.STALE
                    require(messageEpoch==epoch&&playing)
                    val next=number("sequence");require(next==sequence+1&&next<=1_000_000_000)
                    val frames=number("frames");require(frames==1024L)
                    val encoded=value.get("pcm");require(encoded is String&&encoded.length==5464)
                    require(queued.size<8)
                    val bytes=Base64.getDecoder().decode(encoded)
                    try {
                        require(bytes.size==frames.toInt()*4)
                        queued.addLast(BilibiliPcm(epoch,next,rate,frames.toInt(),bytes));sequence=next
                    }catch(e:Exception){bytes.fill(0);throw e}
                    BilibiliAudioEvent.PCM
                }
                else->throw ChatFailure("BILI_AUDIO")
            }
        }catch(e:Exception){throw ChatFailure("BILI_AUDIO")}
    }
}
