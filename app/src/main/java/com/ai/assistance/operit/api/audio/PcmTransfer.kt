package com.ai.assistance.operit.api.audio

import com.ai.assistance.operit.data.model.ChatFailure
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive

/** Callers supply NON_BLOCKING platform operations; device stalls must not hold route cleanup. */
internal object PcmTransfer {
    suspend fun write(bytes:ByteArray,write:(offset:Int,count:Int)->Int,check:()->Unit,
        clock:()->Long={System.nanoTime()/1_000_000},stallTimeoutMs:Long=5_000) {
        require(bytes.isNotEmpty()&&bytes.size%2==0&&stallTimeoutMs>0)
        var offset=0
        var progress=clock()
        while(offset<bytes.size) {
            currentCoroutineContext().ensureActive();check()
            val requested=minOf(8192,bytes.size-offset)
            val count=write(offset,requested)
            check()
            if(count<0||count>requested||count%2!=0)throw ChatFailure("AUDIO_PLAYBACK")
            if(count>0){offset+=count;progress=clock()}
            else {
                if(clock()-progress>=stallTimeoutMs)throw ChatFailure("AUDIO_PLAYBACK")
                delay(10)
            }
        }
        currentCoroutineContext().ensureActive();check()
    }

    suspend fun read(requested:Int,read:(count:Int)->Int,check:()->Unit,stopped:()->Boolean,
        clock:()->Long={System.nanoTime()/1_000_000},stallTimeoutMs:Long=5_000):Int {
        require(requested>0&&requested%2==0&&stallTimeoutMs>0)
        val start=clock()
        while(true) {
            currentCoroutineContext().ensureActive();check()
            if(stopped())return 0
            val count=read(requested)
            check()
            if(count<0||count>requested||count%2!=0)throw ChatFailure("MICROPHONE_UNAVAILABLE")
            if(count>0)return count
            if(clock()-start>=stallTimeoutMs)throw ChatFailure("MICROPHONE_UNAVAILABLE")
            delay(10)
        }
    }
}
