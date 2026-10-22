package com.ai.assistance.operit.api.audio

import com.ai.assistance.operit.data.model.ChatFailure

internal enum class ScoLinkState { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

/** SCO link readiness is necessary but never sufficient evidence of AudioTrack routing. */
internal class LegacyScoHandshake {
    private var requested=false
    private var connected=false
    private var connecting=false
    private var error=""
    private var closed=false

    fun request() {
        if(closed||requested)throw ChatFailure("AUDIO_ROUTE_UNAVAILABLE")
        requested=true
    }
    fun changed(state:ScoLinkState) {
        if(closed||error.isNotEmpty())return
        if(!requested) {connected=state==ScoLinkState.CONNECTED;return}
        when(state) {
            ScoLinkState.CONNECTED->{connected=true;connecting=false}
            ScoLinkState.CONNECTING->{
                if(connected)error="AUDIO_ROUTE_CHANGED" else connecting=true
            }
            ScoLinkState.DISCONNECTED->{
                if(connected)error="AUDIO_ROUTE_CHANGED" else if(connecting)error="AUDIO_ROUTE_UNAVAILABLE"
                connected=false
            }
            ScoLinkState.ERROR->error=if(connected)"AUDIO_ROUTE_CHANGED" else "AUDIO_ROUTE_UNAVAILABLE"
        }
    }
    fun ready():Boolean {
        if(error.isNotEmpty())throw ChatFailure(error)
        if(closed)throw ChatFailure("AUDIO_ROUTE_UNAVAILABLE")
        return requested&&connected
    }
    fun close(){closed=true}
}
