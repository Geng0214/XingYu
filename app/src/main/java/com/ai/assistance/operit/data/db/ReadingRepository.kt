package com.ai.assistance.operit.data.db

import androidx.room.withTransaction
import com.ai.assistance.operit.data.model.*
import java.util.UUID

class ReadingRepository(private val db:AppDatabase) {
    suspend fun import(scope:RoleAccessScope,prepared:PreparedReadingDocument):ReadingDocument=db.withTransaction {
        prepared.validate()
        val role=db.dao().role(scope.roleId)?:throw ChatFailure("ACCESS")
        if(role.spaceId!=scope.spaceId)throw ChatFailure("ACCESS")
        if(db.dao().readingDocumentCount(scope.roleId,scope.spaceId)>=ReadingDocuments.MAX_DOCUMENTS_PER_ROLE)throw ChatFailure("READING_COUNT")
        val now=System.currentTimeMillis()
        ReadingDocument(UUID.randomUUID().toString(),scope.roleId,scope.spaceId,prepared.displayName,prepared.mimeType,prepared.text,
            ReadingDocuments.encodeChapters(prepared.chapters),sha256=prepared.sha256,createdAt=now,updatedAt=now).also {
            ReadingDocuments.validateStored(it);db.dao().insertReadingDocument(it)
        }
    }

    suspend fun passage(scope:RoleAccessScope,chatId:String,documentId:String,chapter:Int,start:Int,end:Int):PromptMaterial=db.withTransaction {
        db.dao().chat(scope.roleId,scope.spaceId,chatId)?:throw ChatFailure("ACCESS")
        val document=db.dao().readingDocument(scope.roleId,scope.spaceId,documentId)?:throw ChatFailure("ACCESS")
        ReadingDocuments.passage(document,chapter,start,end)
    }

    suspend fun verifyPassage(scope:RoleAccessScope,material:PromptMaterial) {
        if(material.kind!=TextMaterials.KIND_READING)return
        val reference=try {ReadingDocuments.parseReference(requireNotNull(material.sourceUrl))}catch(_:Exception){throw ChatFailure("READING_REFERENCE")}
        val document=db.dao().readingDocument(scope.roleId,scope.spaceId,reference.documentId)?:throw ChatFailure("READING_REFERENCE")
        ReadingDocuments.validateStored(document)
        if(reference.end>document.text.length||document.text.substring(reference.start,reference.end)!=material.text)throw ChatFailure("READING_REFERENCE")
        val chapter=document.chapters().singleOrNull {reference.start>=it.start&&reference.end<=it.end}?:throw ChatFailure("READING_REFERENCE")
        val expected=ReadingDocuments.passage(document,chapter.index,reference.start,reference.end)
        if(expected.displayName!=material.displayName||expected.mimeType!=material.mimeType||expected.sha256!=material.sha256)throw ChatFailure("READING_REFERENCE")
    }

    suspend fun updateProgress(scope:RoleAccessScope,documentId:String,chapter:Int,offset:Int) {
        val document=db.dao().readingDocument(scope.roleId,scope.spaceId,documentId)?:throw ChatFailure("ACCESS")
        ReadingDocuments.validateStored(document)
        val selected=document.chapters().getOrNull(chapter)?:throw ChatFailure("READING_PROGRESS")
        if(offset !in selected.start..selected.end)throw ChatFailure("READING_PROGRESS")
        if(db.dao().updateReadingProgress(scope.roleId,scope.spaceId,documentId,chapter,offset,maxOf(System.currentTimeMillis(),document.updatedAt))!=1)throw ChatFailure("ACCESS")
    }

    suspend fun delete(scope:RoleAccessScope,documentId:String)=db.withTransaction {
        db.dao().readingDocument(scope.roleId,scope.spaceId,documentId)?:throw ChatFailure("ACCESS")
        if(db.dao().readingReferenceCount(documentId)>0||db.activities().references(documentId)>0)throw ChatFailure("READING_REFERENCED")
        if(db.dao().deleteReadingDocument(scope.roleId,scope.spaceId,documentId)!=1)throw ChatFailure("ACCESS")
    }
}
