package com.ai.assistance.operit

import com.ai.assistance.operit.api.chat.RequestContext
import com.ai.assistance.operit.api.chat.llmprovider.OpenAiMessages
import com.ai.assistance.operit.data.model.*
import org.junit.Assert.*
import org.junit.Test

class VisionMessageTest {
    private fun image(marker:Int):PromptImage {
        val bytes=byteArrayOf(0xff.toByte(),0xd8.toByte(),marker.toByte(),0xff.toByte(),0xd9.toByte())
        return PromptImage(MessageImages.MIME_TYPE,32,24,MessageImages.sha256(bytes),bytes)
    }
    @Test fun openAiPayloadContainsBoundedImagePartInsteadOfLocalPath() {
        val image=image(7)
        val messages=OpenAiMessages.encode(listOf(PromptTurn("user","请看这张图",listOf(image))))
        val content=messages.getJSONObject(0).getJSONArray("content")
        assertEquals("text",content.getJSONObject(0).getString("type"))
        val url=content.getJSONObject(1).getJSONObject("image_url").getString("url")
        assertTrue(url.startsWith("data:image/jpeg;base64,"));assertFalse(url.contains("file:"));assertFalse(url.contains("content:"))
        OpenAiMessages.checkSize(messages)
    }
    @Test fun contextKeepsCurrentAndOnlyThreeRecentCompletedImages() {
        val role=RoleEntity("role","A","persona",spaceId="space")
        val chat=ChatEntity("chat",role.id,"config","chat")
        val history=buildList {
            repeat(5){n->add(MessageEntity("u$n",chat.id,"r$n","user","",MessageState.COMPLETE,n*2L));add(MessageEntity("a$n",chat.id,"r$n","assistant","ok$n",MessageState.COMPLETE,n*2L+1))}
        }
        val attachments=(0 until 5).map {n->val i=image(n);MessageAttachment("u$n",chat.id,"r$n",i.mimeType,i.width,i.height,i.sha256,i.data)}
        val current=image(9)
        val turns=RequestContext.resolve(role.scope(),role,chat,history,"",emptyList(),emptyList(),attachments,current).turns
        assertEquals(4,turns.sumOf {it.images.size})
        assertArrayEquals(current.data,turns.last().images.single().data)
        assertEquals(2,turns.count {it.content=="[较早图片未再次上传]"})
        assertTrue(turns.filter {it.role=="assistant"}.all {it.images.isEmpty()})
    }
    @Test fun tamperingAndCrossRequestAttachmentAreRejected() {
        val valid=image(1)
        try {valid.copy(sha256="0".repeat(64)).validate();fail()}catch(_:IllegalArgumentException){}
        val role=RoleEntity("role","A","persona",spaceId="space");val chat=ChatEntity("chat",role.id,"config","chat")
        val user=MessageEntity("u",chat.id,"r","user","text",MessageState.COMPLETE,1)
        val bad=MessageAttachment("u",chat.id,"other",valid.mimeType,valid.width,valid.height,valid.sha256,valid.data)
        try {RequestContext.resolve(role.scope(),role,chat,listOf(user),"next",emptyList(),attachments=listOf(bad));fail()}
        catch(e:ChatFailure){assertEquals("ACCESS",e.code)}
    }
}
