package com.ai.assistance.operit.data.model

/** Keeps short-reply paragraph boundaries identical during streaming, migration, and storage. */
internal object MessageParagraphs {
    private data class Parsed(val parts:List<String>,val completeCount:Int)

    fun split(text:String):List<String> = parse(text).parts

    fun visible(text:String,generating:Boolean):List<String> {
        val parsed=parse(text)
        return if(generating)parsed.parts.take(parsed.completeCount) else parsed.parts
    }

    private fun parse(text:String):Parsed {
        if(text.isBlank())return Parsed(emptyList(),0)
        val result=mutableListOf<String>()
        val paragraph=StringBuilder()
        var fence:String?=null
        val lines=text.replace("\r\n","\n").split('\n')
        lines.forEachIndexed {index,line->
            val trimmed=line.trimStart()
            val marker=when {trimmed.startsWith("```")->"```";trimmed.startsWith("~~~")->"~~~";else->null}
            if(marker!=null) {if(fence==null)fence=marker else if(fence==marker)fence=null}
            if(line.isBlank()&&fence==null&&index<lines.lastIndex) {
                if(paragraph.isNotBlank()){result.add(paragraph.toString().trim());paragraph.clear()}
            } else {if(paragraph.isNotEmpty())paragraph.append('\n');paragraph.append(line)}
        }
        val completeCount=result.size
        if(paragraph.isNotBlank())result.add(paragraph.toString().trim())
        return Parsed(result,completeCount)
    }
}
