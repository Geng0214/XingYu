package com.ai.assistance.operit

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import com.ai.assistance.operit.ui.main.CompanionIconAppearance
import com.ai.assistance.operit.ui.main.withAppearance
import org.junit.Assert.*
import org.junit.Test

class PlannerIconTest {
    @Test fun calendarIconsResolveEveryAppAppearanceWithoutLosingMirroring() {
        val families = listOf(
            Icons.Filled.Checklist to Icons.Outlined.Checklist,
            Icons.Filled.CalendarMonth to Icons.Outlined.CalendarMonth,
            Icons.Filled.EventAvailable to Icons.Outlined.EventAvailable,
            Icons.Filled.PeopleAlt to Icons.Outlined.PeopleAlt,
            Icons.AutoMirrored.Filled.KeyboardArrowLeft to Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
            Icons.AutoMirrored.Filled.KeyboardArrowRight to Icons.AutoMirrored.Outlined.KeyboardArrowRight
        )
        for ((filled, outlined) in families) {
            assertSame(filled, filled.withAppearance(CompanionIconAppearance.FILLED))
            assertSame(outlined, filled.withAppearance(CompanionIconAppearance.OUTLINED))
            assertSame(filled, filled.withAppearance(CompanionIconAppearance.TONAL))
        }
        assertTrue(Icons.AutoMirrored.Filled.KeyboardArrowLeft.withAppearance(CompanionIconAppearance.OUTLINED).autoMirror)
        assertTrue(Icons.AutoMirrored.Filled.KeyboardArrowRight.withAppearance(CompanionIconAppearance.FILLED).autoMirror)
    }
}
