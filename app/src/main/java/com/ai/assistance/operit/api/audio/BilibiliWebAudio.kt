package com.ai.assistance.operit.api.audio

import android.webkit.WebView
import androidx.webkit.*
import com.ai.assistance.operit.data.model.ChatFailure
import org.json.JSONObject

/** Main thread only. This object exposes no file, model, credential or application operations. */
internal class BilibiliWebAudio(
    private val view:WebView,
    private val destination:String,
    play:suspend (BilibiliPcm,()->Boolean)->Boolean,
    reset:()->Unit,
) {
    private var reply:JavaScriptReplyProxy?=null
    private var script:ScriptHandler?=null
    private var listening=false
    private var closed=false
    private val stream=BilibiliAudioStream(destination,play,reset) {epoch,sequence->
        requireMuted()
        if(view.url!=destination)throw ChatFailure("BILI_NAVIGATION")
        if(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER))
            (reply?:throw ChatFailure("BILI_AUDIO")).postMessage("{\"epoch\":$epoch,\"sequence\":$sequence}")
        else throw ChatFailure("BILI_AUDIO_UNAVAILABLE")
    }
    fun install() {
        if(closed||listening)throw ChatFailure("BILI_AUDIO")
        if(!WebViewFeature.isFeatureSupported(WebViewFeature.MUTE_AUDIO)||
            !WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)||
            !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))throw ChatFailure("BILI_AUDIO_UNAVAILABLE")
        // Mute the entire WebView before loading any document; never re-enable browser output.
        if(WebViewFeature.isFeatureSupported(WebViewFeature.MUTE_AUDIO))WebViewCompat.setAudioMuted(view,true)
        else throw ChatFailure("BILI_AUDIO_UNAVAILABLE")
        requireMuted()
        if(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER))
            WebViewCompat.addWebMessageListener(view,"CompanionAudio",setOf(ORIGIN)) {_,message,origin,main,proxy->
            if(!closed) {
                val trusted=origin.toString()==ORIGIN&&main&&view.url==destination
                if(trusted)reply=proxy
                val raw=if(message.type==WebMessageCompat.TYPE_STRING)message.data.orEmpty() else ""
                stream.receive(raw,origin.toString(),main,view.url)
            }
        }else throw ChatFailure("BILI_AUDIO_UNAVAILABLE")
        listening=true
        if(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
            script=WebViewCompat.addDocumentStartJavaScript(view,source(view),setOf(ORIGIN))
        else throw ChatFailure("BILI_AUDIO_UNAVAILABLE")
    }
    fun requireMuted() {
        if(!WebViewFeature.isFeatureSupported(WebViewFeature.MUTE_AUDIO)||!WebViewCompat.isAudioMuted(view))throw ChatFailure("BILI_AUDIO_UNAVAILABLE")
    }
    suspend fun run()=stream.run()
    fun requireFlow(moving:Boolean)=stream.requireFlow(moving)
    fun close() {
        if(closed)return
        closed=true;reply=null
        try {stream.close()}finally {
            try {
                val old=script;script=null
                if(old!=null) {
                    if(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))old.remove()
                    else throw ChatFailure("AUDIO_ROUTE_CLEANUP")
                }
            }finally {
                if(listening){
                    listening=false
                    if(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER))WebViewCompat.removeWebMessageListener(view,"CompanionAudio")
                    else throw ChatFailure("AUDIO_ROUTE_CLEANUP")
                }
            }
        }
    }
    companion object {
        const val ORIGIN="https://player.bilibili.com"
        fun source(view:WebView):String {
            val assets=view.context.assets
            val worklet=assets.open("bilibili-audio-worklet.js").bufferedReader(Charsets.UTF_8).use {it.readText()}
            val main=assets.open("bilibili-audio.js").bufferedReader(Charsets.UTF_8).use {it.readText()}
            return main.replace("/*WORKLET_SOURCE*/",JSONObject.quote(worklet))
        }
    }
}
