package eu.kanade.tachiyomi.extension.vi.moetruyen.cipher

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * AEGIS-256 AEAD for IMGX v4 profile p09.
 * Site uses 32-byte tags: (S0^S1^S2)||(S3^S4^S5).
 */
internal object Aegis256 {
    // Site payloads use the full 256-bit tag: (S0^S1^S2)||(S3^S4^S5).
    private const val TAG_LEN = 32
    private const val BLOCK = 16
    private const val STATE_BLOCKS = 6

    private val SBOX = IntArray(256) { i ->
        // Standard AES S-box
        val sbox = intArrayOf(
            0x63, 0x7c, 0x77, 0x7b, 0xf2, 0x6b, 0x6f, 0xc5, 0x30, 0x01, 0x67, 0x2b, 0xfe, 0xd7, 0xab, 0x76,
            0xca, 0x82, 0xc9, 0x7d, 0xfa, 0x59, 0x47, 0xf0, 0xad, 0xd4, 0xa2, 0xaf, 0x9c, 0xa4, 0x72, 0xc0,
            0xb7, 0xfd, 0x93, 0x26, 0x36, 0x3f, 0xf7, 0xcc, 0x34, 0xa5, 0xe5, 0xf1, 0x71, 0xd8, 0x31, 0x15,
            0x04, 0xc7, 0x23, 0xc3, 0x18, 0x96, 0x05, 0x9a, 0x07, 0x12, 0x80, 0xe2, 0xeb, 0x27, 0xb2, 0x75,
            0x09, 0x83, 0x2c, 0x1a, 0x1b, 0x6e, 0x5a, 0xa0, 0x52, 0x3b, 0xd6, 0xb3, 0x29, 0xe3, 0x2f, 0x84,
            0x53, 0xd1, 0x00, 0xed, 0x20, 0xfc, 0xb1, 0x5b, 0x6a, 0xcb, 0xbe, 0x39, 0x4a, 0x4c, 0x58, 0xcf,
            0xd0, 0xef, 0xaa, 0xfb, 0x43, 0x4d, 0x33, 0x85, 0x45, 0xf9, 0x02, 0x7f, 0x50, 0x3c, 0x9f, 0xa8,
            0x51, 0xa3, 0x40, 0x8f, 0x92, 0x9d, 0x38, 0xf5, 0xbc, 0xb6, 0xda, 0x21, 0x10, 0xff, 0xf3, 0xd2,
            0xcd, 0x0c, 0x13, 0xec, 0x5f, 0x97, 0x44, 0x17, 0xc4, 0xa7, 0x7e, 0x3d, 0x64, 0x5d, 0x19, 0x73,
            0x60, 0x81, 0x4f, 0xdc, 0x22, 0x2a, 0x90, 0x88, 0x46, 0xee, 0xb8, 0x14, 0xde, 0x5e, 0x0b, 0xdb,
            0xe0, 0x32, 0x3a, 0x0a, 0x49, 0x06, 0x24, 0x5c, 0xc2, 0xd3, 0xac, 0x62, 0x91, 0x95, 0xe4, 0x79,
            0xe7, 0xc8, 0x37, 0x6d, 0x8d, 0xd5, 0x4e, 0xa9, 0x6c, 0x56, 0xf4, 0xea, 0x65, 0x7a, 0xae, 0x08,
            0xba, 0x78, 0x25, 0x2e, 0x1c, 0xa6, 0xb4, 0xc6, 0xe8, 0xdd, 0x74, 0x1f, 0x4b, 0xbd, 0x8b, 0x8a,
            0x70, 0x3e, 0xb5, 0x66, 0x48, 0x03, 0xf6, 0x0e, 0x61, 0x35, 0x57, 0xb9, 0x86, 0xc1, 0x1d, 0x9e,
            0xe1, 0xf8, 0x98, 0x11, 0x69, 0xd9, 0x8e, 0x94, 0x9b, 0x1e, 0x87, 0xe9, 0xce, 0x55, 0x28, 0xdf,
            0x8c, 0xa1, 0x89, 0x0d, 0xbf, 0xe6, 0x42, 0x68, 0x41, 0x99, 0x2d, 0x0f, 0xb0, 0x54, 0xbb, 0x16,
        )
        sbox[i]
    }

