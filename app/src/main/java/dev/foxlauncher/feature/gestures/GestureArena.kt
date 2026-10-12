package dev.foxlauncher.feature.gestures

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import dev.foxlauncher.core.model.GestureType
import dev.foxlauncher.feature.gestures.classifier.GestureClassifier
import dev.foxlauncher.feature.gestures.classifier.GestureConfig
import dev.foxlauncher.feature.gestures.classifier.GestureResult
import dev.foxlauncher.feature.gestures.classifier.PointerPhase
import dev.foxlauncher.feature.gestures.classifier.PointerSample
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Slowest release that still flings: the drawer's threshold, well above the system minimum of 50 dp/s. */
private val MIN_FLING_VELOCITY = 300.dp

/** Android's double-tap slop; Compose's `ViewConfiguration` does not carry it. */
private val DOUBLE_TAP_SLOP = 100.dp

private val PINCH_THRESHOLD = 48.dp

/**
 * The home's gesture arena (task 3.3, ADR-10): every pointer event goes through a
 * [GestureClassifier] in the Initial pass, before the pages, items and dock below see it, and
 * [onGesture] hears what it recognizes. Once a gesture is the home's (a vertical drag, two
 * fingers, a pinch, a double tap, a long press on empty space, three fingers) the rest of its
 * events are consumed, so the pager does not page and items do not react. Single-finger horizontal
 * swipes stay the pager's, and a long press on an item ([isOnItem]) stays the item's.
 *
 * A single-finger vertical drag that [followsVerticalDrag] accepts (it started on the dock, it goes
 * up) is followed instead of recognized (task 3.7): [onDragStart], then [onDrag] with each move in
 * px, then [onDragEnd] with the release velocity in px/s.
 *
 * A gesture that starts while [enabled] is false, at or below [systemGestureTop] (the system's own
 * gesture strip: its home and recents swipes start there), or whose events arrive already consumed
 * (a drag owns the finger), is ignored to its end; one whose events get consumed midway is
 * cancelled. [enabled] is read only when a gesture starts: a followed drag may open what disables it.
 *
 * The lambdas are read on every event, so they must read current state rather than capture it.
 */
fun Modifier.gestureArena(
    enabled: () -> Boolean,
    isOnItem: (Offset) -> Boolean,
    onGesture: (GestureType) -> Unit,
    pagerTakesSwipeLeft: () -> Boolean = { false },
    pagerTakesSwipeRight: () -> Boolean = { false },
    dockTop: () -> Float = { Float.POSITIVE_INFINITY },
    systemGestureTop: () -> Float = { Float.POSITIVE_INFINITY },
    followsVerticalDrag: (fromDock: Boolean, upward: Boolean) -> Boolean = { _, _ -> false },
    onDragStart: () -> Unit = {},
    onDrag: (dyPx: Float) -> Unit = {},
    onDragEnd: (velocityYPxPerS: Float) -> Unit = {},
): Modifier = pointerInput(Unit) {
    val longPressMs = viewConfiguration.longPressTimeoutMillis
    var classifier: GestureClassifier? = null
    var classifierSize = IntSize.Zero
    val down = HashSet<PointerId>()
    var ignoring = false
    var consumeRest = false
    var longPress: Job? = null

    coroutineScope {
        val timers = this
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                if (down.isEmpty()) {
                    ignoring = !enabled() || event.changes.any { it.isConsumed || it.position.y >= systemGestureTop() }
                    consumeRest = false
                    // A new size means new thresholds; it only changes between gestures.
                    if (classifier == null || size != classifierSize) {
                        classifierSize = size
                        classifier = GestureClassifier(
                            GestureConfig(
                                touchSlopPx = viewConfiguration.touchSlop,
                                minFlingVelocityPxPerS = MIN_FLING_VELOCITY.toPx(),
                                longPressMs = longPressMs,
                                doubleTapMs = viewConfiguration.doubleTapTimeoutMillis,
                                doubleTapSlopPx = DOUBLE_TAP_SLOP.toPx(),
                                pinchThresholdPx = PINCH_THRESHOLD.toPx(),
                                arenaWidthPx = size.width.toFloat(),
                                arenaHeightPx = size.height.toFloat(),
                                dockTop = dockTop,
                                pagerTakesSwipeLeft = pagerTakesSwipeLeft,
                                pagerTakesSwipeRight = pagerTakesSwipeRight,
                                followsVerticalDrag = followsVerticalDrag,
                            ),
                        )
                    }
                }
                event.changes.forEach { if (it.pressed) down += it.id else down -= it.id }
                val active = checkNotNull(classifier)

                if (!ignoring && event.changes.any { it.isConsumed }) {
                    longPress?.cancel()
                    val change = event.changes.first()
                    active.feed(PointerSample(change.id.toInt(), change.position.x, change.position.y, change.uptimeMillis, PointerPhase.CANCEL))
                    ignoring = true
                }
                if (!ignoring) {
                    for (change in event.changes) {
                        for (sample in change.toSamples()) {
                            when (val result = active.feed(sample)) {
                                GestureResult.LongPressPending -> {
                                    longPress?.cancel()
                                    val at = change.position
                                    val deadline = change.uptimeMillis + longPressMs
                                    longPress = timers.launch {
                                        delay(longPressMs)
                                        val timed = active.onTimer(deadline)
                                        if (timed is GestureResult.Recognized && !isOnItem(at)) {
                                            consumeRest = true
                                            onGesture(timed.type)
                                        }
                                    }
                                }
                                GestureResult.Claimed -> {
                                    longPress?.cancel()
                                    consumeRest = true
                                }
                                is GestureResult.Recognized -> {
                                    longPress?.cancel()
                                    consumeRest = true
                                    onGesture(result.type)
                                }
                                is GestureResult.DragStarted -> {
                                    longPress?.cancel()
                                    consumeRest = true
                                    onDragStart()
                                    onDrag(result.dy)
                                }
                                is GestureResult.DragUpdate -> onDrag(result.dy)
                                is GestureResult.DragEnd -> onDragEnd(result.velocityY)
                                GestureResult.Cancelled -> {
                                    longPress?.cancel()
                                    consumeRest = true
                                }
                                GestureResult.None -> Unit
                            }
                        }
                    }
                    if (consumeRest) event.changes.forEach { it.consume() }
                }
                if (down.isEmpty()) {
                    longPress?.cancel()
                    ignoring = false
                }
            }
        }
    }
}

private fun PointerId.toInt(): Int = value.toInt()

/** The classifier's view of one change: its batched historical moves first, then the change itself. */
private fun PointerInputChange.toSamples(): List<PointerSample> {
    val id = id.toInt()
    val phase = when {
        changedToDownIgnoreConsumed() -> return listOf(PointerSample(id, position.x, position.y, uptimeMillis, PointerPhase.DOWN))
        changedToUpIgnoreConsumed() -> PointerPhase.UP
        pressed && positionChangeIgnoreConsumed() != Offset.Zero -> PointerPhase.MOVE
        else -> return emptyList()
    }
    return historical.map { PointerSample(id, it.position.x, it.position.y, it.uptimeMillis, PointerPhase.MOVE) } +
        PointerSample(id, position.x, position.y, uptimeMillis, phase)
}
