package com.ai.assistance.operit.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Reserve fixed spacing plus font-scaled room for the title and actions outside the scrolling body.
internal fun dialogContentMaxHeightForWindow(windowHeight:Dp,preferred:Dp,fontScale:Float=1f):Dp {
    val reservedHeight=96.dp+96.dp*fontScale.coerceAtLeast(1f)
    return minOf(preferred,(windowHeight-reservedHeight).coerceAtLeast(0.dp))
}

@Composable
internal fun adaptiveDialogContentMaxHeight(preferred:Dp):Dp =
    dialogContentMaxHeightForWindow(LocalConfiguration.current.screenHeightDp.dp,preferred,LocalDensity.current.fontScale)

internal enum class CompanionFontScale(val key: String, val label: String, val multiplier: Float?, val description: String) {
    SYSTEM("system", "系统", null, "跟随 Android 字体大小设置。"),
    COMPACT("compact", "较小", 0.9f, "在 Android 当前字体大小基础上缩放 90%。"),
    STANDARD("standard", "标准", 1f, "在 Android 当前字体大小基础上缩放 100%。"),
    LARGE("large", "较大", 1.15f, "在 Android 当前字体大小基础上缩放 115%。"),
    EXTRA_LARGE("extra-large", "特大", 1.3f, "在 Android 当前字体大小基础上缩放 130%。")
}

internal enum class CompanionFontFamily(val key: String, val label: String, val family: FontFamily) {
    SYSTEM("system", "系统", FontFamily.Default),
    SERIF("serif", "衬线", FontFamily.Serif),
    MONOSPACE("monospace", "等宽", FontFamily.Monospace)
}

internal enum class CompanionIconAppearance(val key: String, val label: String, val description: String) {
    FILLED("filled", "实心", "应用页面使用实心 Material 图标。"),
    OUTLINED("outlined", "线框", "应用页面使用线框 Material 图标。"),
    TONAL("tonal", "柔彩", "实心图标置于主题柔彩圆底中。")
}

internal enum class CompanionCornerStyle(val key: String, val label: String, val description: String) {
    SYSTEM("system", "默认", "使用 Material 主题默认圆角。"),
    COMPACT("compact", "紧凑", "卡片和弹窗使用较小圆角。"),
    SOFT("soft", "柔和", "卡片和弹窗使用更大的圆角。")
}

internal enum class CompanionThemeMode(val key: String, val label: String, val isDark: Boolean) {
    DAY("day", "日间", false),
    NIGHT("night", "夜间", true);

    companion object {
        fun fromKey(key: String?): CompanionThemeMode = entries.first { it.key == key }
    }
}

internal fun systemBarUsesDarkIcons(background: Color, foreground: Color): Boolean =
    foreground.luminance() < background.luminance()

// Chat appearance follows the app's chosen mode; Android's mode can differ from it.
@Composable internal fun companionIsDark():Boolean = MaterialTheme.colorScheme.background.luminance()<0.5f

@Composable internal fun themedMessageShape(outgoing:Boolean):CornerBasedShape {
    val corners=MaterialTheme.shapes.medium
    return corners.copy(topStart=if(outgoing)corners.topStart else CornerSize(4.dp),topEnd=if(outgoing)CornerSize(4.dp)else corners.topEnd)
}

internal fun Shapes.withCornerStyle(style: CompanionCornerStyle): Shapes = when (style) {
    CompanionCornerStyle.SYSTEM -> this
    CompanionCornerStyle.COMPACT -> copy(
        extraSmall = RoundedCornerShape(2.dp),
        small = RoundedCornerShape(5.dp),
        medium = RoundedCornerShape(9.dp),
        large = RoundedCornerShape(13.dp),
        extraLarge = RoundedCornerShape(19.dp)
    )
    CompanionCornerStyle.SOFT -> copy(
        extraSmall = RoundedCornerShape(7.dp),
        small = RoundedCornerShape(11.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(23.dp),
        extraLarge = RoundedCornerShape(30.dp)
    )
}

internal fun CompanionIconAppearance.navigationIcon(index: Int): ImageVector = when (this) {
    CompanionIconAppearance.FILLED, CompanionIconAppearance.TONAL -> when (index) {
        0 -> Icons.AutoMirrored.Filled.Chat
        1 -> Icons.Filled.Person
        2 -> Icons.Filled.Settings
        3 -> Icons.Filled.Home
        4 -> Icons.Filled.Extension
        5 -> Icons.Filled.Event
        else -> Icons.Filled.Person
    }
    CompanionIconAppearance.OUTLINED -> when (index) {
        0 -> Icons.AutoMirrored.Outlined.Chat
        1 -> Icons.Outlined.Person
        2 -> Icons.Outlined.Settings
        3 -> Icons.Outlined.Home
        4 -> Icons.Outlined.Extension
        5 -> Icons.Outlined.Event
        else -> Icons.Outlined.Person
    }
}

internal fun Typography.withFontFamily(family: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family)
)

