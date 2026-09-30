package eu.kanade.tachiyomi.extension.vi.moetruyen.cipher

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * AEGIS-128L AEAD for IMGX v4 profile p10.
 * Verified against IETF draft-irtf-cfrg-aegis-aead.
 * State: 8 x 128-bit blocks. Key: 16 bytes. Nonce: 16 bytes. Tag: 32 bytes.
 * Processes 256-bit (32-byte) input blocks.
 */
internal object Aegis128l {
    private const val TAG_LEN = 32

    private val SBOX = intArrayOf(
        0x63, 0x7c, 0x77, 0x7b, 0xf2, 0x6b, 0x6f, 0xc5, 0x30, 0x01, 0x67, 0x2b, 0xfe.toByte().toInt(), 0xd7.toByte().toInt(), 0xab.toByte().toInt(), 0x76,
        0xca.toByte().toInt(), 0x82.toByte().toInt(), 0xc9.toByte().toInt(), 0x7d, 0xfa.toByte().toInt(), 0x59, 0x47, 0xf0.toByte().toInt(), 0xad.toByte().toInt(), 0xd4.toByte().toInt(), 0xa2.toByte().toInt(), 0xaf.toByte().toInt(), 0x9c.toByte().toInt(), 0xa4.toByte().toInt(), 0x72, 0xc0.toByte().toInt(),
        0xb7.toByte().toInt(), 0xfd.toByte().toInt(), 0x93.toByte().toInt(), 0x26, 0x36, 0x3f, 0xf7.toByte().toInt(), 0xcc.toByte().toInt(), 0x34, 0xa5.toByte().toInt(), 0xe5.toByte().toInt(), 0xf1.toByte().toInt(), 0x71, 0xd8.toByte().toInt(), 0x31, 0x15,
        0x04, 0xc7.toByte().toInt(), 0x23, 0xc3.toByte().toInt(), 0x18, 0x96.toByte().toInt(), 0x05, 0x9a.toByte().toInt(), 0x07, 0x12, 0x80.toByte().toInt(), 0xe2.toByte().toInt(), 0xeb.toByte().toInt(), 0x27, 0xb2.toByte().toInt(), 0x75,
        0x09, 0x83.toByte().toInt(), 0x2c, 0x1a, 0x1b, 0x6e, 0x5a, 0xa0.toByte().toInt(), 0x52, 0x3b, 0xd6.toByte().toInt(), 0xb3.toByte().toInt(), 0x29, 0xe3.toByte().toInt(), 0x2f, 0x84.toByte().toInt(),
        0x53, 0xd1.toByte().toInt(), 0x00, 0xed.toByte().toInt(), 0x20, 0xfc.toByte().toInt(), 0xb1.toByte().toInt(), 0x5b, 0x6a, 0xcb.toByte().toInt(), 0xbe.toByte().toInt(), 0x39, 0x4a, 0x4c, 0x58, 0xcf.toByte().toInt(),
        0xd0.toByte().toInt(), 0xef.toByte().toInt(), 0xaa.toByte().toInt(), 0xfb.toByte().toInt(), 0x43, 0x4d, 0x33, 0x85.toByte().toInt(), 0x45, 0xf9.toByte().toInt(), 0x02, 0x7f, 0x50, 0x3c, 0x9f.toByte().toInt(), 0xa8.toByte().toInt(),
        0x51, 0xa3.toByte().toInt(), 0x40, 0x8f.toByte().toInt(), 0x92.toByte().toInt(), 0x9d.toByte().toInt(), 0x38, 0xf5.toByte().toInt(), 0xbc.toByte().toInt(), 0xb6.toByte().toInt(), 0xda.toByte().toInt(), 0x21, 0x10, 0xff.toByte().toInt(), 0xf3.toByte().toInt(), 0xd2.toByte().toInt(),
        0xcd.toByte().toInt(), 0x0c, 0x13, 0xec.toByte().toInt(), 0x5f, 0x97.toByte().toInt(), 0x44, 0x17, 0xc4.toByte().toInt(), 0xa7.toByte().toInt(), 0x7e, 0x3d, 0x64, 0x5d, 0x19, 0x73,
        0x60, 0x81.toByte().toInt(), 0x4f, 0xdc.toByte().toInt(), 0x22, 0x2a, 0x90.toByte().toInt(), 0x88.toByte().toInt(), 0x46, 0xee.toByte().toInt(), 0xb8.toByte().toInt(), 0x14, 0xde.toByte().toInt(), 0x5e, 0x0b, 0xdb.toByte().toInt(),
        0xe0.toByte().toInt(), 0x32, 0x3a, 0x0a, 0x49, 0x06, 0x24, 0x5c, 0xc2.toByte().toInt(), 0xd3.toByte().toInt(), 0xac.toByte().toInt(), 0x62, 0x91.toByte().toInt(), 0x95.toByte().toInt(), 0xe4.toByte().toInt(), 0x79,
        0xe7.toByte().toInt(), 0xc8.toByte().toInt(), 0x37, 0x6d, 0x8d.toByte().toInt(), 0xd5.toByte().toInt(), 0x4e, 0xa9.toByte().toInt(), 0x6c, 0x56, 0xf4.toByte().toInt(), 0xea.toByte().toInt(), 0x65, 0x7a, 0xae.toByte().toInt(), 0x08,
        0xba.toByte().toInt(), 0x78, 0x25, 0x2e, 0x1c, 0xa6.toByte().toInt(), 0xb4.toByte().toInt(), 0xc6.toByte().toInt(), 0xe8.toByte().toInt(), 0xdd.toByte().toInt(), 0x74, 0x1f, 0x4b, 0xbd.toByte().toInt(), 0x8b.toByte().toInt(), 0x8a.toByte().toInt(),
        0x70, 0x3e, 0xb5.toByte().toInt(), 0x66, 0x48, 0x03, 0xf6.toByte().toInt(), 0x0e, 0x61, 0x35, 0x57, 0xb9.toByte().toInt(), 0x86.toByte().toInt(), 0xc1.toByte().toInt(), 0x1d, 0x9e.toByte().toInt(),
        0xe1.toByte().toInt(), 0xf8.toByte().toInt(), 0x98.toByte().toInt(), 0x11, 0x69, 0xd9.toByte().toInt(), 0x8e.toByte().toInt(), 0x94.toByte().toInt(), 0x9b.toByte().toInt(), 0x1e, 0x87.toByte().toInt(), 0xe9.toByte().toInt(), 0xce.toByte().toInt(), 0x55, 0x28, 0xdf.toByte().toInt(),
        0x8c.toByte().toInt(), 0xa1.toByte().toInt(), 0x89.toByte().toInt(), 0x0d, 0xbf.toByte().toInt(), 0xe6.toByte().toInt(), 0x42, 0x68, 0x41, 0x99.toByte().toInt(), 0x2d, 0x0f, 0xb0.toByte().toInt(), 0x54, 0xbb.toByte().toInt(), 0x16,
    )

