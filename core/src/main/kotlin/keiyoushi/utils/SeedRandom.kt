package keiyoushi.utils

import kotlin.math.floor
import kotlin.math.pow

/**
 * Kotlin port of [seedrandom](https://github.com/davidbau/seedrandom).
 *
 * @param seed the string used to key the generator
 */
class SeedRandom(seed: String) {
    private val startdenom = WIDTH.toDouble().pow(CHUNKS)
    private val significance = 2.0.pow(DIGITS)
    private val overflow = significance * 2
    private val arc4 = ARC4(mixkey(seed))

    /**
     * Returns the next pseudo-random [Double] in the range `[0, 1)`.
     */
    fun nextDouble(): Double {
        var n = arc4.g(CHUNKS).toDouble()
        var d = startdenom
        var x = 0L
        while (n < significance) {
            n = (n + x) * WIDTH
            d *= WIDTH
            x = arc4.g(1)
        }
        while (n >= overflow) {
            n /= 2
            d /= 2
            x = x ushr 1
        }
        return (n + x) / d
    }

    /**
     * Returns a new list holding the elements of [list] shuffled with this generator.
     *
     * @param list the elements to shuffle
     * @return the shuffled elements
     */
    fun <T> shuffle(list: List<T>): List<T> {
        val size = list.size
        val resp = ArrayList<T>(size)
        val keys = ArrayList<Int>(size)
        for (i in 0 until size) keys.add(i)

        repeat(size) {
            val r = floor(nextDouble() * keys.size).toInt()
            resp.add(list[keys.removeAt(r)])
        }
        return resp
    }

    private fun mixkey(seed: String): IntArray {
        val key = IntArray(WIDTH)
        var smear = 0
        for (j in seed.indices) {
            smear = smear xor (key[MASK and j] * 19)
            key[MASK and j] = MASK and (smear + seed[j].code)
        }
        return key.copyOfRange(0, seed.length.coerceAtMost(WIDTH))
    }

    private class ARC4(key: IntArray) {
        private var i = 0
        private var j = 0
        private val s = IntArray(WIDTH) { it }

        init {
            val effectiveKey = if (key.isEmpty()) intArrayOf(0) else key

            var jCounter = 0
            for (k in 0 until WIDTH) {
                val t = s[k]
                jCounter = MASK and (jCounter + effectiveKey[k % effectiveKey.size] + t)
                s[k] = s[jCounter]
                s[jCounter] = t
            }
            g(WIDTH)
        }

        fun g(count: Int): Long {
            var r = 0L
            var c = count
            while (c-- > 0) {
                i = MASK and (i + 1)
                val t = s[i]
                j = MASK and (j + t)
                val sj = s[j]
                s[i] = sj
                s[j] = t
                r = r * WIDTH + s[MASK and (sj + t)]
            }
            return r
        }
    }
}

private const val WIDTH = 256
private const val CHUNKS = 6
private const val DIGITS = 52
private const val MASK = WIDTH - 1