internal enum class CompanionPalette(val key: String, val title: String, val note: String) {
    GARDEN("garden", "花园", "安静的灰绿"),
    PEACH("peach", "暖桃", "柔和的杏粉"),
    DUSK("dusk", "暮色", "沉静的蓝紫"),
    PAPER("paper", "纸间", "温暖的纸张与陶土"),
    SEA("sea", "海盐", "通透的海蓝与青绿"),
    MIDNIGHT("midnight", "月夜", "深蓝夜色与微光"),
    FOREST("forest", "林间", "苔绿与林间日光"),
    SAKURA("sakura", "樱雨", "樱粉与淡紫花影"),
    OBSERVATORY("observatory", "观星", "蓝黑夜空与黄铜星轨");

    fun colors(dark: Boolean): ColorScheme = when (this) {
        GARDEN -> if (dark) darkColorScheme(
            primary = Color(0xFFA5CCB6), onPrimary = Color(0xFF17382B),
            primaryContainer = Color(0xFF28483A), onPrimaryContainer = Color(0xFFD1E9D9),
            secondary = Color(0xFFBACCBF), onSecondary = Color(0xFF24352B),
            background = Color(0xFF171C19), onBackground = Color(0xFFE4E9E5),
            surface = Color(0xFF1E2520), onSurface = Color(0xFFE4E9E5),
            surfaceVariant = Color(0xFF343D37), onSurfaceVariant = Color(0xFFC1CAC3),
            outline = Color(0xFF89948C)
        ) else lightColorScheme(
            primary = Color(0xFF536C69), onPrimary = Color.White,
            primaryContainer = Color(0xFFD7E7DC), onPrimaryContainer = Color(0xFF263D34),
            secondary = Color(0xFF647B70), onSecondary = Color.White,
            background = Color(0xFFF6F7F5), surface = Color.White,
            surfaceVariant = Color(0xFFEDF0EC), onSurfaceVariant = Color(0xFF48534C),
            outline = Color(0xFF737E76)
        )
        PEACH -> if (dark) darkColorScheme(
            primary = Color(0xFFFFB9A7), onPrimary = Color(0xFF542018),
            primaryContainer = Color(0xFF71372B), onPrimaryContainer = Color(0xFFFFDBD1),
            secondary = Color(0xFFE8BDB2), onSecondary = Color(0xFF442A24),
            background = Color(0xFF211917), onBackground = Color(0xFFF3E6E1),
            surface = Color(0xFF2A211F), onSurface = Color(0xFFF3E6E1),
            surfaceVariant = Color(0xFF49332F), onSurfaceVariant = Color(0xFFE0C3BB),
            outline = Color(0xFFA98A81)
        ) else lightColorScheme(
            primary = Color(0xFF87564F), onPrimary = Color.White,
            primaryContainer = Color(0xFFFFDAD1), onPrimaryContainer = Color(0xFF3B211C),
            secondary = Color(0xFF765A53), onSecondary = Color.White,
            background = Color(0xFFFFF8F5), surface = Color(0xFFFFFCFA),
            surfaceVariant = Color(0xFFF4EAE6), onSurfaceVariant = Color(0xFF594842),
            outline = Color(0xFF82706A)
        )
        DUSK -> if (dark) darkColorScheme(
            primary = Color(0xFFC9B8F1), onPrimary = Color(0xFF33245C),
            primaryContainer = Color(0xFF4A3B73), onPrimaryContainer = Color(0xFFE8DDFF),
            secondary = Color(0xFFC8C0E0), onSecondary = Color(0xFF302B42),
            background = Color(0xFF1B1921), onBackground = Color(0xFFEAE5F0),
            surface = Color(0xFF24212B), onSurface = Color(0xFFEAE5F0),
            surfaceVariant = Color(0xFF393443), onSurfaceVariant = Color(0xFFCBC3D6),
            outline = Color(0xFF92899E)
        ) else lightColorScheme(
            primary = Color(0xFF625486), onPrimary = Color.White,
            primaryContainer = Color(0xFFE9DDFF), onPrimaryContainer = Color(0xFF251A46),
            secondary = Color(0xFF695F7D), onSecondary = Color.White,
            background = Color(0xFFF8F6FC), surface = Color.White,
            surfaceVariant = Color(0xFFF0ECF6), onSurfaceVariant = Color(0xFF4C4658),
            outline = Color(0xFF777180)
        )
        PAPER -> if (dark) darkColorScheme(
            primary = Color(0xFFE6B69C), onPrimary = Color(0xFF462719),
            primaryContainer = Color(0xFF664131), onPrimaryContainer = Color(0xFFFFDDCA),
            secondary = Color(0xFFD0BBA5), onSecondary = Color(0xFF3B2D22),
            background = Color(0xFF211D19), onBackground = Color(0xFFF0E8DD),
            surface = Color(0xFF2B2520), onSurface = Color(0xFFF0E8DD),
            surfaceVariant = Color(0xFF42382F), onSurfaceVariant = Color(0xFFD8C9BA),
            outline = Color(0xFFAC9988)
        ) else lightColorScheme(
            primary = Color(0xFF795545), onPrimary = Color.White,
            primaryContainer = Color(0xFFF2DCCD), onPrimaryContainer = Color(0xFF352319),
            secondary = Color(0xFF6F5B4D), onSecondary = Color.White,
            background = Color(0xFFFAF6EE), onBackground = Color(0xFF2B241E),
            surface = Color(0xFFFFFDF8), onSurface = Color(0xFF2B241E),
            surfaceVariant = Color(0xFFF1E9DD), onSurfaceVariant = Color(0xFF574C42),
            outline = Color(0xFF796F65)
        )
        SEA -> if (dark) darkColorScheme(
            primary = Color(0xFF9DD9D5), onPrimary = Color(0xFF123938),
            primaryContainer = Color(0xFF285556), onPrimaryContainer = Color(0xFFC0F2EF),
            secondary = Color(0xFFACD0D0), onSecondary = Color(0xFF203738),
            background = Color(0xFF142327), onBackground = Color(0xFFDCEBED),
            surface = Color(0xFF1C2C30), onSurface = Color(0xFFDCEBED),
            surfaceVariant = Color(0xFF30464A), onSurfaceVariant = Color(0xFFBFD3D6),
            outline = Color(0xFF89A3A6)
        ) else lightColorScheme(
            primary = Color(0xFF28686C), onPrimary = Color.White,
            primaryContainer = Color(0xFFC9E9E7), onPrimaryContainer = Color(0xFF153C3F),
            secondary = Color(0xFF42676A), onSecondary = Color.White,
            background = Color(0xFFF2F8F7), onBackground = Color(0xFF203337),
            surface = Color(0xFFFBFEFD), onSurface = Color(0xFF203337),
            surfaceVariant = Color(0xFFE4F0EF), onSurfaceVariant = Color(0xFF435C60),
            outline = Color(0xFF667E81)
        )
        MIDNIGHT -> if (dark) darkColorScheme(
            primary = Color(0xFF9DD4D7), onPrimary = Color(0xFF12383F),
            primaryContainer = Color(0xFF274B57), onPrimaryContainer = Color(0xFFC6EFF1),
            secondary = Color(0xFFC9B8DF), onSecondary = Color(0xFF352945),
            background = Color(0xFF101A2A), onBackground = Color(0xFFE7EDF4),
            surface = Color(0xFF172438), onSurface = Color(0xFFE7EDF4),
            surfaceVariant = Color(0xFF2A3A50), onSurfaceVariant = Color(0xFFC2D0E0),
            outline = Color(0xFF8D9CB1)
        ) else lightColorScheme(
            primary = Color(0xFF315E6D), onPrimary = Color.White,
            primaryContainer = Color(0xFFD1E9EF), onPrimaryContainer = Color(0xFF17353E),
            secondary = Color(0xFF635A7B), onSecondary = Color.White,
            background = Color(0xFFF3F6FA), onBackground = Color(0xFF242D3B),
            surface = Color.White, onSurface = Color(0xFF242D3B),
            surfaceVariant = Color(0xFFE7ECF4), onSurfaceVariant = Color(0xFF4B5668),
            outline = Color(0xFF707C8D)
        )
        FOREST -> if(dark) darkColorScheme(
            primary=Color(0xFFB0D6AD),onPrimary=Color(0xFF183B21),
            primaryContainer=Color(0xFF30523A),onPrimaryContainer=Color(0xFFD3E9CE),
            secondary=Color(0xFFD1C39C),onSecondary=Color(0xFF3A3120),
            background=Color(0xFF152119),onBackground=Color(0xFFE7EEE2),
            surface=Color(0xFF1E2B21),onSurface=Color(0xFFE7EEE2),
            surfaceVariant=Color(0xFF334736),onSurfaceVariant=Color(0xFFC5D5BE),outline=Color(0xFF94AD8D)
        ) else lightColorScheme(
            primary=Color(0xFF285942),onPrimary=Color.White,
            primaryContainer=Color(0xFFD3E9CE),onPrimaryContainer=Color(0xFF203B28),
            secondary=Color(0xFF6D5B30),onSecondary=Color.White,
            background=Color(0xFFF5F8F2),onBackground=Color(0xFF273B2B),
            surface=Color(0xFFFEFFF9),onSurface=Color(0xFF273B2B),
            surfaceVariant=Color(0xFFE4ECDC),onSurfaceVariant=Color(0xFF465640),outline=Color(0xFF6D8068)
        )
        SAKURA -> if(dark) darkColorScheme(
            primary=Color(0xFFF1B6D1),onPrimary=Color(0xFF49253A),
            primaryContainer=Color(0xFF63364D),onPrimaryContainer=Color(0xFFFFD9E8),
            secondary=Color(0xFFD2BDE8),onSecondary=Color(0xFF38294D),
            background=Color(0xFF261B25),onBackground=Color(0xFFF2E7EF),
            surface=Color(0xFF30232E),onSurface=Color(0xFFF2E7EF),
            surfaceVariant=Color(0xFF4C3748),onSurfaceVariant=Color(0xFFE2CADB),outline=Color(0xFFB897AB)
        ) else lightColorScheme(
            primary=Color(0xFF83476B),onPrimary=Color.White,
            primaryContainer=Color(0xFFF4D2E4),onPrimaryContainer=Color(0xFF432137),
            secondary=Color(0xFF70558A),onSecondary=Color.White,
            background=Color(0xFFFFF6FA),onBackground=Color(0xFF362C3C),
            surface=Color(0xFFFFFCFE),onSurface=Color(0xFF362C3C),
            surfaceVariant=Color(0xFFF2E3ED),onSurfaceVariant=Color(0xFF624C60),outline=Color(0xFF917084)
        )
        OBSERVATORY -> if(dark) darkColorScheme(
            primary=Color(0xFFE8C37A),onPrimary=Color(0xFF3B2D14),
            primaryContainer=Color(0xFF534329),onPrimaryContainer=Color(0xFFFFE8BB),
            secondary=Color(0xFFA8CECE),onSecondary=Color(0xFF183B40),
            background=Color(0xFF0E2130),onBackground=Color(0xFFEDF4F7),
            surface=Color(0xFF182D3B),onSurface=Color(0xFFEDF4F7),
            surfaceVariant=Color(0xFF2C424E),onSurfaceVariant=Color(0xFFCBD9DE),outline=Color(0xFF8FA8AE)
        ) else lightColorScheme(
            primary=Color(0xFF6E5422),onPrimary=Color.White,
            primaryContainer=Color(0xFFF3E4BD),onPrimaryContainer=Color(0xFF382D18),
            secondary=Color(0xFF365F65),onSecondary=Color.White,
            background=Color(0xFFF3F7F9),onBackground=Color(0xFF243641),
            surface=Color(0xFFFCFEFF),onSurface=Color(0xFF243641),
            surfaceVariant=Color(0xFFE3EBEF),onSurfaceVariant=Color(0xFF465E69),outline=Color(0xFF6C828B)
        )
    }.let {scheme->
        // Material's default purple selection token leaked into every built-in palette.
        scheme.copy(secondaryContainer=scheme.primaryContainer,onSecondaryContainer=scheme.onPrimaryContainer).withCompanionSurfaceTokens(dark)
    }

