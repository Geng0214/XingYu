package com.ai.assistance.operit

import com.ai.assistance.operit.api.audio.BilibiliWebPolicy
import com.ai.assistance.operit.api.audio.BilibiliPlaybackRun
import com.ai.assistance.operit.api.audio.withMediaAudioHealth
import com.ai.assistance.operit.data.model.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class BilibiliWatchingTest {
    private val video=BilibiliVideo("BV1B7411m7LV")
    private val watch=BilibiliWatch("watch","role","space","chat","我们的动画夜",video.videoId,createdAt=1)
    private fun reject(code:String,block:()->Unit){try{block();fail()}catch(e:ChatFailure){assertEquals(code,e.code)}}
    @Test fun focusLossDuringPendingWebSampleFinalizesOnlyTheLastConfirmedPosition()=runBlocking {
        val failure=CompletableDeferred<String>();var position=-1L;var duration:Long?=null;var closed=false
        try {
            BilibiliPlaybackRun({w,t->w.copy(playbackToken=t)}, {_,update->
                withMediaAudioHealth(failure,{}){try {update(1200,5000);failure.complete("MUSIC_FOCUS");awaitCancellation()}finally {closed=true}}
            },{_,_,p,d,finish->if(finish){assertTrue(closed);position=p;duration=d};true},{},{fail()}).run(watch)
            fail()
        }catch(e:ChatFailure){assertEquals("MUSIC_FOCUS",e.code)}
        assertEquals(1200,position);assertEquals(5000L,duration)
    }
    @Test fun canonicalLinksPreservePartAndDiscardTrackingWithoutAnyNetworkLookup() {
        assertEquals(video,BilibiliVideo.parse(" BV1B7411m7LV "))
        val parsed=BilibiliVideo.parse("https://www.bilibili.com/video/BV1B7411m7LV/?p=3&share_source=PRIVATE&token=PRIVATE")
        assertEquals(3,parsed.part)
        assertEquals("https://player.bilibili.com/player.html?bvid=BV1B7411m7LV&p=3&autoplay=0&danmaku=0&t=12",parsed.playerUrl(12345))
        assertFalse(parsed.pageUrl().contains("PRIVATE"))
        assertTrue(BilibiliVideo.parse("av170001").playerUrl(0).contains("aid=170001"))
    }
    @Test fun foreignOriginsCredentialsScriptsEncodedPathsAndAmbiguousPartsAreRejected() {
        for(link in listOf("http://www.bilibili.com/video/BV1B7411m7LV","https://www.bilibili.com.evil.invalid/video/BV1B7411m7LV", "https://user@www.bilibili.com/video/BV1B7411m7LV", "https://www.bilibili.com:443/video/BV1B7411m7LV", "javascript:alert(1)","file:///private", "https://b23.tv/a", "https://www.bilibili.com/video/%42V1B7411m7LV", "https://www.bilibili.com/video/BV1B7411m7LV?p=0", "https://www.bilibili.com/video/BV1B7411m7LV?p=1&p=2", "https://www.bilibili.com/video/BV1B7411m7LV?p=2147483648", "https://www.bilibili.com/video/BV1B7411m7LV#private", "av9007199254740992"))reject("BILI_LINK"){BilibiliVideo.parse(link)}
    }
    @Test fun resourcesRequireHttpsAndExactPlatformOrCdnDomains() {
        for(url in listOf("https://player.bilibili.com/player.html","https://api.bilibili.com/x/player","https://s1.hdslb.com/a.js","https://upos-test.bilivideo.com/v.mp4"))assertTrue(BilibiliWebPolicy.resource(url))
        for(url in listOf("https://evilbilivideo.com/v","https://bilivideo.com.evil.invalid/v","https://s1.hdslb.com:123/a","https://user@s1.hdslb.com/a","http://api.bilibili.com/x","file:///data/user/0","content://private","https://127.0.0.1/","https://passport.bilibili.com/login"))assertFalse(BilibiliWebPolicy.resource(url))
    }
    @Test fun progressRejectsNonFiniteInvalidAndOverlongMediaAndSummaryContainsNoScope() {
        assertEquals(38548L to 590000L,BilibiliWatching.position(38.548159,590.0))
        for((p,d) in listOf(Double.NaN to 10.0,0.0 to Double.POSITIVE_INFINITY,-1.0 to 10.0,11.0 to 10.0,0.0 to 0.0,0.0 to 21601.0))reject("BILI_PROGRESS"){BilibiliWatching.position(p,d)}
        val text=BilibiliWatching.summary(watch.copy(positionMs=12000,durationMs=590000,note="这段好喜欢"))
        assertTrue(text.contains("这段好喜欢"));assertTrue(text.contains(video.pageUrl()));assertFalse(text.contains("space"))
    }
    @Test fun twentyLateCancellationAndReopenRoundsRetainOnlyOwnedProgress()=runBlocking {
        val tokens=mutableSetOf<String>()
        repeat(20) {
            val running=CompletableDeferred<Unit>();var owner:String?=null;var finalPosition=-1L;var finalDuration:Long?=null
            val job=launch {
                BilibiliPlaybackRun({w,t->assertTrue(tokens.add(t));owner=t;w.copy(playbackToken=t,version=w.version+1)},
                    {_,update->update(38548,590000);running.complete(Unit);awaitCancellation()},
                    {_,t,p,d,finish->assertEquals(owner,t);if(finish){finalPosition=p;finalDuration=d;owner=null};true},{},{fail()}).run(watch)
            }
            running.await();job.cancelAndJoin();assertNull(owner);assertEquals(38548,finalPosition);assertEquals(590000L,finalDuration)
            BilibiliPlaybackRun({w,t->assertTrue(tokens.add(t));owner=t;w.copy(playbackToken=t)},
                {_,update->update(1200,590000)}, {_,t,_,_,finish->assertEquals(owner,t);if(finish)owner=null;true},{},{fail()}).run(watch)
            assertNull(owner)
        }
    }
    @Test fun cancellingDuringBeginStillFinalizesButNeverOpensTheWebsite()=runBlocking {
        val entering=CompletableDeferred<Unit>();val commit=CompletableDeferred<Unit>();var finalized=false
        val job=launch {
            BilibiliPlaybackRun({w,t->entering.complete(Unit);commit.await();w.copy(playbackToken=t)},
                {_,_->error("Must not open")},{_,_,p,d,finish->assertEquals(0,p);assertNull(d);finalized=finish;true},{},{fail()}).run(watch)
        }
        entering.await();job.cancel();commit.complete(Unit);job.join();assertTrue(finalized)
    }
    @Test fun revokedAccessAndLateWritesAbortWithExplicitSaveFailure()=runBlocking {
        var allowed=true;var reported=false
        try {
            BilibiliPlaybackRun({w,t->w.copy(playbackToken=t)}, {_,update->allowed=false;update(10,1000)},
                {_,_,_,_,finish->if(finish)throw IllegalStateException("store");true}, {if(!allowed)throw ChatFailure("ACCESS")},{reported=true}).run(watch);fail()
        }catch(e:ChatFailure){assertEquals("ACCESS",e.code)}
        assertTrue(reported)
        try {
            BilibiliPlaybackRun({w,t->w.copy(playbackToken=t)},{_,update->update(10,1000)}, {_,_,_,_,_->false},{},{fail()}).run(watch);fail()
        }catch(e:ChatFailure){assertEquals("ACCESS",e.code)}
    }
}
