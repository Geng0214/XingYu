package com.ai.assistance.operit.api.audio

import com.ai.assistance.operit.data.model.ChatFailure

/** One playback's output snapshot. A reconnect never clears a failure or resumes playback. */
internal class MediaAudioPolicy(outputs:List<CommunicationDevice>,private val stop:(String)->Unit) {
    private val outputs=identities(outputs)
    private var actual:Set<Pair<Int,Int>>?=null
    private var failure:String?=null
    private var closed=false
    private fun identities(devices:List<CommunicationDevice>)=devices.map {it.id to it.type}.toSet()

    @Synchronized fun outputsChanged(devices:List<CommunicationDevice>) {
        if(identities(devices)!=outputs)fail("MUSIC_ROUTE_CHANGED")
    }
    @Synchronized fun removed(devices:List<CommunicationDevice>) {
        // Preserve a transient removal even if a later directory query already sees a reconnect.
        if(identities(devices).any {it in outputs})fail("MUSIC_ROUTE_CHANGED")
    }
    @Synchronized fun observe(devices:List<CommunicationDevice>) {
        if(closed||failure!=null)return
        val current=identities(devices)
        if(actual==null) {if(current.isNotEmpty())actual=current}
        else if(actual!=current)fail("MUSIC_ROUTE_CHANGED")
    }
    @Synchronized fun fail(code:String) {
        if(closed||failure!=null)return
        failure=code
        stop(code)
    }
    @Synchronized fun requireHealthy() {
        failure?.let {throw ChatFailure(it)}
        if(closed)throw ChatFailure("MUSIC_PLAYBACK")
    }
    @Synchronized fun routeReady():Boolean {requireHealthy();return actual!=null}
    @Synchronized fun close(){closed=true}
}
