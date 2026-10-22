package com.ai.assistance.operit.data.model

import kotlin.math.abs

data class HomeTile(val x:Int,val y:Int) {
    fun distance(other:HomeTile)=abs(x-other.x)+abs(y-other.y)
}

enum class HomePlace(val title:String,val tile:HomeTile) {
    HOUSE("小屋",HomeTile(14,9)), KITCHEN("厨房",HomeTile(16,8)),
    SOFA("沙发",HomeTile(11,6)), TABLE("餐桌",HomeTile(14,6)), BED("床",HomeTile(16,5)),
    BOOKSHELF("书柜",HomeTile(16,4)), WARDROBE("衣柜",HomeTile(11,4)), PLANT("花盆",HomeTile(12,8)),
    WORKBENCH("工作台",HomeTile(20,9)), POND("池塘",HomeTile(22,16)),
    BENCH("花园长椅",HomeTile(17,13)), CAMPFIRE("篝火",HomeTile(17,20)),
    MAILBOX("心愿信箱",HomeTile(10,10))
}

sealed interface HomeTarget {
    data class Plot(val index:Int):HomeTarget
    data class Resident(val roleId:String):HomeTarget
    data class Place(val place:HomePlace):HomeTarget
    data class Resource(val index:Int):HomeTarget
}

enum class HomeResourceKind(val title:String) { TREE("树木"), ROCK("石头"), BUSH("浆果丛") }
data class HomeResource(val kind:HomeResourceKind,val tile:HomeTile)

/** The same map supplies rendering, hit detection and navigation; scenery cannot disagree with collision. */
object HomeWorld {
    const val WIDTH=30
    const val HEIGHT=27
    val spawn=HomeTile(14,14)
    val plots=List(HomeState.PLOTS) {HomeTile(6+(it%3)*3,12+(it/3)*3)}
    val resources=listOf(
        HomeResource(HomeResourceKind.TREE,HomeTile(4,8)),HomeResource(HomeResourceKind.TREE,HomeTile(3,19)),
        HomeResource(HomeResourceKind.TREE,HomeTile(8,21)),HomeResource(HomeResourceKind.TREE,HomeTile(24,7)),
        HomeResource(HomeResourceKind.ROCK,HomeTile(20,22)),HomeResource(HomeResourceKind.ROCK,HomeTile(6,22)),
        HomeResource(HomeResourceKind.BUSH,HomeTile(19,17)),HomeResource(HomeResourceKind.BUSH,HomeTile(5,10))
    )
    val trees=listOf(HomeTile(2,4),HomeTile(5,3),HomeTile(8,2),HomeTile(21,3),HomeTile(26,4),HomeTile(28,9),HomeTile(27,21),HomeTile(23,24),HomeTile(12,24),HomeTile(2,24))
    val fences=(3..9).map {HomeTile(it,6)}+(20..27).map {HomeTile(it,6)}+(3..10).map {HomeTile(it,24)}+(17..26).map {HomeTile(it,24)}
    val furnitureTiles=mapOf(HomeTile(12,6) to HomePlace.SOFA,HomeTile(15,6) to HomePlace.TABLE,
        HomeTile(17,5) to HomePlace.BED,HomeTile(16,6) to HomePlace.BED,HomeTile(17,6) to HomePlace.BED,
        HomeTile(17,4) to HomePlace.BOOKSHELF,HomeTile(17,8) to HomePlace.KITCHEN)
    val residentSpawns:List<HomeTile> by lazy {
        (9 until HEIGHT-2).flatMap {y->(3 until WIDTH-2).map {x->HomeTile(x,y)}}
            .filter {walkable(it)&&it!=spawn&&it.x%2==1&&it.y%2==0}.sortedBy {it.distance(spawn)}
    }

