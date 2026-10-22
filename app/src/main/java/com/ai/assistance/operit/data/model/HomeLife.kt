package com.ai.assistance.operit.data.model

import java.util.Collections

enum class HomeRoom(val title:String) { LIVING("客厅"), KITCHEN("厨房"), BEDROOM("卧室"), GARDEN("院子") }

enum class HomeTogetherKind(val title:String,val room:HomeRoom,val seconds:Int,val energy:Int,val coins:Int,val xp:Int,val response:String) {
    TEA("一起喝茶",HomeRoom.LIVING,20,1,12,8,"茶刚刚好。下次还坐在这里，好吗？"),
    READ("一起共读",HomeRoom.LIVING,30,2,18,12,"这一页，我想和你再读一次。"),
    CLEAN("一起收拾",HomeRoom.BEDROOM,25,2,20,12,"窗边也整理好了，我们的小家又舒服了一点。"),
    MEAL("一起吃饭",HomeRoom.KITCHEN,30,2,26,18,"自己种、自己做的这一餐，很有家的味道。"),
    WALK("院子散步",HomeRoom.GARDEN,20,1,12,8,"等这些小苗长大，我们再来看看。"),
    REST("一起休息",HomeRoom.BEDROOM,30,0,0,2,"休息好了。今天剩下的时间，慢慢来。")
}

enum class HomeFurnitureSlot(val title:String,val room:HomeRoom) {
    SOFA("沙发",HomeRoom.LIVING), TABLE("餐桌",HomeRoom.KITCHEN), BED("床",HomeRoom.BEDROOM), PLANT("花盆",HomeRoom.GARDEN)
}

enum class HomeFurniture(val title:String,val slot:HomeFurnitureSlot,val cost:Int,val level:Int,val color:Long) {
    SAGE_SOFA("鼠尾草沙发",HomeFurnitureSlot.SOFA,0,1,0xFF86A58A),
    HONEY_SOFA("蜂蜜沙发",HomeFurnitureSlot.SOFA,65,1,0xFFE7B956),
    BERRY_SOFA("莓果沙发",HomeFurnitureSlot.SOFA,110,2,0xFFC78294),
    OAK_TABLE("橡木餐桌",HomeFurnitureSlot.TABLE,0,1,0xFFB78C61),
    WHITE_TABLE("白木餐桌",HomeFurnitureSlot.TABLE,55,1,0xFFE9DECE),
    BLUE_TABLE("海蓝餐桌",HomeFurnitureSlot.TABLE,100,2,0xFF729AAB),
    SKY_BED("晴空小床",HomeFurnitureSlot.BED,0,1,0xFF93B8C5),
    ROSE_BED("蔷薇小床",HomeFurnitureSlot.BED,75,1,0xFFD49FA7),
    NIGHT_BED("星夜小床",HomeFurnitureSlot.BED,140,3,0xFF797FAA),
    FERN_POT("蕨叶盆栽",HomeFurnitureSlot.PLANT,0,1,0xFF799976),
    DAISY_POT("雏菊花盆",HomeFurnitureSlot.PLANT,45,1,0xFFF0CF72),
    ROSE_POT("玫瑰花盆",HomeFurnitureSlot.PLANT,95,2,0xFFD68996)
}

enum class HomeWish(val title:String,val activity:HomeTogetherKind,val coins:Int,val xp:Int) {
    TEA("留一点喝茶的时间",HomeTogetherKind.TEA,20,10),
    READ("一起读完一小段",HomeTogetherKind.READ,25,15),
    CLEAN("把小家整理一下",HomeTogetherKind.CLEAN,25,15)
}

data class HomeTogether(val roleId:String,val kind:HomeTogetherKind,val startedAt:Long) {
    val readyAt:Long get()=Math.addExact(startedAt,kind.seconds*1000L)
}

