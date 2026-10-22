package com.ai.assistance.operit

import com.ai.assistance.operit.api.chat.*
import com.ai.assistance.operit.api.chat.llmprovider.AIService
import com.ai.assistance.operit.api.chat.llmprovider.ChatStreamEvent
import com.ai.assistance.operit.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class DiaryTest {
    private val role=RoleEntity("role-a","小岚","温柔但不虚构现实经历",version=4,spaceId="space-a")
    private val config=ModelConfigData("cfg","主连接","OpenAI 兼容","https://example.invalid/v1/chat/completions","model-x",60,"ref",version=7)
    private fun frozen(notebook:String="role_diary",prompt:String="写下今天一起看海的虚构片段🙂")=FrozenDiaryGeneration("diary-request",role,config,notebook,prompt,10)
    private fun rejected(block:()->Unit){try{block();fail("Expected invalid diary data")}catch(_:IllegalArgumentException){}}

    @Test fun strictDraftProtocolAcceptsChineseEmojiAndRejectsUnknownExecutableOrOversizedShapes() {
        val text="""{"schemaVersion":1,"title":"海边晚风🙂","content":"在虚构故事里一起看了海。","mood":"happy"}"""
        val draft=DiaryGenerations.decode(text,"role_diary");assertEquals("海边晚风🙂",draft.title)
        listOf(
            "```json\n$text\n```",
            """{"schemaVersion":2,"title":"a","content":"b","mood":"calm"}""",
            """{"schemaVersion":1,"title":"a","content":"b","mood":"calm","url":"https://evil.invalid"}""",
            """{"schemaVersion":1,"title":"a","content":"b","mood":"calm","script":"run()"}""",
            """{"schemaVersion":1,"title":"a","content":"b","mood":"unknown"}"""
        ).forEach {bad->rejected {DiaryGenerations.decode(bad,"heart")}}
        rejected {DiaryGenerations.decode("x".repeat(Diaries.OUTPUT_LIMIT+1),"heart")}
    }

    @Test fun independentRequestFreezesOnlyRoleModelAndExplicitTopicWithoutToolsOrPrivateContext() {
        val request=DiaryGenerations.request(frozen())
        assertEquals(role,request.role);assertEquals(config,request.config);assertEquals(role.scope(),request.scope)
        assertEquals(2,request.turns.size);assertTrue(request.turns.last().content.contains("一起看海"));assertTrue(request.turns.last().content.contains(role.prompt))
        val all=request.turns.joinToString("\n"){it.content}
        assertFalse(all.contains("FOREIGN-CHAT"));assertFalse(all.contains("MEMORY-PRIVATE"));assertFalse(all.contains("WORLDBOOK-PRIVATE"))
        assertTrue(request.tools.isEmpty());assertTrue(request.worldbook.decisions.isEmpty());assertTrue(request.relationships.decisions.isEmpty());assertTrue(request.memories.decisions.isEmpty())
    }

    @Test fun serviceRequiresSingleTerminalEventAndRejectsLateChunks()=runTest {
        val json="""{"schemaVersion":1,"title":"海边","content":"一起看海。","mood":"calm"}"""
        val normal=object:AIService {
            override fun sendMessage(request:RequestSnapshot,key:String):Flow<String> = error("unused")
            override fun sendEvents(request:RequestSnapshot,key:String)=flow {emit(ChatStreamEvent.Text(json.take(18)));emit(ChatStreamEvent.Text(json.drop(18)));emit(ChatStreamEvent.RoundEnd)}
        }
        val result=DiaryGenerationService(normal).generate(frozen(),"key");assertEquals("海边",result.draft.title)
        val missing=object:AIService {override fun sendMessage(request:RequestSnapshot,key:String)=flow<String>{};override fun sendEvents(request:RequestSnapshot,key:String)=flow {emit(ChatStreamEvent.Text(json))}}
        try {DiaryGenerationService(missing).generate(frozen(),"key");fail()}catch(e:ChatFailure){assertEquals("TRUNCATED",e.code)}
        val late=object:AIService {override fun sendMessage(request:RequestSnapshot,key:String)=flow<String>{};override fun sendEvents(request:RequestSnapshot,key:String)=flow {emit(ChatStreamEvent.Text(json));emit(ChatStreamEvent.RoundEnd);emit(ChatStreamEvent.Text("late"))}}
        try {DiaryGenerationService(late).generate(frozen(),"key");fail()}catch(e:ChatFailure){assertEquals("PROTOCOL",e.code)}
    }

    @Test fun storedValidationDistinguishesManualAndGeneratedSources() {
        val manual=DiaryEntry("a",role.id,role.spaceId,"heart","一件心事","今天有点紧张。","anxious","manual",occurredAt=100,createdAt=100,updatedAt=100)
        Diaries.validateStored(manual)
        val generated=manual.copy(id="b",notebook="role_diary",source="generated",sourceModel="OpenAI 兼容 · model-x",sourceConfigVersion=7,generationPrompt="写下今天的虚构片段")
        Diaries.validateStored(generated)
        rejected {Diaries.validateStored(manual.copy(sourceModel="secret"))}
        rejected {Diaries.validateStored(generated.copy(generationPrompt=null))}
        rejected {Diaries.validate("heart"," bad ","正文","calm",100)}
    }

    @Test fun exportShowsProvenanceVerifiesFrozenSourceAndRejectsShortWrite() {
        val entry=DiaryEntry("a",role.id,role.spaceId,"role_diary","月光厨房","在虚构故事里做晚饭。","happy","generated","OpenAI 兼容 · model-x",7,"写一篇厨房日记",100,2,100,120)
        val preview=DiaryExports.prepare(role.scope(),role,entry)
        assertTrue(preview.content.contains("模型生成后确认"));assertTrue(preview.content.contains("不代表模型拥有真实经历"));assertFalse(preview.content.contains(entry.generationPrompt!!))
        val file=DiaryExports.freeze(preview,"月光厨房-导出")
        val output=ByteArrayOutputStream();DiaryExportFiles.writeVerified(file,{output},{ByteArrayInputStream(output.toByteArray())})
        assertArrayEquals(file.bytes(),output.toByteArray());DiaryExports.verifySource(file,entry)
        try {DiaryExports.verifySource(file,entry.copy(version=3));fail()}catch(e:ChatFailure){assertEquals("DIARY_CHANGED",e.code)}
        try {DiaryExportFiles.writeVerified(file,{ByteArrayOutputStream()},{ByteArrayInputStream(byteArrayOf(1))});fail()}catch(e:ChatFailure){assertEquals("EXPORT_WRITE",e.code)}
    }
}
