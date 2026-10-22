package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName="memory_entries",indices=[Index(value=["roleId","spaceId"]),Index(value=["sourceChatId"]),Index(value=["roleId","spaceId","forgottenAt"])])
data class MemoryEntry(
    @PrimaryKey val id:String,
    val roleId:String,
    val spaceId:String,
    val kind:String,
    val title:String,
    val content:String,
    val enabled:Boolean,
    val priority:Int,
    val source:String="manual",
    val version:Long=1,
    val createdAt:Long=System.currentTimeMillis(),
    val updatedAt:Long=createdAt,
    val sourceChatId:String?=null,
    val sourceRequestId:String?=null,
    val sourceUserMessageId:String?=null,
    val sourceAssistantMessageId:String?=null,
    val sourceModel:String?=null,
    val sourceConfigVersion:Long?=null,
    val sourceMessageAt:Long?=null,
    val extractedAt:Long?=null,
    @ColumnInfo(defaultValue="'always'") val recallMode:String="always",
    val expiresAt:Long?=null,
    val forgottenAt:Long?=null,
    val replacedById:String?=null
) {
    fun scope()=RoleAccessScope(roleId,spaceId)
    fun sourceLinked()=sourceChatId!=null
}
data class MemoryDecision(val id:String,val version:Long,val title:String,val kind:String,val reason:String,val characters:Int,val matchPercent:Int=0)
data class MemorySelection(val instruction:String,val decisions:List<MemoryDecision>,val characters:Int)
data class MemoryConflict(val id:String,val version:Long,val title:String,val content:String,val matchPercent:Int)

/** User-owned memory with bounded local recall. Background extraction remains a separate later stage. */
object Memories {
    const val MAX_ENTRIES=64
    const val TITLE_LIMIT=80
    const val CONTENT_LIMIT=2000
    const val BUDGET=4000
    const val RECALL_QUERY_LIMIT=16000
    const val AUTO_MATCH_PERCENT=22
    val KINDS=setOf("preference","real_experience","current_plan","fictional_story")
    val RECALL_MODES=setOf("always","auto")
    private const val HEADER="\n\n用户已确认的长期记忆（按类型理解；“虚构剧情”不是现实事实）："

    fun label(kind:String)=when(kind) {
        "preference"->"用户偏好"
        "real_experience"->"现实经历"
        "current_plan"->"当前计划"
        "fictional_story"->"虚构剧情"
        else->throw IllegalArgumentException("Unknown memory kind")
    }

    fun validate(kind:String,title:String,content:String,priority:Int,source:String="manual",recallMode:String="always",expiresAt:Long?=null) {
        require(kind in KINDS&&source in setOf("manual","extracted")&&priority in 0..100&&recallMode in RECALL_MODES)
        require(expiresAt==null||expiresAt>=0)
        require(title.isNotBlank()&&title.length<=TITLE_LIMIT&&title.none(Char::isISOControl))
        require(content.isNotBlank()&&content.length<=CONTENT_LIMIT&&'\u0000' !in content)
        listOf(title,content).forEach {text->
            var i=0
            while(i<text.length) {
                val c=text[i++]
                if(Character.isHighSurrogate(c)){require(i<text.length&&Character.isLowSurrogate(text[i]));i++}
                else require(!Character.isLowSurrogate(c))
            }
        }
    }

    fun validateStored(entry:MemoryEntry) {
        validate(entry.kind,entry.title,entry.content,entry.priority,entry.source,entry.recallMode,entry.expiresAt)
        require(entry.version>0&&entry.createdAt>=0&&entry.updatedAt>=entry.createdAt)
        require(entry.expiresAt==null||entry.expiresAt>=entry.createdAt)
        require(entry.forgottenAt==null||entry.forgottenAt in entry.createdAt..entry.updatedAt)
        require(entry.replacedById==null||(entry.forgottenAt!=null&&entry.replacedById!=entry.id&&entry.replacedById.isNotBlank()&&entry.replacedById.length<=128))
        val ids=listOf(entry.sourceChatId,entry.sourceRequestId,entry.sourceUserMessageId,entry.sourceAssistantMessageId)
        if(entry.source=="manual") {
            require(ids.all {it==null}&&entry.sourceModel==null&&entry.sourceConfigVersion==null&&entry.sourceMessageAt==null&&entry.extractedAt==null)
        } else {
            require(entry.sourceModel?.let {it.isNotBlank()&&it.length<=300&&it.none(Char::isISOControl)}==true)
            require(entry.sourceConfigVersion!=null&&entry.sourceConfigVersion>0&&entry.sourceMessageAt!=null&&entry.sourceMessageAt>=0&&entry.extractedAt!=null&&entry.extractedAt>=entry.sourceMessageAt)
            require(ids.all {it==null}||ids.all {it!=null&&it.isNotBlank()&&it.length<=128})
        }
    }

