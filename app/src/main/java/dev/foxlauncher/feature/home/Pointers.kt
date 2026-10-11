package dev.foxlauncher.feature.home

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Consumes every pointer change that reaches it, so nothing behind (the home grid, the drawer
 * swipe) reacts. Put it before (outside) a tap detector on the same node: that detector then
 * still sees each event first.
 */
internal fun Modifier.swallowPointers(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent().changes.forEach { it.consume() }
    }
}
