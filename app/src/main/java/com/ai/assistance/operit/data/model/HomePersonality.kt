package com.ai.assistance.operit.data.model

import kotlin.random.Random

enum class HomeIntent(val greeting:String,val activity:HomeTogetherKind) {
    CHAT("来陪你待一会儿",HomeTogetherKind.TEA),
    GARDEN("一起看看小苗？",HomeTogetherKind.WALK),
    READ("想和你读一页",HomeTogetherKind.READ),
    COOK("一起做点好吃的？",HomeTogetherKind.MEAL),
    EXPLORE("陪我走走吧",HomeTogetherKind.WALK),
    REST("歇一会儿好吗？",HomeTogetherKind.REST),
    CLEAN("一起收拾小家？",HomeTogetherKind.CLEAN)
}

/** Local game behavior reads the saved persona; it never sends the persona to a service.
 * Traits add weights rather than assigning a role to one rigid personality category. */
data class HomePersonality(val sociability:Int,val initiativeSeconds:Int,val paceMillis:Int,val interests:Map<HomeIntent,Int>) {
    fun intention(identity:String,turn:Int):HomeIntent {
        var draw=Random(identity.hashCode() xor (turn*7919)).nextInt(interests.values.sum())
        interests.forEach {(intent,weight)->draw-=weight;if(draw<0)return intent}
        error("Invalid personality weights")
    }
    fun destination(intent:HomeIntent,identity:String,turn:Int,occupied:Set<HomeTile>):HomeTile? {
        val anchors=when(intent) {
            HomeIntent.CHAT->listOf(HomePlace.BENCH.tile,HomePlace.SOFA.tile)
            HomeIntent.GARDEN->HomeWorld.plots
            HomeIntent.READ->listOf(HomePlace.BOOKSHELF.tile,HomePlace.BENCH.tile)
            HomeIntent.COOK->listOf(HomePlace.KITCHEN.tile,HomePlace.CAMPFIRE.tile)
            HomeIntent.EXPLORE->listOf(HomePlace.POND.tile,HomePlace.PLANT.tile,HomePlace.WORKBENCH.tile)
            HomeIntent.REST->listOf(HomePlace.BED.tile,HomePlace.BENCH.tile)
            HomeIntent.CLEAN->listOf(HomePlace.WARDROBE.tile,HomePlace.TABLE.tile)
        }
        return anchors.flatMap(HomeWorld::neighbors).distinct().filter {it !in occupied}
            .shuffled(Random(identity.hashCode() xor turn)).firstOrNull()
    }
    companion object {
        fun from(prompt:String):HomePersonality {
            val text=prompt.lowercase()
            // Negated traits must not make a quiet resident behave as outgoing.
            fun has(vararg words:String)=words.any {word->Regex("(?<!不)(?<!非)(?<!并不)(?<!not )${Regex.escape(word)}").containsMatchIn(text)}
            val outgoing=has("外向","活泼","热情","主动","黏人","开朗","outgoing","energetic","affectionate","extrovert")
            val quiet=has("内向","害羞","安静","寡言","冷静","独处","沉稳","introvert","shy","quiet","reserved")
            val gentle=has("温柔","体贴","关心","照顾","温暖","gentle","caring","warm")
            val garden=has("种植","种地","园艺","植物","花草","自然","gardening","plants","nature")
            val books=has("读书","阅读","书籍","文学","研究","reading","books","studious")
            val food=has("做饭","烹饪","美食","吃货","料理","cooking","food")
            val explore=has("好奇","冒险","探索","钓鱼","旅行","curious","adventure","fishing")
            val tidy=has("勤劳","整洁","认真","自律","整理","tidy","diligent","organized")
            val sleepy=has("慵懒","懒散","贪睡","悠闲","慢悠悠","sleepy","lazy","relaxed")
            return HomePersonality(
                sociability=(3+(if(outgoing)3 else 0)+(if(gentle)1 else 0)-(if(quiet)2 else 0)).coerceIn(1,7),
                initiativeSeconds=(28-(if(outgoing)14 else 0)-(if(gentle)5 else 0)+(if(quiet)18 else 0)).coerceIn(12,60),
                paceMillis=if(outgoing||explore)230 else if(quiet||sleepy)390 else 300,
                interests=linkedMapOf(HomeIntent.CHAT to 2+(if(outgoing||gentle)8 else 0),
                    HomeIntent.GARDEN to 2+(if(garden)14 else 0),HomeIntent.READ to 2+(if(books||quiet)12 else 0),
                    HomeIntent.COOK to 2+(if(food)14 else 0),HomeIntent.EXPLORE to 2+(if(explore)14 else 0),
                    HomeIntent.REST to 1+(if(sleepy)14 else 0),HomeIntent.CLEAN to 1+(if(tidy)14 else 0)))
        }
    }
}

/** Volatile decisions live only while the farm is active; only actual positions enter the world ledger. */
class HomeResidentBrain(val personality:HomePersonality,val identity:String) {
    var turn=0; private set
    var nextDecisionAt=0L; private set
    var lastApproachAt=0L; private set
    var intent=HomeIntent.CHAT; private set
    var approaching=false; private set
    fun decide(now:Long,playerMoving:Boolean,friendship:Int):Boolean {
        if(now<nextDecisionAt)return false
        turn++;intent=personality.intention(identity,turn)
        val eager=Random(identity.hashCode() xor turn*101).nextInt(10)<minOf(9,personality.sociability+friendship/50)
        approaching=!playerMoving&&now-lastApproachAt>=personality.initiativeSeconds*1000L&&eager
        if(approaching)lastApproachAt=now
        nextDecisionAt=now+(if(approaching)personality.initiativeSeconds else 6+turn%7)*1000L
        return true
    }
    fun greeted(now:Long){approaching=false;nextDecisionAt=now+personality.initiativeSeconds*1000L}
}
