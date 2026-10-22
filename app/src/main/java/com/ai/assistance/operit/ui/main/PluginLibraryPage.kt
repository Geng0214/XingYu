package com.ai.assistance.operit.ui.main

import com.ai.assistance.operit.R
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal data class BuiltinThemePlugin(
    val id:String,
    val name:String,
    val tagline:String,
    val description:String,
    val palette:CompanionPalette,
    val scale:CompanionFontScale,
    val font:CompanionFontFamily,
    val corners:CompanionCornerStyle,
    val icons:CompanionIconAppearance,
    val chatPalette:String,
    val mode:CompanionThemeMode,
    val artwork:Int?
)

internal val builtinThemePlugins=listOf(
    BuiltinThemePlugin("minimal","雾白极简","留白、细线与灰绿","安静清爽，适合长时间阅读和聊天。",CompanionPalette.GARDEN,CompanionFontScale.SYSTEM,CompanionFontFamily.SYSTEM,CompanionCornerStyle.COMPACT,CompanionIconAppearance.OUTLINED,"green",CompanionThemeMode.DAY,null),
    BuiltinThemePlugin("anime","星夜物语","蓝紫夜色、实心图标与衬线文字","柔和的暮色配色，实心图标和衬线文字营造沉浸阅读感。",CompanionPalette.DUSK,CompanionFontScale.SYSTEM,CompanionFontFamily.SERIF,CompanionCornerStyle.SOFT,CompanionIconAppearance.FILLED,"blue",CompanionThemeMode.DAY,null),
    // The previous preview used the default filled icon style, making this theme look like a palette-only change.
    BuiltinThemePlugin("chibi","团子日常","暖桃、圆角与柔彩图标","柔彩圆底图标配合暖桃色和大圆角，让页面入口更轻快。",CompanionPalette.PEACH,CompanionFontScale.SYSTEM,CompanionFontFamily.SYSTEM,CompanionCornerStyle.SOFT,CompanionIconAppearance.TONAL,"rose",CompanionThemeMode.DAY,null),
    BuiltinThemePlugin("letter","纸间暖信","暖纸、陶土与衬线文字","纸张纹理点缀聊天页眉；柔和暖色与线框图标适合静静写信。",CompanionPalette.PAPER,CompanionFontScale.SYSTEM,CompanionFontFamily.SERIF,CompanionCornerStyle.COMPACT,CompanionIconAppearance.OUTLINED,"paper",CompanionThemeMode.DAY,R.drawable.theme_letter_paper),
    BuiltinThemePlugin("coast","海盐晴窗","海蓝、青绿与轻盈留白","海岸水彩点缀聊天页眉；清透的卡片和柔彩图标保持阅读轻松。",CompanionPalette.SEA,CompanionFontScale.SYSTEM,CompanionFontFamily.SYSTEM,CompanionCornerStyle.SOFT,CompanionIconAppearance.TONAL,"sea",CompanionThemeMode.DAY,R.drawable.theme_sea_glass),
    BuiltinThemePlugin("moon-radio","月色电台","深蓝、极光与柔和微光","夜色图案点缀聊天页眉；深色卡片与浅色文字适合夜间聊天。",CompanionPalette.MIDNIGHT,CompanionFontScale.SYSTEM,CompanionFontFamily.SYSTEM,CompanionCornerStyle.SOFT,CompanionIconAppearance.FILLED,"midnight",CompanionThemeMode.NIGHT,R.drawable.theme_moon_radio),
    BuiltinThemePlugin("forest","林间小憩","苔绿、枝叶与细线图标","叶影页眉、清绿气泡与紧凑圆角，让你们的小家像住在树林旁。",CompanionPalette.FOREST,CompanionFontScale.SYSTEM,CompanionFontFamily.SYSTEM,CompanionCornerStyle.COMPACT,CompanionIconAppearance.OUTLINED,"forest",CompanionThemeMode.DAY,R.drawable.theme_forest_canopy),
    BuiltinThemePlugin("sakura","樱雨来信","樱粉、花影与柔彩图标","樱枝页眉搭配衬线文字、花瓣色气泡和大圆角，适合慢慢写下一天。",CompanionPalette.SAKURA,CompanionFontScale.SYSTEM,CompanionFontFamily.SERIF,CompanionCornerStyle.SOFT,CompanionIconAppearance.TONAL,"sakura",CompanionThemeMode.DAY,R.drawable.theme_sakura_branch),
    BuiltinThemePlugin("observatory","观星手记","黄铜星轨、等宽文字与细线图标","星轨页眉、蓝黑夜色和黄铜色气泡，像一本留给彼此的观星手记。",CompanionPalette.OBSERVATORY,CompanionFontScale.SYSTEM,CompanionFontFamily.MONOSPACE,CompanionCornerStyle.COMPACT,CompanionIconAppearance.OUTLINED,"observatory",CompanionThemeMode.NIGHT,R.drawable.theme_observatory_orbit)
)

internal val LocalCompanionThemeArtwork = compositionLocalOf<Int?> { null }

@Composable
internal fun ThemeHeaderArtwork(artwork: Int, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Box(modifier.background(MaterialTheme.colorScheme.background)) {
        Image(
            painter = painterResource(artwork),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = if (dark) 0.6f else 0.8f,
            modifier = Modifier.fillMaxSize()
        )
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(
            MaterialTheme.colorScheme.background.copy(alpha = 0.18f),
            MaterialTheme.colorScheme.background.copy(alpha = if (dark) 0.48f else 0.56f)
        ))))
    }
}