    private fun compact(text:String)=text.lowercase().filter {it.isLetterOrDigit()}

    private fun vector(text:String):Map<Int,Int> {
        val points=mutableListOf<Int>();var offset=0
        val normalized=text.lowercase()
        while(offset<normalized.length) {
            val point=Character.codePointAt(normalized,offset);offset+=Character.charCount(point)
            if(Character.isLetterOrDigit(point)||point>0xffff)points+=point
        }
        val result=mutableMapOf<Int,Int>()
        fun add(feature:Int){result[feature]=(result[feature]?:0)+1}
        points.forEach {point->if(point>0xffff)add(point)}
        for(i in 0 until points.lastIndex)add(31*points[i]+points[i+1])
        for(i in 0 until points.size-2)add(31*(31*points[i]+points[i+1])+points[i+2])
        Regex("[a-z0-9]{2,}").findAll(normalized).forEach {add(it.value.hashCode())}
        return result
    }

    fun similarity(first:String,second:String):Int {
        val a=vector(first);val b=vector(second)
        if(a.isEmpty()||b.isEmpty())return 0
        var dot=0.0;var left=0.0;var right=0.0
        a.forEach {(feature,count)->dot+=count.toDouble()*(b[feature]?:0);left+=count.toDouble()*count}
        b.values.forEach {right+=it.toDouble()*it}
        return ((dot/kotlin.math.sqrt(left*right))*100).toInt().coerceIn(0,100)
    }

    fun conflicts(scope:RoleAccessScope,entries:List<MemoryEntry>,kind:String,title:String,content:String,exceptId:String?=null,now:Long=System.currentTimeMillis()):List<MemoryConflict> {
        if(entries.any {it.scope()!=scope})throw ChatFailure("ACCESS")
        validate(kind,title,content,50)
        val normalizedTitle=compact(title)
        return entries.asSequence().filter {it.id!=exceptId&&it.kind==kind&&it.enabled&&it.forgottenAt==null&&(it.expiresAt==null||it.expiresAt>now)}.map {entry->
            validateStored(entry)
            val score=if(normalizedTitle.isNotEmpty()&&compact(entry.title)==normalizedTitle)100 else similarity("$title\n$content","${entry.title}\n${entry.content}")
            MemoryConflict(entry.id,entry.version,entry.title,entry.content,score)
        }.filter {it.matchPercent>=55}.sortedWith(compareByDescending<MemoryConflict>{it.matchPercent}.thenBy {it.id}).toList()
    }

    fun select(scope:RoleAccessScope,entries:List<MemoryEntry>,raw:Boolean=false,query:String="",now:Long=System.currentTimeMillis()):MemorySelection {
        if(entries.any {it.scope()!=scope})throw ChatFailure("ACCESS")
        require(entries.size<=MAX_ENTRIES&&entries.map {it.id}.distinct().size==entries.size)
        val text=StringBuilder();val decisions=mutableListOf<MemoryDecision>()
        data class Scored(val entry:MemoryEntry,val score:Int)
        entries.map {entry->validateStored(entry);Scored(entry,if(entry.recallMode=="always")100 else similarity(query,"${entry.title}\n${entry.content}"))}
            .sortedWith(compareByDescending<Scored>{it.entry.recallMode=="always"}.thenByDescending {it.score}.thenByDescending {it.entry.priority}.thenByDescending {it.entry.updatedAt}.thenBy {it.entry.id}).forEach {(entry,score)->
            validateStored(entry)
            val block="\n\n【${label(entry.kind)} · ${entry.title}】\n${entry.content}"
            val cost=block.length+if(text.isEmpty())HEADER.length else 0
            val reason=when {
                raw->"raw_mode"
                entry.forgottenAt!=null->"forgotten"
                entry.expiresAt!=null&&entry.expiresAt<=now->"expired"
                !entry.enabled->"disabled"
                entry.recallMode=="auto"&&score<AUTO_MATCH_PERCENT->"no_match"
                text.length+cost>BUDGET->"budget"
                else->"included"
            }
            if(reason=="included"){if(text.isEmpty())text.append(HEADER);text.append(block)}
            decisions+=MemoryDecision(entry.id,entry.version,entry.title,entry.kind,reason,if(reason=="included")cost else 0,score)
        }
        return MemorySelection(text.toString(),decisions.toList(),text.length)
    }
}
