package com.ai.assistance.operit.data.model

import java.util.Collections

data class HomeResident(val role:RoleAccessScope,val tile:HomeTile,val outfit:HomeItem?=null,
    val received:Map<HomeItem,Int> = emptyMap(),val friendship:Int=0,val response:String="今天想一起做点什么？")

object HomeEconomy {
    fun price(item:HomeItem)=when(item) {
        HomeItem.CARROT->8;HomeItem.TOMATO->12;HomeItem.COTTON->10;HomeItem.WOOD->4;HomeItem.STONE->5
        HomeItem.BERRY->6;HomeItem.FISH->18;HomeItem.CLOTH->28;HomeItem.CARROT_SOUP->22
        HomeItem.TOMATO_SOUP->32;HomeItem.SALAD->30;HomeItem.APRON->65;HomeItem.SUN_HAT->35;HomeItem.GARDEN_SUIT->95
        HomeItem.GRILLED_FISH->28;HomeItem.BERRY_JAM->18
        else->0
    }
}

/** One immutable shared world. Every mutation consumes the same version and is recorded once. */
class HomeState private constructor(val scope:HomeScope,val name:String,val version:Long,val createdAt:Long,val updatedAt:Long,
    inventory:Map<HomeItem,Int>,plots:List<HomePlot?>,val userOutfit:HomeItem?,val player:HomeTile,
    residents:Map<String,HomeResident>,val followingRoleId:String?,resourceClaims:List<Long>,val fishedAt:Long,val life:HomeLife) {
    val inventory:Map<HomeItem,Int> = Collections.unmodifiableMap(LinkedHashMap(inventory))
    val plots=PlayJson.frozen(plots)
    val residents:Map<String,HomeResident> = Collections.unmodifiableMap(residents.mapValues {(_,r)->
        r.copy(received=Collections.unmodifiableMap(LinkedHashMap(r.received)))})
    val resourceClaims=PlayJson.frozen(resourceClaims)
    init {
        PlayJson.text(name,80);require(version>=1&&createdAt in 0..MAX_TIME&&updatedAt in createdAt..MAX_TIME)
        require(plots.size==PLOTS&&HomeWorld.walkable(player))
        require(inventory.values.all {it in 1..MAX_COUNT})
        plots.filterNotNull().forEach {require(it.plantedAt in createdAt..updatedAt&&it.readyAt<=MAX_TIME)}
        HomeCrop.entries.forEach {crop->require((inventory[crop.seed]?:0)+plots.count {it?.crop==crop}==INITIAL_SEEDS)}
        userOutfit?.let {require(it.group==HomeItemGroup.CLOTHING&&(inventory[it]?:0)>0)}
        residents.forEach {(id,r)->
            require(id==r.role.roleId);PlaySessions.id(id);PlaySessions.id(r.role.spaceId)
            require(HomeWorld.walkable(r.tile)&&r.friendship in 0..999)
            PlayJson.text(r.response,256)
            require(r.received.all {it.key.giftable&&it.value in 1..MAX_COUNT})
            r.outfit?.let {require(it.group==HomeItemGroup.CLOTHING&&(r.received[it]?:0)>0)}
        }
        require(followingRoleId==null||followingRoleId in residents)
        require(resourceClaims.size==HomeWorld.resources.size&&resourceClaims.all {it==-1L||it in createdAt..updatedAt})
        require(fishedAt==-1L||fishedAt in createdAt..updatedAt)
        life.together?.let {require(it.roleId in residents&&it.startedAt in createdAt..updatedAt)}
        require(life.day<=HomeLife.dayAt(updatedAt))
    }
    fun ready(plot:Int,now:Long):Boolean {require(plot in plots.indices);return plots[plot]?.let {now>=it.readyAt}==true}
    fun gatherReady(index:Int,now:Long)=resourceClaims[index]==-1L||now-resourceClaims[index]>=RESOURCE_COOLDOWN
    fun fishReady(now:Long)=fishedAt==-1L||now-fishedAt>=FISH_COOLDOWN
    fun apply(scope:HomeScope,expectedVersion:Long,now:Long,action:HomeAction):HomeChange {
        if(this.scope!=scope)throw ChatFailure("ACCESS")
        if(version!=expectedVersion)throw ChatFailure("HOME_CHANGED")
        if(now !in updatedAt..MAX_TIME)throw ChatFailure("HOME_CLOCK")
        if(version==Long.MAX_VALUE)throw ChatFailure("HOME_LIMIT")
        val bag=inventory.toMutableMap();val soil=plots.toMutableList();val people=residents.toMutableMap();val claims=resourceClaims.toMutableList()
        var user=userOutfit;var title=name;var position=player;var following=followingRoleId;var fishing=fishedAt
        fun fail(code:String):Nothing=throw ChatFailure(code)
        fun add(to:MutableMap<HomeItem,Int>,item:HomeItem,n:Int) {
            if(n !in 1..MAX_COUNT)fail("HOME_QUANTITY")
            val total=(to[item]?:0)+n;if(total>MAX_COUNT)fail("HOME_CAPACITY");to[item]=total
        }
        fun take(item:HomeItem,n:Int) {
            if(n !in 1..MAX_COUNT)fail("HOME_QUANTITY")
            val left=(bag[item]?:0)-n;if(left<0)fail("HOME_MATERIAL")
            if(item==user&&left==0)fail("HOME_WORN")
            if(left==0)bag.remove(item)else bag[item]=left
        }
        fun person(id:String)=people[id]?:fail("HOME_RESIDENT")
        fun index(n:Int){if(n !in soil.indices)fail("HOME_PLOT")}
        fun planted(n:Int):HomePlot {index(n);return soil[n]?:fail("HOME_EMPTY")}
        fun near(tile:HomeTile){if(position.distance(tile)>1)fail("HOME_DISTANCE")}
        val description=when(action) {
            is HomeAction.Plant->{index(action.plot);if(soil[action.plot]!=null)fail("HOME_OCCUPIED")
                if(now>MAX_TIME-action.crop.minutes*60_000L)fail("HOME_CLOCK")
                take(action.crop.seed,1);soil[action.plot]=HomePlot(action.crop,now);"种下${action.crop.title}。"}
            is HomeAction.Water->{val p=planted(action.plot);if(p.watered)fail("HOME_WATERED");if(now>=p.readyAt)fail("HOME_MATURE")
                soil[action.plot]=p.copy(watered=true);"小苗喝饱水了。"}
            is HomeAction.Harvest->{val p=planted(action.plot);if(now<p.readyAt)fail("HOME_GROWING")
                add(bag,p.crop.produce,3);add(bag,p.crop.seed,1);soil[action.plot]=null;"收获${p.crop.title} × 3。"}
            is HomeAction.ClearPlot->{val p=planted(action.plot);add(bag,p.crop.seed,1);soil[action.plot]=null;"清理田地，保留种子。"}
            is HomeAction.Craft->{if(action.batches !in 1..99)fail("HOME_QUANTITY")
                action.recipe.ingredients.forEach {(item,n)->take(item,n*action.batches)};add(bag,action.recipe.product,action.batches)
                "制成${action.recipe.product.title} × ${action.batches}。"}
            is HomeAction.Gift->{val r=person(action.roleId);if(!action.item.giftable)fail("HOME_GIFT_TYPE")
                take(action.item,action.count);val gifts=r.received.toMutableMap();add(gifts,action.item,action.count)
                people[action.roleId]=r.copy(received=gifts,friendship=minOf(999,r.friendship+action.count*2),response="谢谢你，我会好好珍惜这份礼物。")
                "送出${action.item.title} × ${action.count}。"}
            is HomeAction.Discard->{if(action.item.group==HomeItemGroup.SEED)fail("HOME_SEED");take(action.item,action.count);"整理背包。"}
            is HomeAction.Wear->{
                val r=if(action.wearer==HomeWearer.ROLE)person(requireNotNull(action.roleId))else null
                if(action.wearer==HomeWearer.USER&&action.roleId!=null)fail("HOME_RESIDENT")
                val stock=if(r==null)bag else r.received
                action.item?.let {if(it.group!=HomeItemGroup.CLOTHING||(stock[it]?:0)<1)fail("HOME_OUTFIT")}
                if(r==null)user=action.item else people[r.role.roleId]=r.copy(outfit=action.item)
                if(action.item==null)"卸下服装。"else"换上${action.item.title}。"
            }
            is HomeAction.Rename->{PlayJson.text(action.name,80);title=action.name;"家园更名为${action.name}。"}
            is HomeAction.StartTogether->{
                person(action.roleId)
                if(action.kind==HomeTogetherKind.MEAL) {
                    val meal=action.meal?:fail("HOME_MEAL");if(meal.group!=HomeItemGroup.FOOD)fail("HOME_MEAL");take(meal,1)
                }else if(action.meal!=null)fail("HOME_MEAL")
                "开始${action.kind.title}。"
            }
            HomeAction.FinishTogether->{val a=life.together?:fail("HOME_TOGETHER_EMPTY");val r=person(a.roleId)
                people[a.roleId]=r.copy(friendship=minOf(999,r.friendship+3),response=a.kind.response)
                "完成${a.kind.title}，+${a.kind.coins} 家园币，+${a.kind.xp} 经验。"}
            is HomeAction.Decorate->{
                if(action.item !in life.owned){take(HomeItem.WOOD,3);take(HomeItem.STONE,1)}
                "摆放${action.item.title}。"
            }
            is HomeAction.ClaimWish->"领取心愿「${action.wish.title}」的奖励。"
            is HomeAction.Join->{
                if(action.role.roleId in people)fail("HOME_RESIDENT_EXISTS")
                val occupied=people.values.map {it.tile}.toSet()+position
                val tile=HomeWorld.residentSpawns.firstOrNull {it !in occupied}?:fail("HOME_RESIDENT_LIMIT")
                people[action.role.roleId]=HomeResident(action.role,tile);"新居民入住。"
            }
            is HomeAction.Leave->{person(action.roleId);people.remove(action.roleId);if(following==action.roleId)following=null;"居民离开，家园保留。"}
            is HomeAction.Move->{
                if(action.residents.keys!=people.keys||!HomeWorld.walkable(action.player))fail("HOME_MOVE")
                if(position!=action.player&&HomeWorld.route(position,action.player,false).isEmpty())fail("HOME_MOVE")
                action.residents.forEach {(id,tile)->val r=person(id)
                    if(!HomeWorld.walkable(tile)||(r.tile!=tile&&HomeWorld.route(r.tile,tile,false).isEmpty()))fail("HOME_MOVE")
                    people[id]=r.copy(tile=tile)}
                position=action.player;"保存人物位置。"
            }
            is HomeAction.Follow->{action.roleId?.let {person(it)};following=action.roleId;if(following==null)"自由活动。"else"一起走走。"}
            is HomeAction.Talk->{val r=person(action.roleId);near(r.tile)
                val lines=listOf("今天的风很舒服，要不要去看看菜苗？","我们种的东西，做成晚饭一定很好吃。","不赶时间，陪你在这里就很好。")
                val line=lines[(version%lines.size).toInt()];people[action.roleId]=r.copy(response=line);line}
            is HomeAction.Gather->{
                if(action.resource !in HomeWorld.resources.indices)fail("HOME_RESOURCE")
                val resource=HomeWorld.resources[action.resource];near(resource.tile)
                if(!gatherReady(action.resource,now))fail("HOME_RESOURCE_WAIT")
                val item=when(resource.kind){HomeResourceKind.TREE->HomeItem.WOOD;HomeResourceKind.ROCK->HomeItem.STONE;HomeResourceKind.BUSH->HomeItem.BERRY}
                add(bag,item,2);claims[action.resource]=now;"采集${item.title} × 2。"
            }
            HomeAction.Fish->{
                if(position.distance(HomePlace.POND.tile)>4)fail("HOME_DISTANCE")
                if(!fishReady(now))fail("HOME_RESOURCE_WAIT")
                add(bag,HomeItem.FISH,1);fishing=now;"钓到一条鲜鱼！"
            }
            is HomeAction.Sell->{val price=HomeEconomy.price(action.item);if(price<=0)fail("HOME_SELL")
                take(action.item,action.count);"出售${action.item.title} × ${action.count}，+${price*action.count} 家园币。"}
            is HomeAction.AdventureReward->{PlaySessions.id(action.sourceId)
                if(person(action.sourceRole.roleId).role!=action.sourceRole)fail("ACCESS")
                if(!action.proof.earned(action.reward))fail("HOME_REWARD_UNEARNED")
                add(bag,action.reward.item,1);"领取${action.reward.item.title}。"}
        }
        return HomeChange(HomeState(scope,title,version+1,createdAt,now,bag,soil,user,position,people,following,claims,fishing,life.apply(action,now)),description)
    }
    fun encode()=HomeCodec.encode(this)
    companion object {
        const val PLOTS=6;const val INITIAL_SEEDS=2;const val MAX_COUNT=9999;const val MAX_TIME=253402300799999L
        const val RESOURCE_COOLDOWN=120_000L;const val FISH_COOLDOWN=60_000L
        fun create(name:String,now:Long,roles:List<RoleAccessScope>):HomeState {
            require(roles.map {it.roleId}.distinct().size==roles.size&&roles.size<=HomeWorld.residentSpawns.size)
            return HomeState(HomeScope(),name,1,now,now,HomeCrop.entries.associate {it.seed to INITIAL_SEEDS},List(PLOTS){null},null,
                HomeWorld.spawn,roles.mapIndexed {i,r->r.roleId to HomeResident(r,HomeWorld.residentSpawns[i])}.toMap(),null,
                List(HomeWorld.resources.size){-1L},-1,HomeLife.create())
        }
        internal fun restore(name:String,version:Long,createdAt:Long,updatedAt:Long,inventory:Map<HomeItem,Int>,plots:List<HomePlot?>,
            user:HomeItem?,player:HomeTile,residents:Map<String,HomeResident>,following:String?,claims:List<Long>,fishedAt:Long,life:HomeLife)=
            HomeState(HomeScope(),name,version,createdAt,updatedAt,inventory,plots,user,player,residents,following,claims,fishedAt,life)
        fun decode(text:String)=HomeCodec.decode(text)
    }
}
