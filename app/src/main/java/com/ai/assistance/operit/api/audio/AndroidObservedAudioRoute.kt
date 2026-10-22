package com.ai.assistance.operit.api.audio

import android.media.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.ai.assistance.operit.data.model.ChatFailure

/** Owns callbacks for exactly one AudioRecord/AudioTrack, never a shared player's current item. */
internal class AndroidObservedAudioRoute(
    private val manager:AudioManager,
    private val routing:AudioRouting,
    private val expected:CommunicationDevice,
    private val input:Boolean,
    private val requireLease:()->Unit,
    private val stopOwnedAudio:()->Unit,
):AutoCloseable {
    private val state=ObservedAudioRoute(expected)
    private val handler=Handler(Looper.getMainLooper())
    private var closed=false
    private var routesRegistered=false
    private var devicesRegistered=false
    private fun value(device:AudioDeviceInfo)=CommunicationDevice(device.id,device.type,"")
    private val routeListener=AudioRouting.OnRoutingChangedListener {checkCallback()}
    private val deviceListener=object:AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(devices:Array<out AudioDeviceInfo>) {
            synchronized(this@AndroidObservedAudioRoute) {
                if(!closed&&devices.any {expected.matches(value(it))}) {
                    state.lost("AUDIO_ROUTE_CHANGED");stopOwnedAudio()
                }
            }
        }
    }

    fun install() {
        try {
            requireLease()
            routesRegistered=true;routing.addOnRoutingChangedListener(routeListener,handler)
            devicesRegistered=true;manager.registerAudioDeviceCallback(deviceListener,handler)
            val flags=if(input)AudioManager.GET_DEVICES_INPUTS else AudioManager.GET_DEVICES_OUTPUTS
            val device=manager.getDevices(flags).singleOrNull {expected.matches(value(it))}
                ?:throw ChatFailure("AUDIO_ROUTE_UNAVAILABLE")
            if(!routing.setPreferredDevice(device))throw ChatFailure("AUDIO_ROUTE_UNAVAILABLE")
        } catch(e:Exception) {
            try {close()}catch(cleanup:Exception){e.addSuppressed(cleanup)}
            throw e
        }
    }

    @Synchronized fun poll():Boolean {
        if(closed)throw ChatFailure("AUDIO_ROUTE_UNAVAILABLE")
        requireLease()
        // API 36 may expose multiple simultaneous outputs. All must match the chosen device.
        val actual=if(Build.VERSION.SDK_INT>=36)routing.routedDevices else listOfNotNull(routing.routedDevice)
        state.observe(actual.map(::value))
        return state.ready()
    }
    fun requireReady(){if(!poll())throw ChatFailure("AUDIO_ROUTE_UNAVAILABLE")}
    @Synchronized private fun checkCallback() {
        if(closed)return
        try {poll()}catch(e:Exception) {
            state.lost(if(e is ChatFailure)e.code else "AUDIO_ROUTE_CHANGED")
            stopOwnedAudio()
        }
    }
    @Synchronized override fun close() {
        if(closed)return
        closed=true;state.close()
        var failure:Exception?=null
        try {if(routesRegistered)routing.removeOnRoutingChangedListener(routeListener)}catch(e:Exception){failure=e}
        try {if(devicesRegistered)manager.unregisterAudioDeviceCallback(deviceListener)}catch(e:Exception){
            if(failure==null)failure=e else failure.addSuppressed(e)
        }
        if(failure!=null)throw ChatFailure("AUDIO_ROUTE_CLEANUP").also {it.addSuppressed(failure)}
    }
}
