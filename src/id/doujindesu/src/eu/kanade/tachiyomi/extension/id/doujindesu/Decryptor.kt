package eu.kanade.tachiyomi.extension.id.doujindesu

import keiyoushi.utils.parseAs
import okhttp3.Interceptor
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.math.abs

class Decryptor(val apiUrl: String) {

    fun xorInterceptor() = Interceptor { chain ->
        val request = chain.request()
        if (!request.url.toString().contains(apiUrl)) return@Interceptor chain.proceed(request)

        val response = chain.proceed(request)

        val dto = response.parseAs<EncryptedDto>()
        val decryptedJson = decrypt(dto.encrypted) ?: throw IOException("Decryption failed")

        response.newBuilder()
            .body(decryptedJson.toResponseBody(response.body.contentType()))
            .build()
    }

    // index-*.js
    fun toSigned32(x: Long): Int {
        var y = x and 0xFFFFFFFFL
        if (y >= 0x80000000L) y -= 0x100000000L
        return y.toInt()
    }

    fun wH(e: Int): String {
        val t = SALT + "_" + e
        var a = 0
        for (ch in t) {
            a = (a shl 5) - a + ch.code
            a = toSigned32(a.toLong())
        }
        var l = if (a != 0) abs(a).toLong() else 123456789L
        return buildString {
            repeat(32) {
                l = (l * 1664525L + 1013904223L) % 4294967296L
                append((33 + (l % 93)).toInt().toChar())
            }
        }
    }

    fun lU(): List<String> {
        val now = System.currentTimeMillis()
        val t = now / HOUR_MS
        return listOf(wH(t.toInt()), wH((t - 1).toInt()), wH((t + 1).toInt()))
    }

    fun yre(encryptedHex: String, key: String): String {
        val bytes = encryptedHex.chunked(2).mapNotNull { it.toIntOrNull(16) }
        var d = 42
        val keyLen = key.length
        return buildString {
            for ((idx, byteVal) in bytes.withIndex()) {
                val keyChar = key[idx % keyLen].code
                val k = byteVal xor keyChar xor (idx * 13) xor d
                append((k and 0xFF).toChar())
                d = (d + byteVal) % 256
            }
        }
    }

    fun decrypt(encryptedHex: String): String? {
        for (key in lU()) {
            try {
                val decoded = yre(encryptedHex, key)
                return URLDecoder.decode(decoded, StandardCharsets.UTF_8.name())
            } catch (_: Exception) {
            }
        }
        return null
    }

    companion object {
        private const val SALT = "doujindesu-scrapers-cannot-read-this-super-secret-salt-2026-v2"
        private const val HOUR_MS = 3600000L
    }
}
