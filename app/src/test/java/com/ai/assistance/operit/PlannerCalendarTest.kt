package com.ai.assistance.operit

import com.ai.assistance.operit.data.model.PlannerCalendar
import com.ai.assistance.operit.data.model.PlannerEntry
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class PlannerCalendarTest {
    private val shanghai = ZoneId.of("Asia/Shanghai")
    private fun entry(start: String, end: String? = null, kind: String = "event", zone: String = "UTC") =
        PlannerEntry("p", "role", "space", kind, "安排", "", Instant.parse(start).toEpochMilli(),
            end?.let { Instant.parse(it).toEpochMilli() }, zone, null, null, false)

    @Test fun monthGridIsMondayAlignedAndCoversAllDatesWithoutDuplicates() {
        for (month in listOf(YearMonth.of(2026, 2), YearMonth.of(2026, 3), YearMonth.of(2028, 2), YearMonth.of(2026, 10))) {
            val grid = PlannerCalendar.monthDays(month)
            assertEquals(DayOfWeek.MONDAY, grid.first().dayOfWeek)
            assertEquals(DayOfWeek.SUNDAY, grid.last().dayOfWeek)
            assertEquals(0, grid.size % 7)
            assertEquals(grid.size, grid.toSet().size)
            assertEquals((1..month.lengthOfMonth()).map { month.atDay(it) }, grid.filter { YearMonth.from(it) == month })
        }
        assertEquals(LocalDate.of(2025, 12, 29), PlannerCalendar.weekDays(LocalDate.of(2026, 1, 1)).first())
    }

    @Test fun deviceZoneDeterminesDayAndEndAtMidnightIsExclusive() {
        val event = entry("2026-10-02T15:30:00Z", "2026-10-03T16:00:00Z")
        assertEquals(1, PlannerCalendar.onDay(listOf(event), LocalDate.of(2026, 10, 2), shanghai).size)
        assertEquals(1, PlannerCalendar.onDay(listOf(event), LocalDate.of(2026, 10, 3), shanghai).size)
        assertTrue(PlannerCalendar.onDay(listOf(event), LocalDate.of(2026, 10, 4), shanghai).isEmpty())
        val point = entry("2026-10-03T16:00:00Z", "2026-10-03T16:00:00Z")
        assertTrue(PlannerCalendar.onDay(listOf(point), LocalDate.of(2026, 10, 3), shanghai).isEmpty())
        assertEquals(1, PlannerCalendar.onDay(listOf(point), LocalDate.of(2026, 10, 4), shanghai).size)
    }

    @Test fun daylightSavingBoundaryUsesTwentyThreeAndTwentyFiveHourDays() {
        val newYork = ZoneId.of("America/New_York")
        val springEnd = entry("2026-03-09T03:30:00Z")
        assertEquals(1, PlannerCalendar.onDay(listOf(springEnd), LocalDate.of(2026, 3, 8), newYork).size)
        assertTrue(PlannerCalendar.onDay(listOf(springEnd), LocalDate.of(2026, 3, 9), newYork).isEmpty())
        val autumnEnd = entry("2026-11-02T04:30:00Z")
        assertEquals(1, PlannerCalendar.onDay(listOf(autumnEnd), LocalDate.of(2026, 11, 1), newYork).size)
    }

    @Test fun leapAnniversaryMatchesExistingAnnualRuleAndNeverPredatesFirstOccurrence() {
        val annual = entry("2028-02-29T01:00:00Z", kind = "anniversary", zone = "Asia/Shanghai")
        assertTrue(PlannerCalendar.onDay(listOf(annual), LocalDate.of(2027, 2, 28), shanghai).isEmpty())
        assertEquals(1, PlannerCalendar.onDay(listOf(annual), LocalDate.of(2029, 2, 28), shanghai).size)
        val next = PlannerCalendar.nextAnniversary(annual, LocalDate.of(2026, 1, 1), shanghai)
        assertEquals(annual.startsAt, next.startsAt)
        assertEquals(Instant.parse("2029-02-28T01:00:00Z").toEpochMilli(), PlannerCalendar.nextAnniversary(annual, LocalDate.of(2028, 3, 1), shanghai).startsAt)
    }

    @Test fun annualDateNearNewYearProjectsIntoDeviceZone() {
        val annual = entry("2024-12-31T23:30:00Z", kind = "anniversary")
        assertEquals(Instant.parse("2026-12-31T23:30:00Z").toEpochMilli(),
            PlannerCalendar.onDay(listOf(annual), LocalDate.of(2027, 1, 1), shanghai).single().startsAt)
        assertTrue(PlannerCalendar.onDay(listOf(annual), LocalDate.of(2026, 12, 31), shanghai).isEmpty())
    }

    @Test fun calendarKeepsCompletedTasksAndSortsActualInstants() {
        val later = entry("2026-10-03T04:00:00Z").copy(id = "later", kind = "task", completedAt = 1)
        val earlier = entry("2026-10-03T01:00:00Z").copy(id = "earlier")
        assertEquals(listOf("earlier", "later"), PlannerCalendar.onDay(listOf(later, earlier), LocalDate.of(2026, 10, 3), shanghai).map { it.entry.id })
    }
}
