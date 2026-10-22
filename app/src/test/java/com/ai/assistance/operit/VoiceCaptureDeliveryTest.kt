package com.ai.assistance.operit

import com.ai.assistance.operit.data.model.*
import com.ai.assistance.operit.ui.main.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class VoiceCaptureDeliveryTest {
    private val scope=RoleAccessScope("role","space")
    private fun initial()=CompanionState(scope=scope,chatId="chat",voiceRecording=true,voiceRecordingReady=true)

    @Test fun publicationBeforeCancellationIsTakenAndWipedByCancellation() {
        val state=MutableStateFlow(initial());val voice=RecordedVoice(ByteArray(64){42},2)
        publishVoiceCapture(state,scope,"chat",voice);discardVoiceCapture(state)
        assertNull(state.value.recordedVoice);assertFalse(state.value.voiceRecording);assertFalse(state.value.voiceRecordingReady)
        assertTrue(voice.pcm.all {it==0.toByte()})
    }
    @Test fun twentyCancellationsBetweenPublicationReadAndCasRejectTheLateResult() {
        val executor=Executors.newSingleThreadExecutor()
        try {repeat(20) {
            val backing=MutableStateFlow(initial())
            val entering=CountDownLatch(1);val release=CountDownLatch(1)
            val state=object:MutableStateFlow<CompanionState> by backing {
                override fun compareAndSet(expect:CompanionState,update:CompanionState):Boolean {
                    if(update.recordedVoice!=null) {entering.countDown();check(release.await(2,TimeUnit.SECONDS))}
                    return backing.compareAndSet(expect,update)
                }
            }
            val voice=RecordedVoice(ByteArray(64){42},2)
            val publication=executor.submit<Boolean> {
                var published=false
                try {publishVoiceCapture(state,scope,"chat",voice);published=true;false}
                catch(_:CancellationException){true}
                finally {if(!published)voice.pcm.fill(0)}
            }
            assertTrue(entering.await(2,TimeUnit.SECONDS))
            discardVoiceCapture(state);release.countDown()
            assertTrue(publication.get(2,TimeUnit.SECONDS));assertNull(state.value.recordedVoice)
            assertFalse(state.value.voiceRecording);assertTrue(voice.pcm.all {it==0.toByte()})
        }}finally {executor.shutdownNow()}
    }
    @Test fun foreignRoleSpaceOrChatCannotPublish() {
        val state=MutableStateFlow(initial());val voice=RecordedVoice(ByteArray(64),2)
        for((owner,chat) in listOf(scope.copy(roleId="other") to "chat",scope.copy(spaceId="other") to "chat",scope to "other"))
            assertEquals("ACCESS",assertThrows(ChatFailure::class.java){publishVoiceCapture(state,owner,chat,voice)}.code)
        assertNull(state.value.recordedVoice)
    }
}
