package com.ai.assistance.operit.api.audio

import android.content.*
import android.media.*
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.ai.assistance.operit.data.model.ChatFailure
import kotlinx.coroutines.*

/** Main-thread ownership of one player's focus and system output notifications. */
internal class AndroidMediaAudioSession(
    private val context:Context,attributes:AudioAttributes,private val stopOwnedPlayer:()->Unit,
):AutoCloseable {
    private val audio=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler=Handler(Looper.getMainLooper())
    private val failure=CompletableDeferred<String>()
    private var cleanupFailure:Exception?=null
    private var closed=false
    private var devicesRegistered=false
    private var receiverRegistered=false
    private var focusRequested=false
    private fun devices()=audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map {CommunicationDevice(it.id,it.type,"")}
    // Query the system only inside install's cleanup boundary, never in a player's constructor.
    private val policyState=lazy {MediaAudioPolicy(devices()) {code->
        failure.complete(code)
        try {stopOwnedPlayer()}catch(e:Exception){cleanupFailure=e}
    }}
    private val policy by policyState
    private val focus=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener({change->if(change!=AudioManager.AUDIOFOCUS_GAIN)policy.fail("MUSIC_FOCUS")},handler).build()
    private val receiver=object:BroadcastReceiver() {
        override fun onReceive(context:Context,intent:Intent){if(intent.action==AudioManager.ACTION_AUDIO_BECOMING_NOISY)policy.fail("MUSIC_ROUTE_CHANGED")}
    }
    private val callback=object:AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added:Array<out AudioDeviceInfo>){changed()}
        override fun onAudioDevicesRemoved(removed:Array<out AudioDeviceInfo>){
            policy.removed(removed.filter {it.isSink}.map {CommunicationDevice(it.id,it.type,"")});changed()
        }
        private fun changed(){if(!closed)try {policy.outputsChanged(devices())}catch(_:Exception){policy.fail("MUSIC_ROUTE_CHANGED")}}
    }
    fun install() {
        check(!closed)
        try {
            requireHealthy()
            devicesRegistered=true;audio.registerAudioDeviceCallback(callback,handler)
            receiverRegistered=true
            ContextCompat.registerReceiver(context,receiver,IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),ContextCompat.RECEIVER_NOT_EXPORTED)
            policy.outputsChanged(devices());requireHealthy()
        }catch(e:Exception){try {close()}catch(cleanup:Exception){e.addSuppressed(cleanup)};throw e}
    }
    fun requestFocus() {
        requireHealthy();focusRequested=true
        if(audio.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED)policy.fail("MUSIC_FOCUS")
        requireHealthy()
    }
    fun observe(devices:List<CommunicationDevice>){policy.observe(devices);requireHealthy()}
    fun fail(code:String){policy.fail(code)}
    fun routeReady()=policy.routeReady()
    fun requireHealthy() {
        cleanupFailure?.let {throw ChatFailure("AUDIO_ROUTE_CLEANUP").also {error->error.addSuppressed(it)}}
        policy.requireHealthy()
    }
    suspend fun <T> whileHealthy(block:suspend ()->T):T=withMediaAudioHealth(failure,::requireHealthy,block)
    override fun close() {
        if(closed)return
        closed=true
        if(policyState.isInitialized())policy.close()
        var error:Exception?=cleanupFailure
        fun attempt(action:()->Unit){try {action()}catch(e:Exception){if(error==null)error=e else error!!.addSuppressed(e)}}
        if(devicesRegistered)attempt {audio.unregisterAudioDeviceCallback(callback)}
        if(receiverRegistered)attempt {context.unregisterReceiver(receiver)}
        if(focusRequested)attempt {audio.abandonAudioFocusRequest(focus)}
        error?.let {throw ChatFailure("AUDIO_ROUTE_CLEANUP").also {failure->failure.addSuppressed(it)}}
    }
}
