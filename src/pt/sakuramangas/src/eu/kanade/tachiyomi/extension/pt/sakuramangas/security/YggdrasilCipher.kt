package eu.kanade.tachiyomi.extension.pt.sakuramangas.security

import android.util.Base64
import eu.kanade.tachiyomi.extension.pt.sakuramangas.EncryptedKeyDto
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sin

/**
 * Yggdrasil Cipher System.
 *
 * Credits: Implementation based on reverse engineering work by WebDitto.
 * @see https://github.com/WebDitto/mihon-extensions/blob/fix/pt/sakuramangas5/src/pt/sakuramangas/src/eu/kanade/tachiyomi/extension/pt/sakuramangas/YggdrasilCipher.kt
 */
object YggdrasilCipher {

    object CryptoUtils {
        private fun bufferToHex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }

        fun hexToBytes(hex: String): ByteArray {
            require(hex.length % 2 == 0) { "Hex string must have an even length." }
            return ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        }

        private fun digest(algorithm: String, input: String): String {
            val md = MessageDigest.getInstance(algorithm)
            return bufferToHex(md.digest(input.toByteArray(Charsets.UTF_8)))
        }

        fun sha256(input: String) = digest("SHA-256", input)
        fun sha512(input: String) = digest("SHA-512", input)
        fun md5(input: String) = digest("MD5", input)
    }

    // FREYA - MD5 + sin/log pseudo-random XOR
    private fun decryptFreya(encrypted: ByteArray, subtoken: String): String {
        val md5Hex = CryptoUtils.md5(subtoken)
        val seed = md5Hex.substring(0, 8).toLong(16).toDouble()

        val result = CharArray(encrypted.size)
        for (i in encrypted.indices) {
            val byteValue = encrypted[i].toInt() and 0xFF
            val t = seed + i.toDouble()
            val rand = floor(abs(sin(t) * ln(seed + 1.0)) * 256.0).toInt() and 0xFF
            result[i] = (byteValue xor rand).toChar()
        }
        return String(result)
    }

    // FENRIR - SHA-512 XOR + feedback chain
    private fun decryptFenrir(encrypted: ByteArray, subtoken: String): String {
        val sha512Hex = CryptoUtils.sha512(subtoken)
        val keyBytes = CryptoUtils.hexToBytes(sha512Hex)
        val keyLength = keyBytes.size

        var prevByte = keyBytes[0].toInt() and 0xFF
        val result = CharArray(encrypted.size)

        for (i in encrypted.indices) {
            val keyByte = keyBytes[i % keyLength].toInt() and 0xFF
            val encryptedByte = encrypted[i].toInt() and 0xFF
            val decryptedByte = (encryptedByte xor keyByte xor prevByte) and 0xFF
            result[i] = decryptedByte.toChar()
            prevByte = encryptedByte
        }
        return String(result)
    }

    // HEIMDALL - SHA-256 subtraction + bit permutation + XOR
    private fun decryptHeimdall(encrypted: ByteArray, subtoken: String): String {
        val sha256Hex = CryptoUtils.sha256(subtoken)
        val keyBytes = CryptoUtils.hexToBytes(sha256Hex)
        val keyLength = keyBytes.size

        fun permuteBits(value: Int): Int {
            var result = 0
            if (value and (1 shl 0) != 0) result = result or (1 shl 7)
            if (value and (1 shl 1) != 0) result = result or (1 shl 5)
            if (value and (1 shl 2) != 0) result = result or (1 shl 3)
            if (value and (1 shl 3) != 0) result = result or (1 shl 1)
            if (value and (1 shl 4) != 0) result = result or (1 shl 6)
            if (value and (1 shl 5) != 0) result = result or (1 shl 2)
            if (value and (1 shl 6) != 0) result = result or (1 shl 4)
            if (value and (1 shl 7) != 0) result = result or (1 shl 0)
            return result
        }

        val result = CharArray(encrypted.size)
        for (i in encrypted.indices) {
            var byteValue = encrypted[i].toInt() and 0xFF
            val subtractKey = keyBytes[(i + 1) % keyLength].toInt() and 0xFF
            byteValue = (byteValue - subtractKey + 256) % 256
            byteValue = permuteBits(byteValue)
            val xorKey = keyBytes[i % keyLength].toInt() and 0xFF
            result[i] = (byteValue xor xorKey).toChar()
        }
        return String(result)
    }

    // JORMUNGANDR - SHA-256 circular bit rotation
    private fun decryptJormungandr(encrypted: ByteArray, subtoken: String): String {
        val sha256Hex = CryptoUtils.sha256(subtoken)
        val keyBytes = CryptoUtils.hexToBytes(sha256Hex)
        val keyLength = keyBytes.size

        fun circularRotate(value: Int, shift: Int): Int {
            val shiftAmount = shift and 7
            if (shiftAmount == 0) return value
            return ((value shr shiftAmount) or (value shl (8 - shiftAmount))) and 0xFF
        }

        val result = ByteArray(encrypted.size)
        for (i in encrypted.indices) {
            val rotationAmount = (keyBytes[i % keyLength].toInt() and 0xFF) % 8
            result[i] = circularRotate(encrypted[i].toInt() and 0xFF, rotationAmount).toByte()
        }
        return String(result.map { (it.toInt() and 0xFF).toChar() }.toCharArray())
    }

    // LOKI - SHA-256 chunked reversal + XOR
    private fun decryptLoki(encrypted: ByteArray, subtoken: String): String {
        val sha256Hex = CryptoUtils.sha256(subtoken)
        val keyBytes = CryptoUtils.hexToBytes(sha256Hex)
        val firstByte = keyBytes[0].toInt() and 0xFF
        val chunkSize = (firstByte % 8) + 3

        val inputString = String(encrypted.map { (it.toInt() and 0xFF).toChar() }.toCharArray())

        val chunks = mutableListOf<String>()
        var i = 0
        while (i < inputString.length) {
            val end = minOf(i + chunkSize, inputString.length)
            chunks.add(inputString.substring(i, end))
            i += chunkSize
        }

        val processedString = StringBuilder()
        chunks.forEachIndexed { index, chunk ->
            processedString.append(if (index % 2 == 0) chunk.reversed() else chunk)
        }

        val result = CharArray(processedString.length)
        for (j in processedString.indices) {
            result[j] = (processedString[j].code xor subtoken[j % subtoken.length].code).toChar()
        }
        return String(result)
    }

    // NIDHOGG - SHA-512 multi-step transformation
    private fun decryptNidhogg(encrypted: ByteArray, subtoken: String): String {
        val sha512Hex = CryptoUtils.sha512(subtoken)
        val keyBytes = CryptoUtils.hexToBytes(sha512Hex)

        val result = CharArray(encrypted.size)
        for (i in encrypted.indices) {
            var b = encrypted[i].toInt() and 0xFF
            val k1 = keyBytes[i % 64].toInt() and 0xFF
            val k2 = keyBytes[(i + 16) % 64].toInt() and 0xFF

            b = b xor ((b shr 4) and 0x0F)
            b = (b - k1 + 256) and 0xFF
            b = b.inv() and 0xFF
            b = b xor k2
            b = ((b shr 3) or (b shl 5)) and 0xFF
            b = b xor k1

            result[i] = b.toChar()
        }
        return String(result)
    }

    // ODIN - SHA-256 + SHA-512 S-box permutation (RC4-like)
    private fun decryptOdin(encrypted: ByteArray, subtoken: String): String {
        val sha256Hex = CryptoUtils.sha256(subtoken)
        val keyBytes1 = CryptoUtils.hexToBytes(sha256Hex)

        val sBox = IntArray(256) { it }
        var j = 0
        for (i in 0 until 256) {
            j = (j + sBox[i] + (keyBytes1[i % keyBytes1.size].toInt() and 0xFF)) % 256
            val temp = sBox[i]
            sBox[i] = sBox[j]
            sBox[j] = temp
        }

        val inverseSbox = IntArray(256)
        for (i in 0 until 256) {
            inverseSbox[sBox[i]] = i
        }

        val sha512Hex = CryptoUtils.sha512(subtoken)
        val keyBytes2 = CryptoUtils.hexToBytes(sha512Hex)
        val keyLength2 = keyBytes2.size

        val result = CharArray(encrypted.size)
        for (i in encrypted.indices) {
            val xorResult = (encrypted[i].toInt() and 0xFF) xor (keyBytes2[i % keyLength2].toInt() and 0xFF)
            result[i] = inverseSbox[xorResult].toChar()
        }
        return String(result)
    }

    // SLEIPNIR - SHA-512 reverse shuffle permutation
    private fun decryptSleipnir(encrypted: ByteArray, subtoken: String): String {
        val inputString = String(encrypted.map { (it.toInt() and 0xFF).toChar() }.toCharArray())
        val length = inputString.length

        val sha512Hex = CryptoUtils.sha512(subtoken)
        val keyBytes = CryptoUtils.hexToBytes(sha512Hex)
        val keyLength = keyBytes.size

        val indices = IntArray(length) { it }
        for (i in length - 1 downTo 1) {
            val swapIndex = (keyBytes[i % keyLength].toInt() and 0xFF) % (i + 1)
            val temp = indices[i]
            indices[i] = indices[swapIndex]
            indices[swapIndex] = temp
        }

        val inversePermutation = IntArray(length)
        for (i in 0 until length) {
            inversePermutation[indices[i]] = i
        }

        val result = CharArray(length)
        for (i in 0 until length) {
            result[inversePermutation[i]] = inputString[i]
        }
        return String(result)
    }

    // THOR - SHA-256 32-bit word XOR + PKCS padding
    private fun decryptThor(encrypted: ByteArray, subtoken: String): String {
        val sha256Hex = CryptoUtils.sha256(subtoken)
        val keyBytes = CryptoUtils.hexToBytes(sha256Hex)

        val keyWordCount = keyBytes.size / 4
        val keyWords = IntArray(keyWordCount)
        for (i in 0 until keyWordCount) {
            val base = i * 4
            keyWords[i] = (keyBytes[base].toInt() and 0xFF) or
                ((keyBytes[base + 1].toInt() and 0xFF) shl 8) or
                ((keyBytes[base + 2].toInt() and 0xFF) shl 16) or
                ((keyBytes[base + 3].toInt() and 0xFF) shl 24)
        }
        val keyLen = keyWords.size

        val outBytes = encrypted.copyOf()
        var blockIndex = 0
        var i = 0
        while (i + 4 <= encrypted.size) {
            val word = (encrypted[i].toInt() and 0xFF) or
                ((encrypted[i + 1].toInt() and 0xFF) shl 8) or
                ((encrypted[i + 2].toInt() and 0xFF) shl 16) or
                ((encrypted[i + 3].toInt() and 0xFF) shl 24)

            val xored = word xor keyWords[blockIndex % keyLen]
            outBytes[i] = (xored and 0xFF).toByte()
            outBytes[i + 1] = ((xored ushr 8) and 0xFF).toByte()
            outBytes[i + 2] = ((xored ushr 16) and 0xFF).toByte()
            outBytes[i + 3] = ((xored ushr 24) and 0xFF).toByte()

            blockIndex++
            i += 4
        }

        var result = String(outBytes.map { (it.toInt() and 0xFF).toChar() }.toCharArray())

        // Remove PKCS padding
        if (result.isNotEmpty()) {
            val padLen = result.last().code
            if (padLen in 1..4 && result.length >= padLen) {
                var validPadding = true
                for (p in 1..padLen) {
                    if (result[result.length - p].code != padLen) {
                        validPadding = false
                        break
                    }
                }
                if (validPadding) {
                    result = result.dropLast(padLen)
                }
            }
        }
        return result
    }

    // BIFROST - SHA-256 byte-pair swap + XOR
    private fun decryptBifrost(encrypted: ByteArray, subtoken: String): String {
        val sha256Hex = CryptoUtils.sha256(subtoken)
        val keyBytes = CryptoUtils.hexToBytes(sha256Hex)
        val keyLen = keyBytes.size

        val result = StringBuilder()
        var i = 0
        while (i < encrypted.size) {
            val byte1 = encrypted[i].toInt() and 0xFF
            val byte2 = if (i + 1 < encrypted.size) encrypted[i + 1].toInt() and 0xFF else 0
            val key1 = keyBytes[i % keyLen].toInt() and 0xFF
            val key2 = keyBytes[(i + 1) % keyLen].toInt() and 0xFF

            result.append((byte2 xor key1).toChar())
            if (i + 1 < encrypted.size) {
                result.append((byte1 xor key2).toChar())
            }
            i += 2
        }
        return result.toString().replace(Regex("\u0000+$"), "")
    }

    // GJALLARHORN - SHA-512 XOR + running sum accumulator feedback
    private fun decryptGjallarhorn(encrypted: ByteArray, subtoken: String): String {
        val sha512Hex = CryptoUtils.sha512(subtoken)
        val keyBytes = CryptoUtils.hexToBytes(sha512Hex)
        val keyLength = keyBytes.size

        var accumulator = 0
        val result = CharArray(encrypted.size)

        for (i in encrypted.indices) {
            val keyByte = keyBytes[i % keyLength].toInt() and 0xFF
            val encryptedByte = encrypted[i].toInt() and 0xFF
            val decryptedByte = (encryptedByte xor keyByte xor accumulator) and 0xFF
            result[i] = decryptedByte.toChar()
            accumulator = (accumulator + encryptedByte) and 0xFF
        }
        return String(result)
    }

    val supportedCiphers = setOf(
        "BIFROST", "FENRIR", "FREYA", "GJALLARHORN", "HEIMDALL",
        "JORMUNGANDR", "LOKI", "NIDHOGG", "ODIN", "SLEIPNIR", "THOR",
    )

    // Deciphers encrypted ephemeral key using specified cipher
    fun decipher(encryptedEphemeralKey: EncryptedKeyDto, subtoken: String): String {
        val cipherName = encryptedEphemeralKey.cipher.uppercase()
        val payloadBytes = Base64.decode(encryptedEphemeralKey.payload, Base64.DEFAULT)

        return when (cipherName) {
            "BIFROST" -> decryptBifrost(payloadBytes, subtoken)
            "FENRIR" -> decryptFenrir(payloadBytes, subtoken)
            "FREYA" -> decryptFreya(payloadBytes, subtoken)
            "GJALLARHORN" -> decryptGjallarhorn(payloadBytes, subtoken)
            "HEIMDALL" -> decryptHeimdall(payloadBytes, subtoken)
            "JORMUNGANDR" -> decryptJormungandr(payloadBytes, subtoken)
            "LOKI" -> decryptLoki(payloadBytes, subtoken)
            "NIDHOGG" -> decryptNidhogg(payloadBytes, subtoken)
            "ODIN" -> decryptOdin(payloadBytes, subtoken)
            "SLEIPNIR" -> decryptSleipnir(payloadBytes, subtoken)
            "THOR" -> decryptThor(payloadBytes, subtoken)
            else -> error("New cipher: '$cipherName'.")
        }
    }

    // Overload for direct cipher name + payload
    fun decipher(cipherName: String, payload: String, subtoken: String): String? {
        val name = cipherName.uppercase()
        if (name !in supportedCiphers) return null

        val payloadBytes = Base64.decode(payload, Base64.DEFAULT)

        return when (name) {
            "BIFROST" -> decryptBifrost(payloadBytes, subtoken)
            "FENRIR" -> decryptFenrir(payloadBytes, subtoken)
            "FREYA" -> decryptFreya(payloadBytes, subtoken)
            "GJALLARHORN" -> decryptGjallarhorn(payloadBytes, subtoken)
            "HEIMDALL" -> decryptHeimdall(payloadBytes, subtoken)
            "JORMUNGANDR" -> decryptJormungandr(payloadBytes, subtoken)
            "LOKI" -> decryptLoki(payloadBytes, subtoken)
            "NIDHOGG" -> decryptNidhogg(payloadBytes, subtoken)
            "ODIN" -> decryptOdin(payloadBytes, subtoken)
            "SLEIPNIR" -> decryptSleipnir(payloadBytes, subtoken)
            "THOR" -> decryptThor(payloadBytes, subtoken)
            else -> null
        }
    }
}
