package com.ai.assistance.operit

import com.ai.assistance.operit.data.model.*
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.*
import org.junit.Test

class ProactiveMessagesTest {
    private val zone=ZoneId.of("Asia/Shanghai")
    private fun time(hour:Int,day:Int=20)=ZonedDateTime.of(2026,9,day,hour,0,0,0,zone).toInstant().toEpochMilli()
    private fun setting(enabled:Boolean=true,next:Long=0)=ProactiveRoleSetting("role","space",enabled,"chat","config",1320,480,24,2,next,1,1)
    @Test fun overnightQuietHoursUseLocalClockAndEqualBoundsDisableQuiet() {
        assertTrue(ProactiveMessages.quiet(time(23),zone,1320,480));assertTrue(ProactiveMessages.quiet(time(7),zone,1320,480))
        assertFalse(ProactiveMessages.quiet(time(12),zone,1320,480));assertFalse(ProactiveMessages.quiet(time(23),zone,0,0))
    }
    @Test fun eligibilityEnforcesEveryGateWithoutCatchUp() {
        val noon=time(12);val base=setting(next=noon-1);val counts=ProactiveCounts(0)
        assertTrue(ProactiveMessages.eligible(base,counts,0,3,noon,zone))
        assertFalse(ProactiveMessages.eligible(base.copy(enabled=false),counts,0,3,noon,zone))
        assertFalse(ProactiveMessages.eligible(base.copy(nextEligibleAt=noon+1),counts,0,3,noon,zone))
        assertFalse(ProactiveMessages.eligible(base,counts.copy(dayCount=2),0,3,noon,zone))
        assertFalse(ProactiveMessages.eligible(base,counts,3,3,noon,zone))
    }
    @Test fun localDayKeysFollowSelectedZone() {
        assertEquals("2026-09-20",ProactiveMessages.dayKey(time(12),zone))
    }
    @Test fun twentyLateRunsAdvanceFromNowWithoutBackfill() {
        val now=time(12);repeat(20){assertEquals(now+24L*60L*60L*1000L,ProactiveMessages.nextEligibleAt(now,24))}
    }
    private fun assertFails(block:()->Unit){try{block();fail("expected failure")}catch(_:IllegalArgumentException){}}
}
