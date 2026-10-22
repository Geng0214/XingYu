package com.ai.assistance.operit.ui.main

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ai.assistance.operit.data.model.*
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

internal class HomeTraffic {
    val actors=mutableMapOf<String,HomeSceneController>()
    fun occupied(identity:String)=actors.filterKeys {it!=identity}.values.flatMap {actor->
        if(actor.crossing)listOf(actor.origin,actor.tile)else listOf(actor.tile)
    }.toSet()
}

@Stable internal class HomeSceneController(start:HomeTile,val traffic:HomeTraffic,val identity:String) {
    init {check(identity !in traffic.actors);traffic.actors[identity]=this}
    var tile by mutableStateOf(start)
        private set
    var origin by mutableStateOf(start)
        private set
    val progress=Animatable(1f)
    var path by mutableStateOf(emptyList<HomeTile>())
        private set
    var pendingTarget by mutableStateOf<HomeTarget?>(null)
        private set
    var revision by mutableIntStateOf(0)
        private set
    var overview by mutableStateOf(false)
    var zoom by mutableFloatStateOf(1f)
    var followingRoleId by mutableStateOf<String?>(null)
    var crossing by mutableStateOf(false)
        private set
    var facing by mutableIntStateOf(1)
        private set
    val walking get()=path.isNotEmpty()
    val x get()=origin.x+(tile.x-origin.x)*progress.value
    val y get()=origin.y+(tile.y-origin.y)*progress.value

    fun go(destination:HomeTile,target:HomeTarget?=null):Boolean {
        val route=HomeWorld.route(tile,destination,target!=null,traffic.occupied(identity))
        if(route.isEmpty()&&!(tile==destination||target!=null&&tile.distance(destination)<=1))return false
        // A new tap commits the current tile visually before replacing its route; cancellation must not leave a half-step forever.
        origin=tile;crossing=false
        path=route;pendingTarget=target;revision++
        return true
    }
    suspend fun advance(duration:Int=170) {
        // Reserve both ends before suspending. Otherwise two simultaneous steps can claim the same cell or swap through each other.
        if(path.first() in traffic.occupied(identity)){path=emptyList();pendingTarget=null;return}
        origin=tile;crossing=true;tile=path.first();if(tile.x!=origin.x)facing=if(tile.x>origin.x)1 else -1;progress.snapTo(0f)
        progress.animateTo(1f,tween(duration,easing=LinearEasing))
        crossing=false;path=path.drop(1)
    }
    fun arrived():HomeTarget? {val target=pendingTarget;pendingTarget=null;return target}
}

internal data class HomeInvitation(val roleId:String,val intent:HomeIntent)

@Composable internal fun rememberHomeScene(home:HomeState):HomeSceneController {
    return remember(home.scope.homeId){HomeSceneController(home.player,HomeTraffic(),"user")}
}

private data class HomeCamera(val scale:Float,val left:Float,val top:Float) {
    fun screen(x:Float,y:Float)=Offset((x*HomeRenderFrame.TILE-left)*scale,(y*HomeRenderFrame.TILE-top)*scale)
    fun tile(point:Offset)=HomeTile(((point.x/scale+left)/HomeRenderFrame.TILE).toInt(),((point.y/scale+top)/HomeRenderFrame.TILE).toInt())
    companion object {
        fun create(width:Float,height:Float,unit:Float,scene:HomeSceneController):HomeCamera {
            val scale=if(scene.overview)minOf(width/(HomeWorld.WIDTH*HomeRenderFrame.TILE),height/(HomeWorld.HEIGHT*HomeRenderFrame.TILE))else unit*scene.zoom/HomeRenderFrame.TILE
            val visibleWidth=width/scale;val visibleHeight=height/scale
            val left=if(visibleWidth>=HomeWorld.WIDTH*HomeRenderFrame.TILE)(HomeWorld.WIDTH*HomeRenderFrame.TILE-visibleWidth)/2 else ((scene.x+.5f)*HomeRenderFrame.TILE-visibleWidth/2).coerceIn(0f,HomeWorld.WIDTH*HomeRenderFrame.TILE-visibleWidth)
            val top=if(visibleHeight>=HomeWorld.HEIGHT*HomeRenderFrame.TILE)(HomeWorld.HEIGHT*HomeRenderFrame.TILE-visibleHeight)/2 else ((scene.y+.5f)*HomeRenderFrame.TILE-visibleHeight*.58f).coerceIn(0f,HomeWorld.HEIGHT*HomeRenderFrame.TILE-visibleHeight)
            return HomeCamera(scale,left,top)
        }
    }
}

