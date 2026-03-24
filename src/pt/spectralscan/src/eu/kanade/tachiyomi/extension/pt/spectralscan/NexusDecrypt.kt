package eu.kanade.tachiyomi.extension.pt.spectralscan

import android.util.Base64
import keiyoushi.utils.parseAs
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import java.security.MessageDigest

object NexusDecrypt {

    private const val CRYPTO_SECRET = "OrionNexus2025CryptoKey!Secure"
    private const val NUM_KEYS = 5

    private var keys: List<KeyData>? = null
    private var initialized = false

    private data class KeyData(
        val key: ByteArray,
        val sbox: IntArray,
        val rsbox: IntArray,
    )

    @Synchronized
    private fun initialize() {
        if (initialized) return

        val derivedKeys = mutableListOf<KeyData>()

        for (i in 0 until NUM_KEYS) {
            val pattern = "_orion_key_${i}_v2_$CRYPTO_SECRET"
            val hash = MessageDigest.getInstance("SHA-256").digest(pattern.toByteArray())
            val hexKey = hash.joinToString("") { "%02x".format(it) }

            val keyBytes = hexKey.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val keyData = KeyData(
                key = keyBytes,
                sbox = IntArray(256),
                rsbox = IntArray(256),
            )
            initSBoxForKey(keyData)
            derivedKeys.add(keyData)
        }

        keys = derivedKeys
        initialized = true
    }

    private fun initSBoxForKey(keyData: KeyData) {
        val key = keyData.key

        for (i in 0 until 256) {
            keyData.sbox[i] = i
        }

        var j = 0
        for (i in 0 until 256) {
            j = (j + keyData.sbox[i] + (key[i % key.size].toInt() and 0xFF)) % 256
            val temp = keyData.sbox[i]
            keyData.sbox[i] = keyData.sbox[j]
            keyData.sbox[j] = temp
        }

        for (i in 0 until 256) {
            keyData.rsbox[keyData.sbox[i]] = i
        }
    }

    private fun rotateRight(byte: Int, shift: Int): Int {
        val s = shift % 8
        return ((byte ushr s) or (byte shl (8 - s))) and 0xFF
    }

    private fun decrypt(keyIndex: Int, base64Data: String): String {
        initialize()

        val keysList = keys ?: throw IllegalStateException("OrionCrypto not initialized")

        if (keyIndex < 0 || keyIndex >= NUM_KEYS) {
            throw IllegalArgumentException("Invalid key index: $keyIndex")
        }

        val keyData = keysList[keyIndex]
        val key = keyData.key
        val rsbox = keyData.rsbox

        val input = Base64.decode(base64Data, Base64.DEFAULT)
        val output = ByteArray(input.size)
        val keyLen = key.size

        for (i in input.size - 1 downTo 0) {
            var byte = input[i].toInt() and 0xFF

            byte = if (i > 0) {
                byte xor (input[i - 1].toInt() and 0xFF)
            } else {
                byte xor (key[keyLen - 1].toInt() and 0xFF)
            }

            byte = rsbox[byte]

            val rotAmount = ((key[(i + 3) % keyLen].toInt() and 0xFF) + (i and 0xFF) and 0xFF) % 7 + 1

            byte = rotateRight(byte, rotAmount)

            byte = byte xor (key[i % keyLen].toInt() and 0xFF)

            output[i] = byte.toByte()
        }

        return String(output, Charsets.UTF_8)
    }

    fun isEncryptedResponse(body: String): Boolean = try {
        val enc = body.parseAs<EncryptedResponse>()
        (enc.v == 1 || enc.v == 2)
    } catch (_: Exception) {
        false
    }

    fun decryptResponse(body: String): String {
        val enc = body.parseAs<EncryptedResponse>()
        val keyIndex = if (enc.v == 1) 0 else enc.k
        return decrypt(keyIndex, enc.d)
    }

    fun createInterceptor(): Interceptor {
        return Interceptor { chain ->
            val response = chain.proceed(chain.request())

            val contentType = response.header("Content-Type")
            if (contentType == null || !contentType.contains("application/json")) {
                return@Interceptor response
            }

            val body = response.body?.string() ?: return@Interceptor response

            val decryptedBody = if (isEncryptedResponse(body)) {
                try {
                    decryptResponse(body)
                } catch (e: Exception) {
                    body
                }
            } else {
                body
            }

            response.newBuilder()
                .body(decryptedBody.toResponseBody("application/json".toMediaType()))
                .build()
        }
    }
}
