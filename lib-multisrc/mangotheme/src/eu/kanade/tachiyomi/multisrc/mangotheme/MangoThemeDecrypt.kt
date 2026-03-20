package eu.kanade.tachiyomi.multisrc.mangotheme

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object MangoThemeDecrypt {

    private const val SALT = "salt"
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"

    fun decrypt(payload: String, keyString: String): String {
        val trimmedPayload = payload.trim()

        if (trimmedPayload.startsWith("{") || trimmedPayload.startsWith("[")) {
            return trimmedPayload
        }

        val parts = trimmedPayload.split(":", limit = 2)
        require(parts.size == 2) { "Invalid encrypted payload" }

        val keyBytes = MessageDigest.getInstance("SHA-256")
            .digest((keyString + SALT).toByteArray(Charsets.UTF_8))

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(keyBytes, ALGORITHM),
            IvParameterSpec(parts[0].hexToBytes()),
        )

        return String(cipher.doFinal(parts[1].hexToBytes()), Charsets.UTF_8)
    }

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0) { "Invalid hex string length" }

        return ByteArray(length / 2) { index ->
            substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}
