package eu.kanade.tachiyomi.extension.en.comix

import android.util.Base64
import java.net.URLEncoder

object Hash {
    // [RC4 key, mutKey, prefKey] × 5 rounds
    private val KEYS = arrayOf(
        "JxTcdyiA5GZxnbrmthXBQfU2IMTKcY1+3nNhbq98Sgo=", // 0  RC4 key  round 1
        "3PordjODbhqla382Cxapmo/1JiABJQcjiJj1+48gTJ4=", // 1  mutKey   round 1
        "OaKvnI5ARA==", // 2  prefKey  round 1
        "MHNBHYWA7lvy867fXgvGcJwWDk79KqUJUVFsh3RwnnI=", // 3  RC4 key  round 2
        "8i0Cru/VJBSVB2Y1GcMDVpzx2WepOcfnWdd81yxICl4=", // 4  mutKey   round 2
        "Fyskubz8VvA=", // 5  prefKey  round 2
        "B46L1x+UeWP+19cRpQ+OZvdLAK9EHID8g3mSgn57tew=", // 6  RC4 key  round 3
        "DTSTmUt6LpDUw9r1lSQqyb3YlFTzruT8tk8wUGkwehQ=", // 7  mutKey   round 3
        "vY/meeI=", // 8  prefKey  round 3
        "7xWfIF5THL5LAnRgAARg+4mjWHPU9n3PQwvzbaMNi+Q=", // 9  RC4 key  round 4
        "bewtiTuV+HJk56xxkf2iCljLgruCpBmN9BgE8i6gc9M=", // 10 mutKey   round 4
        "/Xcb2zAu8AU=", // 11 prefKey  round 4
        "WgeCQ3T8R51uTwVSiVa7Zy0dN6JOg6Z5JleMS+HV8Aw=", // 12 RC4 key  round 5
        "yXayUVFrrcW56jQCEfZzuCidjpnWKjTDUNT7XeX9i7k=", // 13 mutKey   round 5
        "tSLco2w=", // 14 prefKey  round 5
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

    private fun getMutKey(mk: IntArray, idx: Int): Int = if (mk.isNotEmpty() && (idx % 32) < mk.size) mk[idx % 32] else 0

    private fun opShiftRight7Left1(e: Int): Int = ((e ushr 7) or (e shl 1)) and 255
    private fun opShiftLeft1Right7(e: Int): Int = ((e shl 1) or (e ushr 7)) and 255
    private fun opShiftRight2Left6(e: Int): Int = ((e ushr 2) or (e shl 6)) and 255
    private fun opShiftLeft4Right4(e: Int): Int = ((e shl 4) or (e ushr 4)) and 255
    private fun opShiftRight4Left4(e: Int): Int = ((e ushr 4) or (e shl 4)) and 255

    private fun mutate(data: IntArray, mutKey: IntArray, prefKey: IntArray, prefKeyLimit: Int, round: Int): IntArray {
        val out = mutableListOf<Int>()
        for (o in data.indices) {
            if (o < prefKeyLimit && o < prefKey.size) out.add(prefKey[o])
            var n = data[o] xor getMutKey(mutKey, o)
            n = when (round) {
                1 -> when (o % 10) {
                    0 -> opShiftRight7Left1(n)
                    1 -> n xor 37
                    2 -> n xor 81
                    3 -> n xor 147
                    4 -> opShiftRight2Left6(n)
                    5, 8 -> opShiftRight4Left4(n)
                    6 -> n xor 218
                    7 -> (n + 159) and 255
                    9 -> n xor 180
                    else -> n
                }
                2 -> when (o % 10) {
                    0, 9 -> n xor 180
                    1 -> opShiftLeft1Right7(n)
                    2 -> n xor 147
                    3 -> opShiftRight7Left1(n)
                    4 -> opShiftRight2Left6(n)
                    5 -> opShiftRight4Left4(n)
                    6, 8 -> (n + 159) and 255
                    7 -> (n + 34) and 255
                    else -> n
                }
                3 -> when (o % 10) {
                    0 -> n xor 81
                    1 -> opShiftRight4Left4(n)
                    2, 9 -> opShiftLeft4Right4(n)
                    3 -> n xor 37
                    4 -> (n + 159) and 255
                    5 -> opShiftLeft1Right7(n)
                    6 -> n xor 180
                    7 -> (n + 34) and 255
                    8 -> opShiftRight2Left6(n)
                    else -> n
                }
                4 -> when (o % 10) {
                    0, 7 -> n xor 218
                    1, 4 -> opShiftLeft1Right7(n)
                    2 -> opShiftRight7Left1(n)
                    3 -> (n + 159) and 255
                    5, 8 -> n xor 180
                    6 -> n xor 147
                    9 -> n xor 37
                    else -> n
                }
                5 -> when (o % 10) {
                    0 -> opShiftLeft4Right4(n)
                    1, 3 -> n xor 147
                    2 -> (n + 34) and 255
                    4, 9 -> n xor 218
                    5, 7 -> opShiftLeft1Right7(n)
                    6 -> n xor 180
                    8 -> opShiftRight2Left6(n)
                    else -> n
                }
                else -> n
            }
            out.add(n and 255)
        }
        return out.toIntArray()
    }

    private fun round1(data: IntArray): IntArray {
        val mut = mutate(data, getKeyBytes(1), getKeyBytes(2), 7, 1)
        return rc4(getKeyBytes(0), mut)
    }

    private fun round2(data: IntArray): IntArray {
        val mut = mutate(data, getKeyBytes(4), getKeyBytes(5), 8, 2)
        return rc4(getKeyBytes(3), mut)
    }

    private fun round3(data: IntArray): IntArray {
        val mut = mutate(data, getKeyBytes(7), getKeyBytes(8), 5, 3)
        return rc4(getKeyBytes(6), mut)
    }

    private fun round4(data: IntArray): IntArray {
        val mut = mutate(data, getKeyBytes(10), getKeyBytes(11), 8, 4)
        return rc4(getKeyBytes(9), mut)
    }

    private fun round5(data: IntArray): IntArray {
        val mut = mutate(data, getKeyBytes(13), getKeyBytes(14), 5, 5)
        return rc4(getKeyBytes(12), mut)
    }

    fun generateHash(path: String): String {
        val encoded = URLEncoder.encode(path, "UTF-8")
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
