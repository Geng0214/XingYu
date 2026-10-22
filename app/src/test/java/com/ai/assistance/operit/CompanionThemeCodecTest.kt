package com.ai.assistance.operit

import androidx.compose.ui.graphics.lerp
import com.ai.assistance.operit.data.model.ChatFailure
import com.ai.assistance.operit.ui.main.CompanionPalette
import com.ai.assistance.operit.ui.main.CompanionThemeCodec
import com.ai.assistance.operit.ui.main.CompanionThemeSpec
import com.ai.assistance.operit.ui.main.dialogContentMaxHeightForWindow
import com.ai.assistance.operit.ui.main.systemBarUsesDarkIcons
import com.ai.assistance.operit.ui.main.toThemeSpec
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import androidx.compose.ui.unit.dp

class CompanionThemeCodecTest {
    private val light = JSONObject().apply {
        put("primary", "#536C69"); put("onPrimary", "#FFFFFF")
        put("primaryContainer", "#D7E7DC"); put("onPrimaryContainer", "#263D34")
        put("secondary", "#647B70"); put("onSecondary", "#FFFFFF")
        put("secondaryContainer", "#D5E3D9"); put("onSecondaryContainer", "#28392F")
        put("background", "#F6F7F5"); put("onBackground", "#26302A")
        put("surface", "#FFFFFF"); put("onSurface", "#202722")
        put("surfaceVariant", "#EDF0EC"); put("onSurfaceVariant", "#48534C")
        put("outline", "#737E76")
    }
    private val dark = JSONObject().apply {
        put("primary", "#A5CCB6"); put("onPrimary", "#17382B")
        put("primaryContainer", "#28483A"); put("onPrimaryContainer", "#D1E9D9")
        put("secondary", "#BACCBF"); put("onSecondary", "#24352B")
        put("secondaryContainer", "#384B40"); put("onSecondaryContainer", "#E2ECE5")
        put("background", "#171C19"); put("onBackground", "#E4E9E5")
        put("surface", "#1E2520"); put("onSurface", "#E4E9E5")
        put("surfaceVariant", "#343D37"); put("onSurfaceVariant", "#C1CAC3")
        put("outline", "#89948C")
    }

    private fun source(lightColors: JSONObject = light, darkColors: JSONObject = dark) = JSONObject()
        .put("schemaVersion", 1).put("type", "companion.theme").put("name", "花园自定义")
        .put("light", lightColors).put("dark", darkColors)

    private fun reject(bytes: ByteArray) {
        try {
            CompanionThemeCodec.decode(bytes)
            fail("Invalid theme was accepted")
        } catch (error: ChatFailure) {
            assertEquals("TEMPLATE", error.code)
        }
    }

    @Test fun validThemeRoundTripsWithExactLightAndDarkTokens() {
        val encoded = CompanionThemeCodec.encode(CompanionThemeCodec.decode(source().toString().toByteArray()))
        val decoded: CompanionThemeSpec = CompanionThemeCodec.decode(encoded.toByteArray())
        assertEquals("花园自定义", decoded.name)
        assertEquals(CompanionThemeCodec.decode(source().toString().toByteArray()), decoded)
        assertFalse(encoded.contains("script"))
    }

    @Test fun customThemeDerivesMaterialSurfacesFromItsOwnTokens() {
        val decoded = CompanionThemeCodec.decode(source().toString().toByteArray())
        val lightScheme = decoded.colors(false)
        val darkScheme = decoded.colors(true)
        assertEquals(lerp(lightScheme.surface, lightScheme.surfaceVariant, 0.25f), lightScheme.surfaceContainerLow)
        assertEquals(lerp(lightScheme.surface, lightScheme.surfaceVariant, 0.5f), lightScheme.surfaceContainer)
        assertEquals(lerp(lightScheme.surface, lightScheme.surfaceVariant, 0.75f), lightScheme.surfaceContainerHigh)
        assertEquals(lightScheme.surfaceVariant, lightScheme.surfaceContainerHighest)
        assertEquals(darkScheme.surfaceVariant, darkScheme.surfaceContainerHighest)
        assertEquals(lightScheme.outline, lightScheme.outlineVariant)
    }

    @Test fun builtInPalettesMeetDerivedSurfaceContrastRequirements() {
        CompanionPalette.entries.forEach { it.toThemeSpec() }
    }

