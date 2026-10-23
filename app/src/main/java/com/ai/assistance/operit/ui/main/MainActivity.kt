package com.ai.assistance.operit.ui.main

import com.ai.assistance.operit.ui.main.ThemedIcon as Icon

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRailItem
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModelProvider
import com.ai.assistance.operit.data.model.*

class MainActivity : ComponentActivity() {
    private val homeEngineDelegate=lazy {HomeGodotHost(this)}
    internal val homeEngine by homeEngineDelegate
    private lateinit var companion:CompanionViewModel
    private lateinit var authentication:PrivacyAuthenticator
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uiPreferences = getSharedPreferences("companion-ui", Context.MODE_PRIVATE)
        if (uiPreferences.getInt("frontend-revision", 0) < 1) {
            // 0.39.0 is still the user's private debug build; clear the old display overrides so the redesigned UI starts with readable system typography.
            uiPreferences.edit()
                .putString("app-font-family", CompanionFontFamily.SYSTEM.key)
                .putString("app-font-scale", CompanionFontScale.SYSTEM.key)
                .putInt("frontend-revision", 1)
                .apply()
        }
        companion=ViewModelProvider(this)[CompanionViewModel::class.java]
        authentication=PrivacyAuthenticator(this,companion)
        setContent {CompositionLocalProvider(LocalPrivacyAuthentication provides authentication::request){CompanionApp(companion)}}
    }
    override fun onResume(){super.onResume();if(::companion.isInitialized)companion.privacy.resumed(authentication.deviceSecure())}
    override fun onPause(){if(::companion.isInitialized){companion.stopPlay();companion.stopAudioForBackground();companion.privacy.stopped()};super.onPause()}
    override fun onStop(){if(::companion.isInitialized)companion.privacy.stopped();super.onStop()}
    override fun onConfigurationChanged(newConfig:android.content.res.Configuration){super.onConfigurationChanged(newConfig);if(homeEngineDelegate.isInitialized())homeEngine.configuration(newConfig)}
    override fun onDestroy(){if(::authentication.isInitialized)authentication.dispose();if(homeEngineDelegate.isInitialized())homeEngine.destroy();super.onDestroy()}
}

internal data class FileActions(val importRole:()->Unit,val exportRole:()->Unit,val importToolPkg:()->Unit,val importBackup:()->Unit,val exportBackup:()->Unit,val importWorldbook:()->Unit,val exportWorldbook:()->Unit)
internal val LocalFileActions=staticCompositionLocalOf<FileActions>{error("Missing file picker host")}

@Composable
private fun RowScope.DockNavigationBarDestinations(
    visible:List<CompanionDockDestination>,
    hidden:List<CompanionDockDestination>,
    selectedTab:Int,
    iconAppearance:CompanionIconAppearance,
    onSelect:(Int)->Unit,
    onMore:()->Unit
) {
    visible.forEach {destination->
        NavigationBarItem(
            selected=selectedTab==destination.tab,
            onClick={onSelect(destination.tab)},
            label={Text(destination.title)},
            icon={Icon(iconAppearance.navigationIcon(destination.iconIndex),null)}
        )
    }
    if(hidden.isNotEmpty()) {
        NavigationBarItem(
            selected=hidden.any {it.tab==selectedTab},
            onClick=onMore,
            label={Text("更多")},
            icon={Icon(Icons.Default.MoreHoriz,null)}
        )
    }
}

