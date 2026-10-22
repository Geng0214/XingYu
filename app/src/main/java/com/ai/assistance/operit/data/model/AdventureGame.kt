package com.ai.assistance.operit.data.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections

sealed interface AdventureAction {
    data class Move(val direction:AdventureDirection):AdventureAction
    data class Interact(val objectId:String):AdventureAction
    data class Say(val side:PlaySide,val text:String):AdventureAction
    data object SkipDialogue:AdventureAction
    data object Finish:AdventureAction
}
data class AdventureRecord(val position:AdventurePosition,val side:PlaySide?,val text:String)
data class AdventureInteraction(val target:AdventureObject,val enabled:Boolean,val reason:String)

/** Authoritative state is replayed from user movement/interactions and confirmed dialogue only. */
class AdventureGame private constructor(val userName:String,val roleName:String,events:List<AdventureAction>,private val state:AdventureProgress) {
    val events=PlayJson.frozen(events)
    val position get()=state.position
    val companion get()=state.companion
    val inventory get()=state.inventory
    val flags get()=state.flags
    val picked get()=state.picked
    val explored get()=state.explored
    val records get()=state.records
    val ended get()=state.ended
    val turn get()=if(ended)null else state.turn
    val roleActionCount get()=events.count {it is AdventureAction.Say&&it.side==PlaySide.ROLE}
    val objective get()=when {
        AdventureFlag.LIT in flags->"灯塔已经点亮。可以继续探索、归还围巾，或结束本次冒险。"
        AdventureFlag.BRIDGE !in flags->"找到两块木板和一根绳索，在河岸修复木桥。"
        AdventureFlag.LANTERN !in flags->"探索林地，找到灯罩和萤火种，在工作台制作提灯。"
        AdventureItem.CRYSTAL !in inventory->"带着提灯进入旧塔，寻找星光灯芯。"
        else->"把提灯和星光灯芯带回村庄，点亮海边灯塔。"
    }
    fun active(target:AdventureObject)=state.active(target)
    fun visible(target:AdventureObject)=target.kind!=AdventureObjectKind.PICKUP||active(target)
    fun objectLabel(target:AdventureObject)=target.title+when {
        active(target)->""
        target.kind==AdventureObjectKind.LIGHTHOUSE->" · 已点亮"
        target.kind==AdventureObjectKind.BRIDGE->" · 已修复"
        target.kind==AdventureObjectKind.WORKBENCH->" · 提灯已制作"
        target.kind==AdventureObjectKind.SWITCH->" · 已开启"
        else->""
    }
    fun capacityReason(action:AdventureAction):String {
        val reply=action is AdventureAction.Say&&action.side==PlaySide.USER
        val steps=if(reply)2 else 1
        val notes=if(action is AdventureAction.Move)0 else steps
        if(events.size+steps>MAX_EVENTS)return "本局动作记录已满，无法再写入。可以查看记录、暂停或新建冒险。"
        if(records.size+notes>MAX_RECORDS)return "本局事件与对白记录已满，无法再写入。可以继续移动、查看记录或新建冒险。"
        if(reply&&state.characters+(action as AdventureAction.Say).text.length+MAX_REPLY>MAX_CHARACTERS)
            return "本局对白字数不足以保存完整回复。可以继续探索，或新建冒险后再聊。"
        return try {apply(action);""}catch(_:IllegalArgumentException){"当前动作无法写入，或存档容量不足。已填写的对白保留，可以查看记录或新建冒险。"}
    }
    fun canMove(direction:AdventureDirection)=!ended&&turn==PlaySide.USER&&events.size<MAX_EVENTS&&state.walkable(position.step(direction))
    fun nearby():List<AdventureInteraction> = AdventureWorld.objects.filter {active(it)&&it.position.distance(position)<=1}.map {target->
        val reason=when {events.size>=MAX_EVENTS->"本局动作记录已满";records.size>=MAX_RECORDS->"本局事件记录已满";else->state.unavailable(target)}
        AdventureInteraction(target,!ended&&turn==PlaySide.USER&&reason.isEmpty(),when {ended->"本次冒险已结束";turn!=PlaySide.USER->"请先完成本次对白";else->reason})
    }
    fun apply(action:AdventureAction):AdventureGame {
        require(events.size<MAX_EVENTS)
        val next=AdventureGame(userName,roleName,events+action,state.apply(action))
        next.checkSize()
        // Reserve a complete maximum-sized reply before accepting a paid dialogue invitation.
        if(action is AdventureAction.Say&&action.side==PlaySide.USER)require(next.canAcceptReply())
        return next
    }
    fun canAcceptReply():Boolean=turn==PlaySide.ROLE&&events.size<MAX_EVENTS&&records.size<MAX_RECORDS&&state.characters+MAX_REPLY<=MAX_CHARACTERS&&
        encode().toByteArray(Charsets.UTF_8).size+MAX_REPLY*6+64<=MAX_BYTES
    fun isPrefixOf(other:AdventureGame)=userName==other.userName&&roleName==other.roleName&&other.events.take(events.size)==events
    fun restart()=start(userName,roleName)
    fun encode():String=JSONObject().put("schemaVersion",1).put("world",AdventureWorld.ID).put("userName",userName).put("roleName",roleName)
        .put("events",JSONArray().apply {events.forEach {event->put(when(event) {
            is AdventureAction.Move->JSONArray().put("move").put(event.direction.name)
            is AdventureAction.Interact->JSONArray().put("interact").put(event.objectId)
            is AdventureAction.Say->JSONArray().put("say").put(event.side.wire).put(event.text)
            AdventureAction.SkipDialogue->JSONArray().put("skip_dialogue")
            AdventureAction.Finish->JSONArray().put("finish")
        })}}).toString()
    private fun checkSize(){require(encode().toByteArray(Charsets.UTF_8).size<=MAX_BYTES)}
    fun observation():JSONObject {
        val visible=explored.getValue(position.area)
        return JSONObject().put("game","pixel_adventure").put("world",AdventureWorld.ID).put("fictional",true)
            .put("participants",JSONObject().put("user",userName).put("role",roleName))
            .put("place",position.area.title).put("position",coordinates(position)).put("companion",coordinates(companion))
            .put("objective",objective).put("turn",turn?.wire?:JSONObject.NULL).put("ended",ended)
            .put("inventory",JSONArray().apply {inventory.forEach {(item,count)->put(JSONObject().put("item",item.title).put("count",count))}})
            .put("exploredTiles",JSONArray().apply {visible.sorted().forEach {index->
                val p=AdventurePosition(position.area,index%AdventureWorld.WIDTH,index/AdventureWorld.WIDTH)
                put(coordinates(p).put("terrain",AdventureWorld.groundLabel(p,flags)))
            }})
            .put("objects",JSONArray().apply {AdventureWorld.objects.filter {it.position.area==position.area&&it.position.index in visible&&visible(it)}.forEach {
                put(coordinates(it.position).put("name",objectLabel(it)))
            }})
            .put("history",JSONArray().apply {records.takeLast(24).forEach {r->put(JSONObject().put("place",r.position.area.title)
                .put("side",r.side?.wire?:"event").put("text",r.text))}})
    }
    companion object {
        // Leave space for the PlayState envelope; otherwise a valid paid reply could exceed the stored row limit.
        private const val MAX_BYTES=PlayJson.MAX_BYTES-256
        const val MAX_EVENTS=4096
        const val MAX_REPLY=2000
        const val MAX_USER_TEXT=1000
        const val MAX_CHARACTERS=16000
        const val MAX_RECORDS=512
        internal fun <K,V> map(values:Map<K,V>):Map<K,V> = Collections.unmodifiableMap(LinkedHashMap(values))
        internal fun <T> set(values:Set<T>):Set<T> = Collections.unmodifiableSet(LinkedHashSet(values))
        private fun coordinates(p:AdventurePosition)=JSONObject().put("x",p.x).put("y",p.y)
        fun start(userName:String="我",roleName:String="同行者"):AdventureGame {
            PlayJson.text(userName,40);PlayJson.text(roleName,40)
            return AdventureGame(userName,roleName,emptyList(),AdventureProgress.start())
        }
        fun decode(text:String):AdventureGame {
            val value=PlayJson.objectValue(text)
            PlayJson.keys(value,"schemaVersion","world","userName","roleName","events")
            require(PlayJson.integer(value,"schemaVersion",1..1)==1&&PlayJson.string(value,"world")==AdventureWorld.ID)
            val user=PlayJson.string(value,"userName");val role=PlayJson.string(value,"roleName")
            PlayJson.text(user,40);PlayJson.text(role,40)
            val input=PlayJson.array(value,"events");require(input.length()<=MAX_EVENTS)
            val events=(0 until input.length()).map {i->
                val e=input.getJSONArray(i);require(e.length() in 1..3)
                fun s(n:Int):String {val raw=e.get(n);require(raw is String);return raw}
                when(s(0)) {
                    "move"->{require(e.length()==2);AdventureAction.Move(AdventureDirection.valueOf(s(1)))}
                    "interact"->{require(e.length()==2);AdventureAction.Interact(s(1))}
                    "say"->{require(e.length()==3);AdventureAction.Say(PlayJson.side(s(1)),s(2))}
                    "finish"->{require(e.length()==1);AdventureAction.Finish}
                    "skip_dialogue"->{require(e.length()==1);AdventureAction.SkipDialogue}
                    else->throw IllegalArgumentException()
                }
            }
            var progress=AdventureProgress.start()
            events.forEachIndexed {index,event->
                progress=progress.apply(event)
                if(event is AdventureAction.Say&&event.side==PlaySide.USER)
                    require(AdventureGame(user,role,events.take(index+1),progress).canAcceptReply())
            }
            return AdventureGame(user,role,events,progress).also {it.checkSize()}
        }
    }
}
