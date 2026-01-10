package eu.kanade.tachiyomi.lib.seedrandom

import kotlin.math.floor
import kotlin.math.pow

// https://github.com/davidbau/seedrandom
class SeedRandom(seed: String) {
    private val width = 256
    private val chunks = 6
    private val digits = 52
    private val startdenom = width.toDouble().pow(chunks)
    private val significance = 2.0.pow(digits)
    private val overflow = significance * 2
    private val mask = width - 1
    private val arc4: ARC4

    init {
        val key = mixkey(seed, width, mask)
        arc4 = ARC4(key, width, mask)
    }

    fun nextDouble(): Double {
        var n = arc4.g(chunks).toDouble()
        var d = startdenom
        var x = 0L
        while (n < significance) {
            n = (n + x) * width
            d *= width
            x = arc4.g(1)
        }
        while (n >= overflow) {
            n /= 2
            d /= 2
            x = x ushr 1
        }
        return (n + x) / d
    }

    fun <T> shuffle(list: MutableList<T>): List<T> {
        val size = list.size
        val resp = ArrayList<T>(size)
        val keys = ArrayList<Int>(size)
        for (i in 0 until size) keys.add(i)

        repeat(size) {
            val r = floor(nextDouble() * keys.size).toInt()
            val g = keys[r]
            keys.removeAt(r)
            resp.add(list[g])
        }
        return resp
    }

    private fun mixkey(seed: String, width: Int, mask: Int): IntArray {
        val key = IntArray(width)
        val stringseed = seed + ""
        var smear = 0
        var j = 0
        while (j < stringseed.length) {
            val charCode = stringseed[j].code
            val keyVal = key[mask and j]
            smear = smear xor (keyVal * 19)
            val mixed = mask and (smear + charCode)
            key[mask and j] = mixed
            j++
        }
        val actualLen = if (stringseed.isEmpty()) 0 else if (stringseed.length < width) stringseed.length else width
        return key.copyOfRange(0, actualLen)
    }

    private class ARC4(key: IntArray, val width: Int, val mask: Int) {
        private var i = 0
        private var j = 0
        private val s = IntArray(width)

        init {
            var effectiveKey = key
            var keylen = effectiveKey.size

            if (keylen == 0) {
                effectiveKey = intArrayOf(0)
                keylen = 1
            }

            for (k in 0 until width) s[k] = k

            var jCounter = 0
            for (k in 0 until width) {
                val t = s[k]
                jCounter = mask and (jCounter + effectiveKey[k % keylen] + t)
                s[k] = s[jCounter]
                s[jCounter] = t
            }
            g(width)
        }

        fun g(count: Int): Long {
            var r = 0L
            var c = count
            while (c-- > 0) {
                i = mask and (i + 1)
                val t = s[i]
                j = mask and (j + t)
                val sj = s[j]
                s[i] = sj
                s[j] = t
                r = r * width + s[mask and (sj + t)]
            }
            return r
        }
    }
}
