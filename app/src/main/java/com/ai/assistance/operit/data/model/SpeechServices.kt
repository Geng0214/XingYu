package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName="speech_service_configs")
data class SpeechServiceConfig(
    @PrimaryKey val id:String,
    val name:String,
    val kind:String,
    val endpoint:String,
    val model:String,
    val voice:String="",
    val timeoutSeconds:Int,
    val credentialRef:String,
    val version:Long=1
)

data class RecordedVoice(val pcm:ByteArray,val durationMs:Long)

enum class VoiceConversationStage { CONNECTING,LISTENING,TRANSCRIBING,GENERATING,SYNTHESIZING,SPEAKING,PAUSED,ENDED }
enum class VoiceConversationEvent { CAPTURE_READY,CAPTURED,TRANSCRIBED,REPLY_READY,AUDIO_READY,TURN_FINISHED,INTERRUPT,FAILURE,RESUME,END }

data class VoiceConversationUi(
    val id:String,
    val scope:RoleAccessScope,
    val chatId:String,
    val roleName:String,
    val transcriptionName:String,
    val transcriptionEndpoint:String,
    val synthesisName:String,
    val synthesisEndpoint:String,
    val stage:VoiceConversationStage=VoiceConversationStage.CONNECTING,
    val turn:Int=1,
    val transcript:String="",
    val reply:String="",
    val errorCode:String="",
    val inputName:String="",
    val outputName:String="",
    val reconnecting:Boolean=false
)

/** Strict in-memory state machine used by both the continuous-call UI and its orchestration loop. */
object VoiceConversationMachine {
    fun transition(stage:VoiceConversationStage,event:VoiceConversationEvent):VoiceConversationStage {
        if(event==VoiceConversationEvent.END)return VoiceConversationStage.ENDED
        if(stage==VoiceConversationStage.ENDED)throw IllegalStateException("Voice conversation already ended")
        if(event==VoiceConversationEvent.FAILURE)return VoiceConversationStage.PAUSED
        if(event==VoiceConversationEvent.INTERRUPT) {
            // The old loop can advance while its stop is being joined. Reset only after it exits.
            require(stage!=VoiceConversationStage.PAUSED)
            return VoiceConversationStage.CONNECTING
        }
        return when(stage) {
            VoiceConversationStage.CONNECTING->if(event==VoiceConversationEvent.CAPTURE_READY)VoiceConversationStage.LISTENING else invalid(stage,event)
            VoiceConversationStage.LISTENING->if(event==VoiceConversationEvent.CAPTURED)VoiceConversationStage.TRANSCRIBING else invalid(stage,event)
            VoiceConversationStage.TRANSCRIBING->if(event==VoiceConversationEvent.TRANSCRIBED)VoiceConversationStage.GENERATING else invalid(stage,event)
            VoiceConversationStage.GENERATING->if(event==VoiceConversationEvent.REPLY_READY)VoiceConversationStage.SYNTHESIZING else invalid(stage,event)
            VoiceConversationStage.SYNTHESIZING->if(event==VoiceConversationEvent.AUDIO_READY)VoiceConversationStage.SPEAKING else invalid(stage,event)
            VoiceConversationStage.SPEAKING->if(event==VoiceConversationEvent.TURN_FINISHED)VoiceConversationStage.CONNECTING else invalid(stage,event)
            VoiceConversationStage.PAUSED->if(event==VoiceConversationEvent.RESUME)VoiceConversationStage.CONNECTING else invalid(stage,event)
            VoiceConversationStage.ENDED->error("unreachable")
        }
    }
    private fun invalid(stage:VoiceConversationStage,event:VoiceConversationEvent):Nothing=throw IllegalArgumentException("Invalid voice transition: $stage + $event")
}

object SpeechServices {
    const val TRANSCRIPTION="transcription"
    const val SYNTHESIS="synthesis"
    const val RECORD_SAMPLE_RATE=16_000
    const val PLAY_SAMPLE_RATE=24_000
    const val MAX_RECORD_SECONDS=60
    const val MAX_RECORD_BYTES=RECORD_SAMPLE_RATE*2*MAX_RECORD_SECONDS
    const val MIN_RECORD_MS=250L
    const val MAX_TRANSCRIPT_JSON=256*1024
    const val MAX_TRANSCRIPT_CHARS=12_000
    const val MAX_SPEECH_CHARS=4_096
    const val MAX_SPEECH_BYTES=12*1024*1024
    val kinds=setOf(TRANSCRIPTION,SYNTHESIS)

    fun validateConfig(value:SpeechServiceConfig) {
        require(value.id.isNotBlank()&&value.name.isNotBlank()&&value.name.length<=240)
        require(value.kind in kinds&&value.model.isNotBlank()&&value.model.length<=200)
        require(value.timeoutSeconds in 15..300&&value.credentialRef.isNotBlank()&&value.version>0)
        if(value.kind==SYNTHESIS)require(value.voice.isNotBlank()&&value.voice.length<=100)
        else require(value.voice.isEmpty())
    }

    fun validateRecording(value:RecordedVoice) {
        require(value.durationMs in MIN_RECORD_MS..MAX_RECORD_SECONDS*1000L)
        require(value.pcm.isNotEmpty()&&value.pcm.size<=MAX_RECORD_BYTES&&value.pcm.size%2==0)
    }

    fun validateSpeechText(value:String) {
        require(value.isNotBlank()&&value.length<=MAX_SPEECH_CHARS)
        require(value.none {it.isISOControl()&&it!='\n'&&it!='\t'})
    }

    fun validateConversationConfigs(transcription:SpeechServiceConfig,synthesis:SpeechServiceConfig) {
        validateConfig(transcription);validateConfig(synthesis)
        require(transcription.kind==TRANSCRIPTION&&synthesis.kind==SYNTHESIS)
        require(transcription.id!=synthesis.id)
    }
}
