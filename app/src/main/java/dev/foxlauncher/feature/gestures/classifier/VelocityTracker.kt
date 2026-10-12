package dev.foxlauncher.feature.gestures.classifier

/** A velocity in pixels per second; y grows downwards, as on screen. */
data class Velocity(val xPerS: Float, val yPerS: Float)

/**
 * Pointer velocity from the samples of the last [windowMs] (task 3.2): a least-squares line through
 * position over time, per axis. A finger that stops before lifting has no recent movement, so its
 * velocity is zero and the lift is not a fling.
 */
class VelocityTracker(private val windowMs: Long = DEFAULT_WINDOW_MS) {

    private val samples = ArrayDeque<Sample>()

    fun add(timeMs: Long, x: Float, y: Float) {
        samples.addLast(Sample(timeMs, x, y))
        while (samples.first().timeMs < timeMs - windowMs) samples.removeFirst()
    }

    fun clear() = samples.clear()

    fun velocity(): Velocity {
        if (samples.size < 2) return Velocity(0f, 0f)
        val t0 = samples.first().timeMs
        val meanT = samples.sumOf { it.timeMs - t0 }.toDouble() / samples.size
        val meanX = samples.sumOf { it.x.toDouble() } / samples.size
        val meanY = samples.sumOf { it.y.toDouble() } / samples.size
        var varT = 0.0
        var covX = 0.0
        var covY = 0.0
        for (s in samples) {
            val dt = s.timeMs - t0 - meanT
            varT += dt * dt
            covX += dt * (s.x - meanX)
            covY += dt * (s.y - meanY)
        }
        if (varT == 0.0) return Velocity(0f, 0f)
        return Velocity((covX / varT * MS_PER_S).toFloat(), (covY / varT * MS_PER_S).toFloat())
    }

    private data class Sample(val timeMs: Long, val x: Float, val y: Float)

    private companion object {
        const val DEFAULT_WINDOW_MS = 100L
        const val MS_PER_S = 1000.0
    }
}
