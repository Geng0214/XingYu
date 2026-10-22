package com.ai.assistance.operit

import com.ai.assistance.operit.api.audio.*
import com.ai.assistance.operit.data.model.*
import org.junit.Assert.*
import org.junit.Test

class VerifiedMediaReaderTest {
    private class Input(var bytes:ByteArray,val piece:Int=Int.MAX_VALUE):MediaInput {
        var closed=false;var maxRead=0
        override val length:Long get()=bytes.size.toLong()
        override fun readAt(position:Long,buffer:ByteArray,offset:Int,size:Int):Int {
            check(!closed);maxRead=maxOf(maxRead,size)
            if(position>=bytes.size)return -1
            val count=minOf(piece,size,bytes.size-position.toInt());bytes.copyInto(buffer,offset,position.toInt(),position.toInt()+count);return count
        }
        override fun close(){closed=true}
    }
    private fun rejected(code:String,block:()->Unit){try{block();fail()}catch(e:ChatFailure){assertEquals(code,e.code)}}
    @Test fun partialReadsCrossBlockSeeksEofAndBoundedReadSizes() {
        val data=ByteArray(VerifiedMediaReader.BLOCK_SIZE*3+79){(it%253).toByte()};val input=Input(data,777)
        val fp=VerifiedMediaReader.scan(input,data.size.toLong()){}
        assertEquals(LocalMusic.hash(data),fp.sha256);assertEquals(4,fp.blocks.size)
        VerifiedMediaReader(input,fp).use {reader->
            for(position in listOf(0,VerifiedMediaReader.BLOCK_SIZE-19,data.size-17,20)) {
                val out=ByteArray(101);val count=reader.readAt(position.toLong(),out,3,98)
                assertArrayEquals(data.copyOfRange(position,position+count),out.copyOfRange(3,3+count))
            }
            assertEquals(-1,reader.readAt(data.size.toLong(),ByteArray(1),0,1));assertEquals(0,reader.readAt(0,ByteArray(0),0,0))
        }
        assertTrue(input.closed);assertTrue(input.maxRead<=VerifiedMediaReader.BLOCK_SIZE)
    }
    @Test fun modificationAfterScanIsRejectedBeforeExposingTheChangedBlock() {
        val data=ByteArray(VerifiedMediaReader.BLOCK_SIZE+50){7};val input=Input(data)
        val fp=VerifiedMediaReader.scan(input,data.size.toLong()){}
        val reader=VerifiedMediaReader(input,fp);input.bytes[VerifiedMediaReader.BLOCK_SIZE+1]=9
        val out=ByteArray(10){42}
        rejected("MUSIC_CHANGED"){reader.readAt(VerifiedMediaReader.BLOCK_SIZE.toLong(),out,0,10)}
        assertTrue(out.all {it==42.toByte()});reader.close()
    }
    @Test fun resizingLimitsCancellationAndClosedReadsAreExplicit() {
        val input=Input(ByteArray(128));rejected("MUSIC_LIMIT"){VerifiedMediaReader.scan(input,127){}}
        try{VerifiedMediaReader.scan(input,128){throw IllegalStateException("cancel")};fail()}catch(_:IllegalStateException){}
        val fp=VerifiedMediaReader.scan(input,128){};val reader=VerifiedMediaReader(input,fp)
        input.bytes=ByteArray(129);rejected("MUSIC_CHANGED"){reader.readAt(0,ByteArray(1),0,1)}
        reader.close();try{reader.readAt(0,ByteArray(1),0,1);fail()}catch(_:IllegalStateException){}
    }
}
