package com.ai.assistance.operit.api.chat

import com.ai.assistance.operit.data.model.*
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

class ReadingDocumentReader {
    fun file(input:InputStream,displayName:String,mimeType:String?):PreparedReadingDocument {
        val name=cleanName(displayName)
        val extension=name.substringAfterLast('.',"").lowercase()
        val normalizedMime=when {
            mimeType?.substringBefore(';')?.trim()?.lowercase() in setOf("text/markdown","text/x-markdown")->TextMaterials.MIME_MARKDOWN
            mimeType?.substringBefore(';')?.trim()?.lowercase()=="text/plain"->if(extension in setOf("md","markdown"))TextMaterials.MIME_MARKDOWN else TextMaterials.MIME_TEXT
            (mimeType.isNullOrBlank()||mimeType.substringBefore(';').trim().equals("application/octet-stream",true))&&extension in setOf("txt","md","markdown")->if(extension=="txt")TextMaterials.MIME_TEXT else TextMaterials.MIME_MARKDOWN
            else->throw ChatFailure("READING_TYPE")
        }
        if(extension !in setOf("txt","md","markdown"))throw ChatFailure("READING_TYPE")
        val text=normalize(strictUtf8(read(input))).removePrefix("\uFEFF")
        if(text.isBlank())throw ChatFailure("READING_EMPTY")
        if(text.length>ReadingDocuments.MAX_TEXT_CHARS)throw ChatFailure("READING_LIMIT")
        val chapters=chapters(text,normalizedMime)
        return PreparedReadingDocument(name,normalizedMime,text,chapters,ReadingDocuments.sha256(text)).also(PreparedReadingDocument::validate)
    }

    private fun read(input:InputStream):ByteArray {
        val output=ByteArrayOutputStream();val buffer=ByteArray(8192)
        while(true) {
            val remaining=ReadingDocuments.MAX_FILE_BYTES+1-output.size()
            if(remaining<=0)throw ChatFailure("READING_LIMIT")
            val count=input.read(buffer,0,minOf(buffer.size,remaining))
            if(count<0)break
            if(count>0)output.write(buffer,0,count)
        }
        return output.toByteArray()
    }

    private fun strictUtf8(bytes:ByteArray):String=try {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    }catch(_:Exception){throw ChatFailure("READING_ENCODING")}

    private fun cleanName(value:String):String {
        val name=value.substringAfterLast('/').substringAfterLast('\\').trim().replace(Regex("[\\p{Cntrl}]"),"")
        if(name.isBlank()||name.length>ReadingDocuments.MAX_NAME)throw ChatFailure("READING_NAME")
        return name
    }

    private fun normalize(value:String)=value.replace("\r\n","\n").replace('\r','\n').lines().joinToString("\n"){it.trimEnd()}.trim()

    private fun chapters(text:String,mime:String):List<ReadingChapter> {
        val heading=if(mime==TextMaterials.MIME_MARKDOWN)Regex("(?m)^#{1,6}[ \\t]+(.{1,120}?)[ \\t]*$")
            else Regex("(?im)^(第[0-9一二三四五六七八九十百千万零〇两]{1,12}[章节卷回][^\\n]{0,100}|chapter[ \\t]+[0-9ivxlcdm]+[^\\n]{0,100})$")
        val matches=heading.findAll(text).map {it.range.first to it.groupValues[1].trim().trimStart('#').trim()}.toList()
        val starts=buildList {
            if(matches.isEmpty()||matches.first().first>0)add(0 to if(matches.isEmpty())"正文" else "开篇")
            addAll(matches)
        }.distinctBy {it.first}
        val ranges=starts.mapIndexed {index,(start,title)->Triple(start,starts.getOrNull(index+1)?.first?:text.length,title.ifBlank {"第 ${index+1} 节"})}
        val result=mutableListOf<ReadingChapter>()
        ranges.forEach {(rangeStart,rangeEnd,title)->
            var start=rangeStart;var part=1
            while(start<rangeEnd) {
                var end=minOf(start+ReadingDocuments.MAX_CHAPTER_CHARS,rangeEnd)
                if(end<rangeEnd) {
                    val floor=start+ReadingDocuments.MAX_CHAPTER_CHARS/2
                    end=text.lastIndexOf("\n\n",end-1).takeIf {it>=floor}?.plus(2)
                        ?:text.lastIndexOf('\n',end-1).takeIf {it>=floor}?.plus(1)?:end
                }
                if(end<text.length&&end>start&&Character.isHighSurrogate(text[end-1])&&Character.isLowSurrogate(text[end]))end--
                val label=(if(part==1)title else "$title（续 $part）").take(ReadingDocuments.MAX_TITLE)
                result+=ReadingChapter(result.size,label,start,end)
                start=end;part++
                if(result.size>ReadingDocuments.MAX_CHAPTERS)throw ChatFailure("READING_CHAPTERS")
            }
        }
        return result
    }
}
