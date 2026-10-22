package com.ai.assistance.operit.ui.main

import com.ai.assistance.operit.ui.main.ThemedIcon as Icon

import android.media.AudioDeviceInfo
import android.os.Build
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.api.audio.CommunicationDevice
import com.ai.assistance.operit.api.audio.communicationPairSupported
import com.ai.assistance.operit.data.model.ChatFailure
import com.ai.assistance.operit.data.model.errorText
import kotlinx.coroutines.CancellationException

internal fun voiceDevicePairReady(input:CommunicationDevice?,output:CommunicationDevice?) =
    input!=null&&output!=null&&communicationPairSupported(input,output,Build.VERSION.SDK_INT)

internal fun voiceDeviceLabel(device:CommunicationDevice):String {
    val kind=when(device.type) {
        AudioDeviceInfo.TYPE_BUILTIN_MIC->"手机麦克风"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER->"手机扬声器"
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE->"手机听筒"
        AudioDeviceInfo.TYPE_WIRED_HEADSET->"有线耳麦"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES->"有线耳机"
        AudioDeviceInfo.TYPE_USB_DEVICE,AudioDeviceInfo.TYPE_USB_HEADSET->"USB 音频"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO->"蓝牙耳麦"
        else->if(Build.VERSION.SDK_INT>=31&&device.type==AudioDeviceInfo.TYPE_BLE_HEADSET)"蓝牙耳麦" else "音频设备"
    }
    return if(device.name.isBlank())kind else "$kind · ${device.name}"
}

@Composable internal fun VoiceAudioPicker(
    vm:CompanionViewModel,input:CommunicationDevice?,output:CommunicationDevice?,
    onInput:(CommunicationDevice?)->Unit,onOutput:(CommunicationDevice?)->Unit,enabled:Boolean=true,
    includeInputs:Boolean=true,
) {
    var revision by remember {mutableIntStateOf(0)}
    var loading by remember {mutableStateOf(true)}
    var choices by remember {mutableStateOf<VoiceAudioDevices?>(null)}
    var issue by remember {mutableStateOf("")}
    LaunchedEffect(vm,revision,includeInputs) {
        loading=true;choices=null;issue=""
        try {choices=vm.voiceAudioDevices(includeInputs)}
        catch(e:CancellationException){throw e}
        catch(e:ChatFailure){issue=errorText(e.code)}
        catch(_:Exception){issue="无法读取音频设备，请稍后刷新。"}
        finally {loading=false}
    }
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("本次使用的设备",Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
            TextButton(enabled=enabled&&!loading,onClick={onInput(null);onOutput(null);revision++},modifier=Modifier.heightIn(min=48.dp)) {
                Icon(Icons.Default.Refresh,null);Spacer(Modifier.width(6.dp));Text("刷新")
            }
        }
        if(loading)Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp),strokeWidth=2.dp)
            Text("正在读取设备…",style=MaterialTheme.typography.bodyMedium)
        }
        if(issue.isNotEmpty())Text(issue,Modifier.semantics {liveRegion=LiveRegionMode.Polite},color=MaterialTheme.colorScheme.error)
        choices?.let {devices->
            if((includeInputs&&devices.inputs.isEmpty())||devices.outputs.isEmpty())Text(if(includeInputs)"未找到可用的麦克风或播放设备。请连接耳机后刷新，或检查手机音频设置。" else "未找到可用的播放设备。请连接耳机后刷新，或检查手机音频设置。",color=MaterialTheme.colorScheme.error)
            if(includeInputs) {
                Text("麦克风",style=MaterialTheme.typography.labelLarge)
                VoiceDeviceOptions(devices.inputs,input,enabled&&!loading,onInput)
            }
            Text("声音播放到",Modifier.padding(top=8.dp),style=MaterialTheme.typography.labelLarge)
            VoiceDeviceOptions(devices.outputs,output,enabled&&!loading,onOutput)
        }
        Text(if(includeInputs)"请分别选择输入和输出。设备断开后本轮会停止，重新连接不会自动开启麦克风。" else "朗读不使用麦克风。播放设备断开后会停止，重新连接不会自动继续朗读。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(includeInputs&&input!=null&&output!=null&&!voiceDevicePairReady(input,output))Text(errorText("AUDIO_ROUTE_PAIR"),Modifier.semantics {liveRegion=LiveRegionMode.Polite},color=MaterialTheme.colorScheme.error)
    }
}

@Composable private fun VoiceDeviceOptions(devices:List<CommunicationDevice>,selected:CommunicationDevice?,enabled:Boolean,onSelect:(CommunicationDevice)->Unit) {
    Column(Modifier.fillMaxWidth().selectableGroup(),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        devices.forEach {device->
            Row(Modifier.fillMaxWidth().heightIn(min=48.dp).selectable(selected=device.matches(selected),enabled=enabled,role=Role.RadioButton,onClick={onSelect(device)}).padding(vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
                RadioButton(selected=device.matches(selected),onClick=null,enabled=enabled)
                Spacer(Modifier.width(12.dp))
                Text(voiceDeviceLabel(device),style=MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