/** Rendering and taps use the same camera; changing the viewport never changes the world's tile coordinates. */
@Composable internal fun HomeGameScene(home:HomeState,role:RoleEntity?,residents:List<RoleEntity>,scene:HomeSceneController,now:Long,enabled:Boolean,
    selected:HomeTarget?,onTarget:(HomeTarget?)->Unit,onMove:(HomeAction.Move)->Unit,modifier:Modifier,
    reservedBottom:Dp=0.dp,reservedRight:Dp=0.dp,paused:Boolean=false,onInvitation:(HomeInvitation)->Unit={}) {
    val density=LocalDensity.current
    val context=androidx.compose.ui.platform.LocalContext.current
    val engine=remember(context){generateSequence(context){(it as? android.content.ContextWrapper)?.baseContext}.filterIsInstance<MainActivity>().first().homeEngine}
    val unit=with(density){30.dp.toPx()}
    var viewport by remember {mutableStateOf(Size.Zero)}
    val select by rememberUpdatedState(onTarget)
    val saveMove by rememberUpdatedState(onMove)
    val invite by rememberUpdatedState(onInvitation)
    val currentHome by rememberUpdatedState(home)
    val currentResidents by rememberUpdatedState(residents)
    val currentSelected by rememberUpdatedState(selected)
    val canAct by rememberUpdatedState(enabled&&!paused)
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val bottom=with(density){reservedBottom.toPx()};val right=with(density){reservedRight.toPx()}
    fun camera(width:Float,height:Float)=HomeCamera.create((width-right).coerceAtLeast(unit*3),(height-bottom).coerceAtLeast(unit*3),unit,scene)
    LaunchedEffect(scene.revision) {
        while(scene.path.isNotEmpty())scene.advance()
        select(scene.arrived())
    }
    val residentScenes=remember(home.scope.homeId){mutableStateMapOf<String,HomeSceneController>()}
    val brains=remember(home.scope.homeId){mutableMapOf<String,HomeResidentBrain>()}
    val emotions=remember(home.scope.homeId){mutableStateMapOf<String,HomeIntent>()}
    LaunchedEffect(residents.map {it.id}) {
        residentScenes.keys.filter {id->residents.none {it.id==id}}.forEach {residentScenes.remove(it);scene.traffic.actors.remove(it)}
        brains.keys.retainAll(residents.map {it.id}.toSet());emotions.keys.retainAll(residents.map {it.id}.toSet())
        residents.forEach {resident->if(resident.id !in residentScenes){
            val start=HomeWorld.residentTile(home.residents.getValue(resident.id).tile,scene.traffic.occupied(resident.id))
            residentScenes[resident.id]=HomeSceneController(start,scene.traffic,resident.id)
        }}
    }
    LaunchedEffect(residents.map {it.id to it.prompt}) {
        residents.forEach {brains[it.id]=HomeResidentBrain(HomePersonality.from(it.prompt),it.id)}
    }
    // Autonomy stops outside the resumed farm and during protected user decisions. It never spends inventory or claims rewards.
    LaunchedEffect(home.scope.homeId,lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while(true) {
                delay(1000)
                if(canAct) {
                    val time=System.currentTimeMillis();val world=currentHome
                    currentResidents.forEach {person->
                        val motion=residentScenes[person.id];val brain=brains[person.id]
                        if(motion!=null&&brain!=null&&person.id!=scene.followingRoleId&&person.id!=world.life.together?.roleId&&
                            person.id!=(currentSelected as? HomeTarget.Resident)?.roleId&&person.id!=(scene.pendingTarget as? HomeTarget.Resident)?.roleId&&!motion.walking) {
                            if(brain.approaching&&motion.tile.distance(scene.tile)<=1&&!scene.walking) {
                                emotions[person.id]=brain.intent
                                invite(HomeInvitation(person.id,brain.intent));brain.greeted(time)
                            } else if(brain.decide(time,scene.walking,world.residents.getValue(person.id).friendship)) {
                                emotions.remove(person.id)
                                val occupied=residentScenes.values.map {it.tile}.toSet()+scene.tile
                                val destination=if(brain.approaching)HomeWorld.neighbors(scene.tile).firstOrNull {it !in occupied}
                                    else brain.personality.destination(brain.intent,person.id,brain.turn,occupied)
                                if(destination!=null)motion.go(destination)
                            } else if(!motion.walking&&!brain.approaching)emotions[person.id]=brain.intent
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(home.followingRoleId){scene.followingRoleId=home.followingRoleId}
    LaunchedEffect(scene.tile,scene.followingRoleId,home.life.together?.roleId) {
        val companions=listOfNotNull(home.life.together?.roleId,scene.followingRoleId).distinct()
        companions.forEach {id->if(id in residentScenes)residentScenes.getValue(id).go(scene.tile,HomeTarget.Resident("user"))}
    }
    residents.forEach {resident->residentScenes[resident.id]?.let {motion->
        LaunchedEffect(resident.id,motion.revision,scene.followingRoleId,home.life.together?.roleId){
            val pace=if(resident.id==scene.followingRoleId||resident.id==home.life.together?.roleId)170 else HomePersonality.from(resident.prompt).paceMillis
            while(motion.path.isNotEmpty())motion.advance(pace)
            motion.arrived()
        }
    }}
    val positions=residents.filter {it.id in residentScenes}.map {resident->resident to residentScenes.getValue(resident.id).tile}
    val savedPositions=positions.associate {it.first.id to it.second}
    SideEffect {
        // Commit settled movement, not every animated NPC step; repeated writes otherwise disabled player actions throughout a stroll.
        if(enabled&&!scene.walking&&(home.player!=scene.tile||residentScenes.values.none {it.walking})&&savedPositions.keys==home.residents.keys&&
            (home.player!=scene.tile||home.residents.any {(id,r)->r.tile!=savedPositions[id]})) {
            saveMove(HomeAction.Move(scene.tile,savedPositions))
        }
    }
    val departed by rememberUpdatedState(HomeAction.Move(scene.tile,savedPositions))
    val expectedResidents by rememberUpdatedState(home.residents.keys)
    DisposableEffect(home.scope.homeId) {onDispose {if(departed.residents.keys==expectedResidents)saveMove(departed)}}
    // A closed roof hides occupants for both drawing and taps, while their actual indoor position remains unchanged.
    val visiblePositions=positions.filter {HomeWorld.interior(scene.tile)||!HomeWorld.interior(it.second)}
    // Camera transforms draw beyond the visible world; clipping keeps them out of the app bar and system insets.
    Box(modifier.clipToBounds().onSizeChanged {viewport=Size(it.width.toFloat(),it.height.toFloat())}) {
        val renderCamera=camera(viewport.width,viewport.height)
        val activity=home.life.together
        val actors=visiblePositions.map {(resident,_)->val motion=residentScenes.getValue(resident.id)
            HomeRenderActor(resident.id,motion.x,motion.y,false,home.residents.getValue(resident.id).outfit,motion.walking,motion.facing,
                if(resident.id==activity?.roleId)activity.kind else if(!motion.walking)emotions[resident.id]?.activity else null)}+
            HomeRenderActor("user",scene.x,scene.y,true,home.userOutfit,scene.walking,scene.facing,activity?.kind)
        val focus=selected?.let {target->if(target is HomeTarget.Resident)visiblePositions.firstOrNull {it.first.id==target.roleId}?.second else HomeWorld.tile(target)}
        HomeGodotSurface(HomeRenderFrame.packet(home,now,actors,renderCamera.left,renderCamera.top,renderCamera.scale,focus,scene.path,HomeWorld.interior(scene.tile),engine.reduceMotion),Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().pointerInput(home.scope.homeId,enabled,scene,visiblePositions,bottom,right) {
            detectTapGestures {point->
                if(enabled) {
                    val camera=camera(size.width.toFloat(),size.height.toFloat())
                    val tile=camera.tile(point)
                    val resident=visiblePositions.firstOrNull {(_,at)->at==tile||HomeTile(at.x,at.y-1)==tile}
                    resident?.let {residentScenes.getValue(it.first.id).go(it.second)}
                    val target=resident?.let {HomeTarget.Resident(it.first.id)}?:HomeWorld.target(tile,HomeWorld.interior(scene.tile))
                    val destination=when(target){is HomeTarget.Resident->requireNotNull(resident).second;null->tile;else->HomeWorld.tile(target)}
                    if(scene.go(destination,target)){scene.overview=false;select(null)}
                }
            }
        }.semantics {
            contentDescription="共享家园地图。点击地面走动，点击菜地种植，点击人物互动，点击房屋和工作台制作。"
            customActions=HomeWorld.plots.mapIndexed {index,tile->CustomAccessibilityAction("田地 ${index+1}，${homePlotLabel(home,index,now)}") {if(enabled)scene.go(tile,HomeTarget.Plot(index))else false}}+
                residents.map {resident->CustomAccessibilityAction("与 ${resident.name} 互动") {if(enabled&&resident.id in residentScenes)scene.go(residentScenes.getValue(resident.id).tile,HomeTarget.Resident(resident.id))else false}}+
                HomePlace.entries.map {place->CustomAccessibilityAction(place.title) {if(enabled)scene.go(place.tile,HomeTarget.Place(place))else false}}+
                HomeWorld.resources.mapIndexed {index,resource->CustomAccessibilityAction("采集${resource.kind.title}，${if(home.gatherReady(index,now))"可采集"else"恢复中"}") {if(enabled)scene.go(resource.tile,HomeTarget.Resource(index))else false}}
        })
        if(viewport!=Size.Zero) {
            val camera=camera(viewport.width,viewport.height)
            val labels=visiblePositions.filter {it.first.id==role?.id||(selected as? HomeTarget.Resident)?.roleId==it.first.id}.map {(resident,_)->val motion=residentScenes.getValue(resident.id);Triple(resident.name,camera.screen(motion.x+.5f,motion.y+1),false)}+
                Triple("我",camera.screen(scene.x+.5f,scene.y+1),true)
            val feet=visiblePositions.map {(resident,_)->val motion=residentScenes.getValue(resident.id);camera.screen(motion.x+.5f,motion.y+1)}+camera.screen(scene.x+.5f,scene.y+1)
            val silhouettes=visiblePositions.map {(resident,_)->val motion=residentScenes.getValue(resident.id);val foot=camera.screen(motion.x+.5f,motion.y+1)
                Rect(foot.x-18*camera.scale,foot.y-(if(resident.id in emotions&&!motion.walking)70 else 48)*camera.scale,foot.x+18*camera.scale,foot.y+6*camera.scale)}+
                Rect(feet.last().x-18*camera.scale,feet.last().y-48*camera.scale,feet.last().x+18*camera.scale,feet.last().y+6*camera.scale)
            // Measure scaled text before anchoring it: fixed offsets covered hats at 200% and whole people in overview.
            Layout(modifier=Modifier.fillMaxSize(),content={
                labels.forEach {(name,_,player)->
                    Text(name,Modifier.widthIn(min=32.dp,max=if(player)64.dp else 120.dp).background(Color(0xFF3E493C),RoundedCornerShape(4.dp)).padding(horizontal=4.dp,vertical=2.dp),
                        color=Color(0xFFFDF3D7),style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                }
            }) {measurables,constraints->
                val placed=mutableListOf<Rect>();val gap=6.dp.toPx()
                val measured=measurables.map {it.measure(constraints.copy(minWidth=0,minHeight=0,maxWidth=minOf(constraints.maxWidth,120.dp.roundToPx())))}
                layout(constraints.maxWidth,constraints.maxHeight) {
                    measured.forEachIndexed {index,child->
                        val (_,foot,player)=labels[index]
                        if(foot.x !in 0f..viewport.width||foot.y !in 0f..viewport.height)return@forEachIndexed
                        val x=(foot.x-child.width/2f).coerceIn(0f,(viewport.width-child.width).coerceAtLeast(0f))
                        val y=if(player)foot.y+6*camera.scale+gap else foot.y-48*camera.scale-gap-child.height
                        var bounds=Rect(x,y,x+child.width,y+child.height)
                        val blockers=(silhouettes+placed).sortedBy {if(player)it.top else -it.bottom}
                        blockers.forEach {blocker->if(bounds.overlaps(blocker)) {
                            val top=if(player)blocker.bottom+gap else blocker.top-gap-child.height
                            bounds=Rect(x,top,x+child.width,top+child.height)
                        }}
                        // Labels outside the camera are hidden with their off-screen actors, never clipped into stray fragments.
                        if(bounds.top>=0&&bounds.bottom<=viewport.height){child.place(bounds.left.roundToInt(),bounds.top.roundToInt());placed.add(bounds)}
                    }
                }
            }
        }
    }
}

@Composable internal fun HomeFurniturePreview(item:HomeFurniture,modifier:Modifier) {
    Canvas(modifier) {
        val color=Color(item.color)
        withTransform({scale(size.width/64,size.height/64,pivot=Offset.Zero)}) {
            when(item.slot) {
                HomeFurnitureSlot.SOFA->{drawRect(Color(0xFF765B3F),Offset(8f,49f),Size(6f,9f));drawRect(Color(0xFF765B3F),Offset(50f,49f),Size(6f,9f));drawRect(color,Offset(6f,20f),Size(52f,32f));drawRect(color.copy(alpha=.7f),Offset(2f,33f),Size(60f,19f));drawRect(Color(0xFFF1D7A5),Offset(12f,28f),Size(13f,12f))}
                HomeFurnitureSlot.TABLE->{drawRect(Color(0xFF765B3F),Offset(10f,28f),Size(6f,29f));drawRect(Color(0xFF765B3F),Offset(48f,28f),Size(6f,29f));drawRect(color,Offset(4f,20f),Size(56f,12f))}
                HomeFurnitureSlot.BED->{drawRect(Color(0xFF765B3F),Offset(4f,10f),Size(56f,47f));drawRect(color,Offset(7f,27f),Size(50f,26f));drawRect(Color(0xFFF5E8C8),Offset(10f,16f),Size(18f,11f));drawRect(Color(0xFFF5E8C8),Offset(35f,16f),Size(18f,11f))}
                HomeFurnitureSlot.PLANT->{drawRect(Color(0xFFBC875A),Offset(21f,39f),Size(22f,20f));drawRect(color,Offset(18f,12f),Size(9f,29f));drawRect(color,Offset(37f,9f),Size(9f,32f));drawRect(color,Offset(29f,18f),Size(7f,25f))}
            }
        }
    }
}
