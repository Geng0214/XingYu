package com.ai.assistance.operit.api.audio

import com.ai.assistance.operit.data.model.ChatFailure

/** An explicit input/output pair, valid only inside its owning communication lease. */
class CommunicationAudioBinding(
    val input:CommunicationDevice,
    val output:CommunicationDevice,
    val requireLease:()->Unit,
)

class CommunicationPlaybackBinding(val output:CommunicationDevice,val requireLease:()->Unit)

/** Preferred-device acceptance is not evidence that any audio reached that device. */
internal class ObservedAudioRoute(private val expected:CommunicationDevice) {
    private var confirmed=false
    private var error=""
    private var closed=false

    @Synchronized fun observe(actual:List<CommunicationDevice>) {
        if(closed||error.isNotEmpty())return
        val matches=actual.size==1&&expected.matches(actual.single())
        if(matches)confirmed=true
        else if(confirmed)error="AUDIO_ROUTE_CHANGED"
    }
    @Synchronized fun lost(code:String) {if(!closed&&error.isEmpty())error=code}
    @Synchronized fun ready():Boolean {
        if(error.isNotEmpty())throw ChatFailure(error)
        if(closed)throw ChatFailure("AUDIO_ROUTE_UNAVAILABLE")
        return confirmed
    }
    fun requireReady(){if(!ready())throw ChatFailure("AUDIO_ROUTE_UNAVAILABLE")}
    @Synchronized fun close(){closed=true}
}
