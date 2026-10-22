package com.ai.assistance.operit.data.model

import org.json.JSONArray
import org.json.JSONObject

enum class JourneyKind(val wire:String) { CITY("city_walk"), SECRET("secret_date") }
data class JourneyPlace(val name:String,val scene:String)
data class JourneyRecord(val visit:Int,val place:Int,val side:PlaySide?,val text:String)

/** A route is data, never GPS, a URL or a model command. Only the user chooses a destination. */
class JourneyState private constructor(val mode:JourneyKind,val premise:String,val userName:String,val roleName:String,
    places:List<JourneyPlace>,events:List<PlayAction>,val ended:Boolean,val outfits:PlayOutfits?=null):PlayState {
    override val kind=mode.wire
    val places=PlayJson.frozen(places)
    val events=PlayJson.frozen(events)
    val records:List<JourneyRecord>
    val current:Int
    val visited:List<Int>
    val canAdvance:Boolean
    override val turn:PlaySide?
    override val result=if(ended)PlayResult.ENDED else PlayResult.ACTIVE
    init {
        PlayJson.text(premise,2000);PlayJson.text(userName,40);PlayJson.text(roleName,40)
        require(places.size in 2..12&&events.size<=MAX_EVENTS)
        places.forEach {PlayJson.text(it.name,80);PlayJson.text(it.scene,800)}
        var at=0;var next=PlaySide.USER;var visit=0;var answered=false
        val seen=linkedSetOf(0);val history=mutableListOf(JourneyRecord(0,0,null,""))
        var characters=0
        for(event in this.events)when(event) {
            is PlayAction.Visit->{
                require(next==PlaySide.USER&&event.place in places.indices&&event.place!=at)
                if(mode==JourneyKind.SECRET)require(event.place==at+1&&answered)
                at=event.place;seen+=at;visit++;answered=false
                history+=JourneyRecord(visit,at,null,"")
            }
            is PlayAction.Narrate->{
                require(event.side==next);PlayJson.text(event.text,if(next==PlaySide.USER)4000 else 8000)
                characters+=event.text.length;require(characters<=MAX_HISTORY)
                history+=JourneyRecord(visit,at,next,event.text)
                if(next==PlaySide.ROLE)answered=true
                next=next.other()
            }
            else->throw IllegalArgumentException()
        }
        current=at;visited=PlayJson.frozen(seen.toList());records=PlayJson.frozen(history)
        turn=if(ended)null else next
        canAdvance=!ended&&next==PlaySide.USER&&answered&&at<places.lastIndex
        require(encode().toByteArray(Charsets.UTF_8).size<=PlayJson.MAX_BYTES)
    }
    override fun apply(action:PlayAction):JourneyState=try {
        require(!ended)
        when(action) {
            is PlayAction.Visit,is PlayAction.Narrate->JourneyState(mode,premise,userName,roleName,places,events+action,false,outfits).also {
                // Never save a user turn for which even the smallest model reply cannot fit.
                if(it.turn==PlaySide.ROLE&&!it.canAcceptReply())throw ChatFailure("PLAY_LIMIT")
            }
            PlayAction.EndStory->JourneyState(mode,premise,userName,roleName,places,events,true,outfits)
            else->throw IllegalArgumentException()
        }
    }catch(_:IllegalArgumentException){throw ChatFailure("PLAY_ACTION")}
    fun canAcceptReply():Boolean=turn==PlaySide.ROLE&&runCatching {
        JourneyState(mode,premise,userName,roleName,places,events+PlayAction.Narrate(PlaySide.ROLE,"a"),false,outfits)
    }.isSuccess
    fun restart()=start(mode,premise,userName,roleName,places)
    fun isPrefixOf(other:JourneyState)=mode==other.mode&&premise==other.premise&&userName==other.userName&&roleName==other.roleName&&
        outfits==other.outfits&&places==other.places&&other.events.take(events.size)==events&&(!ended||other.ended)
    override fun encode():String=JSONObject().put("schemaVersion",2).put("kind",kind).put("premise",premise)
        .apply {outfits?.let {put("schemaVersion",6);put("outfits",it.encode())}}
        .put("userName",userName).put("roleName",roleName).put("ended",ended)
        .put("places",JSONArray().apply {places.forEach {put(JSONObject().put("name",it.name).put("scene",it.scene))}})
        .put("events",JSONArray().apply {events.forEach {event->put(when(event) {
            is PlayAction.Visit->JSONObject().put("action","visit").put("place",event.place)
            is PlayAction.Narrate->JSONObject().put("action","narrate").put("side",event.side.wire).put("text",event.text)
            else->error("Invalid journey action")
        })}}).toString()
    override fun observation():JSONObject=JSONObject().put("scene",kind).put("fictional",true).put("premise",premise)
        .apply {outfits?.let {put("outfits",it.observation())}}
        .put("participants",JSONObject().put("user",userName).put("role",roleName))
        .put("currentPlace",JSONObject().put("index",current).put("name",places[current].name).put("scene",places[current].scene))
        .put("turn",turn?.wire?:JSONObject.NULL)
        // Future scene descriptions do not enter an invitation before the user reaches that place.
        .put("history",JSONArray().apply {records.forEach {record->put(JSONObject().put("visit",record.visit)
            .put("placeIndex",record.place).put("place",places[record.place].name).put("side",record.side?.wire?:"arrival").put("text",record.text))}})
    internal fun withOutfits(value:PlayOutfits):JourneyState {
        require(events.isEmpty()&&!ended)
        return JourneyState(mode,premise,userName,roleName,places,events,false,value)
    }
    companion object {
        const val MAX_EVENTS=200
        const val MAX_HISTORY=24000
        fun start(mode:JourneyKind,premise:String,userName:String,roleName:String,places:List<JourneyPlace>)=
            JourneyState(mode,premise,userName,roleName,places,emptyList(),false)
        internal fun restore(value:JSONObject):JourneyState {
            val dressed=PlayJson.integer(value,"schemaVersion",2..6)==6
            val fields=arrayOf("schemaVersion","kind","premise","userName","roleName","ended","places","events")
            PlayJson.keys(value,*(if(dressed)fields+"outfits" else fields))
            val places=PlayJson.array(value,"places");require(places.length() in 2..12)
            val events=PlayJson.array(value,"events");require(events.length()<=MAX_EVENTS)
            val ended=value.get("ended");require(ended is Boolean)
            return JourneyState(JourneyKind.entries.single {it.wire==PlayJson.string(value,"kind")},PlayJson.string(value,"premise"),
                PlayJson.string(value,"userName"),PlayJson.string(value,"roleName"),(0 until places.length()).map {i->
                    val p=places.getJSONObject(i);PlayJson.keys(p,"name","scene");JourneyPlace(PlayJson.string(p,"name"),PlayJson.string(p,"scene"))
                },(0 until events.length()).map {i->val event=events.getJSONObject(i)
                    when(PlayJson.string(event,"action")) {
                        "visit"->{PlayJson.keys(event,"action","place");PlayAction.Visit(PlayJson.integer(event,"place",0 until places.length()))}
                        "narrate"->{PlayJson.keys(event,"action","side","text");PlayAction.Narrate(PlayJson.side(PlayJson.string(event,"side")),PlayJson.string(event,"text"))}
                        else->throw IllegalArgumentException()
                    }
                },ended,if(dressed)PlayOutfits.decode(value.getJSONObject("outfits"))else null)
        }
    }
}
