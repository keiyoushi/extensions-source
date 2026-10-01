package eu.kanade.tachiyomi.extension.vi.moetruyen.cipher

import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * ChaCha20-Poly1305 (IETF RFC 8439) for IMGX v4 profile p02.
 * Body: nonce(12) || ciphertext || tag(16).
 * Matches libsodium crypto_aead_chacha20poly1305_ietf_decrypt.
 */
internal object ChaCha20Poly1305 {

    private fun rotl(x: Int, n: Int): Int = (x shl n) or (x ushr (32 - n))

    private fun quarterRound(s: IntArray, a: Int, b: Int, c: Int, d: Int) {
        s[a] += s[b]
        s[d] = rotl(s[d] xor s[a], 16)
        s[c] += s[d]
        s[b] = rotl(s[b] xor s[c], 12)
        s[a] += s[b]
        s[d] = rotl(s[d] xor s[a], 8)
        s[c] += s[d]
        s[b] = rotl(s[b] xor s[c], 7)
    }

    private fun chachaBlock(key: ByteArray, counter: Int, nonce: ByteArray): ByteArray {
        val state = IntArray(16)
        state[0] = 0x61707865
        state[1] = 0x3320646e
        state[2] = 0x79622d32
        state[3] = 0x6b206574
        val kb = ByteBuffer.wrap(key).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until 8) state[4 + i] = kb.getInt(i * 4)
        state[12] = counter
        val nb = ByteBuffer.wrap(nonce).order(ByteOrder.LITTLE_ENDIAN)
        state[13] = nb.getInt(0)
        state[14] = nb.getInt(4)
        state[15] = nb.getInt(8)
        val x = state.copyOf()
        repeat(10) {
            quarterRound(x, 0, 4, 8, 12)
            quarterRound(x, 1, 5, 9, 13)
            quarterRound(x, 2, 6, 10, 14)
            quarterRound(x, 3, 7, 11, 15)
            quarterRound(x, 0, 5, 10, 15)
            quarterRound(x, 1, 6, 11, 12)
            quarterRound(x, 2, 7, 8, 13)
            quarterRound(x, 3, 4, 9, 14)
        }
        val out = ByteArray(64)
        val ob = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until 16) ob.putInt(i * 4, x[i] + state[i])
        return out
    }

    internal fun chachaBlockPublic(key: ByteArray, counter: Int, nonce: ByteArray): ByteArray = chachaBlock(key, counter, nonce)

    private fun chachaXor(key: ByteArray, counter: Int, nonce: ByteArray, input: ByteArray): ByteArray {
        val out = ByteArray(input.size)
        var offset = 0
        var ctr = counter
        while (offset < input.size) {
            val block = chachaBlock(key, ctr, nonce)
            val n = minOf(64, input.size - offset)
            for (i in 0 until n) out[offset + i] = (input[offset + i].toInt() xor block[i].toInt()).toByte()
            offset += n
            ctr++
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

    /** RFC 8439 Poly1305 over AEAD mac_data = aad||pad||ct||pad||le64(aadLen)||le64(ctLen). */
    private fun poly1305(otk: ByteArray, aad: ByteArray, ciphertext: ByteArray): ByteArray {
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

        val lens = ByteArray(16)
        ByteBuffer.wrap(lens).order(ByteOrder.LITTLE_ENDIAN).apply {
            putLong(0, aad.size.toLong())
            putLong(8, ciphertext.size.toLong())
        }
        val aadPad = if (aad.size % 16 == 0) ByteArray(0) else ByteArray(16 - aad.size % 16)
        val ctPad = if (ciphertext.size % 16 == 0) ByteArray(0) else ByteArray(16 - ciphertext.size % 16)
        val macData = aad + aadPad + ciphertext + ctPad + lens

        var acc = BigInteger.ZERO
        var off = 0
        while (off < macData.size) {
            val nbi = leInt(macData.copyOfRange(off, off + 16)).add(BigInteger.ONE.shiftLeft(128))
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

    fun decrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray {
        require(key.size == 32) { "ChaCha20-Poly1305 key must be 32 bytes" }
        require(nonce.size == 12) { "ChaCha20-Poly1305 nonce must be 12 bytes" }
        require(ciphertext.size >= 16) { "ChaCha20-Poly1305 ciphertext too short" }
        val tag = ciphertext.copyOfRange(ciphertext.size - 16, ciphertext.size)
        val ct = ciphertext.copyOfRange(0, ciphertext.size - 16)
        val otk = chachaBlock(key, 0, nonce).copyOfRange(0, 32)
        val plain = chachaXor(key, 1, nonce, ct)
        val expected = poly1305(otk, aad, ct)
        var diff = 0
        for (i in 0 until 16) diff = diff or (tag[i].toInt() xor expected[i].toInt())
        require(diff == 0) { "ChaCha20-Poly1305 authentication failed" }
        return plain
    }
}
