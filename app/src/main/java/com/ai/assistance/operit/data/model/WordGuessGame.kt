package com.ai.assistance.operit.data.model

import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.text.Normalizer
import java.util.Locale

sealed interface WordMove {
    val side:PlaySide
    data class Clue(override val side:PlaySide,val text:String):WordMove
    data class Guess(override val side:PlaySide,val text:String):WordMove
    data class GiveUp(override val side:PlaySide):WordMove
}
data class WordPuzzle(val category:String,val answer:String,val aliases:List<String> = emptyList())

/** Hidden answers are held only in the save; observation(viewer) is the sole network/UI projection. */
class WordGuessGame private constructor(val setter:PlaySide,val category:String,private val answer:String,
    aliases:List<String>,val source:String,moves:List<WordMove>) {
    private val aliases=PlayJson.frozen(aliases)
    val moves=PlayJson.frozen(moves)
    val guesses:Int
    val result:PlayResult
    val turn:PlaySide?
    val solved:Boolean
    init {
        PlayJson.text(category,80);PlayJson.text(answer,80)
        require(source in setOf("builtin-v1","custom")&&aliases.size<=8&&moves.size<=MAX_GUESSES*2)
        aliases.forEach {PlayJson.text(it,80)}
        val solutions=(listOf(answer)+aliases).map(::normalize)
        require(solutions.all {it.isNotEmpty()}&&solutions.distinct().size==solutions.size)
        var next=setter;var count=0;var outcome=PlayResult.ACTIVE;var correct=false
        this.moves.forEach {move->
            require(outcome==PlayResult.ACTIVE&&move.side==next)
            when(move) {
                is WordMove.Clue->{
                    require(move.side==setter);validateClueText(move.text)
                    next=setter.other()
                }
                is WordMove.Guess->{
                    require(move.side!=setter);PlayJson.text(move.text,80);count++
                    correct=normalize(move.text) in solutions
                    if(correct)outcome=winner(move.side)
                    else if(count==MAX_GUESSES)outcome=winner(setter)
                    else next=setter
                }
                is WordMove.GiveUp->{require(move.side!=setter);outcome=winner(setter)}
            }
        }
        guesses=count;result=outcome;turn=if(outcome==PlayResult.ACTIVE)next else null;solved=correct
    }
    fun apply(move:WordMove):WordGuessGame=try {
        WordGuessGame(setter,category,answer,aliases,source,moves+move)
    }catch(_:IllegalArgumentException){throw ChatFailure("PLAY_ACTION")}
    internal fun validateClueText(text:String,limit:Int=600,blank:Boolean=false) {
        PlayJson.text(text,limit,blank)
        val normalized=normalize(text)
        require((listOf(answer)+aliases).none {normalized.contains(normalize(it))})
    }
    fun revealedAnswer():String?=if(result==PlayResult.ACTIVE)null else answer
    fun observation(viewer:PlaySide):JSONObject=JSONObject().put("game","word_guess").put("setter",setter.wire)
        .put("category",category).put("source",source).put("remainingGuesses",MAX_GUESSES-guesses)
        .put("turn",turn?.wire?:JSONObject.NULL).put("result",result.name).put("solved",solved)
        .put("history",encodeMoves()).apply {
            if(viewer==setter||result!=PlayResult.ACTIVE){put("answer",answer);put("aliases",JSONArray(aliases))}
        }
    fun encode():String=JSONObject().put("schemaVersion",1).put("kind","word_guess").put("setter",setter.wire)
        .put("category",category).put("answer",answer).put("aliases",JSONArray(aliases)).put("source",source)
        .put("moves",encodeMoves()).toString().also {require(it.toByteArray(Charsets.UTF_8).size<=PlayJson.MAX_BYTES)}
    private fun encodeMoves()=JSONArray().apply {moves.forEach {m->put(JSONObject().put("side",m.side.wire).apply {
        when(m){is WordMove.Clue->put("action","clue").put("text",m.text);is WordMove.Guess->put("action","guess").put("text",m.text);is WordMove.GiveUp->put("action","give_up")}
    })}}
    fun isPrefixOf(other:WordGuessGame)=setter==other.setter&&category==other.category&&answer==other.answer&&aliases==other.aliases&&source==other.source&&other.moves.take(moves.size)==moves
    companion object {
        const val MAX_GUESSES=8
        fun normalize(text:String)=Normalizer.normalize(text,Normalizer.Form.NFKC).trim().lowercase(Locale.ROOT)
        private fun winner(side:PlaySide)=if(side==PlaySide.USER)PlayResult.USER_WIN else PlayResult.ROLE_WIN
        fun start(setter:PlaySide,puzzle:WordPuzzle,source:String="custom")=WordGuessGame(setter,puzzle.category,puzzle.answer,puzzle.aliases,source,emptyList())
        fun decode(text:String):WordGuessGame=try {
            val v=PlayJson.objectValue(text);PlayJson.keys(v,"schemaVersion","kind","setter","category","answer","aliases","source","moves")
            PlayJson.integer(v,"schemaVersion",1..1);require(PlayJson.string(v,"kind")=="word_guess")
            val a=PlayJson.array(v,"aliases");require(a.length()<=8)
            val aliases=(0 until a.length()).map {a.get(it).also {x->require(x is String)} as String}
            val list=PlayJson.array(v,"moves");require(list.length()<=MAX_GUESSES*2)
            val moves=(0 until list.length()).map {i->val m=list.getJSONObject(i);val side=PlayJson.side(PlayJson.string(m,"side"))
                when(PlayJson.string(m,"action")) {
                    "clue"->{PlayJson.keys(m,"action","side","text");WordMove.Clue(side,PlayJson.string(m,"text"))}
                    "guess"->{PlayJson.keys(m,"action","side","text");WordMove.Guess(side,PlayJson.string(m,"text"))}
                    "give_up"->{PlayJson.keys(m,"action","side");WordMove.GiveUp(side)}
                    else->throw IllegalArgumentException()
                }
            }
            WordGuessGame(PlayJson.side(PlayJson.string(v,"setter")),PlayJson.string(v,"category"),PlayJson.string(v,"answer"),aliases,PlayJson.string(v,"source"),moves)
        }catch(_:Exception){throw ChatFailure("PLAY_FORMAT")}
    }
}

