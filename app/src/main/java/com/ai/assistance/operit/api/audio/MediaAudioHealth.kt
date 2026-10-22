package com.ai.assistance.operit.api.audio

import com.ai.assistance.operit.data.model.ChatFailure
import kotlinx.coroutines.*
import kotlinx.coroutines.selects.select

/** Stops pending prepare/seek/WebView sampling as well as an actively advancing player. */
internal suspend fun <T> withMediaAudioHealth(failure:Deferred<String>,requireHealthy:()->Unit,block:suspend ()->T):T=coroutineScope {
    requireHealthy()
    // Report a platform error only after checking whether route loss caused that platform error.
    val work=async(start=CoroutineStart.LAZY) {
        try {Result.success(block())}
        catch(e:CancellationException){throw e}
        catch(e:Exception){Result.failure<T>(e)}
    }
    try {
        work.start()
        select<T> {
            failure.onAwait {requireHealthy();throw ChatFailure(it)}
            work.onAwait {value->requireHealthy();value.getOrThrow()}
        }
    }finally {withContext(NonCancellable){work.cancelAndJoin()}}
}
