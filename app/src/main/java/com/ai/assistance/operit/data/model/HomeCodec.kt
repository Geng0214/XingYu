package com.ai.assistance.operit.data.model

import org.json.JSONArray
import org.json.JSONObject

/** The only current world format. Retired per-role save formats are not game inputs. */
internal object HomeCodec {
    private fun stock(v:Map<HomeItem,Int>)=JSONObject().apply {v.toSortedMap(compareBy {it.name}).forEach {(k,n)->put(k.name,n)}}
    private fun tile(v:HomeTile)=JSONObject().put("x",v.x).put("y",v.y)
    private fun life(v:HomeLife)=JSONObject().put("coins",v.coins).put("xp",v.xp).put("energy",v.energy)
        .put("owned",JSONArray(v.owned.sortedBy {it.ordinal}.map {it.name}))
        .put("placed",JSONObject().apply {v.placed.entries.sortedBy {it.key.ordinal}.forEach {(slot,item)->put(slot.name,item.name)}})
        .put("together",v.together?.let {JSONObject().put("roleId",it.roleId).put("kind",it.kind.name).put("startedAt",it.startedAt)}?:JSONObject.NULL)
        .put("day",v.day).put("done",JSONArray(v.done.sortedBy {it.ordinal}.map {it.name}))
        .put("claimed",JSONArray(v.claimed.sortedBy {it.ordinal}.map {it.name})).put("lastActivity",v.lastActivity?.name?:JSONObject.NULL)
    private fun pet(v:HomePet)=JSONObject().put("name",v.name).put("adoptedAt",v.adoptedAt)
        .put("hunger",v.hunger).put("mood",v.mood).put("energy",v.energy).put("hygiene",v.hygiene).put("bond",v.bond)
        .put("lastTickAt",v.lastTickAt).put("lastCareAt",v.lastCareAt).put("lastCareBy",v.lastCareBy?:JSONObject.NULL)
        .put("handoffRoleId",v.handoffRoleId?:JSONObject.NULL).put("handoffAt",v.handoffAt)
        .put("userCareCount",v.userCareCount).put("roleCareCount",v.roleCareCount)
    fun encode(home:HomeState):String=JSONObject().put("schemaVersion",4).put("homeId",home.scope.homeId)
        .put("name",home.name).put("version",home.version).put("createdAt",home.createdAt).put("updatedAt",home.updatedAt)
        .put("inventory",stock(home.inventory))
        .put("plots",JSONArray().apply {home.plots.forEach {p->put(p?.let {JSONObject().put("crop",it.crop.name).put("plantedAt",it.plantedAt).put("watered",it.watered)}?:JSONObject.NULL)}})
        .put("userOutfit",home.userOutfit?.name?:JSONObject.NULL).put("player",tile(home.player))
        .put("residents",JSONArray(home.residents.toSortedMap().values.map {r->JSONObject().put("roleId",r.role.roleId).put("spaceId",r.role.spaceId)
            .put("tile",tile(r.tile)).put("outfit",r.outfit?.name?:JSONObject.NULL).put("received",stock(r.received))
            .put("friendship",r.friendship).put("response",r.response)}))
        .put("followingRoleId",home.followingRoleId?:JSONObject.NULL).put("resourceClaims",JSONArray(home.resourceClaims))
        .put("fishedAt",home.fishedAt).put("life",life(home.life)).put("pet",home.pet?.let(::pet)?:JSONObject.NULL).toString()
    private fun long(v:Any):Long {require(v is Int||v is Long);return (v as Number).toLong()}
    private fun nullableString(v:Any)=if(v===JSONObject.NULL)null else {require(v is String);v}
    private fun items(v:JSONObject)=v.keys().asSequence().associate {key->HomeItem.valueOf(key) to PlayJson.integer(v,key,1..HomeState.MAX_COUNT)}
    private fun outfit(v:Any)=nullableString(v)?.let(HomeItem::valueOf)
    internal fun readTile(v:JSONObject):HomeTile {PlayJson.keys(v,"x","y");return HomeTile(PlayJson.integer(v,"x",0 until HomeWorld.WIDTH),PlayJson.integer(v,"y",0 until HomeWorld.HEIGHT))}
    private fun <T> set(v:JSONArray,read:(String)->T):Set<T> {
        val entries=List(v.length()){i->val item=v.get(i);require(item is String);read(item)}
        require(entries.distinct().size==entries.size);return entries.toSet()
    }
    private fun readLife(v:JSONObject):HomeLife {
        PlayJson.keys(v,"coins","xp","energy","owned","placed","together","day","done","claimed","lastActivity")
        val positions=v.getJSONObject("placed");PlayJson.keys(positions,*HomeFurnitureSlot.entries.map {it.name}.toTypedArray())
        val current=v.get("together")
        val activity=if(current===JSONObject.NULL)null else {
            require(current is JSONObject);PlayJson.keys(current,"roleId","kind","startedAt")
            HomeTogether(PlayJson.string(current,"roleId"),HomeTogetherKind.valueOf(PlayJson.string(current,"kind")),long(current.get("startedAt")))
        }
        val last=nullableString(v.get("lastActivity"))
        return HomeLife.restore(PlayJson.integer(v,"coins",0..HomeLife.MAX_PROGRESS),PlayJson.integer(v,"xp",0..HomeLife.MAX_PROGRESS),
            PlayJson.integer(v,"energy",0..HomeLife.MAX_ENERGY),set(PlayJson.array(v,"owned"),HomeFurniture::valueOf),
            HomeFurnitureSlot.entries.associateWith {HomeFurniture.valueOf(PlayJson.string(positions,it.name))},activity,long(v.get("day")),
            set(PlayJson.array(v,"done"),HomeTogetherKind::valueOf),set(PlayJson.array(v,"claimed"),HomeWish::valueOf),last?.let(HomeTogetherKind::valueOf))
    }
    private fun readPet(value:Any):HomePet? {
        if(value===JSONObject.NULL)return null
        require(value is JSONObject)
        PlayJson.keys(value,"name","adoptedAt","hunger","mood","energy","hygiene","bond","lastTickAt","lastCareAt",
            "lastCareBy","handoffRoleId","handoffAt","userCareCount","roleCareCount")
        return HomePet(PlayJson.string(value,"name"),long(value.get("adoptedAt")),
            PlayJson.integer(value,"hunger",HomePetRules.MIN_NEED..HomePetRules.MAX_NEED),
            PlayJson.integer(value,"mood",HomePetRules.MIN_NEED..HomePetRules.MAX_NEED),
            PlayJson.integer(value,"energy",HomePetRules.MIN_NEED..HomePetRules.MAX_NEED),
            PlayJson.integer(value,"hygiene",HomePetRules.MIN_NEED..HomePetRules.MAX_NEED),
            PlayJson.integer(value,"bond",0..HomePetRules.MAX_BOND),long(value.get("lastTickAt")),long(value.get("lastCareAt")),
            nullableString(value.get("lastCareBy")),nullableString(value.get("handoffRoleId")),long(value.get("handoffAt")),
            PlayJson.integer(value,"userCareCount",0..HomePetRules.MAX_CARE_COUNT),
            PlayJson.integer(value,"roleCareCount",0..HomePetRules.MAX_CARE_COUNT))
    }
    fun remap(text:String,role:(String)->String,space:(String)->String):String {
        val v=JSONObject(decode(text).encode())
        val residents=v.getJSONArray("residents")
        repeat(residents.length()){index->val r=residents.getJSONObject(index);r.put("roleId",role(r.getString("roleId")));r.put("spaceId",space(r.getString("spaceId")))}
        if(v.get("followingRoleId")!==JSONObject.NULL)v.put("followingRoleId",role(v.getString("followingRoleId")))
        val together=v.getJSONObject("life").get("together")
        if(together is JSONObject)together.put("roleId",role(together.getString("roleId")))
        v.optJSONObject("pet")?.let {pet->
            if(pet.get("handoffRoleId")!==JSONObject.NULL)pet.put("handoffRoleId",role(pet.getString("handoffRoleId")))
            if(pet.get("lastCareBy")!==JSONObject.NULL)pet.put("lastCareBy",role(pet.getString("lastCareBy")))
        }
        return decode(v.toString()).encode()
    }
    fun decode(text:String):HomeState=try {
        val v=PlayJson.objectValue(text)
        val schema=PlayJson.integer(v,"schemaVersion",3..4)
        val keys=arrayOf("schemaVersion","homeId","name","version","createdAt","updatedAt","inventory","plots","userOutfit","player",
            "residents","followingRoleId","resourceClaims","fishedAt","life")
        val expected=if(schema==4)keys+"pet" else keys
        PlayJson.keys(v,*expected)
        require(PlayJson.string(v,"homeId")==HomeScope.ID)
        val plots=PlayJson.array(v,"plots");require(plots.length()==HomeState.PLOTS)
        val soil=List(plots.length()){i->val p=plots.get(i);if(p===JSONObject.NULL)null else {
            require(p is JSONObject);PlayJson.keys(p,"crop","plantedAt","watered");val watered=p.get("watered");require(watered is Boolean)
            HomePlot(HomeCrop.valueOf(PlayJson.string(p,"crop")),long(p.get("plantedAt")),watered)
        }}
        val array=PlayJson.array(v,"residents")
        val people=List(array.length()){i->val r=array.getJSONObject(i)
            PlayJson.keys(r,"roleId","spaceId","tile","outfit","received","friendship","response")
            HomeResident(RoleAccessScope(PlayJson.string(r,"roleId"),PlayJson.string(r,"spaceId")),readTile(r.getJSONObject("tile")),
                outfit(r.get("outfit")),items(r.getJSONObject("received")),PlayJson.integer(r,"friendship",0..999),PlayJson.string(r,"response"))}
        require(people.map {it.role.roleId}.distinct().size==people.size)
        val claims=PlayJson.array(v,"resourceClaims")
        HomeState.restore(PlayJson.string(v,"name"),long(v.get("version")),long(v.get("createdAt")),long(v.get("updatedAt")),
            items(v.getJSONObject("inventory")),soil,outfit(v.get("userOutfit")),readTile(v.getJSONObject("player")),
            people.associateBy {it.role.roleId},nullableString(v.get("followingRoleId")),List(claims.length()){long(claims.get(it))},
            long(v.get("fishedAt")),readLife(v.getJSONObject("life")),if(schema==4)readPet(v.get("pet"))else null)
    }catch(_:Exception){throw ChatFailure("HOME_FORMAT")}
}
