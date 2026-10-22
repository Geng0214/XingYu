package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Named participants are local story labels, never identities granting access to another role. */
@Entity(tableName="relationship_entries",indices=[Index(value=["roleId","spaceId"])])
data class RelationshipEntry(@PrimaryKey val id:String,val roleId:String,val spaceId:String,val chatId:String?,
    val sourceName:String,val targetName:String,val label:String,val description:String,val enabled:Boolean,val priority:Int,val version:Long=1) {
    fun scope()=RoleAccessScope(roleId,spaceId)
    fun title()="$sourceName → $targetName · $label"
}
data class RelationshipDecision(val id:String,val version:Long,val title:String,val reason:String,val characters:Int)
data class RelationshipSelection(val instruction:String,val decisions:List<RelationshipDecision>,val characters:Int)

object Relationships {
    const val MAX_ENTRIES=32
    const val BUDGET=4000
    const val DESCRIPTION_LIMIT=2000
    fun validate(source:String,target:String,label:String,description:String,priority:Int) {
        listOf(source,target,label).forEach {require(it.isNotBlank()&&it.length<=80&&it.none(Char::isISOControl))}
        require(description.length<=DESCRIPTION_LIMIT&&'\u0000' !in description&&priority in 0..100)
        listOf(source,target,label,description).forEach {s->
            var i=0
            while(i<s.length) {val c=s[i++];if(Character.isHighSurrogate(c)){require(i<s.length&&Character.isLowSurrogate(s[i]));i++}else require(!Character.isLowSurrogate(c))}
        }
    }
    fun select(scope:RoleAccessScope,chat:ChatEntity,entries:List<RelationshipEntry>,raw:Boolean):RelationshipSelection {
        if(chat.roleId!=scope.roleId||entries.any {it.scope()!=scope})throw ChatFailure("ACCESS")
        require(entries.size<=MAX_ENTRIES&&entries.map {it.id}.distinct().size==entries.size)
        val text=StringBuilder();val decisions=mutableListOf<RelationshipDecision>();val header="\n\n当前角色空间的关系设定："
        entries.sortedWith(compareByDescending<RelationshipEntry> {it.priority}.thenBy {it.id}).forEach {entry->
            validate(entry.sourceName,entry.targetName,entry.label,entry.description,entry.priority);require(entry.version>0)
            val block="\n\n【${entry.title()}】"+if(entry.description.isEmpty())""else"\n${entry.description}"
            val cost=block.length+if(text.isEmpty())header.length else 0
            val reason=when {
                raw->"raw_mode"
                !entry.enabled->"disabled"
                entry.chatId!=null&&entry.chatId!=chat.id->"other_chat"
                text.length+cost>BUDGET->"budget"
                else->"included"
            }
            if(reason=="included"){if(text.isEmpty())text.append(header);text.append(block)}
            decisions+=RelationshipDecision(entry.id,entry.version,entry.title(),reason,if(reason=="included")cost else 0)
        }
        return RelationshipSelection(text.toString(),decisions.toList(),text.length)
    }
}
