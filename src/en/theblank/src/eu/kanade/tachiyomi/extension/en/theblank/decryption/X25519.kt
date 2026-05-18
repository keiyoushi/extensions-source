package eu.kanade.tachiyomi.extension.en.theblank.decryption

import java.math.BigInteger

/**
 * Self-contained X25519 (RFC 7748) using BigInteger.
 *
 * Used once per chapter to derive the ECDH shared secret with the server's
 * static public key. Not constant-time — acceptable here because the
 * private scalar is freshly generated, used once, then wiped.
 */
internal object X25519 {
    private val P = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19))
    private val A24 = BigInteger.valueOf(121665)
    private val TWO = BigInteger.valueOf(2)

    private val BASE = ByteArray(32).also { it[0] = 9 }

    fun publicKey(privateKey: ByteArray): ByteArray = scalarMult(privateKey, BASE)

    fun scalarMult(scalar: ByteArray, u: ByteArray): ByteArray {
        require(scalar.size == 32) { "scalar must be 32 bytes" }
        require(u.size == 32) { "u must be 32 bytes" }
        val k = clamp(scalar)
        val x = decodeU(u)
        return encodeU(ladder(k, x))
    }

    private fun clamp(s: ByteArray): BigInteger {
        val k = s.copyOf()
        k[0] = (k[0].toInt() and 248).toByte()
        k[31] = ((k[31].toInt() and 127) or 64).toByte()
        return leToBig(k)
    }

    private fun decodeU(u: ByteArray): BigInteger {
        val u2 = u.copyOf()
        u2[31] = (u2[31].toInt() and 127).toByte()
        return leToBig(u2).mod(P)
    }

    private fun encodeU(x: BigInteger): ByteArray = bigToLe(x.mod(P), 32)

    private fun leToBig(le: ByteArray): BigInteger {
        val be = ByteArray(le.size + 1) // leading 0x00 to keep positive
        for (i in le.indices) be[le.size - i] = le[i]
        return BigInteger(be)
    }

    private fun bigToLe(x: BigInteger, len: Int): ByteArray {
        val out = ByteArray(len)
        var v = x
        var i = 0
        while (i < len && v.signum() > 0) {
            out[i] = v.toInt().and(0xff).toByte()
            v = v.shiftRight(8)
            i++
        }
        return out
    }

    private fun ladder(k: BigInteger, u: BigInteger): BigInteger {
        var x1 = u
        var x2 = BigInteger.ONE
        var z2 = BigInteger.ZERO
        var x3 = u
        var z3 = BigInteger.ONE
        var swap = 0

        for (t in 254 downTo 0) {
            val kt = if (k.testBit(t)) 1 else 0
            swap = swap xor kt
            if (swap == 1) {
                val tx = x2
                x2 = x3
                x3 = tx
                val tz = z2
                z2 = z3
                z3 = tz
            }
            swap = kt

            val a = x2.add(z2).mod(P)
            val aa = a.multiply(a).mod(P)
            val b = x2.subtract(z2).mod(P)
            val bb = b.multiply(b).mod(P)
            val e = aa.subtract(bb).mod(P)
            val c = x3.add(z3).mod(P)
            val d = x3.subtract(z3).mod(P)
            val da = d.multiply(a).mod(P)
            val cb = c.multiply(b).mod(P)

            val nx3 = da.add(cb).mod(P).modPow(TWO, P)
            val nz3 = x1.multiply(da.subtract(cb).mod(P).modPow(TWO, P)).mod(P)
            val nx2 = aa.multiply(bb).mod(P)
            val nz2 = e.multiply(aa.add(A24.multiply(e)).mod(P)).mod(P)

            x2 = nx2
            z2 = nz2
            x3 = nx3
            z3 = nz3
        }
        if (swap == 1) {
            val tx = x2
            x2 = x3
            x3 = tx
            val tz = z2
            z2 = z3
            z3 = tz
        }
        return x2.multiply(z2.modInverse(P)).mod(P)
    }
}