    @Test fun systemBarIconAppearanceFollowsBuiltInAndImportedLightAndDarkTokens() {
        CompanionPalette.entries.forEach { palette ->
            val light = palette.colors(false)
            val dark = palette.colors(true)
            assertTrue(systemBarUsesDarkIcons(light.background, light.onBackground))
            assertTrue(systemBarUsesDarkIcons(light.surface, light.onSurface))
            assertFalse(systemBarUsesDarkIcons(dark.background, dark.onBackground))
            assertFalse(systemBarUsesDarkIcons(dark.surface, dark.onSurface))
        }
        val imported = CompanionThemeCodec.decode(source().toString().toByteArray())
        val light = imported.colors(false)
        val dark = imported.colors(true)
        assertTrue(systemBarUsesDarkIcons(light.background, light.onBackground))
        assertTrue(systemBarUsesDarkIcons(light.surface, light.onSurface))
        assertFalse(systemBarUsesDarkIcons(dark.background, dark.onBackground))
        assertFalse(systemBarUsesDarkIcons(dark.surface, dark.onSurface))
    }

    @Test fun dialogContentHeightShrinksWithLandscapeAndWindowedHeights() {
        assertEquals(540.dp,dialogContentMaxHeightForWindow(800.dp,540.dp))
        assertEquals(168.dp,dialogContentMaxHeightForWindow(360.dp,540.dp))
        assertEquals(48.dp,dialogContentMaxHeightForWindow(240.dp,540.dp))
        assertEquals(120.dp,dialogContentMaxHeightForWindow(360.dp,540.dp,1.5f))
        assertEquals(0.dp,dialogContentMaxHeightForWindow(240.dp,540.dp,1.5f))
    }

    @Test fun rejectsUnknownFieldsScriptsDuplicateKeysAndUnsupportedVersions() {
        reject(source().put("script", "run(){}").toString().toByteArray())
        reject(source(lightColors = JSONObject(light.toString()).put("image", "content://private")).toString().toByteArray())
        reject(source().put("schemaVersion", 2).toString().toByteArray())
        reject("{\"schemaVersion\":1,\"schemaVersion\":1}".toByteArray())
        reject("{schemaVersion:1}".toByteArray())
    }

    @Test fun rejectsInvalidColorsAndInsufficientTextContrast() {
        reject(source(lightColors = JSONObject(light.toString()).put("primary", "transparent")).toString().toByteArray())
        reject(source(lightColors = JSONObject(light.toString()).put("onPrimary", "#536C69")).toString().toByteArray())
        reject(source(lightColors = JSONObject(light.toString()).put("outline", "#FFFFFF")).toString().toByteArray())
    }

    @Test fun rejectsPrimaryThatCannotBeReadOnPageAndCardSurfaces() {
        val palePrimary = JSONObject(light.toString()).put("primary", "#F1F1F1").put("onPrimary", "#202020")
        reject(source(lightColors = palePrimary).toString().toByteArray())
    }

    @Test fun rejectsSurfaceTextThatFailsOnDerivedCardSurfaces() {
        val mismatchedSurfaceText = JSONObject(light.toString())
            .put("primary", "#000000")
            .put("onPrimary", "#FFFFFF")
            .put("background", "#FFFFFF")
            .put("onBackground", "#000000")
            .put("surface", "#FEFEFE")
            .put("onSurface", "#000000")
            .put("surfaceVariant", "#767676")
            .put("onSurfaceVariant", "#FFFFFF")
        reject(source(lightColors = mismatchedSurfaceText).toString().toByteArray())
    }

    @Test fun rejectsMalformedUtf8AndOversizedFiles() {
        reject(byteArrayOf(0xC3.toByte(), 0x28))
        reject(ByteArray(CompanionThemeCodec.MAX_BYTES + 1) { 32 })
    }

    @Test fun boundedStreamStopsAtLimitPlusOneByte() {
        var consumed = 0
        val input = object : InputStream() {
            override fun read(): Int { consumed++; return 32 }
        }
        try {
            CompanionThemeCodec.read(input)
            fail("Oversized theme stream was accepted")
        } catch (error: ChatFailure) {
            assertEquals("TEMPLATE", error.code)
        }
        assertEquals(CompanionThemeCodec.MAX_BYTES + 1, consumed)
    }
}
