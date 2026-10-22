package com.ai.assistance.operit

import com.ai.assistance.operit.api.chat.RequestContext
import com.ai.assistance.operit.api.chat.llmprovider.OpenAiMessages
import com.ai.assistance.operit.data.model.*
import org.junit.Assert.*
import org.junit.Test

class StickerContextTest {
    private fun sticker(name:String,marker:Int,source:String?=null):PromptSticker {
        val bytes=byteArrayOf(0xff.toByte(),0xd8.toByte(),marker.toByte(),0xff.toByte(),0xd9.toByte())
        return PromptSticker(source,name,StickerAssets.MIME_TYPE,32,24,StickerAssets.sha256(bytes),bytes)
    }
    private fun stored(message:String,chat:String,request:String,sticker:PromptSticker)=MessageSticker(message,chat,request,sticker.displayName,sticker.mimeType,sticker.width,sticker.height,sticker.sha256,sticker.data)

    @Test fun stickerNameAlwaysReachesModelWhilePixelsRequireVision() {
        val role=RoleEntity("role","A","persona",spaceId="space");val chat=ChatEntity("chat",role.id,"cfg","chat")
        val current=sticker("开心",1,"asset")
        val textOnly=RequestContext.resolve(role.scope(),role,chat,emptyList(),"",emptyList(),currentSticker=current,includeVisuals=false).turns.last()
        assertTrue(textOnly.content.contains("用户发送了表情：开心"));assertTrue(textOnly.images.isEmpty())
        val visual=RequestContext.resolve(role.scope(),role,chat,emptyList(),"看看",emptyList(),currentSticker=current,includeVisuals=true).turns.last()
        assertEquals(1,visual.images.size)
        val encoded=OpenAiMessages.encode(listOf(visual)).getJSONObject(0).getJSONArray("content")
        assertTrue(encoded.getJSONObject(0).getString("text").contains("开心"))
        assertTrue(encoded.getJSONObject(1).getJSONObject("image_url").getString("url").startsWith("data:image/jpeg;base64,"))
    }

    @Test fun imageAndStickerHistoryShareThreeVisualSlots() {
        val role=RoleEntity("role","A","persona",spaceId="space");val chat=ChatEntity("chat",role.id,"cfg","chat")
        val history=buildList {repeat(5){n->add(MessageEntity("u$n",chat.id,"r$n","user","q$n",MessageState.COMPLETE,n*2L));add(MessageEntity("a$n",chat.id,"r$n","assistant","a$n",MessageState.COMPLETE,n*2L+1))}}
        val attachments=listOf(0,2,4).map {n->val s=sticker("image$n",n);MessageAttachment("u$n",chat.id,"r$n",s.mimeType,s.width,s.height,s.sha256,s.data)}
        val stickers=listOf(1,3).map {n->stored("u$n",chat.id,"r$n",sticker("表情$n",n))}
        val turns=RequestContext.resolve(role.scope(),role,chat,history,"next",emptyList(),attachments=attachments,stickers=stickers).turns
        assertEquals(3,turns.sumOf {it.images.size})
        assertTrue(turns.first {it.content.startsWith("q1")}.content.contains("表情1"))
        assertTrue(turns.first {it.content.startsWith("q3")}.content.contains("表情3"))
        assertTrue(turns.filter {it.role=="assistant"}.all {it.images.isEmpty()})
    }

    @Test fun tamperingCrossRequestAndMixedPayloadAreRejected() {
        val valid=sticker("开心",1)
        try {valid.copy(sha256="0".repeat(64)).validate();fail()}catch(_:IllegalArgumentException){}
        val role=RoleEntity("role","A","persona",spaceId="space");val chat=ChatEntity("chat",role.id,"cfg","chat")
        val user=MessageEntity("u",chat.id,"r","user","",MessageState.COMPLETE,1)
        val foreign=stored("u",chat.id,"other",valid)
        try {RequestContext.resolve(role.scope(),role,chat,listOf(user),"next",emptyList(),stickers=listOf(foreign));fail()}catch(e:ChatFailure){assertEquals("ACCESS",e.code)}
        val image=valid.promptImage()
        try {RequestContext.resolve(role.scope(),role,chat,emptyList(),"",emptyList(),currentImage=image,currentSticker=valid);fail()}catch(_:IllegalArgumentException){}
    }
}
