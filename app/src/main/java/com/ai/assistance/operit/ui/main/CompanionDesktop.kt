package com.ai.assistance.operit.ui.main

import com.ai.assistance.operit.ui.main.ThemedIcon as Icon

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.filled.Yard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.key
import com.ai.assistance.operit.data.db.HomeView
import com.ai.assistance.operit.data.db.PlaySessionRow
import com.ai.assistance.operit.data.model.ConversationCard
import com.ai.assistance.operit.data.model.MusicTrack
import com.ai.assistance.operit.data.model.LocalMusic
import com.ai.assistance.operit.data.model.PlannerEntry
import com.ai.assistance.operit.data.model.PlannerRecords
import com.ai.assistance.operit.data.model.ReadingDocument
import com.ai.assistance.operit.data.model.RoleEntity
import com.ai.assistance.operit.data.model.SharedActivity
import com.ai.assistance.operit.data.model.PlayStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

internal enum class DesktopSection(val key: String, val title: String, val description: String) {
    TODAY("today", "今天", "日程、待办和生活安排"),
    READING("reading", "继续共读", "本机书架和最近阅读进度"),
    ACTIVITIES("activities", "共同回忆", "继续之前保存的活动")
}

internal enum class CompanionDockDestination(val key: String, val title: String, val tab: Int, val iconIndex: Int) {
    CHAT("chat", "聊天", 0, 0),
    COMPANION("companion", "家园", 1, 3),
    SCHEDULE("schedule", "日程", 2, 5),
    SETTINGS("settings", "设置", 3, 2)
}

internal data class CompanionDockConfig(
    val order: List<CompanionDockDestination>,
    val visible: Set<CompanionDockDestination>
) {
    fun orderValue():String = order.joinToString(",") {it.key}

    fun visibleValue():String = visible.joinToString(",") {it.key}

    companion object {
        internal const val ORDER_KEY = "desktop-dock-order"
        internal const val VISIBLE_KEY = "desktop-visible-dock"
        val DEFAULT = CompanionDockConfig(CompanionDockDestination.entries, CompanionDockDestination.entries.toSet())

        fun fromStoredValues(orderValue: String?, visibleValue: String?): CompanionDockConfig {
            val known = CompanionDockDestination.entries.associateBy { it.key } +
                ("plugins" to CompanionDockDestination.SCHEDULE) + ("roles" to CompanionDockDestination.SCHEDULE)
            val decodedOrder = orderValue?.split(',')?.mapNotNull(known::get).orEmpty()
            val order = (decodedOrder + CompanionDockDestination.entries).distinct()
            val visible = if (visibleValue == null) CompanionDockDestination.entries.toSet()
                else visibleValue.split(',').mapNotNull(known::get).toSet()
            return CompanionDockConfig(order, visible)
        }

        fun read(preferences: SharedPreferences): CompanionDockConfig = fromStoredValues(
            preferences.getString(ORDER_KEY, null), preferences.getString(VISIBLE_KEY, null)
        )

        fun putInto(editor: SharedPreferences.Editor, value: CompanionDockConfig): SharedPreferences.Editor = editor
            .putString(ORDER_KEY, value.orderValue())
            .putString(VISIBLE_KEY, value.visibleValue())

    }
}

internal enum class DesktopWidgetSize(val key: String, val label: String, val padding: androidx.compose.ui.unit.Dp, val spacing: androidx.compose.ui.unit.Dp, val itemLimit: Int) {
    COMPACT("compact", "紧凑", 12.dp, 6.dp, 2),
    STANDARD("standard", "标准", 16.dp, 10.dp, 4),
    EXPANDED("expanded", "舒展", 20.dp, 14.dp, 7)
}

private val LocalDesktopWidgetSize = staticCompositionLocalOf { DesktopWidgetSize.STANDARD }

internal enum class CompanionActivityKind(val key: String, val label: String) {
    HUB("hub", "共同活动"),
    READING("reading", "共读"),
    AUDIO("audio", "听歌"),
    VIDEO("video", "本机视频"),
    BILIBILI("bilibili", "B 站"),
    PLAY("play", "游戏与剧情"),
    HOME("home", "家园")
}

internal data class DesktopActivityLink(
    val chatId: String?,
    val id: String?,
    val kind: CompanionActivityKind,
    val title: String,
    val detail: String,
    val updatedAt: Long
)

