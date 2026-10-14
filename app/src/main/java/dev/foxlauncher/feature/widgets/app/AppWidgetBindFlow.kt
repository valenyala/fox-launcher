package dev.foxlauncher.feature.widgets.app

import dev.foxlauncher.feature.home.grid.Span

/** An app widget between the allocation of its id and its place on the grid (task 6.3). */
data class PendingAppWidget(val appWidgetId: Int, val provider: String, val userSerial: Long, val span: Span)

/** How binding a widget id without asking went. */
enum class BindAttempt {
    /** Bound: the user allowed Fox to add widgets before. */
    BOUND,

    /** Android must ask the user first (`ACTION_APPWIDGET_BIND`). */
    ASK,

    /** It cannot be bound at all: the provider or its profile is gone. */
    FAILED,
}

/** What adding an app widget needs from Android; `AppWidgetBinder` is the real one. */
interface AppWidgetBindPort {
    fun allocateId(): Int

    fun bindIfAllowed(pending: PendingAppWidget): BindAttempt

    /** Whether the bound widget wants its configuration activity before it is placed. */
    fun needsConfiguration(appWidgetId: Int): Boolean

    fun deleteId(appWidgetId: Int)
}

/** The next thing to do for a [PendingAppWidget]. */
sealed interface BindStep {
    val pending: PendingAppWidget

    /** Launch `ACTION_APPWIDGET_BIND` and report through [AppWidgetBindFlow.onBindResult]. */
    data class AskToBind(override val pending: PendingAppWidget) : BindStep

    /** Start its configuration activity and report through [AppWidgetBindFlow.onConfigureResult]. */
    data class Configure(override val pending: PendingAppWidget) : BindStep

    /** Put it on the grid. */
    data class Place(override val pending: PendingAppWidget) : BindStep

    /** Nothing more: its id has been freed. */
    data class Cancelled(override val pending: PendingAppWidget) : BindStep
}

/**
 * Adding an app widget (task 6.3, PLATFORM-NOTES AW): allocate an id, bind it (asking the user
 * when Android wants to), let the widget configure itself if it must, then place it. A refusal,
 * a failure or a cancel at any step frees the id, so none is left bound to nothing. Pure, so it
 * is unit tested; the activity carries each [BindStep] out.
 */
class AppWidgetBindFlow(private val port: AppWidgetBindPort) {

    fun start(provider: String, userSerial: Long, span: Span): BindStep {
        val pending = PendingAppWidget(port.allocateId(), provider, userSerial, span)
        return when (port.bindIfAllowed(pending)) {
            BindAttempt.BOUND -> afterBind(pending)
            BindAttempt.ASK -> BindStep.AskToBind(pending)
            BindAttempt.FAILED -> cancel(pending)
        }
    }

    fun onBindResult(pending: PendingAppWidget, bound: Boolean): BindStep = if (bound) afterBind(pending) else cancel(pending)

    fun onConfigureResult(pending: PendingAppWidget, configured: Boolean): BindStep =
        if (configured) BindStep.Place(pending) else cancel(pending)

    private fun afterBind(pending: PendingAppWidget): BindStep =
        if (port.needsConfiguration(pending.appWidgetId)) BindStep.Configure(pending) else BindStep.Place(pending)

    private fun cancel(pending: PendingAppWidget): BindStep {
        port.deleteId(pending.appWidgetId)
        return BindStep.Cancelled(pending)
    }
}
