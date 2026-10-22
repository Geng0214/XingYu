package com.ai.assistance.operit.data.model

import org.json.JSONObject

object HomeActionCodec {
    const val MAX_BYTES=PlayJson.MAX_BYTES
    fun remap(a:HomeAction,role:(String)->String,space:(String)->String,activity:(String)->String):HomeAction=when(a) {
        is HomeAction.Join->a.copy(role=RoleAccessScope(role(a.role.roleId),space(a.role.spaceId)))
        is HomeAction.Leave->a.copy(roleId=role(a.roleId))
        is HomeAction.Gift->a.copy(roleId=role(a.roleId))
        is HomeAction.Wear->a.copy(roleId=a.roleId?.let(role))
        is HomeAction.StartTogether->a.copy(roleId=role(a.roleId))
        is HomeAction.Follow->a.copy(roleId=a.roleId?.let(role))
        is HomeAction.Talk->a.copy(roleId=role(a.roleId))
        is HomeAction.Move->a.copy(residents=a.residents.mapKeys {role(it.key)})
        is HomeAction.AdventureReward->a.copy(sourceId=activity(a.sourceId),sourceRole=RoleAccessScope(role(a.sourceRole.roleId),space(a.sourceRole.spaceId)))
        else->a
    }
    fun encode(a:HomeAction):String {
        val j=JSONObject().put("schemaVersion",4)
        fun tile(v:HomeTile)=JSONObject().put("x",v.x).put("y",v.y)
        when(a) {
            is HomeAction.Plant->j.put("type","plant").put("plot",a.plot).put("crop",a.crop.name)
            is HomeAction.Water->j.put("type","water").put("plot",a.plot)
            is HomeAction.Harvest->j.put("type","harvest").put("plot",a.plot)
            is HomeAction.ClearPlot->j.put("type","clear").put("plot",a.plot)
            is HomeAction.Craft->j.put("type","craft").put("recipe",a.recipe.name).put("batches",a.batches)
            is HomeAction.Gift->j.put("type","gift").put("roleId",a.roleId).put("item",a.item.name).put("count",a.count)
            is HomeAction.Discard->j.put("type","discard").put("item",a.item.name).put("count",a.count)
            is HomeAction.Wear->j.put("type","wear").put("wearer",a.wearer.name).put("item",a.item?.name?:JSONObject.NULL).put("roleId",a.roleId?:JSONObject.NULL)
            is HomeAction.Rename->j.put("type","rename").put("name",a.name)
            is HomeAction.StartTogether->j.put("type","together_start").put("roleId",a.roleId).put("kind",a.kind.name).put("meal",a.meal?.name?:JSONObject.NULL)
            HomeAction.FinishTogether->j.put("type","together_finish")
            is HomeAction.Decorate->j.put("type","decorate").put("item",a.item.name)
            is HomeAction.ClaimWish->j.put("type","wish").put("wish",a.wish.name)
            is HomeAction.Join->j.put("type","join").put("roleId",a.role.roleId).put("spaceId",a.role.spaceId)
            is HomeAction.Leave->j.put("type","leave").put("roleId",a.roleId)
            is HomeAction.Follow->j.put("type","follow").put("roleId",a.roleId?:JSONObject.NULL)
            is HomeAction.Talk->j.put("type","talk").put("roleId",a.roleId)
            is HomeAction.Move->j.put("type","move").put("player",tile(a.player)).put("residents",JSONObject().apply {a.residents.toSortedMap().forEach {(id,t)->put(id,tile(t))}})
            is HomeAction.Gather->j.put("type","gather").put("resource",a.resource)
            HomeAction.Fish->j.put("type","fish")
            is HomeAction.Sell->j.put("type","sell").put("item",a.item.name).put("count",a.count)
            is HomeAction.AdventureReward->j.put("type","adventure_reward").put("sourceId",a.sourceId).put("reward",a.reward.name).put("proof",JSONObject(a.proof.json)).put("roleId",a.sourceRole.roleId).put("spaceId",a.sourceRole.spaceId)
        }
        return j.toString().also {require(it.toByteArray(Charsets.UTF_8).size<=MAX_BYTES)}
    }
    fun decode(text:String):HomeAction=try {
        require(text.toByteArray(Charsets.UTF_8).size<=MAX_BYTES)
        val j=PlayJson.objectValue(text);require(PlayJson.integer(j,"schemaVersion",4..4)==4)
        fun keys(vararg names:String)=PlayJson.keys(j,"schemaVersion","type",*names)
        fun role()=PlayJson.string(j,"roleId").also(PlaySessions::id)
        fun nullableRole()=if(j.get("roleId")===JSONObject.NULL)null else role()
        fun plot()=PlayJson.integer(j,"plot",0 until HomeState.PLOTS)
        fun item(key:String="item")=HomeItem.valueOf(PlayJson.string(j,key))
        fun count()=PlayJson.integer(j,"count",1..HomeState.MAX_COUNT)
        when(PlayJson.string(j,"type")) {
            "plant"->{keys("plot","crop");HomeAction.Plant(plot(),HomeCrop.valueOf(PlayJson.string(j,"crop")))}
            "water"->{keys("plot");HomeAction.Water(plot())}
            "harvest"->{keys("plot");HomeAction.Harvest(plot())}
            "clear"->{keys("plot");HomeAction.ClearPlot(plot())}
            "craft"->{keys("recipe","batches");HomeAction.Craft(HomeRecipe.valueOf(PlayJson.string(j,"recipe")),PlayJson.integer(j,"batches",1..99))}
            "gift"->{keys("roleId","item","count");HomeAction.Gift(role(),item(),count())}
            "discard"->{keys("item","count");HomeAction.Discard(item(),count())}
            "wear"->{keys("wearer","item","roleId");HomeAction.Wear(HomeWearer.valueOf(PlayJson.string(j,"wearer")),if(j.get("item")===JSONObject.NULL)null else item(),nullableRole())}
            "rename"->{keys("name");HomeAction.Rename(PlayJson.string(j,"name").also {PlayJson.text(it,80)})}
            "together_start"->{keys("roleId","kind","meal");HomeAction.StartTogether(role(),HomeTogetherKind.valueOf(PlayJson.string(j,"kind")),if(j.get("meal")===JSONObject.NULL)null else item("meal"))}
            "together_finish"->{keys();HomeAction.FinishTogether}
            "decorate"->{keys("item");HomeAction.Decorate(HomeFurniture.valueOf(PlayJson.string(j,"item")))}
            "wish"->{keys("wish");HomeAction.ClaimWish(HomeWish.valueOf(PlayJson.string(j,"wish")))}
            "join"->{keys("roleId","spaceId");HomeAction.Join(RoleAccessScope(role(),PlayJson.string(j,"spaceId").also(PlaySessions::id)))}
            "leave"->{keys("roleId");HomeAction.Leave(role())}
            "follow"->{keys("roleId");HomeAction.Follow(nullableRole())}
            "talk"->{keys("roleId");HomeAction.Talk(role())}
            "move"->{keys("player","residents");val people=j.getJSONObject("residents")
                HomeAction.Move(HomeCodec.readTile(j.getJSONObject("player")),people.keys().asSequence().associate {id->PlaySessions.id(id);id to HomeCodec.readTile(people.getJSONObject(id))})}
            "gather"->{keys("resource");HomeAction.Gather(PlayJson.integer(j,"resource",HomeWorld.resources.indices))}
            "fish"->{keys();HomeAction.Fish}
            "sell"->{keys("item","count");HomeAction.Sell(item(),count())}
            "adventure_reward"->{keys("sourceId","reward","proof","roleId","spaceId");HomeAction.AdventureReward(PlayJson.string(j,"sourceId").also(PlaySessions::id),HomeAdventureReward.valueOf(PlayJson.string(j,"reward")),HomeAdventureProof.decode(j.getJSONObject("proof").toString()),RoleAccessScope(role(),PlayJson.string(j,"spaceId").also(PlaySessions::id)))}
            else->error("Unknown home command")
        }
    }catch(_:Exception){throw ChatFailure("HOME_FORMAT")}
}
