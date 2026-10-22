package com.ai.assistance.operit

import com.ai.assistance.operit.data.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class VoiceConversationTest {
    @Test fun twentyVoiceTurnsFollowTheExplicitCycle() {
        var stage=VoiceConversationStage.CONNECTING
        repeat(20) {
            stage=VoiceConversationMachine.transition(stage,VoiceConversationEvent.CAPTURE_READY)
            assertEquals(VoiceConversationStage.LISTENING,stage)
            stage=VoiceConversationMachine.transition(stage,VoiceConversationEvent.CAPTURED)
            assertEquals(VoiceConversationStage.TRANSCRIBING,stage)
            stage=VoiceConversationMachine.transition(stage,VoiceConversationEvent.TRANSCRIBED)
            assertEquals(VoiceConversationStage.GENERATING,stage)
            stage=VoiceConversationMachine.transition(stage,VoiceConversationEvent.REPLY_READY)
            assertEquals(VoiceConversationStage.SYNTHESIZING,stage)
            stage=VoiceConversationMachine.transition(stage,VoiceConversationEvent.AUDIO_READY)
            assertEquals(VoiceConversationStage.SPEAKING,stage)
            stage=VoiceConversationMachine.transition(stage,VoiceConversationEvent.TURN_FINISHED)
            assertEquals(VoiceConversationStage.CONNECTING,stage)
        }
    }

    @Test fun afterTheOldLoopStopsItsLatestStageResetsToConnecting() {
        listOf(VoiceConversationStage.CONNECTING,VoiceConversationStage.LISTENING,VoiceConversationStage.TRANSCRIBING,VoiceConversationStage.GENERATING,VoiceConversationStage.SYNTHESIZING,
            VoiceConversationStage.SPEAKING).forEach {stage->
            assertEquals(VoiceConversationStage.CONNECTING,VoiceConversationMachine.transition(stage,VoiceConversationEvent.INTERRUPT))
        }
    }

    @Test fun failuresPauseAndResumeWithoutInventingACompletedTurn() {
        VoiceConversationStage.entries.filter {it!=VoiceConversationStage.ENDED}.forEach {stage->
            assertEquals(VoiceConversationStage.PAUSED,VoiceConversationMachine.transition(stage,VoiceConversationEvent.FAILURE))
        }
        assertEquals(VoiceConversationStage.CONNECTING,VoiceConversationMachine.transition(VoiceConversationStage.PAUSED,VoiceConversationEvent.RESUME))
    }

    @Test fun endIsTerminalAndInvalidTransitionsAreRejected() {
        VoiceConversationStage.entries.forEach {stage->assertEquals(VoiceConversationStage.ENDED,VoiceConversationMachine.transition(stage,VoiceConversationEvent.END))}
        assertThrows(IllegalStateException::class.java){VoiceConversationMachine.transition(VoiceConversationStage.ENDED,VoiceConversationEvent.RESUME)}
        assertThrows(IllegalArgumentException::class.java){VoiceConversationMachine.transition(VoiceConversationStage.LISTENING,VoiceConversationEvent.AUDIO_READY)}
        assertThrows(IllegalArgumentException::class.java){VoiceConversationMachine.transition(VoiceConversationStage.PAUSED,VoiceConversationEvent.INTERRUPT)}
        assertThrows(IllegalArgumentException::class.java){VoiceConversationMachine.transition(VoiceConversationStage.CONNECTING,VoiceConversationEvent.CAPTURED)}
        assertThrows(IllegalArgumentException::class.java){VoiceConversationMachine.transition(VoiceConversationStage.PAUSED,VoiceConversationEvent.CAPTURE_READY)}
    }

    @Test fun continuousConversationRequiresSeparateValidatedSpeechServices() {
        val transcription=SpeechServiceConfig("stt","识别",SpeechServices.TRANSCRIPTION,"https://example.com/v1/audio/transcriptions","whisper-1","",60,"stt-key",1)
        val synthesis=SpeechServiceConfig("tts","合成",SpeechServices.SYNTHESIS,"https://example.com/v1/audio/speech","tts-1","alloy",60,"tts-key",1)
        SpeechServices.validateConversationConfigs(transcription,synthesis)
        assertThrows(IllegalArgumentException::class.java){SpeechServices.validateConversationConfigs(transcription,transcription)}
        assertThrows(IllegalArgumentException::class.java){SpeechServices.validateConversationConfigs(synthesis,transcription)}
    }
}
