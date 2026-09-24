package eu.kanade.tachiyomi.extension.uk.faust

import keiyoushi.utils.jsonInstance
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class AuthInterceptor(
    private val clientProvider: () -> OkHttpClient,
    private val baseUrl: String,
) : Interceptor {

    private val domain by lazy { baseUrl.toHttpUrl().host }

    private val secretBytes by lazy {
        try {
            val b64 = SIGNING_SECRET_B64.replace(Regex("\\s+"), "").replace("-", "+").replace("_", "/")
            val padded = b64.padEnd(b64.length + ((4 - (b64.length % 4)) % 4), '=')
            Base64.getDecoder().decode(padded).toString(Charsets.UTF_8).trim().toByteArray(Charsets.UTF_8)
        } catch (_: Exception) {
            byteArrayOf()
        }
    }

    private val secretKeySpec by lazy { SecretKeySpec(secretBytes, "HmacSHA256") }

    private var accessToken: String? = null
    private var tokenExpiresAt: Long = 0L
    private var isRefreshing: Boolean = false

    override fun intercept(chain: Interceptor.Chain): Response {
        var originalRequest = chain.request()
        val url = originalRequest.url

        if (url.host != domain || !url.encodedPath.startsWith("/api")) {
            return chain.proceed(originalRequest)
        }

        val currentToken = getValidAccessToken()
        val timestamp = Instant.now().epochSecond.toString()
        val pathAndQuery = url.encodedPath + if (url.encodedQuery != null) "?${url.encodedQuery}" else ""
        val signature = hmacSha256("$timestamp:$pathAndQuery")

        val requestBuilder = originalRequest.newBuilder()
            .header(HEADER_TIMESTAMP, timestamp)
            .header(HEADER_SIGN, signature)
            .header(HEADER_OBFUSCATION, OBFUSCATION_MODE)

        if (!currentToken.isNullOrEmpty() && !url.encodedPath.contains("/authentication/")) {
            requestBuilder.header("Authorization", "Bearer $currentToken")
        }

        originalRequest = requestBuilder.build()
        var response = chain.proceed(originalRequest)

        // Handle 401 Unauthorized -> Refresh token and retry once
        if (response.code == 401 && !url.encodedPath.contains("/authentication/")) {
            response.close()
            synchronized(this) {
                val freshToken = refreshAccessToken()
                if (!freshToken.isNullOrEmpty()) {
                    val retryTimestamp = Instant.now().epochSecond.toString()
                    val retrySignature = hmacSha256("$retryTimestamp:$pathAndQuery")
                    val retryRequest = originalRequest.newBuilder()
                        .header(HEADER_TIMESTAMP, retryTimestamp)
                        .header(HEADER_SIGN, retrySignature)
                        .header("Authorization", "Bearer $freshToken")
                        .build()
                    response = chain.proceed(retryRequest)
                }
            }
        }

        // Handle XOR obfuscation response
        if (response.header(HEADER_RESP_OBFUSCATED)?.lowercase() == OBFUSCATION_MODE) {
            val responseTs = response.header(HEADER_RESP_TIMESTAMP)
                ?: response.request.header(HEADER_TIMESTAMP)
                ?: timestamp
            val responseBodyBytes = response.body.bytes()
            val decodedBytes = xorDecrypt(responseBodyBytes, responseTs, secretBytes)
            val decryptedResponseBody = decodedBytes.toResponseBody("application/json; charset=utf-8".toMediaType())

            return response.newBuilder()
                .removeHeader(HEADER_RESP_OBFUSCATED)
                .removeHeader(HEADER_RESP_TIMESTAMP)
                .header("Content-Type", "application/json; charset=utf-8")
                .body(decryptedResponseBody)
                .build()
        }

        return response
    }

    private fun getValidAccessToken(): String? {
        synchronized(this) {
            if (!accessToken.isNullOrEmpty() && Instant.now().epochSecond < tokenExpiresAt - 60) {
                return accessToken
            }
            return refreshAccessToken()
        }
    }

    private fun refreshAccessToken(): String? {
        if (isRefreshing) return accessToken
        isRefreshing = true
        try {
            val url = "$baseUrl/api/authentication/refresh"
            val timestamp = Instant.now().epochSecond.toString()
            val pathAndQuery = "/api/authentication/refresh"
            val signature = hmacSha256("$timestamp:$pathAndQuery")

            val request = Request.Builder()
                .url(url)
                .post("{}".toRequestBody("application/json".toMediaType()))
                .header(HEADER_TIMESTAMP, timestamp)
                .header(HEADER_SIGN, signature)
                .header(HEADER_OBFUSCATION, OBFUSCATION_MODE)
                .header("Content-Type", "application/json")
                .build()

            val client = clientProvider()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyBytes = response.body.bytes()
                    val bodyString = if (response.header(HEADER_RESP_OBFUSCATED)?.lowercase() == OBFUSCATION_MODE) {
                        val respTs = response.header(HEADER_RESP_TIMESTAMP) ?: timestamp
                        String(xorDecrypt(bodyBytes, respTs, secretBytes), Charsets.UTF_8)
                    } else {
                        String(bodyBytes, Charsets.UTF_8)
                    }

                    val tokenDto = jsonInstance.decodeFromString<AuthResponseDto>(bodyString)
                    if (tokenDto.token.isNotEmpty()) {
                        accessToken = tokenDto.token
                        tokenExpiresAt = decodeJwtExpiration(tokenDto.token)
                        return accessToken
                    }
                }
            }
        } catch (_: Exception) {} finally {
            isRefreshing = false
        }
        return null
    }

    private fun decodeJwtExpiration(token: String): Long = try {
        val parts = token.split(".")
        if (parts.size > 1) {
            val base64Payload = parts[1].replace("-", "+").replace("_", "/")
            val padded = base64Payload.padEnd(base64Payload.length + ((4 - (base64Payload.length % 4)) % 4), '=')
            val payloadJson = String(Base64.getDecoder().decode(padded), Charsets.UTF_8)
            val jsonObject = jsonInstance.decodeFromString<JsonObject>(payloadJson)
            jsonObject["exp"]?.jsonPrimitive?.content?.toLong() ?: (Instant.now().epochSecond + 3600)
        } else {
            Instant.now().epochSecond + 3600
        }
    } catch (_: Exception) {
        Instant.now().epochSecond + 3600
    }

    private fun hmacSha256(data: String): String {
        if (secretBytes.isEmpty()) return ""
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(secretKeySpec)
        return mac.doFinal(data.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun xorDecrypt(cipherBytes: ByteArray, timestamp: String, secret: ByteArray): ByteArray {
        if (secret.isEmpty()) return cipherBytes
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("$timestamp:".toByteArray(Charsets.UTF_8))
        digest.update(secret)
        val key = digest.digest()

        val decrypted = ByteArray(cipherBytes.size)
        for (i in cipherBytes.indices) {
            decrypted[i] = (cipherBytes[i].toInt() xor key[i % key.size].toInt()).toByte()
        }
        return decrypted
    }

    companion object {
        private const val SIGNING_SECRET_B64 = "WmgrUEwlWlN9K3FqVyc0bTtIJ3ZtaEAjXSh3"
        private const val HEADER_SIGN = "X-Request-Sign"
        private const val HEADER_TIMESTAMP = "X-Request-Timestamp"
        private const val HEADER_OBFUSCATION = "X-Json-Obfuscation"
        private const val HEADER_RESP_OBFUSCATED = "X-Response-Obfuscated"
        private const val HEADER_RESP_TIMESTAMP = "X-Response-Timestamp"
        private const val OBFUSCATION_MODE = "xor-v1"
    }
}

@Serializable
class AuthResponseDto(
    val token: String,
)
