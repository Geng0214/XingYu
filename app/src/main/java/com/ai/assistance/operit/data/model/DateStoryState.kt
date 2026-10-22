package com.ai.assistance.operit.data.model

import org.json.JSONArray
import org.json.JSONObject

/** Confirmed scene turns only. Partial/failed model output belongs to a separate request record. */
class DateStoryState private constructor(val place:String,val premise:String,turns:List<PlayAction.Narrate>,val ended:Boolean,
    val outfits:PlayOutfits?=null):PlayState {
    override val kind="date"
    val turns=PlayJson.frozen(turns)
    override val turn=if(ended)null else if(turns.size%2==0)PlaySide.USER else PlaySide.ROLE
    override val result=if(ended)PlayResult.ENDED else PlayResult.ACTIVE
    init {
        PlayJson.text(place,120);PlayJson.text(premise,2000)
        require(turns.size<=MAX_TURNS)
        turns.forEachIndexed {i,t->require(t.side==if(i%2==0)PlaySide.USER else PlaySide.ROLE);PlayJson.text(t.text,if(t.side==PlaySide.USER)4000 else 8000)}
        require(turns.sumOf {it.text.length}<=MAX_HISTORY)
        // Escaped control characters can be much larger than their UTF-16 character count.
        require(encode().toByteArray(Charsets.UTF_8).size<=PlayJson.MAX_BYTES)
    }
    override fun apply(action:PlayAction):DateStoryState=try {
        require(!ended)
        when(action) {
            is PlayAction.Narrate->{require(action.side==turn);DateStoryState(place,premise,turns+action,false,outfits)}
            PlayAction.EndStory->DateStoryState(place,premise,turns,true,outfits)
            else->throw IllegalArgumentException()
        }
    }catch(_:IllegalArgumentException){throw ChatFailure("PLAY_ACTION")}
    override fun encode():String=PlayJson.base(kind).put("place",place).put("premise",premise).put("ended",ended)
        .apply {outfits?.let {put("schemaVersion",6);put("outfits",it.encode())}}
        .put("turns",JSONArray().apply {turns.forEach {put(JSONObject().put("side",it.side.wire).put("text",it.text))}}).toString()
    override fun observation():JSONObject=JSONObject().put("scene",kind).put("fictional",true).put("place",place).put("premise",premise)
        .apply {outfits?.let {put("outfits",it.observation())}}
        .put("turn",turn?.wire?:JSONObject.NULL).put("turns",JSONArray().apply {turns.forEach {put(JSONObject().put("side",it.side.wire).put("text",it.text))}})
    internal fun withOutfits(value:PlayOutfits):DateStoryState {
        require(turns.isEmpty()&&!ended)
        return DateStoryState(place,premise,turns,false,value)
    }
    companion object {
        const val MAX_TURNS=100
        const val MAX_HISTORY=24000
        fun start(place:String,premise:String)=DateStoryState(place,premise,emptyList(),false)
        internal fun restore(value:JSONObject):DateStoryState {
            val dressed=PlayJson.integer(value,"schemaVersion",1..6)==6
            val fields=arrayOf("schemaVersion","kind","place","premise","ended","turns")
            PlayJson.keys(value,*(if(dressed)fields+"outfits" else fields))
            val list=PlayJson.array(value,"turns");require(list.length()<=MAX_TURNS)
            val ended=value.get("ended");require(ended is Boolean)
            return DateStoryState(PlayJson.string(value,"place"),PlayJson.string(value,"premise"),(0 until list.length()).map {i->
                val t=list.getJSONObject(i);PlayJson.keys(t,"side","text");PlayAction.Narrate(PlayJson.side(PlayJson.string(t,"side")),PlayJson.string(t,"text"))
            },ended,if(dressed)PlayOutfits.decode(value.getJSONObject("outfits"))else null)
        }
    }
}
