package dev.foxlauncher.feature.widgets.clock

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.AlarmClock
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.foxlauncher.R
import dev.foxlauncher.core.common.FoxLog
import dev.foxlauncher.core.common.IoDispatcher
import dev.foxlauncher.feature.home.grid.Span
import dev.foxlauncher.feature.widgets.NativeWidgetScope
import dev.foxlauncher.feature.widgets.NativeWidgetSpec
import java.util.Date
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The time and date (task 5.1): the trivial widget that proves the framework, and a useful one. */
object ClockWidget {

    const val KIND = "clock"
    const val TEST_TAG = "clock_widget"

    val spec = NativeWidgetSpec(
        kind = KIND,
        nameRes = R.string.widget_clock,
        descriptionRes = R.string.widget_clock_description,
        minSpan = Span(2, 1),
        defaultSpan = Span(4, 1),
        maxSpan = Span(5, 2),
        content = { ClockContent(it) },
    )
}

/**
 * Opens the clock app (user request, 2026-10-14): the app that answers Android's "show alarms",
 * at its main screen when it has one; on the main thread only the start itself.
 */
@HiltViewModel
class ClockWidgetViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : ViewModel() {

    fun openClockApp() {
        viewModelScope.launch {
            val intent = withContext(io) { clockAppIntent() }
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (e: ActivityNotFoundException) {
                FoxLog.w("No clock app", e)
            }
        }
    }

    private fun clockAppIntent(): Intent {
        val showAlarms = Intent(AlarmClock.ACTION_SHOW_ALARMS)
        val pm = context.packageManager
        // With several clock apps and no default, Android's chooser answers: then let it ask.
        val clockPackage = pm.resolveActivity(showAlarms, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName?.takeIf { it != ANDROID_PACKAGE }
        return clockPackage?.let(pm::getLaunchIntentForPackage) ?: showAlarms
    }

    private companion object {
        const val ANDROID_PACKAGE = "android"
    }
}

@Composable
private fun ClockContent(scope: NativeWidgetScope) {
    if (scope.isPreview) {
        ClockFace(scope, onClick = null)
    } else {
        val viewModel: ClockWidgetViewModel = hiltViewModel()
        ClockFace(scope, onClick = viewModel::openClockApp)
    }
}

/** Ticks on each minute while composed; follows the system's 12/24-hour choice and locale. A tap opens the clock app. */
@Composable
internal fun ClockFace(scope: NativeWidgetScope, onClick: (() -> Unit)?) {
    val context = LocalContext.current
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(MINUTE_MS - value % MINUTE_MS)
            value = System.currentTimeMillis()
        }
    }
    val time = remember(now) { DateFormat.getTimeFormat(context).format(Date(now)) }
    val date = remember(now) {
        DateUtils.formatDateTime(context, now, DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_YEAR)
    }
    val shadow = Shadow(color = Color.Black.copy(alpha = SHADOW_ALPHA), blurRadius = SHADOW_BLUR)
    val timeStyle = if (scope.spanY >= 2) MaterialTheme.typography.displayLarge else MaterialTheme.typography.displaySmall
    val openLabel = stringResource(R.string.widget_clock_open)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = openLabel, onClick = onClick) else Modifier)
            .testTag(ClockWidget.TEST_TAG),
    ) {
        Text(time, style = timeStyle.copy(color = Color.White, shadow = shadow), textAlign = TextAlign.Center, maxLines = 1)
        Text(date, style = MaterialTheme.typography.titleSmall.copy(color = Color.White, shadow = shadow), textAlign = TextAlign.Center, maxLines = 1)
    }
}

private const val MINUTE_MS = 60_000L
private const val SHADOW_ALPHA = 0.6f
private const val SHADOW_BLUR = 8f