    private val C0 = byteArrayOf(0x00, 0x01, 0x01, 0x02, 0x03, 0x05, 0x08, 0x0d, 0x15, 0x22, 0x37, 0x59, 0x90.toByte(), 0xe9.toByte(), 0x79, 0x62)
    private val C1 = byteArrayOf(0xdb.toByte(), 0x3d, 0x18, 0x55, 0x6d, 0xc2.toByte(), 0x2f, 0xf1.toByte(), 0x20, 0x11, 0x31, 0x42, 0x73, 0xb5.toByte(), 0x28, 0xdd.toByte())

    private fun xtime(a: Int): Int = ((a shl 1) xor (((a shr 7) and 1) * 0x1b)) and 0xFF

    /** AESRound(in, rk) = SubBytes + ShiftRows + MixColumns + AddRoundKey */
    private fun aesRound(input: ByteArray, roundKey: ByteArray): ByteArray {
        val s = ByteArray(16) { SBOX[input[it].toInt() and 0xFF].toByte() }
        // ShiftRows
        val sr = ByteArray(16)
        sr[0] = s[0]
        sr[4] = s[4]
        sr[8] = s[8]
        sr[12] = s[12]
        sr[1] = s[5]
        sr[5] = s[9]
        sr[9] = s[13]
        sr[13] = s[1]
        sr[2] = s[10]
        sr[6] = s[14]
        sr[10] = s[2]
        sr[14] = s[6]
        sr[3] = s[15]
        sr[7] = s[3]
        sr[11] = s[7]
        sr[15] = s[11]
        // MixColumns
        val out = ByteArray(16)
        for (c in 0 until 4) {
            val i = c * 4
            val a0 = sr[i].toInt() and 0xFF
            val a1 = sr[i + 1].toInt() and 0xFF
            val a2 = sr[i + 2].toInt() and 0xFF
            val a3 = sr[i + 3].toInt() and 0xFF
            out[i] = (xtime(a0) xor (xtime(a1) xor a1) xor a2 xor a3).toByte()
            out[i + 1] = (a0 xor xtime(a1) xor (xtime(a2) xor a2) xor a3).toByte()
            out[i + 2] = (a0 xor a1 xor xtime(a2) xor (xtime(a3) xor a3)).toByte()
            out[i + 3] = ((xtime(a0) xor a0) xor a1 xor a2 xor xtime(a3)).toByte()
        }
        // AddRoundKey
        for (i in 0 until 16) out[i] = (out[i].toInt() xor roundKey[i].toInt()).toByte()
        return out
    }

    private fun xor16(a: ByteArray, b: ByteArray): ByteArray = ByteArray(16) { (a[it].toInt() xor b[it].toInt()).toByte() }
    private fun and16(a: ByteArray, b: ByteArray): ByteArray = ByteArray(16) { (a[it].toInt() and b[it].toInt()).toByte() }

    private fun init(key: ByteArray, nonce: ByteArray): Array<ByteArray> {
        val k0 = key.copyOfRange(0, 16)
        val k1 = key.copyOfRange(16, 32)
        val n0 = nonce.copyOfRange(0, 16)
        val n1 = nonce.copyOfRange(16, 32)
        val s = Array(STATE_BLOCKS) { ByteArray(16) }
        s[0] = xor16(k0, n0)
        s[1] = xor16(k1, n1)
        s[2] = C1.clone()
        s[3] = C0.clone()
        s[4] = xor16(k0, C0)
        s[5] = xor16(k1, C1)
        val k0n0 = xor16(k0, n0)
        val k1n1 = xor16(k1, n1)
        repeat(4) {
            update(s, k0)
            update(s, k1)
            update(s, k0n0)
            update(s, k1n1)
        }
        return s
    }

