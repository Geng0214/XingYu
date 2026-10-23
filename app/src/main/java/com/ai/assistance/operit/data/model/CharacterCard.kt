package com.ai.assistance.operit.data.model

import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import org.json.JSONArray
import org.json.JSONObject

data class CharacterCardLore(val title:String,val content:String,val keywords:List<String>,val priority:Int)

data class CharacterCard(
    val name:String,
    val prompt:String,
    val openingMessage:String,
    val lore:List<CharacterCardLore>,
    val ignoredFields:Int,
    val alternateGreetings:Int
)

sealed interface CharacterCardResult {
    data class Success(val card:CharacterCard):CharacterCardResult
    data class Rejected(val reason:String):CharacterCardResult
}

/** SillyTavern 角色卡（V1 平铺 / V2 data 节点）。超限一律拒绝，不截断。 */
object CharacterCards {
    const val MAX_BYTES=1024*1024
    const val MAX_NAME=40
    const val MAX_PROMPT=8000
    const val MAX_OPENING=8000
    const val MAX_LORE=64
    const val MAX_LORE_CONTENT=4000
    const val MAX_KEYWORDS=20
    const val MAX_KEYWORD=80
    private val rootFields=setOf("spec","spec_version","data","name","description","personality","scenario","first_mes","mes_example","system_prompt","post_history_instructions","alternate_greetings","character_book","avatar","create_date","creator","creator_notes","tags","extensions")

    fun read(bytes:ByteArray):CharacterCardResult {
        if(bytes.size>MAX_BYTES)return CharacterCardResult.Rejected("文件超过 1 MB")
        val text=try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        } catch(_:Exception){return CharacterCardResult.Rejected("文件不是 UTF-8 文本")}
        try {StrictJson(text).validate()} catch(_:Exception){return CharacterCardResult.Rejected("JSON 格式不合法")}
        val root=try {JSONObject(text)} catch(_:Exception){return CharacterCardResult.Rejected("JSON 根节点不是对象")}
        val v2=root.optString("spec")=="chara_card_v2"||root.optJSONObject("data")!=null
        val data=if(v2)root.optJSONObject("data")?:return CharacterCardResult.Rejected("缺少 data 节点") else root
        val name=data.optString("name").trim()
        if(name.isEmpty())return CharacterCardResult.Rejected("缺少角色名")
        if(name.length>MAX_NAME)return CharacterCardResult.Rejected("角色名 ${name.length} 字，超出 $MAX_NAME 字上限")
        val sections=buildList {
            fun add(title:String,key:String) {
                val value=data.optString(key).trim()
                if(value.isNotEmpty())add("【$title】\n$value")
            }
            add("角色描述","description")
            add("性格","personality")
            add("场景","scenario")
            add("系统提示","system_prompt")
            add("对话示例","mes_example")
        }
        val prompt=sections.joinToString("\n\n")
        if(prompt.isBlank())return CharacterCardResult.Rejected("角色卡没有可导入的人设内容")
        if(prompt.length>MAX_PROMPT)return CharacterCardResult.Rejected("人设 ${prompt.length} 字，超出 $MAX_PROMPT 字上限")
        val opening=data.optString("first_mes")
        if(opening.length>MAX_OPENING)return CharacterCardResult.Rejected("开场白 ${opening.length} 字，超出 $MAX_OPENING 字上限")
        val alternates=data.optJSONArray("alternate_greetings")?.length()?:0
        val entries=data.optJSONObject("character_book")?.optJSONArray("entries")?:JSONArray()
        if(entries.length()>MAX_LORE)return CharacterCardResult.Rejected("世界书 ${entries.length()} 条，超出 $MAX_LORE 条上限")
        val lore=mutableListOf<CharacterCardLore>()
        for(i in 0 until entries.length()) {
            val entry=entries.optJSONObject(i) ?: continue
            val content=entry.optString("content").trim()
            if(content.isEmpty())continue
            if(content.length>MAX_LORE_CONTENT)return CharacterCardResult.Rejected("世界书第 ${i+1} 条 ${content.length} 字，超出 $MAX_LORE_CONTENT 字上限")
            val keys=entry.optJSONArray("keys")
            val keywords=buildList {
                if(keys!=null)for(k in 0 until keys.length()) {
                    val value=keys.optString(k).trim()
                    if(value.isNotEmpty())add(value)
                }
            }
            if(keywords.size>MAX_KEYWORDS)return CharacterCardResult.Rejected("世界书第 ${i+1} 条有 ${keywords.size} 个关键词，超出 $MAX_KEYWORDS 个上限")
            if(keywords.any {it.length>MAX_KEYWORD})return CharacterCardResult.Rejected("世界书第 ${i+1} 条有关键词超过 $MAX_KEYWORD 字")
            val rawTitle=entry.optString("name").trim().ifEmpty {entry.optString("comment").trim()}
            val title=if(rawTitle.length in 1..80)rawTitle else content.take(20)
            lore+=CharacterCardLore(title,content,keywords,entry.optInt("insertion_order",50).coerceIn(0,100))
        }
        val ignored=root.keys().asSequence().count {it !in rootFields}
        return CharacterCardResult.Success(CharacterCard(name,prompt,opening,lore,ignored,alternates))
    }
}
