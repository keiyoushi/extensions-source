package eu.kanade.tachiyomi.extension.vi.moetruyen.cipher

import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

/**
 * NaCl XSalsa20-Poly1305 secretbox for IMGX v4 profile p04.
 * Body: nonce(24) || mac(16) || ciphertext.
 * Plaintext = SHA-256(aad) || image.
 */
internal object Xsalsa20Poly1305 {

    private fun rotl(a: Int, b: Int): Int = (a shl b) or (a ushr (32 - b))

    private val SIGMA = intArrayOf(0x61707865, 0x3320646e, 0x79622d32, 0x6b206574)

    private fun salsa20Block(input: IntArray): IntArray {
        val x = input.copyOf()
        repeat(10) {
            x[4] = x[4] xor rotl(x[0] + x[12], 7)
            x[8] = x[8] xor rotl(x[4] + x[0], 9)
            x[12] = x[12] xor rotl(x[8] + x[4], 13)
            x[0] = x[0] xor rotl(x[12] + x[8], 18)
            x[9] = x[9] xor rotl(x[5] + x[1], 7)
            x[13] = x[13] xor rotl(x[9] + x[5], 9)
            x[1] = x[1] xor rotl(x[13] + x[9], 13)
            x[5] = x[5] xor rotl(x[1] + x[13], 18)
            x[14] = x[14] xor rotl(x[10] + x[6], 7)
            x[2] = x[2] xor rotl(x[14] + x[10], 9)
            x[6] = x[6] xor rotl(x[2] + x[14], 13)
            x[10] = x[10] xor rotl(x[6] + x[2], 18)
            x[3] = x[3] xor rotl(x[15] + x[11], 7)
            x[7] = x[7] xor rotl(x[3] + x[15], 9)
            x[11] = x[11] xor rotl(x[7] + x[3], 13)
            x[15] = x[15] xor rotl(x[11] + x[7], 18)
            x[1] = x[1] xor rotl(x[0] + x[3], 7)
            x[2] = x[2] xor rotl(x[1] + x[0], 9)
            x[3] = x[3] xor rotl(x[2] + x[1], 13)
            x[0] = x[0] xor rotl(x[3] + x[2], 18)
            x[6] = x[6] xor rotl(x[5] + x[4], 7)
            x[7] = x[7] xor rotl(x[6] + x[5], 9)
            x[4] = x[4] xor rotl(x[7] + x[6], 13)
            x[5] = x[5] xor rotl(x[4] + x[7], 18)
            x[11] = x[11] xor rotl(x[10] + x[9], 7)
            x[8] = x[8] xor rotl(x[11] + x[10], 9)
            x[9] = x[9] xor rotl(x[8] + x[11], 13)
            x[10] = x[10] xor rotl(x[9] + x[8], 18)
            x[12] = x[12] xor rotl(x[15] + x[14], 7)
            x[13] = x[13] xor rotl(x[12] + x[15], 9)
            x[14] = x[14] xor rotl(x[13] + x[12], 13)
            x[15] = x[15] xor rotl(x[14] + x[13], 18)
        }
        return x
    }

    private fun leWords(bytes: ByteArray): IntArray {
        val n = bytes.size / 4
        val bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return IntArray(n) { bb.getInt(it * 4) }
    }