internal data class TodayOptions(val schedule: Boolean = true, val tasks: Boolean = true, val garden: Boolean = true) {
    fun storedValue():String = buildList {
        if(schedule)add("schedule")
        if(tasks)add("tasks")
        if(garden)add("garden")
    }.joinToString(",")

    companion object {
        private const val KEY = "desktop-today-options"

        fun fromStoredValue(saved:String?):TodayOptions {
            val values=saved?.split(',')?.toSet()?:return TodayOptions()
            return TodayOptions("schedule" in values, "tasks" in values, "garden" in values)
        }

        fun read(preferences: SharedPreferences):TodayOptions = fromStoredValue(preferences.getString(KEY,null))

        fun putInto(editor:SharedPreferences.Editor,value:TodayOptions):SharedPreferences.Editor = editor.putString(KEY,value.storedValue())
    }
}

internal data class DesktopLayoutValues(
    val sectionOrder:String?,
    val visibleSections:String?,
    val sizes:String?
)

internal data class DesktopLayout(
    val order: List<DesktopSection>,
    val visible: Set<DesktopSection>,
    val sizes: Map<DesktopSection, DesktopWidgetSize>,
    val dock: CompanionDockConfig
) {
    fun storedValues()=DesktopLayoutValues(
        order.joinToString(",") {it.key},
        visible.joinToString(",") {it.key},
        sizes.entries.joinToString(",") {"${it.key.key}:${it.value.key}"}
    )

    companion object {
        val DEFAULT = DesktopLayout(
            DesktopSection.entries,
            DesktopSection.entries.toSet(),
            DesktopSection.entries.associateWith { DesktopWidgetSize.STANDARD },
            CompanionDockConfig.DEFAULT
        )
        private const val ORDER_KEY = "desktop-section-order"
        private const val VISIBLE_KEY = "desktop-visible-sections"
        private const val SIZES_KEY = "desktop-section-sizes"
        fun fromStoredValues(values:DesktopLayoutValues,dock:CompanionDockConfig=CompanionDockConfig.DEFAULT):DesktopLayout {
            val known = DesktopSection.entries.associateBy { it.key }
            val decodedOrder = values.sectionOrder?.split(',')?.mapNotNull(known::get).orEmpty()
            val order = (decodedOrder + DesktopSection.entries).distinct()
            val visible = if (values.visibleSections == null) DesktopSection.entries.toSet()
                else values.visibleSections.split(',').mapNotNull(known::get).toSet()
            val knownSizes = DesktopWidgetSize.entries.associateBy { it.key }
            val decodedSizes = values.sizes?.split(',')
                ?.mapNotNull { entry ->
                    val parts = entry.split(':', limit = 2)
                    val section = parts.firstOrNull()?.let(known::get)
                    val size = parts.getOrNull(1)?.let(knownSizes::get)
                    if (section != null && size != null) section to size else null
            }.orEmpty().toMap()
            val sizes = DesktopSection.entries.associateWith { decodedSizes[it] ?: DesktopWidgetSize.STANDARD }
            return DesktopLayout(order, visible, sizes, dock)
        }

        fun read(preferences:SharedPreferences):DesktopLayout = fromStoredValues(
            DesktopLayoutValues(
                preferences.getString(ORDER_KEY,null),preferences.getString(VISIBLE_KEY,null),preferences.getString(SIZES_KEY,null)
            ),
            CompanionDockConfig.read(preferences)
        )

        private fun putInto(editor:SharedPreferences.Editor,layout:DesktopLayout):SharedPreferences.Editor {
            val values=layout.storedValues()
            return editor
                .putString(ORDER_KEY,values.sectionOrder)
                .putString(VISIBLE_KEY,values.visibleSections)
                .putString(SIZES_KEY,values.sizes)
        }

        fun write(preferences: SharedPreferences, layout: DesktopLayout, todayOptions: TodayOptions) {
            val editor=putInto(preferences.edit(),layout)
            CompanionDockConfig.putInto(editor, layout.dock)
            TodayOptions.putInto(editor, todayOptions).apply()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ColumnScope.CompanionDesktopPage(
    state: CompanionState,
    vm: CompanionViewModel,
    onOpenChats: () -> Unit,
    onPortraitPick:(com.ai.assistance.operit.data.model.RoleAccessScope,String)->Unit,
    initialHomeRoleId:String?=null,
    initialHomeCommandId:String?=null,
    onInitialHomeConsumed:()->Unit={},
    onDockPreview: (CompanionDockConfig?) -> Unit,
    onOpenActivity: (RoleEntity, DesktopActivityLink) -> Unit,
    onOpenHomeRecord: (RoleEntity, String) -> Unit,
    onOpenActivities: (RoleEntity, String) -> Unit
) {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("companion-ui", Context.MODE_PRIVATE) }
    val dockPreviewChanged = rememberUpdatedState(onDockPreview)
    DisposableEffect(Unit) {
        onDispose { dockPreviewChanged.value(null) }
    }
    var layout by remember { mutableStateOf(DesktopLayout.read(preferences)) }
    var todayOptions by remember { mutableStateOf(TodayOptions.read(preferences)) }
    var previewLayout by remember { mutableStateOf<DesktopLayout?>(null) }
    var previewTodayOptions by remember { mutableStateOf<TodayOptions?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var plannerOpen by rememberSaveable { mutableStateOf(false) }
    var plannerEntryId by rememberSaveable { mutableStateOf<String?>(null) }
    var roleManagerOpen by rememberSaveable { mutableStateOf(false) }
    var openedHomeRoleId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(initialHomeRoleId,initialHomeCommandId) {
        if(initialHomeRoleId!=null)roleManagerOpen=true
    }
    if(roleManagerOpen) {
        RolePage(state,vm,onPortraitPick,openedHomeRoleId?:initialHomeRoleId,initialHomeCommandId,
            onInitialHomeConsumed={openedHomeRoleId=null;onInitialHomeConsumed()},onBack={roleManagerOpen=false})
        return
    }
    val openRoleHome:(RoleEntity)->Unit={candidate->openedHomeRoleId=candidate.id;roleManagerOpen=true}
    // Companion is a personal dashboard; its data follows the role already selected from the chat home.
    val role = state.roles.firstOrNull { it.scope() == state.scope }
    val activeLayout = previewLayout ?: layout
    val activeTodayOptions = previewTodayOptions ?: todayOptions
    val roleChats = role?.let { selected ->
        state.inbox.filter { it.roleId == selected.id && it.spaceId == selected.spaceId }
    }.orEmpty()
    val loadedScope = role?.scope() == state.scope
    val books = if (loadedScope) state.readingDocuments.sortedByDescending { it.updatedAt } else emptyList()
    val activities = if (loadedScope && role != null) state.sharedActivities
        .filter { it.roleId == role.id && it.spaceId == role.spaceId }
        .sortedByDescending { it.updatedAt }
    else emptyList()
    val scope = role?.scope()?.takeIf { loadedScope }
    var plannerData by remember(scope) { mutableStateOf<PlannerData?>(null) }
    var homeData by remember(scope) { mutableStateOf<HomeSummaryData?>(null) }
    var playSummary by remember(scope) { mutableStateOf<PlaySummaryData?>(null) }
    LaunchedEffect(scope) {
        plannerData = null
        homeData = null
        playSummary = null
        if (scope != null) {
            launch { vm.planner(scope).collect { plannerData = it } }
            launch { vm.homeSummary(scope).collect { homeData = it } }
            launch { vm.playSummary(scope).collect { playSummary = it } }
        }
    }
    val activityLinks = if (!loadedScope || role == null) emptyList() else buildList {
        activities.forEach { activity ->
            val book = books.firstOrNull { it.id == activity.readingDocumentId }
            val chapter = book?.chapters()?.getOrNull(activity.chapter)
            add(DesktopActivityLink(
                activity.chatId, activity.id, CompanionActivityKind.READING,
                book?.displayName ?: "共读记录",
                "共读 · ${chapter?.title ?: "进度已保存"} · ${activityStatus(activity.state)}",
                activity.updatedAt
            ))
        }
        val tracks = state.musicTracks.associateBy(MusicTrack::id)
        state.musicSessions.filter { it.roleId == role.id && it.spaceId == role.spaceId }.forEach { session ->
            val track = tracks[session.trackId] ?: return@forEach
            val kind = if (track.mediaKind == "audio") CompanionActivityKind.AUDIO else CompanionActivityKind.VIDEO
            add(DesktopActivityLink(
                session.chatId, session.id, kind, track.displayName,
                "${kind.label} · ${LocalMusic.time(session.positionMs)} · ${mediaStatus(session.state)}",
                session.updatedAt
            ))
        }
        state.bilibiliWatches.filter { it.roleId == role.id && it.spaceId == role.spaceId }.forEach { watch ->
            add(DesktopActivityLink(
                watch.chatId, watch.id, CompanionActivityKind.BILIBILI, watch.title,
                "B 站 · 第 ${watch.part} 集 · ${LocalMusic.time(watch.positionMs)}",
                watch.updatedAt
            ))
        }
        playSummary?.sessions.orEmpty().forEach { session ->
            val status = when (PlayStatus.valueOf(session.status)) {
                PlayStatus.ACTIVE -> "进行中"
                PlayStatus.PAUSED -> "已暂停"
                PlayStatus.COMPLETE -> "已结束"
                PlayStatus.INTERRUPTED -> "已中断"
            }
            val chatTitle = roleChats.firstOrNull { it.chatId == session.chatId }?.title
            add(DesktopActivityLink(
                session.chatId, session.id, CompanionActivityKind.PLAY, session.title,
                "游戏与剧情 · $status${chatTitle?.let { " · $it" } ?: ""}",
                session.updatedAt
            ))
        }
        homeData?.view?.recent.orEmpty().forEach { command ->
            add(DesktopActivityLink(
                null, command.commandId, CompanionActivityKind.HOME, command.description,
                "家园记录", command.createdAt
            ))
        }
    }.sortedByDescending(DesktopActivityLink::updatedAt)
    val now by produceState(System.currentTimeMillis(), scope) {
        while (true) {
            value = System.currentTimeMillis()
            delay(30_000)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 22.dp, end = 8.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("陪伴", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("把今天的计划、阅读和共同回忆放在一起", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { editing = true }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Edit, contentDescription = "编辑桌面布局")
            }
        }

        if (role == null) {
            Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.Start) {
                Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("给自己留一点时间", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text("选择聊天中的角色后，这里会显示对应的日程、阅读进度和共同回忆。", color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Button(onClick = onOpenChats) { Text("去聊天首页选择角色") }
                    }
                }
            }
        } else {
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val columnCount = if (maxWidth >= 600.dp) 2 else 1
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columnCount),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val visibleSections = activeLayout.order.filter { section ->
                        section in activeLayout.visible && when (section) {
                            // Empty personal libraries and activity histories add height without a useful next step on the first-run desktop.
                            DesktopSection.READING -> !loadedScope || books.isNotEmpty()
                            DesktopSection.ACTIVITIES -> !loadedScope || activityLinks.isNotEmpty() ||
                                homeData?.error != null || playSummary?.error != null || homeData == null || playSummary == null
                            else -> true
                        }
                    }
                    items(
                        items = visibleSections,
                        key = { it.key },
                        span = { section ->
                            val compactOnWideScreen = columnCount == 2 && activeLayout.sizes.getValue(section) == DesktopWidgetSize.COMPACT
                            GridItemSpan(if (columnCount == 1 || compactOnWideScreen) 1 else maxLineSpan)
                        }
                    ) { section ->
                        CompositionLocalProvider(LocalDesktopWidgetSize provides activeLayout.sizes.getValue(section)) {
                            when (section) {
                                DesktopSection.TODAY -> TodaySummaryCard(
                                    now = now,
                                    options = activeTodayOptions,
                                    planner = plannerData,
                                    home = homeData,
                                    onOpenPlanner = { entry -> plannerEntryId = entry?.id; plannerOpen = true },
                                    onOpenHome = { openRoleHome(role) }
                                )
                                DesktopSection.READING -> ReadingCard(role, loadedScope, books, activities, { onOpenChats() }, onOpenActivity)
                                DesktopSection.ACTIVITIES -> ActivitiesCard(
                                    role, loadedScope, activityLinks, homeData, playSummary, roleChats.firstOrNull()?.chatId,
                                    { onOpenChats() }, onOpenActivity, onOpenHomeRecord, onOpenActivities
                                )
                            }
                        }
                    }
                    if (!loadedScope && state.roles.size > 1) item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            "阅读和活动摘要只读取当前已打开角色的数据。点选角色后会切换到对应空间。",
                            Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        if (previewLayout != null) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text("正在预览，尚未保存", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TextButton(onClick = { editing = true }) { Text("调整") }
                        TextButton(onClick = {
                            previewLayout = null
                            previewTodayOptions = null
                            onDockPreview(null)
                        }) { Text("取消") }
                        Button(onClick = {
                            val staged = requireNotNull(previewLayout)
                            val stagedToday = requireNotNull(previewTodayOptions)
                            DesktopLayout.write(preferences, staged, stagedToday)
                            layout = staged
                            todayOptions = stagedToday
                            previewLayout = null
                            previewTodayOptions = null
                            onDockPreview(null)
                        }) { Text("保存") }
                    }
                }
            }
        }
    }

    if (editing) DesktopLayoutEditor(
        layout = activeLayout,
        todayOptions = activeTodayOptions,
        onDismiss = { editing = false },
        onPreview = { updated, summaryOptions ->
            previewLayout = updated
            previewTodayOptions = summaryOptions
            onDockPreview(updated.dock)
            editing = false
        }
    )
    if (plannerOpen && role != null) PlannerDialog(role, state, vm, plannerEntryId) { plannerOpen = false; plannerEntryId = null }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodaySummaryCard(
    now: Long,
    options: TodayOptions,
    planner: PlannerData?,
    home: HomeSummaryData?,
    onOpenPlanner: (PlannerEntry?) -> Unit,
    onOpenHome: () -> Unit
) {
    val size = LocalDesktopWidgetSize.current
    // Use the same sampled instant as entry filtering so the caption cannot cross midnight separately.
    val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
    DesktopCard(title = "今天的安排", caption = "${today.monthValue}月${today.dayOfMonth}日") {
        val showPlanner = options.schedule || options.tasks
        if (!showPlanner && !options.garden) {
            Text("今天摘要项目都已关闭。可在桌面布局编辑中重新开启。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val plannerEntries = planner?.entries.orEmpty()
        if (showPlanner) when {
            planner == null -> Text("正在读取日程和待办…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            planner.error != null -> Text("日程读取失败：${planner.error}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            else -> {
                val todayEntries = PlannerRecords.todaySummaryEntries(plannerEntries,options.schedule,options.tasks,now)
                    .sortedWith(compareBy<PlannerEntry>({ if (it.kind == "task") 1 else 0 }, {
                        if (it.kind == "task") -it.startsAt else plannerDisplayTime(it, now).toInstant().toEpochMilli()
                    }))
                val visibleEntries = todayEntries.take(size.itemLimit)
                if (visibleEntries.isEmpty()) {
                    val enabledTypes = listOfNotNull("日程和纪念日".takeIf { options.schedule }, "待办".takeIf { options.tasks })
                    Text("今天还没有${enabledTypes.joinToString("、")}。可以先记下一件想做的事。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else visibleEntries.forEach { entry ->
                    val zone = ZoneId.of(entry.zoneId)
                    val local = plannerDisplayTime(entry, now)
                    val localToday = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
                    val kindLabel = when (entry.kind) { "task" -> if (local.toLocalDate().isBefore(localToday)) "逾期待办" else "待办"; "anniversary" -> "纪念日"; else -> "日程" }
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { onOpenPlanner(entry) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(if (entry.kind == "task") Icons.Default.CheckCircleOutline else Icons.Default.Event, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(entry.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("$kindLabel · ${local.toLocalDate()} ${local.toLocalTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))} · ${entry.zoneId}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                // Keep the freshness label within the user's enabled categories; hidden entries must not affect the summary.
                visibleEntries.maxOfOrNull { it.updatedAt }?.let { updated ->
                    val source = listOfNotNull(
                        "角色日程和纪念日".takeIf { visibleEntries.any { it.kind != "task" } },
                        "待办".takeIf { visibleEntries.any { it.kind == "task" } }
                    ).joinToString("与")
                    Text("来源：$source · 更新于 ${dateLabel(updated)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (options.garden) when {
            home == null -> Text("正在读取家园菜地…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            home.error != null -> Text("家园读取失败：${home.error}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            home.view == null -> Text("首次打开家园即可入住。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> {
                val row = home.view.home
                val saved = row.value()
                Text("${saved.name} · 菜地", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                if (!homeClockValid(saved, now)) {
                    Text("手机时间与家园存档不一致，暂不显示成熟状态。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                } else {
                    val planted = saved.plots.mapIndexedNotNull { index, plot -> plot?.let { index to it } }
                    if (planted.isEmpty()) Text("菜地还没有种植。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else planted.forEach { (index, plot) ->
                        Text("第 ${index + 1} 块 · ${plot.crop.title} · ${homePlotLabel(saved, index, now)}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                Text("来源：本机家园菜地 · 更新于 ${dateLabel(row.updatedAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (showPlanner) {
            Text("家园菜地摘要已关闭。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (showPlanner) OutlinedButton(onClick = { onOpenPlanner(null) }) { Text("打开日程") }
            if (options.garden) OutlinedButton(onClick = onOpenHome) { Text("打开家园") }
        }
    }
}

private fun plannerDisplayTime(entry: PlannerEntry, now: Long) =
    Instant.ofEpochMilli(entry.startsAt).atZone(ZoneId.of(entry.zoneId)).let { original ->
        if (entry.kind == "anniversary") {
            Instant.ofEpochMilli(now).atZone(original.zone).toLocalDate().atTime(original.toLocalTime()).atZone(original.zone)
        } else original
    }

@Composable
private fun ReadingCard(
    role: RoleEntity,
    loadedScope: Boolean,
    books: List<ReadingDocument>,
    activities: List<SharedActivity>,
    onOpenChats: () -> Unit,
    onOpenActivity: (RoleEntity, DesktopActivityLink) -> Unit
) {
    DesktopCard(title = "继续共读", caption = "读取本机书架和保存的进度") {
        when {
            !loadedScope -> Text("去聊天首页选择角色后，这里会显示已保存的阅读进度。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            books.isEmpty() -> Text("书架为空 · 导入的书只保存在本机。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> {
                val book = books.first()
                val chapter = book.chapters().getOrNull(book.currentChapter)
                Text(book.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(chapter?.title?.let { "读到：$it" } ?: "已保存阅读进度", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val activity = activities.firstOrNull { it.readingDocumentId == book.id }
                if (activity != null) OutlinedButton(onClick = {
                    onOpenActivity(role, DesktopActivityLink(activity.chatId, activity.id, CompanionActivityKind.READING,
                        book.displayName, "共读 · ${chapter?.title ?: "进度已保存"}", activity.updatedAt))
                }) { Text("打开这次共读") }
                else Text("共保存 ${books.size} 本书；可以从聊天的共同活动中继续共读。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!loadedScope) TextButton(onClick = onOpenChats) { Text("选择角色") }
    }
}

@Composable
private fun ActivitiesCard(
    role: RoleEntity,
    loadedScope: Boolean,
    activities: List<DesktopActivityLink>,
    homeSummary: HomeSummaryData?,
    playSummary: PlaySummaryData?,
    latestChatId: String?,
    onOpenChats: () -> Unit,
    onOpenActivity: (RoleEntity, DesktopActivityLink) -> Unit,
    onOpenHomeRecord: (RoleEntity, String) -> Unit,
    onOpenActivities: (RoleEntity, String) -> Unit
) {
    val size = LocalDesktopWidgetSize.current
    DesktopCard(title = "共同回忆", caption = "回到保存的聊天、阅读和活动记录") {
        when {
            !loadedScope -> Text("去聊天首页选择角色后，这里会整理已保存的共同活动。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            activities.isEmpty() && (homeSummary?.error != null || playSummary?.error != null) -> Text(
                listOfNotNull(homeSummary?.error?.let { "家园：$it" }, playSummary?.error?.let { "游戏与剧情：$it" })
                    .joinToString("；", prefix = "部分记录读取失败："),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error
            )
            activities.isEmpty() && (homeSummary == null || playSummary == null) -> Text("正在读取共同活动记录…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            activities.isEmpty() -> Text("还没有共同活动记录。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> {
                activities.take(size.itemLimit).forEachIndexed { index, activity ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 64.dp)
                            .clickable(role = Role.Button) {
                                if (activity.kind == CompanionActivityKind.HOME) onOpenHomeRecord(role, requireNotNull(activity.id))
                                else onOpenActivity(role, activity)
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(activityIcon(activity.kind), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(activity.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(activity.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(dateLabel(activity.updatedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        if (loadedScope && activities.isNotEmpty() && playSummary?.error != null) {
            Text("游戏与剧情摘要读取失败：${playSummary.error}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (loadedScope && activities.isNotEmpty() && homeSummary?.error != null) {
            Text("家园记录读取失败：${homeSummary.error}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (!loadedScope) TextButton(onClick = onOpenChats) { Text("选择角色") }
        else if (latestChatId != null) OutlinedButton(onClick = { onOpenActivities(role, latestChatId) }) { Text("打开共同活动") }
        else Text("先为这个角色创建一个聊天，就可以从聊天里发起共同活动。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun activityIcon(kind: CompanionActivityKind) = when (kind) {
    CompanionActivityKind.READING -> Icons.AutoMirrored.Filled.MenuBook
    CompanionActivityKind.AUDIO -> Icons.Default.Headphones
    CompanionActivityKind.VIDEO -> Icons.Default.Movie
    CompanionActivityKind.BILIBILI -> Icons.Default.OndemandVideo
    CompanionActivityKind.PLAY -> Icons.Default.SportsEsports
    CompanionActivityKind.HUB -> Icons.Default.Widgets
    CompanionActivityKind.HOME -> Icons.Default.Yard
}

private fun activityStatus(state: String) = when (state) {
    "active" -> "进行中"
    "paused" -> "已暂停"
    "completed" -> "已结束"
    else -> error("Unknown shared activity state")
}

private fun mediaStatus(state: String) = when (state) {
    "playing" -> "播放中"
    "paused" -> "已暂停"
    "completed" -> "已结束"
    else -> error("Unknown media session state")
}

@Composable
private fun DesktopCard(title: String, caption: String, content: @Composable ColumnScope.() -> Unit) {
    val size = LocalDesktopWidgetSize.current
    Surface(shape = RoundedCornerShape((size.padding + 6.dp)), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(size.padding), verticalArrangement = Arrangement.spacedBy(size.spacing)) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DesktopLayoutEditor(
    layout: DesktopLayout,
    todayOptions: TodayOptions,
    onDismiss: () -> Unit,
    onPreview: (DesktopLayout, TodayOptions) -> Unit
) {
    var order by remember(layout) { mutableStateOf(layout.order) }
    val density = LocalDensity.current
    val reorderStep = with(density) { 112.dp.toPx() }
    var visible by remember(layout) { mutableStateOf(layout.visible) }
    var sizes by remember(layout) { mutableStateOf(layout.sizes) }
    var dock by remember(layout) { mutableStateOf(layout.dock) }
    var summaryOptions by remember(layout, todayOptions) { mutableStateOf(todayOptions) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑陪伴桌面") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = adaptiveDialogContentMaxHeight(540.dp)).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("勾选显示状态，可用上下按钮排序；触屏也可长按拖动手柄。宽屏下紧凑卡片占半行，标准和舒展卡片占整行；窄屏保持单列，尺寸会调整间距和内容条数。")
                order.forEachIndexed { index, section ->
                    key(section.key) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            // Keep each checkbox and its visible label in one TalkBack toggle target.
                            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                                Row(
                                    Modifier.weight(1f).toggleable(
                                    value = section in visible,
                                        role = Role.Checkbox,
                                        onValueChange = { checked -> visible = if (checked) visible + section else visible - section }
                                    ),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(checked = section in visible, onCheckedChange = null)
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                        Text(section.title, style = MaterialTheme.typography.titleSmall)
                                        Text(section.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                IconButton(
                                    enabled = index > 0,
                                    onClick = { order = order.toMutableList().also { it.add(index - 1, it.removeAt(index)) } },
                                    modifier = Modifier.size(48.dp).semantics { contentDescription = "将${section.title}上移" }
                                ) { Icon(Icons.Default.ArrowUpward, contentDescription = null) }
                                IconButton(
                                    enabled = index < order.lastIndex,
                                    onClick = { order = order.toMutableList().also { it.add(index + 1, it.removeAt(index)) } },
                                    modifier = Modifier.size(48.dp).semantics { contentDescription = "将${section.title}下移" }
                                ) { Icon(Icons.Default.ArrowDownward, contentDescription = null) }
                            }
                            FlowRow(
                                Modifier.fillMaxWidth().padding(start = 56.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                var dragDistance by remember(section) { mutableFloatStateOf(0f) }
                                // TalkBack users reorder with the real up/down buttons above; exposing this drag-only handle as a button would announce an action it cannot activate.
                                Icon(
                                    Icons.Default.DragHandle,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp)
                                        .pointerInput(section, reorderStep) {
                                            detectDragGesturesAfterLongPress(
                                                onDragEnd = { dragDistance = 0f },
                                                onDragCancel = { dragDistance = 0f }
                                            ) { change, dragAmount ->
                                                change.consume()
                                                dragDistance += dragAmount.y
                                                val direction = when {
                                                    dragDistance >= reorderStep -> 1
                                                    dragDistance <= -reorderStep -> -1
                                                    else -> 0
                                                }
                                                if (direction != 0) {
                                                    val currentIndex = order.indexOf(section)
                                                    val targetIndex = (currentIndex + direction).coerceIn(0, order.lastIndex)
                                                    if (targetIndex != currentIndex) {
                                                        order = order.toMutableList().also { it.add(targetIndex, it.removeAt(currentIndex)) }
                                                        dragDistance -= direction * reorderStep
                                                    } else dragDistance = 0f
                                                }
                                            }
                                        }
                                )
                                DesktopWidgetSize.entries.forEach { size ->
                                    FilterChip(
                                        selected = sizes.getValue(section) == size,
                                        onClick = { sizes = sizes + (section to size) },
                                        label = { Text(size.label) }
                                    )
                                }
                            }
                        }
                    }
                }
                Text("应用导航", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("导航会根据窗口宽度显示在底部或侧边。勾选要直接显示的页面并调整顺序；隐藏页面放进‘更多’，仍可随时打开。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                dock.order.forEachIndexed { index, destination ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier.weight(1f).toggleable(
                                value = destination in dock.visible,
                                role = Role.Checkbox,
                                onValueChange = { checked ->
                                    dock = dock.copy(visible = if (checked) dock.visible + destination else dock.visible - destination)
                                }
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(checked = destination in dock.visible, onCheckedChange = null)
                            Text(destination.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(
                            enabled = index > 0,
                            onClick = { dock = dock.copy(order = dock.order.toMutableList().also { it.add(index - 1, it.removeAt(index)) }) },
                            modifier = Modifier.size(48.dp).semantics { contentDescription = "将${destination.title}页面上移" }
                        ) { Icon(Icons.Default.ArrowUpward, contentDescription = null) }
                        IconButton(
                            enabled = index < dock.order.lastIndex,
                            onClick = { dock = dock.copy(order = dock.order.toMutableList().also { it.add(index + 1, it.removeAt(index)) }) },
                            modifier = Modifier.size(48.dp).semantics { contentDescription = "将${destination.title}页面下移" }
                        ) { Icon(Icons.Default.ArrowDownward, contentDescription = null) }
                    }
                }
                Text("今天摘要显示内容", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                TodayOptionRow("角色日程和纪念日", summaryOptions.schedule) { summaryOptions = summaryOptions.copy(schedule = it) }
                TodayOptionRow("未完成待办", summaryOptions.tasks) { summaryOptions = summaryOptions.copy(tasks = it) }
                TodayOptionRow("家园菜地状态", summaryOptions.garden) { summaryOptions = summaryOptions.copy(garden = it) }
                HorizontalDivider()
                Text("预览顺序：${order.filter { it in visible }.joinToString(" · ") { it.title }.ifBlank { "桌面暂时没有卡片" }}", style = MaterialTheme.typography.bodySmall)
                Text("卡片尺寸：${order.filter { it in visible }.joinToString(" · ") { "${it.title} ${sizes.getValue(it).label}" }.ifBlank { "无" }}", style = MaterialTheme.typography.bodySmall)
                Text("页面导航：${dock.order.filter { it in dock.visible }.joinToString(" · ") { it.title }.ifBlank { "更多" }}${if (dock.visible.size < dock.order.size) " · 更多" else ""}", style = MaterialTheme.typography.bodySmall)
                Text("布局只保存在这台设备，不包含在个人加密备份中。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = {
                    order = DesktopLayout.DEFAULT.order
                    visible = DesktopLayout.DEFAULT.visible
                    sizes = DesktopLayout.DEFAULT.sizes
                    dock = DesktopLayout.DEFAULT.dock
                    summaryOptions = TodayOptions()
                }) { Text("恢复默认布局") }
                Spacer(Modifier.height(2.dp))
            }
        },
        confirmButton = { Button(onClick = { onPreview(DesktopLayout(order, visible, sizes, dock), summaryOptions) }) { Text("预览布局") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun TodayOptionRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, role = Role.Checkbox, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun dateLabel(time: Long): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(time))
