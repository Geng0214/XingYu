package com.ai.assistance.operit

import com.ai.assistance.operit.ui.main.CompanionDockConfig
import com.ai.assistance.operit.ui.main.CompanionDockDestination
import com.ai.assistance.operit.ui.main.DesktopLayout
import com.ai.assistance.operit.ui.main.DesktopLayoutValues
import com.ai.assistance.operit.ui.main.DesktopSection
import com.ai.assistance.operit.ui.main.DesktopWidgetSize
import com.ai.assistance.operit.ui.main.TodayOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionDesktopTest {
    @Test fun dockSettingsNormalizeLegacyOrderAndRoundTripHiddenDestinations() {
        val saved=CompanionDockConfig.fromStoredValues("settings,chat,settings,unknown","settings,chat,unknown")
        assertEquals(
            listOf(CompanionDockDestination.SETTINGS,CompanionDockDestination.CHAT,CompanionDockDestination.COMPANION,CompanionDockDestination.SCHEDULE),
            saved.order
        )
        assertEquals(setOf(CompanionDockDestination.SETTINGS,CompanionDockDestination.CHAT),saved.visible)
        assertEquals(saved,CompanionDockConfig.fromStoredValues(saved.orderValue(),saved.visibleValue()))
    }

    @Test fun firstDockReadShowsEveryPageInItsDefaultOrder() {
        assertEquals(CompanionDockConfig.DEFAULT,CompanionDockConfig.fromStoredValues(null,null))
    }

    @Test fun previousPluginDockPositionBecomesTheScheduleTab() {
        val saved=CompanionDockConfig.fromStoredValues("chat,roles,companion,settings","chat,roles")
        assertEquals(listOf(CompanionDockDestination.CHAT,CompanionDockDestination.SCHEDULE,CompanionDockDestination.COMPANION,CompanionDockDestination.SETTINGS),saved.order)
        assertEquals(setOf(CompanionDockDestination.CHAT,CompanionDockDestination.SCHEDULE),saved.visible)
    }

    @Test fun desktopLayoutEncodingRoundTripsPersonalSectionsAndSizes() {
        val defaults=DesktopLayout.DEFAULT
        val layout=defaults.copy(
            order=listOf(DesktopSection.ACTIVITIES,DesktopSection.TODAY,DesktopSection.READING),
            visible=setOf(DesktopSection.ACTIVITIES,DesktopSection.TODAY),
            sizes=defaults.sizes+(DesktopSection.TODAY to DesktopWidgetSize.COMPACT)
        )

        assertEquals(layout,DesktopLayout.fromStoredValues(layout.storedValues()))
    }

    @Test fun desktopLayoutDropsUnknownsDeduplicatesAndCompletesSectionOrders() {
        val layout=DesktopLayout.fromStoredValues(DesktopLayoutValues(
            sectionOrder="activities,today,activities,unknown",
            visibleSections="today",
            sizes="today:compact,unknown:expanded,reading:missing"
        ))
        assertEquals(
            listOf(DesktopSection.ACTIVITIES,DesktopSection.TODAY,DesktopSection.READING),
            layout.order
        )
        assertEquals(setOf(DesktopSection.TODAY),layout.visible)
        assertEquals(DesktopWidgetSize.COMPACT,layout.sizes.getValue(DesktopSection.TODAY))
        assertTrue(layout.sizes.filterKeys {it!=DesktopSection.TODAY}.values.all {it==DesktopWidgetSize.STANDARD})
    }

    @Test fun todaySummaryOptionsPreserveFirstReadDefaultsAndExplicitEmptySelection() {
        assertEquals(TodayOptions(),TodayOptions.fromStoredValue(null))
        assertEquals(TodayOptions(false,false,false),TodayOptions.fromStoredValue(""))
        val saved=TodayOptions(schedule=true,tasks=false,garden=true)
        assertEquals(saved,TodayOptions.fromStoredValue(saved.storedValue()))
    }
}
