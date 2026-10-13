package dev.foxlauncher.feature.widgets.clock

import android.text.format.DateFormat
import android.text.format.DateUtils
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
import androidx.compose.ui.text.style.TextAlign
import dev.foxlauncher.R
import dev.foxlauncher.feature.home.grid.Span
import dev.foxlauncher.feature.widgets.NativeWidgetScope
import dev.foxlauncher.feature.widgets.NativeWidgetSpec
import java.util.Date
import kotlinx.coroutines.delay

/** The time and date (task 5.1): the trivial widget that proves the framework, and a useful one. */
object ClockWidget {

    const val KIND = "clock"

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

/** Ticks on each minute while composed; follows the system's 12/24-hour choice and locale. */
@Composable
private fun ClockContent(scope: NativeWidgetScope) {
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
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        Text(time, style = timeStyle.copy(color = Color.White, shadow = shadow), textAlign = TextAlign.Center, maxLines = 1)
        Text(date, style = MaterialTheme.typography.titleSmall.copy(color = Color.White, shadow = shadow), textAlign = TextAlign.Center, maxLines = 1)
    }
}

private const val MINUTE_MS = 60_000L
private const val SHADOW_ALPHA = 0.6f
private const val SHADOW_BLUR = 8f
