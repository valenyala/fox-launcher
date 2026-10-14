package dev.foxlauncher.feature.widgets.app

import dev.foxlauncher.feature.home.grid.Span
import kotlin.test.assertEquals
import org.junit.Test

class AppWidgetBindFlowTest {

    private class FakePort(var attempt: BindAttempt = BindAttempt.BOUND, var configure: Boolean = false) : AppWidgetBindPort {
        private var nextId = 7
        val allocated = mutableListOf<Int>()
        val deleted = mutableListOf<Int>()

        override fun allocateId(): Int = nextId++.also { allocated += it }

        override fun bindIfAllowed(pending: PendingAppWidget) = attempt

        override fun needsConfiguration(appWidgetId: Int) = configure

        override fun deleteId(appWidgetId: Int) {
            deleted += appWidgetId
        }
    }

    private val port = FakePort()
    private val flow = AppWidgetBindFlow(port)
    private val span = Span(4, 2)
    private val pending = PendingAppWidget(7, "com.clock/.Analog", 10, span)

    private fun start() = flow.start("com.clock/.Analog", 10, span)

    @Test
    fun anAllowedWidgetWithoutConfigurationIsPlacedAtOnce() {
        assertEquals(BindStep.Place(pending), start())
        assertEquals(emptyList(), port.deleted)
    }

    @Test
    fun aWidgetThatMustConfigureItselfDoesSoFirst() {
        port.configure = true

        assertEquals(BindStep.Configure(pending), start())
        assertEquals(BindStep.Place(pending), flow.onConfigureResult(pending, configured = true))
    }

    @Test
    fun withoutPermissionTheUserIsAskedAndAGrantGoesOn() {
        port.attempt = BindAttempt.ASK
        port.configure = true

        assertEquals(BindStep.AskToBind(pending), start())
        assertEquals(BindStep.Configure(pending), flow.onBindResult(pending, bound = true))
    }

    @Test
    fun refusingToBindFreesTheId() {
        port.attempt = BindAttempt.ASK
        start()

        assertEquals(BindStep.Cancelled(pending), flow.onBindResult(pending, bound = false))
        assertEquals(listOf(7), port.deleted)
    }

    @Test
    fun cancellingTheConfigurationFreesTheId() {
        port.configure = true
        start()

        assertEquals(BindStep.Cancelled(pending), flow.onConfigureResult(pending, configured = false))
        assertEquals(listOf(7), port.deleted)
    }

    @Test
    fun aProviderThatCannotBeBoundFreesTheIdAtOnce() {
        port.attempt = BindAttempt.FAILED

        assertEquals(BindStep.Cancelled(pending), start())
        assertEquals(listOf(7), port.deleted)
    }

    @Test
    fun eachWidgetGetsItsOwnId() {
        start()
        start()

        assertEquals(listOf(7, 8), port.allocated)
    }
}
