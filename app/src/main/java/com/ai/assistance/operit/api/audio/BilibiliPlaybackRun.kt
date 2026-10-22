package com.ai.assistance.operit.api.audio

import com.ai.assistance.operit.data.model.*
import kotlinx.coroutines.*
import java.util.UUID

class BilibiliPlaybackRun(
    private val begin:suspend (BilibiliWatch,String)->BilibiliWatch,
    private val play:suspend (BilibiliWatch,suspend (Long,Long)->Unit)->Unit,
    private val save:suspend (BilibiliWatch,String,Long,Long?,Boolean)->Boolean,
    private val requireAccess:()->Unit,
    private val saveFailed:()->Unit
) {
    suspend fun run(item:BilibiliWatch) {
        val token=UUID.randomUUID().toString();var started=false
        var position=item.positionMs;var duration=item.durationMs
        try {
            currentCoroutineContext().ensureActive();requireAccess()
            val active=withContext(NonCancellable){begin(item,token).also {started=true}}
            currentCoroutineContext().ensureActive();requireAccess()
            var lastPosition=-1L;var lastDuration:Long?=null
            play(active){p,d->
                currentCoroutineContext().ensureActive();requireAccess()
                BilibiliWatching.validate(active.copy(positionMs=p,durationMs=d))
                position=p;duration=d
                if(lastPosition<0||kotlin.math.abs(p-lastPosition)>=1000||d!=lastDuration) {
                    if(!withContext(Dispatchers.IO){save(active,token,p,d,false)})throw ChatFailure("ACCESS")
                    lastPosition=p;lastDuration=d
                }
            }
        }finally {
            if(started)withContext(NonCancellable+Dispatchers.IO) {
                try{save(item,token,position,duration,true)}catch(_:Exception){saveFailed()}
            }
        }
    }
}
