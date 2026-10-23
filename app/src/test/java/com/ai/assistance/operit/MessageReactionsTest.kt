package com.ai.assistance.operit

import com.ai.assistance.operit.data.model.*
import org.junit.Assert.*
import org.junit.Test

class MessageReactionsTest {
    private val now=1_000L

    @Test fun toggleAddsAndRemovesSameEmoji() {
        val one=Reactions.toggle(emptyList(),"m1","c1",Reactions.USER,"😊",now)
        assertEquals(1,one.size)
        assertEquals("😊",one.first().emoji)
        assertTrue(Reactions.toggle(one,"m1","c1",Reactions.USER,"😊",now).isEmpty())
    }

    @Test fun toggleStopsAtThreePerSide() {
        var list=Reactions.toggle(emptyList(),"m1","c1",Reactions.USER,"😊",now)
        list=Reactions.toggle(list,"m1","c1",Reactions.USER,"🥰",now)
        list=Reactions.toggle(list,"m1","c1",Reactions.USER,"😂",now)
        assertEquals(3,list.size)
        assertEquals(3,Reactions.toggle(list,"m1","c1",Reactions.USER,"😭",now).size)
        assertEquals(1,Reactions.toggle(list,"m1","c1",Reactions.ROLE,"😊",now).count {it.side==Reactions.ROLE})
    }

    @Test fun toggleRejectsUnknownSideAndEmoji() {
        assertThrows(IllegalArgumentException::class.java){Reactions.toggle(emptyList(),"m1","c1","system","😊",now)}
        assertThrows(IllegalArgumentException::class.java){Reactions.toggle(emptyList(),"m1","c1",Reactions.USER,"🐱",now)}
    }

    @Test fun roleReactionIsStablePerMessage() {
        assertEquals(Reactions.roleReactionFor("abc"),Reactions.roleReactionFor("abc"))
        assertTrue(Reactions.roleReactionFor("abc") in Reactions.ROLE_EMOJI)
        assertEquals(6,Reactions.ROLE_EMOJI.size)
    }

    @Test fun emojiWhitelistIsSharedWithStickers() {
        assertTrue(Reactions.ROLE_EMOJI.all {it in StickerAssets.EMOJI})
    }
}
