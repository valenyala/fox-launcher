package dev.foxlauncher.core.apps

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap
import dev.foxlauncher.core.ui.IconShape
import dev.foxlauncher.core.ui.PathCommand
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Renders a launcher icon drawable into a square bitmap. Adaptive icons are masked with our own
 * [IconShape] instead of the system mask; legacy icons are scaled to fit with [LEGACY_PADDING].
 * Called on IO only.
 */
internal object IconRenderer {

    /** Inset around non-adaptive icons, as a fraction of the icon size. */
    const val LEGACY_PADDING: Float = 0.08f

    fun render(drawable: Drawable, sizePx: Int, shape: IconShape): Bitmap {
        val bitmap = createBitmap(sizePx, sizePx)
        val canvas = Canvas(bitmap)
        if (drawable is AdaptiveIconDrawable) {
            // setBounds lays the 108dp layers out around the 72dp viewport; we only swap the mask.
            drawable.setBounds(0, 0, sizePx, sizePx)
            canvas.clipPath(shape.toPath(sizePx.toFloat()))
            drawable.background?.draw(canvas)
            drawable.foreground?.draw(canvas)
        } else {
            drawLegacy(drawable, canvas, sizePx)
        }
        return bitmap
    }

    private fun drawLegacy(drawable: Drawable, canvas: Canvas, sizePx: Int) {
        val inset = sizePx * LEGACY_PADDING
        val box = sizePx - 2 * inset
        val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: sizePx
        val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: sizePx
        val scale = min(box / w, box / h)
        val dw = (w * scale).roundToInt()
        val dh = (h * scale).roundToInt()
        val left = (sizePx - dw) / 2
        val top = (sizePx - dh) / 2
        drawable.setBounds(left, top, left + dw, top + dh)
        drawable.draw(canvas)
    }

    fun IconShape.toPath(size: Float): Path = Path().apply {
        commands(size).forEach { command ->
            when (command) {
                is PathCommand.MoveTo -> moveTo(command.x, command.y)
                is PathCommand.LineTo -> lineTo(command.x, command.y)
                is PathCommand.CubicTo ->
                    cubicTo(command.x1, command.y1, command.x2, command.y2, command.x, command.y)
                PathCommand.Close -> close()
            }
        }
    }
}
