package com.ai.assistance.operit

import com.ai.assistance.operit.data.model.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

class MessageExportTest {
    private val role=RoleEntity("role","小岚","persona",spaceId="space")
    private val scope=role.scope()
    private val chat=ChatEntity("chat",role.id,"config","海边 / 对话")
    private val message=MessageEntity("assistant",chat.id,"request","assistant","# 回信\n你好🙂",MessageState.COMPLETE,1_700_000_000_000)

    @Test fun previewFreezesExactUtf8ReplyAndSafeSuggestedName() {
        val preview=MessageExports.prepare(scope,role,chat,message)
        assertEquals(message.content,preview.content)
        assertEquals(message.content.toByteArray().size,preview.byteCount)
        assertEquals(MessageExports.sha256(message.content.toByteArray()),preview.contentSha256)
        assertFalse(preview.suggestedBaseName.contains('/'))
    }

    @Test fun txtAndMarkdownDeclareExactMimeExtensionWithoutChangingContent() {
        val preview=MessageExports.prepare(scope,role,chat,message)
        val text=MessageExports.freeze(preview,MessageExportFormat.TEXT,"回复")
        val markdown=MessageExports.freeze(preview,MessageExportFormat.MARKDOWN,"回复")
        assertEquals("回复.txt",text.fileName);assertEquals("text/plain",text.mimeType)
        assertEquals("回复.md",markdown.fileName);assertEquals("text/markdown",markdown.mimeType)
        assertArrayEquals(message.content.toByteArray(),text.bytes());assertArrayEquals(text.bytes(),markdown.bytes())
    }

    @Test fun onlyCompleteNonEmptyAssistantRepliesCanBeExported() {
        rejected("EXPORT_MESSAGE"){MessageExports.prepare(scope,role,chat,message.copy(kind="user"))}
        rejected("EXPORT_MESSAGE"){MessageExports.prepare(scope,role,chat,message.copy(state=MessageState.FAILED))}
        rejected("EXPORT_MESSAGE"){MessageExports.prepare(scope,role,chat,message.copy(content="  "))}
        rejected("EXPORT_LIMIT"){MessageExports.prepare(scope,role,chat,message.copy(content="🙂".repeat(MessageExports.MAX_BYTES/4+1)))}
        rejected("ACCESS"){MessageExports.prepare(RoleAccessScope(role.id,"other"),role,chat,message)}
    }

    @Test fun invalidNamesAndChangedSourceAreRejected() {
        val preview=MessageExports.prepare(scope,role,chat,message)
        assertFalse(MessageExports.isValidBaseName("../reply"));assertFalse(MessageExports.isValidBaseName(" reply"))
        rejected("EXPORT_NAME"){MessageExports.freeze(preview,MessageExportFormat.TEXT,"../reply")}
        val file=MessageExports.freeze(preview,MessageExportFormat.MARKDOWN,"回复")
        rejected("EXPORT_CHANGED"){MessageExports.verifySource(file,message.copy(content="changed"))}
        rejected("EXPORT_CHANGED"){MessageExports.verifySource(file,message.copy(requestId="other"))}
    }

    @Test fun writeMustRoundTripExactBytesBeforeSuccess() {
        val file=MessageExports.freeze(MessageExports.prepare(scope,role,chat,message),MessageExportFormat.MARKDOWN,"回复")
        val saved=ByteArrayOutputStream()
        MessageExportFiles.writeVerified(file,{saved},{ByteArrayInputStream(saved.toByteArray())})
        assertArrayEquals(file.bytes(),saved.toByteArray())
        rejected("EXPORT_WRITE"){MessageExportFiles.writeVerified(file,{ByteArrayOutputStream()},{ByteArrayInputStream("short".toByteArray())})}
        rejected("EXPORT_WRITE"){MessageExportFiles.writeVerified(file,{null},{ByteArrayInputStream(file.bytes())})}
    }

    private fun rejected(code:String,block:()->Unit) {try {block();fail("Expected $code")}catch(e:ChatFailure){assertEquals(code,e.code)}}
}
