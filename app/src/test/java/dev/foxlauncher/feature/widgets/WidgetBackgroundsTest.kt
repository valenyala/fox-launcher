package dev.foxlauncher.feature.widgets

import dev.foxlauncher.core.model.HomeContainer
import dev.foxlauncher.core.model.HomeItem
import dev.foxlauncher.core.model.HomePosition
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import org.junit.Test

/** Each widget's background (task 5.8). */
class WidgetBackgroundsTest {

    private val pos = HomePosition(HomeContainer.PAGE, 0, 0, 0, spanX = 2)
    private val clock = HomeItem.NativeWidget(1, pos, "clock")
    private val appWidget = HomeItem.AppWidget(2, pos, appWidgetId = 7, provider = "com.c/.Weather", userSerial = 0)
    private val app = HomeItem.App(3, pos, "com.a/.Main", 0)

    @Test
    fun aFoxWidgetFollowsTheSettingUntilItHasItsOwn() {
        assertEquals(25, widgetBackgroundOpacity(clock, defaultOpacity = 25))
        assertEquals(70, widgetBackgroundOpacity(clock.withOwnBackgroundOpacity(70), defaultOpacity = 25))
        assertEquals(0, widgetBackgroundOpacity(clock.withOwnBackgroundOpacity(0), defaultOpacity = 25))
    }

    @Test
    fun anAppWidgetHasNoBackdropUntilItIsGivenOne() {
        assertEquals(0, widgetBackgroundOpacity(appWidget, defaultOpacity = 25))
        assertEquals(40, widgetBackgroundOpacity(appWidget.withOwnBackgroundOpacity(40), defaultOpacity = 25))
    }

    @Test
    fun ownBackgroundRoundTripsAndClears() {
        assertNull(clock.ownBackgroundOpacity)
        assertEquals(55, clock.withOwnBackgroundOpacity(55).ownBackgroundOpacity)
        assertNull(clock.withOwnBackgroundOpacity(55).withOwnBackgroundOpacity(null).ownBackgroundOpacity)
        assertEquals(10, appWidget.withOwnBackgroundOpacity(10).ownBackgroundOpacity)
    }

    @Test
    fun itemsThatAreNoWidgetsHaveNone() {
        assertNull(app.ownBackgroundOpacity)
        assertSame(app, app.withOwnBackgroundOpacity(50))
        assertEquals(0, widgetBackgroundOpacity(app, defaultOpacity = 25))
    }
}
