package com.ai.assistance.operit.api.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

internal fun AudioManager.communicationInputs():List<AudioDeviceInfo> =getDevices(AudioManager.GET_DEVICES_INPUTS).filter {
    it.isSource&&(it.type in setOf(AudioDeviceInfo.TYPE_BUILTIN_MIC,AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_USB_HEADSET,AudioDeviceInfo.TYPE_USB_DEVICE,AudioDeviceInfo.TYPE_BLUETOOTH_SCO)
        ||(Build.VERSION.SDK_INT>=31&&it.type==AudioDeviceInfo.TYPE_BLE_HEADSET))
}

/** Choose the platform implementation by OS version, never retry with a different route on error. */
fun createAndroidCommunicationRoutes(context:Context)=CommunicationRoutes(
    if(Build.VERSION.SDK_INT>=31)AndroidCommunicationRoutes(context) else AndroidLegacyCommunicationRoutes(context)
)
