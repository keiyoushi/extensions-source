package eu.kanade.tachiyomi.extension.en.comix

import android.util.Base64
import java.net.URLEncoder

object Hash {
    // [RC4 key, mutKey, prefKey] × 5 rounds
    private val KEYS = arrayOf(
        "13YDu67uDgFczo3DnuTIURqas4lfMEPADY6Jaeqky+w=", // 0  RC4 key  round 1
        "yEy7wBfBc+gsYPiQL/4Dfd0pIBZFzMwrtlRQGwMXy3Q=", // 1  mutKey   round 1
        "yrP+EVA1Dw==", // 2  prefKey  round 1
        "vZ23RT7pbSlxwiygkHd1dhToIku8SNHPC6V36L4cnwM=", // 3  RC4 key  round 2
        "QX0sLahOByWLcWGnv6l98vQudWqdRI3DOXBdit9bxCE=", // 4  mutKey   round 2
        "WJwgqCmf", // 5  prefKey  round 2
        "BkWI8feqSlDZKMq6awfzWlUypl88nz65KVRmpH0RWIc=", // 6  RC4 key  round 3
        "v7EIpiQQjd2BGuJzMbBA0qPWDSS+wTJRQ7uGzZ6rJKs=", // 7  mutKey   round 3
        "1SUReYlCRA==", // 8  prefKey  round 3
        "RougjiFHkSKs20DZ6BWXiWwQUGZXtseZIyQWKz5eG34=", // 9  RC4 key  round 4
        "LL97cwoDoG5cw8QmhI+KSWzfW+8VehIh+inTxnVJ2ps=", // 10 mutKey   round 4
        "52iDqjzlqe8=", // 11 prefKey  round 4
        "U9LRYFL2zXU4TtALIYDj+lCATRk/EJtH7/y7qYYNlh8=", // 12 RC4 key  round 5
        "e/GtffFDTvnw7LBRixAD+iGixjqTq9kIZ1m0Hj+s6fY=", // 13 mutKey   round 5
        "xb2XwHNB", // 14 prefKey  round 5
    )

    private fun getKeyBytes(index: Int): IntArray {
        val b64 = KEYS.getOrNull(index) ?: return IntArray(0)
        return try {
            Base64.decode(b64, Base64.DEFAULT)
                .map { it.toInt() and 0xFF }
                .toIntArray()
        } catch (_: Exception) {
            IntArray(0)
        }
    }

    private fun rc4(key: IntArray, data: IntArray): IntArray {
        if (key.isEmpty()) return data
        val s = IntArray(256) { it }
        var j = 0
        for (i in 0 until 256) {
            j = (j + s[i] + key[i % key.size]) % 256
            val temp = s[i]
            s[i] = s[j]
            s[j] = temp
        }
        var i = 0
        j = 0
        val out = IntArray(data.size)
        for (k in data.indices) {
            i = (i + 1) % 256
            j = (j + s[i]) % 256
            val temp = s[i]
            s[i] = s[j]
            s[j] = temp
            out[k] = data[k] xor s[(s[i] + s[j]) % 256]
        }
        return out
    }

    private fun mutS(e: Int): Int = (e + 143) % 256
    private fun mutL(e: Int): Int = ((e ushr 1) or (e shl 7)) and 255
    private fun mutC(e: Int): Int = (e + 115) % 256
    private fun mutM(e: Int): Int = e xor 177
    private fun mutF(e: Int): Int = (e - 188 + 256) % 256
    private fun mutG(e: Int): Int = ((e shl 2) or (e ushr 6)) and 255
    private fun mutH(e: Int): Int = (e - 42 + 256) % 256
    private fun mutDollar(e: Int): Int = ((e shl 4) or (e ushr 4)) and 255
    private fun mutB(e: Int): Int = (e - 12 + 256) % 256
    private fun mutUnderscore(e: Int): Int = (e - 20 + 256) % 256
    private fun mutY(e: Int): Int = ((e ushr 1) or (e shl 7)) and 255
    private fun mutK(e: Int): Int = (e - 241 + 256) % 256
    private fun getMutKey(mk: IntArray, idx: Int): Int = if (mk.isNotEmpty() && (idx % 32) < mk.size) mk[idx % 32] else 0

