package com.ai.assistance.operit.data.model

import java.util.Collections

enum class HomeItem(val title:String,val group:HomeItemGroup) {
    CARROT_SEED("胡萝卜种子",HomeItemGroup.SEED),TOMATO_SEED("番茄种子",HomeItemGroup.SEED),COTTON_SEED("棉花种子",HomeItemGroup.SEED),
    CARROT("胡萝卜",HomeItemGroup.CROP),TOMATO("番茄",HomeItemGroup.CROP),COTTON("棉花",HomeItemGroup.MATERIAL),
    CLOTH("棉布",HomeItemGroup.MATERIAL),CARROT_SOUP("胡萝卜汤",HomeItemGroup.FOOD),TOMATO_SOUP("番茄汤",HomeItemGroup.FOOD),
    SALAD("田园沙拉",HomeItemGroup.FOOD),APRON("围裙",HomeItemGroup.CLOTHING),SUN_HAT("太阳帽",HomeItemGroup.CLOTHING),GARDEN_SUIT("田园服",HomeItemGroup.CLOTHING),
    HARBOR_BADGE("归航纪念章",HomeItemGroup.SOUVENIR),SEAGULL_PIN("海鸥胸针",HomeItemGroup.SOUVENIR),
    WOOD("木材",HomeItemGroup.MATERIAL),STONE("石料",HomeItemGroup.MATERIAL),BERRY("浆果",HomeItemGroup.CROP),FISH("鲜鱼",HomeItemGroup.CROP),
    GRILLED_FISH("烤鱼",HomeItemGroup.FOOD),BERRY_JAM("浆果酱",HomeItemGroup.FOOD);
    val giftable get()=group in setOf(HomeItemGroup.CROP,HomeItemGroup.FOOD,HomeItemGroup.CLOTHING,HomeItemGroup.SOUVENIR)
}
enum class HomeItemGroup { SEED,CROP,MATERIAL,FOOD,CLOTHING,SOUVENIR }
enum class HomeCrop(val title:String,val seed:HomeItem,val produce:HomeItem,val minutes:Int) {
    CARROT("胡萝卜",HomeItem.CARROT_SEED,HomeItem.CARROT,5),TOMATO("番茄",HomeItem.TOMATO_SEED,HomeItem.TOMATO,10),COTTON("棉花",HomeItem.COTTON_SEED,HomeItem.COTTON,15)
}
enum class HomeRecipe(val title:String,val product:HomeItem,ingredients:Map<HomeItem,Int>) {
    CARROT_SOUP("煮胡萝卜汤",HomeItem.CARROT_SOUP,mapOf(HomeItem.CARROT to 2)),
    TOMATO_SOUP("煮番茄汤",HomeItem.TOMATO_SOUP,mapOf(HomeItem.TOMATO to 2)),
    SALAD("做田园沙拉",HomeItem.SALAD,mapOf(HomeItem.CARROT to 1,HomeItem.TOMATO to 1)),
    CLOTH("织棉布",HomeItem.CLOTH,mapOf(HomeItem.COTTON to 2)),
    APRON("制作围裙",HomeItem.APRON,mapOf(HomeItem.CLOTH to 2)),
    SUN_HAT("制作太阳帽",HomeItem.SUN_HAT,mapOf(HomeItem.CLOTH to 1)),
    GARDEN_SUIT("制作田园服",HomeItem.GARDEN_SUIT,mapOf(HomeItem.CLOTH to 3)),
    GRILLED_FISH("烤鲜鱼",HomeItem.GRILLED_FISH,mapOf(HomeItem.FISH to 1,HomeItem.WOOD to 1)),
    BERRY_JAM("熬浆果酱",HomeItem.BERRY_JAM,mapOf(HomeItem.BERRY to 2));
    val ingredients:Map<HomeItem,Int> = Collections.unmodifiableMap(LinkedHashMap(ingredients))
}
enum class HomeWearer { USER,ROLE }
data class HomeScope(val homeId:String=ID) {init {require(homeId==ID)};companion object {const val ID="shared-home"}}
data class HomePlot(val crop:HomeCrop,val plantedAt:Long,val watered:Boolean=false) {
    val readyAt get()=Math.addExact(plantedAt,(crop.minutes-(if(watered)1 else 0))*60_000L)
}
sealed interface HomeAction {
    data class Plant(val plot:Int,val crop:HomeCrop):HomeAction
    data class Water(val plot:Int):HomeAction
    data class Harvest(val plot:Int):HomeAction
    data class ClearPlot(val plot:Int):HomeAction
    data class Craft(val recipe:HomeRecipe,val batches:Int=1):HomeAction
    data class Gift(val roleId:String,val item:HomeItem,val count:Int):HomeAction
    data class Discard(val item:HomeItem,val count:Int):HomeAction
    data class Wear(val wearer:HomeWearer,val item:HomeItem?,val roleId:String?=null):HomeAction
    data class Rename(val name:String):HomeAction
    data class StartTogether(val roleId:String,val kind:HomeTogetherKind,val meal:HomeItem?=null):HomeAction
    data object FinishTogether:HomeAction
    data class Decorate(val item:HomeFurniture):HomeAction
    data class ClaimWish(val wish:HomeWish):HomeAction
    data class Join(val role:RoleAccessScope):HomeAction
    data class Leave(val roleId:String):HomeAction
    data class Move(val player:HomeTile,val residents:Map<String,HomeTile>):HomeAction
    data class Follow(val roleId:String?):HomeAction
    data class Talk(val roleId:String):HomeAction
    data class Gather(val resource:Int):HomeAction
    data object Fish:HomeAction
    data class Sell(val item:HomeItem,val count:Int):HomeAction
    data class AdventureReward(val sourceId:String,val reward:HomeAdventureReward,val proof:HomeAdventureProof,val sourceRole:RoleAccessScope):HomeAction
    data class AdoptPet(val name:String):HomeAction
    data class CarePet(val kind:HomePetCare):HomeAction
    data class HandoffPet(val roleId:String):HomeAction
    data class ResolvePetHandoff(val roleId:String):HomeAction
}
data class HomeChange(val state:HomeState,val description:String)
