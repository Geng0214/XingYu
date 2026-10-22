package com.ai.assistance.operit.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

data class ReadingChapter(val index:Int,val title:String,val start:Int,val end:Int)
data class ReadingReference(val documentId:String,val start:Int,val end:Int)

data class PreparedReadingDocument(
    val displayName:String,
    val mimeType:String,
    val text:String,
    val chapters:List<ReadingChapter>,
    val sha256:String
) {
    fun validate()=ReadingDocuments.validatePrepared(this)
}

@Entity(
    tableName="reading_documents",
    foreignKeys=[ForeignKey(entity=RoleEntity::class,parentColumns=["id"],childColumns=["roleId"],onDelete=ForeignKey.CASCADE)],
    indices=[Index("roleId"),Index(value=["roleId","spaceId"])]
)
data class ReadingDocument(
    @PrimaryKey val id:String,
    val roleId:String,
    val spaceId:String,
    val displayName:String,
    val mimeType:String,
    val text:String,
    val chaptersJson:String,
    val currentChapter:Int=0,
    val currentOffset:Int=0,
    val sha256:String,
    val version:Long=1,
    val createdAt:Long=System.currentTimeMillis(),
    val updatedAt:Long=createdAt
) {
    fun chapters():List<ReadingChapter> = ReadingDocuments.decodeChapters(chaptersJson)
}

object ReadingDocuments {
    const val MAX_FILE_BYTES=1024*1024
    const val MAX_TEXT_CHARS=750_000
    const val MAX_NAME=120
    const val MAX_TITLE=120
    const val MAX_CHAPTERS=512
    const val MAX_CHAPTER_CHARS=16_000
    const val MAX_DOCUMENTS_PER_ROLE=16
    const val PASSAGE_CHARS=4_000
    private val referencePattern=Regex("^reading:([0-9a-fA-F-]{36}):([0-9]+):([0-9]+)$")

    fun sha256(text:String)=MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString(""){"%02x".format(it)}

    fun encodeChapters(chapters:List<ReadingChapter>):String=JSONArray().also {array->chapters.forEach {chapter->
        array.put(JSONObject().put("index",chapter.index).put("title",chapter.title).put("start",chapter.start).put("end",chapter.end))
    }}.toString()

    fun decodeChapters(value:String):List<ReadingChapter> {
        require(value.toByteArray().size<=256*1024)
        val array=JSONArray(value)
        require(array.length() in 1..MAX_CHAPTERS)
        return List(array.length()) {index->
            val item=array.getJSONObject(index)
            require(item.keys().asSequence().toSet()==setOf("index","title","start","end"))
            ReadingChapter(item.getInt("index"),item.getString("title"),item.getInt("start"),item.getInt("end"))
        }
    }

    fun validatePrepared(document:PreparedReadingDocument) {
        require(document.displayName.isNotBlank()&&document.displayName.length<=MAX_NAME&&document.displayName.none(Char::isISOControl))
        require(document.mimeType in setOf(TextMaterials.MIME_TEXT,TextMaterials.MIME_MARKDOWN))
        require(document.text.isNotBlank()&&document.text.length<=MAX_TEXT_CHARS&&document.text.toByteArray().size<=MAX_FILE_BYTES)
        require('\u0000' !in document.text&&document.sha256==sha256(document.text))
        validateChapters(document.chapters,document.text.length)
    }

    fun validateStored(document:ReadingDocument) {
        require(document.id.isNotBlank()&&document.roleId.isNotBlank()&&document.spaceId.isNotBlank())
        require(document.version>0&&document.createdAt>=0&&document.updatedAt>=document.createdAt)
        val chapters=document.chapters()
        validatePrepared(PreparedReadingDocument(document.displayName,document.mimeType,document.text,chapters,document.sha256))
        require(document.currentChapter in chapters.indices)
        require(document.currentOffset in chapters[document.currentChapter].start..chapters[document.currentChapter].end)
    }

    private fun validateChapters(chapters:List<ReadingChapter>,textLength:Int) {
        require(chapters.size in 1..MAX_CHAPTERS)
        var previous=0
        chapters.forEachIndexed {index,chapter->
            require(chapter.index==index&&chapter.title.isNotBlank()&&chapter.title.length<=MAX_TITLE&&chapter.title.none(Char::isISOControl))
            require(chapter.start==previous&&chapter.end in chapter.start+1..textLength)
            previous=chapter.end
        }
        require(previous==textLength)
    }

    fun reference(documentId:String,start:Int,end:Int):String {
        require(Regex("^[0-9a-fA-F-]{36}$").matches(documentId)&&start>=0&&end>start)
        return "reading:$documentId:$start:$end"
    }

    fun parseReference(value:String):ReadingReference {
        val match=referencePattern.matchEntire(value)?:throw IllegalArgumentException("Invalid reading reference")
        return ReadingReference(match.groupValues[1],match.groupValues[2].toInt(),match.groupValues[3].toInt()).also {require(it.start>=0&&it.end>it.start)}
    }

    fun passage(document:ReadingDocument,chapterIndex:Int,start:Int,end:Int):PromptMaterial {
        validateStored(document)
        val chapter=document.chapters()[chapterIndex]
        require(start in chapter.start until chapter.end&&end in start+1..chapter.end&&end-start<=PASSAGE_CHARS)
        val text=document.text.substring(start,end)
        require(text.isNotBlank())
        val suffix=" · ${chapter.title}"
        val book=document.displayName.take((TextMaterials.MAX_NAME-suffix.length).coerceAtLeast(1))
        return PromptMaterial(TextMaterials.KIND_READING,book+suffix,reference(document.id,start,end),document.mimeType,text,text.length,false,TextMaterials.sha256(text)).also(PromptMaterial::validate)
    }

    fun previousPageStart(text:String,start:Int,chapterStart:Int):Int {
        require(chapterStart in 0 until start&&start<=text.length)
        var target=(start-PASSAGE_CHARS).coerceAtLeast(chapterStart)
        // Moving backwards by UTF-16 units must not land inside an emoji surrogate pair.
        if(target>chapterStart&&Character.isLowSurrogate(text[target])&&Character.isHighSurrogate(text[target-1]))target--
        return target
    }

    fun pageEnd(text:String,start:Int,chapterEnd:Int):Int {
        require(start in 0 until chapterEnd&&chapterEnd<=text.length)
        var end=minOf(start+PASSAGE_CHARS,chapterEnd)
        if(end<chapterEnd) {
            val floor=start+PASSAGE_CHARS/2
            val paragraph=text.lastIndexOf("\n\n",end-1).takeIf {it>=floor}
            val line=text.lastIndexOf('\n',end-1).takeIf {it>=floor}
            end=(paragraph?.plus(2)?:line?.plus(1)?:end)
        }
        if(end<text.length&&end>start&&Character.isHighSurrogate(text[end-1])&&Character.isLowSurrogate(text[end]))end--
        return end
    }
}
