package com.ai.assistance.operit.api.audio

import android.media.*
import android.os.SystemClock
import android.view.Surface
import com.ai.assistance.operit.data.model.ChatFailure
import kotlinx.coroutines.*

internal data class LocalVideoStreams(val audioDurationUs:Long?,val durationUs:Long)

/** IO-thread extractor/codec ownership; the caller serializes rendering with its stop gate. */
internal object AndroidMediaVideoDecoder {
    suspend fun streams(source:AndroidMediaSource):LocalVideoStreams=withContext(Dispatchers.IO) {
        val extractor=MediaExtractor()
        try {
            extractor.setDataSource(source.borrow())
            val formats=(0 until extractor.trackCount).map {extractor.getTrackFormat(it)}
            val video=formats.firstOrNull {it.getString(MediaFormat.KEY_MIME)?.startsWith("video/")==true}?:throw ChatFailure("MUSIC_FORMAT")
            val duration=video.getLong(MediaFormat.KEY_DURATION)
            if(duration !in 1..21_600_000_000L)throw ChatFailure("MUSIC_FORMAT")
            val audio=formats.firstOrNull {it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/")==true}
            val audioDuration=audio?.getLong(MediaFormat.KEY_DURATION)
            if(audioDuration!=null&&audioDuration !in 1..21_600_000_000L)throw ChatFailure("MUSIC_FORMAT")
            LocalVideoStreams(audioDuration,maxOf(duration,audioDuration?:0))
        }finally {extractor.release()}
    }
    suspend fun decode(source:AndroidMediaSource,startUs:Long,surface:Surface,
        render:suspend (Long,()->Unit)->Unit) {
        val extractor=MediaExtractor();var codec:MediaCodec?=null
        var frames:SeekVideoFrames?=null
        try {
            if(!surface.isValid)throw ChatFailure("VIDEO_SURFACE")
            extractor.setDataSource(source.borrow())
            val index=(0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/")==true
            }?:throw ChatFailure("MUSIC_FORMAT")
            val format=extractor.getTrackFormat(index)
            val mime=format.getString(MediaFormat.KEY_MIME)?:throw ChatFailure("MUSIC_FORMAT")
            extractor.selectTrack(index)
            if(startUs>0)extractor.seekTo(startUs,MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val decoder=MediaCodec.createDecoderByType(mime).also {codec=it}
            decoder.configure(format,surface,null,0);decoder.setVideoScalingMode(MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT);decoder.start()
            val info=MediaCodec.BufferInfo()
            val frameQueue=SeekVideoFrames(startUs,{slot,show->decoder.releaseOutputBuffer(slot,show)},render).also {frames=it}
            var inputEnded=false;var outputEnded=false;var progressAt=SystemClock.elapsedRealtime()
            while(!outputEnded) {
                currentCoroutineContext().ensureActive()
                if(!surface.isValid)throw ChatFailure("VIDEO_SURFACE")
                var progressed=false
                if(!inputEnded) {
                    val slot=decoder.dequeueInputBuffer(0)
                    if(slot>=0) {
                        val input=decoder.getInputBuffer(slot)?:throw ChatFailure("MUSIC_FORMAT")
                        input.clear()
                        if(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_ENCRYPTED!=0)throw ChatFailure("MUSIC_FORMAT")
                        val count=extractor.readSampleData(input,0)
                        if(count<0){decoder.queueInputBuffer(slot,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputEnded=true}
                        else {
                            val flags=if(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_PARTIAL_FRAME!=0)MediaCodec.BUFFER_FLAG_PARTIAL_FRAME else 0
                            decoder.queueInputBuffer(slot,0,count,extractor.sampleTime,flags);extractor.advance()
                        }
                        progressed=true
                    }
                }
                val slot=decoder.dequeueOutputBuffer(info,0)
                if(slot>=0) {
                    if((info.size>0||info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM==0)&&info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG==0)
                        frameQueue.accept(slot,info.presentationTimeUs)
                    else decoder.releaseOutputBuffer(slot,false)
                    outputEnded=info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0;progressed=true
                }else if(slot==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED)progressed=true
                if(progressed)progressAt=SystemClock.elapsedRealtime()
                else {if(SystemClock.elapsedRealtime()-progressAt>=10_000)throw ChatFailure("MUSIC_PLAYBACK");delay(5)}
            }
            frameQueue.finish()
        }catch(e:CancellationException){throw e}
        catch(e:Exception){throw ChatFailure(source.failure?:if(e is ChatFailure)e.code else "MUSIC_PLAYBACK")}
        finally {try {try {frames?.close()}finally {codec?.release()}}finally {extractor.release()}}
    }
}
