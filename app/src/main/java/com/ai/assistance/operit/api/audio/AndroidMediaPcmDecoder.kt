package com.ai.assistance.operit.api.audio

import android.media.*
import android.os.SystemClock
import com.ai.assistance.operit.data.model.ChatFailure
import kotlinx.coroutines.*

/** Runs on the PCM producer's IO dispatcher; never opens a URL or an unverified descriptor. */
internal object AndroidMediaPcmDecoder {
    suspend fun decode(source:AndroidMediaSource,startUs:Long,emit:suspend (DecodedMediaPcm)->Unit) {
        val extractor=MediaExtractor()
        var codec:MediaCodec?=null
        try {
            extractor.setDataSource(source.borrow())
            val index=(0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/")==true
            }?:throw ChatFailure("MUSIC_FORMAT")
            val inputFormat=extractor.getTrackFormat(index)
            val mime=inputFormat.getString(MediaFormat.KEY_MIME)?:throw ChatFailure("MUSIC_FORMAT")
            extractor.selectTrack(index)
            if(startUs>0)extractor.seekTo(startUs,MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            // Android decoders emit signed 16-bit PCM unless float was explicitly requested.
            inputFormat.setInteger(MediaFormat.KEY_PCM_ENCODING,AudioFormat.ENCODING_PCM_16BIT)
            val decoder=MediaCodec.createDecoderByType(mime).also {codec=it}
            decoder.configure(inputFormat,null,null,0);decoder.start()
            val info=MediaCodec.BufferInfo()
            var inputEnded=false;var outputEnded=false
            var lastProgress=SystemClock.elapsedRealtime()
            while(!outputEnded) {
                currentCoroutineContext().ensureActive()
                var progressed=false
                if(!inputEnded) {
                    val slot=decoder.dequeueInputBuffer(0)
                    if(slot>=0) {
                        val buffer=decoder.getInputBuffer(slot)?:throw ChatFailure("MUSIC_FORMAT")
                        buffer.clear()
                        if(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED!=0)throw ChatFailure("MUSIC_FORMAT")
                        val count=extractor.readSampleData(buffer,0)
                        if(count<0) {
                            decoder.queueInputBuffer(slot,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputEnded=true
                        }else {
                            val flags=if(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME!=0)MediaCodec.BUFFER_FLAG_PARTIAL_FRAME else 0
                            decoder.queueInputBuffer(slot,0,count,extractor.sampleTime,flags);extractor.advance()
                        }
                        progressed=true
                    }
                }
                val slot=decoder.dequeueOutputBuffer(info,0)
                if(slot>=0) {
                    var pcm:DecodedMediaPcm?=null
                    try {
                        if(info.size>0&&info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG==0) {
                            val format=decoder.getOutputFormat(slot)
                            if(format.containsKey(MediaFormat.KEY_PCM_ENCODING)&&format.getInteger(MediaFormat.KEY_PCM_ENCODING)!=AudioFormat.ENCODING_PCM_16BIT)
                                throw ChatFailure("MUSIC_FORMAT")
                            if(info.size>512*1024)throw ChatFailure("MUSIC_FORMAT")
                            val buffer=decoder.getOutputBuffer(slot)?:throw ChatFailure("MUSIC_FORMAT")
                            val bytes=ByteArray(info.size)
                            try {
                                buffer.position(info.offset);buffer.limit(info.offset+info.size);buffer.get(bytes)
                                pcm=DecodedMediaPcm(format.getInteger(MediaFormat.KEY_SAMPLE_RATE),format.getInteger(MediaFormat.KEY_CHANNEL_COUNT),info.presentationTimeUs,bytes)
                            }catch(e:Exception){bytes.fill(0);throw e}
                        }
                    }finally {
                        try {decoder.releaseOutputBuffer(slot,false)}catch(e:Exception){pcm?.bytes?.fill(0);throw e}
                    }
                    pcm?.let {emit(it)}
                    outputEnded=info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0
                    progressed=true
                }else if(slot==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED)progressed=true
                if(progressed)lastProgress=SystemClock.elapsedRealtime()
                else {
                    if(SystemClock.elapsedRealtime()-lastProgress>=10_000)throw ChatFailure("MUSIC_PLAYBACK")
                    delay(5)
                }
            }
        }catch(e:CancellationException){throw e}
        catch(e:Exception){throw ChatFailure(source.failure?:if(e is ChatFailure)e.code else "MUSIC_PLAYBACK")}
        finally {try {codec?.release()}finally {extractor.release()}}
    }
}
