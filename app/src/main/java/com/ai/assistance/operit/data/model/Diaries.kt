package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Entity(tableName="diary_entries",indices=[Index(value=["roleId","spaceId"]),Index(value=["roleId","spaceId","notebook","occurredAt"])])
data class DiaryEntry(
    @PrimaryKey val id:String,
    val roleId:String,
    val spaceId:String,
    val notebook:String,
    val title:String,
    val content:String,
    val mood:String,
    val source:String,
    val sourceModel:String?=null,
    val sourceConfigVersion:Long?=null,
    val generationPrompt:String?=null,
    val occurredAt:Long,
    val version:Long=1,
    val createdAt:Long,
    val updatedAt:Long
) {
    fun scope()=RoleAccessScope(roleId,spaceId)
    fun generated()=source=="generated"
}
object Diaries {
    const val MAX_ENTRIES=256
    const val TITLE_LIMIT=80
    const val CONTENT_LIMIT=16_000
    const val PROMPT_LIMIT=4_000
    const val OUTPUT_LIMIT=64*1024
    val NOTEBOOKS=setOf("heart","role_diary")
    val MOODS=setOf("calm","happy","sad","anxious","angry","hopeful","other")
    val SOURCES=setOf("manual","generated")
    private val date=DateTimeFormatter.ISO_LOCAL_DATE

    fun notebookLabel(value:String)=when(value){"heart"->"我的心事";"role_diary"->"角色日记";else->throw IllegalArgumentException("Unknown notebook")}
    fun moodLabel(value:String)=when(value){"calm"->"平静";"happy"->"开心";"sad"->"难过";"anxious"->"不安";"angry"->"生气";"hopeful"->"期待";"other"->"其它";else->throw IllegalArgumentException("Unknown mood")}
    fun sourceLabel(value:String)=when(value){"manual"->"用户填写";"generated"->"模型生成后确认";else->throw IllegalArgumentException("Unknown source")}

    fun validate(notebook:String,title:String,content:String,mood:String,occurredAt:Long) {
        require(notebook in NOTEBOOKS&&mood in MOODS)
        require(title.trim()==title&&title.isNotEmpty()&&title.length<=TITLE_LIMIT&&title.none {it=='\u0000'||(it.isISOControl()&&it!='\n'&&it!='\t')})
        require(content.isNotBlank()&&content.length<=CONTENT_LIMIT&&'\u0000' !in content)
        require(occurredAt in 0..253402300799999L)
    }

    fun validatePrompt(prompt:String) {
        require(prompt.isNotBlank()&&prompt.length<=PROMPT_LIMIT&&'\u0000' !in prompt)
    }

    fun validateStored(entry:DiaryEntry) {
        require(entry.id.isNotBlank()&&entry.roleId.isNotBlank()&&entry.spaceId.isNotBlank())
        validate(entry.notebook,entry.title,entry.content,entry.mood,entry.occurredAt)
        require(entry.source in SOURCES&&entry.version>0&&entry.createdAt>=0&&entry.updatedAt>=entry.createdAt)
        if(entry.source=="manual")require(entry.sourceModel==null&&entry.sourceConfigVersion==null&&entry.generationPrompt==null)
        else {
            require(!entry.sourceModel.isNullOrBlank()&&entry.sourceModel.length<=300)
            require(entry.sourceConfigVersion!=null&&entry.sourceConfigVersion>0)
            validatePrompt(requireNotNull(entry.generationPrompt))
        }
    }

    fun formatDate(value:Long,zone:ZoneId=ZoneId.systemDefault())=Instant.ofEpochMilli(value).atZone(zone).toLocalDate().format(date)
    fun parseDate(value:String,zone:ZoneId=ZoneId.systemDefault())=LocalDate.parse(value,date).atStartOfDay(zone).toInstant().toEpochMilli()
    fun today(zone:ZoneId=ZoneId.systemDefault())=LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
}
