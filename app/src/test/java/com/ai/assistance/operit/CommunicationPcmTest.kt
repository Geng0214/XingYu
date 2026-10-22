package com.ai.assistance.operit

import com.ai.assistance.operit.api.audio.CommunicationPcm
import com.ai.assistance.operit.data.model.ChatFailure
import com.ai.assistance.operit.data.model.SpeechServices
import java.util.concurrent.CancellationException
import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

class CommunicationPcmTest {
    private fun pcm(count:Int,sample:(Int)->Int)=ByteArray(count*2).also {bytes->
        repeat(count){val value=sample(it);bytes[it*2]=value.toByte();bytes[it*2+1]=(value shr 8).toByte()}
    }
    private fun samples(bytes:ByteArray)=IntArray(bytes.size/2){index->
        ((bytes[index*2].toInt() and 255) or (bytes[index*2+1].toInt() shl 8)).toShort().toInt()
    }
    private fun rms(bytes:ByteArray):Double {
        val values=samples(bytes).drop(100).dropLast(100)
        return sqrt(values.sumOf {it.toDouble()*it}/values.size)
    }

    @Test fun bothDirectionsPreserveDurationAndDoNotMutateSource() {
        val recording=pcm(8_000){(sin(2*PI*1_000*it/8_000)*12_000).roundToInt()}
        val original=recording.copyOf()
        val converted=CommunicationPcm.capture(recording){}
        assertEquals(16_000*2,converted.size);assertArrayEquals(original,recording)
        val speech=pcm(24_000){(sin(2*PI*1_000*it/24_000)*12_000).roundToInt()}
        val speechCopy=speech.copyOf()
        val output=CommunicationPcm.playback(speech){}
        assertEquals(16_000*2,output.size);assertArrayEquals(speechCopy,speech)
        assertEquals(rms(recording),rms(converted),35.0)
        assertEquals(rms(speech),rms(output),35.0)
    }

    @Test fun downsamplingRejectsTenKHzAliasingWhileKeepingFourKHzSpeech() {
        fun tone(hz:Int)=pcm(24_000){(sin(2*PI*hz*it/24_000)*16_000).roundToInt()}
        val pass=tone(4_000);val stop=tone(10_000)
        val passRms=rms(CommunicationPcm.playback(pass){})
        val stopRms=rms(CommunicationPcm.playback(stop){})
        assertTrue("4 kHz passband",passRms/rms(pass) in 0.97..1.03)
        assertTrue("10 kHz alias suppressed by at least 40 dB",stopRms/rms(stop)<0.01)
    }

    @Test fun silenceSignedExtremesAndOneFrameInputsAreValid() {
        for(value in listOf(0,-32768,-1,32767)) {
            val recording=CommunicationPcm.capture(pcm(128){value}){}
            val speech=CommunicationPcm.playback(pcm(128){value}){}
            assertTrue(samples(recording).all {it==value})
            assertTrue(samples(speech).all {it==value})
            assertArrayEquals(intArrayOf(value,value),samples(CommunicationPcm.capture(pcm(1){value}){}))
            assertArrayEquals(intArrayOf(value),samples(CommunicationPcm.playback(pcm(1){value}){}))
        }
    }

    @Test fun impulseAndFullScaleStepDoNotWrapOrChangeLength() {
        val impulse=pcm(800){if(it==400)32767 else 0}
        val converted=samples(CommunicationPcm.capture(impulse){})
        assertEquals(1600,converted.size)
        assertEquals(800,converted.indices.maxBy {converted[it]})
        val step=samples(CommunicationPcm.capture(pcm(800){if(it<400)32767 else -32768}){})
        // Filter ringing must saturate. A narrowing cast would wrap these lobes across zero.
        assertTrue(step.slice(780..795).toString(),step.slice(780..795).all {it>0})
        assertTrue(step.slice(805..820).toString(),step.slice(805..820).all {it<0})
        assertTrue(step.slice(780..795).any {it==32767})
        assertTrue(step.slice(805..820).any {it== -32768})
    }

    @Test fun oddEmptyAndOversizedPayloadsAreRejectedBeforeConversion() {
        for(bytes in listOf(ByteArray(0),byteArrayOf(1),ByteArray(SpeechServices.MAX_RECORD_BYTES/2+2))) {
            try {CommunicationPcm.capture(bytes){};fail("invalid capture accepted")}catch(e:ChatFailure){assertEquals("SPEECH_PROTOCOL",e.code)}
        }
        for(bytes in listOf(ByteArray(0),byteArrayOf(1),ByteArray(SpeechServices.MAX_SPEECH_BYTES+2))) {
            try {CommunicationPcm.playback(bytes){};fail("invalid playback accepted")}catch(e:ChatFailure){assertEquals("SPEECH_PROTOCOL",e.code)}
        }
    }

    @Test fun cancellationAndRouteLossInterruptConversionWithoutMutatingSource() {
        val source=pcm(24_000){1000}
        val original=source.copyOf();var calls=0
        try {
            CommunicationPcm.playback(source){if(++calls==3)throw CancellationException("cancelled")}
            fail("conversion ignored cancellation")
        }catch(_:CancellationException){}
        assertEquals(3,calls);assertArrayEquals(original,source)
        calls=0
        try {
            CommunicationPcm.capture(pcm(8_000){1000}){if(++calls==3)throw ChatFailure("AUDIO_ROUTE_CHANGED")}
            fail("conversion ignored route loss")
        }catch(e:ChatFailure){assertEquals("AUDIO_ROUTE_CHANGED",e.code)}
    }
}
