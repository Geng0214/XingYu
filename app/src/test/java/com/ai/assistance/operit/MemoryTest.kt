package com.ai.assistance.operit

import com.ai.assistance.operit.api.chat.RequestContext
import com.ai.assistance.operit.data.model.*
import org.junit.Assert.*
import org.junit.Test

class MemoryTest {
    private val role=RoleEntity("role-a","角色","基础人设",spaceId="space-a",replyStyle="paragraph")
    private val chat=ChatEntity("chat-a",role.id,"model","会话")
    private fun memory(id:String="memory",kind:String="preference",content:String="喜欢热咖啡",enabled:Boolean=true,priority:Int=50,updatedAt:Long=10)=
        MemoryEntry(id,role.id,role.spaceId,kind,"标题 $id",content,enabled,priority,version=1,createdAt=1,updatedAt=updatedAt)
    private fun resolve(r:RoleEntity=role,memories:List<MemoryEntry>,text:String="你还记得吗")=
        RequestContext.resolve(r.scope(),r,chat,emptyList(),text,emptyList(),emptyList(),memories=memories)
    private fun rejected(code:String,block:()->Unit){try{block();fail("Expected $code")}catch(e:ChatFailure){assertEquals(code,e.code)}}

    @Test fun fourKindsAreVisibleAndFictionalStoryIsExplicitlySeparatedFromReality() {
        val entries=listOf(
            memory("preference","preference","用户喜欢茶"),
            memory("real","real_experience","用户去过海边"),
            memory("plan","current_plan","用户正在准备考试"),
            memory("story","fictional_story","故事中一起住在月球")
        )
        val result=resolve(memories=entries)
        listOf("用户偏好","现实经历","当前计划","虚构剧情").forEach {assertTrue(result.systemInstruction.contains(it))}
        assertTrue(result.systemInstruction.contains("虚构剧情”不是现实事实"))
        assertEquals(4,result.memories.decisions.count {it.reason=="included"})
    }

    @Test fun fiftyAlternatingRolesNeverInjectTheOtherRolesTenMarkers() {
        val b=role.copy(id="role-b",spaceId="space-b");val cb=chat.copy(id="chat-b",roleId=b.id)
        repeat(50){round->
            val current=if(round%2==0)role else b;val currentChat=if(round%2==0)chat else cb;val foreign=if(round%2==0)b else role
            val entries=(0 until 10).map {memory("$it",content="${current.id}-MEMORY-$it").copy(roleId=current.id,spaceId=current.spaceId)}
            val actual=RequestContext.resolve(current.scope(),current,currentChat,emptyList(),"继续",emptyList(),emptyList(),memories=entries)
            repeat(10){assertTrue(actual.systemInstruction.contains("${current.id}-MEMORY-$it"));assertFalse(actual.systemInstruction.contains("${foreign.id}-MEMORY-$it"))}
            rejected("ACCESS"){RequestContext.resolve(current.scope(),current,currentChat,emptyList(),"继续",emptyList(),emptyList(),memories=entries+memory("foreign").copy(spaceId="wrong"))}
        }
    }

    @Test fun disabledBudgetPriorityAndStableOrderingMatchTheActualPrompt() {
        val entries=listOf(
            memory("large",content="高".repeat(2000),priority=100,updatedAt=4),
            memory("budget",content="满".repeat(2000),priority=99,updatedAt=5),
            memory("newer",content="新",priority=10,updatedAt=20),
            memory("older",content="旧",priority=10,updatedAt=10),
            memory("off",enabled=false,priority=100)
        )
        val result=resolve(memories=entries)
        assertEquals("included",result.memories.decisions.single {it.id=="large"}.reason)
        assertEquals("budget",result.memories.decisions.single {it.id=="budget"}.reason)
        assertEquals("disabled",result.memories.decisions.single {it.id=="off"}.reason)
        assertTrue(result.memories.characters<=Memories.BUDGET)
        assertEquals(result.memories.characters,result.memories.decisions.sumOf {it.characters})
        assertTrue(result.systemInstruction.indexOf("标题 newer")<result.systemInstruction.indexOf("标题 older"))
        assertEquals(result.memories,resolve(memories=entries.reversed()).memories)
    }

