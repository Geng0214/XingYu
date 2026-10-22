package com.ai.assistance.operit.data.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections

enum class PlaySide(val wire:String) { USER("user"), ROLE("role"); fun other()=if(this==USER)ROLE else USER }
enum class PlayResult { ACTIVE, USER_WIN, ROLE_WIN, DRAW, ENDED }

/** Immutable rule state. Persistence encoding may contain secrets; only observation() goes to models. */
sealed interface PlayState {
    val kind:String
    val turn:PlaySide?
    val result:PlayResult
    fun encode():String
    fun observation():JSONObject
    fun apply(action:PlayAction):PlayState
}

sealed interface PlayAction {
    data class Place(val side:PlaySide,val row:Int,val column:Int):PlayAction
    data class Flip(val side:PlaySide,val index:Int):PlayAction
    data object HidePair:PlayAction
    data class Narrate(val side:PlaySide,val text:String):PlayAction
    data object EndStory:PlayAction
    data class Visit(val place:Int):PlayAction
    data class Word(val move:WordMove):PlayAction
    data class Card(val move:CardMove):PlayAction
    data class Chess(val side:PlaySide,val move:XqMove):PlayAction
    data class ResignChess(val side:PlaySide):PlayAction
    data class Adventure(val move:AdventureAction):PlayAction
}

internal object PlayJson {
    const val MAX_BYTES=131072
    fun objectValue(text:String):JSONObject {
        require(text.length<=MAX_BYTES&&text.toByteArray(Charsets.UTF_8).size<=MAX_BYTES)
        StrictJson(text).validate();return JSONObject(text)
    }
    fun keys(value:JSONObject,vararg keys:String){require(value.keys().asSequence().toSet()==keys.toSet())}
    fun integer(value:JSONObject,key:String,range:IntRange):Int {
        val n=value.get(key);require(n is Int||n is Long)
        return (n as Number).toLong().also {require(it in range.first.toLong()..range.last.toLong())}.toInt()
    }
    fun string(value:JSONObject,key:String):String {val s=value.get(key);require(s is String);return s}
    fun array(value:JSONObject,key:String):JSONArray {val a=value.get(key);require(a is JSONArray);return a}
    fun side(value:String)=PlaySide.entries.single {it.wire==value}
    fun text(value:String,limit:Int,blank:Boolean=false) {
        require((blank||value.isNotBlank())&&value.length<=limit&&'\u0000' !in value)
        var i=0
        while(i<value.length){val c=value[i++];if(c.isHighSurrogate()){require(i<value.length&&value[i++].isLowSurrogate())}else require(!c.isLowSurrogate())}
    }
    fun <T> frozen(values:List<T>):List<T> = Collections.unmodifiableList(values.toList())
    fun base(kind:String)=JSONObject().put("schemaVersion",1).put("kind",kind)
}

object PlayStates {
    fun decode(text:String):PlayState=try {
        val value=PlayJson.objectValue(text)
        val version=PlayJson.integer(value,"schemaVersion",1..6)
        if(version==6)when(PlayJson.string(value,"kind")) {
            "date"->DateStoryState.restore(value)
            "city_walk","secret_date"->JourneyState.restore(value)
            else->throw IllegalArgumentException()
        }else if(version==5) {
            PlayJson.keys(value,"schemaVersion","kind","game")
            require(PlayJson.string(value,"kind")=="pixel_adventure")
            AdventurePlayState(AdventureGame.decode(value.getJSONObject("game").toString()))
        }else if(version==4) {
            PlayJson.keys(value,"schemaVersion","kind","userColor","game")
            require(PlayJson.string(value,"kind")=="xiangqi")
            XiangqiPlayState(XqColor.valueOf(PlayJson.string(value,"userColor")),XiangqiGame.decode(value.getJSONObject("game").toString()))
        }else if(version==3) {
            PlayJson.keys(value,"schemaVersion","kind","game")
            when(PlayJson.string(value,"kind")) {
                "word_guess"->WordPlayState(WordGuessGame.decode(value.getJSONObject("game").toString()))
                "four_color"->CardPlayState(FourColorGame.decode(value.getJSONObject("game").toString()))
                else->throw IllegalArgumentException()
            }
        }else if(version==2)JourneyState.restore(value)else when(PlayJson.string(value,"kind")) {
            "gomoku"->GomokuState.restore(value)
            "memory"->MemoryPairsState.restore(value)
            "date"->DateStoryState.restore(value)
            else->throw IllegalArgumentException()
        }
    }catch(_:Exception){throw ChatFailure("PLAY_FORMAT")}
}
