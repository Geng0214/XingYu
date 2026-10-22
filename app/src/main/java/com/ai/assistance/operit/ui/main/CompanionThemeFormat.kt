package com.ai.assistance.operit.ui.main

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import com.ai.assistance.operit.data.model.ChatFailure
import com.ai.assistance.operit.data.model.StrictJson
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

data class CompanionThemeSpec(
    val name: String,
    val light: CompanionThemeColors,
    val dark: CompanionThemeColors
) {
    fun colors(isDark: Boolean): ColorScheme = (if (isDark) dark else light).toColorScheme(isDark)

}

data class CompanionThemeColors(
    val primary: Int,
    val onPrimary: Int,
    val primaryContainer: Int,
    val onPrimaryContainer: Int,
    val secondary: Int,
    val onSecondary: Int,
    val secondaryContainer: Int,
    val onSecondaryContainer: Int,
    val background: Int,
    val onBackground: Int,
    val surface: Int,
    val onSurface: Int,
    val surfaceVariant: Int,
    val onSurfaceVariant: Int,
    val outline: Int
) {
    internal fun meetsContrastRequirements(): Boolean {
        // Labels are also drawn on Material-derived card layers; checking only base tokens can admit unreadable themes.
        val derivedSurfaces = listOf(
            background,
            surface,
            surfaceVariant,
            lerp(Color(surface), Color(surfaceVariant), 0.25f).toArgb(),
            lerp(Color(surface), Color(surfaceVariant), 0.5f).toArgb(),
            lerp(Color(surface), Color(surfaceVariant), 0.75f).toArgb()
        )
        val textPairs = listOf(
            primary to onPrimary,
            primaryContainer to onPrimaryContainer,
            secondary to onSecondary,
            secondaryContainer to onSecondaryContainer,
            background to primary,
            surface to primary,
            background to onBackground,
            surface to onSurface,
            surfaceVariant to onSurface,
            surfaceVariant to onSurfaceVariant
        )
        return textPairs.all { (background, foreground) -> contrast(background, foreground) >= 4.5 } &&
            derivedSurfaces.all { surfaceColor ->
                contrast(surfaceColor, primary) >= 4.5 &&
                    contrast(surfaceColor, onSurface) >= 4.5 &&
                    contrast(surfaceColor, onSurfaceVariant) >= 4.5
            } &&
            contrast(background, outline) >= 3.0 && contrast(surface, outline) >= 3.0
    }

    fun toColorScheme(isDark: Boolean): ColorScheme {
        val values = arrayOf(
            Color(primary), Color(onPrimary), Color(primaryContainer), Color(onPrimaryContainer),
            Color(secondary), Color(onSecondary), Color(secondaryContainer), Color(onSecondaryContainer),
            Color(background), Color(onBackground), Color(surface), Color(onSurface),
            Color(surfaceVariant), Color(onSurfaceVariant), Color(outline)
        )
        val scheme = if (isDark) darkColorScheme(
            primary = values[0], onPrimary = values[1], primaryContainer = values[2], onPrimaryContainer = values[3],
            secondary = values[4], onSecondary = values[5], secondaryContainer = values[6], onSecondaryContainer = values[7],
            background = values[8], onBackground = values[9], surface = values[10], onSurface = values[11],
            surfaceVariant = values[12], onSurfaceVariant = values[13], outline = values[14]
        ) else lightColorScheme(
            primary = values[0], onPrimary = values[1], primaryContainer = values[2], onPrimaryContainer = values[3],
            secondary = values[4], onSecondary = values[5], secondaryContainer = values[6], onSecondaryContainer = values[7],
            background = values[8], onBackground = values[9], surface = values[10], onSurface = values[11],
            surfaceVariant = values[12], onSurfaceVariant = values[13], outline = values[14]
        )
        return scheme.withCompanionSurfaceTokens(isDark)
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("primary", hex(primary)); put("onPrimary", hex(onPrimary))
        put("primaryContainer", hex(primaryContainer)); put("onPrimaryContainer", hex(onPrimaryContainer))
        put("secondary", hex(secondary)); put("onSecondary", hex(onSecondary))
        put("secondaryContainer", hex(secondaryContainer)); put("onSecondaryContainer", hex(onSecondaryContainer))
        put("background", hex(background)); put("onBackground", hex(onBackground))
        put("surface", hex(surface)); put("onSurface", hex(onSurface))
        put("surfaceVariant", hex(surfaceVariant)); put("onSurfaceVariant", hex(onSurfaceVariant))
        put("outline", hex(outline))
    }

    companion object {
        val FIELDS = setOf(
            "primary", "onPrimary", "primaryContainer", "onPrimaryContainer", "secondary", "onSecondary",
            "secondaryContainer", "onSecondaryContainer", "background", "onBackground", "surface", "onSurface",
            "surfaceVariant", "onSurfaceVariant", "outline"
        )

        fun fromColorScheme(colors: ColorScheme): CompanionThemeColors = CompanionThemeColors(
            colors.primary.toArgb(), colors.onPrimary.toArgb(), colors.primaryContainer.toArgb(), colors.onPrimaryContainer.toArgb(),
            colors.secondary.toArgb(), colors.onSecondary.toArgb(), colors.secondaryContainer.toArgb(), colors.onSecondaryContainer.toArgb(),
            colors.background.toArgb(), colors.onBackground.toArgb(), colors.surface.toArgb(), colors.onSurface.toArgb(),
            colors.surfaceVariant.toArgb(), colors.onSurfaceVariant.toArgb(), colors.outline.toArgb()
        ).also(::validate)

        fun fromJson(value: JSONObject): CompanionThemeColors {
            require(value.keys().asSequence().toSet() == FIELDS)
            fun color(key: String): Int {
                val text = value.get(key)
                require(text is String && text.matches(Regex("#[0-9A-Fa-f]{6}")))
                return text.substring(1).toInt(16) or 0xFF000000.toInt()
            }
            return CompanionThemeColors(
                color("primary"), color("onPrimary"), color("primaryContainer"), color("onPrimaryContainer"),
                color("secondary"), color("onSecondary"), color("secondaryContainer"), color("onSecondaryContainer"),
                color("background"), color("onBackground"), color("surface"), color("onSurface"),
                color("surfaceVariant"), color("onSurfaceVariant"), color("outline")
            ).also(::validate)
        }

        private fun validate(colors: CompanionThemeColors) {
            require(colors.meetsContrastRequirements())
        }

        private fun contrast(first: Int, second: Int): Double {
            val a = luminance(first)
            val b = luminance(second)
            return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
        }

        private fun luminance(color: Int): Double {
            fun channel(shift: Int): Double {
                val value = ((color shr shift) and 0xFF) / 255.0
                return if (value <= 0.04045) value / 12.92 else Math.pow((value + 0.055) / 1.055, 2.4)
            }
            return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
        }

        private fun hex(color: Int) = "#%06X".format(java.util.Locale.ROOT, color and 0xFFFFFF)
    }
}

