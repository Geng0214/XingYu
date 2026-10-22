package com.ai.assistance.operit

import android.util.Base64
import android.webkit.*
import androidx.test.platform.app.InstrumentationRegistry
import com.ai.assistance.operit.api.audio.BilibiliWebAudio
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.resume
import kotlin.math.*

/** Offline synthetic media: proves WebView capture/muting, not native routes or real Bilibili sync. */
class BilibiliWebAudioAndroidTest {
    private fun tone():String {
        val rate=48000;val frames=rate*2;val data=frames*2
        val wav=ByteBuffer.allocate(44+data).order(ByteOrder.LITTLE_ENDIAN)
        wav.put("RIFF".toByteArray()).putInt(36+data).put("WAVEfmt ".toByteArray()).putInt(16)
        wav.putShort(1).putShort(1).putInt(rate).putInt(rate*2).putShort(2).putShort(16)
        wav.put("data".toByteArray()).putInt(data)
        repeat(frames){wav.putShort((sin(it*2*PI*440/rate)*16000).roundToInt().toShort())}
        return Base64.encodeToString(wav.array(),Base64.NO_WRAP)
    }
    @Test fun mutedWebViewProducesPcmAndHonorsVolumePauseSeekAndResume()=runBlocking {
        val fixture=tone()
        withContext(Dispatchers.Main.immediate) {
            val view=WebView(InstrumentationRegistry.getInstrumentation().targetContext)
            val samples=Channel<Pair<Long,Int>>(64)
            var resets=0;var packets=0;var externalRequests=0
            val destination="https://player.bilibili.com/player.html?fixture=offline"
            val bridge=BilibiliWebAudio(view,destination,{packet,current->
                val shorts=ByteBuffer.wrap(packet.bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                var peak=0;while(shorts.hasRemaining())peak=max(peak,abs(shorts.get().toInt()))
                if(current()){check(samples.trySend(packet.epoch to peak).isSuccess);packets++;true}else false
            },{resets++})
            suspend fun js(command:String)=suspendCancellableCoroutine<Unit> {continuation->
                view.evaluateJavascript(command){if(continuation.isActive)continuation.resume(Unit)}
            }
            suspend fun peakMatching(check:(Int)->Boolean)=withTimeout(10_000) {
                while(true){val value=samples.receive();if(check(value.second))return@withTimeout value}
                @Suppress("UNREACHABLE_CODE") error("unreachable")
            }
            var job:Job?=null
            try {
                view.settings.javaScriptEnabled=true
                view.settings.mediaPlaybackRequiresUserGesture=false // Fixture starts synthetic media without physical taps.
                view.settings.cacheMode=WebSettings.LOAD_NO_CACHE
                view.webViewClient=object:WebViewClient(){
                    override fun shouldInterceptRequest(v:WebView,r:WebResourceRequest):WebResourceResponse {
                        externalRequests++
                        return WebResourceResponse("text/plain","UTF-8",403,"Offline fixture",emptyMap(),java.io.ByteArrayInputStream(ByteArray(0)))
                    }
                }
                bridge.install();bridge.requireMuted()
                job=launch {bridge.run()}
                view.loadDataWithBaseURL(destination,"<html><video id='v' autoplay loop src='data:audio/wav;base64,$fixture'></video></html>","text/html","UTF-8",null)
                assertTrue(peakMatching {it>10000}.second>10000)
                js("document.getElementById('v').volume=0")
                assertEquals(0,peakMatching {it==0}.second)
                js("document.getElementById('v').volume=0.5")
                assertTrue(peakMatching {it in 6000..9000}.second in 6000..9000)
                js("document.getElementById('v').muted=true")
                assertEquals(0,peakMatching {it==0}.second)
                js("document.getElementById('v').muted=false")
                peakMatching {it>6000}
                repeat(20) {
                    val before=resets;js("document.getElementById('v').pause()")
                    withTimeout(5000){while(resets==before)delay(10)}
                    while(samples.tryReceive().isSuccess){}
                    val pausedPackets=packets;delay(100);assertEquals(pausedPackets,packets)
                    js("document.getElementById('v').currentTime=0.5;document.getElementById('v').play()")
                    peakMatching {it>6000};bridge.requireMuted()
                }
                assertEquals(0,externalRequests)
            }finally {
                try {job?.cancelAndJoin();bridge.close()}finally {view.stopLoading();view.destroy();samples.cancel()}
            }
        }
    }
}
