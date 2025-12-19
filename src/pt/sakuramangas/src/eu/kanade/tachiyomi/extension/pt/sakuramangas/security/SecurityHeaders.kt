package eu.kanade.tachiyomi.extension.pt.sakuramangas.security

import android.util.Base64
import okhttp3.Headers
import okio.IOException

/**
 * Security headers and proof generation for SakuraMangas API requests.
 */
object SecurityHeaders {

    /**
     * Generates proof hash using FNV-1a + MurmurHash2 hybrid algorithm.
     * Replicates the obfuscated JavaScript algorithm.
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

            // FNV-1a hash
            var hash = 2166136261L
            for (char in input) {
                hash = hash xor char.code.toLong()
                hash = (hash * 16777619L) and 0xFFFFFFFFL
            }

            // Initialize with magic constants
            var v1 = hash xor 0xCAFEBABEL
            var v2 = hash xor 0xDEADBEEFL
            var v3 = hash xor 0xBADDCAF1L
            var v4 = hash xor 0xDEADBFFEL

            // MurmurHash2-style mixing
            val m = 0x5BD1E995L
            val r = 24

            repeat(20) {
                v1 = ((v1 * m) and 0xFFFFFFFFL)
                v1 = v1 xor (v1 ushr r)
                v1 = ((v1 * m) and 0xFFFFFFFFL)

                v2 = ((v2 * m) and 0xFFFFFFFFL)
                v2 = v2 xor (v2 ushr r)
                v2 = ((v2 * m) and 0xFFFFFFFFL)

                v3 = ((v3 xor v1) * m) and 0xFFFFFFFFL
                v4 = ((v4 xor v2) * m) and 0xFFFFFFFFL
            }

            // Final mix
            v1 = v1 xor (v1 ushr 13)
            v1 = ((v1 * m) and 0xFFFFFFFFL)
            v1 = v1 xor (v1 ushr 15)

            v2 = v2 xor (v2 ushr 13)
            v2 = ((v2 * m) and 0xFFFFFFFFL)
            v2 = v2 xor (v2 ushr 15)

            v3 = v3 xor (v3 ushr 13)
            v3 = ((v3 * m) and 0xFFFFFFFFL)
            v3 = v3 xor (v3 ushr 15)

            v4 = v4 xor (v4 ushr 13)
            v4 = ((v4 * m) and 0xFFFFFFFFL)
            v4 = v4 xor (v4 ushr 15)

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