    private val C0 = byteArrayOf(0x00, 0x01, 0x01, 0x02, 0x03, 0x05, 0x08, 0x0d, 0x15, 0x22, 0x37, 0x59, 0x90.toByte(), 0xe9.toByte(), 0x79, 0x62)
    private val C1 = byteArrayOf(0xdb.toByte(), 0x3d, 0x18, 0x55, 0x6d, 0xc2.toByte(), 0x2f, 0xf1.toByte(), 0x20, 0x11, 0x31, 0x42, 0x73, 0xb5.toByte(), 0x28, 0xdd.toByte())

    private fun xtime(a: Int): Int = ((a shl 1) xor (((a shr 7) and 1) * 0x1b)) and 0xFF

    /** AESRound(in, rk) = SubBytes + ShiftRows + MixColumns + AddRoundKey */
    private fun aesRound(input: ByteArray, roundKey: ByteArray): ByteArray {
        val s = ByteArray(16) { SBOX[input[it].toInt() and 0xFF].toByte() }
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
        for (i in 0 until 16) out[i] = (out[i].toInt() xor roundKey[i].toInt()).toByte()
        return out
    }

    private fun xor16(a: ByteArray, b: ByteArray): ByteArray = ByteArray(16) { (a[it].toInt() xor b[it].toInt()).toByte() }
    private fun and16(a: ByteArray, b: ByteArray): ByteArray = ByteArray(16) { (a[it].toInt() and b[it].toInt()).toByte() }

    private fun init(key: ByteArray, nonce: ByteArray): Array<ByteArray> {
        val s = Array(8) { ByteArray(16) }
        val kn = xor16(key, nonce)
        s[0] = kn
        s[1] = C1.clone()
        s[2] = C0.clone()
        s[3] = C1.clone()
        s[4] = kn.clone()
        s[5] = xor16(key, C0)
        s[6] = xor16(key, C1)
        s[7] = xor16(key, C0)
        repeat(10) { update(s, nonce, key) }
        return s
    }

