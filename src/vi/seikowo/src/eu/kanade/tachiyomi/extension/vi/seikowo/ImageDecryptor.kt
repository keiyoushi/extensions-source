package eu.kanade.tachiyomi.extension.vi.seikowo

import android.util.Base64
import keiyoushi.utils.aesDecrypt
import keiyoushi.utils.aesGcm
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream

object ImageDecryptor {
    private const val ENCRYPTION_KEY = "super_secret_key_for_manga_app_1"
    private const val IV_SIZE = 12

    fun decryptPayload(base64Payload: String): String {
        val combined = Base64.decode(base64Payload, Base64.DEFAULT)
        require(combined.size > IV_SIZE) { "Invalid payload: Missing IV" }

        val iv = combined.copyOfRange(0, IV_SIZE)
        val encrypted = combined.copyOfRange(IV_SIZE, combined.size)

        val compressed = encrypted.aesDecrypt(aesGcm(normalizedKeyBytes(), iv))
        return decompressGzip(compressed)
    }

    private fun normalizedKeyBytes(): ByteArray {
        val normalized = when {
            ENCRYPTION_KEY.length < 32 -> ENCRYPTION_KEY.padEnd(32, '0')
            ENCRYPTION_KEY.length > 32 -> ENCRYPTION_KEY.take(32)
            else -> ENCRYPTION_KEY
        }
        return normalized.toByteArray(StandardCharsets.UTF_8)
    }

    private fun decompressGzip(compressed: ByteArray): String {
        GZIPInputStream(ByteArrayInputStream(compressed)).use { gzipInput ->
            ByteArrayOutputStream().use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var read = gzipInput.read(buffer)
                while (read >= 0) {
                    output.write(buffer, 0, read)
                    read = gzipInput.read(buffer)
                }
                return output.toString(StandardCharsets.UTF_8.name())
            }
        }
    }
}
