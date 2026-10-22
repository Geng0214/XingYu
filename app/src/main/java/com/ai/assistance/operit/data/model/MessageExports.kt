package com.ai.assistance.operit.data.model

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class MessageExportFormat(val id:String,val label:String,val mimeType:String,val extension:String) {
    TEXT("text","TXT","text/plain","txt"),
    MARKDOWN("markdown","Markdown","text/markdown","md");

    companion object {
        fun fromId(id:String)=entries.singleOrNull {it.id==id}?:throw ChatFailure("EXPORT_FORMAT")
    }
}

data class MessageExportPreview(
    val scope:RoleAccessScope,
    val chatId:String,
    val messageId:String,
    val requestId:String,
    val roleName:String,
    val chatTitle:String,
    val content:String,
    val contentSha256:String,
    val byteCount:Int,
    val createdAt:Long,
    val suggestedBaseName:String
)

data class PreparedMessageExport(
    val preview:MessageExportPreview,
    val format:MessageExportFormat,
    val fileName:String
) {
    val mimeType get()=format.mimeType
    val byteCount get()=preview.byteCount
    fun bytes()=preview.content.toByteArray(Charsets.UTF_8)
}

object MessageExports {
    const val MAX_BYTES=512*1024
    const val MAX_BASE_NAME=100
    private val invalidName=Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
    private val timestamp=DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")

    fun sha256(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}

    fun prepare(scope:RoleAccessScope,role:RoleEntity,chat:ChatEntity,message:MessageEntity):MessageExportPreview {
        if(role.scope()!=scope||chat.roleId!=scope.roleId||message.chatId!=chat.id)throw ChatFailure("ACCESS")
        if(message.kind!="assistant"||message.state!=MessageState.COMPLETE||message.content.isBlank())throw ChatFailure("EXPORT_MESSAGE")
        if('\u0000' in message.content)throw ChatFailure("EXPORT_MESSAGE")
        val bytes=message.content.toByteArray(Charsets.UTF_8)
        if(bytes.size>MAX_BYTES)throw ChatFailure("EXPORT_LIMIT")
        val time=Instant.ofEpochMilli(message.createdAt).atZone(ZoneId.systemDefault()).format(timestamp)
        val raw="${role.name}-${chat.title}-$time".replace(invalidName,"_").trim().trimEnd('.').take(MAX_BASE_NAME)
        if(!isValidBaseName(raw))throw ChatFailure("EXPORT_NAME")
        return MessageExportPreview(scope,chat.id,message.id,message.requestId,role.name,chat.title,message.content,sha256(bytes),bytes.size,message.createdAt,raw)
    }

    fun isValidBaseName(value:String):Boolean {
        val trimmed=value.trim()
        return value==trimmed&&trimmed.isNotEmpty()&&trimmed.length<=MAX_BASE_NAME&&!invalidName.containsMatchIn(trimmed)&&!trimmed.endsWith('.')
    }

    fun fileName(baseName:String,format:MessageExportFormat):String {
        if(!isValidBaseName(baseName))throw ChatFailure("EXPORT_NAME")
        return "$baseName.${format.extension}"
    }

    fun freeze(preview:MessageExportPreview,format:MessageExportFormat,baseName:String)=PreparedMessageExport(preview,format,fileName(baseName,format)).also(::validate)

    fun validate(file:PreparedMessageExport) {
        val bytes=file.bytes()
        if(file.fileName!=fileName(file.fileName.removeSuffix(".${file.format.extension}"),file.format))throw ChatFailure("EXPORT_NAME")
        if(bytes.isEmpty()||bytes.size!=file.byteCount||bytes.size>MAX_BYTES||sha256(bytes)!=file.preview.contentSha256)throw ChatFailure("EXPORT_MESSAGE")
    }

    fun verifySource(file:PreparedMessageExport,message:MessageEntity) {
        validate(file)
        val preview=file.preview
        if(message.id!=preview.messageId||message.chatId!=preview.chatId||message.requestId!=preview.requestId||message.kind!="assistant"||message.state!=MessageState.COMPLETE||message.createdAt!=preview.createdAt||message.content!=preview.content||sha256(message.content.toByteArray(Charsets.UTF_8))!=preview.contentSha256)throw ChatFailure("EXPORT_CHANGED")
    }
}

object MessageExportFiles {
    fun writeVerified(file:PreparedMessageExport,openOutput:()->OutputStream?,openInput:()->InputStream?) {
        MessageExports.validate(file)
        val expected=file.bytes()
        try {
            val output=openOutput()?:throw ChatFailure("EXPORT_WRITE")
            output.use {it.write(expected);it.flush()}
            val input=openInput()?:throw ChatFailure("EXPORT_WRITE")
            val restored=input.use {readBounded(it,expected.size)}
            if(!restored.contentEquals(expected))throw ChatFailure("EXPORT_WRITE")
        } catch(e:ChatFailure) {throw e}
          catch(_:Exception) {throw ChatFailure("EXPORT_WRITE")}
    }

    private fun readBounded(input:InputStream,expected:Int):ByteArray {
        val output=ByteArrayOutputStream(expected);val buffer=ByteArray(8192)
        while(true) {
            val remaining=expected+1-output.size()
            if(remaining<=0)throw ChatFailure("EXPORT_WRITE")
            val count=input.read(buffer,0,minOf(buffer.size,remaining))
            if(count<0)break
            if(count>0)output.write(buffer,0,count)
        }
        return output.toByteArray()
    }
}
