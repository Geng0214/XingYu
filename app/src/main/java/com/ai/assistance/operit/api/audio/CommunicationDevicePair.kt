package com.ai.assistance.operit.api.audio

import android.media.AudioDeviceInfo

/** Legacy SCO is enabled by the output route; a SCO microphone cannot use another output. */
fun communicationPairSupported(input:CommunicationDevice,output:CommunicationDevice,apiLevel:Int):Boolean =
    apiLevel>=31||input.type!=AudioDeviceInfo.TYPE_BLUETOOTH_SCO||output.type==AudioDeviceInfo.TYPE_BLUETOOTH_SCO
