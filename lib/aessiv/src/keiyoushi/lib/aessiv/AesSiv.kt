package keiyoushi.lib.aessiv

import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * AES-SIV (RFC 5297) AEAD decryption.
 */
object AesSiv {

    private fun aesEcbEncrypt(key: ByteArray, block: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(block)
    }

    /** RFC 4493 AES-CMAC. */
    private fun cmac(key: ByteArray, message: ByteArray): ByteArray {
        val blockSize = 16
        val zero = ByteArray(blockSize)
        val l = aesEcbEncrypt(key, zero)
        val k1 = dbl(l)
        val k2 = dbl(k1)
        val complete = message.isNotEmpty() && message.size % blockSize == 0
        val last = if (complete) {
            val out = ByteArray(blockSize)
            val off = message.size - blockSize
            for (i in 0 until blockSize) out[i] = (message[off + i].toInt() xor k1[i].toInt()).toByte()
            out
        } else {
            val out = ByteArray(blockSize)
            val rem = message.size % blockSize
            if (rem > 0) message.copyInto(out, 0, message.size - rem, message.size)
            out[rem] = 0x80.toByte()
            for (i in 0 until blockSize) out[i] = (out[i].toInt() xor k2[i].toInt()).toByte()
            out
        }
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        var x = ByteArray(blockSize)
        var offset = 0
        val fullBlocks = if (complete) message.size - blockSize else message.size - (message.size % blockSize)
        while (offset < fullBlocks) {
            val block = ByteArray(blockSize)
            for (i in 0 until blockSize) block[i] = (x[i].toInt() xor message[offset + i].toInt()).toByte()
            x = cipher.doFinal(block)
            offset += blockSize
        }
        val finalBlock = ByteArray(blockSize)
        for (i in 0 until blockSize) finalBlock[i] = (x[i].toInt() xor last[i].toInt()).toByte()
        return cipher.doFinal(finalBlock)
    }

    /** RFC 5297 §2.1 doubling in GF(2^128) for CMAC. */
    private fun dbl(block: ByteArray): ByteArray {
        val out = ByteArray(16)
        var carry = 0
        for (i in 15 downTo 0) {
            val v = (block[i].toInt() and 0xFF)
            val newCarry = (v ushr 7) and 1
            out[i] = (((v shl 1) or carry) and 0xFF).toByte()
            carry = newCarry
        }
        if (carry != 0) out[15] = (out[15].toInt() xor 0x87).toByte()
        return out
    }

    private fun xorBytes(a: ByteArray, b: ByteArray): ByteArray = ByteArray(a.size) { i -> (a[i].toInt() xor b[i].toInt()).toByte() }

    /** S2V(K, S1..Sn) with multiple associated-data components + plaintext. */
    private fun s2v(key: ByteArray, ad: List<ByteArray>, plaintext: ByteArray): ByteArray {
        val parts = ad.filter { it.isNotEmpty() } + listOf(plaintext)
        if (parts.isEmpty()) {
            val one = ByteArray(16)
            one[15] = 0x01
            return cmac(key, one)
        }
        var d = cmac(key, ByteArray(16))
        for (i in 0 until parts.size - 1) {
            d = xorBytes(dbl(d), cmac(key, parts[i]))
        }
        val sn = parts.last()
        val t = if (sn.size >= 16) {
            val out = sn.copyOf()
            val off = out.size - 16
            for (i in 0 until 16) out[off + i] = (out[off + i].toInt() xor d[i].toInt()).toByte()
            out
        } else {
            val padded = ByteArray(16)
            sn.copyInto(padded, 0, 0, sn.size)
            padded[sn.size] = 0x80.toByte()
            xorBytes(dbl(d), padded)
        }
        return cmac(key, t)
    }

    /** AES-CTR, full 16-byte big-endian counter. */
    private fun aesCtrDecrypt(key: ByteArray, iv: ByteArray, ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        }
        val ctr = iv.copyOf()
        val out = ByteArray(ciphertext.size)
        var pos = 0
        while (pos < ciphertext.size) {
            val ks = cipher.doFinal(ctr)
            val n = minOf(16, ciphertext.size - pos)
            for (i in 0 until n) out[pos + i] = (ciphertext[pos + i].toInt() xor ks[i].toInt()).toByte()
            for (j in 15 downTo 0) {
                val v = (ctr[j].toInt() and 0xFF) + 1
                ctr[j] = (v and 0xFF).toByte()
                if (v <= 0xFF) break
            }
            pos += 16
        }
        return out
    }

    /**
     * Decrypts ciphertext (tag || ciphertext) using AES-SIV (RFC 5297).
     * [key] must be 32 bytes (AES-128-SIV) or 64 bytes (AES-256-SIV).
     */
    fun decrypt(key: ByteArray, ciphertext: ByteArray, vararg aad: ByteArray): ByteArray {
        require(key.size == 32 || key.size == 64) { "AES-SIV key must be 32 or 64 bytes" }
        require(ciphertext.size >= 16) { "AES-SIV ciphertext too short" }
        val half = key.size / 2
        val k1 = key.copyOfRange(0, half)
        val k2 = key.copyOfRange(half, key.size)
        try {
            val v = ciphertext.copyOfRange(0, 16)
            val c = ciphertext.copyOfRange(16, ciphertext.size)
            val q = v.copyOf()
            q[8] = (q[8].toInt() and 0x7F).toByte()
            q[12] = (q[12].toInt() and 0x7F).toByte()
            val plaintext = aesCtrDecrypt(k2, q, c)
            val expected = s2v(k1, aad.toList(), plaintext)
            var diff = 0
            for (i in 0 until 16) diff = diff or (v[i].toInt() xor expected[i].toInt())
            require(diff == 0) { "AES-SIV authentication failed" }
            return plaintext
        } finally {
            k1.fill(0)
            k2.fill(0)
        }
    }
}
