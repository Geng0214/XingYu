package com.ai.assistance.operit

import com.ai.assistance.operit.api.chat.*
import com.ai.assistance.operit.api.chat.llmprovider.AIService
import com.ai.assistance.operit.api.chat.llmprovider.ChatStreamEvent
import com.ai.assistance.operit.data.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class MemoryExtractionTest {
    private val role=RoleEntity("role-a","小岚","温柔陪伴",version=4,spaceId="space-a")
    private val config=ModelConfigData("cfg","主连接","OpenAI 兼容","https://example.invalid/v1/chat/completions","model-x",60,"ref",version=7)
    private fun frozen(marker:String="中文🙂")=FrozenMemoryExtraction("extract-a",role,config,
        MemoryExtractionSource(role.scope(),"chat-a","request-a","user-a","assistant-a","我喜欢热茶 $marker","记住啦 $marker",12),10)
    private fun rejected(block:()->Unit){try{block();fail("Expected invalid extraction output")}catch(_:IllegalArgumentException){}}

    @Test fun strictJsonAcceptsChineseEmojiAndRejectsUnknownOrExecutableShapes() {
        val text="""{"schemaVersion":1,"candidates":[{"kind":"preference","title":"饮品🙂","content":"用户喜欢热茶"},{"kind":"fictional_story","title":"月球家园","content":"故事中住在月球"}]}"""
        val result=MemoryExtractions.decode(text)
        assertEquals(2,result.size);assertEquals("饮品🙂",result.first().title)
        listOf(
            "```json\n$text\n```",
            """{"schemaVersion":2,"candidates":[{"kind":"preference","title":"a","content":"b"}]}""",
            """{"schemaVersion":1,"url":"https://evil.invalid","candidates":[{"kind":"preference","title":"a","content":"b"}]}""",
            """{"schemaVersion":1,"candidates":[{"kind":"preference","title":"a","content":"b","script":"run()"}]}""",
            """{"schemaVersion":1,"candidates":[]}""",
            """{"schemaVersion":1,"candidates":[${(0..4).joinToString(","){"{\"kind\":\"preference\",\"title\":\"$it\",\"content\":\"$it\"}"}}]}""",
            """{"schemaVersion":1,"candidates":[{"kind":"unknown","title":"a","content":"b"}]}"""
        ).forEach {bad->rejected {MemoryExtractions.decode(bad)}}
        rejected {MemoryExtractions.decode("x".repeat(MemoryExtractions.OUTPUT_LIMIT+1))}
    }

    @Test fun requestContainsOnlySelectedTurnFrozenModelAndNoTools() {
        val frozen=frozen("A-MARKER")
        val request=MemoryExtractions.request(frozen)
        assertEquals(frozen.role,request.role);assertEquals(frozen.config,request.config);assertEquals(frozen.source.scope,request.scope)
        assertEquals(2,request.turns.size);assertEquals("system",request.turns[0].role);assertEquals("user",request.turns[1].role)
        assertTrue(request.turns[1].content.contains("我喜欢热茶 A-MARKER"));assertTrue(request.turns[1].content.contains("记住啦 A-MARKER"))
        assertFalse(request.turns.joinToString {it.content}.contains("FOREIGN-HISTORY"));assertTrue(request.tools.isEmpty())
        assertTrue(request.worldbook.decisions.isEmpty());assertTrue(request.relationships.decisions.isEmpty());assertTrue(request.memories.decisions.isEmpty())
    }

    @Test fun serviceRequiresOneTerminalEventAndRejectsLateChunks()=runTest {
        val json="""{"schemaVersion":1,"candidates":[{"kind":"preference","title":"茶","content":"用户喜欢热茶"}]}"""
        val normal=object:AIService {
            override fun sendMessage(request:RequestSnapshot,key:String):Flow<String> = error("unused")
            override fun sendEvents(request:RequestSnapshot,key:String)=flow {emit(ChatStreamEvent.Text(json.take(20)));emit(ChatStreamEvent.Text(json.drop(20)));emit(ChatStreamEvent.RoundEnd)}
        }
        val result=MemoryCandidateService(normal).extract(frozen(),"synthetic")
        assertEquals("茶",result.candidates.single().title)
        val late=object:AIService {
            override fun sendMessage(request:RequestSnapshot,key:String):Flow<String> = error("unused")
            override fun sendEvents(request:RequestSnapshot,key:String)=flow {emit(ChatStreamEvent.RoundEnd);emit(ChatStreamEvent.Text(json))}
        }
        try {MemoryCandidateService(late).extract(frozen(),"synthetic");fail()}catch(e:ChatFailure){assertEquals("PROTOCOL",e.code)}
        val missing=object:AIService {
            override fun sendMessage(request:RequestSnapshot,key:String)=flow {emit(json)}
            override fun sendEvents(request:RequestSnapshot,key:String)=flow<ChatStreamEvent> {emit(ChatStreamEvent.Text(json))}
        }
        try {MemoryCandidateService(missing).extract(frozen(),"synthetic");fail()}catch(e:ChatFailure){assertEquals("TRUNCATED",e.code)}
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun cancellationCannotReturnLateCandidates()=runTest {
        val service=object:AIService {
            override fun sendMessage(request:RequestSnapshot,key:String):Flow<String> = error("unused")
            override fun sendEvents(request:RequestSnapshot,key:String)=flow<ChatStreamEvent> {emit(ChatStreamEvent.Text("{"));awaitCancellation()}
        }
        val job=async {MemoryCandidateService(service).extract(frozen(),"synthetic")}
        runCurrent();job.cancel();runCurrent()
        assertTrue(job.isCancelled)
    }

    @Test fun fiftyAlternatingSourcesAndTwentyConfigChangesKeepSnapshotsIsolated() {
        repeat(50){round->
            val side=if(round%2==0)"A" else "B";val other=if(side=="A")"B" else "A"
            val own=(0 until 10).joinToString("|"){"$side-$round-MARKER-$it"}
            val request=MemoryExtractions.request(frozen(own));val text=request.turns.last().content
            repeat(10){index->assertTrue(text.contains("$side-$round-MARKER-$index"));assertFalse(text.contains("$other-$round-MARKER-$index"))}
        }
        repeat(20){round->
            val frozen=frozen("round-$round")
            val changed=config.copy(model="changed-$round",version=config.version+1)
            val request=MemoryExtractions.request(frozen)
            assertEquals("model-x",request.config.model);assertEquals(7,request.config.version);assertNotEquals(changed,request.config)
        }
    }
}
