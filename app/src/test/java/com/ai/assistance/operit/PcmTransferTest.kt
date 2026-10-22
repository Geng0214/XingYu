package com.ai.assistance.operit

import com.ai.assistance.operit.api.audio.PcmTransfer
import com.ai.assistance.operit.data.model.ChatFailure
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PcmTransferTest {
    @Test fun partialWritesAndFullBuffersPreserveEveryByteInOrder()=runTest {
        val bytes=ByteArray(20_000){it.toByte()};val output=ByteArrayOutputStream()
        var calls=0
        PcmTransfer.write(bytes,{offset,count->
            assertTrue(count<=8192)
            val written=if(++calls%3==0)0 else minOf(2400,count)
            output.write(bytes,offset,written);written
        },{},clock={testScheduler.currentTime})
        assertArrayEquals(bytes,output.toByteArray())
        assertTrue(testScheduler.currentTime>0)
    }

    @Test fun noProgressHasABoundedErrorForInputAndOutput()=runTest {
        try {PcmTransfer.write(ByteArray(4),{_,_->0},{},clock={testScheduler.currentTime},stallTimeoutMs=100);fail()}
        catch(e:ChatFailure){assertEquals("AUDIO_PLAYBACK",e.code)}
        assertEquals(100L,testScheduler.currentTime)
        try {PcmTransfer.read(4,{0},{},{false},clock={testScheduler.currentTime},stallTimeoutMs=100);fail()}
        catch(e:ChatFailure){assertEquals("MICROPHONE_UNAVAILABLE",e.code)}
        assertEquals(200L,testScheduler.currentTime)
    }

    @Test fun cancellationStopsBothLoopsWithoutWaitingForTheStallDeadline()=runTest {
        var reads=0;var writes=0
        val reader=launch{PcmTransfer.read(4,{reads++;0},{},{false},clock={testScheduler.currentTime})}
        val writer=launch{PcmTransfer.write(ByteArray(4),{_,_->writes++;0},{},clock={testScheduler.currentTime})}
        runCurrent();assertEquals(1,reads);assertEquals(1,writes)
        reader.cancelAndJoin();writer.cancelAndJoin()
        advanceTimeBy(6000);runCurrent()
        assertEquals(1,reads);assertEquals(1,writes)
    }

    @Test fun invalidNativeCountsAreRejectedRatherThanSpinningOrSkippingBytes()=runTest {
        for(count in listOf(-1,1,6)) {
            try {PcmTransfer.write(ByteArray(4),{_,_->count},{});fail()}
            catch(e:ChatFailure){assertEquals("AUDIO_PLAYBACK",e.code)}
            try {PcmTransfer.read(4,{count},{},{false});fail()}
            catch(e:ChatFailure){assertEquals("MICROPHONE_UNAVAILABLE",e.code)}
        }
    }

    @Test fun stopAndRouteLossDoNotDeliverQueuedMicrophoneFrames()=runTest {
        var stopped=false;var reads=0
        val reader=async{PcmTransfer.read(4,{reads++;0},{},{stopped},clock={testScheduler.currentTime})}
        runCurrent();stopped=true;advanceTimeBy(11);runCurrent()
        assertEquals(0,reader.await());assertEquals(1,reads)
        var lost=false
        try {
            PcmTransfer.read(4,{lost=true;4},{if(lost)throw ChatFailure("AUDIO_ROUTE_CHANGED")},{false})
            fail("frames returned after input loss")
        }catch(e:ChatFailure){assertEquals("AUDIO_ROUTE_CHANGED",e.code)}
        lost=false
        try {
            PcmTransfer.write(ByteArray(4),{_,_->lost=true;4},{if(lost)throw ChatFailure("AUDIO_FOCUS")})
            fail("write completed after focus loss")
        }catch(e:ChatFailure){assertEquals("AUDIO_FOCUS",e.code)}
    }
}
