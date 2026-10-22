package com.ai.assistance.operit

import com.ai.assistance.operit.api.chat.RequestContext
import com.ai.assistance.operit.data.model.*
import org.junit.Assert.*
import org.junit.Test

class ReadingContextTest {
    @Test fun onlyConfirmedReadingPassageEntersCurrentRequest() {
        val role=RoleEntity("role","小青","温柔陪伴",spaceId="space")
        val chat=ChatEntity("chat",role.id,"cfg","伴读")
        val visible="她在窗边看雨。"
        val hidden="PRIVATE-CHAPTER-NOT-SELECTED"
        val text=visible+hidden
        val chapter=ReadingChapter(0,"雨夜",0,text.length)
        val document=ReadingDocument("11111111-1111-1111-1111-111111111111",role.id,role.spaceId,"故事.md",TextMaterials.MIME_MARKDOWN,text,ReadingDocuments.encodeChapters(listOf(chapter)),sha256=ReadingDocuments.sha256(text))
        val passage=ReadingDocuments.passage(document,0,0,visible.length)
        val turns=RequestContext.resolve(role.scope(),role,chat,emptyList(),"一起读这一段",emptyList(),currentMaterial=passage).turns
        val payload=turns.last().modelText()
        assertTrue(payload.contains(visible));assertTrue(payload.contains("雨夜"));assertTrue(payload.contains("字符 0–${visible.length}"))
        assertFalse(payload.contains(hidden));assertFalse(payload.contains(document.id))
    }

    @Test fun forgedReadingReferenceIsRejectedByMaterialValidation() {
        val text="片段"
        val material=PromptMaterial(TextMaterials.KIND_READING,"故事 · 第一章","reading:bad:0:2",TextMaterials.MIME_TEXT,text,text.length,false,TextMaterials.sha256(text))
        try {material.validate();fail("Expected invalid reference")}catch(_:IllegalArgumentException){}
    }
}
