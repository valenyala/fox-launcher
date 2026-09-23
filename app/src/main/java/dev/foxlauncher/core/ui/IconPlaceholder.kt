package dev.foxlauncher.core.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.absoluteValue

/** A muted tile color derived from the label, shown while an app's icon is still loading. */
fun iconPlaceholderColor(label: String): Color =
    Color.hsl(hue = (label.hashCode().absoluteValue % 360).toFloat(), saturation = 0.35f, lightness = 0.55f)
