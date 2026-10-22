package com.ai.assistance.operit

import com.ai.assistance.operit.api.chat.RequestContext
import com.ai.assistance.operit.data.model.*
import org.junit.Assert.*
import org.junit.Test

class UserPersonaTest {
    @Test fun publicTemplateNeverIncludesPersonaAndForeignPersonaFieldsAreIgnored() {
        val role=RoleEntity("role","AI","public persona",userName="PRIVATE_NAME",userPersona="PRIVATE_DETAIL",personaEnabled=true)
        val text=RoleTemplateCodec.encode(RoleTemplateCodec.fromRole(role))
        assertFalse(text.contains("PRIVATE_"));assertFalse(text.contains("userName"))
        val untrusted=org.json.JSONObject(text).put("userName","PRIVATE_NAME").put("userPersona","PRIVATE_DETAIL").put("personaEnabled",true).toString()
        val imported=RoleTemplateCodec.decode(untrusted.toByteArray())
        assertEquals(3,imported.ignoredFields);assertEquals(RoleTemplateCodec.fromRole(role),imported.template)
    }
    @Test fun disabledPersonaDoesNotChangeLegacyPromptOrEraseStoredDraft() {
        val original=RoleEntity("role","AI","persona",replyStyle="paragraph")
        val saved=original.copy(userName="小青",userPersona="private detail",personaEnabled=false)
        assertEquals("persona",RequestContext.systemPrompt(saved));assertEquals("我",saved.selfName())
        val enabled=saved.copy(personaEnabled=true)
        assertTrue(RequestContext.systemPrompt(enabled).contains("称呼：小青"));assertEquals("小青",enabled.selfName())
        assertEquals("private detail",enabled.copy(personaEnabled=false).userPersona)
    }
    @Test fun fiftyAlternatingScopesIncludeTenOwnPersonaMarkersAndRejectCrossRoleContext() {
        val roles=listOf("A","B").map {id->RoleEntity(id,"同名","角色-$id",userName="称呼-$id",userPersona=(1..10).joinToString("\n"){"$id-private-$it"},personaEnabled=true)}
        repeat(50){n->val role=roles[n%2];val other=roles[1-n%2];val chat=ChatEntity("chat-${role.id}",role.id,"cfg","chat")
            val turns=RequestContext.build(role.scope(),role,chat,emptyList(),"hello😀")
            assertEquals(RequestContext.systemPrompt(role),turns.first().content)
            repeat(10){assertTrue(turns.first().content.contains("${role.id}-private-${it+1}"))}
            assertFalse(turns.any {it.content.contains("${other.id}-private-")||it.content.contains("称呼-${other.id}")})
            try {RequestContext.build(other.scope(),role,chat,emptyList(),"bad");fail()}catch(e:ChatFailure){assertEquals("ACCESS",e.code)}
        }
    }
    @Test fun personaLengthValidationAndSharedContextBudgetAreEnforced() {
        listOf({UserPersona.validate("", "",true)},{UserPersona.validate("a".repeat(41),"",false)},
            {UserPersona.validate("line\nbreak","",true)},{UserPersona.validate("name","a".repeat(4001),true)}).forEach {call->
            try {call();fail()}catch(_:IllegalArgumentException){}
        }
        val role=RoleEntity("r","AI","p",userName="name",userPersona="m".repeat(4000),personaEnabled=true)
        val chat=ChatEntity("c","r","cfg","chat")
        val history=listOf(MessageEntity("u","c","req","user","x".repeat(61000),MessageState.COMPLETE,1),
            MessageEntity("a","c","req","assistant","reply",MessageState.COMPLETE,2))
        try {RequestContext.build(role.scope(),role,chat,history,"new");fail()}catch(e:ChatFailure){assertEquals("CONTEXT_LIMIT",e.code)}
        assertTrue(RequestContext.build(role.scope(),role.copy(personaEnabled=false),chat,history,"new").isNotEmpty())
    }
}
