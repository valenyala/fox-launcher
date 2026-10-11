package dev.foxlauncher.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Dp
import dev.foxlauncher.core.apps.IconCache
import dev.foxlauncher.core.model.AppEntry

/** The process-wide icon cache, provided once by `HomeActivity`. */
val LocalIconCache = staticCompositionLocalOf<IconCache> { error("LocalIconCache not provided") }

/**
 * [IconCache.generation], collected once by `HomeActivity` rather than by every icon: a collector
 * per icon was one more coroutine for each icon composed while the drawer scrolls.
 */
val LocalIconGeneration = compositionLocalOf { 0L }

/** Alpha for apps that cannot be opened right now (suspended, or work profile paused). */
const val UNAVAILABLE_ICON_ALPHA: Float = 0.45f

/**
 * An app's icon from [LocalIconCache]. Shows the memory-cached bitmap immediately when there is
 * one, otherwise a label-colored placeholder until the load finishes. Reloads when
 * [LocalIconGeneration] changes (package updated, icon settings changed).
 */
@Composable
fun AppIcon(entry: AppEntry, size: Dp, modifier: Modifier = Modifier) {
    val cache = LocalIconCache.current
    val generation = LocalIconGeneration.current
    val image by produceState<ImageBitmap?>(cache.cached(entry), entry, generation) {
        value = cache.icon(entry)
    }
    val dimmed = entry.isSuspended || !entry.isAvailable
    val iconModifier = modifier
        .size(size)
        .alpha(if (dimmed) UNAVAILABLE_ICON_ALPHA else 1f)
    val bitmap = image
    if (bitmap != null) {
        Image(bitmap = bitmap, contentDescription = null, modifier = iconModifier)
    } else {
        Box(iconModifier.clip(CircleShape).background(iconPlaceholderColor(entry.label)))
    }
}