/** The shared world's economy and activity are committed with inventory and residents. */
class HomeLife private constructor(val coins:Int,val xp:Int,val energy:Int,owned:Set<HomeFurniture>,placed:Map<HomeFurnitureSlot,HomeFurniture>,
    val together:HomeTogether?,val day:Long,done:Set<HomeTogetherKind>,claimed:Set<HomeWish>,val lastActivity:HomeTogetherKind?) {
    val owned:Set<HomeFurniture> = Collections.unmodifiableSet(owned.toSet())
    val placed:Map<HomeFurnitureSlot,HomeFurniture> = Collections.unmodifiableMap(placed.toMap())
    val done:Set<HomeTogetherKind> = Collections.unmodifiableSet(done.toSet())
    val claimed:Set<HomeWish> = Collections.unmodifiableSet(claimed.toSet())
    val level get()=1+xp/100
    init {
        require(coins in 0..MAX_PROGRESS&&xp in 0..MAX_PROGRESS&&energy in 0..MAX_ENERGY)
        require(HomeFurniture.entries.filter {it.cost==0}.all {it in owned})
        require(placed.size==HomeFurnitureSlot.entries.size&&placed.all {(slot,item)->item.slot==slot&&item in owned})
        require(owned.all {it.level<=level}&&day in -1..dayAt(HomeState.MAX_TIME))
        require(claimed.all {it.activity in done})
        together?.let {require(it.startedAt in 0..HomeState.MAX_TIME&&it.readyAt<=HomeState.MAX_TIME)}
    }
    fun canClaim(wish:HomeWish,now:Long)=day==dayAt(now)&&wish.activity in done&&wish !in claimed
    fun apply(action:HomeAction,now:Long):HomeLife {
        var money=coins;var experience=xp;var stamina=energy;var running=together
        val furniture=owned.toMutableSet();val positions=placed.toMutableMap()
        val today=dayAt(now);val activities=if(day==today)done.toMutableSet() else mutableSetOf()
        val rewards=if(day==today)claimed.toMutableSet() else mutableSetOf()
        var last=lastActivity
        fun reward(c:Int,x:Int) {
            if(money>MAX_PROGRESS-c||experience>MAX_PROGRESS-x)throw ChatFailure("HOME_LIFE_LIMIT")
            money+=c;experience+=x
        }
        when(action) {
            is HomeAction.StartTogether->{
                if(running!=null)throw ChatFailure("HOME_TOGETHER_BUSY")
                if(stamina<action.kind.energy)throw ChatFailure("HOME_ENERGY")
                if(now>HomeState.MAX_TIME-action.kind.seconds*1000L)throw ChatFailure("HOME_CLOCK")
                if(action.kind==HomeTogetherKind.REST&&stamina==MAX_ENERGY)throw ChatFailure("HOME_REST_FULL")
                stamina-=action.kind.energy;running=HomeTogether(action.roleId,action.kind,now)
            }
            HomeAction.FinishTogether->{
                val activity=running?:throw ChatFailure("HOME_TOGETHER_EMPTY")
                if(now<activity.readyAt)throw ChatFailure("HOME_TOGETHER_RUNNING")
                reward(activity.kind.coins,activity.kind.xp)
                if(activity.kind==HomeTogetherKind.REST)stamina=minOf(MAX_ENERGY,stamina+6)
                activities+=activity.kind;last=activity.kind;running=null
            }
            is HomeAction.Decorate->{
                if(action.item.level>level)throw ChatFailure("HOME_FURNITURE_LEVEL")
                if(action.item !in furniture) {
                    if(money<action.item.cost)throw ChatFailure("HOME_COINS")
                    money-=action.item.cost;furniture+=action.item
                }
                positions[action.item.slot]=action.item
            }
            is HomeAction.ClaimWish->{
                if(!canClaim(action.wish,now))throw ChatFailure("HOME_WISH")
                reward(action.wish.coins,action.wish.xp);rewards+=action.wish
            }
            is HomeAction.Leave->{if(running?.roleId==action.roleId)running=null}
            is HomeAction.Gather,HomeAction.Fish->{
                if(stamina<1)throw ChatFailure("HOME_ENERGY")
                stamina--;reward(0,3)
            }
            is HomeAction.Sell->reward(HomeEconomy.price(action.item)*action.count,action.count)
            else->return this
        }
        return HomeLife(money,experience,stamina,furniture,positions,running,today,activities,rewards,last)
    }
    companion object {
        const val MAX_ENERGY=12
        const val MAX_PROGRESS=1_000_000
        // A fixed game day makes ledger replay independent of the phone's current time zone.
        fun dayAt(time:Long)=(time+8*60*60*1000L)/(24*60*60*1000L)
        fun create():HomeLife {
            val initial=HomeFurniture.entries.filter {it.cost==0}
            return HomeLife(120,0,8,initial.toSet(),initial.associateBy {it.slot},null,-1,emptySet(),emptySet(),null)
        }
        internal fun restore(coins:Int,xp:Int,energy:Int,owned:Set<HomeFurniture>,placed:Map<HomeFurnitureSlot,HomeFurniture>,together:HomeTogether?,day:Long,
            done:Set<HomeTogetherKind>,claimed:Set<HomeWish>,last:HomeTogetherKind?)=HomeLife(coins,xp,energy,owned,placed,together,day,done,claimed,last)
    }
}
