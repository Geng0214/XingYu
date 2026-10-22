package com.ai.assistance.operit

import com.ai.assistance.operit.api.chat.ReadingDocumentReader
import com.ai.assistance.operit.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class ReadingDocumentReaderTest {
    private val reader=ReadingDocumentReader()

    @Test fun markdownHeadingsBecomeContinuousTraceableChapters() {
        val text="# 第一章\n你好🙂\n\n## 第二章\n再见"
        val result=reader.file(ByteArrayInputStream(text.toByteArray()),"story.md","text/plain")
        assertEquals(TextMaterials.MIME_MARKDOWN,result.mimeType);assertEquals(2,result.chapters.size)
        assertEquals("第一章",result.chapters[0].title);assertEquals("第二章",result.chapters[1].title)
        assertEquals(0,result.chapters.first().start);assertEquals(result.text.length,result.chapters.last().end)
        assertEquals(result.chapters[0].end,result.chapters[1].start);result.validate()
    }

    @Test fun plainTextChaptersAndLongSectionsAreBoundedWithoutDroppingText() {
        val text="第一章 出发\n"+"甲".repeat(ReadingDocuments.MAX_CHAPTER_CHARS+50)+"\n第二章 回家\n结束"
        val result=reader.file(ByteArrayInputStream(text.toByteArray()),"novel.txt","text/plain")
        assertTrue(result.chapters.size>=3)
        assertEquals(result.text,result.chapters.joinToString(""){result.text.substring(it.start,it.end)})
        assertTrue(result.chapters.all {it.end-it.start<=ReadingDocuments.MAX_CHAPTER_CHARS})
    }

    @Test fun passageContainsOnlyExplicitWindowAndKeepsSourcePosition() {
        val hidden="PRIVATE-WHOLE-BOOK-TAIL"
        val text="# 第一章\n眼前的海\n"+hidden
        val prepared=reader.file(ByteArrayInputStream(text.toByteArray()),"story.md","text/markdown")
        val document=ReadingDocument("11111111-1111-1111-1111-111111111111","role","space",prepared.displayName,prepared.mimeType,prepared.text,ReadingDocuments.encodeChapters(prepared.chapters),sha256=prepared.sha256)
        val start=text.indexOf("眼前");val end=start+"眼前的海".length
        val passage=ReadingDocuments.passage(document,0,start,end)
        assertEquals("眼前的海",passage.text);assertFalse(passage.modelText().contains(hidden))
        val reference=ReadingDocuments.parseReference(requireNotNull(passage.sourceUrl))
        assertEquals(document.id,reference.documentId);assertEquals(start,reference.start);assertEquals(end,reference.end)
        assertTrue(passage.modelText().contains("字符 $start–$end"))
    }

    @Test fun pageBoundaryNeverCutsEmojiSurrogatePair() {
        val text="甲".repeat(ReadingDocuments.PASSAGE_CHARS-1)+"🙂尾"
        val end=ReadingDocuments.pageEnd(text,0,text.length)
        assertFalse(Character.isHighSurrogate(text[end-1]))
        assertEquals(ReadingDocuments.PASSAGE_CHARS-1,end)
    }

    @Test fun invalidEncodingWrongTypeOversizeAndTooManyChaptersAreRejected() {
        rejected("READING_ENCODING"){reader.file(ByteArrayInputStream(byteArrayOf(0xc3.toByte(),0x28)),"bad.txt","text/plain")}
        rejected("READING_TYPE"){reader.file(ByteArrayInputStream("x".toByteArray()),"book.pdf","application/pdf")}
        rejected("READING_LIMIT"){reader.file(ByteArrayInputStream(ByteArray(ReadingDocuments.MAX_FILE_BYTES+1)),"large.txt","text/plain")}
        val headings=(0..ReadingDocuments.MAX_CHAPTERS).joinToString("\n"){"# 第 $it 章\n内容"}
        rejected("READING_CHAPTERS"){reader.file(ByteArrayInputStream(headings.toByteArray()),"many.md","text/markdown")}
    }

    private fun rejected(code:String,block:()->Unit) {try {block();fail("Expected $code")}catch(e:ChatFailure){assertEquals(code,e.code)}}
}
