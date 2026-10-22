package com.ai.assistance.operit.api.chat

import com.ai.assistance.operit.data.model.ChatFailure
import com.ai.assistance.operit.data.model.PromptMaterial
import com.ai.assistance.operit.data.model.TextMaterials
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

class TextMaterialReader(private val client:OkHttpClient=OkHttpClient.Builder()
    .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).connectTimeout(15,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).callTimeout(30,TimeUnit.SECONDS).build()) {

    fun file(input:InputStream,displayName:String,mimeType:String?):PromptMaterial {
        val name=cleanName(displayName)
        val extension=name.substringAfterLast('.',"").lowercase()
        val normalizedMime=when {
            mimeType?.substringBefore(';')?.trim()?.lowercase() in setOf("text/markdown","text/x-markdown")->TextMaterials.MIME_MARKDOWN
            mimeType?.substringBefore(';')?.trim()?.lowercase()=="text/plain"->if(extension in setOf("md","markdown"))TextMaterials.MIME_MARKDOWN else TextMaterials.MIME_TEXT
            (mimeType.isNullOrBlank()||mimeType.substringBefore(';').trim().equals("application/octet-stream",true))&&extension in setOf("txt","md","markdown")->if(extension=="txt")TextMaterials.MIME_TEXT else TextMaterials.MIME_MARKDOWN
            else->throw ChatFailure("MATERIAL_TYPE")
        }
        if(extension !in setOf("txt","md","markdown"))throw ChatFailure("MATERIAL_TYPE")
        return prepare(TextMaterials.KIND_FILE,name,null,normalizedMime,strictUtf8(read(input,TextMaterials.MAX_FILE_BYTES)))
    }

    fun link(value:String):PromptMaterial {
        val url=try {TextMaterials.normalizeHttpsUrl(value)}catch(_:Exception){throw ChatFailure("LINK_URL")}
        val request=Request.Builder().url(url).get().header("Accept","text/html,text/markdown,text/plain;q=0.9").build()
        try {
            client.newCall(request).execute().use {response->
                if(response.code in 300..399)throw ChatFailure("LINK_REDIRECT")
                if(!response.isSuccessful)throw ChatFailure("LINK_HTTP")
                val body=response.body?:throw ChatFailure("LINK_HTTP")
                val media=body.contentType()
                val type=media?.let {"${it.type}/${it.subtype}".lowercase()}?:throw ChatFailure("MATERIAL_TYPE")
                val mime=when(type) {
                    "text/plain"->TextMaterials.MIME_TEXT
                    "text/markdown","text/x-markdown"->TextMaterials.MIME_MARKDOWN
                    "text/html","application/xhtml+xml"->TextMaterials.MIME_HTML
                    else->throw ChatFailure("MATERIAL_TYPE")
                }
                if(media.charset(Charsets.UTF_8)!=Charsets.UTF_8)throw ChatFailure("MATERIAL_ENCODING")
                val decoded=strictUtf8(read(body.byteStream(),TextMaterials.MAX_LINK_BYTES))
                val title=if(mime==TextMaterials.MIME_HTML)htmlTitle(decoded) else null
                val text=if(mime==TextMaterials.MIME_HTML)htmlText(decoded) else decoded
                return prepare(TextMaterials.KIND_LINK,title?.takeIf {it.isNotBlank()}?:request.url.host,url,mime,text)
            }
        } catch(e:ChatFailure){throw e}
          catch(_:java.net.SocketTimeoutException){throw ChatFailure("TIMEOUT")}
          catch(_:java.io.IOException){throw ChatFailure("NETWORK")}
          catch(_:Exception){throw ChatFailure("MATERIAL_FORMAT")}
    }

    private fun prepare(kind:String,name:String,url:String?,mime:String,value:String):PromptMaterial {
        val normalized=normalizeText(value)
        if(normalized.isBlank())throw ChatFailure("MATERIAL_EMPTY")
        require(normalized.length<=TextMaterials.MAX_ORIGINAL_CHARS)
        val truncated=normalized.length>TextMaterials.MAX_CONTEXT_CHARS
        val excerpt=(if(truncated)normalized.take(TextMaterials.MAX_CONTEXT_CHARS) else normalized).trimEnd()
        if(excerpt.toByteArray().size>TextMaterials.MAX_TEXT_BYTES)throw ChatFailure("MATERIAL_TEXT_LIMIT")
        return PromptMaterial(kind,cleanName(name),url,mime,excerpt,normalized.length,truncated,TextMaterials.sha256(excerpt)).also(PromptMaterial::validate)
    }

    private fun read(input:InputStream,max:Int):ByteArray {
        val output=ByteArrayOutputStream();val buffer=ByteArray(8192)
        while(true) {
            val remaining=max+1-output.size()
            if(remaining<=0)throw ChatFailure("MATERIAL_LIMIT")
            val count=input.read(buffer,0,minOf(buffer.size,remaining))
            if(count<0)break
            if(count>0)output.write(buffer,0,count)
        }
        return output.toByteArray()
    }

    private fun strictUtf8(bytes:ByteArray):String=try {
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
    }catch(_:Exception){throw ChatFailure("MATERIAL_ENCODING")}

    private fun cleanName(value:String):String {
        val name=value.substringAfterLast('/').substringAfterLast('\\').trim().replace(Regex("[\\p{Cntrl}]"),"")
        if(name.isBlank()||name.length>TextMaterials.MAX_NAME)throw ChatFailure("MATERIAL_NAME")
        return name
    }

    private fun normalizeText(value:String)=value.replace("\r\n","\n").replace('\r','\n')
        .lines().joinToString("\n"){it.trimEnd()}.trim()

    private fun htmlTitle(html:String)=Regex("(?is)<title(?:\\s[^>]*)?>(.*?)</title>").find(html)?.groupValues?.get(1)?.let(::decodeEntities)?.replace(Regex("\\s+")," ")?.trim()

    private fun htmlText(html:String):String {
        var value=html.replace(Regex("(?is)<(script|style|noscript|svg|template)(?:\\s[^>]*)?>.*?</\\1\\s*>",RegexOption.IGNORE_CASE)," ")
        value=value.replace(Regex("(?i)<(?:br|/p|/div|/li|/h[1-6]|/tr)\\s*/?>"),"\n")
        value=value.replace(Regex("(?s)<[^>]{1,4096}>")," ")
        value=decodeEntities(value)
        return value.lines().map {it.replace(Regex("[ \\t\\x0B\\f]+")," ").trim()}.filter {it.isNotEmpty()}.joinToString("\n")
    }

    private fun decodeEntities(value:String)=Regex("&(#(?:x[0-9a-fA-F]+|[0-9]+)|amp|lt|gt|quot|apos|nbsp);").replace(value){match->
        when(val entity=match.groupValues[1]) {
            "amp"->"&";"lt"->"<";"gt"->">";"quot"->"\"";"apos"->"'";"nbsp"->" "
            else->try {val number=if(entity.startsWith("#x",true))entity.drop(2).toInt(16) else entity.drop(1).toInt();String(Character.toChars(number))}catch(_:Exception){"�"}
        }
    }
}
