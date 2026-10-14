package dev.foxlauncher.feature.home

import android.app.WallpaperManager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.flow.conflate

/** X offset reported when there is only one page: the wallpaper stays centred. */
private const val SINGLE_PAGE_OFFSET = 0.5f

/**
 * Tells the wallpaper where the pager is, for parallax (PLATFORM-NOTES W). Snapshot changes are
 * applied at most once per frame and the collector is conflated, so this runs at most at frame
 * rate. Fox's own wallpaper stays still and does not ask for them (ADR-26); other wallpapers may.
 */
@Composable
fun WallpaperOffsetsEffect(pagerState: PagerState) {
    val context = LocalContext.current
    val view = LocalView.current
    LaunchedEffect(pagerState, view) {
        val wallpaperManager = WallpaperManager.getInstance(context)
        snapshotFlow { pagerState.pageCount to pagerState.currentPage + pagerState.currentPageOffsetFraction }
            .conflate()
            .collect { (pageCount, position) ->
                val token = view.windowToken ?: return@collect
                val lastPage = (pageCount - 1).coerceAtLeast(0)
                wallpaperManager.setWallpaperOffsetSteps(if (lastPage > 0) 1f / lastPage else 0f, 1f)
                val x = if (lastPage > 0) (position / lastPage).coerceIn(0f, 1f) else SINGLE_PAGE_OFFSET
                wallpaperManager.setWallpaperOffsets(token, x, 0f)
            }
    }
}
