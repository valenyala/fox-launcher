package dev.foxlauncher.core.ui

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/**
 * One step of an icon mask outline, in pixels inside a `size × size` box. Pure data so the mask
 * math is unit tested on the JVM; `IconRenderer` turns it into an `android.graphics.Path`.
 */
sealed interface PathCommand {
    data class MoveTo(val x: Float, val y: Float) : PathCommand
    data class LineTo(val x: Float, val y: Float) : PathCommand
    data class CubicTo(
        val x1: Float,
        val y1: Float,
        val x2: Float,
        val y2: Float,
        val x: Float,
        val y: Float,
    ) : PathCommand
    data object Close : PathCommand
}

/** Adaptive-icon mask shapes. [id] is what settings persist. */
enum class IconShape(val id: String) {
    CIRCLE("circle"),
    SQUIRCLE("squircle"),
    ROUNDED_SQUARE("rounded_square"),
    ;

    /** The closed outline of this shape filling a `size × size` box, touching all four edges. */
    fun commands(size: Float): List<PathCommand> = when (this) {
        CIRCLE -> circle(size)
        SQUIRCLE -> squircle(size)
        ROUNDED_SQUARE -> roundedSquare(size, cornerRadius = size * ROUNDED_SQUARE_RADIUS_FRACTION)
    }

    companion object {
        val DEFAULT: IconShape = SQUIRCLE

        fun fromId(id: String?): IconShape = entries.firstOrNull { it.id == id } ?: DEFAULT

        /** Corner radius of [ROUNDED_SQUARE] as a fraction of the icon size. */
        const val ROUNDED_SQUARE_RADIUS_FRACTION: Float = 0.2f

        /** Superellipse exponent of [SQUIRCLE]: `|x|^n + |y|^n = 1`. */
        const val SQUIRCLE_EXPONENT: Double = 4.0

        /** Line segments approximating [SQUIRCLE]; plenty for icon sizes up to a few hundred px. */
        const val SQUIRCLE_SEGMENTS: Int = 96

        /** Control-point distance for a quarter circle drawn as one cubic Bézier, as a fraction of the radius. */
        const val CUBIC_ARC_KAPPA: Float = 0.5522848f
    }
}

private fun circle(size: Float): List<PathCommand> {
    val r = size / 2f
    val c = r
    val k = r * IconShape.CUBIC_ARC_KAPPA
    return listOf(
        PathCommand.MoveTo(c + r, c),
        PathCommand.CubicTo(c + r, c + k, c + k, c + r, c, c + r),
        PathCommand.CubicTo(c - k, c + r, c - r, c + k, c - r, c),
        PathCommand.CubicTo(c - r, c - k, c - k, c - r, c, c - r),
        PathCommand.CubicTo(c + k, c - r, c + r, c - k, c + r, c),
        PathCommand.Close,
    )
}

private fun squircle(size: Float): List<PathCommand> {
    val r = size / 2.0
    val exponent = 2.0 / IconShape.SQUIRCLE_EXPONENT
    val segments = IconShape.SQUIRCLE_SEGMENTS
    val points = (0 until segments).map { i ->
        val t = 2.0 * PI * i / segments
        val cosT = cos(t)
        val sinT = sin(t)
        val x = r + r * sign(cosT) * abs(cosT).pow(exponent)
        val y = r + r * sign(sinT) * abs(sinT).pow(exponent)
        x.toFloat() to y.toFloat()
    }
    return buildList {
        add(PathCommand.MoveTo(points[0].first, points[0].second))
        points.drop(1).forEach { (x, y) -> add(PathCommand.LineTo(x, y)) }
        add(PathCommand.Close)
    }
}

private fun roundedSquare(size: Float, cornerRadius: Float): List<PathCommand> {
    val r = cornerRadius
    val k = r * IconShape.CUBIC_ARC_KAPPA
    val s = size
    return listOf(
        PathCommand.MoveTo(r, 0f),
        PathCommand.LineTo(s - r, 0f),
        PathCommand.CubicTo(s - r + k, 0f, s, r - k, s, r),
        PathCommand.LineTo(s, s - r),
        PathCommand.CubicTo(s, s - r + k, s - r + k, s, s - r, s),
        PathCommand.LineTo(r, s),
        PathCommand.CubicTo(r - k, s, 0f, s - r + k, 0f, s - r),
        PathCommand.LineTo(0f, r),
        PathCommand.CubicTo(0f, r - k, r - k, 0f, r, 0f),
        PathCommand.Close,
    )
}