@Composable
private fun BuiltinThemePreview(plugin: BuiltinThemePlugin) {
    // Draw the real app controls with the selected theme tokens so the preview cannot drift like the old concept artwork.
    MaterialTheme(
        colorScheme = plugin.palette.colors(dark = plugin.mode.isDark),
        typography = MaterialTheme.typography.withFontFamily(plugin.font.family),
        shapes = MaterialTheme.shapes.withCornerStyle(plugin.corners)
    ) {
        CompositionLocalProvider(LocalCompanionIconAppearance provides plugin.icons) {
            Surface(
                modifier = Modifier.fillMaxWidth().height(218.dp+88.dp*(LocalDensity.current.fontScale-1f).coerceAtLeast(0f)),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.background
            ) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().height(if (plugin.artwork == null) 56.dp else 68.dp)) {
                        plugin.artwork?.let { ThemeHeaderArtwork(it, Modifier.fillMaxSize()) }
                        Text("聊天", Modifier.align(Alignment.Center), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Row(Modifier.align(Alignment.CenterEnd).padding(end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            // Keep the preview header controls in the same positions as the chat list.
                            listOf(Icons.Default.Search, Icons.Default.Add).forEach { icon ->
                                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                                    ThemedIcon(icon, null, Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(9.dp))
                    Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        RoleAvatar("初见","theme-preview",42.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("初见", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            Text("你好呀，今天过得怎么样？", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        Text("12:30", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                        Row(Modifier.fillMaxWidth().height(48.dp+14.dp*(LocalDensity.current.fontScale-1f).coerceAtLeast(0f)), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
                            PreviewNavigationItem(Icons.AutoMirrored.Filled.Chat, "聊天", selected = true)
                            PreviewNavigationItem(Icons.Default.Home, "家园")
                            PreviewNavigationItem(Icons.Default.Event, "日程")
                            PreviewNavigationItem(Icons.Default.Settings, "设置")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewNavigationItem(imageVector: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean = false) {
    Column(Modifier.height(44.dp+14.dp*(LocalDensity.current.fontScale-1f).coerceAtLeast(0f)), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) {
            ThemedIcon(
                imageVector = imageVector,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun PluginLibraryPage(state:CompanionState,vm:CompanionViewModel,activeThemeId:String?,onOpenTheme:()->Unit,onApplyTheme:(BuiltinThemePlugin)->Unit,showPageHeader:Boolean=true) {
    var category by rememberSaveable { mutableStateOf("themes") }
    if(category=="tools") {
        ExtensionPage(state,vm,onBack={category="themes"})
        return
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start=22.dp,end=22.dp,top=if(showPageHeader)18.dp else 8.dp,bottom=12.dp)) {
            if(showPageHeader) {
                Text("插件",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
                Text("管理应用外观和角色可用的工具扩展。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(Modifier.padding(top=if(showPageHeader)14.dp else 0.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                FilterChip(category=="themes",{category="themes"},label={Text("应用主题")},leadingIcon={androidx.compose.material3.Icon(Icons.Default.Palette,null)})
                FilterChip(category=="tools",{category="tools"},label={Text("工具扩展")},leadingIcon={androidx.compose.material3.Icon(Icons.Default.Extension,null)})
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Card(shape=RoundedCornerShape(22.dp),modifier=Modifier.fillMaxWidth()) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                // The edit action gets its own row at large text sizes; three columns squeezed the description into broken fragments.
                if(maxWidth<480.dp&&LocalDensity.current.fontScale>=1.3f) {
                    Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                            ThemedIcon(Icons.Default.Palette,null,Modifier.size(30.dp))
                            Text("自定义应用主题",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,modifier=Modifier.weight(1f))
                        }
                        Text("调整全局配色、字体、图标和圆角。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick=onOpenTheme,modifier=Modifier.align(Alignment.End)) { Text("编辑") }
                    }
                } else {
                Row(Modifier.fillMaxWidth().padding(18.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                    androidx.compose.material3.Icon(Icons.Default.Palette,null,tint=MaterialTheme.colorScheme.primary,modifier=Modifier.size(30.dp))
                    Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Text("自定义应用主题",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                        Text("调整全局配色、字体、图标和圆角。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick=onOpenTheme) { Text("编辑") }
                }
                }
                }
            }
            Text("内置主题",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
            Text("选择“应用”后，主界面、导航、卡片和聊天气泡会一起切换。",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            builtinThemePlugins.forEach {plugin->
                Card(shape=RoundedCornerShape(22.dp),modifier=Modifier.fillMaxWidth()) {
                    Column {
                        Box(Modifier.fillMaxWidth().padding(10.dp)) { BuiltinThemePreview(plugin) }
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            Row {
                                Column(Modifier.weight(1f)) {
                                    Text(plugin.name,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
                                    Text(plugin.tagline,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.primary)
                                }
                                if(activeThemeId==plugin.id)androidx.compose.material3.Icon(Icons.Default.CheckCircle,"已应用",Modifier.size(22.dp),tint=MaterialTheme.colorScheme.primary)
                            }
                            Text(plugin.description,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                                Button(enabled=activeThemeId!=plugin.id,onClick={onApplyTheme(plugin)},modifier=Modifier.semantics {
                                    contentDescription=if(activeThemeId==plugin.id)"${plugin.name}已应用"else"应用${plugin.name}"
                                }) {
                                    Text(if(activeThemeId==plugin.id)"已应用" else "应用主题")
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}
