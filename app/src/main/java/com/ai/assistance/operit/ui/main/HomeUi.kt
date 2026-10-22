package com.ai.assistance.operit.ui.main

import com.ai.assistance.operit.data.db.*
import com.ai.assistance.operit.data.model.*
import java.util.Collections

/** Constructed by the IO delivery path once per snapshot, never by the one-second garden clock. */
class HomeLedger private constructor(val records:List<HomeCommandRow>,val gifts:List<HomeCommandRow>) {
    companion object {
        val EMPTY=HomeLedger(emptyList(),emptyList())
        fun from(rows:List<HomeCommandRow>):HomeLedger {
            require(rows.map {it.commandId}.distinct().size==rows.size)
            val ordered=rows.filter {it.action() !is HomeAction.Move}.sortedByDescending {it.beforeVersion}
            return HomeLedger(Collections.unmodifiableList(ordered),Collections.unmodifiableList(ordered.filter {it.action() is HomeAction.Gift}))
        }
    }
}

data class HomePending(val id:String,val scope:HomeScope,val version:Long,val action:HomeAction,val error:String?=null)
data class HomeNameDraft(val original:String,val text:String=original) {val dirty get()=text!=original}
data class HomeUi(val token:String,val loading:Boolean=true,val view:HomeView?=null,val positionSaving:Boolean=false,
    val readError:String?=null,val pending:HomePending?=null,val nameDraft:HomeNameDraft?=null,val history:HomeView?=null,val ledger:HomeLedger=HomeLedger.EMPTY) {
    fun received(next:HomeView?):HomeUi {
        next?.let {it.home.value();require(it.recent.all {c->c.scope()==it.home.scope()})}
        if(next!=null&&view?.home?.id==next.home.id&&view.home.version>next.home.version)return this
        val keptHistory=history?.takeIf {next!=null&&it.home.id==next.home.id&&it.home.version==next.home.version}
        return copy(loading=false,view=next,readError=null,
            history=keptHistory,ledger=if(keptHistory!=null)ledger else HomeLedger.from(next?.recent.orEmpty()))
    }
    fun completed(receipt:HomeReceipt):HomeUi {
        val command=pending?:return this
        require(receipt.command.commandId==command.id&&receipt.command.matches(command.scope,command.version,command.action))
        require(receipt.home.scope()==command.scope)
        // Flow may already have delivered a newer state. A receipt only clears its own pending operation.
        val previous=view
        val fresh=if(previous==null||previous.home.version<=receipt.home.version)received(HomeView(receipt.home,
            (previous?.recent.orEmpty().filter {it.commandId!=receipt.command.commandId}+receipt.command).sortedByDescending {it.beforeVersion}.take(20)))else this
        return fresh.copy(pending=null,nameDraft=if(command.action is HomeAction.Rename)null else nameDraft)
    }
    fun fullHistory(next:HomeView):HomeUi {
        require(next.recent.all {it.scope()==next.home.scope()})
        return if(view?.home?.id==next.home.id&&view.home.version==next.home.version)copy(history=next,ledger=HomeLedger.from(next.recent))else this
    }
}

internal fun homeClockValid(home:HomeState,now:Long)=now in home.updatedAt..HomeState.MAX_TIME
internal fun homePlotLabel(home:HomeState,plot:Int,now:Long):String {
    val planted=home.plots[plot]?:return "空地 · 可以播种"
    if(!homeClockValid(home,now))return "手机时间异常"
    if(now>=planted.readyAt)return "已成熟 · 可以收获"
    val minutes=(planted.readyAt-now+59_999)/60_000
    return "约 $minutes 分钟后成熟${if(planted.watered)" · 已浇水"else""}"
}
internal fun homeCraftLimit(home:HomeState,recipe:HomeRecipe):Int {
    val materials=recipe.ingredients.minOf {(item,n)->(home.inventory[item]?:0)/n}
    return minOf(99,materials,HomeState.MAX_COUNT-(home.inventory[recipe.product]?:0))
}
internal fun homeTransferLimit(home:HomeState,item:HomeItem,gift:Boolean,roleId:String?=null):Int {
    if(item.group==HomeItemGroup.SEED||gift&&!item.giftable)return 0
    val available=(home.inventory[item]?:0)-(if(home.userOutfit==item)1 else 0)
    val received=if(gift)home.residents[roleId]?.received?:return 0 else emptyMap()
    return minOf(available,if(gift)HomeState.MAX_COUNT-(received[item]?:0)else HomeState.MAX_COUNT).coerceAtLeast(0)
}
