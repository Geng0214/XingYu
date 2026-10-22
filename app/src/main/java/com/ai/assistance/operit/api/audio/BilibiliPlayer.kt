package com.ai.assistance.operit.api.audio

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.media.*
import android.net.Uri
import android.net.http.SslError
import android.webkit.*
import com.ai.assistance.operit.data.model.*
import kotlinx.coroutines.*
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayInputStream
import kotlin.coroutines.resume

/** The official guest player receives only the public video identity and starting position. */
@SuppressLint("SetJavaScriptEnabled")
class BilibiliPlayer(context:Context,private val video:BilibiliVideo,positionMs:Long) {
    val view=WebView(context)
    private val destination=video.playerUrl(positionMs)
    private var failure:String?=null
    private var closed=false
    private var documentStarted=false
    private val audioSession=AndroidMediaAudioSession(context,
        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build(),::close)
    private val pcmOutput=BilibiliPcmOutput(audioSession)
    private val webAudio=BilibiliWebAudio(view,destination,pcmOutput::play,pcmOutput::reset)
    val fullscreen=kotlinx.coroutines.flow.MutableStateFlow<android.view.View?>(null)
    private var fullscreenCallback:WebChromeClient.CustomViewCallback?=null
    init {
        CookieManager.getInstance().setAcceptCookie(false)
        CookieManager.getInstance().setAcceptThirdPartyCookies(view,false)
        view.settings.apply {
            javaScriptEnabled=true;domStorageEnabled=false;databaseEnabled=false;cacheMode=WebSettings.LOAD_NO_CACHE
            allowFileAccess=false;allowContentAccess=false;allowFileAccessFromFileURLs=false;allowUniversalAccessFromFileURLs=false
            javaScriptCanOpenWindowsAutomatically=false;setSupportMultipleWindows(false);setGeolocationEnabled(false)
            mediaPlaybackRequiresUserGesture=true;mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
            saveFormData=false
        }
        view.importantForAutofill=android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        view.webChromeClient=object:WebChromeClient() {
            override fun onShowCustomView(v:android.view.View,callback:CustomViewCallback){exitFullscreen();fullscreenCallback=callback;fullscreen.value=v}
            override fun onHideCustomView(){exitFullscreen()}
            override fun onPermissionRequest(request:PermissionRequest){request.deny()}
            override fun onGeolocationPermissionsShowPrompt(origin:String,callback:GeolocationPermissions.Callback){callback.invoke(origin,false,false)}
            override fun onConsoleMessage(message:ConsoleMessage)=true
            override fun onShowFileChooser(webView:WebView,callback:ValueCallback<Array<Uri>>,params:FileChooserParams):Boolean {callback.onReceiveValue(null);return true}
        }
        view.setDownloadListener {_,_,_,_,_->failure="BILI_NAVIGATION"}
        view.webViewClient=object:WebViewClient() {
            override fun shouldOverrideUrlLoading(v:WebView,r:WebResourceRequest):Boolean {
                val allowed=r.isForMainFrame&&r.url.toString()==destination
                if(!allowed&&r.isForMainFrame)failure="BILI_NAVIGATION"
                return !allowed
            }
            override fun onPageStarted(v:WebView,url:String,icon:Bitmap?) {
                if(url!=destination||documentStarted){failure="BILI_NAVIGATION";audioSession.fail("BILI_NAVIGATION")}
                else documentStarted=true
            }
            override fun shouldInterceptRequest(v:WebView,r:WebResourceRequest):WebResourceResponse? {
                if(BilibiliWebPolicy.resource(r.url.toString()))return null
                return WebResourceResponse("text/plain","UTF-8",403,"Blocked",emptyMap(),ByteArrayInputStream(ByteArray(0)))
            }
            override fun onReceivedError(v:WebView,r:WebResourceRequest,e:WebResourceError){if(r.isForMainFrame)failure="BILI_NETWORK"}
            override fun onReceivedHttpError(v:WebView,r:WebResourceRequest,response:WebResourceResponse){if(r.isForMainFrame)failure="BILI_UNAVAILABLE"}
            override fun onReceivedSslError(v:WebView,handler:SslErrorHandler,error:SslError){handler.cancel();failure="BILI_NETWORK"}
            override fun onRenderProcessGone(v:WebView,detail:RenderProcessGoneDetail):Boolean {failure="BILI_UNAVAILABLE";return true}
        }
    }
    suspend fun run(onProgress:suspend (Long,Long)->Unit)=withContext(Dispatchers.Main.immediate) {
        try {
            audioSession.install();audioSession.requestFocus()
            audioSession.whileHealthy {
            coroutineScope {
            webAudio.install()
            launch {webAudio.run()}
            view.loadUrl(destination)
            val started=android.os.SystemClock.elapsedRealtime();var lastMedia:Long?=null
            while(true) {
                currentCoroutineContext().ensureActive()
                audioSession.requireHealthy()
                webAudio.requireMuted()
                failure?.let {throw ChatFailure(it)}
                if(closed)throw CancellationException("Player closed")
                val sample=try{withTimeout(10_000){sample()}}catch(_:TimeoutCancellationException){throw ChatFailure("BILI_UNAVAILABLE")}
                audioSession.requireHealthy()
                val now=android.os.SystemClock.elapsedRealtime()
                if(sample!=null) {lastMedia=now;onProgress(sample.first,sample.second)}
                if(lastMedia==null&&now-started>45_000)throw ChatFailure("BILI_UNAVAILABLE")
                if(lastMedia!=null&&now-lastMedia>15_000)throw ChatFailure("BILI_UNAVAILABLE")
                delay(750)
            }
            }
            }
        }finally {close()}
    }
    private suspend fun sample():Pair<Long,Long>?=suspendCancellableCoroutine {continuation->
        if(!documentStarted){continuation.resume(null);return@suspendCancellableCoroutine}
        view.evaluateJavascript(BilibiliWebPolicy.SAMPLE) {raw->
            if(!continuation.isActive)return@evaluateJavascript
            try {
                if(view.url!=destination)throw ChatFailure("BILI_NAVIGATION")
                val json=JSONTokener(raw).nextValue()
                if(json==JSONObject.NULL){continuation.resume(null);return@evaluateJavascript}
                val value=JSONObject(json as String)
                if(!value.getBoolean("ready")){continuation.resume(null);return@evaluateJavascript}
                webAudio.requireFlow(value.getBoolean("moving"))
                continuation.resume(BilibiliWatching.position(value.getDouble("time"),value.getDouble("duration")))
            }catch(e:Exception){failure=if(e is ChatFailure)e.code else "BILI_PROGRESS";continuation.resume(null)}
        }
    }
    fun close() {
        if(closed)return
        closed=true
        try {
            webAudio.close()
        }finally {try {
            exitFullscreen()
            view.stopLoading();view.onPause();view.clearHistory();view.clearFormData();view.clearCache(true)
            view.removeAllViews();view.destroy()
        }finally {
            audioSession.close()
        }}
    }
    fun exitFullscreen(){val callback=fullscreenCallback;fullscreenCallback=null;fullscreen.value=null;callback?.onCustomViewHidden()}
}

/** No application objects are exposed through addJavascriptInterface. */
object BilibiliWebPolicy {
    const val SAMPLE="""(()=>{const list=document.querySelectorAll('video');if(list.length!==1)return JSON.stringify({ready:false});const v=list[0];if(v.readyState<1)return JSON.stringify({ready:false});return JSON.stringify({ready:true,time:v.currentTime,duration:v.duration,moving:!v.paused&&!v.ended&&!v.seeking&&v.readyState>=3});})()"""
    fun resource(url:String):Boolean=try {
        val u=java.net.URI(url);val host=u.host
        u.scheme=="https"&&u.rawUserInfo==null&&(u.port==-1||u.port==443)&&host!=null&&
            (host in setOf("player.bilibili.com","api.bilibili.com","data.bilibili.com")||host.endsWith(".hdslb.com")||host.endsWith(".bilivideo.com"))
    }catch(_:Exception){false}
}
