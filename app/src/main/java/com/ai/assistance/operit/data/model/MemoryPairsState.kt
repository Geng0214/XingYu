package com.ai.assistance.operit.data.model

import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

/** Match retains the turn; a mismatch stays visible until HidePair, then the other side plays. */
class MemoryPairsState private constructor(deck:List<Int>,val first:PlaySide,actions:List<PlayAction>):PlayState {
    override val kind="memory"
    private val deck=PlayJson.frozen(deck)
    val actions=PlayJson.frozen(actions)
    val size get()=deck.size
    val open:List<Int>
    val matched:List<Int>
    val seen:Map<Int,Int>
    val userPairs:Int
    val rolePairs:Int
    override val turn:PlaySide?
    override val result:PlayResult
    init {
        require(deck.size in 4..24&&deck.size%2==0&&actions.size<=MAX_ACTIONS)
        require(deck.toSet()==(0 until deck.size/2).toSet()&&deck.groupingBy {it}.eachCount().values.all {it==2})
        val revealed=mutableListOf<Int>();val found=mutableSetOf<Int>();val known=mutableMapOf<Int,Int>()
        var next=first;var user=0;var role=0
        for(action in this.actions) {
            require(found.size<deck.size)
            when(action) {
                is PlayAction.Flip->{
                    require(action.side==next&&revealed.size<2&&action.index in deck.indices&&action.index !in found&&action.index !in revealed)
                    revealed.add(action.index);known[action.index]=deck[action.index]
                    if(revealed.size==2&&deck[revealed[0]]==deck[revealed[1]]) {
                        found.addAll(revealed);revealed.clear();if(next==PlaySide.USER)user++ else role++
                    }
                }
                PlayAction.HidePair->{require(revealed.size==2);revealed.clear();next=next.other()}
                else->throw IllegalArgumentException()
            }
        }
        open=PlayJson.frozen(revealed);matched=PlayJson.frozen(found.sorted());seen=java.util.Collections.unmodifiableMap(known.toMap())
        userPairs=user;rolePairs=role
        result=if(found.size<deck.size)PlayResult.ACTIVE else when {user>role->PlayResult.USER_WIN;role>user->PlayResult.ROLE_WIN;else->PlayResult.DRAW}
        turn=if(result==PlayResult.ACTIVE)next else null
    }
    /** Only a currently revealed/matched face can be painted; previously seen faces live in history. */
    fun face(index:Int):Int? {require(index in deck.indices);return if(index in open||index in matched)deck[index] else null}
    override fun apply(action:PlayAction):MemoryPairsState=try {MemoryPairsState(deck,first,actions+action)}catch(_:IllegalArgumentException){throw ChatFailure("PLAY_ACTION")}
    override fun encode():String=PlayJson.base(kind).put("deck",JSONArray(deck)).put("first",first.wire)
        .put("actions",JSONArray().apply {actions.forEach {action->put(when(action){
            is PlayAction.Flip->JSONObject().put("action","flip").put("side",action.side.wire).put("index",action.index)
            PlayAction.HidePair->JSONObject().put("action","hide")
            else->throw IllegalStateException()
        })}}).toString().also {require(it.toByteArray(Charsets.UTF_8).size<=PlayJson.MAX_BYTES)}
    override fun observation():JSONObject=JSONObject().put("game",kind).put("size",size).put("turn",turn?.wire?:JSONObject.NULL)
        .put("result",result.name).put("phase",if(open.size==2)"reveal" else "choose").put("open",JSONArray(open))
        .put("matched",JSONArray(matched)).put("seen",JSONObject().apply {seen.forEach {(index,face)->put(index.toString(),face)}})
        .put("userPairs",userPairs).put("rolePairs",rolePairs)
        .put("legalIndices",JSONArray(if(open.size<2&&result==PlayResult.ACTIVE)deck.indices.filter {it !in open&&it !in matched} else emptyList<Int>()))
    companion object {
        const val MAX_ACTIONS=2048
        fun start(pairs:Int=8,first:PlaySide=PlaySide.USER,random:SecureRandom=SecureRandom()):MemoryPairsState {
            require(pairs in 2..12)
            val deck=MutableList(pairs*2){it/2}
            for(i in deck.lastIndex downTo 1){val j=random.nextInt(i+1);val old=deck[i];deck[i]=deck[j];deck[j]=old}
            return MemoryPairsState(deck,first,emptyList())
        }
        internal fun restore(value:JSONObject):MemoryPairsState {
            PlayJson.keys(value,"schemaVersion","kind","deck","first","actions")
            val cards=PlayJson.array(value,"deck");require(cards.length() in 4..24)
            val deck=(0 until cards.length()).map {i->val x=cards.get(i);require(x is Int&&x in 0..11);x}
            val list=PlayJson.array(value,"actions");require(list.length()<=MAX_ACTIONS)
            val actions=(0 until list.length()).map {i->val action=list.getJSONObject(i)
                when(PlayJson.string(action,"action")) {
                    "flip"->{PlayJson.keys(action,"action","side","index");PlayAction.Flip(PlayJson.side(PlayJson.string(action,"side")),PlayJson.integer(action,"index",0 until cards.length()))}
                    "hide"->{PlayJson.keys(action,"action");PlayAction.HidePair}
                    else->throw IllegalArgumentException()
                }
            }
            return MemoryPairsState(deck,PlayJson.side(PlayJson.string(value,"first")),actions)
        }
    }
}