    private fun wordsToBytes(words: IntArray): ByteArray {
        val out = ByteArray(words.size * 4)
        ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN).apply {
            words.forEachIndexed { i, w -> putInt(i * 4, w) }
        }
        return out
    }

    fun hsalsa20(key: ByteArray, nonce16: ByteArray): ByteArray {
        val input = IntArray(16)
        input[0] = SIGMA[0]
        input[5] = SIGMA[1]
        input[10] = SIGMA[2]
        input[15] = SIGMA[3]
        val k = leWords(key)
        val n = leWords(nonce16)
        input[1] = k[0]
        input[2] = k[1]
        input[3] = k[2]
        input[4] = k[3]
        input[11] = k[4]
        input[12] = k[5]
        input[13] = k[6]
        input[14] = k[7]
        input[6] = n[0]
        input[7] = n[1]
        input[8] = n[2]
        input[9] = n[3]
        val x = salsa20Block(input)
        return wordsToBytes(intArrayOf(x[0], x[5], x[10], x[15], x[6], x[7], x[8], x[9]))
    }

    fun salsa20Xor(subkey: ByteArray, nonce8: ByteArray, input: ByteArray, ic: Int): ByteArray {
        val out = ByteArray(input.size)
        val k = leWords(subkey)
        val n = leWords(nonce8)
        var counter = ic
        var pos = 0
        while (pos < input.size) {
            val iw = IntArray(16)
            iw[0] = SIGMA[0]
            iw[5] = SIGMA[1]
            iw[10] = SIGMA[2]
            iw[15] = SIGMA[3]
            iw[1] = k[0]
            iw[2] = k[1]
            iw[3] = k[2]
            iw[4] = k[3]
            iw[11] = k[4]
            iw[12] = k[5]
            iw[13] = k[6]
            iw[14] = k[7]
            iw[6] = n[0]
            iw[7] = n[1]
            iw[8] = counter
            iw[9] = 0
            val x = salsa20Block(iw)
            val block = wordsToBytes(IntArray(16) { (x[it] + iw[it]) })
            val take = minOf(64, input.size - pos)
            for (i in 0 until take) out[pos + i] = (input[pos + i].toInt() xor block[i].toInt()).toByte()
            pos += take
            counter++
        }
        return out
    }

    private fun leInt(bytes: ByteArray): BigInteger {
        var v = BigInteger.ZERO
        for (i in bytes.indices.reversed()) {
            v = v.shiftLeft(8).add(BigInteger.valueOf(bytes[i].toLong() and 0xFF))
        }
        return v
    }

    fun poly1305Mac(otk: ByteArray, data: ByteArray): ByteArray {
        val rBytes = otk.copyOfRange(0, 16)
        rBytes[3] = (rBytes[3].toInt() and 0x0F).toByte()
        rBytes[7] = (rBytes[7].toInt() and 0x0F).toByte()
        rBytes[11] = (rBytes[11].toInt() and 0x0F).toByte()
        rBytes[15] = (rBytes[15].toInt() and 0x0F).toByte()
        rBytes[4] = (rBytes[4].toInt() and 0xFC).toByte()
        rBytes[8] = (rBytes[8].toInt() and 0xFC).toByte()
        rBytes[12] = (rBytes[12].toInt() and 0xFC).toByte()
        val r = leInt(rBytes)
        val s = leInt(otk.copyOfRange(16, 32))
        val p = BigInteger.ONE.shiftLeft(130).subtract(BigInteger.valueOf(5))
        var acc = BigInteger.ZERO
        var off = 0
        while (off < data.size) {
            val n = minOf(16, data.size - off)
            val nbi = leInt(data.copyOfRange(off, off + n)).add(BigInteger.ONE.shiftLeft(8 * n))
            acc = acc.add(nbi).multiply(r).mod(p)
            off += 16
        }
        val tagInt = acc.add(s)
        val out = ByteArray(16)
        var v = tagInt
        for (i in 0 until 16) {
            out[i] = (v.toInt() and 0xFF).toByte()
            v = v.shiftRight(8)
        }
        return out
    }

    fun secretboxOpenEasy(cipher: ByteArray, nonce: ByteArray, key: ByteArray): ByteArray {
        require(nonce.size == 24) { "secretbox nonce must be 24 bytes" }
        require(key.size == 32) { "secretbox key must be 32 bytes" }
        require(cipher.size >= 16) { "secretbox ciphertext too short" }
        val mac = cipher.copyOfRange(0, 16)
        val ct = cipher.copyOfRange(16, cipher.size)
        val subkey = hsalsa20(key, nonce.copyOfRange(0, 16))
        val n8 = nonce.copyOfRange(16, 24)
        try {
            val otk = salsa20Xor(subkey, n8, ByteArray(32), 0)
            // MAC is over ciphertext only (site/NaCl secretbox).
            val expected = poly1305Mac(otk, ct)
            var diff = 0
            for (i in 0 until 16) diff = diff or (mac[i].toInt() xor expected[i].toInt())
            require(diff == 0) { "secretbox authentication failed" }
            // Message uses stream[32:] (second half of first Salsa20 block).
            val ks = salsa20Xor(subkey, n8, ByteArray(32 + ct.size), 0)
            return ByteArray(ct.size) { i -> (ct[i].toInt() xor ks[32 + i].toInt()).toByte() }
        } finally {
            subkey.fill(0)
        }
    }

    /** IMGX v4 p04: nonce(24) || secretbox(SHA-256(aad) || image). */
    fun decrypt(key: ByteArray, body: ByteArray, aad: ByteArray): ByteArray {
        require(body.size > 48) { "IMGX p04 payload too short" }
        val nonce = body.copyOfRange(0, 24)
        val m = secretboxOpenEasy(body.copyOfRange(24, body.size), nonce, key)
        val expectedHash = MessageDigest.getInstance("SHA-256").digest(aad)
        require(m.size >= 32) { "IMGX p04 plaintext too short" }
        for (i in 0 until 32) {
            require(m[i] == expectedHash[i]) { "IMGX v4 context authentication failed" }
        }
        return m.copyOfRange(32, m.size)
    }
}
