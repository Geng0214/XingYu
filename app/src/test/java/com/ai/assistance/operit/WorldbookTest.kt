package com.ai.assistance.operit

import com.ai.assistance.operit.api.chat.RequestContext
import com.ai.assistance.operit.data.model.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class WorldbookTest {
    @Test fun compactImportNearLimitCanBePreviewedWithoutAllowingOversizeExports() {
        val template=(3900..4000).asSequence().map {size->WorldbookTemplate(List(64){WorldbookTemplateEntry("e$it","x".repeat(size),"always","",50)})}
            .first {val text=WorldbookTemplateCodec.format(it);text.toByteArray().size>WorldbookTemplateCodec.MAX_BYTES&&JSONObject(text).toString().toByteArray().size<=WorldbookTemplateCodec.MAX_BYTES}
        val compact=JSONObject(WorldbookTemplateCodec.format(template)).toString().toByteArray()
        val preview=WorldbookTemplateCodec.decode(compact)
        assertEquals(template,preview.template)
        assertTrue(WorldbookTemplateCodec.format(preview.template).length>WorldbookTemplateCodec.MAX_BYTES)
        rejected("WORLDBOOK_TEMPLATE"){WorldbookTemplateCodec.encode(preview.template)}
    }
    private val role=RoleEntity("a","角色","基础人设",spaceId="space-a",replyStyle="paragraph")
    private val chat=ChatEntity("chat-a",role.id,"model","会话",0)
    private fun entry(id:String="one",trigger:String="always",keys:String="",content:String="合成世界设定",priority:Int=50,enabled:Boolean=true,chatId:String?=null)=
        WorldbookEntry(id,role.id,role.spaceId,chatId,"设定 $id",content,trigger,keys,enabled,priority)
    private fun resolve(entries:List<WorldbookEntry>,text:String="你好",history:List<MessageEntity> = emptyList())=
        RequestContext.resolve(role.scope(),role,chat,history,text,entries)
    private fun rejected(code:String,block:()->Unit){try{block();fail("Expected $code")}catch(e:ChatFailure){assertEquals(code,e.code)}}
    @Test fun fiftyAlternatingSpacesUseTenMarkersWithoutCrossRoleInjection() {
        val b=role.copy(id="b",spaceId="space-b");val cb=chat.copy(id="chat-b",roleId=b.id)
        repeat(50) {round->
            val r=if(round%2==0)role else b;val c=if(round%2==0)chat else cb
            val entries=(0 until 10).map {entry("$it",content="${r.id}-WORLD-MARKER-$it").copy(roleId=r.id,spaceId=r.spaceId)}
            val actual=RequestContext.resolve(r.scope(),r,c,emptyList(),"你好",entries)
            entries.forEach {assertTrue(actual.turns.first().content.contains(it.content))}
            assertFalse(actual.turns.first().content.contains("${if(r==role)"b"else"a"}-WORLD-MARKER"))
            rejected("ACCESS"){RequestContext.resolve(r.scope(),r,c,emptyList(),"你好",entries+entry("foreign").copy(spaceId="wrong"))}
        }
    }
    @Test fun triggersAreLiteralCaseInsensitiveAndLimitedToLatestFourCompleteUserTurns() {
        val lore=entry(trigger="keywords",keys="咖啡\n.*[x]\nHELLO😀")
        assertEquals("included",resolve(listOf(lore),"hello😀").worldbook.decisions.single().reason)
        assertEquals("no_match",resolve(listOf(lore),"regex x").worldbook.decisions.single().reason)
        assertEquals("included",resolve(listOf(lore),"这是 .*[x]").worldbook.decisions.single().reason)
        fun pair(i:Int,user:String,answer:String,state:MessageState=MessageState.COMPLETE)=listOf(
            MessageEntity("u$i",chat.id,"r$i","user",user,MessageState.COMPLETE,i*2L),
            MessageEntity("a$i",chat.id,"r$i","assistant",answer,state,i*2L+1))
        val old=pair(0,"咖啡","你好")+(1..4).flatMap {pair(it,"别的话","咖啡")}
        assertEquals("no_match",resolve(listOf(lore),history=old).worldbook.decisions.single().reason)
        assertEquals("included",resolve(listOf(lore),history=old+pair(5,"咖啡","好")).worldbook.decisions.single().reason)
        assertEquals("no_match",resolve(listOf(lore),history=old+pair(5,"咖啡","",MessageState.CANCELLED)).worldbook.decisions.single().reason)
        val nonRecursive=listOf(entry("first",content="咖啡"),lore.copy(id="second"))
        assertEquals("no_match",resolve(nonRecursive).worldbook.decisions.single {it.id=="second"}.reason)
    }
    @Test fun scopeDisabledBudgetAndPriorityDecisionsMatchActualSystemPrompt() {
        val entries=listOf(entry("large",content="长".repeat(3900),priority=99),entry("budget",content="满".repeat(100),priority=98),
            entry("small",content="小"),entry("off",enabled=false),entry("other",chatId="other"))
        val result=resolve(entries);val selected=result.worldbook
        assertEquals("included",selected.decisions.single {it.id=="large"}.reason)
        assertEquals("budget",selected.decisions.single {it.id=="budget"}.reason)
        assertEquals("disabled",selected.decisions.single {it.id=="off"}.reason)
        assertEquals("other_chat",selected.decisions.single {it.id=="other"}.reason)
        assertTrue(selected.characters<=Worldbook.BUDGET)
        assertEquals(selected.instruction.length,selected.characters)
        assertEquals(selected.characters,selected.decisions.sumOf {it.characters})
        assertEquals(RequestContext.systemPrompt(role)+selected.instruction,result.turns.first().content)
        assertEquals(selected,resolve(entries.reversed()).worldbook)
        assertEquals("included",resolve(listOf(entry(chatId=chat.id))).worldbook.decisions.single().reason)
    }
    @Test fun emptyWorldbookPreservesPromptAndTotalContextLimitStillApplies() {
        assertEquals(RequestContext.systemPrompt(role),resolve(emptyList()).turns.first().content)
        val history=(0..4).flatMap {i->listOf(
            MessageEntity("u$i",chat.id,"r$i","user","文".repeat(6000),MessageState.COMPLETE,i*2L),
            MessageEntity("a$i",chat.id,"r$i","assistant","答".repeat(6000),MessageState.COMPLETE,i*2L+1))}
        rejected("CONTEXT_LIMIT"){resolve(listOf(entry(content="设".repeat(3900))),"新".repeat(1000),history)}
    }
    @Test fun frozenResolutionSurvivesTwentyEditDisableAndRetryRounds() {
        repeat(20) {i->
            val original=entry(content="原始-$i",priority=i)
            val frozen=resolve(listOf(original))
            val changed=original.copy(content="修改-$i",enabled=i%2==0,version=2)
            val retry=resolve(listOf(changed))
            assertTrue(frozen.turns.first().content.contains("原始-$i"))
            assertFalse(frozen.turns.first().content.contains("修改-$i"))
            assertEquals(1L,frozen.worldbook.decisions.single().version)
            assertEquals(2L,retry.worldbook.decisions.single().version)
            assertEquals(i%2==0,retry.turns.first().content.contains("修改-$i"))
        }
    }
    @Test fun safeTemplateContainsOnlyLoreAndNeverActivatesExternalIdsOrPrograms() {
        val template=WorldbookTemplateCodec.fromEntries(listOf(entry(content="https://example.invalid/private\n<script>doNothing()</script>")))
        val exported=WorldbookTemplateCodec.encode(template)
        val json=JSONObject(exported);val raw=json.getJSONArray("entries").getJSONObject(0)
        assertEquals(setOf("title","content","trigger","keywords","priority"),raw.keys().asSequence().toSet())
        assertFalse(exported.contains("space-a"));assertFalse(exported.contains("chat-a"))
        raw.put("id","foreign").put("roleId","someone").put("enabled",true).put("path","../../private").put("url","https://example.invalid/run")
        json.put("script","fetch('/execute')").put("credentialRef","private-key")
        val decoded=WorldbookTemplateCodec.decode(json.toString().toByteArray())
        assertEquals(7,decoded.ignoredFields);assertEquals(template,decoded.template)
        assertEquals(decoded,WorldbookTemplateCodec.decode(json.toString().toByteArray()))
    }
    @Test fun malformedOversizeUnknownVersionDuplicateFieldsAndDeepTemplatesAreRejected() {
        val valid=WorldbookTemplateCodec.encode(WorldbookTemplateCodec.fromEntries(listOf(entry())))
        val bad=listOf(valid.replace("\"schemaVersion\": 1","\"schemaVersion\": 99"),valid.dropLast(1),
            valid.replace("\"type\":","\"type\":\"companion.worldbook\",\"type\":"),
            "{\"nested\":"+"[".repeat(18)+"0"+"]".repeat(18)+"}",
            valid.replace("\"priority\": 50","\"priority\": 50.5"))
        bad.forEach {rejected("WORLDBOOK_TEMPLATE"){WorldbookTemplateCodec.decode(it.toByteArray())}}
        rejected("WORLDBOOK_TEMPLATE"){WorldbookTemplateCodec.decode(ByteArray(WorldbookTemplateCodec.MAX_BYTES+1))}
        rejected("WORLDBOOK_TEMPLATE"){WorldbookTemplateCodec.decode(byteArrayOf(0xc3.toByte(),0x28))}
        fun invalid(block:()->Unit){try{block();fail("invalid lore accepted")}catch(_:IllegalArgumentException){}}
        invalid{Worldbook.validate("名字","正文","keywords","",50)}
        invalid{Worldbook.validate("名字","正文","regex",".*",50)}
        invalid{Worldbook.validate("名字","正文","always","x",101)}
        invalid{Worldbook.validate("名字","文".repeat(4001),"always","",0)}
        invalid{Worldbook.validate("名字","\uD83D","always","",0)}
    }
}
