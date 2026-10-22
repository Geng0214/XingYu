package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName="worldbook_entries",indices=[Index(value=["roleId","spaceId"])])
data class WorldbookEntry(@PrimaryKey val id:String,val roleId:String,val spaceId:String,
    val chatId:String?,val title:String,val content:String,val trigger:String,val keywords:String,
    val enabled:Boolean,val priority:Int,val version:Long=1) {
    fun scope()=RoleAccessScope(roleId,spaceId)
}

data class WorldbookDecision(val id:String,val version:Long,val title:String,val reason:String,val characters:Int)
data class WorldbookSelection(val instruction:String,val decisions:List<WorldbookDecision>,val characters:Int)

/** Literal, bounded, non-recursive matching. Imported text is data, never an executable program. */
object Worldbook {
    const val MAX_ENTRIES=64
    const val BUDGET=4000
    const val CONTENT_LIMIT=4000
    private const val HEADER="\n\n当前角色空间的世界书设定："
    fun keywords(text:String)=text.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
    fun validate(title:String,content:String,trigger:String,keys:String,priority:Int) {
        require(title.isNotBlank()&&title.length<=80&&title.none(Char::isISOControl))
        require(content.isNotBlank()&&content.length<=CONTENT_LIMIT&&'\u0000' !in content)
        require(trigger in setOf("always","keywords")&&priority in 0..100)
        require(keys.length<=1620)
        val parsed=keywords(keys)
        require(parsed.size<=20&&parsed.all {it.length<=80&&it.none(Char::isISOControl)})
        require(trigger!="keywords"||parsed.isNotEmpty())
        listOf(title,content,keys).forEach {text->
            var i=0
            while(i<text.length) {
                val c=text[i++]
                if(Character.isHighSurrogate(c)){require(i<text.length&&Character.isLowSurrogate(text[i]));i++}
                else require(!Character.isLowSurrogate(c))
            }
        }
    }
    fun select(scope:RoleAccessScope,chat:ChatEntity,history:List<MessageEntity>,text:String,entries:List<WorldbookEntry>,raw:Boolean=false):WorldbookSelection {
        if(chat.roleId!=scope.roleId||history.any {it.chatId!=chat.id}||entries.any {it.scope()!=scope})throw ChatFailure("ACCESS")
        require(text.length<=12000&&entries.size<=MAX_ENTRIES&&entries.map {it.id}.distinct().size==entries.size)
        val complete=history.filter {it.kind=="assistant"&&it.state==MessageState.COMPLETE}.map {it.requestId}.toSet()
        // Only recent user turns trigger lore: generated lore and assistant replies cannot trigger more entries.
        val scan=listOf(text)+history.filter {it.kind=="user"&&it.requestId in complete}.takeLast(4).map {it.content}
        val result=StringBuilder();val decisions=mutableListOf<WorldbookDecision>()
        entries.sortedWith(compareByDescending<WorldbookEntry> {it.priority}.thenBy {it.id}).forEach {e->
            validate(e.title,e.content,e.trigger,e.keywords,e.priority);require(e.version>0)
            val block="\n\n【${e.title}】\n${e.content}"
            val cost=block.length+if(result.isEmpty())HEADER.length else 0
            val reason=when {
                raw->"raw_mode"
                !e.enabled->"disabled"
                e.chatId!=null&&e.chatId!=chat.id->"other_chat"
                e.trigger=="keywords"&&keywords(e.keywords).none {key->scan.any {it.contains(key,ignoreCase=true)}}->"no_match"
                result.length+cost>BUDGET->"budget"
                else->"included"
            }
            if(reason=="included"){if(result.isEmpty())result.append(HEADER);result.append(block)}
            decisions+=WorldbookDecision(e.id,e.version,e.title,reason,if(reason=="included")cost else 0)
        }
        return WorldbookSelection(result.toString(),decisions.toList(),result.length)
    }
}
