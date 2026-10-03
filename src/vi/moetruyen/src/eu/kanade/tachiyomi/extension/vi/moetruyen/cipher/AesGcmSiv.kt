package eu.kanade.tachiyomi.extension.vi.moetruyen.cipher

import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * AES-GCM-SIV (RFC 8452) for IMGX v4 profile p07.
 * Matches noble-ciphers gcmsiv used by moetruyen (verified against site payloads).
 */
internal object AesGcmSiv {

    private fun aesEcbEncrypt(key: ByteArray, block: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        return cipher.doFinal(block)
    }

    private fun deriveKeys(masterKey: ByteArray, nonce: ByteArray): Pair<ByteArray, ByteArray> {
        require(masterKey.size == 32) { "AES-GCM-SIV key must be 32 bytes" }
        require(nonce.size == 12) { "AES-GCM-SIV nonce must be 12 bytes" }
        val outputs = IntArray(12)
        val block = ByteArray(16)
        var counter = 0
        for (call in 0 until 6) {
            block[0] = (counter and 0xFF).toByte()
            block[1] = ((counter ushr 8) and 0xFF).toByte()
            block[2] = ((counter ushr 16) and 0xFF).toByte()
            block[3] = ((counter ushr 24) and 0xFF).toByte()
            nonce.copyInto(block, 4)
            val enc = aesEcbEncrypt(masterKey, block)
            val bb = ByteBuffer.wrap(enc).order(ByteOrder.LITTLE_ENDIAN)
            outputs[call * 2] = bb.getInt(0)
            outputs[call * 2 + 1] = bb.getInt(4)
            counter++
        }
        val authKey = ByteArray(16)
        val encKey = ByteArray(32)
        val ab = ByteBuffer.wrap(authKey).order(ByteOrder.LITTLE_ENDIAN)
        val eb = ByteBuffer.wrap(encKey).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until 4) ab.putInt(outputs[i])
        for (i in 0 until 8) eb.putInt(outputs[i + 4])
        return authKey to encKey
    }

    private fun aesCtrLe32Decrypt(encKey: ByteArray, iv: ByteArray, ciphertext: ByteArray): ByteArray {
        val ctr = iv.copyOf()
        val out = ByteArray(ciphertext.size)
        var ctrNum = ByteBuffer.wrap(ctr).order(ByteOrder.LITTLE_ENDIAN).getInt(0)
        var pos = 0
        while (pos < ciphertext.size) {
            val ks = aesEcbEncrypt(encKey, ctr)
            val n = minOf(16, ciphertext.size - pos)
            for (i in 0 until n) out[pos + i] = (ciphertext[pos + i].toInt() xor ks[i].toInt()).toByte()
            ctrNum += 1
            ctr[0] = (ctrNum and 0xFF).toByte()
            ctr[1] = ((ctrNum ushr 8) and 0xFF).toByte()
            ctr[2] = ((ctrNum ushr 16) and 0xFF).toByte()
            ctr[3] = ((ctrNum ushr 24) and 0xFF).toByte()
            pos += 16
        }
        return out
    }

    /** Multiply by x in POLYVAL field (LE bits, poly x^128+x^127+x^126+x^121+1). */
    private fun mulxLe(v: ByteArray): ByteArray {
        val out = v.copyOf()
        val msb = out[15].toInt() and 0x80
        for (j in 15 downTo 1) {
            out[j] = (((out[j].toInt() and 0xFF) shl 1) or ((out[j - 1].toInt() and 0xFF) ushr 7)).toByte()
        }
        out[0] = ((out[0].toInt() and 0xFF) shl 1).toByte()
        if (msb != 0) {
            out[0] = (out[0].toInt() xor 0x01).toByte()
            out[15] = (out[15].toInt() xor 0xC2).toByte()
        }
        return out
    }

    private fun mulPolyval(a: ByteArray, b: ByteArray): ByteArray {
        val z = ByteArray(16)
        var v = b.copyOf()
        for (i in 0 until 128) {
            if (((a[i shr 3].toInt() ushr (i and 7)) and 1) == 1) {
                for (j in 0 until 16) z[j] = (z[j].toInt() xor v[j].toInt()).toByte()
            }
            v = mulxLe(v)
        }
        return z
    }

    /** POLYVAL(H, parts) via S = dot(S+X, H) = (S+X)*H*x^{-128}. */
    private fun polyval(h: ByteArray, parts: List<ByteArray>): ByteArray {
        val xInv = byteArrayOf(
            0x01, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 0, 0, 0x04, 0x92.toByte(),
        )
        var s = ByteArray(16)
        for (data in parts) {
            var i = 0
            while (i < data.size) {
                val x = ByteArray(16)
                val len = minOf(16, data.size - i)
                data.copyInto(x, 0, i, i + len)
                val t = ByteArray(16)
                for (j in 0 until 16) t[j] = (s[j].toInt() xor x[j].toInt()).toByte()
                s = mulPolyval(mulPolyval(t, h), xInv)
                i += 16
            }
        }
        return s
    }

    private fun pad16(b: ByteArray): ByteArray {
        if (b.size % 16 == 0) return b
        return b + ByteArray(16 - (b.size % 16))
    }

    fun decrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray {
        require(nonce.size == 12) { "AES-GCM-SIV nonce must be 12 bytes" }
        require(ciphertext.size >= 16) { "AES-GCM-SIV ciphertext too short" }

        val tag = ciphertext.copyOfRange(ciphertext.size - 16, ciphertext.size)
        val ct = ciphertext.copyOfRange(0, ciphertext.size - 16)
        val (authKey, encKey) = deriveKeys(key, nonce)
        try {
            val iv = tag.copyOf()
            iv[15] = (iv[15].toInt() or 0x80).toByte()
            val plaintext = aesCtrLe32Decrypt(encKey, iv, ct)

            val lenBlock = ByteArray(16)
            ByteBuffer.wrap(lenBlock).order(ByteOrder.LITTLE_ENDIAN).apply {
                putLong(0, aad.size * 8L)
                putLong(8, plaintext.size * 8L)
            }
            val ss = polyval(authKey, listOf(pad16(aad), pad16(plaintext), lenBlock))
            val masked = ss.copyOf()
            for (i in 0 until 12) masked[i] = (masked[i].toInt() xor nonce[i].toInt()).toByte()
            masked[15] = (masked[15].toInt() and 0x7F).toByte()
            val expected = aesEcbEncrypt(encKey, masked)
            var diff = 0
            for (i in 0 until 16) diff = diff or (tag[i].toInt() xor expected[i].toInt())
            require(diff == 0) { "AES-GCM-SIV authentication failed" }
            return plaintext
        } finally {
            authKey.fill(0)
            encKey.fill(0)
        }
    }
}
