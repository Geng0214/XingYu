package com.ai.assistance.operit

import com.ai.assistance.operit.data.model.*
import com.ai.assistance.operit.api.audio.MediaPlaybackRun
import com.ai.assistance.operit.api.audio.withMediaAudioHealth
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class LocalMusicTest {
    private class Resource(val data:ByteArray):AutoCloseable {override fun close(){data.fill(0)}}
    private val data="synthetic-audio".toByteArray()
    private val track=MusicTrack("track","role","space","歌曲🙂","audio/mpeg",5000,data.size.toLong(),LocalMusic.hash(data),"content://synthetic/track",createdAt=1)
    private val session=MusicSession("listen","role","space","chat","track",createdAt=2)
    private fun invalid(block:()->Unit){try{block();fail()}catch(_:IllegalArgumentException){}}
    @Test fun videoDimensionsLimitsAndSummaryRemainExplicit() {
        val video=track.copy(mediaKind="video",mimeType="video/mp4",videoWidth=1920,videoHeight=1080,byteCount=LocalMusic.MAX_VIDEO_BYTES)
        LocalMusic.validate(video);LocalMusic.validate(session,video)
        assertTrue(LocalMusic.summary(session,video).contains("观看"))
        for(t in listOf(video.copy(videoWidth=null),video.copy(videoHeight=0),video.copy(videoWidth=16385),
            video.copy(byteCount=LocalMusic.MAX_VIDEO_BYTES+1),video.copy(mimeType="audio/mp4"),
            track.copy(videoWidth=100),video.copy(mediaKind="remote")))invalid{LocalMusic.validate(t)}
    }
    @Test fun contentHashAndSizeRejectChangedFiles() {
        LocalMusic.verify(track,data)
        for(bytes in listOf(data+0,data.reversedArray()))try{LocalMusic.verify(track,bytes);fail()}catch(e:ChatFailure){assertEquals("MUSIC_CHANGED",e.code)}
    }
    @Test fun foreignOwnersAndInvalidProgressAreRejected() {
        LocalMusic.validate(session,track)
        for(s in listOf(session.copy(roleId="b"),session.copy(spaceId="b"),session.copy(trackId="b"),session.copy(positionMs=-1),session.copy(positionMs=5001),session.copy(state="playing"),session.copy(playbackToken=""),session.copy(note="a".repeat(2001))))invalid{LocalMusic.validate(s,track)}
        for(t in listOf(track.copy(byteCount=LocalMusic.MAX_BYTES+1L),track.copy(sourceUri="https://invalid/audio"),track.copy(durationMs=0)))invalid{LocalMusic.validate(t)}
    }
    @Test fun summariesContainOnlyExplicitMusicMetadataAndNote() {
        val text=LocalMusic.summary(session.copy(note="这句很喜欢"),track)
        assertTrue(text.contains("歌曲🙂"));assertTrue(text.contains("这句很喜欢"));assertFalse(text.contains(track.sourceUri!!));assertFalse(text.contains(track.sha256))
    }
    private data class Write(val token:String,val position:Long,val finish:Boolean,val completed:Boolean)
    @Test fun routeLossWhileWaitingForPlayerKeepsLastPositionAndFinalizesAsPaused()=runBlocking {
        val failure=CompletableDeferred<String>();val writes=mutableListOf<Write>();val bytes=data.copyOf()
        try {
            MediaPlaybackRun({s,t->s.copy(state="playing",playbackToken=t) to track},{Resource(bytes)},
                {_,_,progress->withMediaAudioHealth(failure,{}){progress(1250);failure.complete("MUSIC_ROUTE_CHANGED");awaitCancellation()}},
                {_,t,p,f,c->writes.add(Write(t,p,f,c));true},{},{fail()}).run(session)
            fail()
        }catch(e:ChatFailure){assertEquals("MUSIC_ROUTE_CHANGED",e.code)}
        assertEquals(1250,writes.last().position);assertTrue(writes.last().finish);assertFalse(writes.last().completed)
        assertEquals(1,writes.map {it.token}.distinct().size);assertTrue(bytes.all {it==0.toByte()})
    }
    @Test fun completionAndPeriodicProgressUseOneOwnedTokenAndEraseBytes()=runBlocking {
        val writes=mutableListOf<Write>();val bytes=data.copyOf()
        MediaPlaybackRun({s,t->s.copy(state="playing",playbackToken=t) to track},{Resource(bytes)},
            {_,start,progress->assertEquals(0,start);for(p in listOf(0L,250L,999L,1000L,1500L,2000L))progress(p);true},
            {_,t,p,f,c->writes.add(Write(t,p,f,c));true},{},{fail()}).run(session)
        assertEquals(listOf(0L,1000L,2000L,5000L),writes.map {it.position});assertEquals(1,writes.map {it.token}.distinct().size)
        assertTrue(writes.last().finish&&writes.last().completed);assertTrue(bytes.all {it==0.toByte()})
    }
    @Test fun twentyCancellationAndFastRestartRoundsKeepFinalWritesOwned()=runBlocking {
        val allTokens=mutableSetOf<String>()
        repeat(20) {
            val started=CompletableDeferred<Unit>();val writes=mutableListOf<Write>()
            val first=launch {
                MediaPlaybackRun({s,t->s.copy(state="playing",playbackToken=t) to track},{Resource(data.copyOf())},
                    {_,_,progress->progress(1250);started.complete(Unit);awaitCancellation()},
                    {_,t,p,f,c->writes.add(Write(t,p,f,c));true},{},{fail()}).run(session)
            }
            started.await();first.cancelAndJoin()
            assertEquals(1250,writes.last().position);assertTrue(writes.last().finish);assertFalse(writes.last().completed)
            val oldToken=writes.last().token;assertTrue(allTokens.add(oldToken))
            MediaPlaybackRun({s,t->assertNotEquals(oldToken,t);s.copy(state="playing",playbackToken=t) to track},{Resource(data.copyOf())},
                {_,_,progress->progress(3000);true},{_,t,p,f,c->writes.add(Write(t,p,f,c));true},{},{fail()}).run(session)
            assertNotEquals(oldToken,writes.last().token);assertTrue(allTokens.add(writes.last().token))
        }
    }
    @Test fun cancellationWhileBeginCommitsStillFinalizesAndNeverLoadsAudio()=runBlocking {
        val committing=CompletableDeferred<Unit>();val release=CompletableDeferred<Unit>();var final=false
        val job=launch {
            MediaPlaybackRun<Resource>({s,t->committing.complete(Unit);release.await();s.copy(state="playing",playbackToken=t) to track},
                {error("must not load")},{_,_,_->error("must not play")},{_,_,_,f,_->final=f;true},{},{fail()}).run(session)
        }
        committing.await();job.cancel();release.complete(Unit);job.join();assertTrue(final)
    }
    @Test fun accessRevocationStopsBeforeDecodeAndPersistsPause()=runBlocking {
        var allowed=true;var final=false;val bytes=data.copyOf()
        try {
            MediaPlaybackRun({s,t->s.copy(state="playing",playbackToken=t) to track},{allowed=false;Resource(bytes)},
                {_,_,_->error("must not play")},{_,_,_,f,c->final=f;assertFalse(c);true},
                {if(!allowed)throw ChatFailure("LOCKED")},{fail()}).run(session);fail()
        }catch(e:ChatFailure){assertEquals("LOCKED",e.code)}
        assertTrue(final);assertTrue(bytes.all {it==0.toByte()})
    }
    @Test fun rejectedLateProgressStopsRunAndFinalSaveFailureIsReported()=runBlocking {
        var reported=false
        try {
            MediaPlaybackRun({s,t->s.copy(state="playing",playbackToken=t) to track},{Resource(data.copyOf())},
                {_,_,p->p(10);error("must stop")},{_,_,_,f,_->if(f)throw IllegalStateException("storage");false},{},{reported=true}).run(session);fail()
        }catch(e:ChatFailure){assertEquals("ACCESS",e.code)}
        assertTrue(reported)
    }
}
