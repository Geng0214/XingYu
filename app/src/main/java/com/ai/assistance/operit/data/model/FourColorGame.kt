package com.ai.assistance.operit.data.model

import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.security.SecureRandom

enum class CardColor(val wire:String) { RED("red"), YELLOW("yellow"), GREEN("green"), BLUE("blue") }
/** Face 0..9 are numbers, 10 skip, 11 reverse, 12 draw two, 13 wild, 14 wild draw four. */
data class ColorCard(val id:Int,val color:CardColor?,val face:Int) {
    val points get()=if(face<=9)face else if(face<=12)20 else 50
    fun json()=JSONObject().put("id",id).put("color",color?.wire?:JSONObject.NULL).put("face",face)
}
sealed interface CardMove {
    val side:PlaySide
    data class Play(override val side:PlaySide,val card:Int,val color:CardColor?=null):CardMove
    data class Draw(override val side:PlaySide):CardMove
    data class Pass(override val side:PlaySide):CardMove
}
data class CardPublicEvent(val side:PlaySide,val action:String,val card:Int?,val color:CardColor?,val drawn:Int)

/** Complete replayable save. Only observation(viewer) may be used as a model or page projection. */
class FourColorGame private constructor(deck:List<Int>,private val shuffleKey:String,val first:PlaySide,moves:List<CardMove>) {
    private val deck=PlayJson.frozen(deck)
    val moves=PlayJson.frozen(moves)
    private val userHand:List<Int>
    private val roleHand:List<Int>
    val top:ColorCard
    val color:CardColor
    val turn:PlaySide?
    val result:PlayResult
    val score:Int
    val drawCount:Int
    val discardCount:Int
    val reshuffles:Int
    private val drawnCard:Int?
    val history:List<CardPublicEvent>
    init {
        require(deck.size==108&&deck.toSet()==(0..107).toSet())
        require(shuffleKey.matches(Regex("[0-9a-f]{64}"))&&moves.size<=MAX_MOVES)
        val hands=mapOf(PlaySide.USER to mutableListOf<Int>(),PlaySide.ROLE to mutableListOf())
        repeat(14){i->hands.getValue(if(i%2==0)first else first.other()).add(deck[i])}
        val draw=deck.drop(14).toMutableList()
        val start=draw.indexOfFirst {card(it).face<=9};require(start>=0)
        val discard=mutableListOf(draw.removeAt(start));var activeColor=requireNotNull(card(discard.last()).color)
        var next=first;var pending:Int?=null;var outcome=PlayResult.ACTIVE;var points=0;var cycle=0;var stalled=0
        val public=mutableListOf<CardPublicEvent>()
        fun legal(id:Int,side:PlaySide):Boolean {
            val c=card(id)
            return when(c.face){14->hands.getValue(side).none {card(it).color==activeColor};13->true;else->c.color==activeColor||c.face==card(discard.last()).face}
        }
        fun take(side:PlaySide,amount:Int):List<Int> {
            val taken=mutableListOf<Int>()
            repeat(amount) {
                if(draw.isEmpty()&&discard.size>1) {
                    cycle++
                    // Secret per-save entropy and cycle make replay stable without exposing future cards.
                    draw.addAll(discard.dropLast(1).sortedBy {id->hash("$shuffleKey:$cycle:$id")})
                    val last=discard.last();discard.clear();discard.add(last)
                }
                if(draw.isNotEmpty()){val id=draw.removeAt(0);hands.getValue(side).add(id);taken.add(id)}
            }
            return taken
        }
        this.moves.forEach {move->
            require(outcome==PlayResult.ACTIVE&&move.side==next)
            val hand=hands.getValue(next)
            when(move) {
                is CardMove.Play->{
                    require(move.card in hand&&(pending==null||move.card==pending)&&legal(move.card,next))
                    val c=card(move.card)
                    require(if(c.color==null)move.color!=null else move.color==null)
                    hand.remove(move.card);discard.add(move.card);activeColor=c.color?:requireNotNull(move.color)
                    pending=null;stalled=0
                    val penalty=when(c.face){12->2;14->4;else->0}
                    val actual=take(next.other(),penalty).size
                    public+=CardPublicEvent(next,"play",c.id,activeColor,actual)
                    if(hand.isEmpty()) {
                        outcome=if(next==PlaySide.USER)PlayResult.USER_WIN else PlayResult.ROLE_WIN
                        points=hands.getValue(next.other()).sumOf {card(it).points}
                    }else if(c.face !in setOf(10,11,12,14))next=next.other()
                }
                is CardMove.Draw->{
                    require(pending==null)
                    val taken=take(next,1)
                    public+=CardPublicEvent(next,"draw",null,null,taken.size)
                    if(taken.isEmpty()) {
                        stalled=if(hand.none {legal(it,next)})stalled+1 else 0
                        if(stalled==2)outcome=PlayResult.DRAW else next=next.other()
                    }else {
                        stalled=0
                        if(legal(taken.single(),next))pending=taken.single()else next=next.other()
                    }
                }
                is CardMove.Pass->{
                    require(pending!=null);pending=null;stalled=0
                    public+=CardPublicEvent(next,"pass",null,null,0);next=next.other()
                }
            }
            require(hands.values.flatten().plus(draw).plus(discard).let {it.size==108&&it.toSet().size==108})
        }
        if(outcome==PlayResult.ACTIVE&&moves.size==MAX_MOVES)outcome=PlayResult.DRAW
        userHand=PlayJson.frozen(hands.getValue(PlaySide.USER));roleHand=PlayJson.frozen(hands.getValue(PlaySide.ROLE))
        top=card(discard.last());color=activeColor;turn=if(outcome==PlayResult.ACTIVE)next else null
        result=outcome;score=points;drawCount=draw.size;discardCount=discard.size;reshuffles=cycle
        drawnCard=if(outcome==PlayResult.ACTIVE)pending else null;history=PlayJson.frozen(public)
        require(encode().toByteArray(Charsets.UTF_8).size<=PlayJson.MAX_BYTES)
    }
    private fun ids(side:PlaySide)=if(side==PlaySide.USER)userHand else roleHand
    fun handCount(side:PlaySide)=ids(side).size
    fun hand(viewer:PlaySide):List<ColorCard> = PlayJson.frozen(ids(viewer).map(::card))
    fun legalMoves(side:PlaySide):List<CardMove> {
        if(turn!=side)return emptyList()
        val hand=ids(side)
        val playable=hand.filter {id->(drawnCard==null||id==drawnCard)&&card(id).let {c->
            when(c.face){14->hand.none {card(it).color==color};13->true;else->c.color==color||c.face==top.face}
        }}
        return PlayJson.frozen(playable.flatMap {id->if(card(id).color==null)CardColor.entries.map {CardMove.Play(side,id,it)} else listOf(CardMove.Play(side,id))}+
            if(drawnCard==null)listOf(CardMove.Draw(side))else listOf(CardMove.Pass(side)))
    }
    fun apply(move:CardMove):FourColorGame=try {FourColorGame(deck,shuffleKey,first,moves+move)}catch(_:IllegalArgumentException){throw ChatFailure("PLAY_ACTION")}
    fun observation(viewer:PlaySide):JSONObject=JSONObject().put("game","four_color").put("rules","duel-v1")
        .put("viewer",viewer.wire).put("turn",turn?.wire?:JSONObject.NULL).put("result",result.name).put("score",score)
        .put("top",top.json()).put("color",color.wire).put("hand",JSONArray().apply {hand(viewer).forEach {put(it.json())}})
        .put("opponentCount",handCount(viewer.other())).put("drawCount",drawCount).put("discardCount",discardCount)
        .put("drawnCard",if(turn==viewer)drawnCard?:JSONObject.NULL else JSONObject.NULL)
        .put("legalMoves",JSONArray().apply {legalMoves(viewer).forEach {put(moveJson(it))}})
        .put("history",JSONArray().apply {history.takeLast(40).forEach {e->put(JSONObject().put("side",e.side.wire).put("action",e.action)
            .put("card",e.card?:JSONObject.NULL).put("color",e.color?.wire?:JSONObject.NULL).put("drawn",e.drawn))}})
    fun encode():String=JSONObject().put("schemaVersion",1).put("kind","four_color").put("rules","duel-v1")
        .put("deck",JSONArray(deck)).put("shuffleKey",shuffleKey).put("first",first.wire)
        .put("moves",JSONArray().apply {moves.forEach {put(moveJson(it))}}).toString()
    fun isPrefixOf(other:FourColorGame)=deck==other.deck&&shuffleKey==other.shuffleKey&&first==other.first&&other.moves.take(moves.size)==moves
    companion object {
        const val MAX_MOVES=2048
        private fun hash(text:String)=MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
        fun card(id:Int):ColorCard {
            require(id in 0..107)
            if(id>=100)return ColorCard(id,null,if(id<104)13 else 14)
            val slot=id%25
            return ColorCard(id,CardColor.entries[id/25],if(slot==0)0 else if(slot<=18)(slot+1)/2 else 10+(slot-19)/2)
        }
        fun start(first:PlaySide=PlaySide.USER,random:SecureRandom=SecureRandom()):FourColorGame {
            val deck=(0..107).toMutableList()
            for(i in deck.lastIndex downTo 1){val j=random.nextInt(i+1);val old=deck[i];deck[i]=deck[j];deck[j]=old}
            val secret=ByteArray(32).also(random::nextBytes).joinToString(""){"%02x".format(it)}
            return FourColorGame(deck,secret,first,emptyList())
        }
        /** Compact action arrays keep the complete maximum history below the existing save limit. */
        private fun moveJson(move:CardMove)=JSONArray().put(when(move){is CardMove.Play->"play";is CardMove.Draw->"draw";is CardMove.Pass->"pass"})
            .put(move.side.wire).apply {if(move is CardMove.Play){put(move.card);put(move.color?.wire?:JSONObject.NULL)}}
        fun decode(text:String):FourColorGame=try {
            val v=PlayJson.objectValue(text);PlayJson.keys(v,"schemaVersion","kind","rules","deck","shuffleKey","first","moves")
            PlayJson.integer(v,"schemaVersion",1..1);require(PlayJson.string(v,"kind")=="four_color"&&PlayJson.string(v,"rules")=="duel-v1")
            val d=PlayJson.array(v,"deck");require(d.length()==108)
            val deck=(0 until d.length()).map {d.get(it).also {x->require(x is Int&&x in 0..107)} as Int}
            val list=PlayJson.array(v,"moves");require(list.length()<=MAX_MOVES)
            val moves=(0 until list.length()).map {i->val m=list.getJSONArray(i);require(m.length() in setOf(2,4))
                val action=m.get(0);val s=m.get(1);require(action is String&&s is String);val side=PlayJson.side(s)
                when(action) {
                    "play"->{require(m.length()==4);val id=m.get(2);require(id is Int&&id in 0..107)
                        val c=m.get(3);require(c==JSONObject.NULL||c is String)
                        CardMove.Play(side,id,if(c==JSONObject.NULL)null else CardColor.entries.single {it.wire==c})}
                    "draw"->{require(m.length()==2);CardMove.Draw(side)}
                    "pass"->{require(m.length()==2);CardMove.Pass(side)}
                    else->throw IllegalArgumentException()
                }
            }
            FourColorGame(deck,PlayJson.string(v,"shuffleKey"),PlayJson.side(PlayJson.string(v,"first")),moves)
        }catch(_:Exception){throw ChatFailure("PLAY_FORMAT")}
    }
}
