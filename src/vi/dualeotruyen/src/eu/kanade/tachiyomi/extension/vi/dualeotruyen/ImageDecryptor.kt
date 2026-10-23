package eu.kanade.tachiyomi.extension.vi.dualeotruyen

import android.util.Base64
import keiyoushi.utils.hmacSha256
import keiyoushi.utils.sha256
import org.jsoup.nodes.Document

class ImageDecryptor {

    private var cachedSecret = "dualeo_url_v5_M4nP_2026_Z7cL"

    fun extractSecret(document: Document) {
        document.select("script").firstNotNullOfOrNull { script ->
            secretRegex.find(script.data())?.groupValues?.get(1)
        }?.let { cachedSecret = it }
    }

    fun decryptImageUrl(url: String): String {
        val lastSlashIndex = url.lastIndexOf('/')
        val dotIndex = url.lastIndexOf('.')
        if (lastSlashIndex == -1 || dotIndex == -1 || dotIndex <= lastSlashIndex) return url

        val basePath = url.substring(0, lastSlashIndex + 1)
        val encodedName = url.substring(lastSlashIndex + 1, dotIndex)
        val extension = url.substring(dotIndex)

        return when {
            encodedName.startsWith("q5_") ->
                decodeV5(basePath, encodedName.substring(3), extension, cachedSecret) ?: url
            encodedName.startsWith("q4_") ->
                decodeV4(basePath, encodedName.substring(3), extension, cachedSecret) ?: url
            else -> decodeLegacy(basePath, encodedName, extension) ?: url
        }
    }

    private fun decodeLegacy(basePath: String, encodedName: String, extension: String): String? {
        val base64 = encodedName.replace('-', '+').replace('_', '/')
        val decoded = try {
            Base64.decode(base64, Base64.DEFAULT)
        } catch (_: Exception) {
            return null
        }

        val decrypted = ByteArray(decoded.size) { i ->
            (decoded[i].toInt() xor decryptSalt[i % decryptSalt.length].code).toByte()
        }

        val decryptedName = String(decrypted, Charsets.UTF_8)
        return "$basePath$decryptedName$extension"
    }

    private fun decodeV5(
        basePath: String,
        encoded: String,
        extension: String,
        secret: String,
    ): String? {
        return runCatching {
            val payload = String(base64UrlDecode(encoded), Charsets.ISO_8859_1)
            val parts = payload.split('|')
            if (parts.size != 5 || parts[0] != "V5") return null

            val nonce = parts[1]
            val salt = parts[2]
            val mac = parts[3]
            val cipherB64 = parts[4]
            if (nonce.isEmpty() || salt.isEmpty() || mac.isEmpty() || cipherB64.isEmpty()) {
                return null
            }

            val cipherBytes = Base64.decode(cipherB64, Base64.DEFAULT)

            val stream = ByteArray(cipherBytes.size)
            var offset = 0
            var counter = 0
            while (offset < stream.size) {
                val block = "$secret|STREAM|$nonce|$salt|$counter".sha256()
                val count = minOf(block.size, stream.size - offset)
                System.arraycopy(block, 0, stream, offset, count)
                offset += count
                counter++
            }

            val nameBytes = ByteArray(cipherBytes.size) { i ->
                (cipherBytes[i].toInt() xor (stream[i].toInt() and 0xFF)).toByte()
            }
            val name = String(nameBytes, Charsets.UTF_8)

            val expectedMac = "V5|$nonce|$salt|$name".hmacSha256(secret.encodeToByteArray()).toHexString()
            if (!expectedMac.equals(mac, ignoreCase = true)) {
                return null
            }

            "$basePath$name$extension"
        }.getOrNull()
    }

    private fun base64UrlDecode(str: String): ByteArray {
        val padded = buildString(str.length + 3) {
            append(str.replace('-', '+').replace('_', '/'))
            while (length % 4 != 0) {
                append('=')
            }
        }
        return Base64.decode(padded, Base64.DEFAULT)
    }

    private fun decodeV4(
        basePath: String,
        encoded: String,
        extension: String,
        secret: String,
    ): String? {
        return runCatching {
            val payloadBytes = base64UrlDecode(encoded)
            val payload = String(payloadBytes, Charsets.ISO_8859_1)
            val parts = payload.split('|')
            if (parts.size != 5 || parts[0] != "V4") return null

            val checksum = parts[1]
            val nonce = parts[2]
            val salt = parts[3]
            val rawB64 = parts[4]

            if (checksum.isEmpty() || nonce.isEmpty() || salt.isEmpty() || rawB64.isEmpty()) {
                return null
            }

            val k1 = "$secret|A|$nonce|$salt".sha256()
            val k1Hex = k1.toHexString()
            val k2 = "$secret|B|$salt|$nonce|$k1Hex".sha256()
            val key = k1 + k2

            val rawBytes = base64UrlDecode(rawB64)
            rawBytes.reverse()

            val nameBytes = ByteArray(rawBytes.size) { i ->
                (rawBytes[i].toInt() xor (key[i % key.size].toInt() and 0xFF)).toByte()
            }
            val name = String(nameBytes, Charsets.UTF_8)

            val verify = "V4|$secret|$nonce|$salt|$name".sha256()
            val verifyHex = verify.toHexString()
            if (!verifyHex.startsWith(checksum)) {
                return null
            }

            "$basePath$name$extension"
        }.getOrNull()
    }

    private val secretRegex = """OBF_SECRET\s*=\s*['"]([^'"]+)['"]""".toRegex()
    private val decryptSalt = "dualeo_salt_2025"
}
