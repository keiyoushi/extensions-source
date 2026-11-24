package eu.kanade.tachiyomi.extension.zh.vomic

import android.util.Base64
import okhttp3.Interceptor
import okhttp3.Response
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Locale

class SignInterceptor(private val apiHost: String) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (request.url.host != apiHost) return chain.proceed(request)

        val timestamp = request.header("t")
            ?: (System.currentTimeMillis() / 1000).toString()

        val params = request.url.queryParameterNames.associateWith { name ->
            request.url.queryParameterValues(name).lastOrNull().orEmpty()
        }

        val payload = params.toSortedMap()
            .filterValues(String::isNotEmpty)
            .entries.joinToString("&") { (key, value) -> "$key=$value" }

        val encoded = Base64.encodeToString(
            "$payload$timestamp$SALT".toByteArray(UTF_8),
            Base64.NO_WRAP,
        )

        val signature = hash(encoded, timestamp)

        return chain.proceed(
            request.newBuilder()
                .header("t", timestamp)
                .header("s", signature)
                .build(),
        )
    }

    private fun hash(data: String, timestamp: String): String {
        val key = SALT.toByteArray(UTF_8)
        var hash = INITIAL_HASH

        for (byte in key) {
            hash = Integer.rotateLeft(hash xor (byte.toInt() and 0xFF), 7)
        }

        for ((i, byte) in "$data$timestamp$SALT".toByteArray(UTF_8).withIndex()) {
            val k = key[i % key.size].toInt() and 0xFF
            val mix = if (i % 2 == 0) k * 8 else k shr 1

            hash = Integer.rotateLeft(
                hash xor (byte.toInt() and 0xFF),
                5,
            ) xor mix
        }

        for (byte in key) {
            hash = Integer.rotateLeft(
                hash xor ((byte.toInt() and 0xFF) * 16),
                9,
            )
        }

        return String.format(Locale.ROOT, "%08x", hash)
    }

    private companion object {
        const val SALT = "hhhasda"
        const val INITIAL_HASH = -1640531527
    }
}
