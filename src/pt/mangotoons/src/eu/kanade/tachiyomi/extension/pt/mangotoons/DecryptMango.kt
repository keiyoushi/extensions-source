package eu.kanade.tachiyomi.extension.pt.mangotoons

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object DecryptMango {

    private const val SALT = "salt"
    private const val KEY_STRING = "abmPisXlFjOLVTnYhbYQTpkWJtOGKwVttzLqstfjRBNVaEtQYF"
    private const val ALGORITHM = "AES"
    private const val TRANSFORMATION = "AES/CBC/PKCS5Padding"

    private val KEY_BYTES: ByteArray by lazy {
        MessageDigest.getInstance("SHA-256")
            .digest((KEY_STRING + SALT).toByteArray(Charsets.UTF_8))
    }

    fun decrypt(encrypted: String): String {
        val parts = encrypted.split(":")
        if (parts.size != 2) {
            if (encrypted.startsWith("{") || encrypted.startsWith("[")) return encrypted
            throw Exception("Invalid encrypted format")
        }

        val ivHex = parts[0]
        val ciphertextHex = parts[1]

        val iv = hexToBytes(ivHex)
        val ciphertext = hexToBytes(ciphertextHex)

        val secretKeySpec = SecretKeySpec(KEY_BYTES, ALGORITHM)
        val ivParameterSpec = IvParameterSpec(iv)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, ivParameterSpec)

        val decryptedBytes = cipher.doFinal(ciphertext)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = (
                (Character.digit(hex[i], 16) shl 4) +
                    Character.digit(hex[i + 1], 16)
                ).toByte()
            i += 2
        }
        return data
    }
}