    companion object {
        fun fromKey(key: String?): CompanionPalette = entries.firstOrNull { it.key == key } ?: GARDEN
    }
}

internal fun CompanionPalette.toThemeSpec() = CompanionThemeSpec(
    "$title 自定义",
    CompanionThemeColors.fromColorScheme(colors(false)),
    CompanionThemeColors.fromColorScheme(colors(true))
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CompanionThemeEditor(
    selected: CompanionPalette,
    selectedCustom: Boolean,
    customThemeName: String?,
    selectedFontScale: CompanionFontScale,
    selectedFontFamily: CompanionFontFamily,
    selectedIconAppearance: CompanionIconAppearance,
    selectedCornerStyle: CompanionCornerStyle,
    selectedMode: CompanionThemeMode,
    onSelect: (CompanionPalette) -> Unit,
    onSelectCustom: () -> Unit,
    onSelectFontScale: (CompanionFontScale) -> Unit,
    onSelectFontFamily: (CompanionFontFamily) -> Unit,
    onSelectIconAppearance: (CompanionIconAppearance) -> Unit,
    onSelectCornerStyle: (CompanionCornerStyle) -> Unit,
    onSelectMode: (CompanionThemeMode) -> Unit,
    onImportTheme: () -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    val sideBySide = LocalConfiguration.current.screenWidthDp >= 720
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("应用主题") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = adaptiveDialogContentMaxHeight(500.dp)).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("设置应用配色和显示模式。聊天背景和气泡颜色仍按角色保存。", style = MaterialTheme.typography.bodyMedium)
                if (sideBySide) {
                    ThemeEditorWideLayout {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ThemePaletteChoices(selected, selectedCustom, customThemeName, onSelect, onSelectCustom)
                            androidx.compose.material3.HorizontalDivider()
                            ThemeModeChoices(selectedMode, onSelectMode)
                        }
                        ThemeEditorActions(onImportTheme) { onSelect(CompanionPalette.GARDEN) }
                        ThemeDisplayChoices(selectedFontScale, selectedFontFamily, selectedIconAppearance, selectedCornerStyle,
                            onSelectFontScale, onSelectFontFamily, onSelectIconAppearance, onSelectCornerStyle)
                    }
                } else {
                    ThemePaletteChoices(selected, selectedCustom, customThemeName, onSelect, onSelectCustom)
                    androidx.compose.material3.HorizontalDivider()
                    ThemeModeChoices(selectedMode, onSelectMode)
                    ThemeEditorActions(onImportTheme) { onSelect(CompanionPalette.GARDEN) }
                    ThemeDisplayChoices(selectedFontScale, selectedFontFamily, selectedIconAppearance, selectedCornerStyle,
                        onSelectFontScale, onSelectFontFamily, onSelectIconAppearance, onSelectCornerStyle)
                }
                Text("主题保存在本机，不包含在个人加密备份中。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = onApply) { Text("保存主题") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun ThemeEditorActions(onImportTheme: () -> Unit, onRestoreDefaultPalette: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SuggestionChip(onClick = onImportTheme, label = { Text("从文件导入", maxLines = 1, softWrap = false) })
        SuggestionChip(onClick = onRestoreDefaultPalette, label = { Text("恢复默认配色", maxLines = 1, softWrap = false) })
    }
}

