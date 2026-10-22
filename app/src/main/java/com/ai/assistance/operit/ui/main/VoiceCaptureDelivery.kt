package com.ai.assistance.operit.ui.main

import com.ai.assistance.operit.data.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update

internal fun publishVoiceCapture(state:MutableStateFlow<CompanionState>,scope:RoleAccessScope,chatId:String,voice:RecordedVoice) {
    state.update {
        if(!it.voiceRecording)throw CancellationException("Capture ended")
        if(it.scope!=scope||it.chatId!=chatId)throw ChatFailure("ACCESS")
        it.copy(recordedVoice=voice)
    }
}

internal fun discardVoiceCapture(state:MutableStateFlow<CompanionState>) {
    // Capture publication and cancellation race on the same CAS. Never clear a newer PCM
    // reference after reading an older one; the returned state owns exactly what we removed.
    val removed=state.getAndUpdate {it.copy(recordedVoice=null,voiceRecording=false,voiceRecordingReady=false)}
    removed.recordedVoice?.pcm?.fill(0)
}