@Composable
private fun ColumnScope.DockNavigationRailDestinations(
    visible:List<CompanionDockDestination>,
    hidden:List<CompanionDockDestination>,
    selectedTab:Int,
    iconAppearance:CompanionIconAppearance,
    onSelect:(Int)->Unit,
    onMore:()->Unit
) {
    visible.forEach {destination->
        NavigationRailItem(
            selected=selectedTab==destination.tab,
            onClick={onSelect(destination.tab)},
            label={Text(destination.title)},
            icon={Icon(iconAppearance.navigationIcon(destination.iconIndex),null)}
        )
    }
    if(hidden.isNotEmpty()) NavigationRailItem(
        selected=hidden.any {it.tab==selectedTab},
        onClick=onMore,
        label={Text("更多")},
        icon={Icon(Icons.Default.MoreHoriz,null)}
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanionApp(vm: CompanionViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    // Keep the privacy barrier subscribed during onStop; normal data collectors may pause.
    val privacy by vm.privacy.state.collectAsState()
    // Register at the app root so dismissing a dialog or rotating cannot drop the picker callback.
    val wallpaperPicker=rememberLauncherForActivityResult(LocalImagePicker()){uri->vm.receiveExternalResult{vm.readWallpaper(uri)}}
    val portraitPicker=rememberLauncherForActivityResult(LocalImagePicker()){uri->vm.receiveExternalResult{vm.readPortrait(uri)}}
    val messageImagePicker=rememberLauncherForActivityResult(LocalImagePicker()){uri->vm.receiveExternalResult{vm.readMessageImage(uri)}}
    val messageFilePicker=rememberLauncherForActivityResult(LocalImagePicker()){uri->vm.receiveExternalResult{vm.readMessageFile(uri)}}
    val readingFilePicker=rememberLauncherForActivityResult(LocalImagePicker()){uri->vm.receiveExternalResult{vm.readReadingFile(uri)}}
    val musicPicker=rememberLauncherForActivityResult(LocalImagePicker()){uri->vm.receiveExternalResult{vm.readMusic(uri)}}
    val stickerPicker=rememberLauncherForActivityResult(LocalImagePicker()){uri->vm.receiveExternalResult{vm.readSticker(uri)}}
    val roleImporter=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->vm.receiveExternalResult{if(uri!=null)vm.readTemplate(uri)}}
    val themeImporter=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->vm.receiveExternalResult{vm.readCompanionTheme(uri)}}
    val roleExporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->vm.receiveExternalResult{vm.writeTemplate(uri)}}
    val toolImporter=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->vm.receiveExternalResult{vm.readToolPkg(uri)}}
    val backupImporter=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->vm.receiveExternalResult{vm.readBackup(uri)}}
    val backupExporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")){uri->vm.receiveExternalResult{vm.writeBackup(uri)}}
    val worldbookImporter=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->vm.receiveExternalResult{vm.readWorldbookTemplate(uri)}}
    val worldbookExporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->vm.receiveExternalResult{vm.writeWorldbookTemplate(uri)}}
    val messageTextExporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MessageExportFormat.TEXT.mimeType)){uri->vm.receiveExternalResult{vm.writeMessageExport(uri)}}
    val messageMarkdownExporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MessageExportFormat.MARKDOWN.mimeType)){uri->vm.receiveExternalResult{vm.writeMessageExport(uri)}}
    val diaryExporter=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")){uri->vm.receiveExternalResult{vm.writeDiaryExport(uri)}}
    val files=FileActions({roleImporter.launch(arrayOf("application/json","text/plain"))},{roleExporter.launch("companion-role.json")},{toolImporter.launch(arrayOf("*/*"))},
        {backupImporter.launch(arrayOf("*/*"))},{backupExporter.launch("companion-${java.time.LocalDate.now()}.cbackup")},
        {worldbookImporter.launch(arrayOf("application/json","text/plain"))},{worldbookExporter.launch("companion-worldbook.json")})
    LaunchedEffect(privacy.canAccess,state.ready,state.working){if(privacy.canAccess&&state.ready&&!state.working)vm.privacyOpened()}
    val context=LocalContext.current
    val activity=context as? ComponentActivity
    val preferences=remember(context){context.getSharedPreferences("companion-ui",Context.MODE_PRIVATE)}
    var savedDock by remember(preferences) { mutableStateOf(CompanionDockConfig.read(preferences)) }
    var previewDock by remember { mutableStateOf<CompanionDockConfig?>(null) }
    var moreDockOpen by rememberSaveable { mutableStateOf(false) }
    DisposableEffect(preferences) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
            if (key == CompanionDockConfig.ORDER_KEY || key == CompanionDockConfig.VISIBLE_KEY) {
                savedDock = CompanionDockConfig.read(sharedPreferences)
            }
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    var appliedPalette by remember { mutableStateOf(CompanionPalette.fromKey(preferences.getString("app-palette",null))) }
    var previewPalette by remember { mutableStateOf<CompanionPalette?>(null) }
    var appliedThemeMode by remember { mutableStateOf(CompanionThemeMode.fromKey(preferences.getString("app-theme-mode",CompanionThemeMode.DAY.key))) }
    var previewThemeMode by remember { mutableStateOf<CompanionThemeMode?>(null) }
    var appliedCustomTheme by remember {
        mutableStateOf(preferences.getString("app-custom-theme",null)?.let { CompanionThemeCodec.decode(it.toByteArray(Charsets.UTF_8)) })
    }
    var appliedCustomThemeEnabled by remember { mutableStateOf(preferences.getBoolean("app-custom-theme-active",false)) }
    var previewCustomTheme by remember { mutableStateOf<CompanionThemeSpec?>(null) }
    var appliedFontScale by remember {
        val savedKey = preferences.getString("app-font-scale", CompanionFontScale.SYSTEM.key)
        mutableStateOf(CompanionFontScale.entries.first { it.key == savedKey })
    }
    var previewFontScale by remember { mutableStateOf<CompanionFontScale?>(null) }
    var appliedFontFamily by remember {
        val savedKey = preferences.getString("app-font-family", CompanionFontFamily.SYSTEM.key)
        mutableStateOf(CompanionFontFamily.entries.first { it.key == savedKey })
    }
    var previewFontFamily by remember { mutableStateOf<CompanionFontFamily?>(null) }
    var appliedIconAppearance by remember {
        val savedKey = preferences.getString("app-icon-appearance", CompanionIconAppearance.FILLED.key)
        mutableStateOf(CompanionIconAppearance.entries.first { it.key == savedKey })
    }
    var previewIconAppearance by remember { mutableStateOf<CompanionIconAppearance?>(null) }
    var appliedCornerStyle by remember {
        val savedKey = preferences.getString("app-corner-style", CompanionCornerStyle.SYSTEM.key)
        mutableStateOf(CompanionCornerStyle.entries.first { it.key == savedKey })
    }
    var previewCornerStyle by remember { mutableStateOf<CompanionCornerStyle?>(null) }
    var activeThemePluginId by remember { mutableStateOf(preferences.getString("app-theme-plugin",null)) }
    LaunchedEffect(activeThemePluginId, appliedIconAppearance) {
        // The older built-in chibi theme stored FILLED; migrate only that still-active preset so its icon treatment matches the corrected theme.
        if (activeThemePluginId == "chibi" && appliedIconAppearance == CompanionIconAppearance.FILLED) {
            appliedIconAppearance = CompanionIconAppearance.TONAL
            preferences.edit().putString("app-icon-appearance", CompanionIconAppearance.TONAL.key).apply()
        }
    }
    var restoreImportPalette by remember { mutableStateOf<CompanionPalette?>(null) }
    var restoreImportCustomTheme by remember { mutableStateOf<CompanionThemeSpec?>(null) }
    var themeEditorOpen by rememberSaveable { mutableStateOf(false) }
    var pendingActivityChatId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingActivityType by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingActivityId by rememberSaveable { mutableStateOf<String?>(null) }
    val importedTheme=state.companionThemeImport
    LaunchedEffect(privacy.canAccess,importedTheme) {
        if(!privacy.canAccess) {
            moreDockOpen=false
            previewPalette=null
            previewThemeMode=null
            previewCustomTheme=null
            previewFontScale=null
            previewFontFamily=null
            previewIconAppearance=null
            previewCornerStyle=null
            restoreImportPalette=null
            restoreImportCustomTheme=null
            themeEditorOpen=false
        } else if(importedTheme!=null) {
            restoreImportPalette=previewPalette
            restoreImportCustomTheme=previewCustomTheme ?: if(previewPalette==null&&appliedCustomThemeEnabled)appliedCustomTheme else null
            previewPalette=null
            previewCustomTheme=importedTheme
            themeEditorOpen=false
        }
    }
    val useCustomTheme=previewCustomTheme!=null || (previewPalette==null&&appliedCustomThemeEnabled)
    val currentThemeMode=previewThemeMode?:appliedThemeMode
    val colors=if(useCustomTheme)checkNotNull(previewCustomTheme?:appliedCustomTheme).colors(currentThemeMode.isDark)
        else (previewPalette?:appliedPalette).colors(currentThemeMode.isDark)
    val systemDensity = LocalDensity.current
    val selectedFontScale = previewFontScale ?: appliedFontScale
    val selectedFontFamily = previewFontFamily ?: appliedFontFamily
    val selectedIconAppearance = previewIconAppearance ?: appliedIconAppearance
    val selectedCornerStyle = previewCornerStyle ?: appliedCornerStyle
    val density = when (val multiplier = selectedFontScale.multiplier) {
        null -> systemDensity
        else -> Density(systemDensity.density, systemDensity.fontScale * multiplier)
    }
    val typography = MaterialTheme.typography.withFontFamily(selectedFontFamily.family)
    val shapes = MaterialTheme.shapes.withCornerStyle(selectedCornerStyle)
    CompositionLocalProvider(
        LocalDensity provides density,
        LocalCompanionIconAppearance provides selectedIconAppearance
    ) {
    MaterialTheme(colorScheme=colors,typography=typography,shapes=shapes) {
        val window=activity?.window
        val edgeToEdge=Build.VERSION.SDK_INT>=35
        val statusBarColor=if(edgeToEdge)Color.Transparent else colors.background
        val navigationBarColor=if(edgeToEdge)Color.Transparent else colors.surface
        val lightStatusIcons=systemBarUsesDarkIcons(colors.background,colors.onBackground)
        val lightNavigationIcons=systemBarUsesDarkIcons(colors.surface,colors.onSurface)
        DisposableEffect(window,statusBarColor,navigationBarColor,lightStatusIcons,lightNavigationIcons,edgeToEdge) {
            window?.let {
                // The XML window theme is always light; keep native system bars aligned with live Compose theme changes.
                // Key this effect by actual bar values so streamed message recompositions do not rewrite system window state.
                it.statusBarColor=statusBarColor.toArgb()
                it.navigationBarColor=navigationBarColor.toArgb()
                val controller=WindowInsetsControllerCompat(it,it.decorView)
                controller.isAppearanceLightStatusBars=lightStatusIcons
                controller.isAppearanceLightNavigationBars=lightNavigationIcons
                if(edgeToEdge) {
                    it.isStatusBarContrastEnforced=false
                    it.isNavigationBarContrastEnforced=false
                }
            }
            onDispose {}
        }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var roleManagerOpen by rememberSaveable { mutableStateOf(false) }
    var roleManagerEditId by rememberSaveable { mutableStateOf<String?>(null) }
        val activeDock = previewDock ?: savedDock
        val visibleDock = activeDock.order.filter { it in activeDock.visible }
        val hiddenDock = activeDock.order.filter { it !in activeDock.visible }
        var openConnections by remember {mutableStateOf(false)}
        CompositionLocalProvider(LocalFileActions provides files,LocalMusicFilePick provides {scope,track,kind->vm.beginMusicPick(scope,track,kind);musicPicker.launch(arrayOf("$kind/*"))},LocalCompanionChatPalette provides ((if(previewPalette==null)builtinThemePlugins.firstOrNull {it.id==activeThemePluginId}?.chatPalette else null)?:when(previewPalette?:appliedPalette){CompanionPalette.GARDEN->"green";CompanionPalette.PEACH->"rose";CompanionPalette.DUSK->"blue";CompanionPalette.PAPER->"paper";CompanionPalette.SEA->"sea";CompanionPalette.MIDNIGHT->"midnight";CompanionPalette.FOREST->"forest";CompanionPalette.SAKURA->"sakura";CompanionPalette.OBSERVATORY->"observatory"}),LocalCompanionThemeArtwork provides (if(previewPalette==null&&previewCustomTheme==null&&!appliedCustomThemeEnabled)builtinThemePlugins.firstOrNull {it.id==activeThemePluginId}?.artwork else null)) {
        val snackbar=remember { SnackbarHostState() }
        LaunchedEffect(vm) { vm.events.collect { snackbar.showSnackbar(it) } }
        LaunchedEffect(vm) {vm.failures.collect {failure->
            val result=snackbar.showSnackbar(failure.message,actionLabel=if(failure.route==RecoveryRoute.CONNECTIONS)"打开设置"else"查看帮助",withDismissAction=true)
            if(result==SnackbarResult.ActionPerformed) {
                tab=3
                if(failure.route==RecoveryRoute.CONNECTIONS)openConnections=true else vm.openGuide()
            }
        }}
        val openRecovery:(String)->Unit={code->
            vm.previewDiagnostic(code);tab=3
            if(SupportDiagnostics.route(code)==RecoveryRoute.CONNECTIONS)openConnections=true else vm.openGuide()
        }
        BackHandler(privacy.canAccess&&!state.guideOpen&&tab!=0) { tab=0 }
        BackHandler(privacy.canAccess&&!state.guideOpen&&tab==0&&state.chatId!=null) { vm.closeChat() }
        BackHandler(privacy.canAccess&&!state.guideOpen&&tab==0&&state.group!=null) { vm.closeGroup() }
        val showNavigation=privacy.canAccess&&!state.guideOpen&&state.ready&&!roleManagerOpen&&!(tab==0&&(state.chatId!=null||state.group!=null))
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Use the current window width so split-screen and landscape windows get a rail instead of losing vertical space.
            val useNavigationRail=maxWidth>=600.dp
            Row(Modifier.fillMaxSize()) {
                if(showNavigation&&useNavigationRail) NavigationRail {
                    DockNavigationRailDestinations(visibleDock,hiddenDock,tab,selectedIconAppearance,{tab=it},{moreDockOpen=true})
                }
                Scaffold(
                    modifier=Modifier.weight(1f).fillMaxHeight(),
                    snackbarHost={SnackbarHost(snackbar)},
                    bottomBar={
                        if(showNavigation&&!useNavigationRail) NavigationBar {
                            DockNavigationBarDestinations(visibleDock,hiddenDock,tab,selectedIconAppearance,{tab=it},{moreDockOpen=true})
                        }
                    }
                ) {padding->
                    // Keeping the native view here prevents a page change from terminating Godot's GL thread.
                    Box(Modifier.fillMaxSize()) {
                    HomeGodotLayer(Modifier.fillMaxSize())
                    Column(Modifier.fillMaxSize().then(if(privacy.canAccess&&!state.guideOpen)Modifier.padding(padding).consumeWindowInsets(padding).imePadding()else Modifier)) {
                        if(privacy.canAccess&&!state.guideOpen&&state.guidePending)Surface(color=MaterialTheme.colorScheme.primaryContainer,modifier=Modifier.fillMaxWidth().clickable {vm.openGuide()}) {
                            Row(Modifier.padding(horizontal=18.dp,vertical=10.dp),verticalAlignment=Alignment.CenterVertically) {Icon(Icons.Default.School,null);Spacer(Modifier.width(10.dp));Text("新手引导进行中 · 点击继续",Modifier.weight(1f),fontWeight=FontWeight.Medium);Icon(Icons.Default.ChevronRight,null)}
                        }
                        if(!privacy.canAccess) {
                            PrivacyLockedPage(privacy)
                        } else if(state.guideOpen) {
                            GuidePage(state,vm,onConnections={vm.closeGuide();tab=3;openConnections=true},onRoles={vm.closeGuide();tab=1},onChat={
                                vm.closeGuide();tab=0;state.inbox.firstOrNull()?.let {vm.selectChat(RoleAccessScope(it.roleId,it.spaceId),it.chatId)}
                            })
                        } else if(state.fatal) {
                            Text("无法打开加密数据",style=MaterialTheme.typography.headlineSmall,modifier=Modifier.padding(24.dp))
                            Text(errorText("STORAGE"),modifier=Modifier.padding(horizontal=24.dp))
                        } else if(!state.ready) {
                            Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
                        } else if(roleManagerOpen) {
                            RolePage(state,vm,{scope,kind->vm.beginPortraitPick(scope,kind);portraitPicker.launch(arrayOf("image/jpeg","image/png","image/webp"))},onBack={roleManagerOpen=false;roleManagerEditId=null},initialEditRoleId=roleManagerEditId)
                        } else when(tab) {
                            0 -> MessengerPage(state,vm,onSettings={tab=3},onRoles={roleManagerEditId=null;roleManagerOpen=true},onEditRole={roleManagerEditId=it;roleManagerOpen=true},onOpenConnections={openConnections=true;tab=3},onErrorHelp=openRecovery,
                                onMessageImagePick={scope,chatId->vm.beginMessageImagePick(scope,chatId);messageImagePicker.launch(arrayOf("image/jpeg","image/png","image/webp"))},
                                onMessageFilePick={scope,chatId->vm.beginMessageFilePick(scope,chatId);messageFilePicker.launch(arrayOf("text/plain","text/markdown","text/x-markdown","application/octet-stream"))},
                                onReadingFilePick={scope->vm.beginReadingFilePick(scope);readingFilePicker.launch(arrayOf("text/plain","text/markdown","text/x-markdown","application/octet-stream"))},
                                onStickerPick={scope->vm.beginStickerPick(scope);stickerPicker.launch(arrayOf("image/jpeg","image/png","image/webp","image/gif"))},
                                onWallpaperPick={scope->vm.beginWallpaperPick(scope);wallpaperPicker.launch(arrayOf("image/jpeg","image/png","image/webp"))},onPortraitPick={scope,kind->vm.beginPortraitPick(scope,kind);portraitPicker.launch(arrayOf("image/jpeg","image/png","image/webp"))},
                                openActivityChatId=pendingActivityChatId,openActivityType=pendingActivityType,openActivityId=pendingActivityId,
                                onActivityOpened={pendingActivityChatId=null;pendingActivityType=null;pendingActivityId=null})
                            1 -> HomePage(null,state,vm,onClose={},isHomeTab=true)
                            2 -> SchedulePage(state,vm,onOpenChats={tab=0},onManageRoles={roleManagerOpen=true})
                            3 -> SettingsPage(
                                state,vm,openConnections,{openConnections=false},activeThemePluginId,
                                dock=activeDock,
                                onSaveDock={updated->
                                    CompanionDockConfig.putInto(preferences.edit(),updated).apply()
                                    savedDock=updated
                                    previewDock=null
                                },
                                onOpenTheme={themeEditorOpen=true},
                                onApplyTheme={plugin->
                                    preferences.edit().putString("app-palette",plugin.palette.key).putBoolean("app-custom-theme-active",false)
                                        .putString("app-theme-mode",plugin.mode.key)
                                        .putString("app-font-scale",plugin.scale.key)
                                        .putString("app-font-family",plugin.font.key).putString("app-icon-appearance",plugin.icons.key)
                                        .putString("app-corner-style",plugin.corners.key).putString("app-theme-plugin",plugin.id).apply()
                                    appliedPalette=plugin.palette;appliedThemeMode=plugin.mode;appliedCustomThemeEnabled=false;appliedCustomTheme=null;appliedFontScale=plugin.scale
                                    appliedFontFamily=plugin.font;appliedIconAppearance=plugin.icons;appliedCornerStyle=plugin.corners
                                    previewPalette=null;previewThemeMode=null;previewCustomTheme=null;previewFontFamily=null;previewIconAppearance=null;previewCornerStyle=null
                                    activeThemePluginId=plugin.id
                                }
                            )
                        }
                    }
                    }
                }
            }
        }
        if (privacy.canAccess&&!state.guideOpen&&moreDockOpen&&hiddenDock.isNotEmpty()) {
            ModalBottomSheet(onDismissRequest = { moreDockOpen = false }) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("更多页面", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 12.dp, bottom = 8.dp))
                    hiddenDock.forEach { destination ->
                        NavigationDrawerItem(
                            label = { Text(destination.title) },
                            selected = tab == destination.tab,
                            onClick = { tab = destination.tab; moreDockOpen = false },
                            icon = { Icon(selectedIconAppearance.navigationIcon(destination.iconIndex), null) },
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
        if(privacy.canAccess&&!state.guideOpen) {
        WallpaperPreviewDialog(state,vm)
        PortraitPreviewDialog(state,vm)
        ToolApprovalDialog(state,vm)
        ToolPkgPreviewDialog(state,vm)
        RoleTemplateDialogs(state,vm)
        BackupDialogs(state,vm)
        WorldbookFileDialog(state,vm)
        MaterialPreviewDialog(state,vm)
        ReadingImportPreviewDialog(state,vm)
        MusicImportDialog(state,vm)
        MessageExportPreviewDialog(state,vm){format,baseName->vm.approveMessageExport(format,baseName){fileName->
            when(format){MessageExportFormat.TEXT->messageTextExporter.launch(fileName);MessageExportFormat.MARKDOWN->messageMarkdownExporter.launch(fileName)}
        }}
        StickerPreviewDialog(state,vm)
        MemoryExtractionDialog(state,vm)
        DiaryGenerationDialog(state,vm)
        DiaryExportPreviewDialog(state,vm){baseName->vm.approveDiaryExport(baseName){fileName->diaryExporter.launch(fileName)}}
        WeatherLookupDialog(state,vm)
        if(themeEditorOpen) CompanionThemeEditor(
            selected=previewPalette?:appliedPalette,
            selectedCustom=previewPalette==null&&(previewCustomTheme!=null||appliedCustomThemeEnabled),
            customThemeName=(previewCustomTheme?:appliedCustomTheme)?.name,
            selectedFontScale=selectedFontScale,
            selectedFontFamily=selectedFontFamily,
            selectedIconAppearance=selectedIconAppearance,
            selectedCornerStyle=selectedCornerStyle,
            selectedMode=currentThemeMode,
            onSelect={previewPalette=it;previewCustomTheme=null},
            onSelectCustom={previewPalette=null;previewCustomTheme=previewCustomTheme?:checkNotNull(appliedCustomTheme)},
            onSelectFontScale={previewFontScale=it},
            onSelectFontFamily={previewFontFamily=it},
            onSelectIconAppearance={previewIconAppearance=it},
            onSelectCornerStyle={previewCornerStyle=it},
            onSelectMode={previewThemeMode=it},
            onImportTheme={themeImporter.launch(arrayOf("application/json","text/plain"))},
            onApply={
                val custom=previewCustomTheme?:if(previewPalette==null&&appliedCustomThemeEnabled)appliedCustomTheme else null
                val editor = preferences.edit()
                if(custom!=null) {
                    editor.putString("app-custom-theme",CompanionThemeCodec.encode(custom)).putBoolean("app-custom-theme-active",true)
                    appliedCustomTheme=custom;appliedCustomThemeEnabled=true
                } else {
                    val chosen=previewPalette?:appliedPalette
                    editor.putString("app-palette",chosen.key).putBoolean("app-custom-theme-active",false)
                    appliedPalette=chosen;appliedCustomThemeEnabled=false
                }
                appliedFontScale=selectedFontScale
                appliedFontFamily=selectedFontFamily
                appliedIconAppearance=selectedIconAppearance
                appliedCornerStyle=selectedCornerStyle
                appliedThemeMode=currentThemeMode
                editor.putString("app-font-scale",appliedFontScale.key)
                    .putString("app-font-family",appliedFontFamily.key)
                    .putString("app-icon-appearance",appliedIconAppearance.key)
                    .putString("app-corner-style",appliedCornerStyle.key)
                    .putString("app-theme-mode",currentThemeMode.key)
                    .remove("app-theme-plugin")
                    .apply()
                activeThemePluginId=null
                previewFontScale=null
                previewFontFamily=null
                previewIconAppearance=null
                previewCornerStyle=null
                previewThemeMode=null
                previewPalette=null;previewCustomTheme=null;themeEditorOpen=false
            },
            onDismiss={previewFontScale=null;previewFontFamily=null;previewIconAppearance=null;previewCornerStyle=null;previewThemeMode=null;previewPalette=null;previewCustomTheme=null;themeEditorOpen=false}
        )
        importedTheme?.let { theme ->
            CompanionThemeImportDialog(
                theme=theme,
                enabled=!state.working,
                onApply={
                    val encoded=CompanionThemeCodec.encode(theme)
                    preferences.edit().putString("app-custom-theme",encoded)
                        .putBoolean("app-custom-theme-active",true)
                        .putString("app-font-scale",selectedFontScale.key)
                        .putString("app-font-family",selectedFontFamily.key)
                        .putString("app-icon-appearance",selectedIconAppearance.key)
                        .putString("app-corner-style",selectedCornerStyle.key)
                        .putString("app-theme-mode",currentThemeMode.key)
                        .remove("app-theme-plugin")
                        .apply()
                    appliedCustomTheme=theme;appliedCustomThemeEnabled=true
                    appliedFontScale=selectedFontScale
                    appliedFontFamily=selectedFontFamily
                    appliedIconAppearance=selectedIconAppearance
                    appliedCornerStyle=selectedCornerStyle
                    appliedThemeMode=currentThemeMode
                    activeThemePluginId=null
                    previewFontScale=null
                    previewFontFamily=null
                    previewIconAppearance=null
                    previewCornerStyle=null
                    previewThemeMode=null
                    previewPalette=null;previewCustomTheme=null;themeEditorOpen=false
                    vm.dismissCompanionThemeImport()
                },
                onDismiss={
                    previewPalette=restoreImportPalette
                    previewCustomTheme=restoreImportCustomTheme
                    themeEditorOpen=true
                    vm.dismissCompanionThemeImport()
                }
            )
        }
        }
        }
    }
    }
}

