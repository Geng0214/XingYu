package com.ai.assistance.operit.core.tools.packTool

import com.ai.assistance.operit.core.tools.ToolFailure
import com.ai.assistance.operit.core.tools.javascript.ToolPkgScript
import com.ai.assistance.operit.core.tools.mcp.McpToolDefinition
import com.ai.assistance.operit.data.model.StrictJson
import org.hjson.JsonValue
import org.hjson.HjsonOptions
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipEntry

data class ToolPkgExport(val name:String,val entry:String,val function:String,val definition:McpToolDefinition)
data class ToolPkgBundle(val packageId:String,val packageVersion:String,val apiVersion:String,val name:String,
    val description:String,val main:String,val sources:Map<String,String>,val tools:List<ToolPkgExport>,val warnings:List<String>,
    val sha256:String,private val payload:ByteArray) {
    fun archive():ByteArray=payload.copyOf()
}

/** Operit schema 1 + Hjson METADATA; inspection never evaluates JavaScript or follows any URL. */
object ToolPkgCodec {
    const val MAX_BYTES=1024*1024
    private val namePattern=Regex("[A-Za-z_][A-Za-z0-9_]{0,63}")
    private val packagePattern=Regex("[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}")
    fun timeExample(read:(String)->ByteArray):ByteArray {
        val out=ByteArrayOutputStream()
        ZipOutputStream(out,Charsets.UTF_8).use {zip->
            for(path in listOf("manifest.json","main.js","packages/time.js")) {
                zip.putNextEntry(ZipEntry(path).apply {time=0});zip.write(read(path));zip.closeEntry()
            }
        }
        return out.toByteArray()
    }
    fun hash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
    fun read(input:InputStream):ToolPkgBundle=decode(readLimited(input,MAX_BYTES))
    fun decode(input:ByteArray):ToolPkgBundle=try {
        if(input.size>MAX_BYTES)throw ToolFailure("PKG_LIMIT")
        val bytes=input.copyOf()
        val files=readZip(bytes)
        val candidates=files.keys.filter {it.substringAfterLast('/') in setOf("manifest.json","manifest.hjson")}
        require(candidates.size==1)
        val manifestPath=candidates.single();val base=manifestPath.substringBeforeLast('/',"").let {if(it.isEmpty())""else"$it/"}
        require(files.keys.all {it.startsWith(base)})
        val contents=files.mapKeys {it.key.removePrefix(base)}
        val warnings=mutableListOf<String>()
        val manifest=readObject(contents.getValue(manifestPath.removePrefix(base)),manifestPath.endsWith(".hjson"))
        if(manifest.get("schema_version")!=1)throw ToolFailure("PKG_VERSION")
        val api=string(manifest,"api_version",20)
        if(api!="1.0.0")throw ToolFailure("PKG_VERSION")
        val id=string(manifest,"toolpkg_id",128);require(packagePattern.matches(id))
        val version=string(manifest,"version",40);require(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][A-Za-z0-9.-]+)?").matches(version))
        val main=string(manifest,"main",240);require(ToolPkgScript.validPath(main)&&main.endsWith(".js")&&main in contents)
        val name=localized(manifest.get("display_name"),80)
        val description=localized(manifest.get("description"),8000)
        for(field in listOf("requires","resources","wasm_modules","workflow_templates","workspace_templates","permissions","capabilities")) {
            if(manifest.has(field)&&manifest.get(field).toString()!="[]")throw ToolFailure("PKG_CAPABILITY")
        }
        if(manifest.has("companion_profile")&&manifest.get("companion_profile")!="tools-v1")throw ToolFailure("PKG_CAPABILITY")
        val allowed=setOf("schema_version","api_version","toolpkg_id","version","main","display_name","description","author","enabled_by_default","subpackages","companion_profile","requires","resources","wasm_modules","workflow_templates","workspace_templates","permissions","capabilities")
        manifest.keys().asSequence().filter {it !in allowed}.forEach {warnings+="未读取清单字段：$it"}
        if(manifest.optBoolean("enabled_by_default",false))warnings+="包声明默认启用；本应用安装后保持停用，需手动授权。"
        val subs=manifest.getJSONArray("subpackages");require(subs.length() in 1..32)
        val subIds=mutableSetOf<String>();val tools=mutableListOf<ToolPkgExport>()
        repeat(subs.length()) {index->
            val sub=subs.getJSONObject(index);val subId=string(sub,"id",40);require(namePattern.matches(subId)&&subIds.add(subId))
            val entry=string(sub,"entry",240);require(ToolPkgScript.validPath(entry)&&entry.endsWith(".js")&&entry in contents)
            val text=decodeUtf8(contents.getValue(entry))
            val match=Regex("^\\s*/\\*\\s*METADATA\\s*([\\s\\S]*?)\\*/").find(text.removePrefix("\uFEFF"))?:throw ToolFailure("PKG_METADATA")
            val metadata=readObject(match.groupValues[1].toByteArray(Charsets.UTF_8),true)
            for(field in listOf("env","permissions","requires"))if(metadata.has(field)&&metadata.get(field).toString()!="[]")throw ToolFailure("PKG_CAPABILITY")
            val definitions=metadata.getJSONArray("tools");require(definitions.length() in 1..128)
            repeat(definitions.length()) {n->
                val definition=definitions.getJSONObject(n)
                val function=string(definition,"name",64);require(namePattern.matches(function))
                val toolName="$subId.$function";require(tools.none {it.name==toolName})
                val toolDescription=localized(definition.get("description"),8000)
                val parameters=definition.getJSONArray("parameters");require(parameters.length()<=64)
                val properties=JSONObject();val required=JSONArray()
                repeat(parameters.length()) {p->
                    val parameter=parameters.getJSONObject(p);val key=string(parameter,"name",64)
                    require(namePattern.matches(key)&&!properties.has(key))
                    val type=string(parameter,"type",20);require(type in setOf("string","number","integer","boolean","object","array"))
                    val schema=JSONObject().put("type",type).put("description",localized(parameter.get("description"),2000))
                    if(parameter.has("enum")) {
                        val choices=parameter.getJSONArray("enum");require(choices.length() in 1..64)
                        repeat(choices.length()){require(choices.get(it) is String||choices.get(it) is Number||choices.get(it) is Boolean)}
                        schema.put("enum",choices)
                    }
                    properties.put(key,schema)
                    if(parameter.has("required")){require(parameter.get("required") is Boolean);if(parameter.getBoolean("required"))required.put(key)}
                    parameter.keys().asSequence().filter {it !in setOf("name","type","description","required","enum")}.forEach {warnings+="未读取参数字段：$toolName.$key.$it"}
                }
                val schema=JSONObject().put("type","object").put("properties",properties).put("required",required).put("additionalProperties",false).toString()
                require(schema.toByteArray().size<=65536)
                tools+=ToolPkgExport(toolName,entry,function,McpToolDefinition(toolName,toolDescription,schema))
            }
        }
        require(tools.size in 1..128)
        val sources=contents.filterKeys {it.endsWith(".js")}.mapValues {decodeUtf8(it.value)}
        ToolPkgBundle(id,version,api,name,description,main,sources,tools,warnings.distinct().take(64),hash(bytes),bytes)
    }catch(e:ToolFailure){throw e}catch(_:Exception){throw ToolFailure("PKG_FORMAT")}

    private fun string(o:JSONObject,key:String,max:Int):String {val v=o.get(key);require(v is String&&v.isNotBlank()&&v.length<=max);return v}
    private fun localized(value:Any,max:Int):String {
        val text=when(value) {
            is String->value
            is JSONObject->{val keys=value.keys().asSequence().toList();require(keys.isNotEmpty());val key=keys.sortedWith(compareBy<String> {when(it){"zh"->0;"en"->1;else->2}}.thenBy {it}).first();val v=value.get(key);require(v is String);v}
            else->throw ToolFailure("PKG_METADATA")
        }
        require(text.isNotBlank()&&text.length<=max)
        return text
    }
    private fun decodeUtf8(bytes:ByteArray)=bytes.decodeToString(throwOnInvalidSequence=true).also {require('\u0000' !in it)}
    private fun readObject(bytes:ByteArray,hjson:Boolean):JSONObject {
        if(bytes.size>65536)throw ToolFailure("PKG_LIMIT")
        val text=decodeUtf8(bytes).removePrefix("\uFEFF")
        if(!hjson){StrictJson(text).validate();return JSONObject(text)}
        // Count every opening delimiter, even inside Hjson bare strings/comments. Closing text cannot
        // trick this pre-parser budget into underestimating recursive nesting. Check actual depth below.
        require(text.trim().startsWith('{')&&text.trim().endsWith('}'))
        require(text.count {it=='{'||it=='['}<=256)
        val parsed=JsonValue.readHjson(text,HjsonOptions().apply {parseLegacyRoot=false})
        fun validate(value:JsonValue,depth:Int) {
            require(depth<=16)
            if(value.isObject) {val names=mutableSetOf<String>();for(member in value.asObject()){require(names.add(member.name));validate(member.value,depth+1)}}
            else if(value.isArray)for(item in value.asArray())validate(item,depth+1)
        }
        validate(parsed,0)
        return JSONObject(parsed.toString())
    }
    private fun readLimited(input:InputStream,limit:Int):ByteArray {
        val out=ByteArrayOutputStream();val buffer=ByteArray(8192)
        while(true){val n=input.read(buffer,0,minOf(buffer.size,limit-out.size()+1));if(n<0)break;out.write(buffer,0,n);if(out.size()>limit)throw ToolFailure("PKG_LIMIT")}
        return out.toByteArray()
    }
    private data class Entry(val size:Long,val compressed:Long,val crc:Long,val method:Int)
    private fun readZip(bytes:ByteArray):Map<String,ByteArray> {
        fun u16(i:Int):Int {require(i>=0&&i+2<=bytes.size);return (bytes[i].toInt() and 255) or ((bytes[i+1].toInt() and 255) shl 8)}
        fun u32(i:Int):Long =u16(i).toLong() or (u16(i+2).toLong() shl 16)
        require(bytes.size>=22&&u32(0)==0x04034b50L)
        val end=(bytes.size-22 downTo maxOf(0,bytes.size-22-1024)).firstOrNull {u32(it)==0x06054b50L&&it+22+u16(it+20)==bytes.size}?:throw ToolFailure("PKG_FORMAT")
        require(u16(end+4)==0&&u16(end+6)==0&&u16(end+8)==u16(end+10))
        val count=u16(end+10);require(count>0);if(count>64)throw ToolFailure("PKG_LIMIT")
        val offset=u32(end+16).toInt();require(offset>=0&&offset+u32(end+12)==end.toLong())
        var cursor=offset;val entries=linkedMapOf<String,Entry>();val names=mutableSetOf<String>();val positions=mutableSetOf<Long>()
        repeat(count) {
            require(cursor+46<=end&&u32(cursor)==0x02014b50L)
            val flags=u16(cursor+8);val method=u16(cursor+10);require(flags and 1==0&&method in setOf(0,8))
            val size=u32(cursor+24);val compressed=u32(cursor+20);if(size>256*1024||compressed>MAX_BYTES)throw ToolFailure("PKG_LIMIT")
            val length=u16(cursor+28);val extra=u16(cursor+30);val comment=u16(cursor+32)
            require(cursor+46+length+extra+comment<=end&&u16(cursor+34)==0)
            val name=decodeUtf8(bytes.copyOfRange(cursor+46,cursor+46+length))
            require(ToolPkgScript.validPath(name.removeSuffix("/"))&&names.add(name.removeSuffix("/").lowercase(Locale.ROOT)))
            val mode=((u32(cursor+38) shr 16).toInt() and 0xf000);require(mode in setOf(0,0x8000,0x4000))
            val local=u32(cursor+42);require(local<offset&&positions.add(local))
            require(u32(local.toInt())==0x04034b50L&&u16(local.toInt()+6)==flags&&u16(local.toInt()+8)==method)
            val localLength=u16(local.toInt()+26)
            require(decodeUtf8(bytes.copyOfRange(local.toInt()+30,local.toInt()+30+localLength))==name)
            entries[name]=Entry(size,compressed,u32(cursor+16),method)
            cursor+=46+length+extra+comment
        }
        require(cursor==end&&positions.min()==0L)
        val result=linkedMapOf<String,ByteArray>();var total=0;var seen=0
        ZipInputStream(ByteArrayInputStream(bytes),Charsets.UTF_8).use {zip->
            while(true) {
                val entry=zip.nextEntry?:break
                val expected=entries[entry.name]?:throw ToolFailure("PKG_FORMAT")
                val content=readLimited(zip,minOf(256*1024,MAX_BYTES-total));total+=content.size
                require(entry.size==expected.size&&entry.compressedSize==expected.compressed&&entry.crc==expected.crc&&entry.method==expected.method)
                seen++
                if(!entry.isDirectory) {
                    require(entry.name.substringAfterLast('.').lowercase(Locale.ROOT) in setOf("js","json","hjson","md","txt")||entry.name.substringAfterLast('/')=="LICENSE")
                    require(result.put(entry.name,content)==null)
                }else require(content.isEmpty())
                zip.closeEntry()
            }
        }
        require(seen==count)
        return result
    }
}
