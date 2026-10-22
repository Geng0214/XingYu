package com.ai.assistance.operit.data.model

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/** Calendar projection only; the stored record, zone and reminder rule remain authoritative. */
data class PlannerOccurrence(val entry: PlannerEntry, val startsAt: Long, val endsAt: Long?)

object PlannerCalendar {
    fun weekDays(date: LocalDate): List<LocalDate> {
        val monday = date.minusDays((date.dayOfWeek.value - 1).toLong())
        return List(7) { monday.plusDays(it.toLong()) }
    }

    fun monthDays(month: YearMonth): List<LocalDate> {
        val first = month.atDay(1)
        val leading = first.dayOfWeek.value - 1
        val count = ((leading + month.lengthOfMonth() + 6) / 7) * 7
        val monday = first.minusDays(leading.toLong())
        return List(count) { monday.plusDays(it.toLong()) }
    }

    fun onDay(entries: List<PlannerEntry>, date: LocalDate, zone: ZoneId): List<PlannerOccurrence> {
        // Actual local midnights are essential: a daylight-saving day is not always 24 hours.
        val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return entries.mapNotNull { entry ->
            if (entry.kind == "anniversary") {
                val recordZone = ZoneId.of(entry.zoneId)
                val firstYear = Instant.ofEpochMilli(dayStart).atZone(recordZone).year
                val lastYear = Instant.ofEpochMilli(dayEnd - 1).atZone(recordZone).year
                (firstYear..lastYear).map { annualStart(entry, it) }.firstOrNull {
                    it >= entry.startsAt && it >= dayStart && it < dayEnd
                }?.let { PlannerOccurrence(entry, it, null) }
            } else {
                val overlaps = if (entry.endsAt != null && entry.endsAt > entry.startsAt) {
                    entry.startsAt < dayEnd && entry.endsAt > dayStart
                } else entry.startsAt >= dayStart && entry.startsAt < dayEnd
                if (overlaps) PlannerOccurrence(entry, entry.startsAt, entry.endsAt) else null
            }
        }.sortedWith(compareBy<PlannerOccurrence> { it.startsAt }.thenBy { it.entry.id })
    }

    fun nextAnniversary(entry: PlannerEntry, from: LocalDate, zone: ZoneId): PlannerOccurrence {
        require(entry.kind == "anniversary")
        val threshold = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val original = Instant.ofEpochMilli(entry.startsAt).atZone(ZoneId.of(entry.zoneId))
        val year = maxOf(original.year, Instant.ofEpochMilli(threshold).atZone(original.zone).year)
        val thisYear = annualStart(entry, year)
        val next = if (thisYear >= threshold && thisYear >= entry.startsAt) thisYear else annualStart(entry, year + 1)
        return PlannerOccurrence(entry, next, null)
    }

    private fun annualStart(entry: PlannerEntry, year: Int): Long {
        val original = Instant.ofEpochMilli(entry.startsAt).atZone(ZoneId.of(entry.zoneId))
        val month = YearMonth.of(year, original.monthValue)
        // Match PlannerRecords' established February 29 -> February 28 rule in non-leap years.
        val date = month.atDay(minOf(original.dayOfMonth, month.lengthOfMonth()))
        return date.atTime(original.toLocalTime()).atZone(original.zone).toInstant().toEpochMilli()
    }
}
