package com.ai.assistance.operit.data.model

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

data class DiaryExportPreview(
    val scope:RoleAccessScope,
    val entry:DiaryEntry,
    val roleName:String,
    val content:String,
    val contentSha256:String,
    val byteCount:Int,
    val suggestedBaseName:String
)

data class PreparedDiaryExport(val preview:DiaryExportPreview,val fileName:String) {
    val mimeType="text/markdown"
    fun bytes()=preview.content.toByteArray(Charsets.UTF_8)
}

object DiaryExports {
    const val MAX_BYTES=128*1024
    const val MAX_BASE_NAME=100
    private val invalidName=Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")

    fun sha256(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}

    fun render(roleName:String,entry:DiaryEntry)=buildString {
        append("# ").append(entry.title).append("\n\n")
        append("- 记录簿：").append(Diaries.notebookLabel(entry.notebook)).append('\n')
        append("- 角色空间：").append(roleName).append('\n')
        append("- 日期：").append(Diaries.formatDate(entry.occurredAt)).append('\n')
        append("- 心情：").append(Diaries.moodLabel(entry.mood)).append('\n')
        append("- 来源：").append(Diaries.sourceLabel(entry.source)).append("\n\n")
        if(entry.generated())append("> 这篇内容由模型生成并经用户确认，不代表模型拥有真实经历、意识或未表达的内部想法。\n\n")
        append(entry.content)
        if(!entry.content.endsWith('\n'))append('\n')
    }

    fun prepare(scope:RoleAccessScope,role:RoleEntity,entry:DiaryEntry):DiaryExportPreview {
        if(role.scope()!=scope||entry.scope()!=scope)throw ChatFailure("ACCESS")
        Diaries.validateStored(entry)
        val content=render(role.name,entry);val bytes=content.toByteArray(Charsets.UTF_8)
        if(bytes.isEmpty()||bytes.size>MAX_BYTES)throw ChatFailure("DIARY_EXPORT_LIMIT")
        val raw="${Diaries.notebookLabel(entry.notebook)}-${Diaries.formatDate(entry.occurredAt)}-${entry.title}".replace(invalidName,"_").trim().trimEnd('.').take(MAX_BASE_NAME)
        if(!isValidBaseName(raw))throw ChatFailure("EXPORT_NAME")
        return DiaryExportPreview(scope,entry.copy(),role.name,content,sha256(bytes),bytes.size,raw)
    }

    fun isValidBaseName(value:String):Boolean {
        val trimmed=value.trim()
        return value==trimmed&&trimmed.isNotEmpty()&&trimmed.length<=MAX_BASE_NAME&&!invalidName.containsMatchIn(trimmed)&&!trimmed.endsWith('.')
    }

    fun fileName(baseName:String):String {
        if(!isValidBaseName(baseName))throw ChatFailure("EXPORT_NAME")
        return "$baseName.md"
    }

    fun freeze(preview:DiaryExportPreview,baseName:String)=PreparedDiaryExport(preview,fileName(baseName)).also(::validate)

    fun validate(file:PreparedDiaryExport) {
        val bytes=file.bytes()
        if(file.fileName!=fileName(file.fileName.removeSuffix(".md")))throw ChatFailure("EXPORT_NAME")
        if(bytes.isEmpty()||bytes.size!=file.preview.byteCount||bytes.size>MAX_BYTES||sha256(bytes)!=file.preview.contentSha256)throw ChatFailure("DIARY_CHANGED")
    }

    fun verifySource(file:PreparedDiaryExport,current:DiaryEntry) {
        validate(file)
        if(current!=file.preview.entry||current.scope()!=file.preview.scope)throw ChatFailure("DIARY_CHANGED")
    }
}

object DiaryExportFiles {
    fun writeVerified(file:PreparedDiaryExport,openOutput:()->OutputStream?,openInput:()->InputStream?) {
        DiaryExports.validate(file);val expected=file.bytes()
        try {
            val output=openOutput()?:throw ChatFailure("EXPORT_WRITE")
            output.use {it.write(expected);it.flush()}
            val input=openInput()?:throw ChatFailure("EXPORT_WRITE")
            val restored=input.use {readBounded(it,expected.size)}
            if(!restored.contentEquals(expected))throw ChatFailure("EXPORT_WRITE")
        } catch(e:ChatFailure){throw e}catch(_:Exception){throw ChatFailure("EXPORT_WRITE")}
    }

    private fun readBounded(input:InputStream,expected:Int):ByteArray {
        val output=ByteArrayOutputStream(expected);val buffer=ByteArray(8192)
        while(true) {
            val remaining=expected+1-output.size();if(remaining<=0)throw ChatFailure("EXPORT_WRITE")
            val count=input.read(buffer,0,minOf(buffer.size,remaining));if(count<0)break
            if(count>0)output.write(buffer,0,count)
        }
        return output.toByteArray()
    }
}
