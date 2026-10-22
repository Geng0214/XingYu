package com.ai.assistance.operit.ui.main

import com.ai.assistance.operit.data.model.*
import org.json.JSONArray
import org.json.JSONObject

internal data class HomeRenderActor(val id:String,val x:Float,val y:Float,val user:Boolean,val outfit:HomeItem?,val walking:Boolean,val facing:Int,val activity:HomeTogetherKind?)

internal fun homeAvatarTile(identity:String,user:Boolean):Int=if(user)108 else listOf(85,86,87,88,96,97,98,99,100)[(identity.hashCode() and Int.MAX_VALUE)%9]

/** A projection, never a second save format. No prompts, chat text or credentials enter the engine. */
internal object HomeRenderFrame {
    const val TILE=32f
    private fun pair(tile:HomeTile)=JSONArray(listOf(tile.x,tile.y))
    private val geometry=JSONObject().put("width",HomeWorld.WIDTH).put("height",HomeWorld.HEIGHT)
        .put("pond",JSONArray((0 until HomeWorld.HEIGHT).flatMap {y->(0 until HomeWorld.WIDTH).map {x->HomeTile(x,y)}}.filter(HomeWorld::pond).map(::pair)))
        .put("trees",JSONArray(HomeWorld.trees.map(::pair))).put("fences",JSONArray(HomeWorld.fences.map(::pair)))
        .put("plots",JSONArray(HomeWorld.plots.map(::pair)))
        .put("resources",JSONArray(HomeWorld.resources.map {JSONObject().put("kind",it.kind.name).put("tile",pair(it.tile))}))
        .put("places",JSONObject().apply {HomePlace.entries.forEach {put(it.name,pair(it.tile))}})

    fun packet(home:HomeState,now:Long,actors:List<HomeRenderActor>,left:Float,top:Float,scale:Float,
               selected:HomeTile?,path:List<HomeTile>,interior:Boolean,reduceMotion:Boolean):String {
        require(scale.isFinite()&&scale>0&&left.isFinite()&&top.isFinite())
        require(actors.count {it.user}==1&&actors.map {it.id}.distinct().size==actors.size)
        require(actors.all {it.x.isFinite()&&it.y.isFinite()})
        return JSONObject().put("protocol",1).put("version",home.version).put("geometry",geometry)
            .put("camera",JSONObject().put("left",left).put("top",top).put("scale",scale))
            .put("interior",interior).put("reduceMotion",reduceMotion).put("hour",((now/3_600_000L+8)%24).toInt())
            .put("path",JSONArray(path.map(::pair))).put("selected",selected?.let(::pair)?:JSONObject.NULL)
            .put("actors",JSONArray(actors.map {actor->JSONObject().put("id",actor.id).put("x",actor.x).put("y",actor.y)
                .put("user",actor.user).put("sprite",homeAvatarTile(actor.id,actor.user)).put("walking",actor.walking).put("facing",actor.facing)
                .put("outfit",actor.outfit?.name?:JSONObject.NULL).put("activity",actor.activity?.name?:JSONObject.NULL)}))
            .put("plots",JSONArray(home.plots.map {plot->plot?.let {
                JSONObject().put("crop",it.crop.name).put("progress",((now-it.plantedAt).toDouble()/(it.readyAt-it.plantedAt)).coerceIn(0.0,1.0)).put("watered",it.watered)
            }?:JSONObject.NULL}))
            .put("resources",JSONArray(HomeWorld.resources.indices.map {home.gatherReady(it,now)}))
            .put("furniture",JSONObject().apply {home.life.placed.forEach {(slot,item)->put(slot.name,"#%06x".format(item.color and 0xFFFFFF))}})
            .toString()
    }
}
