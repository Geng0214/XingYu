package com.ai.assistance.operit.api.audio

import android.Manifest
import android.content.Context
import android.media.*
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import com.ai.assistance.operit.data.model.ChatFailure
import kotlinx.coroutines.*
import java.util.concurrent.Executor

/** API 31+ adapter; API 26–30 uses AndroidLegacyCommunicationRoutes. */
@RequiresApi(31)
class AndroidCommunicationRoutes(context:Context):CommunicationRoutePort {
    private val audio=context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler=Handler(Looper.getMainLooper())
    private val executor=Executor {handler.post(it)}
    private var active:Connection?=null
    private var cleanupFailed=false
    private fun value(device:AudioDeviceInfo)=CommunicationDevice(device.id,device.type,device.productName.toString().filterNot(Char::isISOControl).take(80))

    override suspend fun devices():List<CommunicationDevice> =withContext(Dispatchers.Main.immediate) {
        audio.availableCommunicationDevices.filter {it.isSink}.map(::value)
    }
    override suspend fun inputs():List<CommunicationDevice> =withContext(Dispatchers.Main.immediate){audio.communicationInputs().map(::value)}

    @RequiresPermission(Manifest.permission.MODIFY_AUDIO_SETTINGS)
    override suspend fun open(device:CommunicationDevice,input:CommunicationDevice?,changed:(CommunicationDevice?)->Unit,lost:(String)->Unit):CommunicationConnection=withContext(Dispatchers.Main.immediate) {
        if(cleanupFailed)throw ChatFailure("AUDIO_ROUTE_CLEANUP")
        if(active!=null||audio.mode!=AudioManager.MODE_NORMAL)throw ChatFailure("AUDIO_ROUTE_BUSY")
        val output=audio.availableCommunicationDevices.singleOrNull {it.isSink&&device.matches(value(it))}?:throw ChatFailure("AUDIO_ROUTE_UNAVAILABLE")
        val microphone=input?.let {chosen->audio.communicationInputs().singleOrNull {chosen.matches(value(it))}?:throw ChatFailure("AUDIO_ROUTE_UNAVAILABLE")}
        val connection=Connection(output,microphone,changed,lost)
        active=connection
        try {connection.install();connection}
        catch(e:Exception) {
            try {withContext(NonCancellable){connection.close()}}catch(cleanup:Exception){e.addSuppressed(cleanup)}
            throw e
        }
    }

    private inner class Connection(val output:AudioDeviceInfo,val input:AudioDeviceInfo?,val changed:(CommunicationDevice?)->Unit,val lost:(String)->Unit):CommunicationConnection {
        private var closed=false
        private var selected=false
        private var modeOwned=false
        private var focusRequested=false
        private var focusGranted=false
        private var deviceRegistered=false
        private var routeRegistered=false
        private var modeRegistered=false
        private fun valid()=!closed&&active===this
        private val attributes=AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        private val focus=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener({change->if(valid()&&change<0)lost("AUDIO_FOCUS")},handler).build()
        private val deviceListener=object:AudioDeviceCallback() {
            override fun onAudioDevicesRemoved(devices:Array<out AudioDeviceInfo>) {
                if(valid()&&devices.any {(it.id==output.id&&it.type==output.type)||(input!=null&&it.id==input.id&&it.type==input.type)})lost("AUDIO_ROUTE_CHANGED")
            }
        }
        private val routeListener=AudioManager.OnCommunicationDeviceChangedListener {device->if(valid())changed(device?.let(::value))}
        private val modeListener=AudioManager.OnModeChangedListener {mode->if(valid()&&modeOwned&&mode!=AudioManager.MODE_IN_COMMUNICATION)lost("AUDIO_FOCUS")}

        @RequiresPermission(Manifest.permission.MODIFY_AUDIO_SETTINGS)
        fun install() {
            audio.registerAudioDeviceCallback(deviceListener,handler);deviceRegistered=true
            audio.addOnCommunicationDeviceChangedListener(executor,routeListener);routeRegistered=true
            focusRequested=true
            if(audio.requestAudioFocus(focus)!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED)throw ChatFailure("AUDIO_FOCUS")
            focusGranted=true
            modeOwned=true;audio.mode=AudioManager.MODE_IN_COMMUNICATION
            audio.addOnModeChangedListener(executor,modeListener);modeRegistered=true
            if(audio.mode!=AudioManager.MODE_IN_COMMUNICATION)throw ChatFailure("AUDIO_FOCUS")
        }
        @RequiresPermission(Manifest.permission.MODIFY_AUDIO_SETTINGS)
        override suspend fun request():Boolean=withContext(Dispatchers.Main.immediate) {
            if(!valid())throw ChatFailure("AUDIO_ROUTE_UNAVAILABLE")
            if(audio.availableCommunicationDevices.none {it.id==output.id&&it.type==output.type})throw ChatFailure("AUDIO_ROUTE_UNAVAILABLE")
            if(input!=null&&audio.communicationInputs().none {it.id==input.id&&it.type==input.type})throw ChatFailure("AUDIO_ROUTE_UNAVAILABLE")
            selected=true
            audio.setCommunicationDevice(output)
        }
        override suspend fun current():CommunicationDevice?=withContext(Dispatchers.Main.immediate) {
            if(!valid())throw ChatFailure("AUDIO_ROUTE_UNAVAILABLE")
            audio.communicationDevice?.let(::value)
        }
        @RequiresPermission(Manifest.permission.MODIFY_AUDIO_SETTINGS)
        override suspend fun close()=withContext(Dispatchers.Main.immediate) {
            if(closed)return@withContext
            closed=true
            var failure:Exception?=null
            fun cleanup(action:()->Unit){try {action()}catch(e:Exception){if(failure==null)failure=e else failure!!.addSuppressed(e)}}
            if(routeRegistered)cleanup {audio.removeOnCommunicationDeviceChangedListener(routeListener)}
            if(deviceRegistered)cleanup {audio.unregisterAudioDeviceCallback(deviceListener)}
            if(modeRegistered)cleanup {audio.removeOnModeChangedListener(modeListener)}
            if(selected)cleanup {audio.clearCommunicationDevice()}
            if(modeOwned)cleanup {if(audio.mode==AudioManager.MODE_IN_COMMUNICATION)audio.mode=AudioManager.MODE_NORMAL}
            if(focusRequested)cleanup {
                val result=audio.abandonAudioFocusRequest(focus)
                if(focusGranted&&result!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED)throw ChatFailure("AUDIO_ROUTE_CLEANUP")
            }
            if(active===this@Connection)active=null
            if(failure!=null){cleanupFailed=true;throw ChatFailure("AUDIO_ROUTE_CLEANUP").also {it.addSuppressed(requireNotNull(failure))}}
        }
    }
}
