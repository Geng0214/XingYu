package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@Entity(tableName="planner_entries",indices=[Index(value=["roleId","spaceId"]),Index(value=["reminderMinutesBefore"])])
data class PlannerEntry(
    @PrimaryKey val id:String,val roleId:String,val spaceId:String,val kind:String,
    val title:String,val note:String,val startsAt:Long,val endsAt:Long?,val zoneId:String,
    val completedAt:Long?,val reminderMinutesBefore:Long?,val shareWithRole:Boolean,
    val version:Long=1,val createdAt:Long=System.currentTimeMillis(),val updatedAt:Long=createdAt
) { fun scope()=RoleAccessScope(roleId,spaceId) }

@Entity(tableName="cycle_records",indices=[Index(value=["roleId","spaceId"]),Index(value=["startedOn"])])
data class CycleRecord(
    @PrimaryKey val id:String,val roleId:String,val spaceId:String,val startedOn:Long,val endedOn:Long?,
    val flowLevel:String,val note:String,val shareWithRole:Boolean,val version:Long=1,
    val createdAt:Long=System.currentTimeMillis(),val updatedAt:Long=createdAt
) { fun scope()=RoleAccessScope(roleId,spaceId) }

data class PlannerSelection(
    val instruction:String="",val plannerIds:List<String> = emptyList(),val cycleIds:List<String> = emptyList(),
    val skippedPrivate:Int=0,val skippedWindow:Int=0,val skippedBudget:Int=0
)

object PlannerRecords {
    const val MAX_PLANNER=512
    const val MAX_CYCLES=256
    const val TITLE_LIMIT=120
    const val NOTE_LIMIT=4000
    const val CONTEXT_LIMIT=4000
    const val MAX_REMINDER_MINUTES=43_200L
    private const val DAY_MS=24*60*60*1000L
    private val MAX_EPOCH_DAY=LocalDate.MAX.toEpochDay()
    private val dateTime=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm XXX '['VV']'")

    fun validatePlanner(value:PlannerEntry) {
        require(value.id.isNotBlank()&&value.roleId.isNotBlank()&&value.spaceId.isNotBlank())
        require(value.kind in setOf("event","task","anniversary"))
        require(value.title==value.title.trim()&&value.title.isNotBlank()&&value.title.length<=TITLE_LIMIT&&safe(value.title))
        require(value.note.length<=NOTE_LIMIT&&safe(value.note)&&value.startsAt>=0&&value.endsAt?.let {it>=value.startsAt}!=false)
        require(ZoneId.of(value.zoneId).id==value.zoneId)
        require(value.completedAt==null||value.kind=="task"&&value.completedAt>=0)
        require(value.reminderMinutesBefore==null||value.reminderMinutesBefore in 0..MAX_REMINDER_MINUTES)
        require(value.version>0&&value.createdAt>=0&&value.updatedAt>=value.createdAt)
    }

    fun validateCycle(value:CycleRecord) {
        require(value.id.isNotBlank()&&value.roleId.isNotBlank()&&value.spaceId.isNotBlank())
        require(value.startedOn in 0..MAX_EPOCH_DAY&&value.endedOn?.let {it in value.startedOn..minOf(value.startedOn+30,MAX_EPOCH_DAY)}!=false)
        require(value.flowLevel in setOf("unspecified","light","medium","heavy"))
        require(value.note.length<=NOTE_LIMIT&&safe(value.note)&&value.version>0&&value.createdAt>=0&&value.updatedAt>=value.createdAt)
    }

    fun todaySummaryEntries(entries:List<PlannerEntry>,showSchedule:Boolean,showTasks:Boolean,now:Long):List<PlannerEntry> = entries.filter { entry ->
        val zone=ZoneId.of(entry.zoneId)
        val localStart=Instant.ofEpochMilli(entry.startsAt).atZone(zone).toLocalDate()
        val localToday=Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        when(entry.kind) {
            "event"->showSchedule&&localStart==localToday
            "anniversary"->showSchedule&&anniversaryOccursToday(entry,now)
            "task"->showTasks&&entry.completedAt==null&&!localStart.isAfter(localToday)
            else->false
        }
    }

    private fun safe(value:String)=value.none {it=='\u0000'||it.isISOControl()&&it!='\n'&&it!='\t'}

    fun anniversaryOccursToday(value:PlannerEntry,now:Long=System.currentTimeMillis()):Boolean {
        validatePlanner(value)
        if(value.kind!="anniversary")return false
        val original=Instant.ofEpochMilli(value.startsAt).atZone(ZoneId.of(value.zoneId))
        val today=Instant.ofEpochMilli(now).atZone(original.zone).toLocalDate()
        if(today.isBefore(original.toLocalDate()))return false
        return today.monthValue==original.monthValue&&today.dayOfMonth==minOf(original.dayOfMonth,YearMonth.of(today.year,original.monthValue).lengthOfMonth())
    }