@Composable private fun PageTitle(title: String, caption: String) {
    Column(Modifier.padding(start=24.dp,end=24.dp,top=22.dp,bottom=16.dp)) {
        Text(title,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.SemiBold)
        Text(caption,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,modifier=Modifier.padding(top=6.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun RoleEditorPage(state: CompanionState,vm: CompanionViewModel,initialId: String?,onPortraitPick:(RoleAccessScope,String)->Unit,onBack:()->Unit) {
    var selected by remember { mutableStateOf(initialId) }
    val role=state.roles.firstOrNull{it.id==selected}
    var name by remember(selected,role?.version){mutableStateOf(role?.name ?: "")}
    var prompt by remember(selected,role?.version){mutableStateOf(role?.prompt ?: "")}
    var opening by remember(selected,role?.version){mutableStateOf(role?.openingMessage ?: "")}
    var configId by remember(selected,role?.version){mutableStateOf(role?.defaultConfigId)}
    var replyStyle by remember(selected,role?.version){mutableStateOf(role?.replyStyle ?: "short")}
    var deleting by remember { mutableStateOf<RoleDeleteRequest?>(null) }
    var portraits by remember { mutableStateOf(false) }
    var tools by remember {mutableStateOf(false)}
    var persona by remember {mutableStateOf(false)}
    var worldbook by remember {mutableStateOf(false)}
    var relationships by remember {mutableStateOf(false)}
    var memories by remember {mutableStateOf(false)}
    var diaries by remember {mutableStateOf(false)}
    var environment by remember {mutableStateOf(false)}
    var planner by remember {mutableStateOf(false)}
    var proactive by remember {mutableStateOf(false)}
    var scheduled by remember {mutableStateOf(false)}
    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal=24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        if(role!=null) Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
            PortraitAvatar(role.name,role.scope(),vm,size=56.dp)
            Column(Modifier.weight(1f)) { Text(role.name,style=MaterialTheme.typography.titleLarge);Text(if(role.isRaw())"原味模型"else"AI 伴侣",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant) }
            IconButton(onClick={portraits=true}){Icon(Icons.Default.AddAPhoto,"设置聊天头像")}
        }
        OutlinedTextField(name,{if(it.length<=40)name=it},Modifier.fillMaxWidth(),label={Text("角色名称")},singleLine=true)
        OutlinedTextField(prompt,{if(it.length<=8000)prompt=it},Modifier.fillMaxWidth(),label={Text("人设与交流方式")},minLines=5,maxLines=12,
            supportingText={Text("保存后从下一次请求生效。")})
        OutlinedTextField(opening,{if(it.length<=8000)opening=it},Modifier.fillMaxWidth(),label={Text("开场白（可选）")},minLines=2,maxLines=6,
            supportingText={Text("仅用于之后新建的对话，已有对话保持原样。")})
        ReplyStyleSelector(replyStyle,enabled=!state.working&&role?.isRaw()!=true){replyStyle=it}
        if(role?.isRaw()==true)Text("原味模型不会加入应用的回复风格指令；已保存的选择会在切回角色模式后继续使用。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text("默认聊天模型",style=MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilterChip(selected=configId==null,onClick={configId=null},label={Text("新建对话时选择")})
            state.configs.forEach { config -> FilterChip(selected=configId==config.id,onClick={configId=config.id},label={Text(config.name)}) }
        }
        Button(enabled=!state.working&&(selected==null||role!=null)&&name.isNotBlank()&&prompt.isNotBlank(),onClick={
            // Return only after the transaction saves, so the refreshed role list is the visible result of create/edit.
            vm.saveRole(role?.scope(),name,prompt,opening,configId,replyStyle){selected=it.id;onBack()}
        }){Text(if(state.working)"正在处理…"else"保存角色")}
        if(role!=null) FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick={persona=true}){Text("用户面具")}
            OutlinedButton(onClick={worldbook=true}){Text("世界书")}
            OutlinedButton(onClick={relationships=true}){Text("关系与原味模型")}
            OutlinedButton(onClick={memories=true}){Text("长期记忆")}
            OutlinedButton(onClick={diaries=true}){Text("日记与心事簿")}
            OutlinedButton(onClick={environment=true}){Text("时间与天气")}
            OutlinedButton(onClick={planner=true}){Text("日程与记录")}
            OutlinedButton(onClick={proactive=true}){Text("主动消息")}
            OutlinedButton(onClick={scheduled=true}){Text("定时消息")}
            OutlinedButton(onClick={tools=true}){Text("工具权限")}
            OutlinedButton(enabled=!state.working,onClick={vm.copyRole(role.scope()){selected=it.id}}){Text("复制角色")}
            OutlinedButton(enabled=!state.working,onClick={vm.previewExport(role.scope())}){Text("导出已保存模板")}
            OutlinedButton(enabled=!state.working,onClick={vm.roleChatCount(role.scope()){deleting=RoleDeleteRequest(role,it)}}){Text("删除角色",color=MaterialTheme.colorScheme.error)}
        }
        Text("复制角色会保留对话模式，但不会复制用户面具、关系、记忆、日记、天气城市、日程、经期、世界书或聊天；公开角色模板只分享名称、人设与开场白。个人加密备份会保存完整设定。",
            style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
    }
    if(portraits&&role!=null)PortraitDialog(state,vm,role,onPick={kind->onPortraitPick(role.scope(),kind);portraits=false}){portraits=false}
    if(tools&&role!=null)RoleToolsDialog(role,state,vm){tools=false}
    if(persona&&role!=null)PersonaEditorDialog(role,state,vm){persona=false}
    if(worldbook&&role!=null)WorldbookDialog(role,state,vm){worldbook=false}
    if(relationships&&role!=null)RelationshipDialog(role,state,vm){relationships=false}
    if(memories&&role!=null)MemoryDialog(role,state,vm){memories=false}
    if(diaries&&role!=null)DiaryDialog(role,state,vm){diaries=false}
    if(environment&&role!=null)EnvironmentDialog(role,state,vm){environment=false}
    if(planner&&role!=null)PlannerDialog(role,state,vm){planner=false}
    if(proactive&&role!=null)ProactiveDialog(role,state,vm){proactive=false}
    if(scheduled&&role!=null)ScheduledMessageDialog(role,state.chats.firstOrNull {it.roleId==role.id}?.id,vm){scheduled=false}
    deleting?.let { request ->
        RoleDeleteDialog(request,state.working,onDismiss={deleting=null},onConfirm={
            vm.deleteRole(request.role.scope()) {
                deleting=null
                if(selected==request.role.id){selected=null;onBack()}
            }
        })
    }
}

@Composable private fun RoleTemplateDialogs(state:CompanionState,vm:CompanionViewModel) {
    val files=LocalFileActions.current
    state.importPreview?.let { preview ->
        AlertDialog(onDismissRequest={vm.dismissImport()},title={Text("预览角色模板")},
            text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                if(preview.ignoredFields>0) Text("已忽略 "+preview.ignoredFields+" 个额外字段；它们不会导入或执行。")
                TemplateContent(preview.template)
                Text("确认后创建独立的新角色，不覆盖同名角色。",style=MaterialTheme.typography.bodySmall)
            }},
            confirmButton={TextButton(enabled=!state.working,onClick={vm.importTemplate{vm.selectRole(it.scope())}}){Text("创建新角色")}},
            dismissButton={TextButton(onClick={vm.dismissImport()}){Text("取消")}})
    }
    (state.characterCardPreview as? CharacterCardResult.Success)?.let { result->
        val card=result.card
        var configId by remember(card){mutableStateOf(state.configs.firstOrNull()?.id)}
        AlertDialog(onDismissRequest={vm.dismissCharacterCardPreview()},title={Text("导入角色卡")},
            text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text(card.name,style=MaterialTheme.typography.titleMedium)
                Text("人设 ${card.prompt.length} 字 · 开场白 ${card.openingMessage.length} 字",style=MaterialTheme.typography.bodySmall)
                if(card.lore.isNotEmpty())Text("世界书 ${card.lore.size} 条，导入后默认关闭。",style=MaterialTheme.typography.bodySmall)
                if(card.alternateGreetings>0)Text("已忽略 ${card.alternateGreetings} 条备选开场白。",style=MaterialTheme.typography.bodySmall)
                if(card.ignoredFields>0)Text("已忽略 ${card.ignoredFields} 个未知字段。",style=MaterialTheme.typography.bodySmall)
                Text("默认聊天模型",style=MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected=configId==null,onClick={configId=null},label={Text("新建对话时选择")})
                    state.configs.forEach {config->FilterChip(selected=configId==config.id,onClick={configId=config.id},label={Text(config.name)})}
                }
            }},
            confirmButton={TextButton(enabled=!state.working,onClick={vm.importCharacterCard(configId){vm.selectRole(it.scope())}}){Text("创建角色")}},
            dismissButton={TextButton(onClick={vm.dismissCharacterCardPreview()}){Text("取消")}})
    }
    state.exportPreview?.let { template ->
        AlertDialog(onDismissRequest={vm.dismissExport()},title={Text("确认要分享的内容")},
            text={Column(Modifier.heightIn(max=440.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("请检查人设中是否包含你自行填写的私人信息。文件只包含以下内容和格式版本。")
                TemplateContent(template)
            }},
            confirmButton={TextButton(onClick={vm.approveExport();files.exportRole()}){Text("选择保存位置")}},
            dismissButton={TextButton(onClick={vm.dismissExport()}){Text("取消")}})
    }
}

@Composable private fun TemplateContent(template: RoleTemplate) {
    Text(template.name,style=MaterialTheme.typography.titleMedium)
    Text("人设",style=MaterialTheme.typography.labelLarge)
    Text(template.prompt)
    Text("开场白",style=MaterialTheme.typography.labelLarge)
    Text(template.openingMessage.ifEmpty { "（未设置）" })
}
