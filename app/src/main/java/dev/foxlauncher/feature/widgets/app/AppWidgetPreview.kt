package dev.foxlauncher.feature.widgets.app

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil3.compose.AsyncImage

private val ICON_SIZE = 48.dp

/**
 * An app widget as the picker shows it (task 6.2, PLATFORM-NOTES AW): its preview layout (API 31)
 * applied in an `AppWidgetHostView`, else its preview image, else its icon. Fills its box.
 */
@Composable
fun AppWidgetPreview(option: AppWidgetOption, providers: AppWidgetProviders, modifier: Modifier = Modifier) {
    val info = remember(option.key) { providers.info(option.key) }
    val views = remember(option.key) { providers.previewViews(option.key) }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            info != null && views != null -> AndroidView(
                factory = { context ->
                    AppWidgetHostView(context).apply {
                        setExecutor(providers.inflateExecutor)
                        // A preview belongs to no widget id; the info gives the view the provider's package and padding.
                        setAppWidget(AppWidgetManager.INVALID_APPWIDGET_ID, info)
                        updateAppWidget(views)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            option.previewImage != 0 -> AsyncImage(
                model = resourceUri(option.packageName, option.previewImage),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            option.icon != 0 -> AsyncImage(
                model = resourceUri(option.packageName, option.icon),
                contentDescription = null,
                modifier = Modifier.size(ICON_SIZE),
            )
        }
    }
}

/** Coil loads another package's resources from such a URI, off the main thread. */
private fun resourceUri(packageName: String, resId: Int) = "android.resource://$packageName/$resId"
