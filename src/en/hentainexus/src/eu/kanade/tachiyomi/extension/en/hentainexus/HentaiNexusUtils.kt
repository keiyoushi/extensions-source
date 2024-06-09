package eu.kanade.tachiyomi.extension.en.hentainexus

import android.util.Base64
import kotlin.experimental.xor

object HentaiNexusUtils {
    fun decryptData(data: String): String = decryptData(Base64.decode(data, Base64.DEFAULT))

    private val primeNumbers = intArrayOf(2, 3, 5, 7, 11, 13, 17, 19)

    private fun decryptData(data: ByteArray): String {
        val hostname = "hentainexus.com"

        for (i in hostname.indices) {
            data[i] = data[i] xor hostname[i].code.toByte()
        }

        val keyStream = data.slice(0 until 64).map { it.toUByte().toInt() }
        val ciphertext = data.slice(64 until data.size).map { it.toUByte().toInt() }
        val digest = (0..255).toMutableList()

        var primeIdx = 0
        for (i in 0 until 64) {
            primeIdx = primeIdx xor keyStream[i]

            for (j in 0 until 8) {
                primeIdx = if (primeIdx and 1 != 0) {
                    primeIdx ushr 1 xor 12
                } else {
                    primeIdx ushr 1
                }
            }
        }
        primeIdx = primeIdx and 7

        var temp: Int
        var key = 0
        for (i in 0..255) {
            key = (key + digest[i] + keyStream[i % 64]) % 256

            temp = digest[i]
            digest[i] = digest[key]
            digest[key] = temp
        }

        val q = primeNumbers[primeIdx]
        var k = 0
        var n = 0
        var p = 0
        var xorKey = 0
        return buildString(ciphertext.size) {
            for (i in ciphertext.indices) {
                k = (k + q) % 256
                n = (p + digest[(n + digest[k]) % 256]) % 256
                p = (p + k + digest[k]) % 256

                temp = digest[k]
                digest[k] = digest[n]
                digest[n] = temp

                xorKey = digest[(n + digest[(k + digest[(xorKey + p) % 256]) % 256]) % 256]
                append((ciphertext[i] xor xorKey).toChar())
            }
        }
    }
}