internal fun ColorScheme.withCompanionSurfaceTokens(isDark: Boolean): ColorScheme {
    val page = background
    val card = surface
    val variant = surfaceVariant
    val midTone = lerp(card, variant, 0.5f)
    return copy(
        surfaceDim = if (isDark) page else card,
        surfaceBright = if (isDark) variant else card,
        surfaceContainerLowest = if (isDark) page else card,
        surfaceContainerLow = if (isDark) card else lerp(card, variant, 0.25f),
        surfaceContainer = midTone,
        surfaceContainerHigh = lerp(card, variant, 0.75f),
        surfaceContainerHighest = variant,
        surfaceTint = primary,
        outlineVariant = outline
    )
}

internal object CompanionThemeCodec {
    const val MAX_BYTES = 32 * 1024
    private val ROOT_FIELDS = setOf("schemaVersion", "type", "name", "light", "dark")

    fun read(input: InputStream): CompanionThemeSpec {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(2048)
        while (true) {
            val count = input.read(buffer, 0, minOf(buffer.size, MAX_BYTES + 1 - output.size()))
            if (count < 0) break
            if (count == 0) continue
            output.write(buffer, 0, count)
            if (output.size() > MAX_BYTES) throw ChatFailure("TEMPLATE")
        }
        return decode(output.toByteArray())
    }

    fun decode(bytes: ByteArray): CompanionThemeSpec = try {
        require(bytes.size <= MAX_BYTES)
        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        StrictJson(text).validate()
        val root = JSONObject(text)
        require(root.keys().asSequence().toSet() == ROOT_FIELDS)
        require(root.get("schemaVersion") == 1 && root.get("type") == "companion.theme")
        val name = root.get("name")
        require(name is String && name == name.trim() && name.length in 1..32 && name.none { Character.isISOControl(it) })
        val light = root.get("light")
        val dark = root.get("dark")
        require(light is JSONObject && dark is JSONObject)
        CompanionThemeSpec(name, CompanionThemeColors.fromJson(light), CompanionThemeColors.fromJson(dark))
    } catch (_: Exception) {
        throw ChatFailure("TEMPLATE")
    }

    fun encode(theme: CompanionThemeSpec): String {
        val text = JSONObject().put("schemaVersion", 1).put("type", "companion.theme").put("name", theme.name)
            .put("light", theme.light.toJson()).put("dark", theme.dark.toJson()).toString(2)
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES)
        return text
    }
}