    /** Update(M0, M1): S'0=AESRound(S7,S0^M0), S'1=AESRound(S0,S1), ..., S'4=AESRound(S3,S4^M1), ... */
    private fun update(s: Array<ByteArray>, m0: ByteArray, m1: ByteArray) {
        val sp = Array(8) { ByteArray(16) }
        sp[0] = aesRound(s[7], xor16(s[0], m0))
        sp[1] = aesRound(s[0], s[1])
        sp[2] = aesRound(s[1], s[2])
        sp[3] = aesRound(s[2], s[3])
        sp[4] = aesRound(s[3], xor16(s[4], m1))
        sp[5] = aesRound(s[4], s[5])
        sp[6] = aesRound(s[5], s[6])
        sp[7] = aesRound(s[6], s[7])
        for (i in 0 until 8) s[i] = sp[i]
    }

    /** Dec(ci) for 32-byte block: z0=S1^S6^(S2&S3), z1=S2^S5^(S6&S7) */
    private fun dec(s: Array<ByteArray>, ci: ByteArray): ByteArray {
        val z0 = xor16(xor16(s[1], s[6]), and16(s[2], s[3]))
        val z1 = xor16(xor16(s[2], s[5]), and16(s[6], s[7]))
        val t0 = ci.copyOfRange(0, 16)
        val t1 = ci.copyOfRange(16, 32)
        val out0 = xor16(t0, z0)
        val out1 = xor16(t1, z1)
        update(s, out0, out1)
        return out0 + out1
    }

    private fun finalize(s: Array<ByteArray>, adLenBits: Long, msgLenBits: Long, tagLen: Int): ByteArray {
        val lb = ByteArray(16)
        ByteBuffer.wrap(lb).order(ByteOrder.LITTLE_ENDIAN).apply {
            putLong(0, adLenBits)
            putLong(8, msgLenBits)
        }
        val t = xor16(s[2], lb)
        repeat(7) { update(s, t, t) }
        return if (tagLen == 32) {
            val t0 = ByteArray(16)
            val t1 = ByteArray(16)
            for (i in 0 until 4) {
                for (j in 0 until 16) {
                    t0[j] = (t0[j].toInt() xor s[i][j].toInt()).toByte()
                    t1[j] = (t1[j].toInt() xor s[i + 4][j].toInt()).toByte()
                }
            }
            t0 + t1
        } else {
            val tag = ByteArray(16)
            for (i in 0 until 7) for (j in 0 until 16) tag[j] = (tag[j].toInt() xor s[i][j].toInt()).toByte()
            tag
        }
    }

    fun decrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray, aad: ByteArray): ByteArray {
        require(key.size == 16) { "AEGIS-128L key must be 16 bytes" }
        require(nonce.size == 16) { "AEGIS-128L nonce must be 16 bytes" }
        require(ciphertext.size >= TAG_LEN) { "AEGIS-128L ciphertext too short" }

        val s = init(key, nonce)
        // Absorb AD in 32-byte blocks
        var off = 0
        while (off < aad.size) {
            val b0 = ByteArray(16)
            val b1 = ByteArray(16)
            aad.copyInto(b0, 0, off, minOf(off + 16, aad.size))
            if (off + 16 < aad.size) aad.copyInto(b1, 0, off + 16, minOf(off + 32, aad.size))
            update(s, b0, b1)
            off += 32
        }

        val msgLen = ciphertext.size - TAG_LEN
        val msg = ByteArray(msgLen)
        var i = 0
        while (i + 32 <= msgLen) {
            val xi = dec(s, ciphertext.copyOfRange(i, i + 32))
            xi.copyInto(msg, i)
            i += 32
        }
        val rem = msgLen % 32
        if (rem > 0) {
            val o = msgLen - rem
            val z0 = xor16(xor16(s[1], s[6]), and16(s[2], s[3]))
            val z1 = xor16(xor16(s[2], s[5]), and16(s[6], s[7]))
            val padded = ByteArray(32)
            ciphertext.copyInto(padded, 0, o, msgLen)
            val out0 = xor16(padded.copyOfRange(0, 16), z0)
            val out1 = xor16(padded.copyOfRange(16, 32), z1)
            val combined = out0 + out1
            combined.copyInto(msg, o, 0, rem)
            val v = ByteArray(32)
            msg.copyInto(v, 0, o, msgLen)
            update(s, v.copyOfRange(0, 16), v.copyOfRange(16, 32))
        }

        val expected = finalize(s, aad.size * 8L, msgLen * 8L, TAG_LEN)
        val provided = ciphertext.copyOfRange(msgLen, msgLen + TAG_LEN)
        var diff = 0
        for (j in 0 until TAG_LEN) diff = diff or (expected[j].toInt() xor provided[j].toInt())
        require(diff == 0) { "AEGIS-128L authentication failed" }
        return msg
    }
}
