package eu.kanade.tachiyomi.extension.pt.sakuramangas.security

import android.util.Base64
import okhttp3.Headers
import okio.IOException
import kotlin.math.cos
import kotlin.math.sin

/**
 * Security headers and proof generation for SakuraMangas API requests.
 */
object SecurityHeaders {

    /**
     * Generates proof hash using FNV-1a based algorithm with 23 sin/cos iterations.
     * Replicates the JavaScript security.ksr.js algorithm exactly.
     */
    fun generateHeaderProof(challenge: String?, securityKey: Long?, userAgent: String?): String? {
        if (challenge == null || securityKey == null || userAgent == null) {
            return null
        }

        return try {
            val decoded = String(Base64.decode(challenge, Base64.DEFAULT), Charsets.UTF_8)
            val parts = decoded.split('/')

            if (parts.size != 3) return null

            val input = parts[0] + userAgent + securityKey + parts[2]

            android.util.Log.d("SakuraMangas", "Proof input string (len=${input.length}): $input")

            var hash = 2166136261L
            for (char in input) {
                hash = hash xor char.code.toLong()
                hash = ((hash * 16777619L) and 0xFFFFFFFFL)
                if (hash >= 0x80000000L) hash -= 0x100000000L
            }

            var v1 = hash
            var v2 = hash xor 1431655765L
            var v3 = hash xor -1431655766L
            var v4 = hash xor 858993459L

            repeat(23) { i ->
                val sin1 = sin((v1 + i).toDouble()) * 1337.5
                val cos1 = cos((v2 + i).toDouble()) * 1337.5

                val t1 = sin1.toLong() xor v3
                val t2 = cos1.toLong() xor v4

                val t1u = t1 and 0xFFFFFFFFL
                val t2u = t2 and 0xFFFFFFFFL

                v1 = ((t1u shl 13) or (t1u ushr 19)) and 0xFFFFFFFFL
                v2 = ((t2u ushr 7) or (t2u shl 25)) and 0xFFFFFFFFL

                if (v1 >= 0x80000000L) v1 -= 0x100000000L
                if (v2 >= 0x80000000L) v2 -= 0x100000000L

                v3 = (v3 + v1) and 0xFFFFFFFFL
                v4 = (v4 + v2) and 0xFFFFFFFFL
                if (v3 >= 0x80000000L) v3 -= 0x100000000L
                if (v4 >= 0x80000000L) v4 -= 0x100000000L
            }

            "%08x%08x%08x%08x".format(
                v1 and 0xFFFFFFFFL,
                v2 and 0xFFFFFFFFL,
                v3 and 0xFFFFFFFFL,
                v4 and 0xFFFFFFFFL,
            )
        } catch (e: Exception) {
            throw IOException("Failed to generate header proof: ${e.message}")
        }
    }

    /** Builds AJAX headers with security tokens */
    fun buildSecuredAjaxHeaders(
        baseHeaders: Headers.Builder,
        keys: SecurityConfig.Keys,
        csrfToken: String,
    ): Headers = baseHeaders
        .add("X-Client-Signature", SecurityConfig.CLIENT_SIGNATURE)
        .add("X-Verification-Key-1", keys.xVerificationKey1)
        .add("X-Verification-Key-2", keys.xVerificationKey2)
        .add("X-CSRF-Token", csrfToken)
        .build()

    /** XOR decryption for encrypted chapter data */
    fun xorDecrypt(encrypted: String, key: String): String {
        val decoded = Base64.decode(encrypted, Base64.DEFAULT)
        val result = StringBuilder()
        for (i in decoded.indices) {
            val dataByte = decoded[i].toInt() and 0xFF
            val keyByte = key[i % key.length].code and 0xFF
            result.append((dataByte xor keyByte).toChar())
        }
        return result.toString()
    }
}
