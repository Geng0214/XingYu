package com.ai.assistance.operit.api.audio

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.media.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import com.ai.assistance.operit.data.model.ChatFailure
import kotlinx.coroutines.*

/** Android 8–11: hold a SCO request when selected and verify an actual, silent voice-call track. */
@Suppress("DEPRECATION")
class AndroidLegacyCommunicationRoutes(private val context:Context):CommunicationRoutePort {
    init {require(Build.VERSION.SDK_INT in 26..30)}
    private val audio=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler=Handler(Looper.getMainLooper())
    private var active:Connection?=null
    private var cleanupFailed=false
    private fun value(device:AudioDeviceInfo)=CommunicationDevice(device.id,device.type,device.productName.toString().filterNot(Char::isISOControl).take(80))
    private fun outputs()=audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter {
        it.isSink&&it.type in setOf(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
            AudioDeviceInfo.TYPE_WIRED_HEADSET,AudioDeviceInfo.TYPE_WIRED_HEADPHONES,AudioDeviceInfo.TYPE_USB_HEADSET,
            AudioDeviceInfo.TYPE_USB_DEVICE,AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
    }
    override suspend fun devices():List<CommunicationDevice> =withContext(Dispatchers.Main.immediate){outputs().map(::value)}
    override suspend fun inputs():List<CommunicationDevice> =withContext(Dispatchers.Main.immediate){audio.communicationInputs().map(::value)}

    @RequiresPermission(Manifest.permission.MODIFY_AUDIO_SETTINGS)
    override suspend fun open(device:CommunicationDevice,input:CommunicationDevice?,changed:(CommunicationDevice?)->Unit,lost:(String)->Unit):CommunicationConnection=withContext(Dispatchers.Main.immediate) {
        if(cleanupFailed)throw ChatFailure("AUDIO_ROUTE_CLEANUP")
        if(input!=null&&!communicationPairSupported(input,device,android.os.Build.VERSION.SDK_INT))throw ChatFailure("AUDIO_ROUTE_PAIR")
        if(active!=null||audio.mode!=AudioManager.MODE_NORMAL)throw ChatFailure("AUDIO_ROUTE_BUSY")
        val output=outputs().singleOrNull {device.matches(value(it))}?:throw ChatFailure("AUDIO_ROUTE_UNAVAILABLE")
        val microphone=input?.let {chosen->audio.communicationInputs().singleOrNull {chosen.matches(value(it))}?:throw ChatFailure("AUDIO_ROUTE_UNAVAILABLE")}
        if(output.type==AudioDeviceInfo.TYPE_BLUETOOTH_SCO&&!audio.isBluetoothScoAvailableOffCall)throw ChatFailure("AUDIO_ROUTE_UNAVAILABLE")
        val connection=Connection(output,microphone,changed,lost);active=connection
        try {connection.install();connection}
        catch(e:Exception) {
            try {withContext(NonCancellable){connection.close()}}catch(cleanup:Exception){e.addSuppressed(cleanup)}
            throw e
        }
    }

    private inner class Connection(val output:AudioDeviceInfo,val input:AudioDeviceInfo?,val changed:(CommunicationDevice?)->Unit,val lost:(String)->Unit):CommunicationConnection {
        private val bluetooth=output.type==AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        private val sco=LegacyScoHandshake()
        private val observed=ObservedAudioRoute(value(output))
        private var closed=false
        private var failure=""
        private var requested=false
        private var modeOwned=false
        private var speakerOwned=false
        private var scoRoutingOwned=false
        private var scoRequested=false
        private var focusRequested=false
        private var focusGranted=false
        private var devicesRegistered=false
        private var receiverRegistered=false
        private var probeListenerRegistered=false
        private var probe:AudioTrack?=null
        private val previousSpeaker=audio.isSpeakerphoneOn
        private val previousSco=audio.isBluetoothScoOn
        private fun valid()=!closed&&active===this
        private fun requireValid() {
            if(!valid())throw ChatFailure("AUDIO_ROUTE_UNAVAILABLE")
            if(failure.isNotEmpty())throw ChatFailure(failure)
        }
        private val attributes=AudioAttributes.Builder().setLegacyStreamType(AudioManager.STREAM_VOICE_CALL)
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        private val focus=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener({change->if(valid()&&change<0)fail("AUDIO_FOCUS")},handler).build()
        private val deviceListener=object:AudioDeviceCallback() {
            override fun onAudioDevicesRemoved(devices:Array<out AudioDeviceInfo>) {
                if(valid()&&devices.any {(output.id==it.id&&output.type==it.type)||(input!=null&&input.id==it.id&&input.type==it.type)})fail("AUDIO_ROUTE_CHANGED")
            }
        }
        private val receiver=object:BroadcastReceiver() {
            override fun onReceive(context:Context,intent:Intent) {
                if(!valid()||intent.action!=AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)return
                sco.changed(linkState(intent));observe()
            }
        }
        private val probeListener=AudioRouting.OnRoutingChangedListener {if(valid())observe()}
        private val monitor=object:Runnable {
            override fun run() {
                if(!valid()||failure.isNotEmpty())return
                observe()
                if(valid()&&failure.isEmpty())handler.postDelayed(this,100)
            }
        }
        private fun linkState(intent:Intent)=when(intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE,AudioManager.SCO_AUDIO_STATE_ERROR)) {
            AudioManager.SCO_AUDIO_STATE_CONNECTED->ScoLinkState.CONNECTED
            AudioManager.SCO_AUDIO_STATE_CONNECTING->ScoLinkState.CONNECTING
            AudioManager.SCO_AUDIO_STATE_DISCONNECTED->ScoLinkState.DISCONNECTED
            else->ScoLinkState.ERROR
        }
        private fun fail(code:String) {
            if(!valid()||failure.isNotEmpty())return
            failure=code;observed.lost(code)
            try {probe?.pause()}catch(_:Exception){}
            lost(code)
        }

        @RequiresPermission(Manifest.permission.MODIFY_AUDIO_SETTINGS)
        fun install() {
            devicesRegistered=true;audio.registerAudioDeviceCallback(deviceListener,handler)
            if(bluetooth) {
                // This protected action comes from the system audio service, not an exported app API.
                receiverRegistered=true
                val sticky=ContextCompat.registerReceiver(context,receiver,IntentFilter(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED),null,handler,ContextCompat.RECEIVER_NOT_EXPORTED)
                if(sticky!=null)sco.changed(linkState(sticky))
            }
            focusRequested=true
            if(audio.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED)throw ChatFailure("AUDIO_FOCUS")
            focusGranted=true;modeOwned=true;audio.mode=AudioManager.MODE_IN_COMMUNICATION
            if(audio.mode!=AudioManager.MODE_IN_COMMUNICATION)throw ChatFailure("AUDIO_FOCUS")
            handler.post(monitor)
        }

        @RequiresPermission(Manifest.permission.MODIFY_AUDIO_SETTINGS)
        override suspend fun request():Boolean=withContext(Dispatchers.Main.immediate) {
            requireValid()
            if(requested||outputs().none {it.id==output.id&&it.type==output.type})throw ChatFailure("AUDIO_ROUTE_UNAVAILABLE")
            if(input!=null&&audio.communicationInputs().none {it.id==input.id&&it.type==input.type})throw ChatFailure("AUDIO_ROUTE_UNAVAILABLE")
            requested=true;speakerOwned=true
            audio.isSpeakerphoneOn=output.type==AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
            if(bluetooth) {
                sco.request();scoRequested=true
                // Claim even a sticky already-connected SCO link; stop only our request on close.
                audio.startBluetoothSco()
            } else {
                scoRoutingOwned=true;audio.isBluetoothScoOn=false
            }
            observe();requireValid();true
        }

        private fun startProbe() {
            if(probe!=null)return
            val silence=ByteArray(6_400) // 200 ms at 16 kHz; no microphone or model content.
            val format=AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(16_000).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
            val track=AudioTrack.Builder().setAudioAttributes(attributes).setAudioFormat(format)
                .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(silence.size).build()
            probe=track
            if(track.write(silence,0,silence.size)!=silence.size||track.state!=AudioTrack.STATE_INITIALIZED)throw ChatFailure("AUDIO_PLAYBACK")
            if(track.setLoopPoints(0,silence.size/2,-1)!=AudioTrack.SUCCESS)throw ChatFailure("AUDIO_PLAYBACK")
            probeListenerRegistered=true;track.addOnRoutingChangedListener(probeListener,handler)
            if(!track.setPreferredDevice(output))throw ChatFailure("AUDIO_ROUTE_UNAVAILABLE")
            track.play()
        }

        private fun observe() {
            if(!valid()||failure.isNotEmpty())return
            try {
                if(ContextCompat.checkSelfPermission(context,Manifest.permission.MODIFY_AUDIO_SETTINGS)!=PackageManager.PERMISSION_GRANTED)
                    throw ChatFailure("AUDIO_ROUTE_UNAVAILABLE")
                if(modeOwned&&audio.mode!=AudioManager.MODE_IN_COMMUNICATION)throw ChatFailure("AUDIO_FOCUS")
                if(!requested)return
                if(bluetooth) {
                    if(!sco.ready())return
                    if(!scoRoutingOwned){scoRoutingOwned=true;audio.isBluetoothScoOn=true}
                }
                startProbe()
                val track=requireNotNull(probe)
                if(track.playState!=AudioTrack.PLAYSTATE_PLAYING)throw ChatFailure("AUDIO_ROUTE_CHANGED")
                val actual=track.routedDevice?.let(::value)
                observed.observe(listOfNotNull(actual));observed.ready()
                changed(actual)
            } catch(e:Exception) {fail(if(e is ChatFailure)e.code else "AUDIO_ROUTE_UNAVAILABLE")}
        }
        @RequiresPermission(Manifest.permission.MODIFY_AUDIO_SETTINGS)
        override suspend fun current():CommunicationDevice?=withContext(Dispatchers.Main.immediate) {
            requireValid();observe();requireValid();probe?.routedDevice?.let(::value)
        }
        @RequiresPermission(Manifest.permission.MODIFY_AUDIO_SETTINGS)
        override suspend fun close()=withContext(Dispatchers.Main.immediate) {
            if(closed)return@withContext
            closed=true;sco.close();observed.close();handler.removeCallbacks(monitor)
            var error:Exception?=null
            fun cleanup(action:()->Unit){try {action()}catch(e:Exception){if(error==null)error=e else error!!.addSuppressed(e)}}
            if(devicesRegistered)cleanup {audio.unregisterAudioDeviceCallback(deviceListener)}
            if(receiverRegistered)cleanup {context.unregisterReceiver(receiver)}
            probe?.let {track->
                if(probeListenerRegistered)cleanup {track.removeOnRoutingChangedListener(probeListener)}
                cleanup {track.stop()};cleanup {track.release()}
            }
            probe=null
            // Do not overwrite a phone call or another mode owner while unwinding our lease.
            var ownsMode=false
            cleanup {ownsMode=modeOwned&&audio.mode==AudioManager.MODE_IN_COMMUNICATION}
            if(ownsMode&&scoRoutingOwned)cleanup {audio.isBluetoothScoOn=previousSco}
            if(scoRequested)cleanup {audio.stopBluetoothSco()}
            if(ownsMode&&speakerOwned)cleanup {audio.isSpeakerphoneOn=previousSpeaker}
            if(ownsMode)cleanup {audio.mode=AudioManager.MODE_NORMAL}
            if(focusRequested)cleanup {
                val result=audio.abandonAudioFocusRequest(focus)
                if(focusGranted&&result!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED)throw ChatFailure("AUDIO_ROUTE_CLEANUP")
            }
            if(active===this@Connection)active=null
            if(error!=null){cleanupFailed=true;throw ChatFailure("AUDIO_ROUTE_CLEANUP").also {it.addSuppressed(requireNotNull(error))}}
        }
    }
}
