package com.ai.assistance.operit.data.model

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

enum class HomePetNeed { HUNGER, MOOD, ENERGY, HYGIENE }

enum class HomePetCare(val title:String,val need:HomePetNeed) {
    FEED("喂食",HomePetNeed.HUNGER),PLAY("玩耍",HomePetNeed.MOOD),BRUSH("梳毛",HomePetNeed.HYGIENE),SLEEP("哄睡",HomePetNeed.ENERGY)
}

enum class HomePetStage(val title:String) { KITTEN("幼猫"),YOUNG("少年猫"),ADULT("成年猫") }

/** One shared pet. Values are stored as of [lastTickAt]; [HomePetRules.preview] applies offline decay without writing. */
data class HomePet(
    val name:String,
    val adoptedAt:Long,
    val hunger:Int,
    val mood:Int,
    val energy:Int,
    val hygiene:Int,
    val bond:Int,
    val lastTickAt:Long,
    val lastCareAt:Long,
    val lastCareBy:String?,
    val handoffRoleId:String?,
    val handoffAt:Long,
    val userCareCount:Int,
    val roleCareCount:Int
) {
    init {
        PlayJson.text(name,HomePetRules.NAME_LIMIT);require(name==name.trim())
        require(adoptedAt in 0..HomeState.MAX_TIME)
        require(hunger in HomePetRules.MIN_NEED..HomePetRules.MAX_NEED&&mood in HomePetRules.MIN_NEED..HomePetRules.MAX_NEED)
        require(energy in HomePetRules.MIN_NEED..HomePetRules.MAX_NEED&&hygiene in HomePetRules.MIN_NEED..HomePetRules.MAX_NEED)
        require(bond in 0..HomePetRules.MAX_BOND)
        require(lastTickAt in adoptedAt..HomeState.MAX_TIME)
        require(lastCareAt==-1L||lastCareAt in adoptedAt..lastTickAt)
        require(lastCareAt!=-1L||(lastCareBy==null&&userCareCount==0&&roleCareCount==0))
        lastCareBy?.let(PlaySessions::id)
        require((handoffRoleId==null)==(handoffAt==-1L))
        handoffRoleId?.let {PlaySessions.id(it);require(handoffAt in adoptedAt..lastTickAt)}
        require(userCareCount in 0..HomePetRules.MAX_CARE_COUNT&&roleCareCount in 0..HomePetRules.MAX_CARE_COUNT)
    }
}

object HomePetRules {
    const val NAME_LIMIT=12
    const val MIN_NEED=10
    const val MAX_NEED=100
    const val MAX_BOND=999
    const val MAX_CARE_COUNT=100_000
    const val MAX_OFFLINE_MS=7L*24*60*60*1000
    const val HANDOFF_DELAY_MS=2L*60*1000
    const val DAY_MS=24L*60*60*1000
    private const val HOUR_MS=3_600_000.0