    /** S'0 = AESRound(S5, S0 ^ M); S'1 = AESRound(S0, S1); ... S'5 = AESRound(S4, S5) */
    private fun update(s: Array<ByteArray>, m: ByteArray) {
        val s0 = aesRound(s[5], xor16(s[0], m))
        val s1 = aesRound(s[0], s[1])
        val s2 = aesRound(s[1], s[2])
        val s3 = aesRound(s[2], s[3])
        val s4 = aesRound(s[3], s[4])
        val s5 = aesRound(s[4], s[5])
        s[0] = s0
        s[1] = s1
        s[2] = s2
        s[3] = s3
        s[4] = s4
        s[5] = s5
    }

    private fun dec(s: Array<ByteArray>, ci: ByteArray): ByteArray {
        val z = xor16(xor16(xor16(s[1], s[4]), s[5]), and16(s[2], s[3]))
        val xi = xor16(ci, z)
        update(s, xi)
        return xi
    }

    private fun finalize(s: Array<ByteArray>, adLenBits: Long, msgLenBits: Long): ByteArray {
        val lenBuf = ByteArray(16)
        ByteBuffer.wrap(lenBuf).order(ByteOrder.LITTLE_ENDIAN).apply {
            putLong(0, adLenBits)
            putLong(8, msgLenBits)
        }
        val t = xor16(s[3], lenBuf)
        repeat(7) { update(s, t) }
        // 256-bit tag: (S0^S1^S2) || (S3^S4^S5)
        val t0 = ByteArray(16)
        val t1 = ByteArray(16)
        for (i in 0 until 3) for (j in 0 until 16) t0[j] = (t0[j].toInt() xor s[i][j].toInt()).toByte()
        for (i in 3 until 6) for (j in 0 until 16) t1[j] = (t1[j].toInt() xor s[i][j].toInt()).toByte()
        return t0 + t1
    }

    fun decrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray {
        require(key.size == 32) { "AEGIS-256 key must be 32 bytes" }
        require(nonce.size == 32) { "AEGIS-256 nonce must be 32 bytes" }
        require(ciphertext.size >= TAG_LEN) { "AEGIS-256 ciphertext too short" }

        val s = init(key, nonce)
        // Absorb AD
        var off = 0
        while (off < aad.size) {
            val block = ByteArray(16)
            aad.copyInto(block, 0, off, minOf(off + 16, aad.size))
            update(s, block)
            off += 16
        }

        val msgLen = ciphertext.size - TAG_LEN
        val msg = ByteArray(msgLen)
        // Decrypt full blocks
        var i = 0
        while (i + 16 <= msgLen) {
            val xi = dec(s, ciphertext.copyOfRange(i, i + 16))
            xi.copyInto(msg, i)
            i += 16
        }
        // Partial last block
        val rem = msgLen % 16
        if (rem > 0) {
            val o = msgLen - rem
            val z = xor16(xor16(xor16(s[1], s[4]), s[5]), and16(s[2], s[3]))
            val padded = ByteArray(16)
            ciphertext.copyInto(padded, 0, o, msgLen)
            val out = xor16(padded, z)
            out.copyInto(msg, o, 0, rem)
            val v = ByteArray(16)
            msg.copyInto(v, 0, o, msgLen)
            update(s, v)
        }

        // Verify 256-bit tag
        val expected = finalize(s, aad.size * 8L, msgLen * 8L)
        val provided = ciphertext.copyOfRange(msgLen, msgLen + TAG_LEN)
        var diff = 0
        for (j in 0 until TAG_LEN) diff = diff or (expected[j].toInt() xor provided[j].toInt())
        require(diff == 0) { "AEGIS-256 authentication failed" }
        return msg
    }
}
