package com.ai.assistance.operit.api.audio

import com.ai.assistance.operit.data.model.ChatFailure
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.atomic.AtomicReference

/** Device identities are ephemeral platform IDs, never saved in a role or backup. */
data class CommunicationDevice(val id:Int,val type:Int,val name:String) {
    fun matches(other:CommunicationDevice?)=other!=null&&id==other.id&&type==other.type
}
enum class CommunicationPhase { CONNECTING, READY, LOST, CLOSED }
data class CommunicationStatus(val device:CommunicationDevice,val phase:CommunicationPhase,val error:String="")

interface CommunicationConnection {
    suspend fun request():Boolean
    suspend fun current():CommunicationDevice?
    suspend fun close()
}
interface CommunicationRoutePort {
    suspend fun devices():List<CommunicationDevice>
    suspend fun inputs():List<CommunicationDevice>
    /** Must unwind partial setup itself if this function throws before returning a connection. */
    suspend fun open(device:CommunicationDevice,input:CommunicationDevice?,changed:(CommunicationDevice?)->Unit,lost:(String)->Unit):CommunicationConnection
}

/** Survives cancellation and cleanup; only explicit device reselection creates a new instance. */
class CommunicationRouteFailure {
    private val failure=AtomicReference<String?>(null)
    val code:String? get()=failure.get()
    fun record(code:String){failure.compareAndSet(null,code)}
}

internal class CommunicationSession(val device:CommunicationDevice,private val failure:CommunicationRouteFailure) {
    private val mutable=MutableStateFlow(CommunicationStatus(device,CommunicationPhase.CONNECTING))
    val state=mutable.asStateFlow()
    @Synchronized fun changed(actual:CommunicationDevice?) {
        when(mutable.value.phase) {
            CommunicationPhase.CONNECTING->if(device.matches(actual))mutable.value=CommunicationStatus(device,CommunicationPhase.READY)
            CommunicationPhase.READY->if(!device.matches(actual))lost("AUDIO_ROUTE_CHANGED")
            else->Unit
        }
    }
    @Synchronized fun lost(code:String) {
        if(mutable.value.phase in setOf(CommunicationPhase.CONNECTING,CommunicationPhase.READY)) {
            failure.record(code)
            mutable.value=CommunicationStatus(device,CommunicationPhase.LOST,code)
        }
    }
    @Synchronized fun close(){mutable.value=CommunicationStatus(device,CommunicationPhase.CLOSED)}
    fun requireReady() {
        val status=state.value
        if(status.phase!=CommunicationPhase.READY)throw ChatFailure(if(status.error.isEmpty())"AUDIO_ROUTE_UNAVAILABLE"else status.error)
    }
}

/** Owns routing until all recording, network and playback work in the block has stopped. */
class CommunicationRoutes(private val port:CommunicationRoutePort,private val timeoutMs:Long=30_000) {
    init {require(timeoutMs in 1..30_000)}
    private val mutex=Mutex()
    private val mutable=MutableStateFlow<CommunicationStatus?>(null)
    val status=mutable.asStateFlow()
    suspend fun devices()=port.devices()
    suspend fun inputs()=port.inputs()

    suspend fun <T> withRoute(device:CommunicationDevice,input:CommunicationDevice,failure:CommunicationRouteFailure=CommunicationRouteFailure(),block:suspend (requireRoute:()->Unit)->T):T =
        runRoute(device,input,failure,block)

    /** Playback owns an output without selecting, opening or requiring any microphone. */
    suspend fun <T> withOutputRoute(device:CommunicationDevice,block:suspend (requireRoute:()->Unit)->T):T =
        runRoute(device,null,CommunicationRouteFailure(),block)

    private suspend fun <T> runRoute(device:CommunicationDevice,input:CommunicationDevice?,failure:CommunicationRouteFailure,block:suspend (requireRoute:()->Unit)->T):T {
        failure.code?.let {throw ChatFailure(it)}
        if(!mutex.tryLock())throw ChatFailure("AUDIO_ROUTE_BUSY")
        val session=CommunicationSession(device,failure)
        var connection:CommunicationConnection?=null
        mutable.value=session.state.value
        try {
            currentCoroutineContext().ensureActive()
            if(port.devices().none {device.matches(it)})throw ChatFailure("AUDIO_ROUTE_UNAVAILABLE")
            if(input!=null&&port.inputs().none {input.matches(it)})throw ChatFailure("AUDIO_ROUTE_UNAVAILABLE")
            // Acquire into the owning variable before a dispatcher-return cancellation can discard it.
            withContext(NonCancellable){connection=port.open(device,input,session::changed,session::lost)}
            currentCoroutineContext().ensureActive()
            val opened=requireNotNull(connection)
            if(!opened.request())throw ChatFailure("AUDIO_ROUTE_UNAVAILABLE")
            session.changed(opened.current())
            withTimeoutOrNull(timeoutMs){session.state.first {it.phase!=CommunicationPhase.CONNECTING}}
                ?:throw ChatFailure("AUDIO_ROUTE_TIMEOUT")
            session.requireReady()
            // A loss is sticky: a brief disconnect/reconnect cannot be hidden by conflated flows.
            return coroutineScope {
                val publication=launch(start=CoroutineStart.UNDISPATCHED){session.state.collect {mutable.value=it}}
                val work=async(start=CoroutineStart.LAZY){session.requireReady();block(session::requireReady)}
                val loss=async(start=CoroutineStart.UNDISPATCHED){session.state.first {it.phase==CommunicationPhase.LOST}}
                try {
                    work.start()
                    select<T> {
                        loss.onAwait {throw ChatFailure(it.error)}
                        work.onAwait {value->session.requireReady();value}
                    }
                } finally {
                    withContext(NonCancellable) {
                        // Never clear the communication route while the old mic/track is still active.
                        work.cancelAndJoin();loss.cancelAndJoin();publication.cancelAndJoin()
                    }
                }
            }
        } catch(e:ChatFailure) {
            failure.record(e.code)
            throw e
        } finally {
            session.close()
            try {withContext(NonCancellable){connection?.close()}}
            catch(e:ChatFailure){failure.record(e.code);throw e}
            finally {mutable.value=null;mutex.unlock()}
        }
    }
}