/** Original, hand-written starter vocabulary. A save freezes one puzzle, not the full catalog. */
object WordPuzzleCatalog {
    private val puzzles=listOf(
        WordPuzzle("日常物品","雨伞",listOf("伞")),WordPuzzle("日常物品","闹钟"),WordPuzzle("日常物品","钥匙"),
        WordPuzzle("日常物品","书签"),WordPuzzle("日常物品","背包",listOf("双肩包")),WordPuzzle("日常物品","眼镜"),
        WordPuzzle("日常物品","蜡烛"),WordPuzzle("日常物品","相机",listOf("照相机")),WordPuzzle("日常物品","枕头"),
        WordPuzzle("自然","彩虹"),WordPuzzle("自然","雪花"),WordPuzzle("自然","月亮",listOf("月球")),
        WordPuzzle("自然","瀑布"),WordPuzzle("自然","萤火虫"),WordPuzzle("自然","向日葵"),WordPuzzle("自然","珊瑚"),
        WordPuzzle("活动","露营"),WordPuzzle("活动","游泳"),WordPuzzle("活动","下棋"),WordPuzzle("活动","烘焙"),
        WordPuzzle("活动","放风筝"),WordPuzzle("活动","钓鱼"),WordPuzzle("活动","滑冰"),WordPuzzle("活动","拼图"))
    fun start(setter:PlaySide,random:SecureRandom=SecureRandom())=WordGuessGame.start(setter,puzzles[random.nextInt(puzzles.size)],"builtin-v1")
    val size get()=puzzles.size
}
