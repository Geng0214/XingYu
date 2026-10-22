package com.ai.assistance.operit

import com.ai.assistance.operit.api.chat.RequestContext
import com.ai.assistance.operit.data.model.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class PlannerTest {
    private val scope=RoleAccessScope("role-a","space-a")
    private val role=RoleEntity(scope.roleId,"小岚","温柔交流",spaceId=scope.spaceId)
    private val chat=ChatEntity("chat-a",role.id,"config","chat")
    private val now=Instant.parse("2026-09-20T10:00:00Z").toEpochMilli()
    private fun entry(id:String="p",kind:String="task",title:String="买花",start:Long=now+3_600_000,share:Boolean=true,reminder:Long?=10,completed:Long?=null,note:String="下班后")=
        PlannerEntry(id,scope.roleId,scope.spaceId,kind,title,note,start,null,"Asia/Shanghai",completed,reminder,share,1,1,1)
    private fun cycle(id:String="c",share:Boolean=true,note:String="休息")=CycleRecord(id,scope.roleId,scope.spaceId,LocalDate.of(2026,9,18).toEpochDay(),LocalDate.of(2026,9,20).toEpochDay(),"medium",note,share,1,1,1)

    @Test fun validationRejectsBrokenKindsDatesControlsAndOwnership() {
        PlannerRecords.validatePlanner(entry());PlannerRecords.validateCycle(cycle())
        for(bad in listOf(entry().copy(kind="script"),entry().copy(title=" "),entry().copy(endsAt=0),entry().copy(reminderMinutesBefore=PlannerRecords.MAX_REMINDER_MINUTES+1)))try {PlannerRecords.validatePlanner(bad);fail()}catch(_:IllegalArgumentException){}
        for(bad in listOf(cycle().copy(endedOn=cycle().startedOn+31),cycle().copy(startedOn=Long.MAX_VALUE,endedOn=null)))try {PlannerRecords.validateCycle(bad);fail()}catch(_:IllegalArgumentException){}
        try {PlannerRecords.select(scope,listOf(entry().copy(spaceId="foreign")),emptyList(),false,now);fail()}catch(e:ChatFailure){assertEquals("ACCESS",e.code)}
    }

    @Test fun reminderIsOneShotForTasksAndYearlyForAnniversariesWithoutExactAlarmAssumptions() {
        assertEquals(now+3_000_000,PlannerRecords.reminderAt(entry(),now))
        assertNull(PlannerRecords.reminderAt(entry(completed=now),now));assertNull(PlannerRecords.reminderAt(entry(start=now-1),now))
        val leapStart=ZonedDateTime.of(2024,2,29,9,0,0,0,ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()
        val annual=entry(kind="anniversary",start=leapStart,reminder=0)
        val from=ZonedDateTime.of(2027,1,1,0,0,0,0,ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()
        val expected=ZonedDateTime.of(2027,2,28,9,0,0,0,ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()
        assertEquals(expected,PlannerRecords.reminderAt(annual,from))
    }

    @Test fun futureAnniversaryStartsRepeatingOnItsFirstDateAcrossSummaryContextAndReminder() {
        val firstOccurrence=ZonedDateTime.of(2027,4,12,9,0,0,0,ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()
        val annual=entry(kind="anniversary",start=firstOccurrence,reminder=0)
        val earlierMatchingDate=ZonedDateTime.of(2026,4,12,10,0,0,0,ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()
        val beforeFirstYear=ZonedDateTime.of(2026,4,12,8,0,0,0,ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()
        assertFalse(PlannerRecords.anniversaryOccursToday(annual,earlierMatchingDate))
        assertTrue(PlannerRecords.anniversaryOccursToday(annual,firstOccurrence))
        assertEquals(firstOccurrence,PlannerRecords.reminderAt(annual,beforeFirstYear))
        val selection=PlannerRecords.select(scope,listOf(annual),emptyList(),false,beforeFirstYear)
        assertTrue(selection.instruction.contains("2027-04-12"))
        assertFalse(selection.instruction.contains("2026-04-12"))
    }

    @Test fun futureLeapDayAnniversaryDoesNotAppearBeforeItsFirstOccurrence() {
        val firstOccurrence=ZonedDateTime.of(2028,2,29,9,0,0,0,ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()
        val anniversary=entry(kind="anniversary",title="LEAP-FIRST",start=firstOccurrence,reminder=0)
        val beforeFirstOccurrence=ZonedDateTime.of(2027,2,28,9,0,0,0,ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli()

        assertFalse(PlannerRecords.anniversaryOccursToday(anniversary,beforeFirstOccurrence))
        assertEquals(firstOccurrence,PlannerRecords.reminderAt(anniversary,beforeFirstOccurrence))
        val selection=PlannerRecords.select(scope,listOf(anniversary),emptyList(),false,beforeFirstOccurrence)
        assertTrue(selection.instruction.contains("2028-02-29"))
        assertFalse(selection.instruction.contains("2027-02-28"))
    }

    @Test fun todaySummaryUsesEachEntryZoneAndKeepsOverdueTasksVisible() {
        val now=Instant.parse("2026-09-20T23:30:00Z").toEpochMilli()
        val utcEvent=entry("utc-event",kind="event",start=Instant.parse("2026-09-20T15:00:00Z").toEpochMilli()).copy(zoneId="UTC")
        val shanghaiEvent=entry("shanghai-event",kind="event",start=utcEvent.startsAt).copy(zoneId="Asia/Shanghai")
        val overdue=entry("overdue",kind="task",start=Instant.parse("2026-09-19T12:00:00Z").toEpochMilli()).copy(zoneId="UTC")
        val completed=entry("completed",kind="task",start=now-1,completed=now).copy(zoneId="UTC")
        val futureTask=entry("future",kind="task",start=Instant.parse("2026-09-22T00:00:00Z").toEpochMilli()).copy(zoneId="UTC")

        assertEquals(listOf("utc-event","overdue"),PlannerRecords.todaySummaryEntries(
            listOf(utcEvent,shanghaiEvent,overdue,completed,futureTask),showSchedule=true,showTasks=true,now=now
        ).map {it.id})
        assertEquals(listOf("overdue"),PlannerRecords.todaySummaryEntries(
            listOf(utcEvent,shanghaiEvent,overdue,completed,futureTask),showSchedule=false,showTasks=true,now=now
        ).map {it.id})
    }

    @Test fun selectionIncludesOnlyExplicitBoundedRecordsAndRawModeExcludesEverything() {
        val privateEntry=entry("private",title="PRIVATE-PLAN",share=false)
        val oldEvent=entry("old",kind="event",title="OLD-PLAN",start=now-8*24*60*60*1000L)
        val selected=PlannerRecords.select(scope,listOf(entry(),privateEntry,oldEvent),listOf(cycle(),cycle("private-cycle",false,"PRIVATE-CYCLE")),false,now)
        assertEquals(listOf("p"),selected.plannerIds);assertEquals(listOf("c"),selected.cycleIds);assertEquals(2,selected.skippedPrivate);assertEquals(1,selected.skippedWindow)
        assertTrue(selected.instruction.contains("买花"));assertTrue(selected.instruction.contains("用户明确分享的经期记录"));assertFalse(selected.instruction.contains("PRIVATE"));assertFalse(selected.instruction.contains("OLD-PLAN"))
        assertTrue(PlannerRecords.select(scope,listOf(entry()),listOf(cycle()),true,now).instruction.isEmpty())
    }

    @Test fun requestContextFreezesSharedPlannerAndNeverAddsPrivateCycle() {
        val resolved=RequestContext.resolve(scope,role,chat,emptyList(),"今天做什么",emptyList(),now=now,plannerEntries=listOf(entry()),cycleRecords=listOf(cycle(share=false,note="PRIVATE-CYCLE")))
        assertEquals(listOf("p"),resolved.planner.plannerIds);assertTrue(resolved.systemInstruction.contains("买花"));assertFalse(resolved.systemInstruction.contains("PRIVATE-CYCLE"))
        val rawRole=role.copy(conversationMode="raw")
        val raw=RequestContext.resolve(scope,rawRole,chat,emptyList(),"今天做什么",emptyList(),now=now,plannerEntries=listOf(entry()),cycleRecords=listOf(cycle()))
        assertTrue(raw.planner.instruction.isEmpty());assertFalse(raw.systemInstruction.contains("买花"))
    }

    @Test fun budgetNeverSilentlyTruncatesAnEntry() {
        val entries=(0..2).map {entry("p-$it",title="条目$it",note="字".repeat(PlannerRecords.NOTE_LIMIT))}
        val selected=PlannerRecords.select(scope,entries,emptyList(),false,now)
        assertTrue(selected.plannerIds.isEmpty());assertEquals(3,selected.skippedBudget);assertTrue(selected.instruction.isEmpty())
    }
}
