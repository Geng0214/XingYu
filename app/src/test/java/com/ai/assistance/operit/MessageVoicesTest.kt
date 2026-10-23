package com.ai.assistance.operit

import com.ai.assistance.operit.data.model.*
import org.junit.Assert.*
import org.junit.Test

class MessageVoicesTest {
    private fun pcm(samples:Int,value:Short)=ByteArray(samples*2).also {bytes->
        for(i in 0 until samples) {
            bytes[i*2]=(value.toInt() and 0xFF).toByte()
            bytes[i*2+1]=(value.toInt() shr 8).toByte()
        }
    }

    @Test fun waveformKeepsFixedPointCountAndScalesWithVolume() {
        val silence=MessageVoices.waveform(pcm(1600,0))
        assertEquals(32,silence.size)
        assertTrue(silence.all {it==0.toByte()})
        val loud=MessageVoices.waveform(pcm(1600,32767))
        assertEquals(32,loud.size)
        assertTrue(loud.all {(it.toInt() and 0xFF)>=250})
    }

    @Test fun emptyRecordingYieldsZeroWaveform() {
        val out=MessageVoices.waveform(ByteArray(0))
        assertEquals(32,out.size)
        assertTrue(out.all {it==0.toByte()})
    }

    @Test fun durationUsesSixteenKilohertzMonoPcm() {
        assertEquals(1000L,MessageVoices.durationMs(ByteArray(32000)))
        assertEquals(30_000L,MessageVoices.durationMs(ByteArray(960000)))
    }

    @Test fun promptVoiceRejectsOutOfRangeRecordings() {
        val data=pcm(1600,1000)
        val valid=PromptVoice(16000,500L,ByteArray(32),MessageVoices.sha256(data),data)
        valid.validate()
        assertThrows(IllegalArgumentException::class.java){valid.copy(durationMs=100L).validate()}
        assertThrows(IllegalArgumentException::class.java){valid.copy(durationMs=31_000L).validate()}
        assertThrows(IllegalArgumentException::class.java){valid.copy(waveform=ByteArray(31)).validate()}
        assertThrows(IllegalArgumentException::class.java){valid.copy(sha256="00").validate()}
        assertThrows(IllegalArgumentException::class.java){valid.copy(sampleRate=8000).validate()}
    }

    @Test fun voiceLabelCarriesDurationOnly() {
        val data=pcm(8000,100)
        val voice=PromptVoice(16000,8000L,ByteArray(32),MessageVoices.sha256(data),data)
        assertTrue(voice.label().contains("8 秒"))
        assertFalse(voice.label().contains(MessageVoices.sha256(data)))
    }
}
