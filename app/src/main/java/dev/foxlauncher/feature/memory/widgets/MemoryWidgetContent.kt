package dev.foxlauncher.feature.memory.widgets

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.core.os.ConfigurationCompat
import androidx.compose.ui.platform.LocalConfiguration
import dev.foxlauncher.R
import dev.foxlauncher.core.model.AppEntry
import dev.foxlauncher.core.ui.AppIcon
import dev.foxlauncher.feature.memory.BoostLimit
import dev.foxlauncher.feature.memory.BoostResult
import dev.foxlauncher.feature.memory.MemoryFormat
import dev.foxlauncher.feature.memory.MemorySnapshot
import dev.foxlauncher.feature.memory.RecentApps
import java.util.Locale

object MemoryWidgetTestTags {
    const val GAUGE_RING = "memory_gauge_ring"
    const val SPARKLINE = "memory_sparkline"
}

/** Where the boost widget is: waiting, running (at least 1.2 s, animated), or showing what it measured. */
@Immutable
sealed interface BoostUiState {
    data object Idle : BoostUiState

    data object Boosting : BoostUiState

    data class Done(val result: BoostResult) : BoostUiState
}

/** Above this much memory in use the gauge turns to the warning colour. */
private const val HIGH_USE = 0.9f
private const val TRACK_ALPHA = 0.25f
private val RING_STROKE = 6.dp

/** Text drawn on the wallpaper: white with a soft shadow, as the home's labels. */
@Composable
private fun TextStyle.onWallpaper(): TextStyle =
    copy(color = Color.White, shadow = Shadow(color = Color.Black.copy(alpha = 0.6f), blurRadius = 6f))

@Composable
private fun locale(): Locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.ROOT

/**
 * The RAM gauge (task 5.3): a ring of memory in use with its percentage, and how much is free. At
 * one cell the free amount sits under the ring; wider, beside it.
 */
