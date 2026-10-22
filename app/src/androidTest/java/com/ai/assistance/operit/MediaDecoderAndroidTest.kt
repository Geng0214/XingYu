package com.ai.assistance.operit

import com.ai.assistance.operit.api.audio.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Platform decoder fixtures only; these tests do not exercise a loudspeaker or claim routing. */
class MediaDecoderAndroidTest {
    @Test fun realStereoWavDecoderPreservesSamples()=decode(0)
    @Test fun realStereoWavSeekDropsPrerollOnWholeFrames()=decode(500_000)
    @Test fun closingOneExtractorViewDoesNotCloseAnotherVerifiedView()=runBlocking {
        val bytes=ByteArray(64){it.toByte()};var closed=false
        val input=object:MediaInput {
            override val length=bytes.size.toLong()
            override fun readAt(position:Long,buffer:ByteArray,offset:Int,size:Int):Int {
                if(position>=bytes.size)return -1
                val count=minOf(size,bytes.size-position.toInt())
                bytes.copyInto(buffer,offset,position.toInt(),position.toInt()+count);return count
            }
            override fun close(){closed=true;bytes.fill(0)}
        }
        val source=AndroidMediaSource.fromInput(input,"video")
        try {
            val audio=source.borrow();val video=source.borrow();val actual=ByteArray(16)
            audio.close();assertFalse(closed)
            assertEquals(16,video.readAt(32,actual,0,16));assertArrayEquals(ByteArray(16){(it+32).toByte()},actual)
            video.close();assertFalse(closed)
        }finally {source.close()}
        assertTrue(closed);assertTrue(bytes.all {it==0.toByte()})
    }
    private fun decode(startUs:Long)=runBlocking {
        val rate=48000;val channels=2;val frames=rate
        val dataSize=frames*channels*2
        val data=ByteBuffer.allocate(44+dataSize).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII));putInt(36+dataSize)
            put("WAVEfmt ".toByteArray(Charsets.US_ASCII));putInt(16);putShort(1);putShort(channels.toShort())
            putInt(rate);putInt(rate*channels*2);putShort((channels*2).toShort());putShort(16)
            put("data".toByteArray(Charsets.US_ASCII));putInt(dataSize)
            repeat(frames){putShort(12345);putShort(-12345)}
        }.array()
        var closed=false
        val input=object:MediaInput {
            override val length:Long get()=data.size.toLong()
            override fun readAt(position:Long,buffer:ByteArray,offset:Int,size:Int):Int {
                if(position>=data.size)return -1
                val count=minOf(size,data.size-position.toInt())
                data.copyInto(buffer,offset,position.toInt(),position.toInt()+count);return count
            }
            override fun close(){data.fill(0);closed=true}
        }
        val source=AndroidMediaSource.fromInput(input,"audio")
        var received=0
        val outputs=mutableListOf<ByteArray>()
        try {
            withDecodedMediaPcm({emit->AndroidMediaPcmDecoder.decode(source,startUs,emit)}) {pcm->
                assertEquals(rate,pcm.rate);assertEquals(channels,pcm.channels)
                val skipped=pcm.skippedFrames(startUs)
                val samples=ByteBuffer.wrap(pcm.bytes).order(ByteOrder.LITTLE_ENDIAN)
                samples.position(skipped*pcm.frameBytes)
                while(samples.hasRemaining()) {
                    assertEquals(12345,samples.short.toInt());assertEquals(-12345,samples.short.toInt());received++
                }
                outputs.add(pcm.bytes)
            }
            assertEquals(frames-(startUs*rate/1_000_000).toInt(),received)
            assertTrue(outputs.isNotEmpty());assertTrue(outputs.all {bytes->bytes.all {it==0.toByte()}})
        }finally {source.close()}
        assertTrue(closed);assertTrue(data.all {it==0.toByte()})
    }
}
