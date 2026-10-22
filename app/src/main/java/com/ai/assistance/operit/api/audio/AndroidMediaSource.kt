package com.ai.assistance.operit.api.audio

import android.content.ContentResolver
import android.media.MediaDataSource
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import com.ai.assistance.operit.data.model.ChatFailure
import com.ai.assistance.operit.data.model.LocalMusic
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream

private class MemoryMediaInput(private val bytes:ByteArray):MediaInput {
    override val length:Long get()=bytes.size.toLong()
    override fun readAt(position:Long,buffer:ByteArray,offset:Int,size:Int):Int {
        if(position>=bytes.size)return -1
        val count=minOf(size,bytes.size-position.toInt());bytes.copyInto(buffer,offset,position.toInt(),position.toInt()+count);return count
    }
    override fun close(){bytes.fill(0)}
}

private class AudioBuffer:ByteArrayOutputStream() {fun erase(){buf.fill(0);reset()}}

private class DescriptorMediaInput(private val descriptor:ParcelFileDescriptor):MediaInput {
    init {if(!OsConstants.S_ISREG(Os.fstat(descriptor.fileDescriptor).st_mode))throw ChatFailure("MEDIA_SEEKABLE")}
    override val length:Long get()=Os.fstat(descriptor.fileDescriptor).st_size
    override fun readAt(position:Long,buffer:ByteArray,offset:Int,size:Int):Int=Os.pread(descriptor.fileDescriptor,buffer,offset,size,position)
    override fun close(){descriptor.close()}
}

class AndroidMediaSource private constructor(private val reader:VerifiedMediaReader,val kind:String):MediaDataSource() {
    val fingerprint:MediaFingerprint get()=reader.fingerprint
    @Volatile var failure:String?=null
        private set
    override fun getSize():Long=reader.fingerprint.size
    override fun readAt(position:Long,buffer:ByteArray,offset:Int,size:Int):Int=try{reader.readAt(position,buffer,offset,size)}
        catch(e:ChatFailure){failure=e.code;throw e}
        catch(_:Exception){failure="MUSIC_SOURCE";throw ChatFailure("MUSIC_SOURCE")}
    override fun close(){reader.close()}
    // Independent audio/video extractors borrow one verified source. Only the playback owner
    // closes it, after both decoders exit; one extractor must not erase the other's input.
    internal fun borrow():MediaDataSource=object:MediaDataSource() {
        override fun getSize()=this@AndroidMediaSource.getSize()
        override fun readAt(position:Long,buffer:ByteArray,offset:Int,size:Int)=this@AndroidMediaSource.readAt(position,buffer,offset,size)
        override fun close()=Unit
    }
    companion object {
        suspend fun open(resolver:ContentResolver,uri:Uri,kind:String):AndroidMediaSource {
            if(uri.scheme!="content")throw ChatFailure("MUSIC_SOURCE")
            // Keep the shipped audio stream contract; only new, large video sources require seeking.
            val input=when(kind) {
                "audio"->readAudio(resolver,uri)
                "video"->{
                    val descriptor=try{resolver.openFileDescriptor(uri,"r")?:throw ChatFailure("MUSIC_SOURCE")}catch(_:Exception){throw ChatFailure("MUSIC_SOURCE")}
                    try{DescriptorMediaInput(descriptor)}catch(e:Exception){descriptor.close();throw e}
                }
                else->throw ChatFailure("INPUT")
            }
            return fromInput(input,kind)
        }
        internal suspend fun fromInput(input:MediaInput,kind:String):AndroidMediaSource {
            try {
                val context=currentCoroutineContext()
                val fingerprint=VerifiedMediaReader.scan(input,LocalMusic.maxBytes(kind)){context.ensureActive()}
                return AndroidMediaSource(VerifiedMediaReader(input,fingerprint),kind)
            }catch(e:Exception){input.close();throw e}
        }
        private suspend fun readAudio(resolver:ContentResolver,uri:Uri):MediaInput {
            val buffer=ByteArray(8192);val output=AudioBuffer()
            try {
                (resolver.openInputStream(uri)?:throw ChatFailure("MUSIC_SOURCE")).use {input->
                    while(true) {
                        currentCoroutineContext().ensureActive()
                        val count=input.read(buffer);if(count<0)break
                        if(output.size()+count>LocalMusic.MAX_BYTES)throw ChatFailure("MUSIC_LIMIT")
                        output.write(buffer,0,count)
                    }
                }
                return MemoryMediaInput(output.toByteArray())
            }finally{buffer.fill(0);output.erase()}
        }
    }
}
