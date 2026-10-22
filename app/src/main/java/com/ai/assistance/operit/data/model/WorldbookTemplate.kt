package com.ai.assistance.operit.data.model

import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

data class WorldbookTemplateEntry(val title:String,val content:String,val trigger:String,val keywords:String,val priority:Int)
data class WorldbookTemplate(val entries:List<WorldbookTemplateEntry>)
data class WorldbookTemplatePreview(val template:WorldbookTemplate,val ignoredFields:Int)

/** Share only authored lore. Identity, session bindings, private personas, keys and history are excluded. */
object WorldbookTemplateCodec {
    const val MAX_BYTES=256*1024
    private val fields=setOf("title","content","trigger","keywords","priority")
    fun validate(t:WorldbookTemplate) {
        require(t.entries.size in 1..Worldbook.MAX_ENTRIES)
        t.entries.forEach {Worldbook.validate(it.title,it.content,it.trigger,it.keywords,it.priority)}
    }
    fun fromEntries(entries:List<WorldbookEntry>)=WorldbookTemplate(entries.map {WorldbookTemplateEntry(it.title,it.content,it.trigger,it.keywords,it.priority)})
    fun encode(t:WorldbookTemplate):String {
        val text=format(t)
        if(text.toByteArray(Charsets.UTF_8).size>MAX_BYTES)throw ChatFailure("WORLDBOOK_TEMPLATE")
        return text
    }
    fun format(t:WorldbookTemplate):String {
        validate(t)
        val text=JSONObject().put("schemaVersion",1).put("type","companion.worldbook").put("entries",JSONArray().apply {
            t.entries.forEach {put(JSONObject().put("title",it.title).put("content",it.content).put("trigger",it.trigger).put("keywords",it.keywords).put("priority",it.priority))}
        }).toString(2)
        return text
    }
    fun read(input:InputStream):WorldbookTemplatePreview {
        val out=ByteArrayOutputStream();val buffer=ByteArray(4096)
        while(true){val n=input.read(buffer,0,minOf(buffer.size,MAX_BYTES+1-out.size()));if(n<0)break;if(n==0)continue
            out.write(buffer,0,n);if(out.size()>MAX_BYTES)throw ChatFailure("WORLDBOOK_TEMPLATE")}
        return decode(out.toByteArray())
    }
    fun decode(bytes:ByteArray):WorldbookTemplatePreview=try {
        require(bytes.size<=MAX_BYTES)
        val text=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        StrictJson(text).validate()
        val obj=JSONObject(text)
        require(obj.get("schemaVersion")==1&&obj.get("type")=="companion.worldbook")
        val array=obj.getJSONArray("entries");require(array.length() in 1..Worldbook.MAX_ENTRIES)
        var ignored=obj.keys().asSequence().count {it !in setOf("schemaVersion","type","entries")}
        val entries=(0 until array.length()).map {index->
            val e=array.getJSONObject(index);ignored+=e.keys().asSequence().count {it !in fields}
            fun text(key:String)=e.get(key).also {require(it is String)} as String
            val priority=e.get("priority");require(priority is Int)
            WorldbookTemplateEntry(text("title"),text("content"),text("trigger"),text("keywords"),priority)
        }
        WorldbookTemplate(entries).also(::validate).let {WorldbookTemplatePreview(it,ignored)}
    }catch(_:Exception){throw ChatFailure("WORLDBOOK_TEMPLATE")}
}
