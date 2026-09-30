package eu.kanade.tachiyomi.extension.vi.moetruyen.cipher

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * XChaCha20 family for IMGX v4:
 *  - p03: XChaCha20-Poly1305 IETF one-shot AEAD
 *  - p05: libsodium crypto_secretstream_xchacha20poly1305
 */
internal object XChaCha20Poly1305 {

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

    /** HChaCha20(key, nonce[0..16]) → 32-byte subkey. */
    fun hchacha20(key: ByteArray, nonce16: ByteArray): ByteArray {
        require(key.size == 32) { "HChaCha20 key must be 32 bytes" }
        require(nonce16.size == 16) { "HChaCha20 nonce must be 16 bytes" }
        val state = IntArray(16)
        state[0] = 0x61707865
        state[1] = 0x3320646e
        state[2] = 0x79622d32
        state[3] = 0x6b206574
        val kb = ByteBuffer.wrap(key).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until 8) state[4 + i] = kb.getInt(i * 4)
        val nb = ByteBuffer.wrap(nonce16).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until 4) state[12 + i] = nb.getInt(i * 4)
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
        val out = ByteArray(32)
        val ob = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until 4) ob.putInt(i * 4, x[i])
        for (i in 0 until 4) ob.putInt(16 + i * 4, x[12 + i])
        return out
    }

    /** p03 body: nonce(24) || ciphertext || tag(16). */
    fun decrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray {
        require(key.size == 32) { "XChaCha20-Poly1305 key must be 32 bytes" }
        require(nonce.size == 24) { "XChaCha20-Poly1305 nonce must be 24 bytes" }
        val subkey = hchacha20(key, nonce.copyOfRange(0, 16))
        try {
            val ietfNonce = ByteArray(12)
            nonce.copyInto(ietfNonce, 4, 16, 24)
            return ChaCha20Poly1305.decrypt(subkey, ietfNonce, ciphertext, aad)
        } finally {
            subkey.fill(0)
        }
    }

    // ===================== p05: secretstream =====================

    private const val TAG_MESSAGE = 0
    private const val TAG_REKEY = 2
    private const val TAG_FINAL = 3
    private const val STREAM_AB = 17
    private const val CHUNK = 262144

    private class StreamState(var k: ByteArray, val inonce: ByteArray, var counter: Int)

    private fun streamNonce(counter: Int, inonce: ByteArray): ByteArray {
        val n = ByteArray(12)
        n[0] = (counter and 0xFF).toByte()
        n[1] = ((counter ushr 8) and 0xFF).toByte()
        n[2] = ((counter ushr 16) and 0xFF).toByte()
        n[3] = ((counter ushr 24) and 0xFF).toByte()
        inonce.copyInto(n, 4)
        return n
    }

    private fun chachaXorIc(key: ByteArray, nonce: ByteArray, input: ByteArray, ic: Int): ByteArray {
        val out = ByteArray(input.size)
        var off = 0
        var ctr = ic
        while (off < input.size) {
            val block = ChaCha20Poly1305.chachaBlockPublic(key, ctr, nonce)
            val n = minOf(64, input.size - off)
            for (i in 0 until n) out[off + i] = (input[off + i].toInt() xor block[i].toInt()).toByte()
            off += n
            ctr++
        }
        return out
    }

    private fun streamRekey(st: StreamState) {
        val buf = st.k + st.inonce
        val n = streamNonce(st.counter, st.inonce)
        val ks = chachaXorIc(st.k, n, buf, 0)
        st.k = ks.copyOfRange(0, 32)
        for (i in 0 until 8) st.inonce[i] = ks[32 + i]
        st.counter = 1
    }

    private fun streamPull(st: StreamState, input: ByteArray, ad: ByteArray): Pair<ByteArray, Int> {
        require(input.size >= STREAM_AB) { "secretstream chunk too short" }
        val mlen = input.size - STREAM_AB
        val nonce = streamNonce(st.counter, st.inonce)
        val polyKey = chachaXorIc(st.k, nonce, ByteArray(64), 0).copyOfRange(0, 32)
        try {
            val adPad = pad16(ad.size).takeIf { it > 0 }?.let { ByteArray(it) } ?: ByteArray(0)
            val block = ByteArray(64)
            block[0] = input[0]
            val encBlock = chachaXorIc(st.k, nonce, block, 1)
            val tag = encBlock[0].toInt() and 0xFF
            val authBlock = encBlock.copyOf()
            authBlock[0] = input[0]
            val c = input.copyOfRange(1, 1 + mlen)
            val cPad = pad16(64 + mlen).takeIf { it > 0 }?.let { ByteArray(it) } ?: ByteArray(0)
            val slen = ByteArray(16)
            ByteBuffer.wrap(slen).order(ByteOrder.LITTLE_ENDIAN).apply {
                putLong(0, ad.size.toLong())
                putLong(8, 64L + mlen)
            }
            val expected = Xsalsa20Poly1305.poly1305Mac(polyKey, ad + adPad + authBlock + c + cPad + slen)
            val stored = input.copyOfRange(1 + mlen, input.size)
            var diff = 0
            for (i in 0 until 16) diff = diff or (expected[i].toInt() xor stored[i].toInt())
            require(diff == 0) { "IMGX v4 stream authentication failed" }

            val message = chachaXorIc(st.k, nonce, c, 2)
            for (i in 0 until 8) st.inonce[i] = (st.inonce[i].toInt() xor stored[i].toInt()).toByte()
            st.counter++
            if ((tag and TAG_REKEY) != 0 || st.counter == 0) streamRekey(st)
            return message to tag
        } finally {
            polyKey.fill(0)
        }
    }

    private fun pad16(n: Int): Int = (0x10 - n) and 0xF

    /** p05 body: header(24) || chunks of (chunkLen+17). */
    fun decryptStream(key: ByteArray, body: ByteArray, aad: ByteArray, plainBytes: Int): ByteArray {
        require(body.size > 24) { "IMGX p05 payload too short" }
        val st = StreamState(
            k = hchacha20(key, body.copyOfRange(0, 16)),
            inonce = body.copyOfRange(16, 24),
            counter = 1,
        )
        try {
            val out = ByteArray(plainBytes)
            var inOff = 24
            var outOff = 0
            while (outOff < plainBytes) {
                val cLen = minOf(CHUNK, plainBytes - outOff)
                val chunk = body.copyOfRange(inOff, inOff + cLen + STREAM_AB)
                val (message, tag) = streamPull(st, chunk, aad)
                require(message.size == cLen) { "IMGX v4 stream length mismatch" }
                message.copyInto(out, outOff)
                val want = if (outOff + cLen == plainBytes) TAG_FINAL else TAG_MESSAGE
                require(tag == want) { "IMGX v4 stream tag mismatch" }
                outOff += cLen
                inOff += cLen + STREAM_AB
            }
            return out
        } finally {
            st.k.fill(0)
            st.inonce.fill(0)
        }
    }
}
