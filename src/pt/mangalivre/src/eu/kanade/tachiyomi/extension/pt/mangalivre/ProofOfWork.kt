package eu.kanade.tachiyomi.extension.pt.mangalivre

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * The site gates its chapter endpoints behind a visitor cookie that carries a proof of work
 * derived from a timestamp. The work is a deterministic software render: a seeded generator
 * produces a cloud of points, they are rotated and projected onto a small buffer, the segments
 * between them are rasterized, and the resulting buffer is hashed.
 *
 * It only relies on arithmetic, so it runs here without a WebView.
 */
class ProofOfWork(private val parameters: PowParameters) {

    fun solve(timestamp: Long): String {
        val random = Generator(timestamp)
        val canvas = ByteArray(parameters.size * parameters.size)

        val points = List(parameters.pointCount) {
            Point(random.nextSigned(), random.nextSigned(), random.nextSigned())
        }
        val angleX = random.nextAngle()
        val angleY = random.nextAngle()
        val angleZ = random.nextAngle()

        points.zipWithNext { from, to ->
            val start = from.rotate(angleX, angleY, angleZ)
            val end = to.rotate(angleX, angleY, angleZ)
            canvas.drawLine(start.projectX(), start.projectY(), end.projectX(), end.projectY())
        }

        return canvas.hash()
    }

    private inner class Generator(seed: Long) {
        private var state = (seed and UINT_MASK) xor parameters.seedXor

        fun next(): Double {
            state = (state * parameters.multiplier + parameters.increment) and UINT_MASK
            return state.toDouble() / TWO_POW_32
        }

        fun nextSigned(): Double = next() * 2 - 1

        fun nextAngle(): Double = next() * 2 * Math.PI
    }

    private inner class Point(val x: Double, val y: Double, val z: Double) {
        fun rotate(angleX: Double, angleY: Double, angleZ: Double): Point {
            val cosX = cos(angleX)
            val sinX = sin(angleX)
            val y1 = y * cosX - z * sinX
            val z1 = y * sinX + z * cosX

            val cosY = cos(angleY)
            val sinY = sin(angleY)
            val x1 = x * cosY + z1 * sinY
            val z2 = -x * sinY + z1 * cosY

            val cosZ = cos(angleZ)
            val sinZ = sin(angleZ)
            return Point(x1 * cosZ - y1 * sinZ, x1 * sinZ + y1 * cosZ, z2)
        }

        fun projectX(): Int = project(x)

        fun projectY(): Int = project(y)

        private fun project(value: Double): Int {
            val scale = parameters.size / parameters.divisor
            return floor((value / (z + parameters.depth) + 1) * scale).toInt()
        }
    }

    /** Bresenham, skipping whatever falls outside the buffer. */
    private fun ByteArray.drawLine(fromX: Int, fromY: Int, toX: Int, toY: Int) {
        val size = parameters.size
        var x = fromX
        var y = fromY
        val deltaX = abs(toX - fromX)
        val deltaY = -abs(toY - fromY)
        val stepX = if (fromX < toX) 1 else -1
        val stepY = if (fromY < toY) 1 else -1
        var error = deltaX + deltaY

        while (true) {
            if (x in 0 until size && y in 0 until size) {
                this[y * size + x] = LIT
            }
            if (x == toX && y == toY) return

            val doubled = 2 * error
            if (doubled >= deltaY) {
                error += deltaY
                x += stepX
            }
            if (doubled <= deltaX) {
                error += deltaX
                y += stepY
            }
        }
    }

    /** FNV-1a over the rendered buffer. */
    private fun ByteArray.hash(): String {
        var hash = parameters.fnvOffset
        for (pixel in this) {
            hash = hash xor (pixel.toLong() and BYTE_MASK)
            hash = (hash * parameters.fnvPrime) and UINT_MASK
        }
        return hash.toString(16).padStart(HASH_LENGTH, '0')
    }

    companion object {
        private const val LIT = 255.toByte()
        private const val HASH_LENGTH = 8
        private const val UINT_MASK = 0xFFFFFFFFL
        private const val BYTE_MASK = 0xFFL
        private const val TWO_POW_32 = 4294967296.0
    }
}
