package com.ai.assistance.operit

import com.ai.assistance.operit.api.chat.RequestContext
import com.ai.assistance.operit.data.model.*
import org.junit.Assert.*
import org.junit.Test

class RequestContextTest {
    @Test fun fiftyAlternationsIncludeOnlyOwnCompleteTurnsAndFrozenOpening() {
        val roles=listOf(RoleEntity("A","same","persona-A"),RoleEntity("B","same","persona-B"))
        val chats=roles.map { ChatEntity("chat-"+it.id,it.id,"model","same",openingMessage="opening-"+it.id) }
        val histories=chats.map { chat -> (0..9).flatMap { i -> listOf(
            MessageEntity("u$i",chat.id,"req$i","user",chat.roleId+"-marker-"+i,MessageState.COMPLETE,i*2L),
            MessageEntity("a$i",chat.id,"req$i","assistant",chat.roleId+"-answer-"+i,MessageState.COMPLETE,i*2L+1)) } }
        repeat(50) { i ->
            val n=i%2;val other=1-n
            val role=roles[n].copy(openingMessage="new-opening")
            val incomplete=listOf(MessageEntity("bad-u",chats[n].id,"bad","user","cancelled-input",MessageState.COMPLETE,90),
                MessageEntity("bad-a",chats[n].id,"bad","assistant","cancelled-answer",MessageState.CANCELLED,91))
            val turns=RequestContext.build(role.scope(),role,chats[n],histories[n]+incomplete,"probe")
            assertEquals(23,turns.size)
            assertTrue(turns.first().content.startsWith("persona-"+role.id))
            assertEquals("opening-"+role.id,turns[1].content)
            assertFalse(turns.any { it.content.contains(roles[other].id+"-") || it.content.contains("cancelled") || it.content=="new-opening" })
            repeat(10) { marker -> assertTrue(turns.any { it.content==role.id+"-marker-"+marker }) }
            listOf(
                { RequestContext.build(roles[other].scope(),role,chats[n],histories[n],"probe") },
                { RequestContext.build(role.scope(),role,chats[other],histories[other],"probe") },
                { RequestContext.build(role.scope(),role,chats[n],histories[n]+histories[other],"probe") }
            ).forEach { call -> try { call();fail("Wrong ownership accepted") } catch(e:ChatFailure) { assertEquals("ACCESS",e.code) } }
        }
    }
}
