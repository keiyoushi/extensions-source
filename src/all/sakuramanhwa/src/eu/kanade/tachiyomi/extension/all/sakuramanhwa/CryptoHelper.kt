package eu.kanade.tachiyomi.extension.all.sakuramanhwa

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import keiyoushi.utils.toJsonString
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okio.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class CryptoHelper(
    private val baseUrl: String,
    private val secretKey: String,
    private val encryptKey: String,
) : Interceptor {
    private var client: OkHttpClient? = null
    fun setClient(c: OkHttpClient) {
        client = c
    }

    @Volatile
    private var serverTimeAtGeneration: Long? = null
    private var localTimeAtGeneration: Long? = null

    private val cacheValidityMillis = 4 * 60 * 1000 // 5min expiration

    private var cachedSignedData: CacheState? = null

    private class CacheState(val cachedSignedData: String, val cachedSigneTime: Long)

    @Synchronized
    fun initServerTime() {
        if (serverTimeAtGeneration != null) {
            return
        }

        val response =
            client!!.newCall(GET("$baseUrl/v1", Headers.Builder().set("NX", "").build())).execute()
        val serverTime = response.headers.getDate("Date")?.time
            ?: throw IOException("Uninitialized server date")

        this.localTimeAtGeneration = System.currentTimeMillis()
        this.serverTimeAtGeneration = serverTime
        this.cachedSignedData = null
    }

    @Synchronized
    fun generateSigned(): String {
        if (serverTimeAtGeneration == null) {
            throw Exception("Uninitialized time")
        }

        val currentTime = System.currentTimeMillis()
        if (cachedSignedData != null && currentTime - cachedSignedData!!.cachedSigneTime <= cacheValidityMillis) {
            return cachedSignedData!!.cachedSignedData
        }

        val timestamp = getCurrentServerTime(currentTime).toString()

        val dataToHash = mapOf(Pair("timesTamp", timestamp)).toJsonString()
        val hash = hmacSha256(dataToHash, secretKey)
        val dataToEncrypt = mapOf(Pair("hash", hash), Pair("timesTamp", timestamp)).toJsonString()

        val encrypted = AESCrypt.encrypt(dataToEncrypt, encryptKey)

        cachedSignedData = CacheState(
            encrypted,
            currentTime,
        )
        return cachedSignedData!!.cachedSignedData
    }

    fun hmacSha256(data: String, key: String): String {
        val secretKeySpec = SecretKeySpec(key.toByteArray(), "HmacSHA256")
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(secretKeySpec)
        val hashBytes = mac.doFinal(data.toByteArray())
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private fun getCurrentServerTime(currentLocalTime: Long): Long {
        val elapsedLocalMillis = currentLocalTime - localTimeAtGeneration!!
        return serverTimeAtGeneration!! + elapsedLocalMillis
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        if (request.headers["NX"] == null && serverTimeAtGeneration == null) {
            initServerTime()
        }
        if (request.headers["NX"] == null && request.url.toString().startsWith(baseUrl)) {
            request = request.newBuilder().apply {
                header("Referer", "$baseUrl/")
                header("St-soon", generateSigned())
            }.build()
        }
        return chain.proceed(request)
    }

    private object AESCrypt {
        private const val SALTED_PREFIX = "Salted__"
        private const val KEY_SIZE = 32
        private const val IV_SIZE = 16
        private const val SALT_SIZE = 8
        private const val MD5_ALGORITHM = "MD5"
        private const val AES_ALGORITHM = "AES"
        private const val CIPHER_TRANSFORMATION = "AES/CBC/PKCS5Padding"

        fun encrypt(value: String, key: String): String {
            val salt = ByteArray(SALT_SIZE).apply {
                SecureRandom().nextBytes(this)
            }

            val (keyBytes, iv) = deriveKeyAndIV(key, salt)

            val secretKeySpec = SecretKeySpec(keyBytes, AES_ALGORITHM)
            val ivParameterSpec = IvParameterSpec(iv)

            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec)

            val encrypted = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))

            val resultBytes = ByteArray(SALTED_PREFIX.length + SALT_SIZE + encrypted.size)

            SALTED_PREFIX.toByteArray().copyInto(resultBytes)
            salt.copyInto(resultBytes, SALTED_PREFIX.length)
            encrypted.copyInto(resultBytes, SALTED_PREFIX.length + SALT_SIZE)

            return Base64.encodeToString(resultBytes, Base64.NO_WRAP)
        }

        fun decrypt(encrypted: String, key: String): String {
            val encryptedBytes = Base64.decode(encrypted, Base64.NO_WRAP)

            val salt = ByteArray(SALT_SIZE).also {
                encryptedBytes.copyInto(
                    it,
                    0,
                    SALTED_PREFIX.length,
                    SALTED_PREFIX.length + SALT_SIZE,
                )
            }

            val (keyBytes, iv) = deriveKeyAndIV(key, salt)

            val secretKeySpec = SecretKeySpec(keyBytes, AES_ALGORITHM)
            val ivParameterSpec = IvParameterSpec(iv)

            val cipher = Cipher.getInstance(CIPHER_TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec)

            val ciphertext = ByteArray(encryptedBytes.size - (SALTED_PREFIX.length + SALT_SIZE))
            encryptedBytes.copyInto(ciphertext, 0, SALTED_PREFIX.length + SALT_SIZE)

            val decrypted = cipher.doFinal(ciphertext)

            return String(decrypted, StandardCharsets.UTF_8)
        }

        private fun deriveKeyAndIV(key: String, salt: ByteArray): Pair<ByteArray, ByteArray> {
            val keyBytes = key.toByteArray(StandardCharsets.UTF_8)
            val result = ByteArray(KEY_SIZE + IV_SIZE)
            val md5 = MessageDigest.getInstance(MD5_ALGORITHM)

            var currentResult = md5.digest(keyBytes + salt)
            currentResult.copyInto(result, 0, 0, currentResult.size)

            var keyMaterial = currentResult.size
            while (keyMaterial < result.size) {
                currentResult = md5.digest(currentResult + keyBytes + salt)
                val copyLength = minOf(currentResult.size, result.size - keyMaterial)
                currentResult.copyInto(result, keyMaterial, 0, copyLength)
                keyMaterial += copyLength
            }

            return Pair(
                result.copyOfRange(0, KEY_SIZE),
                result.copyOfRange(KEY_SIZE, KEY_SIZE + IV_SIZE),
            )
        }
    }
}
