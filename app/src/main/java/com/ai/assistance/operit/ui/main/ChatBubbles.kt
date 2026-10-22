package com.ai.assistance.operit.ui.main

import com.ai.assistance.operit.data.model.MessageParagraphs

/** While a short reply is streaming, reveal complete paragraphs as bubbles. */
object ChatBubbles {
    fun split(text:String):List<String> = MessageParagraphs.split(text)
    fun visible(text:String,short:Boolean,generating:Boolean):List<String> = when {
        text.isBlank()->emptyList()
        !short->listOf(text)
        else->MessageParagraphs.visible(text,generating)
    }
}
