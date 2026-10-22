package com.ai.assistance.operit.data.model

import androidx.room.*

@Entity(tableName="shared_activities",foreignKeys=[
    ForeignKey(entity=RoleEntity::class,parentColumns=["id"],childColumns=["roleId"],onDelete=ForeignKey.CASCADE),
    ForeignKey(entity=ChatEntity::class,parentColumns=["id"],childColumns=["chatId"],onDelete=ForeignKey.CASCADE),
    ForeignKey(entity=ReadingDocument::class,parentColumns=["id"],childColumns=["readingDocumentId"],onDelete=ForeignKey.NO_ACTION)
],indices=[Index("roleId"),Index("chatId"),Index("readingDocumentId"),Index(value=["roleId","spaceId"])])
data class SharedActivity(
    @PrimaryKey val id:String,val roleId:String,val spaceId:String,val chatId:String,
    val readingDocumentId:String,val kind:String="reading",val state:String="active",
    val chapter:Int=0,val offset:Int=0,val note:String="",val version:Long=1,
    val createdAt:Long=System.currentTimeMillis(),val updatedAt:Long=createdAt
)

object SharedActivities {
    const val NOTE_LIMIT=2000
    fun validate(activity:SharedActivity,document:ReadingDocument) {
        require(activity.id.isNotBlank()&&activity.chatId.isNotBlank())
        require(activity.kind=="reading"&&activity.state in setOf("active","paused","completed"))
        require(activity.roleId==document.roleId&&activity.spaceId==document.spaceId&&activity.readingDocumentId==document.id)
        require(activity.version in 1 until Long.MAX_VALUE&&activity.createdAt>=0&&activity.updatedAt>=activity.createdAt)
        require(activity.note.length<=NOTE_LIMIT&&'\u0000' !in activity.note)
        val chapter=requireNotNull(document.chapters().getOrNull(activity.chapter)){"Invalid chapter"}
        require(activity.offset in chapter.start until chapter.end)
        require(activity.offset==0||!Character.isLowSurrogate(document.text[activity.offset])||!Character.isHighSurrogate(document.text[activity.offset-1]))
    }
    fun transition(state:String,action:String):String=when(state to action) {
        "active" to "pause"->"paused"
        "paused" to "resume"->"active"
        "active" to "complete","paused" to "complete"->"completed"
        else->throw ChatFailure("ACTIVITY_STATE")
    }
    fun summary(activity:SharedActivity,document:ReadingDocument):String {
        validate(activity,document)
        val chapter=document.chapters()[activity.chapter]
        return buildString {
            append("我们的共读记录\n书目：").append(document.displayName)
            append("\n活动状态：").append(when(activity.state){"active"->"进行中";"paused"->"已暂停";else->"已结束"})
            append("\n读到：").append(chapter.title).append("，字符位置 ").append(activity.offset)
            if(activity.note.isNotBlank())append("\n我的感想：").append(activity.note)
        }
    }
}