    fun inside(tile:HomeTile)=tile.x in 1 until WIDTH-1&&tile.y in 2 until HEIGHT-1
    fun interior(tile:HomeTile)=tile.x in 11..17&&tile.y in 4..8
    private fun houseWall(tile:HomeTile)=tile.x in 10..18&&tile.y==3||tile.x in setOf(10,18)&&tile.y in 4..8||tile.y==9&&tile.x in 10..18&&tile.x!=14
    fun pond(tile:HomeTile):Boolean {
        val x=(tile.x-25f)/3.5f;val y=(tile.y-14f)/3.5f
        return x*x+y*y<=1f
    }
    fun walkable(tile:HomeTile)=inside(tile)&&!pond(tile)&&
        !houseWall(tile)&&tile !in furnitureTiles&&
        plots.none {tile.x in it.x..it.x+1&&tile.y in it.y..it.y+1}&&
        resources.none {it.tile==tile}&&tile !in trees&&tile !in fences&&tile!=HomeTile(18,13)&&
        HomePlace.entries.none {it!=HomePlace.HOUSE&&it!=HomePlace.POND&&it.tile==tile}

    fun neighbors(tile:HomeTile)=listOf(HomeTile(tile.x,tile.y-1),HomeTile(tile.x+1,tile.y),HomeTile(tile.x,tile.y+1),HomeTile(tile.x-1,tile.y)).filter(::walkable)

    /** Saved-world validation checks scenery; live movement also supplies occupied character cells. */
    fun route(from:HomeTile,to:HomeTile,interact:Boolean=false,occupied:Set<HomeTile> = emptySet()):List<HomeTile> {
        if(!walkable(from)||!inside(to))return emptyList()
        val queue=ArrayDeque<HomeTile>();val previous=mutableMapOf<HomeTile,HomeTile?>()
        queue.add(from);previous[from]=null
        var end:HomeTile?=null
        while(queue.isNotEmpty()) {
            val tile=queue.removeFirst()
            if(if(interact)tile.distance(to)<=1 else tile==to) {end=tile;break}
            neighbors(tile).forEach {next->if(next !in previous&&next !in occupied){previous[next]=tile;queue.add(next)}}
        }
        if(end==null)return emptyList()
        val path=mutableListOf<HomeTile>();var cursor:HomeTile?=end
        while(cursor!=null&&cursor!=from){path.add(cursor);cursor=previous[cursor]}
        return path.asReversed()
    }

    /** A resident sharing a saved cell with another person starts in the nearest reachable vacant cell. */
    fun residentTile(saved:HomeTile,occupied:Set<HomeTile>):HomeTile {
        require(walkable(saved))
        val queue=ArrayDeque<HomeTile>();val visited=mutableSetOf(saved)
        queue.add(saved)
        while(queue.isNotEmpty()) {
            val tile=queue.removeFirst()
            if(tile !in occupied)return tile
            neighbors(tile).forEach {if(visited.add(it))queue.add(it)}
        }
        error("No vacant resident cell")
    }

    fun target(tile:HomeTile,interiorVisible:Boolean):HomeTarget? {
        // A closed roof is one door target. Revealing the interior exposes its floor and actual furniture.
        if(!interiorVisible&&tile.x in 10..18&&tile.y in 3..8)return HomeTarget.Place(HomePlace.HOUSE)
        val plot=plots.indexOfFirst {tile.x in it.x..it.x+1&&tile.y in it.y..it.y+1}
        if(plot>=0)return HomeTarget.Plot(plot)
        val resource=resources.indexOfFirst {it.tile==tile}
        if(resource>=0)return HomeTarget.Resource(resource)
        HomePlace.entries.firstOrNull {it.tile==tile}?.let {return HomeTarget.Place(it)}
        furnitureTiles[tile]?.let {return HomeTarget.Place(it)}
        if(houseWall(tile))return HomeTarget.Place(HomePlace.HOUSE)
        if(pond(tile))return HomeTarget.Place(HomePlace.POND)
        return null
    }

    fun tile(target:HomeTarget)=when(target) {
        is HomeTarget.Plot->plots[target.index]
        is HomeTarget.Resource->resources[target.index].tile
        is HomeTarget.Place->target.place.tile
        is HomeTarget.Resident->error("Resident position belongs to its saved resident")
    }
}
