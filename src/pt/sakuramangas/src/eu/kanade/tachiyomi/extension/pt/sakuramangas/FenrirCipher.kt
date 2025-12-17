package eu.kanade.tachiyomi.extension.pt.sakuramangas

import android.util.Base64
import java.security.MessageDigest

/**
 * Fenrir cipher implementation for SakuraMangas chapter decryption.
 *
 * Algorithm:
 * 1. SHA-512 of key to get 64 hash bytes
 * 2. Base64 decode payload to get encrypted data
 * 3. XOR with chained feedback: result[i] = (data[i] ^ hash[i % 64] ^ prevByte) & 0xFF
 *    where prevByte starts as hash[0] and becomes data[i] after each iteration
 */
object FenrirCipher {

    fun decrypt(payload: String, key: String): String {
        val sha512Bytes = MessageDigest.getInstance("SHA-512")
            .digest(key.toByteArray(Charsets.UTF_8))

        val hashBytes = sha512Bytes.map { it.toInt() and 0xFF }
        val decodedBytes = Base64.decode(payload, Base64.DEFAULT)

        val result = StringBuilder()
        var prevByte = hashBytes[0]

        for (i in decodedBytes.indices) {
            val dataByte = decodedBytes[i].toInt() and 0xFF
            val keyByte = hashBytes[i % hashBytes.size]
            val decryptedByte = (dataByte xor keyByte xor prevByte) and 0xFF
            result.append(decryptedByte.toChar())
            prevByte = dataByte
        }

        return result.toString()
    }
}
