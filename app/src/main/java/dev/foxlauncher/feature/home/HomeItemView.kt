package dev.foxlauncher.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.foxlauncher.R
import dev.foxlauncher.core.model.AppEntry
import dev.foxlauncher.core.model.HomeItem
import dev.foxlauncher.core.ui.AppIcon
import dev.foxlauncher.core.ui.UNAVAILABLE_ICON_ALPHA
import kotlinx.collections.immutable.ImmutableMap

private const val FOLDER_BACKGROUND_ALPHA = 0.3f
private const val FOLDER_PREVIEW_COUNT = 4

/** Label style for anything drawn straight onto the wallpaper: white with a soft shadow. */
@Composable
private fun wallpaperLabelStyle() = MaterialTheme.typography.labelMedium.copy(
    color = Color.White,
    shadow = Shadow(color = Color.Black.copy(alpha = 0.6f), blurRadius = 6f),
)

/** Where an item's icon last was, without making every layout pass a recomposition. */
private class IconBounds {
    var value: Rect? = null
}

/**
 * One home or dock item: icon plus optional label. [onClick] receives the icon's bounds in window
 * coordinates for the launch animation.
 */
@Composable
fun HomeItemView(
    item: HomeItem,
    apps: ImmutableMap<String, AppEntry>,
    iconSize: Dp,
    showLabel: Boolean,
    onClick: (HomeItem, Rect?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bounds = remember { IconBounds() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .clickable { onClick(item, bounds.value) }
            .padding(4.dp),
    ) {
        Box(Modifier.onGloballyPositioned { bounds.value = it.boundsInWindow() }) {
            ItemIcon(item, apps, iconSize)
        }
        if (showLabel) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = labelOf(item, apps),
                style = wallpaperLabelStyle(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ItemIcon(item: HomeItem, apps: ImmutableMap<String, AppEntry>, iconSize: Dp) {
    when (item) {
        is HomeItem.App -> AppOrMissingIcon(apps[item.appKey], iconSize)
        is HomeItem.Shortcut -> AppOrMissingIcon(appOfShortcut(item, apps), iconSize)
        is HomeItem.Folder -> FolderIcon(item, apps, iconSize)
        is HomeItem.AppWidget, is HomeItem.NativeWidget -> Box(
            Modifier
                .size(iconSize)
                .background(Color.Black.copy(alpha = FOLDER_BACKGROUND_ALPHA), RoundedCornerShape(16.dp)),
        )
    }
}

/** An app that has vanished from the catalog stays as a dim disc until 2.8 reconciles it away. */
@Composable
private fun AppOrMissingIcon(entry: AppEntry?, size: Dp) {
    if (entry != null) {
        AppIcon(entry, size)
    } else {
        Box(
            Modifier
                .size(size)
                .alpha(UNAVAILABLE_ICON_ALPHA)
                .background(Color.Gray, CircleShape),
        )
    }
}

/** Until 2.6 loads real shortcut icons, a shortcut shows its app's icon. */
private fun appOfShortcut(item: HomeItem.Shortcut, apps: ImmutableMap<String, AppEntry>): AppEntry? =
    apps.values.firstOrNull { it.packageName == item.packageName && it.userSerial == item.userSerial }

/** A 2×2 preview of the folder's first items on a translucent tile. 2.5 builds the rest. */
@Composable
private fun FolderIcon(folder: HomeItem.Folder, apps: ImmutableMap<String, AppEntry>, size: Dp) {
    val preview = folder.items.take(FOLDER_PREVIEW_COUNT)
    val mini = size * 0.4f
    Column(
        verticalArrangement = Arrangement.spacedBy(size * 0.05f, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .size(size)
            .background(Color.White.copy(alpha = FOLDER_BACKGROUND_ALPHA), RoundedCornerShape(size * 0.3f)),
    ) {
        preview.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(size * 0.05f)) {
                row.forEach { child ->
                    when (child) {
                        is HomeItem.App -> AppOrMissingIcon(apps[child.appKey], mini)
                        is HomeItem.Shortcut -> AppOrMissingIcon(appOfShortcut(child, apps), mini)
                        else -> Spacer(Modifier.size(mini))
                    }
                }
            }
        }
    }
}

@Composable
private fun labelOf(item: HomeItem, apps: ImmutableMap<String, AppEntry>): String = when (item) {
    is HomeItem.App -> apps[item.appKey]?.label ?: ""
    is HomeItem.Shortcut -> item.title
    is HomeItem.Folder -> item.title.ifEmpty { stringResource(R.string.folder_untitled) }
    is HomeItem.AppWidget, is HomeItem.NativeWidget -> ""
}
