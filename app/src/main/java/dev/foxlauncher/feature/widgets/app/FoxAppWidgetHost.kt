package dev.foxlauncher.feature.widgets.app

import android.appwidget.AppWidgetHost
import android.content.Context
import android.os.DeadObjectException
import android.os.TransactionTooLargeException
import androidx.compose.runtime.staticCompositionLocalOf
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.foxlauncher.core.common.FoxLog
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fox's one `AppWidgetHost` for third-party widgets (task 6.1, PLATFORM-NOTES AW). One per process:
 * Hilt creates it when `HomeActivity.onCreate` injects it, and the activity drives listening, so
 * widgets get updates only while the home is started.
 *
 * It counts started instances rather than keeping a flag, as [dev.foxlauncher.feature.home.HomeVisibility]
 * does: during the hand-over of ADR-19 a second instance briefly exists. Main thread only.
 */
@Singleton
class FoxAppWidgetHost @Inject constructor(@ApplicationContext context: Context) : AppWidgetHost(context, HOST_ID) {

    private var started = 0

    /** From `HomeActivity.onStart`. */
    fun onHomeStarted() {
        if (started++ == 0) listen()
    }

    /** From `HomeActivity.onStop`. */
    fun onHomeStopped() {
        if (started == 0) return
        if (--started == 0) stopListening()
    }

    /** From `HomeActivity.onDestroy`: the views made by [createView] hold the activity's context. */
    public override fun clearViews() = super.clearViews()

    private fun listen() {
        try {
            startListening()
        } catch (e: RuntimeException) {
            // The pending updates of every widget come back in the one reply, which can outgrow the binder buffer.
            // The host is registered by then and each view gets its update when created; Launcher3 lets it slide too.
            if (e.cause !is TransactionTooLargeException && e.cause !is DeadObjectException) throw e
            FoxLog.w("AppWidgetHost started without its pending updates", e)
        }
    }

    companion object {
        /** Keys every widget id Fox allocates. Never change it: placed widgets would stop resolving. */
        const val HOST_ID = 0x0F0C
    }
}

/** The host, for the home grid's app widgets (task 6.4); null where none is provided, as in tests. */
val LocalAppWidgetHost = staticCompositionLocalOf<FoxAppWidgetHost?> { null }
