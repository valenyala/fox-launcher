package dev.foxlauncher.feature.widgets.app

import android.appwidget.AppWidgetProviderInfo
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

private const val STAND_IN_ALPHA = 0.25f

private sealed interface Provider {
    data object Loading : Provider

    data class Found(val info: AppWidgetProviderInfo) : Provider

    data object Gone : Provider
}

/**
 * A third-party widget on the home grid (task 6.4, PLATFORM-NOTES AW): the host's view for
 * [appWidgetId], filling the cells it is given, which also tells the widget its size. Nothing
 * shows until its provider is known; one that has gone shows [missing].
 *
 * Not [interactive] (the drag overlay), it draws a still of the view on the grid instead: a second
 * view for the same id would take the widget's updates away from that one.
 */
@Composable
fun AppWidgetView(appWidgetId: Int, interactive: Boolean, modifier: Modifier = Modifier, missing: @Composable () -> Unit) {
    val host = LocalAppWidgetHost.current
    if (host == null) {
        StandIn(modifier)
        return
    }
    if (!interactive) {
        val still = remember(appWidgetId) { host.viewOf(appWidgetId)?.snapshot()?.asImageBitmap() }
        if (still != null) Image(still, contentDescription = null, contentScale = ContentScale.FillBounds, modifier = modifier.fillMaxSize()) else StandIn(modifier)
        return
    }
    val provider by produceState(host.cachedInfo(appWidgetId)?.let(Provider::Found) ?: Provider.Loading, appWidgetId) {
        value = host.providerInfo(appWidgetId)?.let(Provider::Found) ?: Provider.Gone
    }
    when (val shown = provider) {
        Provider.Loading -> Box(modifier.fillMaxSize())
        Provider.Gone -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { missing() }
        is Provider.Found -> key(appWidgetId) {
            AndroidView(factory = { context -> host.createView(context, appWidgetId, shown.info) }, modifier = modifier.fillMaxSize())
        }
    }
}

@Composable
private fun StandIn(modifier: Modifier) {
    Box(modifier.fillMaxSize().background(Color.Black.copy(alpha = STAND_IN_ALPHA), RoundedCornerShape(20.dp)))
}
