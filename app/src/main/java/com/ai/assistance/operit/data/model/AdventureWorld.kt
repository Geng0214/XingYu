package com.ai.assistance.operit.data.model

enum class AdventureArea(val wire:String,val title:String) {
    VILLAGE("village","海边村庄"), FOREST("forest","萤火林地"), TOWER("tower","旧观星塔")
}
enum class AdventureDirection(val dx:Int,val dy:Int,val title:String) {
    NORTH(0,-1,"向上"), EAST(1,0,"向右"), SOUTH(0,1,"向下"), WEST(-1,0,"向左")
}
data class AdventurePosition(val area:AdventureArea,val x:Int,val y:Int) {
    val index get()=y*AdventureWorld.WIDTH+x
    fun step(direction:AdventureDirection)=copy(x=x+direction.dx,y=y+direction.dy)
    fun distance(other:AdventurePosition)=if(area==other.area)kotlin.math.abs(x-other.x)+kotlin.math.abs(y-other.y)else Int.MAX_VALUE
}
enum class AdventureItem(val title:String) {
    PLANK("木板"), ROPE("绳索"), GLASS("灯罩"), EMBER("萤火种"), LANTERN("提灯"), CRYSTAL("星光灯芯"),
    SCARF("遗失的围巾"), PIN("海鸥胸针"), BADGE("归航纪念章")
}
enum class AdventureFlag { MET_KEEPER, BRIDGE, LANTERN, SWITCH, LIT, SCARF_RETURNED }
enum class AdventureObjectKind { PICKUP, KEEPER, BRIDGE, WORKBENCH, SWITCH, LIGHTHOUSE, PORTAL }
data class AdventureObject(val id:String,val title:String,val position:AdventurePosition,val kind:AdventureObjectKind,
    val item:AdventureItem?=null,val destination:AdventurePosition?=null,val blocking:Boolean=true)

/** Original fixed world. IDs and geography are part of the save version, not mutable model output. */
object AdventureWorld {
    const val ID="harbor-light-v1"
    const val WIDTH=13
    const val HEIGHT=11
    private val terrain=mapOf(
        AdventureArea.VILLAGE to listOf(
            "#############", "#....#......#", "#....#......#", "#....#......#", "#...........#",
            "#~~~~~.~~~~~#", "#...........#", "#...........#", "#...........#", "#...........#", "#############"),
        AdventureArea.FOREST to listOf(
            "#############", "#.....#.....#", "#..#..#.....#", "#..#........#", "#..###..##..#",
            "#...........#", "#..~~~......#", "#..~~~..#...#", "#.......#...#", "#...........#", "#############"),
        AdventureArea.TOWER to listOf(
            "#############", "#...........#", "#..#........#", "#..#..##....#", "#...........#",
            "########.####", "#...........#", "#..#.....#..#", "#..#.....#..#", "#...........#", "#############")
    )
    private fun p(area:AdventureArea,x:Int,y:Int)=AdventurePosition(area,x,y)
    val start=p(AdventureArea.VILLAGE,2,8)
    val objects:List<AdventureObject> = PlayJson.frozen(listOf(
        AdventureObject("wood_a","漂来的木板",p(AdventureArea.VILLAGE,2,7),AdventureObjectKind.PICKUP,AdventureItem.PLANK),
        AdventureObject("wood_b","旧木板",p(AdventureArea.VILLAGE,4,8),AdventureObjectKind.PICKUP,AdventureItem.PLANK),
        AdventureObject("rope","码头绳索",p(AdventureArea.VILLAGE,9,8),AdventureObjectKind.PICKUP,AdventureItem.ROPE),
        AdventureObject("keeper","守灯人",p(AdventureArea.VILLAGE,3,9),AdventureObjectKind.KEEPER),
        AdventureObject("bridge","修复木桥",p(AdventureArea.VILLAGE,6,6),AdventureObjectKind.BRIDGE,blocking=false),
        AdventureObject("lighthouse","海边灯塔",p(AdventureArea.VILLAGE,10,7),AdventureObjectKind.LIGHTHOUSE),
        AdventureObject("forest_path","通往林地",p(AdventureArea.VILLAGE,11,1),AdventureObjectKind.PORTAL,
            destination=p(AdventureArea.FOREST,2,9),blocking=false),
        AdventureObject("village_path","返回村庄",p(AdventureArea.FOREST,1,9),AdventureObjectKind.PORTAL,
            destination=p(AdventureArea.VILLAGE,10,1),blocking=false),
        AdventureObject("glass","遗落的灯罩",p(AdventureArea.FOREST,2,2),AdventureObjectKind.PICKUP,AdventureItem.GLASS),
        AdventureObject("ember","萤火种",p(AdventureArea.FOREST,9,5),AdventureObjectKind.PICKUP,AdventureItem.EMBER),
        AdventureObject("scarf","树枝上的围巾",p(AdventureArea.FOREST,10,8),AdventureObjectKind.PICKUP,AdventureItem.SCARF),
        AdventureObject("workbench","制作提灯",p(AdventureArea.FOREST,5,5),AdventureObjectKind.WORKBENCH),
        AdventureObject("tower_path","进入旧塔",p(AdventureArea.FOREST,11,1),AdventureObjectKind.PORTAL,
            destination=p(AdventureArea.TOWER,2,9),blocking=false),
        AdventureObject("forest_return","返回林地",p(AdventureArea.TOWER,1,9),AdventureObjectKind.PORTAL,
            destination=p(AdventureArea.FOREST,10,1),blocking=false),
        AdventureObject("gate_switch","打开石门",p(AdventureArea.TOWER,7,7),AdventureObjectKind.SWITCH),
        AdventureObject("crystal","星光灯芯",p(AdventureArea.TOWER,10,2),AdventureObjectKind.PICKUP,AdventureItem.CRYSTAL)
    ))
    init {
        require(objects.map {it.id}.distinct().size==objects.size)
        require(objects.map {it.position}.distinct().size==objects.size)
        require(terrain.values.all {rows->rows.size==HEIGHT&&rows.all {it.length==WIDTH&&it.all {c->c in "#.~"}}})
        require(objects.all {tile(it.position)=='.'&&(it.destination==null||tile(it.destination)=='.')})
    }
    fun tile(position:AdventurePosition):Char {
        if(position.x !in 0 until WIDTH||position.y !in 0 until HEIGHT)return '#'
        return terrain.getValue(position.area)[position.y][position.x]
    }
    fun groundOpen(position:AdventurePosition,flags:Set<AdventureFlag>)=tile(position)=='.'&&
        (position!=p(AdventureArea.VILLAGE,6,5)||AdventureFlag.BRIDGE in flags)&&
        (position!=p(AdventureArea.TOWER,8,5)||AdventureFlag.SWITCH in flags)
    fun groundLabel(position:AdventurePosition,flags:Set<AdventureFlag>)=when {
        tile(position)=='#'->"墙壁或密林"
        tile(position)=='~'->"水面"
        position==p(AdventureArea.VILLAGE,6,5)->if(AdventureFlag.BRIDGE in flags)"木桥"else"断桥"
        position==p(AdventureArea.TOWER,8,5)->if(AdventureFlag.SWITCH in flags)"打开的石门"else"关闭的石门"
        else->"道路"
    }
}
