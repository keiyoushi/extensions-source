package eu.kanade.tachiyomi.extension.en.comix

import android.util.Base64
import keiyoushi.utils.readIntBigEndian
import okhttp3.HttpUrl

class SiteKey private constructor(private val id: String, private val inner: IntArray, private val outer: IntArray, private val mask: ByteArray) {

    fun sign(url: HttpUrl): String {
        val digest = compress(outer, compress(inner, canonical(url).toByteArray(Charsets.UTF_8)))
        return id + "." + Base64.encodeToString(digest.copyOf(12), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    fun decrypt(e: String): String {
        val data = Base64.decode(e, Base64.DEFAULT)
        require(data.size >= 2) { "Encrypted response is too short" }
        val start = (data[0].toInt() and 0xff shl 8) or (data[1].toInt() and 0xff)
        val rotation = start % MASK_SIZE
        val plain = ByteArray(data.size - 2) { i ->
            (data[i + 2].toInt() xor mask[(rotation + i) % MASK_SIZE].toInt()).toByte()
        }
        return plain.toString(Charsets.UTF_8)
    }

    companion object {
        private const val MASK_SIZE = 1024

        fun of(id: String, material: ByteArray): SiteKey {
            val words = { from: Int -> IntArray(8) { material.readIntBigEndian(from + it * 4) } }
            return SiteKey(id, words(0), words(32), material.copyOfRange(64, 64 + MASK_SIZE))
        }

        private fun canonical(url: HttpUrl): String {
            val path = url.encodedPath.removePrefix("/api/v1")
            val indexes = HashMap<String, Int>()
            val params = (0 until url.querySize)
                .filter { url.queryParameterName(it) != "_" }
                .map { i ->
                    val name = url.queryParameterName(i)
                    val value = url.queryParameterValue(i).orEmpty()
                    val base = name.substringBefore('[')
                    if (name.endsWith("[]")) {
                        val index = indexes.getOrDefault(base, 0)
                        indexes[base] = index + 1
                        Param(base, "", index, "$base[$index]=$value")
                    } else {
                        Param(base, name.removePrefix(base), 0, "$name=$value")
                    }
                }
                .sortedWith(compareBy(Param::base, Param::nested, Param::index))
            if (params.isEmpty()) return path
            return path + "?" + params.joinToString("&") { it.text }
        }

        private class Param(val base: String, val nested: String, val index: Int, val text: String)

        private fun compress(iv: IntArray, message: ByteArray): ByteArray {
            val blocks = (message.size + 8) / 64 + 1
            val padded = message.copyOf(blocks * 64)
            padded[message.size] = 0x80.toByte()
            val bitLength = (message.size.toLong() + 64) * 8
            for (i in 0 until 8) {
                padded[padded.size - 1 - i] = (bitLength ushr (8 * i)).toByte()
            }

            val h = iv.copyOf()
            val w = IntArray(64)
            for (block in 0 until blocks) {
                val offset = block * 64
                for (i in 0 until 16) {
                    w[i] = padded.readIntBigEndian(offset + i * 4)
                }
                for (i in 16 until 64) {
                    val s0 = w[i - 15].rotateRight(7) xor w[i - 15].rotateRight(18) xor (w[i - 15] ushr 3)
                    val s1 = w[i - 2].rotateRight(17) xor w[i - 2].rotateRight(19) xor (w[i - 2] ushr 10)
                    w[i] = w[i - 16] + s0 + w[i - 7] + s1
                }

                var a = h[0]
                var b = h[1]
                var c = h[2]
                var d = h[3]
                var e = h[4]
                var f = h[5]
                var g = h[6]
                var hh = h[7]
                for (i in 0 until 64) {
                    val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
                    val t1 = hh + s1 + ((e and f) xor (e.inv() and g)) + K[i] + w[i]
                    val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
                    val t2 = s0 + ((a and b) xor (a and c) xor (b and c))
                    hh = g
                    g = f
                    f = e
                    e = d + t1
                    d = c
                    c = b
                    b = a
                    a = t1 + t2
                }
                h[0] += a
                h[1] += b
                h[2] += c
                h[3] += d
                h[4] += e
                h[5] += f
                h[6] += g
                h[7] += hh
            }

            return ByteArray(32) { (h[it / 4] ushr (24 - 8 * (it % 4))).toByte() }
        }

        private val K = longArrayOf(
            0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
            0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
            0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
            0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
            0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
            0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
            0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
            0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
        ).let { k -> IntArray(k.size) { k[it].toInt() } }
    }
}
