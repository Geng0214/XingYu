package com.ai.assistance.operit

import com.ai.assistance.operit.data.model.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class RoleTemplateTest {
    private val template=RoleTemplate("初见 😀","温柔地用中文交流\n保留换行和引号：\"好\"","你好 👋")
    private fun reject(bytes: ByteArray) {
        try { RoleTemplateCodec.decode(bytes); fail("Malformed template accepted") }
        catch(e: ChatFailure) { assertEquals("TEMPLATE",e.code) }
    }
    @Test fun unicodeRoundTripAndExactFieldAllowlist() {
        val encoded=RoleTemplateCodec.encode(template)
        assertEquals(template,RoleTemplateCodec.read(ByteArrayInputStream(encoded.toByteArray())).template)
        val role=RoleEntity("private-id","A","public persona",spaceId="private-space",defaultConfigId="secret-reference")
        val export=RoleTemplateCodec.encode(RoleTemplateCodec.fromRole(role))
        listOf("private-id","private-space","secret-reference","credentialRef","defaultConfigId","chatId").forEach { assertFalse(export.contains(it)) }
        assertEquals(setOf("schemaVersion","name","prompt","openingMessage"),org.json.JSONObject(export).keys().asSequence().toSet())
    }
    @Test fun ignoresScriptsUrlsCredentialsAndIdsAsData() {
        val obj=org.json.JSONObject(RoleTemplateCodec.encode(template))
            .put("id","existing-role").put("apiKey","synthetic-secret").put("script","deleteEverything()")
            .put("url","https://127.0.0.1/").put("../file","outside")
        val result=RoleTemplateCodec.decode(obj.toString().toByteArray())
        assertEquals(5,result.ignoredFields);assertEquals(template,result.template)
        assertFalse(RoleTemplateCodec.encode(result.template).contains("synthetic-secret"))
    }
    @Test fun rejectsMalformedEncodingDepthSizeAndVersions() {
        reject(byteArrayOf(0xc3.toByte(),0x28))
        reject(ByteArray(RoleTemplateCodec.MAX_BYTES+1){32})
        listOf("", "[]", "{}", "null", "{schemaVersion:1}", "{'schemaVersion':1}",
            "{\"a\":1,}", "{\"a\":1,\"a\":2}", "{\"a\":NaN}", "{\"a\":01}",
            "{\"a\":\"bad\ncontrol\"}", "[] trailing", "[".repeat(18)+"0"+"]".repeat(18)).forEach { reject(it.toByteArray()) }
        val valid=RoleTemplateCodec.encode(template)
        listOf(0,2,"1",true,org.json.JSONObject.NULL).forEach { version ->
            reject(org.json.JSONObject(valid).put("schemaVersion",version).toString().toByteArray())
        }
        reject(org.json.JSONObject(valid).put("name","x".repeat(41)).toString().toByteArray())
        reject(org.json.JSONObject(valid).put("prompt","x".repeat(8001)).toString().toByteArray())
        reject(org.json.JSONObject(valid).put("openingMessage",false).toString().toByteArray())
        reject("{\"schemaVersion\":1,\"name\":\"a\",\"prompt\":\"b\",\"openingMessage\":\"\\ud800\"}".toByteArray())
    }
    @Test fun boundedStreamStopsAtLimitInsteadOfReadingWholeFile() {
        var consumed=0
        val input=object:java.io.InputStream() { override fun read():Int { consumed++;return 32 } }
        try { RoleTemplateCodec.read(input);fail() } catch(e:ChatFailure) { assertEquals("TEMPLATE",e.code) }
        assertEquals(RoleTemplateCodec.MAX_BYTES+1,consumed)
    }
    @Test fun unknownNestedFieldsAreValidatedButNeverExported() {
        val obj=org.json.JSONObject(RoleTemplateCodec.encode(template)).put("extra",org.json.JSONObject("{\"arr\":[null,true,1.2e3,{\"safe\":false}]}"))
        val result=RoleTemplateCodec.decode(obj.toString().toByteArray())
        assertEquals(1,result.ignoredFields)
        assertEquals(template,result.template)
        assertFalse(RoleTemplateCodec.encode(result.template).contains("extra"))
    }
}