@Composable
private fun ThemeEditorWideLayout(content: @Composable () -> Unit) {
    Layout(content = content) { measurables, constraints ->
        val columnGap = 18.dp.roundToPx()
        val actionGap = 12.dp.roundToPx()
        val availableWidth = constraints.maxWidth - columnGap
        val leftWidth = (availableWidth * 0.45f).toInt()
        val rightWidth = availableWidth - leftWidth

        val leftContent = measurables[0].measure(constraints.copy(minWidth = 0, maxWidth = leftWidth, minHeight = 0))
        val actions = measurables[1].measure(constraints.copy(minWidth = 0, maxWidth = leftWidth, minHeight = 0))
        val displayChoices = measurables[2].measure(constraints.copy(minWidth = 0, maxWidth = rightWidth, minHeight = 0))

        // Measure real child heights because FlowRow intrinsic sizing can hide its final option group.
        val contentHeight = maxOf(displayChoices.height, leftContent.height + actionGap + actions.height)
        val layoutHeight = contentHeight.coerceIn(constraints.minHeight, constraints.maxHeight)

        layout(constraints.maxWidth, layoutHeight) {
            leftContent.placeRelative(0, 0)
            actions.placeRelative(0, layoutHeight - actions.height)
            displayChoices.placeRelative(leftWidth + columnGap, 0)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemePaletteChoices(
    selected: CompanionPalette,
    selectedCustom: Boolean,
    customThemeName: String?,
    onSelect: (CompanionPalette) -> Unit,
    onSelectCustom: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("配色", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Column(Modifier.selectableGroup()) {
            CompanionPalette.entries.forEach { palette ->
                val isSelected = !selectedCustom && selected == palette
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                        selected = isSelected,
                        onClick = { onSelect(palette) },
                        role = Role.RadioButton
                    ).padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RadioButton(isSelected, onClick = null)
                    Box(Modifier.size(20.dp).background(palette.colors(false).primary, CircleShape))
                    Column(Modifier.weight(1f)) {
                        Text(palette.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(palette.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (customThemeName != null) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                        selected = selectedCustom,
                        onClick = onSelectCustom,
                        role = Role.RadioButton
                    ).padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RadioButton(selectedCustom, onClick = null)
                    Box(Modifier.size(20.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                    Text(customThemeName, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ThemeDisplayChoices(
    selectedFontScale: CompanionFontScale,
    selectedFontFamily: CompanionFontFamily,
    selectedIconAppearance: CompanionIconAppearance,
    selectedCornerStyle: CompanionCornerStyle,
    onSelectFontScale: (CompanionFontScale) -> Unit,
    onSelectFontFamily: (CompanionFontFamily) -> Unit,
    onSelectIconAppearance: (CompanionIconAppearance) -> Unit,
    onSelectCornerStyle: (CompanionCornerStyle) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("界面样式", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text("字号", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            CompanionFontScale.entries.forEach { scale ->
                FilterChip(selected = selectedFontScale == scale, onClick = { onSelectFontScale(scale) }, label = { Text(scale.label) })
            }
        }
        Text("字体", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            CompanionFontFamily.entries.forEach { family ->
                FilterChip(selected = selectedFontFamily == family, onClick = { onSelectFontFamily(family) }, label = { Text(family.label) })
            }
        }
        Text("图标", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            CompanionIconAppearance.entries.forEach { appearance ->
                FilterChip(selected = selectedIconAppearance == appearance, onClick = { onSelectIconAppearance(appearance) }, label = { Text(appearance.label) })
            }
        }
        Text("圆角", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            CompanionCornerStyle.entries.forEach { style ->
                FilterChip(selected = selectedCornerStyle == style, onClick = { onSelectCornerStyle(style) }, label = { Text(style.label) })
            }
        }
    }
}

@Composable
private fun ThemeModeChoices(selected: CompanionThemeMode, onSelect: (CompanionThemeMode) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("显示模式", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CompanionThemeMode.entries.forEach { mode ->
                FilterChip(selected = selected == mode, onClick = { onSelect(mode) }, label = { Text(mode.label) })
            }
        }
    }
}

@Composable
internal fun CompanionThemeImportDialog(
    theme: CompanionThemeSpec,
    enabled: Boolean,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (enabled) onDismiss() },
        title = { Text("预览主题：${theme.name}") },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = adaptiveDialogContentMaxHeight(540.dp)).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("文件只包含浅色和深色配色，不含图片、字体、图标或脚本。应用会检查字段和文字对比度。")
                ThemeColorPreview("浅色模式", theme.colors(false))
                ThemeColorPreview("深色模式", theme.colors(true))
                Text("预览会立即显示在当前界面。确认后保存在本机，不写入个人加密备份。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(enabled = enabled, onClick = onApply) { Text("应用并保存") } },
        dismissButton = { TextButton(enabled = enabled, onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun ThemeColorPreview(title: String, colors: ColorScheme) {
    Surface(color = colors.background, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = colors.onBackground)
            Surface(color = colors.primaryContainer, shape = RoundedCornerShape(12.dp)) {
                Text("主要按钮与卡片", Modifier.fillMaxWidth().padding(12.dp), color = colors.onPrimaryContainer)
            }
            Surface(color = colors.surface, shape = RoundedCornerShape(12.dp)) {
                Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("界面内容", color = colors.onSurface)
                    Text("次要说明文字", color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
