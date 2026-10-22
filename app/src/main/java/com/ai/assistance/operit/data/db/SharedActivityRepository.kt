package com.ai.assistance.operit.data.db

import androidx.room.*
import com.ai.assistance.operit.data.model.*
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
interface SharedActivityDao {
    @Query("SELECT a.* FROM shared_activities a JOIN roles r ON r.id=a.roleId AND r.spaceId=a.spaceId JOIN chats c ON c.id=a.chatId AND c.roleId=r.id WHERE r.id=:roleId AND r.spaceId=:spaceId ORDER BY a.updatedAt DESC,a.id DESC")
    fun observe(roleId:String,spaceId:String):Flow<List<SharedActivity>>
    @Query("SELECT a.* FROM shared_activities a JOIN roles r ON r.id=a.roleId AND r.spaceId=a.spaceId JOIN chats c ON c.id=a.chatId AND c.roleId=r.id WHERE a.id=:id AND a.chatId=:chatId AND r.id=:roleId AND r.spaceId=:spaceId")
    suspend fun read(roleId:String,spaceId:String,chatId:String,id:String):SharedActivity?
    @Query("SELECT COUNT(*) FROM shared_activities WHERE readingDocumentId=:documentId")
    suspend fun references(documentId:String):Int
    @Insert suspend fun insert(activity:SharedActivity)
    @Update suspend fun update(activity:SharedActivity):Int
    @Delete suspend fun delete(activity:SharedActivity):Int
}

class SharedActivityRepository(private val db:AppDatabase) {
    private suspend fun checked(scope:RoleAccessScope,chatId:String,id:String,version:Long):Pair<SharedActivity,ReadingDocument> {
        val activity=db.activities().read(scope.roleId,scope.spaceId,chatId,id)?:throw ChatFailure("ACCESS")
        if(activity.version!=version)throw ChatFailure("ACTIVITY_CHANGED")
        val document=db.dao().readingDocument(scope.roleId,scope.spaceId,activity.readingDocumentId)?:throw ChatFailure("ACCESS")
        SharedActivities.validate(activity,document)
        return activity to document
    }
    suspend fun create(scope:RoleAccessScope,chatId:String,documentId:String):SharedActivity=db.withTransaction {
        db.dao().chat(scope.roleId,scope.spaceId,chatId)?:throw ChatFailure("ACCESS")
        val document=db.dao().readingDocument(scope.roleId,scope.spaceId,documentId)?:throw ChatFailure("ACCESS")
        ReadingDocuments.validateStored(document)
        SharedActivity(UUID.randomUUID().toString(),scope.roleId,scope.spaceId,chatId,documentId).also {
            SharedActivities.validate(it,document);db.activities().insert(it)
        }
    }
    suspend fun change(scope:RoleAccessScope,chatId:String,id:String,version:Long,action:String?=null,chapter:Int?=null,offset:Int?=null,note:String?=null)=db.withTransaction {
        val (old,document)=checked(scope,chatId,id,version)
        if(old.state=="completed")throw ChatFailure("ACTIVITY_STATE")
        if(chapter!=null||offset!=null) {
            if(old.state!="active"||chapter==null||offset==null)throw ChatFailure("ACTIVITY_STATE")
        }
        val next=old.copy(state=action?.let {SharedActivities.transition(old.state,it)}?:old.state,
            chapter=chapter?:old.chapter,offset=offset?:old.offset,note=note?:old.note,
            version=old.version+1,updatedAt=maxOf(System.currentTimeMillis(),old.updatedAt))
        SharedActivities.validate(next,document)
        check(db.activities().update(next)==1)
    }
    suspend fun summary(scope:RoleAccessScope,chatId:String,id:String,version:Long):String=db.withTransaction {
        val (activity,document)=checked(scope,chatId,id,version)
        SharedActivities.summary(activity,document)
    }
    suspend fun passage(scope:RoleAccessScope,chatId:String,id:String,version:Long,chapter:Int,start:Int,end:Int):PromptMaterial=db.withTransaction {
        val (activity,document)=checked(scope,chatId,id,version)
        if(activity.chapter!=chapter||activity.offset!=start)throw ChatFailure("ACTIVITY_CHANGED")
        ReadingDocuments.passage(document,chapter,start,end)
    }
    suspend fun delete(scope:RoleAccessScope,chatId:String,id:String,version:Long)=db.withTransaction {
        val (activity,_)=checked(scope,chatId,id,version)
        check(db.activities().delete(activity)==1)
    }
}