    @Test fun rawModeExcludesMemoryAndFrozenResolutionSurvivesTwentyEdits() {
        val raw=role.copy(conversationMode="raw")
        val rawResult=resolve(raw,listOf(memory(content="PRIVATE-MEMORY")))
        assertEquals("",rawResult.systemInstruction);assertEquals("raw_mode",rawResult.memories.decisions.single().reason)
        assertEquals(listOf(PromptTurn("user","你还记得吗")),rawResult.turns)
        repeat(20){round->
            val original=memory("m-$round",content="原始-$round",enabled=true,priority=round)
            val frozen=resolve(memories=listOf(original))
            val changed=original.copy(content="修改-$round",enabled=round%2==0,version=2,updatedAt=original.updatedAt+1)
            val retry=resolve(memories=listOf(changed))
            assertTrue(frozen.systemInstruction.contains("原始-$round"));assertFalse(frozen.systemInstruction.contains("修改-$round"))
            assertEquals(1L,frozen.memories.decisions.single().version);assertEquals(2L,retry.memories.decisions.single().version)
            assertEquals(round%2==0,retry.systemInstruction.contains("修改-$round"))
        }
    }

    @Test fun invalidFieldsLimitsAndPublicRoleTemplatesRejectOrExcludeMemory() {
        fun invalid(block:()->Unit){try{block();fail("Invalid memory accepted")}catch(_:IllegalArgumentException){}}
        invalid{Memories.validate("unknown","标题","内容",50)}
        invalid{Memories.validate("preference","","内容",50)}
        invalid{Memories.validate("preference","标题","x".repeat(Memories.CONTENT_LIMIT+1),50)}
        invalid{Memories.validate("preference","标题","\uD83D",50)}
        invalid{Memories.validate("preference","标题","内容",101)}
        invalid{Memories.select(role.scope(),List(Memories.MAX_ENTRIES+1){memory("$it")})}
        val json=RoleTemplateCodec.encode(RoleTemplateCodec.fromRole(role))
        assertFalse(json.contains("memory"));assertFalse(json.contains("喜欢热咖啡"))
    }

    @Test fun localRecallIsBoundedExplainableAndNeverSelectsExpiredOrForgottenEntries() {
        val entries=listOf(
            memory("tea",content="用户喜欢茉莉花茶").copy(recallMode="auto"),
            memory("game",content="用户最近在玩象棋").copy(recallMode="auto"),
            memory("always",content="称呼用户为小雨").copy(recallMode="always"),
            memory("expired",content="明天去露营").copy(recallMode="always",expiresAt=99),
            memory("forgotten",content="不再使用的旧住址").copy(enabled=false,recallMode="always",forgottenAt=90,updatedAt=90)
        )
        val result=Memories.select(role.scope(),entries,query="我们泡一杯茉莉花茶吧",now=100)
        assertEquals("included",result.decisions.single {it.id=="tea"}.reason)
        assertTrue(result.decisions.single {it.id=="tea"}.matchPercent>=Memories.AUTO_MATCH_PERCENT)
        assertEquals("no_match",result.decisions.single {it.id=="game"}.reason)
        assertEquals("included",result.decisions.single {it.id=="always"}.reason)
        assertEquals("expired",result.decisions.single {it.id=="expired"}.reason)
        assertEquals("forgotten",result.decisions.single {it.id=="forgotten"}.reason)
        assertTrue(result.instruction.contains("茉莉花茶"));assertFalse(result.instruction.contains("象棋"));assertFalse(result.instruction.contains("旧住址"))
    }

    @Test fun conflictCandidatesStayInsideTheRoleAndRequireStrongSimilarity() {
        val entries=listOf(
            memory("old",content="用户最喜欢茉莉花茶").copy(title="饮品偏好",recallMode="auto"),
            memory("other",content="用户周末常去爬山").copy(title="周末计划",recallMode="auto"),
            memory("foreign",content="用户最喜欢茉莉花茶").copy(roleId="role-b",spaceId="space-b",recallMode="auto")
        )
        val own=Memories.conflicts(role.scope(),entries.take(2),"preference","饮品偏好","用户改为最喜欢乌龙茶",now=100)
        assertEquals(listOf("old"),own.map {it.id});assertEquals(100,own.single().matchPercent)
        assertTrue(Memories.conflicts(role.scope(),entries.take(2),"current_plan","旅行","下个月去海边",now=100).isEmpty())
        rejected("ACCESS"){Memories.conflicts(role.scope(),entries,"preference","饮品偏好","内容",now=100)}
    }
}
