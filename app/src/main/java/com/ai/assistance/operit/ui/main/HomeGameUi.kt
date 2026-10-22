package com.ai.assistance.operit.ui.main

import com.ai.assistance.operit.ui.main.ThemedIcon as Icon
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ai.assistance.operit.data.model.*
import kotlinx.coroutines.delay

private enum class HomePanel(val title:String) {
    MENU("家园"),BAG("背包"),ITEM("物品"),CRAFT("制作"),RECIPE("配方"),DECORATE("布置"),
    WISH("心愿"),WARDROBE("换装"),JOURNAL("回忆"),HELP("怎么玩"),MEAL("分享食物"),CONFIRM("确认整理")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun ColumnScope.HomeGameUi(home:HomeState,role:RoleEntity?,residents:List<RoleEntity>,vm:CompanionViewModel,ui:HomeUi,now:Long,enabled:Boolean,
    working:Boolean,initialCommandId:String?,onResident:(String)->Unit,onAction:(HomeAction)->Unit) {
    val scene=rememberHomeScene(home)
    var target by remember(home.scope.homeId){mutableStateOf<HomeTarget?>(null)}
    var panelName by rememberSaveable(home.scope.homeId){mutableStateOf(if(initialCommandId!=null)HomePanel.JOURNAL.name else null)}
    var itemName by rememberSaveable {mutableStateOf(HomeItem.CARROT.name)}
    var recipeName by rememberSaveable {mutableStateOf(HomeRecipe.CARROT_SOUP.name)}
    var count by rememberSaveable(itemName,recipeName){mutableIntStateOf(1)}
    var giftMode by rememberSaveable {mutableStateOf(false)}
    var confirm by remember {mutableStateOf<HomeAction?>(null)}
    var invitation by remember {mutableStateOf<HomeInvitation?>(null)}
    var feedbackItem by remember {mutableStateOf<Pair<HomeItem,Int>?>(null)}
    var speech by remember {mutableStateOf<String?>(null)}
    var feedbackId by remember {mutableLongStateOf(0L)}
    var previousWorld by remember {mutableStateOf(home)}
    var previousCommand by remember {mutableStateOf(ui.ledger.records.firstOrNull()?.commandId)}
    var panelHeight by remember {mutableStateOf(0.dp)}
    var panelWidth by remember {mutableStateOf(0.dp)}
    val density=LocalDensity.current
    val selected=target?.takeIf {it !is HomeTarget.Resident||it.roleId in home.residents}
    val panel=panelName?.let(HomePanel::valueOf)
    fun close(){panelName=null;target=null;confirm=null;giftMode=false}
    fun open(value:HomePanel){panelName=value.name}
    fun act(action:HomeAction){onAction(action)}
    fun together(kind:HomeTogetherKind) {
        if(role!=null) {
            if(kind==HomeTogetherKind.MEAL)open(HomePanel.MEAL)
            else {act(HomeAction.StartTogether(role.id,kind));close()}
        }
    }
    val canPlay=enabled&&home.player==scene.tile
    fun canTogether(kind:HomeTogetherKind)=canPlay&&role!=null&&home.life.together==null&&home.life.energy>=kind.energy&&
        !(kind==HomeTogetherKind.REST&&home.life.energy==HomeLife.MAX_ENERGY)
    LaunchedEffect(home.residents.keys) {
        if((target as? HomeTarget.Resident)?.roleId !in home.residents&&target is HomeTarget.Resident)target=null
        if(invitation?.roleId !in home.residents)invitation=null
    }
    LaunchedEffect(home.life.together){if(home.life.together!=null)invitation=null}
    LaunchedEffect(home.version) {
        val gained=home.inventory.entries.firstOrNull {(item,n)->n>(previousWorld.inventory[item]?:0)}
        if(gained!=null){feedbackItem=gained.key to gained.value-(previousWorld.inventory[gained.key]?:0);speech=null;feedbackId++}
        val latest=ui.ledger.records.firstOrNull()
        if(latest!=null&&latest.commandId!=previousCommand) {
            val action=latest.action()
            if(action is HomeAction.Talk){speech=home.residents.getValue(action.roleId).response;feedbackItem=null;feedbackId++}
            previousCommand=latest.commandId
        }
        previousWorld=home
    }
    LaunchedEffect(feedbackId){delay(3500);feedbackItem=null;speech=null}
    BackHandler(enabled=selected!=null||panel!=null||ui.nameDraft!=null) {
        if(ui.nameDraft!=null)vm.dismissHomeName(ui.token)else close()
    }
    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
        val wide=maxWidth>=600.dp&&maxWidth>maxHeight
        val compact=maxHeight<420.dp
        val hasPanel=selected!=null||panel!=null||ui.nameDraft!=null||ui.pending?.error!=null
        val minimalTarget=panel==null&&ui.nameDraft==null&&ui.pending?.error==null&&
            (selected is HomeTarget.Resource||selected is HomeTarget.Place&&selected.place==HomePlace.HOUSE)
        val overlayWidth=if(minimalTarget)minOf(184.dp,maxWidth-24.dp)else if(wide)minOf(336.dp,maxWidth*.46f)else minOf(360.dp,maxWidth-24.dp)
        val overlayMaxHeight=(if(wide)maxHeight-88.dp else minOf(maxHeight*.58f,maxHeight-148.dp)).coerceAtLeast(112.dp)
        // The map keeps its full bounds. The camera focuses into the unobscured area instead of putting controls outside it.
        HomeGameScene(home,role,residents,scene,now,enabled,selected,
            onTarget={target=it;panelName=null;giftMode=false;if(it is HomeTarget.Resident)onResident(it.roleId)},onMove=vm::homePositions,
            modifier=Modifier.fillMaxSize(),reservedBottom=if(hasPanel&&!wide)panelHeight+20.dp else 0.dp,
            reservedRight=if(hasPanel&&wide)panelWidth+20.dp else 0.dp,paused=hasPanel,
            onInvitation={if(!hasPanel&&invitation==null)invitation=it})
        if(working||ui.positionSaving)CircularProgressIndicator(Modifier.align(Alignment.TopCenter).padding(top=12.dp).size(24.dp),strokeWidth=2.dp)
        if(!homeClockValid(home,now))Text("请校准手机时间",Modifier.align(Alignment.TopCenter).padding(top=80.dp),color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
        FlowRow(Modifier.align(Alignment.TopStart).padding(12.dp).padding(end=120.dp),horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            HomeStat(HomeSymbol.COIN,"${home.life.coins}","家园币 ${home.life.coins}")
            HomeStat(HomeSymbol.ENERGY,"${home.life.energy}","精力 ${home.life.energy}")
            HomeStat(HomeSymbol.SEED,"${home.life.level}","等级 ${home.life.level}")
        }
        Row(Modifier.align(Alignment.TopEnd).padding(12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            HomeMapButton(Icons.Default.Map,if(scene.overview)"回到人物"else"查看全图"){scene.overview=!scene.overview}
            HomeMapButton(Icons.Default.MoreHoriz,"家园菜单"){open(HomePanel.MENU)}
        }
        if(!hasPanel) {
            HomeDirectionPad(scene,enabled,Modifier.align(Alignment.BottomStart).padding(12.dp))
            Column(Modifier.align(Alignment.BottomEnd).padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                HomeImageButton("背包",HomeSymbol.BAG,compact=true,onClick={giftMode=false;open(HomePanel.BAG)})
                if(!compact)HomeImageButton("布置",HomeSymbol.DECORATE,compact=true,onClick={open(HomePanel.DECORATE)})
            }
        }
        invitation?.takeIf {!hasPanel}?.let {offer->residents.firstOrNull {it.id==offer.roleId}?.let {person->
            Surface(Modifier.align(Alignment.TopCenter).padding(top=if(compact)68.dp else 76.dp).widthIn(max=320.dp),shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surfaceContainer) {
                Row(Modifier.padding(8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    HomePortrait(person.id,false,home.residents.getValue(person.id).outfit,Modifier.size(40.dp,48.dp))
                    Text(offer.intent.greeting,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=TextOverflow.Ellipsis)
                    HomeImageButton("回应 ${person.name} 的邀约",homeActivityPicture(offer.intent.activity),caption="回应",compact=true,enabled=canTogether(offer.intent.activity),onClick={
                        // Use the offer's identity immediately; the selected-role state updates on the next composition.
                        onResident(person.id);target=HomeTarget.Resident(person.id);invitation=null
                        if(offer.intent.activity==HomeTogetherKind.MEAL)open(HomePanel.MEAL)
                        else {act(HomeAction.StartTogether(person.id,offer.intent.activity));close()}
                    })
                    IconButton(onClick={invitation=null},modifier=Modifier.size(48.dp)){Icon(Icons.Default.Close,"暂不回应")}
                }
            }
        }}
        if(!hasPanel)home.life.together?.let {running->
            HomeActivityProgress(running,now,canPlay,onAction,Modifier.align(Alignment.BottomCenter).padding(bottom=if(compact)12.dp else 132.dp))
        }
        if(feedbackItem!=null||speech!=null)Surface(Modifier.align(Alignment.TopCenter).padding(top=136.dp).widthIn(max=260.dp),shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surfaceContainer) {
            Row(Modifier.padding(8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                feedbackItem?.let {(item,n)->HomeItemPicture(item,Modifier.size(36.dp));Text("+$n",style=MaterialTheme.typography.titleMedium)}
                speech?.let {HomeSymbolPicture(HomeSymbol.TALK,Modifier.size(24.dp));Text(it,style=MaterialTheme.typography.bodySmall,maxLines=2,overflow=TextOverflow.Ellipsis)}
            }
        }
        if(hasPanel) {
            val title=when {
                ui.pending?.error!=null->"操作未完成"
                ui.nameDraft!=null->"家园名称"
                panel==HomePanel.ITEM->HomeItem.valueOf(itemName).title
                panel==HomePanel.RECIPE->HomeRecipe.valueOf(recipeName).product.title
                panel!=null->panel.title
                selected is HomeTarget.Resident->residents.first {it.id==selected.roleId}.name
                selected is HomeTarget.Plot->home.plots[selected.index]?.crop?.title ?: "种点什么"
                selected is HomeTarget.Place->selected.place.title
                selected is HomeTarget.Resource->HomeWorld.resources[selected.index].kind.title
                else->error("Home panel requires a target")
            }
            Surface(Modifier.align(Alignment.BottomEnd).padding(12.dp).width(overlayWidth).heightIn(max=overlayMaxHeight)
                .onSizeChanged {panelHeight=with(density){it.height.toDp()};panelWidth=with(density){it.width.toDp()}}
                .pointerInput(Unit){detectTapGestures {}},shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.surfaceContainer,shadowElevation=3.dp) {
                Column(Modifier.padding(horizontal=12.dp,vertical=4.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(title,Modifier.weight(1f).semantics {heading()},style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=TextOverflow.Ellipsis)
                        IconButton(onClick={if(ui.nameDraft!=null)vm.dismissHomeName(ui.token)else close()},modifier=Modifier.size(48.dp)){Icon(Icons.Default.Close,"收起操作")}
                    }
                    Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()).padding(bottom=8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        when {
                            ui.pending?.error!=null->{
                                Text(ui.pending.error,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error)
                                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    HomeImageButton("重试同一次操作",HomeSymbol.CHECK,enabled=!working,onClick={vm.retryHome(ui.token)})
                                    HomeImageButton("放弃重试",HomeSymbol.TRASH,enabled=!working,onClick={vm.abandonHomeRetry(ui.token)})
                                }
                            }
                            ui.nameDraft!=null->{
                                OutlinedTextField(ui.nameDraft.text,{vm.homeNameDraft(ui.token,it)},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("名称")},enabled=enabled)
                                HomeImageButton("保存名称",HomeSymbol.CHECK,enabled=enabled&&ui.nameDraft.text.isNotBlank(),onClick={act(HomeAction.Rename(ui.nameDraft.text))})
                            }
                            panel!=null->when(panel) {
                                HomePanel.MENU->HomeImageGrid {
                                    HomeImageButton("背包",HomeSymbol.BAG,onClick={giftMode=false;open(HomePanel.BAG)})
                                    HomeImageButton("制作",HomeSymbol.CRAFT,onClick={open(HomePanel.CRAFT)})
                                    HomeImageButton("布置",HomeSymbol.DECORATE,onClick={open(HomePanel.DECORATE)})
                                    HomeImageButton("换装",HomeSymbol.WARDROBE,onClick={open(HomePanel.WARDROBE)})
                                    HomeImageButton("心愿",HomeSymbol.MAIL,onClick={open(HomePanel.WISH)})
                                    HomeImageButton("回忆",HomeSymbol.READ,onClick={open(HomePanel.JOURNAL)})
                                    HomeImageButton("更名",HomeSymbol.DOOR,onClick={panelName=null;vm.editHomeName(ui.token)})
                                    HomeImageButton("玩法",HomeSymbol.TALK,onClick={open(HomePanel.HELP)})
                                    HomeMapButton(Icons.Default.Add,"放大地图"){scene.overview=false;scene.zoom=(scene.zoom+.2f).coerceAtMost(1.6f)}
                                    HomeMapButton(Icons.Default.Remove,"缩小地图"){scene.overview=false;scene.zoom=(scene.zoom-.2f).coerceAtLeast(.6f)}
                                }
                                HomePanel.BAG->HomeImageGrid {
                                    home.inventory.entries.sortedBy {it.key.ordinal}.filter {!giftMode||it.key.giftable}.forEach {(item,n)->
                                        HomeItemButton(item,n,onClick={itemName=item.name;count=1;open(HomePanel.ITEM)})
                                    }
                                }
                                HomePanel.ITEM->{
                                    val item=HomeItem.valueOf(itemName);val stock=home.inventory[item]?:0
                                    val available=homeTransferLimit(home,item,false)
                                    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                        HomeItemPicture(item,Modifier.size(64.dp));Text("× $stock",style=MaterialTheme.typography.titleMedium)
                                    }
                                    if(item.group!=HomeItemGroup.SEED)HomeQuantityPicker(count,available,canPlay){count=it}
                                    val amount=count.coerceAtMost(available)
                                    HomeImageGrid {
                                        if(item.group==HomeItemGroup.SEED)HomeImageButton("去播种",HomeSymbol.SEED,onClick={val index=home.plots.indexOfFirst {it==null};if(index>=0){close();scene.go(HomeWorld.plots[index],HomeTarget.Plot(index))}},enabled=home.plots.any {it==null})
                                        if(item.giftable&&role!=null)HomeImageButton("送给 ${role.name}",HomeSymbol.GIFT,enabled=canPlay&&amount>0&&amount<=homeTransferLimit(home,item,true,role.id),onClick={act(HomeAction.Gift(role.id,item,amount));close()})
                                        if(HomeEconomy.price(item)>0)HomeImageButton("出售",HomeSymbol.SELL,count=HomeEconomy.price(item)*amount,enabled=canPlay&&amount>0,onClick={act(HomeAction.Sell(item,amount))})
                                        if(item.group==HomeItemGroup.CLOTHING)HomeImageButton("${if(home.userOutfit==item)"卸下"else"穿上"}",HomeSymbol.WARDROBE,enabled=canPlay&&stock>0,onClick={act(HomeAction.Wear(HomeWearer.USER,if(home.userOutfit==item)null else item))})
                                        if(item.group!=HomeItemGroup.SEED)HomeImageButton("丢弃",HomeSymbol.TRASH,enabled=canPlay&&amount>0,onClick={confirm=HomeAction.Discard(item,amount);open(HomePanel.CONFIRM)})
                                    }
                                }
                                HomePanel.CRAFT->HomeImageGrid {HomeRecipe.entries.forEach {recipe->HomeItemButton(recipe.product,homeCraftLimit(home,recipe),enabled=canPlay,onClick={recipeName=recipe.name;count=1;open(HomePanel.RECIPE)})}}
                                HomePanel.RECIPE->{
                                    val recipe=HomeRecipe.valueOf(recipeName);val max=homeCraftLimit(home,recipe)
                                    HomeItemPicture(recipe.product,Modifier.align(Alignment.CenterHorizontally).size(72.dp))
                                    HomeImageGrid {recipe.ingredients.forEach {(item,n)->HomeMaterialCost(item,home.inventory[item]?:0,n*count)}}
                                    HomeImageGrid {
                                        HomeQuantityPicker(count,max,canPlay){count=it}
                                        HomeImageButton("制作",HomeSymbol.CRAFT,enabled=canPlay&&count in 1..max,onClick={act(HomeAction.Craft(recipe,count))})
                                    }
                                }
                                HomePanel.DECORATE->HomeDecorationImages(home,canPlay,onAction)
                                HomePanel.WISH->HomeImageGrid {HomeWish.entries.forEach {wish->
                                    val claimed=home.life.day==HomeLife.dayAt(now)&&wish in home.life.claimed;val ready=home.life.canClaim(wish,now)
                                    HomeImageButton(if(claimed)"已领取"else if(ready)"领取"else wish.activity.title.removePrefix("一起"),if(ready||claimed)HomeSymbol.GIFT else homeActivityPicture(wish.activity),count=wish.coins,
                                        enabled=if(ready)canPlay&&!claimed else !claimed&&canTogether(wish.activity),onClick={if(ready)act(HomeAction.ClaimWish(wish))else together(wish.activity)})
                                }}
                                HomePanel.WARDROBE->{
                                    HomeImageGrid {residents.forEach {person->HomePictureButton(person.name,selected=person.id==role?.id,onClick={onResident(person.id)}){HomePortrait(person.id,false,home.residents.getValue(person.id).outfit,Modifier.size(40.dp,48.dp))}}}
                                    role?.let {person->HomeImageGrid {
                                        home.residents.getValue(person.id).received.filter {it.key.group==HomeItemGroup.CLOTHING}.forEach {(item,n)->HomeItemButton(item,n,enabled=canPlay,onClick={act(HomeAction.Wear(HomeWearer.ROLE,if(home.residents.getValue(person.id).outfit==item)null else item,person.id))})}
                                        HomeImageButton("选礼物",HomeSymbol.GIFT,onClick={giftMode=true;open(HomePanel.BAG)})
                                    }}
                                    if(home.userOutfit!=null)HomeImageButton("卸下我的服装",HomeSymbol.WARDROBE,enabled=canPlay,onClick={act(HomeAction.Wear(HomeWearer.USER,null))})
                                }
                                HomePanel.MEAL->HomeImageGrid {
                                    val meals=home.inventory.filter {it.key.group==HomeItemGroup.FOOD}
                                    meals.forEach {(item,n)->HomeItemButton(item,n,enabled=canTogether(HomeTogetherKind.MEAL),onClick={if(role!=null){act(HomeAction.StartTogether(role.id,HomeTogetherKind.MEAL,item));close()}})}
                                    if(meals.isEmpty())HomeImageButton("先做饭",HomeSymbol.CRAFT,onClick={open(HomePanel.CRAFT)})
                                }
                                HomePanel.CONFIRM->{
                                    val action=confirm
                                    if(action is HomeAction.Discard){HomeItemPicture(action.item,Modifier.size(64.dp));Text("丢弃 × ${action.count}？",style=MaterialTheme.typography.bodySmall)}
                                    if(action is HomeAction.ClearPlot)Text("清理作物，取回种子？",style=MaterialTheme.typography.bodySmall)
                                    HomeImageButton("确认整理",HomeSymbol.CHECK,enabled=canPlay&&action!=null,onClick={if(action!=null){act(action);close()}})
                                }
                                HomePanel.JOURNAL->{HomeHistoryControl(ui,!enabled,vm);ui.ledger.records.take(20).forEach {HomeRecord(it,it.commandId==initialCommandId)}}
                                HomePanel.HELP->HomeImageGrid {
                                    HomeImageButton("点地面走动",HomeSymbol.WALK,onClick={close()})
                                    HomeImageButton("点田地种植",HomeSymbol.SEED,onClick={close()})
                                    HomeImageButton("点人物互动",HomeSymbol.TALK,onClick={close()})
                                    HomeImageButton("点物件使用",HomeSymbol.CRAFT,onClick={close()})
                                }
                            }
                            selected!=null->{
                                when(selected) {
                                    is HomeTarget.Plot->{
                                        val crop=home.plots[selected.index]
                                        if(crop==null)HomeImageGrid {HomeCrop.entries.forEach {seed->HomeItemButton(seed.seed,home.inventory[seed.seed]?:0,enabled=canPlay&&(home.inventory[seed.seed]?:0)>0,onClick={act(HomeAction.Plant(selected.index,seed))})}}
                                        else {
                                            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                                                HomeItemPicture(crop.crop.produce,Modifier.size(48.dp))
                                                if(now<crop.readyAt)Text("${((crop.readyAt-now+999)/1000).coerceAtLeast(0)/60}:${(((crop.readyAt-now+999)/1000).coerceAtLeast(0)%60).toString().padStart(2,'0')}",style=MaterialTheme.typography.labelLarge)
                                            }
                                            LinearProgressIndicator(progress={((now-crop.plantedAt).toFloat()/(crop.readyAt-crop.plantedAt)).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
                                            HomeImageGrid {
                                                if(now>=crop.readyAt)HomeImageButton("收获",HomeSymbol.HARVEST,count=3,enabled=canPlay,onClick={act(HomeAction.Harvest(selected.index))})
                                                else HomeImageButton(if(crop.watered)"已浇水"else"浇水",HomeSymbol.WATER,enabled=canPlay&&!crop.watered,onClick={act(HomeAction.Water(selected.index))})
                                                HomeImageButton("清理",HomeSymbol.TRASH,enabled=canPlay,onClick={confirm=HomeAction.ClearPlot(selected.index);open(HomePanel.CONFIRM)})
                                            }
                                        }
                                    }
                                    is HomeTarget.Resident->{
                                        val person=home.residents.getValue(selected.roleId)
                                        HomeImageGrid {
                                            HomePortrait(selected.roleId,false,person.outfit,Modifier.size(48.dp,56.dp))
                                            HomeStat(HomeSymbol.HEART,"${person.friendship}","好感 ${person.friendship}")
                                            HomeImageButton("聊聊",HomeSymbol.TALK,enabled=canPlay,onClick={act(HomeAction.Talk(selected.roleId))})
                                            HomeImageButton("送礼",HomeSymbol.GIFT,onClick={giftMode=true;open(HomePanel.BAG)})
                                        }
                                        HomeImageGrid {
                                            HomeTogetherKind.entries.forEach {kind->HomeImageButton(kind.title.removePrefix("一起"),homeActivityPicture(kind),enabled=canTogether(kind),onClick={together(kind)})}
                                            HomeImageButton(if(home.followingRoleId==selected.roleId)"自由走动"else"同行",HomeSymbol.FOLLOW,enabled=canPlay,onClick={act(HomeAction.Follow(if(home.followingRoleId==selected.roleId)null else selected.roleId));close()})
                                        }
                                    }
                                    is HomeTarget.Resource->{
                                        val item=when(HomeWorld.resources[selected.index].kind){HomeResourceKind.TREE->HomeItem.WOOD;HomeResourceKind.ROCK->HomeItem.STONE;HomeResourceKind.BUSH->HomeItem.BERRY}
                                        HomeItemPicture(item,Modifier.align(Alignment.CenterHorizontally).size(64.dp))
                                        HomeImageButton(if(home.gatherReady(selected.index,now))"采集"else"恢复中",HomeSymbol.AXE,count=2,enabled=canPlay&&home.gatherReady(selected.index,now)&&home.life.energy>0,onClick={act(HomeAction.Gather(selected.index))})
                                    }
                                    is HomeTarget.Place->HomeImageGrid {
                                        val place=selected.place
                                        if(place==HomePlace.HOUSE)HomeImageButton(if(HomeWorld.interior(scene.tile))"出门"else"进屋",HomeSymbol.DOOR,enabled=canPlay,onClick={scene.go(HomeTile(14,if(HomeWorld.interior(scene.tile))10 else 8));close()})
                                        if(place==HomePlace.POND)HomeImageButton(if(home.fishReady(now))"钓鱼"else"休息中",HomeSymbol.FISH,enabled=canPlay&&home.fishReady(now)&&home.life.energy>0,onClick={act(HomeAction.Fish)})
                                        if(place==HomePlace.KITCHEN||place==HomePlace.WORKBENCH)HomeImageButton("制作",HomeSymbol.CRAFT,onClick={open(HomePanel.CRAFT)})
                                        if(place==HomePlace.MAILBOX)HomeImageButton("心愿",HomeSymbol.MAIL,onClick={open(HomePanel.WISH)})
                                        if(place==HomePlace.WARDROBE)HomeImageButton("换装",HomeSymbol.WARDROBE,onClick={open(HomePanel.WARDROBE)})
                                        val kinds=when(place) {
                                            HomePlace.SOFA->listOf(HomeTogetherKind.TEA,HomeTogetherKind.READ)
                                            HomePlace.BOOKSHELF->listOf(HomeTogetherKind.READ)
                                            HomePlace.BED->listOf(HomeTogetherKind.REST,HomeTogetherKind.CLEAN)
                                            HomePlace.TABLE,HomePlace.KITCHEN,HomePlace.CAMPFIRE->listOf(HomeTogetherKind.MEAL)
                                            HomePlace.BENCH->listOf(HomeTogetherKind.TEA,HomeTogetherKind.WALK)
                                            else->emptyList()
                                        }
                                        kinds.forEach {kind->HomeImageButton(kind.title.removePrefix("一起"),homeActivityPicture(kind),enabled=canTogether(kind),onClick={together(kind)})}
                                        if(place in listOf(HomePlace.SOFA,HomePlace.TABLE,HomePlace.BED,HomePlace.PLANT))HomeImageButton("布置",HomeSymbol.DECORATE,onClick={open(HomePanel.DECORATE)})
                                    }
                                }
                                home.life.together?.let {HomeActivityProgress(it,now,canPlay,onAction,Modifier.fillMaxWidth())}
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun HomeImageGrid(content:@Composable FlowRowScope.()->Unit) {
    FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)
}
@Composable private fun HomePictureButton(label:String,count:Int?=null,enabled:Boolean=true,selected:Boolean=false,compact:Boolean=false,caption:String=label,onClick:()->Unit,picture:@Composable ()->Unit) {
    // Large text uses two picture columns rather than shortening different seeds to identical labels.
    val tileWidth=if(!compact&&LocalDensity.current.fontScale>=1.5f)144.dp else 72.dp
    Surface(onClick=onClick,enabled=enabled,shape=RoundedCornerShape(12.dp),color=if(selected)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier=Modifier.width(tileWidth).heightIn(min=72.dp).alpha(if(enabled||selected)1f else .5f).semantics {contentDescription=label+(if(count!=null)"，数量 $count"else"")}) {
        Column(Modifier.padding(6.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(2.dp)) {
            Box(Modifier.size(40.dp,44.dp),contentAlignment=Alignment.Center){picture()}
            if(count!=null)Text("× $count",style=MaterialTheme.typography.labelMedium,maxLines=1)
            Text(caption,style=MaterialTheme.typography.labelSmall,maxLines=if(compact)1 else 2,overflow=TextOverflow.Ellipsis)
        }
    }
}
@Composable private fun HomeImageButton(label:String,symbol:HomeSymbol,count:Int?=null,enabled:Boolean=true,compact:Boolean=false,caption:String=label,onClick:()->Unit) {
    HomePictureButton(label,count,enabled,compact=compact,caption=caption,onClick=onClick){HomeSymbolPicture(symbol,Modifier.size(36.dp))}
}
@Composable private fun HomeItemButton(item:HomeItem,count:Int,enabled:Boolean=true,onClick:()->Unit,caption:String=item.title) {
    HomePictureButton(caption,count,enabled,onClick=onClick){HomeItemPicture(item,Modifier.size(40.dp))}
}
@Composable private fun HomeMaterialCost(item:HomeItem,owned:Int,needed:Int) {
    Row(Modifier.heightIn(min=48.dp).semantics {contentDescription="${item.title}，持有 $owned，需要 $needed"},verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        HomeItemPicture(item,Modifier.size(36.dp))
        Text("$owned/$needed",style=MaterialTheme.typography.labelLarge,color=if(owned>=needed)MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
    }
}
@Composable private fun HomeStat(symbol:HomeSymbol,value:String,label:String) {
    Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surfaceContainer,modifier=Modifier.semantics {contentDescription=label}) {
        Row(Modifier.padding(6.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
            HomeSymbolPicture(symbol,Modifier.size(22.dp));Text(value,style=MaterialTheme.typography.labelLarge)
        }
    }
}
@Composable private fun HomeMapButton(icon:ImageVector,label:String,onClick:()->Unit) {
    Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surfaceContainer,shadowElevation=2.dp) {
        IconButton(onClick=onClick,modifier=Modifier.size(48.dp)){Icon(icon,label)}
    }
}
@Composable private fun HomeDirectionPad(scene:HomeSceneController,enabled:Boolean,modifier:Modifier) {
    Column(modifier,horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(8.dp)) {
        HomeDirectionButton(Icons.Default.KeyboardArrowUp,"向上",enabled){scene.go(HomeTile(scene.tile.x,scene.tile.y-1))}
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            HomeDirectionButton(Icons.Default.KeyboardArrowLeft,"向左",enabled){scene.go(HomeTile(scene.tile.x-1,scene.tile.y))}
            HomeDirectionButton(Icons.Default.KeyboardArrowDown,"向下",enabled){scene.go(HomeTile(scene.tile.x,scene.tile.y+1))}
            HomeDirectionButton(Icons.Default.KeyboardArrowRight,"向右",enabled){scene.go(HomeTile(scene.tile.x+1,scene.tile.y))}
        }
    }
}
@Composable private fun HomeDirectionButton(icon:ImageVector,label:String,enabled:Boolean,onClick:()->Unit) {
    Surface(shape=RoundedCornerShape(12.dp),color=MaterialTheme.colorScheme.surfaceContainer) {
        IconButton(enabled=enabled,onClick=onClick,modifier=Modifier.size(48.dp)){Icon(icon,label)}
    }
}
@Composable private fun HomeQuantityPicker(value:Int,max:Int,enabled:Boolean,onChange:(Int)->Unit) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        IconButton(enabled=enabled&&value>1,onClick={onChange(value-1)},modifier=Modifier.size(48.dp)){Icon(Icons.Default.Remove,"减少数量")}
        Text("$value",style=MaterialTheme.typography.labelLarge)
        IconButton(enabled=enabled&&value<max,onClick={onChange(value+1)},modifier=Modifier.size(48.dp)){Icon(Icons.Default.Add,"增加数量")}
    }
}
@Composable private fun HomeActivityProgress(activity:HomeTogether,now:Long,enabled:Boolean,onAction:(HomeAction)->Unit,modifier:Modifier) {
    val left=((activity.readyAt-now+999)/1000).coerceAtLeast(0)
    Surface(modifier.widthIn(max=240.dp),shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.padding(8.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            HomeSymbolPicture(homeActivityPicture(activity.kind),Modifier.size(36.dp))
            Column(Modifier.weight(1f)) {
                Text(if(left>0)"${left}s"else"完成",style=MaterialTheme.typography.labelLarge)
                LinearProgressIndicator(progress={((now-activity.startedAt)/(activity.kind.seconds*1000f)).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
            }
            IconButton(enabled=enabled&&left==0L,onClick={onAction(HomeAction.FinishTogether)},modifier=Modifier.size(48.dp)){HomeSymbolPicture(HomeSymbol.GIFT,Modifier.size(36.dp).semantics {contentDescription="领取活动奖励"})}
        }
    }
}
@Composable private fun HomeDecorationImages(home:HomeState,enabled:Boolean,onAction:(HomeAction)->Unit) {
    HomeImageGrid {
        HomeMaterialCost(HomeItem.WOOD,home.inventory[HomeItem.WOOD]?:0,3)
        HomeMaterialCost(HomeItem.STONE,home.inventory[HomeItem.STONE]?:0,1)
    }
    HomeFurnitureSlot.entries.forEach {slot->
        Text(slot.title,style=MaterialTheme.typography.labelLarge)
        HomeImageGrid {HomeFurniture.entries.filter {it.slot==slot}.forEach {item->
            val owned=item in home.life.owned;val selected=home.life.placed[slot]==item
            HomePictureButton(item.title,if(owned)null else item.cost,selected=selected,
                enabled=enabled&&!selected&&home.life.level>=item.level&&(owned||home.life.coins>=item.cost&&(home.inventory[HomeItem.WOOD]?:0)>=3&&(home.inventory[HomeItem.STONE]?:0)>=1),onClick={onAction(HomeAction.Decorate(item))}){
                HomeFurniturePreview(item,Modifier.size(44.dp))
            }
        }}
    }
}
