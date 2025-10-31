package eu.kanade.tachiyomi.extension.pt.sakuramangas

import android.util.Base64
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import kotlin.experimental.xor

// Function extracted from https://sakuramangas.org/dist/sakura/pages/capitulo/capitulo.v139w.obk.js
object AetherCipher {

    private data class KeystreamResult(val keystream: ByteArray, val finalState: LongArray)

    fun decrypt(data: String, key: String): String {
        return try {
            val encryptedBytes = Base64.decode(data, Base64.DEFAULT)

            val scheduledKey = generateScheduledKey(key)

            val sha256Digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
            val buffer = ByteBuffer.wrap(sha256Digest).order(ByteOrder.LITTLE_ENDIAN)
            val initialState = LongArray(8) {
                buffer.getInt(it * 4).toLong() and 0xFFFFFFFFL
            }

            val keystreamResult = generateKeystream(encryptedBytes.size, initialState, scheduledKey)
            val keystream = keystreamResult.keystream
            val finalState = keystreamResult.finalState
            val lastStateValue = finalState[7]

            val reversedXORBytes = reverseAetherXORChain(encryptedBytes, lastStateValue.toInt())

            val decryptedBytes = ByteArray(encryptedBytes.size)
            for (i in encryptedBytes.indices) {
                decryptedBytes[i] = reversedXORBytes[i] xor keystream[i]
            }
            String(decryptedBytes, Charsets.UTF_8)
        } catch (_: Exception) {
            throw Error("Could not decrypt chapter data.")
        }
    }

    private fun generateScheduledKey(key: String): IntArray {
        val sha512Digest = MessageDigest.getInstance("SHA-512").digest(key.toByteArray(Charsets.UTF_8))
        val s = IntArray(256) { it }
        var j = 0

        for (i in 255 downTo 1) {
            val shaByte = sha512Digest[i % sha512Digest.size].toInt() and 0xFF // Converte byte para int sem sinal
            j = (j + s[i] + shaByte) % (i + 1)
            val temp = s[i]
            s[i] = s[j]
            s[j] = temp
        }
        return s
    }

    private fun generateKeystream(length: Int, initialState: LongArray, scheduledKey: IntArray): KeystreamResult {
        val state = initialState.clone()
        val keystream = ByteArray(length)

        for (i in 0 until length) {
            state[0] = (state[0] + 2654435769L) and 0xFFFFFFFFL
            state[1] = (state[1] xor state[7]) and 0xFFFFFFFFL
            state[2] = (state[2] + state[0]) and 0xFFFFFFFFL
            state[3] = (state[3] xor state[1].rotateLeft(5)) and 0xFFFFFFFFL
            state[4] = (state[4] - state[2]) and 0xFFFFFFFFL

            val index = (state[7] and 255L).toInt()
            state[5] = (state[5] xor scheduledKey[index].toLong()) and 0xFFFFFFFFL

            val rotation = (state[0] and 31L).toInt()
            state[6] = (state[6] + state[3].rotateLeft(rotation)) and 0xFFFFFFFFL
            state[7] = (state[7] xor state[4]) and 0xFFFFFFFFL

            val keyByte = (state[0] xor state[2] xor state[5] xor state[7]) and 255L
            keystream[i] = keyByte.toByte()
        }
        return KeystreamResult(keystream, state)
    }

    private fun reverseAetherXORChain(data: ByteArray, initialValue: Int): ByteArray {
        if (data.isEmpty()) return ByteArray(0)

        val result = ByteArray(data.size)
        result[0] = (data[0].toInt() xor (initialValue and 0xFF)).toByte()

        for (i in 1 until data.size) {
            result[i] = data[i] xor result[i - 1]
        }
        return result
    }

    private fun Long.rotateLeft(bits: Int): Long {
        return ((this shl bits) or (this ushr (32 - bits))) and 0xFFFFFFFFL
    }
}
