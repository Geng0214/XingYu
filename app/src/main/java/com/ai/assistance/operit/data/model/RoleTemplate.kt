package com.ai.assistance.operit.data.model

import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import org.json.JSONObject
import org.json.JSONTokener

data class RoleTemplate(val name: String, val prompt: String, val openingMessage: String)
data class TemplatePreview(val template: RoleTemplate, val ignoredFields: Int)

object RoleTemplateCodec {
    const val MAX_BYTES = 256 * 1024
    private val fields=setOf("schemaVersion","name","prompt","openingMessage")
    fun fromRole(role: RoleEntity) = RoleTemplate(role.name,role.prompt,role.openingMessage)
    fun read(input: InputStream): TemplatePreview {
        val bytes=ByteArrayOutputStream()
        val buffer=ByteArray(4096)
        while(true) {
            val n=input.read(buffer,0,minOf(buffer.size,MAX_BYTES+1-bytes.size()))
            if(n<0) break
            if(n==0) continue
            bytes.write(buffer,0,n)
            if(bytes.size()>MAX_BYTES) throw ChatFailure("TEMPLATE")
        }
        return decode(bytes.toByteArray())
    }
    fun decode(bytes: ByteArray): TemplatePreview {
        try {
            require(bytes.size<=MAX_BYTES)
            val text=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            // org.json accepts lenient JSON on Android. Validate grammar, depth and duplicate keys first.
            StrictJson(text).validate()
            val obj=JSONObject(text)
            require(obj.get("schemaVersion") == 1)
            fun string(key: String): String { val value=obj.get(key); require(value is String); return value }
            val template=RoleTemplate(string("name"),string("prompt"),string("openingMessage"))
            validate(template)
            return TemplatePreview(template,obj.keys().asSequence().count { it !in fields })
        } catch(_: Exception) { throw ChatFailure("TEMPLATE") }
    }
    fun encode(template: RoleTemplate): String {
        validate(template)
        return JSONObject().put("schemaVersion",1).put("name",template.name)
            .put("prompt",template.prompt).put("openingMessage",template.openingMessage).toString(2)
    }
    private fun validate(t: RoleTemplate) {
        require(t.name.isNotBlank() && t.name.length<=40 && t.prompt.isNotBlank() && t.prompt.length<=8000 && t.openingMessage.length<=8000)
        listOf(t.name,t.prompt,t.openingMessage).forEach { text ->
            var i=0
            while(i<text.length) {
                val c=text[i++]
                if(Character.isHighSurrogate(c)) { require(i<text.length && Character.isLowSurrogate(text[i])); i++ }
                else require(!Character.isLowSurrogate(c))
            }
        }
    }
}

internal class StrictJson(private val source: String) {
    private var index=0
    fun validate() { value(0); whitespace(); require(index==source.length) }
    private fun whitespace() { while(index<source.length && source[index] in " \t\r\n") index++ }
    private fun take(c: Char): Boolean { whitespace(); return if(index<source.length && source[index]==c) { index++;true } else false }
    private fun expect(c: Char) { require(take(c)) }
    private fun string(): String {
        whitespace(); val start=index
        require(index<source.length && source[index++]=='"')
        while(index<source.length) {
            val c=source[index++]
            if(c=='"') return JSONTokener(source.substring(start,index)).nextValue() as String
            require(c.code>=32)
            if(c=='\\') {
                require(index<source.length)
                val escape=source[index++]
                if(escape=='u') repeat(4) { require(index<source.length && source[index++] in "0123456789abcdefABCDEF") }
                else require(escape in "\"\\/bfnrt")
            }
        }
        error("Unterminated string")
    }
    private fun value(depth: Int) {
        require(depth<=16); whitespace(); require(index<source.length)
        when(source[index]) {
            '{' -> {
                index++; val keys=mutableSetOf<String>()
                if(take('}')) return
                do { require(keys.add(string())); expect(':'); value(depth+1) } while(take(','))
                expect('}')
            }
            '[' -> { index++; if(take(']'))return; do { value(depth+1) } while(take(',')); expect(']') }
            '"' -> string()
            else -> {
                val start=index
                while(index<source.length && source[index] !in ",]} \t\r\n")index++
                val token=source.substring(start,index)
                require(token in setOf("true","false","null") || Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?").matches(token))
            }
        }
    }
}
