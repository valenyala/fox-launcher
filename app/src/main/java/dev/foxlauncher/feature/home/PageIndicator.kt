package dev.foxlauncher.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private const val INACTIVE_DOT_ALPHA = 0.4f

/** One dot per page, the current one opaque. Hidden when there is a single page. */
@Composable
fun PageIndicator(pagerState: PagerState, modifier: Modifier = Modifier) {
    if (pagerState.pageCount <= 1) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier.padding(vertical = 8.dp)) {
        repeat(pagerState.pageCount) { page ->
            val alpha = if (page == pagerState.currentPage) 1f else INACTIVE_DOT_ALPHA
            Box(
                Modifier
                    .size(6.dp)
                    .background(Color.White.copy(alpha = alpha), CircleShape),
            )
        }
    }
}
