package com.ai.assistance.operit

import com.ai.assistance.operit.api.chat.RequestContext
import com.ai.assistance.operit.data.model.*
import org.junit.Assert.*
import org.junit.Test

class RelationshipTest {
    private val a=RoleEntity("a","A","PRIVATE_ROLE",spaceId="space-a",userName="PRIVATE_NAME",userPersona="PRIVATE_PERSONA",personaEnabled=true)
    private val chat=ChatEntity("chat-a",a.id,"cfg","chat",openingMessage="PRIVATE_OPENING")
    private fun relation(id:String="r",description:String="PRIVATE_RELATION",chatId:String?=null,enabled:Boolean=true,priority:Int=50)=
        RelationshipEntry(id,a.id,a.spaceId,chatId,"我","角色","朋友",description,enabled,priority)
    private val lore=WorldbookEntry("w",a.id,a.spaceId,null,"PRIVATE_TITLE","PRIVATE_LORE","always","",true,50)
    private fun rejected(block:()->Unit){try{block();fail("Cross-scope access accepted")}catch(e:ChatFailure){assertEquals("ACCESS",e.code)}}
    @Test fun rawModeHasNoSystemPromptOpeningPersonaLoreRelationshipOrShortMessageInstruction() {
        val raw=a.copy(conversationMode="raw")
        val request=RequestContext.resolve(raw.scope(),raw,chat,emptyList(),"hello",listOf(lore),listOf(relation()))
        assertEquals(listOf(PromptTurn("user","hello")),request.turns)
        assertEquals("",request.systemInstruction);assertEquals("",RequestContext.systemPrompt(raw))
        assertEquals("raw_mode",request.worldbook.decisions.single().reason)
        assertEquals("raw_mode",request.relationships.decisions.single().reason)
        assertEquals(0,request.worldbook.characters);assertEquals(0,request.relationships.characters)
        assertEquals("paragraph",raw.effectiveReplyStyle());assertEquals("short",a.effectiveReplyStyle())
        val switchedBack=RequestContext.resolve(a.scope(),a,chat,emptyList(),"hello",listOf(lore),listOf(relation()))
        listOf("PRIVATE_ROLE","PRIVATE_NAME","PRIVATE_PERSONA","PRIVATE_LORE","PRIVATE_RELATION","回复格式").forEach {assertTrue(switchedBack.systemInstruction.contains(it))}
        assertTrue(switchedBack.turns.any {it.content=="PRIVATE_OPENING"})
    }
    @Test fun rawModeKeepsActualHistoryButNeverTreatsUserContentAsSystemPreview() {
        val history=listOf(MessageEntity("u",chat.id,"old","user","old persona discussion",MessageState.COMPLETE,1),
            MessageEntity("a",chat.id,"old","assistant","old answer",MessageState.COMPLETE,2),
            MessageEntity("u2",chat.id,"cancelled","user","unfinished question",MessageState.COMPLETE,3),
            MessageEntity("a2",chat.id,"cancelled","assistant","unfinished",MessageState.CANCELLED,4))
        val raw=a.copy(conversationMode="raw")
        val result=RequestContext.resolve(raw.scope(),raw,chat,history,"next",emptyList())
        assertEquals(listOf("old persona discussion","old answer","next"),result.turns.map {it.content})
        assertEquals(listOf("user","assistant","user"),result.turns.map {it.role})
        assertEquals("",result.systemInstruction)
    }
    @Test fun fiftyAlternatingScopesNeverInjectOtherRolesTenRelationships() {
        val b=a.copy(id="b",spaceId="space-b")
        repeat(50){i->val r=if(i%2==0)a else b;val c=chat.copy(roleId=r.id,id="chat-${r.id}")
            val entries=(0 until 10).map {relation("$it","${r.id}-REL-MARKER-$it").copy(roleId=r.id,spaceId=r.spaceId)}
            val result=RequestContext.resolve(r.scope(),r,c,emptyList(),"next",emptyList(),entries)
            repeat(10){assertTrue(result.systemInstruction.contains("${r.id}-REL-MARKER-$it"))}
            assertFalse(result.systemInstruction.contains("${if(r.id=="a")"b"else"a"}-REL-MARKER"))
            // Even raw mode must reject a forged relation owner, rather than mask the access error.
            rejected{RequestContext.resolve(r.scope(),r.copy(conversationMode="raw"),c,emptyList(),"next",emptyList(),entries+relation("foreign").copy(spaceId="wrong"))}
        }
    }
    @Test fun directedGraphLabelsRemainScopedAndBudgetedWithoutRecursiveLookup() {
        val entries=listOf(relation("first","高".repeat(2000),priority=100),relation("second","低".repeat(2000),priority=99),
            relation("off",enabled=false),relation("other",chatId="chat-b"),relation("graph","补充",priority=1).copy(sourceName="其他角色的名字",targetName="我",label="同学"))
        val selection=Relationships.select(a.scope(),chat,entries,false)
        assertEquals("budget",selection.decisions.single {it.id=="second"}.reason)
        assertEquals("disabled",selection.decisions.single {it.id=="off"}.reason)
        assertEquals("other_chat",selection.decisions.single {it.id=="other"}.reason)
        assertTrue(selection.instruction.contains("其他角色的名字 → 我 · 同学"))
        assertTrue(selection.characters<=Relationships.BUDGET)
        assertEquals(selection.characters,selection.decisions.sumOf {it.characters})
        assertEquals(selection,Relationships.select(a.scope(),chat,entries.reversed(),false))
    }
    @Test fun oldRolesRemainCharacterAndPublicTemplatesExcludeModeAndPrivateRelations() {
        assertEquals("character",a.conversationMode)
        val json=RoleTemplateCodec.encode(RoleTemplateCodec.fromRole(a.copy(conversationMode="raw")))
        assertFalse(json.contains("conversationMode"));assertFalse(json.contains("userPersona"));assertFalse(json.contains("relationships"))
        val foreign=org.json.JSONObject(json).put("conversationMode","raw").put("relationships",org.json.JSONArray())
        assertEquals(2,RoleTemplateCodec.decode(foreign.toString().toByteArray()).ignoredFields)
    }
    @Test fun invalidFieldsModeAndOversizedContextAreRejected() {
        fun invalid(block:()->Unit){try{block();fail("Invalid input accepted")}catch(_:IllegalArgumentException){}}
        invalid{Relationships.validate("","B","朋友","",0)}
        invalid{Relationships.validate("A","B","朋友","x".repeat(2001),0)}
        invalid{Relationships.validate("A","B","\uD83D","",0)}
        invalid{Relationships.validate("A","B","朋友","",101)}
        invalid{RequestContext.systemPrompt(a.copy(conversationMode="unknown"))}
        invalid{Relationships.select(a.scope(),chat,List(33){relation("$it")},false)}
        val history=(0..4).flatMap {i->listOf(MessageEntity("u$i",chat.id,"r$i","user","文".repeat(6000),MessageState.COMPLETE,i*2L),
            MessageEntity("a$i",chat.id,"r$i","assistant","答".repeat(6000),MessageState.COMPLETE,i*2L+1))}
        try {RequestContext.resolve(a.scope(),a,chat,history,"新".repeat(3000),emptyList(),listOf(relation(description="关".repeat(2000))));fail()}
        catch(e:ChatFailure){assertEquals("CONTEXT_LIMIT",e.code)}
    }
}
