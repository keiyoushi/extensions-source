package eu.kanade.tachiyomi.extension.en.anchira

object XXTEA {

    private const val DELTA = -0x61c88647

    @Suppress("NOTHING_TO_INLINE", "FunctionName")
    private inline fun MX(sum: Int, y: Int, z: Int, p: Int, e: Int, k: IntArray): Int {
        return (z.ushr(5) xor (y shl 2)) + (y.ushr(3) xor (z shl 4)) xor (sum xor y) + (k[p and 3 xor e] xor z)
    }

    private fun decrypt(data: ByteArray, key: ByteArray): ByteArray =
        data.takeIf { it.isNotEmpty() }
            ?.let {
                decrypt(data.toIntArray(false), key.fixKey().toIntArray(false))
                    .toByteArray(true)
            } ?: data

    fun decrypt(data: ByteArray, key: String): ByteArray? =
        kotlin.runCatching { decrypt(data, key.toByteArray(Charsets.UTF_8)) }.getOrNull()

    fun decryptToString(data: ByteArray, key: String): String? =
        kotlin.runCatching { decrypt(data, key)?.toString(Charsets.UTF_8) }.getOrNull()

    private fun decrypt(v: IntArray, k: IntArray): IntArray {
        val n = v.size - 1

        if (n < 1) {
            return v
        }
        var p: Int
        val q = 6 + 52 / (n + 1)
        var z: Int
        var y = v[0]
        var sum = q * DELTA
        var e: Int

        while (sum != 0) {
            e = sum.ushr(2) and 3
            p = n
            while (p > 0) {
                z = v[p - 1]
                v[p] -= MX(sum, y, z, p, e, k)
                y = v[p]
                p--
            }
            z = v[n]
            v[0] -= MX(sum, y, z, p, e, k)
            y = v[0]
            sum -= DELTA
        }
        return v
    }

    private fun ByteArray.fixKey(): ByteArray {
        if (size == 16) return this
        val fixedKey = ByteArray(16)

        if (size < 16) {
            copyInto(fixedKey)
        } else {
            copyInto(fixedKey, endIndex = 16)
        }
        return fixedKey
    }

    private fun ByteArray.toIntArray(includeLength: Boolean): IntArray {
        var n = if (size and 3 == 0) {
            size.ushr(2)
        } else {
            size.ushr(2) + 1
        }
        val result: IntArray

        if (includeLength) {
            result = IntArray(n + 1)
            result[n] = size
        } else {
            result = IntArray(n)
        }
        n = size
        for (i in 0 until n) {
            result[i.ushr(2)] =
                result[i.ushr(2)] or (0x000000ff and this[i].toInt() shl (i and 3 shl 3))
        }
        return result
    }

    private fun IntArray.toByteArray(includeLength: Boolean): ByteArray? {
        var n = size shl 2

        if (includeLength) {
            val m = this[size - 1]
            n -= 4
            if (m < n - 3 || m > n) {
                return null
            }
            n = m
        }
        val result = ByteArray(n)

        for (i in 0 until n) {
            result[i] = this[i.ushr(2)].ushr(i and 3 shl 3).toByte()
        }
        return result
    }
}