    fun reminderAt(value:PlannerEntry,now:Long=System.currentTimeMillis()):Long? {
        validatePlanner(value);val lead=value.reminderMinutesBefore?:return null
        if(value.completedAt!=null)return null
        val leadMs=lead*60_000L
        if(value.kind!="anniversary")return (value.startsAt-leadMs).takeIf {it>now}
        val original=Instant.ofEpochMilli(value.startsAt).atZone(ZoneId.of(value.zoneId))
        val current=Instant.ofEpochMilli(now).atZone(original.zone)
        val firstYear=maxOf(current.year,original.year)
        for(year in firstYear..firstYear+2) {
            val day=minOf(original.dayOfMonth,YearMonth.of(year,original.month).lengthOfMonth())
            val occurrence=ZonedDateTime.of(year,original.monthValue,day,original.hour,original.minute,0,0,original.zone).toInstant().toEpochMilli()
            val trigger=occurrence-leadMs
            if(trigger>now)return trigger
        }
        return null
    }

    fun select(scope:RoleAccessScope,entries:List<PlannerEntry>,cycles:List<CycleRecord>,raw:Boolean,now:Long=System.currentTimeMillis()):PlannerSelection {
        if(entries.any {it.scope()!=scope}||cycles.any {it.scope()!=scope})throw ChatFailure("ACCESS")
        entries.forEach(::validatePlanner);cycles.forEach(::validateCycle)
        if(raw)return PlannerSelection()
        var privateCount=entries.count {!it.shareWithRole}+cycles.count {!it.shareWithRole}
        var windowCount=0;var budgetCount=0;var used=0
        val selectedEntries=mutableListOf<String>();val selectedCycles=mutableListOf<String>();val lines=mutableListOf<String>()
        val candidates=entries.filter {it.shareWithRole&&it.completedAt==null}.mapNotNull {entry->
            val occurrence=if(entry.kind=="anniversary")nextOccurrence(entry,now) else entry.startsAt
            val visible=when(entry.kind){"task"->occurrence>=now-30*DAY_MS;"event"->occurrence in now-7*DAY_MS..now+30*DAY_MS;else->occurrence in now..now+370*DAY_MS}
            if(!visible){windowCount++;null}else entry to occurrence
        }.sortedWith(compareBy<Pair<PlannerEntry,Long>> {it.second}.thenBy {it.first.id})
        for((entry,occurrence) in candidates) {
            val label=when(entry.kind){"task"->"待办";"anniversary"->"纪念日";else->"日程"}
            val whenText=Instant.ofEpochMilli(occurrence).atZone(ZoneId.of(entry.zoneId)).format(dateTime)
            val line="- $label：${entry.title}；时间 $whenText${entry.note.takeIf {it.isNotBlank()}?.let {"；备注 $it"}.orEmpty()}"
            if(used+line.length+1>CONTEXT_LIMIT){budgetCount++;continue}
            lines+=line;used+=line.length+1;selectedEntries+=entry.id
        }
        for(record in cycles.filter {it.shareWithRole}.sortedWith(compareByDescending<CycleRecord>{it.startedOn}.thenByDescending {it.id}).take(3)) {
            val start=LocalDate.ofEpochDay(record.startedOn);val end=record.endedOn?.let(LocalDate::ofEpochDay)
            val flow=when(record.flowLevel){"light"->"少";"medium"->"中";"heavy"->"多";else->"未填写"}
            val line="- 用户明确分享的经期记录：$start${end?.let {" 至 $it"}.orEmpty()}；流量 $flow${record.note.takeIf {it.isNotBlank()}?.let {"；备注 $it"}.orEmpty()}。这是用户记录，不要据此诊断或推断健康状况。"
            if(used+line.length+1>CONTEXT_LIMIT){budgetCount++;continue}
            lines+=line;used+=line.length+1;selectedCycles+=record.id
        }
        val instruction=if(lines.isEmpty())"" else "\n\n用户明确允许角色知道的本机记录：\n${lines.joinToString("\n")}"
        return PlannerSelection(instruction,selectedEntries,selectedCycles,privateCount,windowCount,budgetCount)
    }

    private fun nextOccurrence(value:PlannerEntry,now:Long):Long {
        val original=Instant.ofEpochMilli(value.startsAt).atZone(ZoneId.of(value.zoneId));val current=Instant.ofEpochMilli(now).atZone(original.zone)
        val firstYear=maxOf(current.year,original.year)
        for(year in firstYear..firstYear+1) {
            val day=minOf(original.dayOfMonth,YearMonth.of(year,original.month).lengthOfMonth())
            val candidate=ZonedDateTime.of(year,original.monthValue,day,original.hour,original.minute,0,0,original.zone).toInstant().toEpochMilli()
            if(candidate>=now)return candidate
        }
        error("No anniversary occurrence")
    }
}
