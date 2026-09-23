package dev.foxlauncher.feature.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.foxlauncher.core.model.HomeItem

/** The dock: [slots] equal 1×1 slots in a row; an item's `col` is its slot. */
@Composable
fun Dock(
    items: List<HomeItem>,
    slots: Int,
    modifier: Modifier = Modifier,
    itemContent: @Composable (HomeItem) -> Unit,
) {
    val bySlot = items.associateBy { it.position.col }
    Row(modifier.fillMaxWidth()) {
        repeat(slots) { slot ->
            Box(Modifier.weight(1f)) {
                bySlot[slot]?.let { itemContent(it) }
            }
        }
    }
}
