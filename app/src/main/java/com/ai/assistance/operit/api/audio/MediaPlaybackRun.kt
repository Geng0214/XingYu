package com.ai.assistance.operit.api.audio

import com.ai.assistance.operit.data.model.*
import kotlinx.coroutines.*
import java.util.UUID

/** One owned run, including its final write. Callers retain its Job until run() returns. */
class MediaPlaybackRun<T:AutoCloseable>(
    private val begin:suspend (MusicSession,String)->Pair<MusicSession,MusicTrack>,
    private val load:suspend (MusicTrack)->T,
    private val play:suspend (T,Long,suspend (Long)->Unit)->Boolean,
    private val save:suspend (MusicSession,String,Long,Boolean,Boolean)->Boolean,
    private val requireAccess:()->Unit,
    private val saveFailed:()->Unit
) {
    suspend fun run(item:MusicSession) {
        val token=UUID.randomUUID().toString()
        var started=false;var completed=false;var position=item.positionMs
        try {
            currentCoroutineContext().ensureActive();requireAccess()
            // Record ownership before cancellation is observed after Room's transaction commits.
            val (session,track)=withContext(NonCancellable){begin(item,token).also {started=true}}
            currentCoroutineContext().ensureActive();requireAccess()
            val resource=load(track)
            try {
                currentCoroutineContext().ensureActive();requireAccess()
                var lastSaved=-1L
                completed=play(resource,session.positionMs){current->
                    currentCoroutineContext().ensureActive();requireAccess()
                    position=current.coerceIn(0,track.durationMs)
                    if(lastSaved<0||kotlin.math.abs(position-lastSaved)>=1000) {
                        if(!withContext(Dispatchers.IO){save(session,token,position,false,false)})throw ChatFailure("ACCESS")
                        lastSaved=position
                    }
                }
                if(completed)position=track.durationMs
            }finally{resource.close()}
        }finally {
            if(started)withContext(NonCancellable+Dispatchers.IO) {
                try{save(item,token,position,true,completed)}catch(_:Exception){saveFailed()}
            }
        }
    }
}