    private fun round1(data: IntArray): IntArray {
        val enc = rc4(getKeyBytes(0), data)
        val mutKey = getKeyBytes(1)
        val prefKey = getKeyBytes(2)
        val out = mutableListOf<Int>()
        for (i in enc.indices) {
            if (i < 7 && i < prefKey.size) out.add(prefKey[i])
            var v = enc[i] xor getMutKey(mutKey, i)
            v = when (i % 10) {
                0, 9 -> mutC(v)
                1 -> mutB(v)
                2 -> mutY(v)
                3 -> mutDollar(v)
                4, 6 -> mutH(v)
                5 -> mutS(v)
                7 -> mutK(v)
                8 -> mutL(v)
                else -> v
            }
            out.add(v and 255)
        }
        return out.toIntArray()
    }

    private fun round2(data: IntArray): IntArray {
        val enc = rc4(getKeyBytes(3), data)
        val mutKey = getKeyBytes(4)
        val prefKey = getKeyBytes(5)
        val out = mutableListOf<Int>()
        for (i in enc.indices) {
            if (i < 6 && i < prefKey.size) out.add(prefKey[i])
            var v = enc[i] xor getMutKey(mutKey, i)
            v = when (i % 10) {
                0, 8 -> mutC(v)
                1 -> mutB(v)
                2, 6 -> mutDollar(v)
                3 -> mutH(v)
                4, 9 -> mutS(v)
                5 -> mutK(v)
                7 -> mutUnderscore(v)
                else -> v
            }
            out.add(v and 255)
        }
        return out.toIntArray()
    }

    private fun round3(data: IntArray): IntArray {
        val enc = rc4(getKeyBytes(6), data)
        val mutKey = getKeyBytes(7)
        val prefKey = getKeyBytes(8)
        val out = mutableListOf<Int>()
        for (i in enc.indices) {
            if (i < 7 && i < prefKey.size) out.add(prefKey[i])
            var v = enc[i] xor getMutKey(mutKey, i)
            v = when (i % 10) {
                0 -> mutC(v)
                1 -> mutF(v)
                2, 8 -> mutS(v)
                3 -> mutG(v)
                4 -> mutY(v)
                5 -> mutM(v)
                6 -> mutDollar(v)
                7 -> mutK(v)
                9 -> mutB(v)
                else -> v
            }
            out.add(v and 255)
        }
        return out.toIntArray()
    }

    private fun round4(data: IntArray): IntArray {
        val enc = rc4(getKeyBytes(9), data)
        val mutKey = getKeyBytes(10)
        val prefKey = getKeyBytes(11)
        val out = mutableListOf<Int>()
        for (i in enc.indices) {
            if (i < 8 && i < prefKey.size) out.add(prefKey[i])
            var v = enc[i] xor getMutKey(mutKey, i)
            v = when (i % 10) {
                0 -> mutB(v)
                1, 9 -> mutM(v)
                2, 7 -> mutL(v)
                3, 5 -> mutS(v)
                4, 6 -> mutUnderscore(v)
                8 -> mutY(v)
                else -> v
            }
            out.add(v and 255)
        }
        return out.toIntArray()
    }

    private fun round5(data: IntArray): IntArray {
        val enc = rc4(getKeyBytes(12), data)
        val mutKey = getKeyBytes(13)
        val prefKey = getKeyBytes(14)
        val out = mutableListOf<Int>()
        for (i in enc.indices) {
            if (i < 6 && i < prefKey.size) out.add(prefKey[i])
            var v = enc[i] xor getMutKey(mutKey, i)
            v = when (i % 10) {
                0 -> mutUnderscore(v)
                1, 7 -> mutS(v)
                2 -> mutC(v)
                3, 5 -> mutM(v)
                4 -> mutB(v)
                6 -> mutF(v)
                8 -> mutDollar(v)
                9 -> mutG(v)
                else -> v
            }
            out.add(v and 255)
        }
        return out.toIntArray()
    }

    /**
     * @param path     API path, e.g. "/manga/some-hash/chapters"
     * @param bodySize `encodeURIComponent(body).length` for POST, or 0 for GET
     * @param time     1 for GET manga requests, `System.currentTimeMillis()` for POST
     */
    fun generateHash(path: String, bodySize: Int = 0, time: Long = 1): String {
        val baseString = "$path:$bodySize:$time"

        val encoded = URLEncoder.encode(baseString, "UTF-8")
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~")

        val initialBytes = encoded.toByteArray(Charsets.US_ASCII)
            .map { it.toInt() and 0xFF }
            .toIntArray()

        val r1 = round1(initialBytes)
        val r2 = round2(r1)
        val r3 = round3(r2)
        val r4 = round4(r3)
        val r5 = round5(r4)

        val finalBytes = ByteArray(r5.size) { r5[it].toByte() }

        return Base64.encodeToString(
            finalBytes,
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP,
        )
    }
}
