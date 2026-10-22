package com.ai.assistance.operit.data.model

/** Every collection is frozen at construction so UI/model observers cannot change a saved world. */
internal class AdventureProgress private constructor(val position:AdventurePosition,val companion:AdventurePosition,
    inventory:Map<AdventureItem,Int>,flags:Set<AdventureFlag>,picked:Set<String>,explored:Map<AdventureArea,Set<Int>>,
    records:List<AdventureRecord>,val turn:PlaySide,val ended:Boolean,val characters:Int) {
    val inventory=AdventureGame.map(inventory)
    val flags=AdventureGame.set(flags)
    val picked=AdventureGame.set(picked)
    val explored=AdventureGame.map(explored.mapValues {AdventureGame.set(it.value)})
    val records=PlayJson.frozen(records)
    fun active(target:AdventureObject)=when(target.kind) {
        AdventureObjectKind.PICKUP->target.id !in picked
        AdventureObjectKind.BRIDGE->AdventureFlag.BRIDGE !in flags
        AdventureObjectKind.WORKBENCH->AdventureFlag.LANTERN !in flags
        AdventureObjectKind.SWITCH->AdventureFlag.SWITCH !in flags
        AdventureObjectKind.LIGHTHOUSE->AdventureFlag.LIT !in flags
        else->true
    }
    fun walkable(p:AdventurePosition)=AdventureWorld.groundOpen(p,flags)&&AdventureWorld.objects.none {it.position==p&&it.blocking&&active(it)}
    fun unavailable(target:AdventureObject)=when {
        !active(target)->"已经完成这次交互"
        target.kind==AdventureObjectKind.BRIDGE&&((inventory[AdventureItem.PLANK]?:0)<2||(inventory[AdventureItem.ROPE]?:0)<1)->"需要两块木板和一根绳索"
        target.kind==AdventureObjectKind.WORKBENCH&&(AdventureItem.GLASS !in inventory||AdventureItem.EMBER !in inventory)->"需要灯罩和萤火种"
        target.kind==AdventureObjectKind.LIGHTHOUSE&&(AdventureItem.LANTERN !in inventory||AdventureItem.CRYSTAL !in inventory)->"需要提灯和星光灯芯"
        target.id=="tower_path"&&AdventureFlag.LANTERN !in flags->"先制作提灯，旧塔里太暗了"
        else->""
    }
    fun apply(action:AdventureAction):AdventureProgress {
        require(!ended)
        var at=position;var partner=companion;var next=turn;var done=false;var size=characters
        val items=inventory.toMutableMap();val changed=flags.toMutableSet();val collected=picked.toMutableSet();val history=records.toMutableList()
        fun note(text:String,side:PlaySide?=null){history+=AdventureRecord(at,side,text)}
        fun add(item:AdventureItem,count:Int=1){val quantity=(items[item]?:0)+count;require(quantity in 1..9);items[item]=quantity}
        fun consume(item:AdventureItem,count:Int=1){val quantity=items.getValue(item)-count;require(quantity>=0);if(quantity==0)items.remove(item)else items[item]=quantity}
        when(action) {
            is AdventureAction.Move->{require(turn==PlaySide.USER);val target=at.step(action.direction);require(walkable(target));partner=at;at=target}
            is AdventureAction.Interact->{
                require(turn==PlaySide.USER)
                val target=requireNotNull(AdventureWorld.objects.singleOrNull {it.id==action.objectId})
                require(target.position.distance(at)<=1&&active(target)&&unavailable(target).isEmpty())
                when(target.kind) {
                    AdventureObjectKind.PICKUP->{val item=requireNotNull(target.item);add(item);collected+=target.id;note("拾取了${item.title}。")}
                    AdventureObjectKind.BRIDGE->{consume(AdventureItem.PLANK,2);consume(AdventureItem.ROPE);changed+=AdventureFlag.BRIDGE;note("用两块木板和绳索修好了木桥。")}
                    AdventureObjectKind.WORKBENCH->{consume(AdventureItem.GLASS);consume(AdventureItem.EMBER);add(AdventureItem.LANTERN);changed+=AdventureFlag.LANTERN;note("灯罩与萤火种做成了一盏提灯。")}
                    AdventureObjectKind.SWITCH->{changed+=AdventureFlag.SWITCH;note("石门打开了，通往塔的上层。")}
                    AdventureObjectKind.LIGHTHOUSE->{consume(AdventureItem.LANTERN);consume(AdventureItem.CRYSTAL);add(AdventureItem.BADGE);changed+=AdventureFlag.LIT;note("把提灯和星光灯芯装进灯塔，海边重新亮起了灯。获得归航纪念章。")}
                    AdventureObjectKind.KEEPER->{
                        changed+=AdventureFlag.MET_KEEPER
                        if(AdventureItem.SCARF in items) {require(AdventureFlag.SCARF_RETURNED !in flags);consume(AdventureItem.SCARF);add(AdventureItem.PIN);changed+=AdventureFlag.SCARF_RETURNED;note("把围巾还给守灯人，收到海鸥胸针。")}
                        else note(if(AdventureFlag.LIT in flags)"守灯人：灯已经亮了，谢谢你们。"else"守灯人：旧塔里有星光灯芯。先修好桥，再去林地找一盏灯；如果看见我的围巾，也请带回来。")
                    }
                    AdventureObjectKind.PORTAL->{at=requireNotNull(target.destination);require(walkable(at));partner=at;note("来到${at.area.title}。")}
                }
            }
            is AdventureAction.Say->{
                require(turn==action.side)
                PlayJson.text(action.text,if(turn==PlaySide.USER)AdventureGame.MAX_USER_TEXT else AdventureGame.MAX_REPLY)
                size+=action.text.length;require(size<=AdventureGame.MAX_CHARACTERS)
                note(action.text,turn);next=turn.other()
            }
            AdventureAction.Finish->{require(turn==PlaySide.USER&&AdventureFlag.LIT in flags);done=true;note("结束了这次归航冒险，同行记录和纪念物留在本局存档中。")}
            AdventureAction.SkipDialogue->{require(turn==PlaySide.ROLE);next=PlaySide.USER;note("这次对白没有等待回复，继续探索。")}
        }
        require(history.size<=AdventureGame.MAX_RECORDS)
        return AdventureProgress(at,partner,items,changed,collected,reveal(explored,at),history,next,done,size)
    }
    companion object {
        fun start():AdventureProgress {
            val at=AdventureWorld.start
            return AdventureProgress(at,at,emptyMap(),emptySet(),emptySet(),reveal(emptyMap(),at),
                listOf(AdventureRecord(at,null,"海边的灯塔熄灭了。你们决定一起把灯带回来。")),PlaySide.USER,false,0)
        }
        /** Sight extends two grid steps; opaque walls stop propagation, water does not. */
        private fun reveal(previous:Map<AdventureArea,Set<Int>>,at:AdventurePosition):Map<AdventureArea,Set<Int>> {
            val visible=previous[at.area].orEmpty().toMutableSet();val queue=ArrayDeque<Pair<AdventurePosition,Int>>()
            val seen=mutableSetOf(at);queue.add(at to 0)
            while(queue.isNotEmpty()) {
                val (p,distance)=queue.removeFirst()
                if(p.x !in 0 until AdventureWorld.WIDTH||p.y !in 0 until AdventureWorld.HEIGHT)continue
                visible+=p.index
                if(distance==2||AdventureWorld.tile(p)=='#')continue
                AdventureDirection.entries.forEach {direction->val next=p.step(direction);if(seen.add(next))queue.add(next to distance+1)}
            }
            return previous+(at.area to visible)
        }
    }
}
