package dev.foxlauncher.core.ui

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.junit.Test

class IconShapesTest {

    private val size = 168f

    @Test
    fun everyShapeFillsItsBoxExactly() {
        IconShape.entries.forEach { shape ->
            val points = shape.commands(size).flatMap(::endpoints)
            assertEquals(0f, points.minOf { it.first }, EPS, "$shape min x")
            assertEquals(0f, points.minOf { it.second }, EPS, "$shape min y")
            assertEquals(size, points.maxOf { it.first }, EPS, "$shape max x")
            assertEquals(size, points.maxOf { it.second }, EPS, "$shape max y")
        }
    }

    @Test
    fun controlPointsNeverLeaveTheBox() {
        IconShape.entries.forEach { shape ->
            shape.commands(size).flatMap(::allPoints).forEach { (x, y) ->
                assertTrue(x in -EPS..size + EPS && y in -EPS..size + EPS, "$shape point ($x, $y) out of box")
            }
        }
    }

    @Test
    fun everyShapeIsOneClosedOutline() {
        IconShape.entries.forEach { shape ->
            val commands = shape.commands(size)
            assertIs<PathCommand.MoveTo>(commands.first(), "$shape starts with MoveTo")
            assertIs<PathCommand.Close>(commands.last(), "$shape ends with Close")
            assertEquals(1, commands.count { it is PathCommand.MoveTo }, "$shape has one sub-path")
        }
    }

    @Test
    fun circleEndpointsLieOnTheCircle() {
        val r = size / 2
        IconShape.CIRCLE.commands(size).flatMap(::endpoints).forEach { (x, y) ->
            assertEquals(r, hypot(x - r, y - r), EPS)
        }
    }

    @Test
    fun squirclePointsLieOnTheSuperellipse() {
        val r = size / 2
        IconShape.SQUIRCLE.commands(size).flatMap(::endpoints).forEach { (x, y) ->
            val nx = abs((x - r) / r).toDouble()
            val ny = abs((y - r) / r).toDouble()
            val value = nx.pow(IconShape.SQUIRCLE_EXPONENT) + ny.pow(IconShape.SQUIRCLE_EXPONENT)
            assertEquals(1.0, value, 1e-4)
        }
    }

    @Test
    fun squircleIsFullerThanTheCircleOnTheDiagonal() {
        // At 45° the superellipse reaches further into the corner than the inscribed circle.
        val r = size / 2
        val farthest = IconShape.SQUIRCLE.commands(size).flatMap(::endpoints).maxOf { (x, y) -> hypot(x - r, y - r) }
        assertTrue(farthest > r * 1.1f)
    }

    @Test
    fun roundedSquareStraightEdgesStopAtTheCornerRadius() {
        val radius = size * IconShape.ROUNDED_SQUARE_RADIUS_FRACTION
        val lines = IconShape.ROUNDED_SQUARE.commands(size).filterIsInstance<PathCommand.LineTo>()
        assertEquals(4, lines.size)
        lines.forEach { line ->
            val onVerticalEdge = line.x == 0f || line.x == size
            val along = if (onVerticalEdge) line.y else line.x
            assertTrue(abs(along - radius) < EPS || abs(along - (size - radius)) < EPS, "line to $line")
        }
    }

    @Test
    fun unknownIdFallsBackToTheDefault() {
        assertEquals(IconShape.DEFAULT, IconShape.fromId("hexagon"))
        assertEquals(IconShape.DEFAULT, IconShape.fromId(null))
        IconShape.entries.forEach { assertEquals(it, IconShape.fromId(it.id)) }
    }

    private fun endpoints(command: PathCommand): List<Pair<Float, Float>> = when (command) {
        is PathCommand.MoveTo -> listOf(command.x to command.y)
        is PathCommand.LineTo -> listOf(command.x to command.y)
        is PathCommand.CubicTo -> listOf(command.x to command.y)
        PathCommand.Close -> emptyList()
    }

    private fun allPoints(command: PathCommand): List<Pair<Float, Float>> = when (command) {
        is PathCommand.CubicTo -> listOf(command.x1 to command.y1, command.x2 to command.y2, command.x to command.y)
        else -> endpoints(command)
    }

    private companion object {
        const val EPS = 1e-3f
    }
}
