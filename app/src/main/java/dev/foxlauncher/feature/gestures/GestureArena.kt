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

private val ONE_FINGER_VERTICAL = setOf(
    GestureType.SWIPE_UP,
    GestureType.SWIPE_DOWN,
    GestureType.DOCK_SWIPE_UP,
    GestureType.DOCK_SWIPE_DOWN,
)

/**
 * The home's gesture arena (task 3.3, ADR-10): every pointer event goes through a
 * [GestureClassifier] in the Initial pass, before the pages, items and dock below see it, and
 * [onGesture] hears what it recognizes. Once a gesture is the home's (two fingers, a pinch, a
 * double tap, a long press on empty space, three fingers) the rest of its events are consumed, so
 * the pager does not page and items do not react. Single-finger horizontal swipes stay the pager's,
 * and a long press on an item ([isOnItem]) stays the item's.
 *
 * A gesture that starts while [enabled] is false, or whose events arrive already consumed (a drag
 * owns the finger), is ignored to its end; one that gets so midway is cancelled.
 *
 * Single-finger vertical swipes are reported but not consumed: until task 3.7 the home's drawer
 * detector still follows them and needs their release to fling.
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
                    ignoring = !enabled() || event.changes.any { it.isConsumed }
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
                            ),
                        )
                    }
                }
                event.changes.forEach { if (it.pressed) down += it.id else down -= it.id }
                val active = checkNotNull(classifier)

                if (!ignoring && (!enabled() || event.changes.any { it.isConsumed })) {
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
                                    if (down.size > 1) consumeRest = true
                                }
                                is GestureResult.Recognized -> {
                                    longPress?.cancel()
                                    if (result.type !in ONE_FINGER_VERTICAL) consumeRest = true
                                    onGesture(result.type)
                                }
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