@Composable
fun MemoryGaugeContent(snapshot: MemorySnapshot?, spanX: Int, modifier: Modifier = Modifier) {
    val percent = snapshot?.let { stringResource(R.string.memory_percent, MemoryFormat.percent(it.usedFraction)) } ?: stringResource(R.string.memory_unknown)
    val free = snapshot?.let { stringResource(R.string.memory_gb_free, MemoryFormat.gb(it.availableBytes, locale())) } ?: ""
    val fraction = snapshot?.usedFraction ?: 0f
    BoxWithConstraints(modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center) {
        if (spanX <= 1) {
            val ring = min(maxWidth, maxHeight * 0.65f)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Ring(fraction, ring) { Text(percent, style = MaterialTheme.typography.labelLarge.onWallpaper()) }
                // Two lines rather than an ellipsis: "2.1 GB" / "free" fits a narrow cell.
                Text(free, style = MaterialTheme.typography.labelSmall.onWallpaper(), textAlign = TextAlign.Center, maxLines = 2)
            }
        } else {
            val ring = min(maxHeight, maxWidth * 0.45f)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Ring(fraction, ring) {}
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(percent, style = MaterialTheme.typography.headlineSmall.onWallpaper())
                    Text(free, style = MaterialTheme.typography.labelMedium.onWallpaper(), maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun Ring(fraction: Float, size: Dp, center: @Composable () -> Unit) {
    val track = Color.White.copy(alpha = TRACK_ALPHA)
    val fill = if (fraction >= HIGH_USE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size).testTag(MemoryWidgetTestTags.GAUGE_RING)) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = RING_STROKE.toPx()
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            val topLeft = Offset(stroke / 2, stroke / 2)
            drawArc(track, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
            drawArc(fill, -90f, 360f * fraction, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        center()
    }
}

/**
 * The boost button (task 5.3): a rocket to tap, which lifts while the boost runs, then the freed
 * amount as measured, or that there was nothing to free. Where the phone does not let apps close
 * others ([limit]: Android 14+, MIUI), a caption says so, and tapping it calls [onInfo] for the
 * explanation (ADR-5).
 */
@Composable
fun MemoryBoostContent(
    state: BoostUiState,
    limit: BoostLimit?,
    onBoost: () -> Unit,
    onInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val boostLabel = stringResource(R.string.memory_boost_action)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .clickable(enabled = state != BoostUiState.Boosting, role = Role.Button, onClickLabel = boostLabel, onClick = onBoost)
            .padding(6.dp),
    ) {
        val lift = if (state == BoostUiState.Boosting) {
            val transition = rememberInfiniteTransition(label = "boost")
            val value by transition.animateFloat(0f, -10f, infiniteRepeatable(tween(LIFT_MS), RepeatMode.Reverse), label = "lift")
            value
        } else {
            0f
        }
        Icon(
            painterResource(R.drawable.ic_rocket),
            contentDescription = boostLabel,
            tint = Color.White,
            modifier = Modifier
                .size(32.dp)
                .graphicsLayer { translationY = lift.dp.toPx() },
        )
        Spacer(Modifier.height(4.dp))
        val caption = when (state) {
            BoostUiState.Boosting -> stringResource(R.string.memory_boosting)
            is BoostUiState.Done -> MemoryFormat.mb(state.result.freedBytes).takeIf { it > 0 }
                ?.let { stringResource(R.string.memory_freed, it.toString()) }
                ?: stringResource(R.string.memory_nothing_freed)
            BoostUiState.Idle -> null
        }
        if (caption != null) {
            Text(caption, style = MaterialTheme.typography.labelSmall.onWallpaper(), textAlign = TextAlign.Center, maxLines = 2)
        } else if (limit != null) {
            val info = stringResource(R.string.memory_own_only_info)
            // The icon under the caption, not beside it: a one-cell widget is too narrow for both on a line.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clickable(role = Role.Button, onClickLabel = info, onClick = onInfo)
                    .semantics { contentDescription = info },
            ) {
                Text(
                    stringResource(if (limit == BoostLimit.MIUI) R.string.memory_limited_miui else R.string.memory_own_only),
                    style = MaterialTheme.typography.labelSmall.onWallpaper(),
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                )
                Icon(painterResource(R.drawable.ic_info), contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/**
 * Memory details (task 5.3): used, available, cached and swap, a sparkline of the last readings, and
 * from three rows up the recently used apps, each opening its app info (where it can be force-
 * stopped), or the way to grant usage access.
 */
@Composable
fun MemoryDetailContent(
    snapshot: MemorySnapshot?,
    history: List<Float>,
    recent: RecentApps?,
    showRecent: Boolean,
    onAppInfo: (AppEntry) -> Unit,
    onUsageAccess: () -> Unit,
    modifier: Modifier = Modifier,
    appIcon: @Composable (AppEntry) -> Unit = { AppIcon(it, RECENT_ICON_SIZE) },
) {
    val locale = locale()
    val label = MaterialTheme.typography.labelMedium.onWallpaper()
    Column(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        DetailRow(stringResource(R.string.memory_used), snapshot?.usedBytes?.let { size(it, locale) }, label)
        DetailRow(stringResource(R.string.memory_available), snapshot?.availableBytes?.let { size(it, locale) }, label)
        DetailRow(stringResource(R.string.memory_cached), snapshot?.cachedBytes?.let { size(it, locale) }, label)
        val swap = snapshot?.let { s ->
            val used = s.swapUsedBytes
            val total = s.swapTotalBytes
            if (used == null || total == null || total == 0L) null else "${MemoryFormat.gb(used, locale)} / ${size(total, locale)}"
        }
        DetailRow(stringResource(R.string.memory_swap), swap, label)
        Spacer(Modifier.height(6.dp))
        Sparkline(history, Modifier.fillMaxWidth().height(36.dp))
        if (showRecent && recent != null) {
            Spacer(Modifier.height(8.dp))
            val apps = recent.apps
            if (apps == null) {
                Text(
                    stringResource(R.string.memory_usage_access),
                    style = MaterialTheme.typography.labelMedium.onWallpaper(),
                    modifier = Modifier.clickable(role = Role.Button, onClick = onUsageAccess),
                )
            } else if (apps.isNotEmpty()) {
                Text(stringResource(R.string.memory_recent_apps), style = MaterialTheme.typography.labelSmall.onWallpaper())
                apps.forEach { app ->
                    val description = stringResource(R.string.memory_app_info, app.label)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(role = Role.Button, onClickLabel = description) { onAppInfo(app) }
                            .padding(vertical = 2.dp),
                    ) {
                        appIcon(app)
                        Spacer(Modifier.width(8.dp))
                        Text(app.label, style = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun size(bytes: Long, locale: Locale): String {
    val (value, inGb) = MemoryFormat.size(bytes, locale)
    return stringResource(if (inGb) R.string.memory_gb else R.string.memory_mb, value)
}

@Composable
private fun DetailRow(name: String, value: String?, style: TextStyle) {
    Row(Modifier.fillMaxWidth()) {
        Text(name, style = style, modifier = Modifier.weight(1f), maxLines = 1)
        Text(value ?: stringResource(R.string.memory_unknown), style = style, maxLines = 1)
    }
}

/** Memory in use over the last readings, oldest on the left, 0 at the bottom and all of it at the top. */
@Composable
private fun Sparkline(samples: List<Float>, modifier: Modifier) {
    val line = MaterialTheme.colorScheme.primary
    Canvas(modifier.testTag(MemoryWidgetTestTags.SPARKLINE)) {
        if (samples.size < 2) return@Canvas
        val step = size.width / (samples.size - 1)
        val path = Path()
        samples.forEachIndexed { i, sample ->
            val point = Offset(i * step, size.height * (1f - sample.coerceIn(0f, 1f)))
            if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        drawPath(path, line, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}

private const val LIFT_MS = 300
private val RECENT_ICON_SIZE = 20.dp
