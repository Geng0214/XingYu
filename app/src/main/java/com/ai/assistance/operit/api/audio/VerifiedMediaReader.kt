package com.ai.assistance.operit.api.audio

import com.ai.assistance.operit.data.model.ChatFailure
import java.security.MessageDigest

/** Seekable user-selected file; implementations must not fetch arbitrary network URLs. */
interface MediaInput:AutoCloseable {
    val length:Long
    fun readAt(position:Long,buffer:ByteArray,offset:Int,size:Int):Int
}

data class MediaFingerprint(val size:Long,val sha256:String,val blocks:List<String>)

/** A bounded, verified view of one file version. At most one 256 KiB block is retained. */
class VerifiedMediaReader(private val input:MediaInput,val fingerprint:MediaFingerprint):AutoCloseable {
    private var closed=false
    private var cachedIndex=-1
    private var cached=ByteArray(0)
    @Synchronized fun readAt(position:Long,buffer:ByteArray,offset:Int,size:Int):Int {
        check(!closed);require(position>=0&&size>=0&&offset>=0&&offset<=buffer.size-size)
        if(size==0)return 0
        if(input.length!=fingerprint.size)throw ChatFailure("MUSIC_CHANGED")
        if(position>=fingerprint.size)return -1
        var at=position;var copied=0
        val wanted=minOf(size.toLong(),fingerprint.size-position).toInt()
        while(copied<wanted) {
            val index=(at/BLOCK_SIZE).toInt()
            if(cachedIndex!=index) {
                cached.fill(0);cached=ByteArray(minOf(BLOCK_SIZE.toLong(),fingerprint.size-index.toLong()*BLOCK_SIZE).toInt());cachedIndex=-1
                readFully(input,index.toLong()*BLOCK_SIZE,cached)
                if(hash(cached)!=fingerprint.blocks[index]){cached.fill(0);throw ChatFailure("MUSIC_CHANGED")}
                cachedIndex=index
            }
            val from=(at%BLOCK_SIZE).toInt();val count=minOf(wanted-copied,cached.size-from)
            cached.copyInto(buffer,offset+copied,from,from+count);at+=count;copied+=count
        }
        return copied
    }
    @Synchronized override fun close(){if(!closed){closed=true;cached.fill(0);input.close()}}
    companion object {
        const val BLOCK_SIZE=256*1024
        private fun digest(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes)
        private fun hex(bytes:ByteArray)=bytes.joinToString(""){"%02x".format(it)}
        private fun hash(bytes:ByteArray)=hex(digest(bytes))
        private fun readFully(input:MediaInput,position:Long,bytes:ByteArray) {
            var offset=0
            while(offset<bytes.size) {
                val read=input.readAt(position+offset,bytes,offset,bytes.size-offset)
                if(read<=0||read>bytes.size-offset)throw ChatFailure("MUSIC_SOURCE")
                offset+=read
            }
        }
        fun scan(input:MediaInput,maxBytes:Long,checkAccess:()->Unit):MediaFingerprint {
            val length=input.length
            if(length !in 1..maxBytes)throw ChatFailure("MUSIC_LIMIT")
            val digest=MessageDigest.getInstance("SHA-256");val blocks=mutableListOf<String>();var position=0L
            while(position<length) {
                checkAccess()
                val buffer=ByteArray(minOf(BLOCK_SIZE.toLong(),length-position).toInt())
                try {readFully(input,position,buffer);digest.update(buffer);blocks.add(hash(buffer))}finally{buffer.fill(0)}
                position+=minOf(BLOCK_SIZE.toLong(),length-position)
            }
            if(input.length!=length)throw ChatFailure("MUSIC_CHANGED")
            checkAccess();return MediaFingerprint(length,hex(digest.digest()),blocks.toList())
        }
    }
}
