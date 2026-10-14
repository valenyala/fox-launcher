package dev.foxlauncher.core.apps

import android.graphics.Bitmap
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.annotation.RequiresApi
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

    /**
     * An icon pack's own icon (task 7.1): already drawn in the pack's style, so it fills the square
     * with no padding; an adaptive one is masked like the apps' own.
     */
    fun renderPackIcon(drawable: Drawable, sizePx: Int, shape: IconShape): Bitmap {
        if (drawable is AdaptiveIconDrawable) return render(drawable, sizePx, shape)
        return createBitmap(sizePx, sizePx).also { bitmap ->
            drawable.setBounds(0, 0, sizePx, sizePx)
            drawable.draw(Canvas(bitmap))
        }
    }

    /**
     * An app the pack does not draw, in the pack's frame (ADW's format): the app's own [icon] at
     * [scale], cut by [mask] where the mask is opaque, over [back] and under [upon].
     */
    fun renderFramed(icon: Bitmap, back: Drawable?, mask: Drawable?, upon: Drawable?, scale: Float, sizePx: Int): Bitmap {
        val inner = createBitmap(sizePx, sizePx)
        val innerCanvas = Canvas(inner)
        val scaled = (sizePx * scale).roundToInt()
        val offset = (sizePx - scaled) / 2
        innerCanvas.drawBitmap(icon, null, Rect(offset, offset, offset + scaled, offset + scaled), Paint(Paint.FILTER_BITMAP_FLAG))
        if (mask != null) {
            val masked = createBitmap(sizePx, sizePx)
            mask.setBounds(0, 0, sizePx, sizePx)
            mask.draw(Canvas(masked))
            innerCanvas.drawBitmap(masked, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) })
        }
        return createBitmap(sizePx, sizePx).also { out ->
            val canvas = Canvas(out)
            back?.apply { setBounds(0, 0, sizePx, sizePx) }?.draw(canvas)
            canvas.drawBitmap(inner, 0f, 0f, null)
            upon?.apply { setBounds(0, 0, sizePx, sizePx) }?.draw(canvas)
        }
    }

    /**
     * A themed icon (task 7.1): the adaptive icon's monochrome layer in [foreground] on [background],
     * in [shape]; null when the app has no monochrome layer.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun renderThemed(drawable: Drawable, sizePx: Int, shape: IconShape, foreground: Int, background: Int): Bitmap? {
        val monochrome = (drawable as? AdaptiveIconDrawable)?.monochrome?.mutate() ?: return null
        return createBitmap(sizePx, sizePx).also { bitmap ->
            val canvas = Canvas(bitmap)
            canvas.clipPath(shape.toPath(sizePx.toFloat()))
            canvas.drawColor(background)
            // The layers are 108dp to the 72dp viewport: half as wide again, centred.
            val outset = (sizePx * AdaptiveIconDrawable.getExtraInsetFraction()).roundToInt()
            monochrome.setBounds(-outset, -outset, sizePx + outset, sizePx + outset)
            monochrome.colorFilter = BlendModeColorFilter(foreground, BlendMode.SRC_IN)
            monochrome.draw(canvas)
        }
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
