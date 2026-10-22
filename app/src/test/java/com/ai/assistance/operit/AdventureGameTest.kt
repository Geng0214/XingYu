package com.ai.assistance.operit

import com.ai.assistance.operit.data.model.*
import com.ai.assistance.operit.api.chat.AdventureDialogue
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.ArrayDeque

class AdventureGameTest {
    private fun invalid(block:()->Unit){try {block();fail("Expected rejection")}catch(_:IllegalArgumentException){}}
    private fun objectAt(id:String)=AdventureWorld.objects.single {it.id==id}
    /** Test oracle searches actual authored geometry; every returned step still passes production rules. */
    private fun path(game:AdventureGame,target:AdventurePosition,adjacent:Boolean=true):List<AdventureDirection>? {
        if(target.area!=game.position.area)return null
        val queue=ArrayDeque<Pair<AdventurePosition,List<AdventureDirection>>>();queue.add(game.position to emptyList())
        val seen=mutableSetOf(game.position)
        while(queue.isNotEmpty()) {
            val (at,steps)=queue.removeFirst()
            if(if(adjacent)at.distance(target)<=1 else at==target)return steps
            AdventureDirection.entries.forEach {direction->
                val next=at.step(direction)
                if(seen.add(next)&&AdventureWorld.groundOpen(next,game.flags)&&AdventureWorld.objects.none {it.position==next&&it.blocking&&game.active(it)})
                    queue.add(next to steps+direction)
            }
        }
        return null
    }
    private fun approach(game:AdventureGame,id:String):AdventureGame {
        val route=requireNotNull(path(game,objectAt(id).position)){"No route to $id from ${game.position}"}
        return route.fold(game){s,d->s.apply(AdventureAction.Move(d))}
    }
    private fun interact(game:AdventureGame,id:String)=approach(game,id).apply(AdventureAction.Interact(id))
    private fun forest():AdventureGame {
        var g=AdventureGame.start()
        for(id in listOf("wood_a","wood_b","rope","bridge","forest_path"))g=interact(g,id)
        return g
    }
    private fun tower():AdventureGame {
        var g=forest()
        for(id in listOf("glass","ember","workbench","tower_path"))g=interact(g,id)
        return g
    }
    internal fun completed(sideQuest:Boolean=false):AdventureGame {
        var g=forest()
        for(id in listOf("glass","ember","workbench"))g=interact(g,id)
        if(sideQuest)g=interact(g,"scarf")
        for(id in listOf("tower_path","gate_switch","crystal","forest_return","village_path"))g=interact(g,id)
        if(sideQuest)g=interact(g,"keeper")
        return interact(g,"lighthouse")
    }
    @Test fun allAuthoredAreasAndPortalDestinationsAreValidAndReachableThroughTheQuest() {
        assertEquals(3,AdventureArea.entries.size);assertEquals(16,AdventureWorld.objects.size)
        for(area in AdventureArea.entries)for(y in 0 until AdventureWorld.HEIGHT)for(x in 0 until AdventureWorld.WIDTH)
            assertTrue(AdventureWorld.tile(AdventurePosition(area,x,y)) in "#.~")
        AdventureWorld.objects.forEach {assertEquals('.',AdventureWorld.tile(it.position))}
        val game=completed();assertEquals(AdventureArea.VILLAGE,game.position.area)
        assertEquals(setOf(AdventureArea.VILLAGE,AdventureArea.FOREST,AdventureArea.TOWER),game.explored.keys)
        assertTrue(AdventureFlag.LIT in game.flags)
    }
    @Test fun wallsWaterMapEdgesAndObjectsBlockMovesWithoutChangingState() {
        val start=AdventureGame.start();val frozen=start.encode()
        assertFalse(start.canMove(AdventureDirection.NORTH));invalid {start.apply(AdventureAction.Move(AdventureDirection.NORTH))}
        val west=start.apply(AdventureAction.Move(AdventureDirection.WEST))
        assertFalse(west.canMove(AdventureDirection.WEST));invalid {west.apply(AdventureAction.Move(AdventureDirection.WEST))}
        val bank=approach(start,"bridge")
        assertNull(path(bank,AdventurePosition(AdventureArea.VILLAGE,2,4),false))
        assertEquals('#',AdventureWorld.tile(AdventurePosition(AdventureArea.VILLAGE,-1,0)))
        assertEquals('#',AdventureWorld.tile(AdventurePosition(AdventureArea.VILLAGE,13,11)))
        assertEquals(frozen,start.encode())
    }
    @Test fun interactionsRequireNearbyExistingTargetsAndCannotGrantRemoteItems() {
        val g=AdventureGame.start()
        invalid {g.apply(AdventureAction.Interact("crystal"))};invalid {g.apply(AdventureAction.Interact("../../secret"))}
        invalid {g.apply(AdventureAction.Interact("https://example.invalid/"))}
        assertTrue(g.inventory.isEmpty());assertTrue(g.events.isEmpty())
        assertEquals(setOf("wood_a"),g.nearby().map {it.target.id}.toSet())
    }
    @Test fun bridgeConsumesExactlyTheRequiredItemsAndOpensTheOnlyRiverCrossing() {
        var g=approach(AdventureGame.start(),"bridge")
        assertFalse(g.nearby().single {it.target.id=="bridge"}.enabled)
        invalid {g.apply(AdventureAction.Interact("bridge"))}
        for(id in listOf("rope","wood_b","wood_a"))g=interact(g,id)
        assertEquals(2,g.inventory[AdventureItem.PLANK]);assertEquals(1,g.inventory[AdventureItem.ROPE])
        assertNull(path(g,objectAt("forest_path").position))
        g=interact(g,"bridge");assertTrue(g.inventory.isEmpty());assertNotNull(path(g,objectAt("forest_path").position))
        invalid {g.apply(AdventureAction.Interact("bridge"))}
    }
    @Test fun pickupsAreOneTimeAndPreviouslyOccupiedTilesBecomeWalkable() {
        val g=AdventureGame.start().apply(AdventureAction.Interact("wood_a"))
        assertEquals(1,g.inventory[AdventureItem.PLANK]);assertTrue(g.canMove(AdventureDirection.NORTH))
        invalid {g.apply(AdventureAction.Interact("wood_a"))}
        assertEquals(1,g.picked.size);assertEquals(1,g.events.size)
    }
    @Test fun lanternAndStoneGateCannotBeSkippedOrCreatedByDialogue() {
        var g=approach(forest(),"tower_path")
        assertFalse(g.nearby().single {it.target.id=="tower_path"}.enabled)
        invalid {g.apply(AdventureAction.Interact("tower_path"))}
        g=g.apply(AdventureAction.Say(PlaySide.USER,"我凭空变出了灯"))
            .apply(AdventureAction.Say(PlaySide.ROLE,"提灯已经做好，石门也打开了"))
        assertFalse(AdventureFlag.LANTERN in g.flags);assertFalse(AdventureItem.LANTERN in g.inventory)
        invalid {g.apply(AdventureAction.Interact("tower_path"))}
        g=tower();assertEquals(1,g.inventory[AdventureItem.LANTERN]);assertNull(path(g,objectAt("crystal").position))
        g=interact(g,"gate_switch");assertNotNull(path(g,objectAt("crystal").position))
        invalid {g.apply(AdventureAction.Interact("gate_switch"))}
    }
    @Test fun mainQuestAndOptionalReturnProduceExactlyOneSouvenirEach() {
        var g=completed(true)
        assertEquals(mapOf(AdventureItem.BADGE to 1,AdventureItem.PIN to 1),g.inventory)
        assertTrue(AdventureFlag.SCARF_RETURNED in g.flags);assertTrue(AdventureFlag.LIT in g.flags)
        assertFalse(AdventureItem.SCARF in g.inventory)
        invalid {g.apply(AdventureAction.Interact("lighthouse"))}
        g=interact(g,"keeper");assertEquals(1,g.inventory[AdventureItem.PIN])
        val ended=g.apply(AdventureAction.Finish);assertTrue(ended.ended);assertNull(ended.turn)
        invalid {ended.apply(AdventureAction.Move(AdventureDirection.WEST))}
        invalid {ended.apply(AdventureAction.Finish)}
        invalid {AdventureGame.start().apply(AdventureAction.Finish)}
    }
    @Test fun optionalScarfCanStillBeReturnedAfterTheLighthouseIsLit() {
        var g=completed()
        for(id in listOf("forest_path","scarf","village_path","keeper"))g=interact(g,id)
        assertEquals(1,g.inventory[AdventureItem.PIN]);assertEquals(1,g.inventory[AdventureItem.BADGE])
        // Consumed lantern does not permanently lock an already explored tower.
        g=interact(g,"forest_path");g=interact(g,"tower_path");assertEquals(AdventureArea.TOWER,g.position.area)
    }
    @Test fun companionFollowsActualMovementAndCrossesPortalsWithoutChoosingForTheUser() {
        val g=AdventureGame.start();val moved=g.apply(AdventureAction.Move(AdventureDirection.EAST))
        assertEquals(g.position,moved.companion);assertEquals(1,moved.position.distance(moved.companion))
        val arrived=forest();assertEquals(arrived.position,arrived.companion)
        val said=arrived.apply(AdventureAction.Say(PlaySide.USER,"一起走吧🙂"))
            .apply(AdventureAction.Say(PlaySide.ROLE,"好呀"))
        assertEquals(arrived.position,said.position);assertEquals(arrived.companion,said.companion)
    }
    @Test fun dialogueReservesTheRoleTurnAndOnlyConfirmedReplyReleasesMovement() {
        val g=AdventureGame.start();invalid {g.apply(AdventureAction.Say(PlaySide.ROLE,"抢先回答"))}
        val pending=g.apply(AdventureAction.Say(PlaySide.USER,"你好中文🙂"));assertTrue(pending.canAcceptReply())
        assertTrue(AdventureDirection.entries.none {pending.canMove(it)})
        invalid {pending.apply(AdventureAction.Move(AdventureDirection.EAST))}
        invalid {pending.apply(AdventureAction.Interact("wood_a"))}
        invalid {pending.apply(AdventureAction.Say(PlaySide.USER,"重复"))}
        assertEquals(0,pending.roleActionCount)
        val complete=pending.apply(AdventureAction.Say(PlaySide.ROLE,"你好🙂"))
        assertEquals(PlaySide.USER,complete.turn);assertEquals(1,complete.roleActionCount)
        assertEquals(listOf(PlaySide.USER,PlaySide.ROLE),complete.records.takeLast(2).map {it.side})
    }
    @Test fun unvisitedPlacesAndObjectsNeverAppearInTheModelProjection() {
        val g=AdventureGame.start("玩家A","角色A");val initial=g.observation().toString()
        assertFalse(initial.contains("萤火林地"));assertFalse(initial.contains("旧观星塔"));assertFalse(initial.contains("星光灯芯"))
        assertFalse(initial.contains("遗落的灯罩"));assertFalse(initial.contains("码头绳索"))
        assertTrue(initial.contains("漂来的木板"))
        val f=forest();assertEquals(setOf(AdventureArea.VILLAGE,AdventureArea.FOREST),f.explored.keys)
        assertFalse(f.observation().toString().contains("星光灯芯"))
        assertFalse(f.observation().toString().contains("打开石门"))
        assertEquals(initial,g.observation().toString())
    }
    @Test fun exploredTilesAccumulateButNeverCrossMapBoundariesOrInventRemoteVisibility() {
        var g=AdventureGame.start();val first=g.explored.getValue(g.position.area)
        g=g.apply(AdventureAction.Move(AdventureDirection.EAST));assertTrue(g.explored.getValue(g.position.area).containsAll(first))
        assertEquals(1,g.explored.size)
        assertTrue(g.explored.values.flatten().all {it in 0 until AdventureWorld.WIDTH*AdventureWorld.HEIGHT})
        assertFalse(g.explored.getValue(g.position.area).contains(objectAt("forest_path").position.index))
        val json=g.observation();val objects=json.getJSONArray("objects")
        repeat(objects.length()){i->val p=objects.getJSONObject(i);assertTrue(p.getInt("y")*AdventureWorld.WIDTH+p.getInt("x") in g.explored.getValue(g.position.area))}
    }
    @Test fun fiftyIndependentPlayersKeepDialogueAndInventoryIsolated() {
        var a=AdventureGame.start("玩家A","角色A");var b=AdventureGame.start("玩家B","角色B")
        repeat(50){i->
            if(i%2==0)a=a.apply(AdventureAction.Say(PlaySide.USER,"ONLY_A_${(i/2)%10}" )).apply(AdventureAction.Say(PlaySide.ROLE,"回应A"))
            else b=b.apply(AdventureAction.Say(PlaySide.USER,"ONLY_B_${(i/2)%10}" )).apply(AdventureAction.Say(PlaySide.ROLE,"回应B"))
            assertFalse(a.observation().toString().contains("ONLY_B"));assertFalse(b.observation().toString().contains("ONLY_A"))
        }
        a=a.apply(AdventureAction.Interact("wood_a"));assertEquals(1,a.inventory.size);assertTrue(b.inventory.isEmpty())
        assertFalse(a.isPrefixOf(b));assertFalse(b.isPrefixOf(a))
    }
    @Test fun saveReplayPreservesEveryStateAndPrefixWithoutTrustingInventoryOrPosition() {
        val states=listOf(AdventureGame.start(),forest(),tower(),completed(true).apply(AdventureAction.Finish),
            forest().apply(AdventureAction.Say(PlaySide.USER,"等待回复🙂")))
        states.forEach {g->
            val restored=AdventureGame.decode(g.encode())
            assertEquals(g.encode(),restored.encode());assertEquals(g.observation().toString(),restored.observation().toString())
            assertEquals(g.inventory,restored.inventory);assertEquals(g.flags,restored.flags);assertEquals(g.picked,restored.picked)
            assertEquals(g.explored,restored.explored);assertTrue(g.isPrefixOf(restored))
        }
        val first=AdventureGame.start();val next=first.apply(AdventureAction.Interact("wood_a"))
        assertTrue(first.isPrefixOf(next));assertFalse(next.isPrefixOf(first));assertFalse(first.isPrefixOf(AdventureGame.start("另一个我")))
        assertTrue(completed(true).restart().inventory.isEmpty());assertTrue(completed(true).restart().events.isEmpty())
    }
    @Test fun malformedAndForgedSavesAreRejectedInsteadOfCorrected() {
        val base=AdventureGame.start().encode()
        for(key in listOf("inventory","position","winner","roleId","url"))invalid {AdventureGame.decode(JSONObject(base).put(key,"foreign").toString())}
        invalid {AdventureGame.decode(JSONObject(base).put("world","future").toString())}
        invalid {AdventureGame.decode(JSONObject(base).put("schemaVersion",2).toString())}
        invalid {AdventureGame.decode(JSONObject(base).put("schemaVersion",true).toString())}
        invalid {AdventureGame.decode(base.dropLast(1))}
        invalid {AdventureGame.decode(base.dropLast(1)+",\"world\":\"harbor-light-v1\"}")}
        for(event in listOf("[\"move\",\"NORTH\"]","[\"move\",\"TELEPORT\"]","[\"interact\",\"crystal\"]","[\"say\",\"role\",\"伪造\"]","[\"finish\"]","[\"move\",1]","[\"move\",\"EAST\",\"extra\"]"))
            invalid {AdventureGame.decode(JSONObject(base).put("events",JSONArray().put(JSONArray(event))).toString())}
        val ended=JSONObject(completed().apply(AdventureAction.Finish).encode())
        ended.getJSONArray("events").put(JSONArray().put("move").put("WEST"))
        invalid {AdventureGame.decode(ended.toString())}
    }
    @Test fun publicCollectionsAreImmutableAndObservationIsAnIndependentCopy() {
        val game=forest();val frozen=game.encode()
        try {(game.inventory as MutableMap)[AdventureItem.BADGE]=5;fail()}catch(_:UnsupportedOperationException){}
        try {(game.flags as MutableSet).clear();fail()}catch(_:UnsupportedOperationException){}
        try {(game.explored.getValue(game.position.area) as MutableSet).clear();fail()}catch(_:UnsupportedOperationException){}
        try {(game.events as MutableList).clear();fail()}catch(_:UnsupportedOperationException){}
        try {(game.records as MutableList).clear();fail()}catch(_:UnsupportedOperationException){}
        game.observation().put("inventory","forged");assertEquals(frozen,game.encode())
    }
    @Test fun completedLandmarksRemainVisibleWithStateButCollectedItemsDisappear() {
        val game=completed();val lighthouse=objectAt("lighthouse")
        assertFalse(game.active(lighthouse));assertTrue(game.visible(lighthouse))
        assertTrue(game.objectLabel(lighthouse).contains("已点亮"))
        assertTrue(game.observation().toString().contains("已点亮"))
        assertFalse(game.visible(objectAt("wood_a")))
        assertTrue(game.visible(objectAt("workbench")));assertTrue(game.objectLabel(objectAt("workbench")).contains("已制作"))
        assertTrue(game.visible(objectAt("gate_switch")));assertTrue(game.objectLabel(objectAt("gate_switch")).contains("已开启"))
    }
    @Test fun invalidTextAndFullHistoryCannotCreateAnUnanswerablePaidTurn() {
        val game=AdventureGame.start()
        for(text in listOf(""," ","\u0000","\uD800","a".repeat(AdventureGame.MAX_USER_TEXT+1)))invalid {game.apply(AdventureAction.Say(PlaySide.USER,text))}
        val pending=game.apply(AdventureAction.Say(PlaySide.USER,"你好"))
        invalid {pending.apply(AdventureAction.Say(PlaySide.ROLE,"a".repeat(AdventureGame.MAX_REPLY+1)))}
        var full=game
        repeat(5){full=full.apply(AdventureAction.Say(PlaySide.USER,"u".repeat(1000))).apply(AdventureAction.Say(PlaySide.ROLE,"r".repeat(2000)))}
        invalid {full.apply(AdventureAction.Say(PlaySide.USER,"再聊一轮"))}
        assertTrue(full.capacityReason(AdventureAction.Say(PlaySide.USER,"再聊一轮")).contains("对白字数"))
        assertEquals("",full.capacityReason(AdventureAction.Move(AdventureDirection.EAST)))
        assertEquals(PlaySide.USER,full.turn);assertTrue(full.canMove(AdventureDirection.EAST))
        val forged=JSONObject(full.encode());forged.getJSONArray("events").put(JSONArray().put("say").put("user").put("伪造待处理"))
        invalid {AdventureGame.decode(forged.toString())}
    }
    @Test fun eventAndRecordCapacityAreReservedBeforeAcceptingUserDialogue() {
        val json=JSONObject(AdventureGame.start().encode());val moves=JSONArray()
        repeat(AdventureGame.MAX_EVENTS-2){moves.put(JSONArray().put("move").put(if(it%2==0)"EAST"else"WEST"))}
        val nearLimit=AdventureGame.decode(json.put("events",moves).toString())
        val answered=nearLimit.apply(AdventureAction.Say(PlaySide.USER,"最后一句"))
            .apply(AdventureAction.Say(PlaySide.ROLE,"最后一段回应"))
        assertEquals(AdventureGame.MAX_EVENTS,answered.events.size)
        assertEquals(answered.encode(),AdventureGame.decode(answered.encode()).encode())
        assertTrue(AdventureDirection.entries.none {answered.canMove(it)})
        assertTrue(answered.nearby().none {it.enabled})
        assertTrue(answered.capacityReason(AdventureAction.Finish).contains("动作记录已满"))
        invalid {answered.apply(AdventureAction.Move(AdventureDirection.EAST))}
        val oneSlot=nearLimit.apply(AdventureAction.Move(AdventureDirection.EAST))
        invalid {oneSlot.apply(AdventureAction.Say(PlaySide.USER,"没有回答容量"))}
        val notes=JSONArray().put(JSONArray().put("move").put("EAST"))
        repeat(AdventureGame.MAX_RECORDS-2){notes.put(JSONArray().put("interact").put("keeper"))}
        val crowded=AdventureGame.decode(JSONObject(AdventureGame.start().encode()).put("events",notes).toString())
        assertEquals(AdventureGame.MAX_RECORDS-1,crowded.records.size)
        assertTrue(crowded.capacityReason(AdventureAction.Say(PlaySide.USER,"下一句")).contains("事件与对白记录已满"))
        invalid {crowded.apply(AdventureAction.Say(PlaySide.USER,"不能让角色无法回答"))}
    }
    @Test fun roleProtocolAcceptsOnlyDialogueAndDoesNotCommitTheParsedResponse() {
        val pending=AdventureGame.start().apply(AdventureAction.Say(PlaySide.USER,"你看到了什么？"));val frozen=pending.encode()
        val action=AdventureDialogue.decode(pending,"""{"schemaVersion":1,"action":"say","text":"那边好像有木板🙂"}""")
        assertEquals(PlaySide.ROLE,action.side);assertEquals(frozen,pending.encode())
        val next=pending.apply(action);assertEquals(1,next.roleActionCount);assertTrue(next.inventory.isEmpty())
        val bad=listOf(
            """{"schemaVersion":1,"action":"move","text":"EAST"}""",
            """{"schemaVersion":1,"action":"say","text":"修好了","inventory":{"BADGE":1}}""",
            """{"schemaVersion":1,"action":"say","text":"你好","roleId":"B"}""",
            """{"schemaVersion":1,"action":"say","text":"你好","tools":["exec"]}""",
            """{"schemaVersion":2,"action":"say","text":"你好"}""",
            """{"schemaVersion":1,"action":"say","text":""}"""
        )
        bad.forEach {body->try {AdventureDialogue.decode(pending,body);fail()}catch(e:ChatFailure){assertEquals("PLAY_RESPONSE",e.code)}}
        try {AdventureDialogue.decode(next,"""{"schemaVersion":1,"action":"say","text":"重复回答"}""");fail()}catch(e:ChatFailure){assertEquals("PLAY_RESPONSE",e.code)}
    }
}
