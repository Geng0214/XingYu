package com.ai.assistance.operit

import com.ai.assistance.operit.ui.main.ChatBubbles
import com.ai.assistance.operit.api.chat.RequestContext
import com.ai.assistance.operit.data.model.*
import org.junit.Assert.*
import org.junit.Test

class ChatBubblesTest {
    @Test fun paragraphsBecomeMessagesWithoutSplittingSentences() {
        assertEquals(listOf("你回来啦 😀","今天怎么样？\n我想听听。","慢慢说。"),ChatBubbles.split("你回来啦 😀\n\n今天怎么样？\n我想听听。\n\n慢慢说。"))
        assertEquals(listOf("完整的一句。另一句也在同一段。"),ChatBubbles.split("完整的一句。另一句也在同一段。"))
    }
    @Test fun streamingWaitsForBoundaryButCancelShowsPartial() {
        assertEquals(emptyList<String>(),ChatBubbles.visible("你回来",true,true))
        assertEquals(emptyList<String>(),ChatBubbles.visible("你回来\n",true,true))
        assertEquals(listOf("你回来啦"),ChatBubbles.visible("你回来啦\n\n",true,true))
        assertEquals(listOf("你回来啦"),ChatBubbles.visible("你回来啦\n\n今天过",true,true))
        assertEquals(listOf("你回来啦","今天过"),ChatBubbles.visible("你回来啦\n\n今天过",true,false))
        assertEquals(listOf("你回来啦\n\n今天过"),ChatBubbles.visible("你回来啦\n\n今天过",false,true))
    }
    @Test fun codeFencesAndCrlfDoNotCreateBrokenBubbles() {
        val code="```kotlin\nval a=1\n\nprintln(a)\n```"
        assertEquals(listOf("示例",code,"完成"),ChatBubbles.split("示例\n\n"+code+"\n\n完成"))
        assertTrue(ChatBubbles.visible("```\ncode\n\n",true,true).isEmpty())
        assertEquals(listOf("a","b"),ChatBubbles.split("a\r\n\r\nb"))
        assertTrue(ChatBubbles.split("\n \n").isEmpty())
    }
    @Test fun twentyStreamingPartitionsKeepOrderedPrefixes() {
        val text="第一条消息😀\n\n第二条消息👋\n\n第三条消息"
        val expected=ChatBubbles.split(text)
        for(step in 1..20) {
            for(end in step..text.length step step) {
                val visible=ChatBubbles.visible(text.take(end),true,true)
                assertEquals(expected.take(visible.size),visible)
            }
            assertEquals(expected,ChatBubbles.visible(text,true,false))
        }
    }
    @Test fun replyInstructionIsExplicitAndNoWallpaperEntersContext() {
        val short=RoleEntity("role","R","original",replyStyle="short")
        val chat=ChatEntity("chat",short.id,"config","title")
        val shortPrompt=RequestContext.build(short.scope(),short,chat,emptyList(),"hello").first().content
        assertTrue(shortPrompt.startsWith("original"));assertTrue(shortPrompt.contains("空行"));assertTrue(shortPrompt.contains("AI 身份"))
        val normal=short.copy(replyStyle="paragraph")
        assertEquals("original",RequestContext.build(normal.scope(),normal,chat,emptyList(),"hello").first().content)
        assertFalse(shortPrompt.contains("wallpaper"))
    }
}