    fun petName(value:String):String {val cleaned=value.trim();PlayJson.text(cleaned,NAME_LIMIT);return cleaned}
    fun adopt(name:String,now:Long)=HomePet(petName(name),now,72,70,82,74,0,now,-1,null,null,-1,0,0)
    fun stage(pet:HomePet,now:Long):HomePetStage {
        val days=(now.coerceAtLeast(pet.adoptedAt)-pet.adoptedAt)/DAY_MS
        return when {days<7L->HomePetStage.KITTEN;days<30L->HomePetStage.YOUNG;else->HomePetStage.ADULT}
    }
    fun preview(pet:HomePet,now:Long):HomePet {
        if(now<=pet.lastTickAt)return pet
        val elapsed=min(now-pet.lastTickAt,MAX_OFFLINE_MS)
        fun decay(value:Int,perHour:Double)=max(MIN_NEED,value-floor(elapsed/HOUR_MS*perHour).toInt())
        return pet.copy(hunger=decay(pet.hunger,12.0/6),mood=decay(pet.mood,8.0/4),
            energy=decay(pet.energy,10.0/8),hygiene=decay(pet.hygiene,15.0/12))
    }
    fun care(pet:HomePet,kind:HomePetCare,now:Long,byRoleId:String?=null):HomePet {
        byRoleId?.let(PlaySessions::id)
        val base=preview(pet,now);val first=pet.lastCareAt==-1L||HomeLife.dayAt(pet.lastCareAt)<HomeLife.dayAt(now)
        val gain=if(first)3 else 1
        fun up(value:Int,n:Int)=min(MAX_NEED,value+n)
        fun down(value:Int,n:Int)=max(MIN_NEED,value-n)
        val next=when(kind) {
            HomePetCare.FEED->base.copy(hunger=up(base.hunger,30),mood=up(base.mood,4))
            HomePetCare.PLAY->base.copy(mood=up(base.mood,28),energy=down(base.energy,6))
            HomePetCare.BRUSH->base.copy(hygiene=up(base.hygiene,30),mood=up(base.mood,6))
            HomePetCare.SLEEP->base.copy(energy=up(base.energy,35),mood=up(base.mood,4))
        }
        return next.copy(bond=min(MAX_BOND,next.bond+gain+(if(kind==HomePetCare.PLAY)1 else 0)),
            lastTickAt=now,lastCareAt=now,lastCareBy=byRoleId,
            userCareCount=if(byRoleId==null)next.userCareCount+1 else next.userCareCount,
            roleCareCount=if(byRoleId!=null)next.roleCareCount+1 else next.roleCareCount)
    }
    fun handoff(pet:HomePet,roleId:String,now:Long):HomePet {
        PlaySessions.id(roleId);val base=preview(pet,now)
        return base.copy(lastTickAt=now,handoffRoleId=roleId,handoffAt=now)
    }
    fun handoffDue(pet:HomePet,now:Long)=pet.handoffRoleId!=null&&now>=pet.handoffAt&&now-pet.handoffAt>=HANDOFF_DELAY_MS
    fun careChoice(pet:HomePet,now:Long):HomePetCare {
        val p=preview(pet,now)
        val need=listOf(HomePetNeed.HUNGER to p.hunger,HomePetNeed.MOOD to p.mood,
            HomePetNeed.ENERGY to p.energy,HomePetNeed.HYGIENE to p.hygiene).minByOrNull {it.second}!!.first
        return HomePetCare.entries.first {it.need==need}
    }
    fun resolveHandoff(pet:HomePet,now:Long):HomePet {
        val role=pet.handoffRoleId?:throw ChatFailure("HOME_PET_HANDOFF_EMPTY")
        if(!handoffDue(pet,now))throw ChatFailure("HOME_PET_HANDOFF_EARLY")
        return care(pet,careChoice(pet,now),now,role).copy(handoffRoleId=null,handoffAt=-1)
    }
    fun promptSummary(pet:HomePet,now:Long,roleId:String?=null):String {
        val p=preview(pet,now)
        val state=when {
            p.hunger<=35->"有点饿";p.hygiene<=35->"该梳毛了";p.energy<=35->"有点困";p.mood<=35->"想玩";else->"今天很安稳"
        }
        val opening=if(now.coerceAtLeast(pet.adoptedAt)-pet.adoptedAt<DAY_MS) {
            "你们刚把「${pet.name}」领养回家。"
        }else {
            "家园里有一只${stage(pet,now).title}「${pet.name}」。"
        }
        val handoff=if(roleId!=null&&pet.handoffRoleId==roleId)"用户把今晚的照顾交给了你。" else ""
        return opening+"它$state。它由你和用户共同照顾。"+handoff
    }
    fun descriptor(need:HomePetNeed,value:Int):String=when(need) {
        HomePetNeed.HUNGER->when {value<=25->"饿坏了";value<=50->"有点饿";value<=80->"刚吃过";else->"很满足"}
        HomePetNeed.MOOD->when {value<=25->"闷闷不乐";value<=50->"想玩";value<=80->"心情不错";else->"很开心"}
        HomePetNeed.ENERGY->when {value<=25->"困得睁不开眼";value<=50->"有点累";value<=80->"精神";else->"活力满满"}
        HomePetNeed.HYGIENE->when {value<=25->"该好好洗洗了";value<=50->"该梳毛了";value<=80->"还算干净";else->"干干净净"}
    }
}
