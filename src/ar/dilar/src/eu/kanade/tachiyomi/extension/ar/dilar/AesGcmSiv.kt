package eu.kanade.tachiyomi.extension.ar.dilar

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

// AES-GCM-SIV (RFC 8452) decryption, built on AES-ECB because the platform Cipher only exposes
// AES/GCM-SIV/NoPadding from API 34.
internal object AesGcmSiv {
    private const val BLOCK = 16
    private const val TAG_LENGTH = 16

    // x^-128 = x^127 + x^124 + x^121 + x^114 + 1
    private val X_INV_128 = ByteArray(BLOCK).also {
        it[0] = 0x01
        it[14] = 0x04
        it[15] = 0x92.toByte()
    }

    fun decrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray {
        require(nonce.size == 12) { "aes-gcm-siv: nonce must be 12 bytes" }
        require(ciphertext.size >= TAG_LENGTH) { "aes-gcm-siv: ciphertext shorter than the tag" }

        val encrypted = ciphertext.copyOfRange(0, ciphertext.size - TAG_LENGTH)
        val tag = ciphertext.copyOfRange(ciphertext.size - TAG_LENGTH, ciphertext.size)

        val (authKey, encryptionKey) = deriveKeys(key, nonce)
        val counter = tag.copyOf().also { it[15] = (it[15].toInt() or 0x80).toByte() }
        val plaintext = counterMode(encryptionKey, counter, encrypted)

        val expected = polyval(authKey, aad, plaintext)
        for (i in 0 until 12) expected[i] = (expected[i].toInt() xor nonce[i].toInt()).toByte()
        expected[15] = (expected[15].toInt() and 0x7f).toByte()

        require(aesBlock(encryptionKey, expected).contentEquals(tag)) { "aes-gcm-siv: authentication failed" }
        return plaintext
    }

    // Per-nonce keys: 2 blocks feed the 16-byte authentication key, then 2 (AES-128) or 4 (AES-256)
    // blocks feed the encryption key. Each block is little-endian counter || nonce, truncated to 8.
    private fun deriveKeys(key: ByteArray, nonce: ByteArray): Pair<ByteArray, ByteArray> {
        val encryptionKeySize = if (key.size == 32) 32 else 16
        val blocks = 2 + encryptionKeySize / 8
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
            .apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES")) }
        val material = ByteArray(blocks * 8)
        for (i in 0 until blocks) {
            val block = ByteArray(BLOCK)
            block[0] = i.toByte()
            block[1] = (i ushr 8).toByte()
            block[2] = (i ushr 16).toByte()
            block[3] = (i ushr 24).toByte()
            System.arraycopy(nonce, 0, block, 4, 12)
            System.arraycopy(cipher.doFinal(block), 0, material, i * 8, 8)
        }
        return material.copyOfRange(0, 16) to material.copyOfRange(16, 16 + encryptionKeySize)
    }

    private fun counterMode(key: ByteArray, initialCounter: ByteArray, input: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
            .apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES")) }
        val output = ByteArray(input.size)
        val counter = initialCounter.copyOf()
        var offset = 0
        while (offset < input.size) {
            val keystream = cipher.doFinal(counter)
            val length = minOf(BLOCK, input.size - offset)
            for (i in 0 until length) {
                output[offset + i] = (input[offset + i].toInt() xor keystream[i].toInt()).toByte()
            }
            var value = (counter[0].toInt() and 0xff) or
                ((counter[1].toInt() and 0xff) shl 8) or
                ((counter[2].toInt() and 0xff) shl 16) or
                ((counter[3].toInt() and 0xff) shl 24)
            value += 1
            counter[0] = value.toByte()
            counter[1] = (value ushr 8).toByte()
            counter[2] = (value ushr 16).toByte()
            counter[3] = (value ushr 24).toByte()
            offset += length
        }
        return output
    }

    private fun polyval(authKey: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray {
        // dot(x, authKey) == x * (authKey * x^-128), so fold x^-128 into H once.
        val h = multiply(authKey, X_INV_128)
        var lo = 0L
        var hi = 0L

        fun absorb(data: ByteArray) {
            var offset = 0
            while (offset < data.size) {
                val chunk = ByteArray(BLOCK)
                val length = minOf(BLOCK, data.size - offset)
                System.arraycopy(data, offset, chunk, 0, length)
                val x = toLongs(chunk)
                val product = multiply(lo xor x[0], hi xor x[1], h[0], h[1])
                lo = product[0]
                hi = product[1]
                offset += BLOCK
            }
        }

        absorb(aad)
        absorb(plaintext)
        val lengthBlock = ByteArray(BLOCK)
        putLe64(lengthBlock, 0, aad.size.toLong() * 8)
        putLe64(lengthBlock, 8, plaintext.size.toLong() * 8)
        absorb(lengthBlock)
        return fromLongs(lo, hi)
    }

    // GF(2^128) multiplication in POLYVAL bit order, reduced modulo x^128 + x^127 + x^126 + x^121 + 1.
    private fun multiply(aLo: Long, aHi: Long, bLo: Long, bHi: Long): LongArray {
        val r = LongArray(4)
        var x0 = aLo
        var x1 = aHi
        var x2 = 0L
        var x3 = 0L
        for (i in 0 until 128) {
            val bit = if (i < 64) (bLo ushr i) and 1L else (bHi ushr (i - 64)) and 1L
            if (bit != 0L) {
                r[0] = r[0] xor x0
                r[1] = r[1] xor x1
                r[2] = r[2] xor x2
                r[3] = r[3] xor x3
            }
            x3 = (x3 shl 1) or (x2 ushr 63)
            x2 = (x2 shl 1) or (x1 ushr 63)
            x1 = (x1 shl 1) or (x0 ushr 63)
            x0 = x0 shl 1
        }
        for (i in 255 downTo 128) {
            if (((r[i ushr 6] ushr (i and 63)) and 1L) != 0L) {
                flip(r, i)
                flip(r, i - 1)
                flip(r, i - 2)
                flip(r, i - 7)
                flip(r, i - 128)
            }
        }
        return longArrayOf(r[0], r[1])
    }

    private fun multiply(a: ByteArray, b: ByteArray): LongArray {
        val left = toLongs(a)
        val right = toLongs(b)
        return multiply(left[0], left[1], right[0], right[1])
    }

    private fun flip(r: LongArray, bit: Int) {
        r[bit ushr 6] = r[bit ushr 6] xor (1L shl (bit and 63))
    }

    private fun toLongs(bytes: ByteArray): LongArray {
        var lo = 0L
        var hi = 0L
        for (i in 0 until 8) {
            lo = lo or ((bytes[i].toLong() and 0xff) shl (8 * i))
            hi = hi or ((bytes[8 + i].toLong() and 0xff) shl (8 * i))
        }
        return longArrayOf(lo, hi)
    }

    private fun fromLongs(lo: Long, hi: Long): ByteArray {
        val bytes = ByteArray(BLOCK)
        for (i in 0 until 8) {
            bytes[i] = (lo ushr (8 * i)).toByte()
            bytes[8 + i] = (hi ushr (8 * i)).toByte()
        }
        return bytes
    }

    private fun putLe64(target: ByteArray, offset: Int, value: Long) {
        for (i in 0 until 8) target[offset + i] = (value ushr (8 * i)).toByte()
    }

    private fun aesBlock(key: ByteArray, block: ByteArray): ByteArray = Cipher.getInstance("AES/ECB/NoPadding")
        .apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES")) }
        .doFinal(block)
}
