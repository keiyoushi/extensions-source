package keiyoushi.lib.ece

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.interfaces.ECPublicKey
import java.security.spec.ECFieldFp
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val CURVE_NAME = "secp256r1"
private val ECE_KEY_INFO = "Content-Encoding: aes128gcm\u0000".toByteArray()
private val ECE_NONCE_INFO = "Content-Encoding: nonce\u0000".toByteArray()

object Ece {

    /**
     * Decrypts an RFC 8188 `aes128gcm` payload with the raw input keying material [ikm].
     */
    fun decrypt(payload: ByteArray, ikm: ByteArray): ByteArray {
        require(payload.size >= 21) { "ece: payload shorter than the header" }

        val salt = payload.copyOfRange(0, 16)
        val recordSize = ByteBuffer.wrap(payload, 16, 4).int
        var pos = 21 + (payload[20].toInt() and 0xFF)
        require(recordSize >= 18 && pos < payload.size) { "ece: malformed header" }

        val key = SecretKeySpec(hkdf(ikm, salt, ECE_KEY_INFO, 16), "AES")
        val nonce = hkdf(ikm, salt, ECE_NONCE_INFO, 12)
        val out = ByteArrayOutputStream()
        var sequence = 0

        while (pos < payload.size) {
            val record = payload.copyOfRange(pos, pos + minOf(recordSize, payload.size - pos))
            pos += record.size
            require(record.size >= 18) { "ece: record $sequence too short" }

            val iv = nonce.copyOf()
            var counter = sequence
            for (i in 0..3) {
                iv[8 + i] = (iv[8 + i].toInt() xor (counter ushr (8 * (3 - i)))).toByte()
            }

            val plain = Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
                doFinal(record)
            }

            // Records are zero-padded up to a delimiter byte: 2 on the last one, 1 elsewhere.
            var last = plain.size - 1
            while (last >= 0 && plain[last].toInt() == 0) last--
            val isFinal = pos >= payload.size
            require(last >= 0 && plain[last].toInt() == if (isFinal) 2 else 1) {
                "ece: record $sequence has the wrong delimiter"
            }

            out.write(plain, 0, last)
            sequence++
        }

        return out.toByteArray()
    }

    /**
     * HKDF with [algorithm]. An empty [salt] is treated as HashLen zero bytes per RFC 5869.
     */
    fun hkdf(
        ikm: ByteArray,
        salt: ByteArray,
        info: ByteArray,
        length: Int,
        algorithm: String = "HmacSHA256",
    ): ByteArray {
        val mac = Mac.getInstance(algorithm)
        val effectiveSalt = salt.takeIf { it.isNotEmpty() } ?: ByteArray(mac.macLength)
        mac.init(SecretKeySpec(effectiveSalt, algorithm))
        val prk = mac.doFinal(ikm)

        require(length <= 255 * mac.macLength) { "hkdf: length exceeds 255 * HashLen" }

        mac.init(SecretKeySpec(prk, algorithm))
        val okm = ByteArrayOutputStream()
        var previous = ByteArray(0)
        var counter = 1
        while (okm.size() < length) {
            mac.update(previous)
            mac.update(info)
            mac.update(counter.toByte())
            previous = mac.doFinal()
            okm.write(previous)
            counter++
        }
        return okm.toByteArray().copyOf(length)
    }

    /**
     * The raw uncompressed (`0x04 || X || Y`) form of a P-256 [publicKey].
     */
    fun publicKeyToRaw(publicKey: ECPublicKey): ByteArray = byteArrayOf(4) +
        publicKey.w.affineX.toFixedBytes(32) +
        publicKey.w.affineY.toFixedBytes(32)

    /**
     * Inverse of [publicKeyToRaw]. Rejects coordinates that are not on the P-256 curve, because
     * some providers accept an off-curve point when building the key.
     */
    fun rawToPublicKey(raw: ByteArray): ECPublicKey {
        require(raw.size == 65 && raw[0] == 0x04.toByte()) { "Invalid P-256 raw public key" }

        val params = AlgorithmParameters.getInstance("EC").apply {
            init(ECGenParameterSpec(CURVE_NAME))
        }.getParameterSpec(ECParameterSpec::class.java)

        val x = BigInteger(1, raw.copyOfRange(1, 33))
        val y = BigInteger(1, raw.copyOfRange(33, 65))

        // y^2 == x^3 + ax + b (mod p)
        val p = (params.curve.field as ECFieldFp).p
        require(y * y % p == (x * x * x + params.curve.a * x + params.curve.b) % p) {
            "Invalid P-256 raw public key: point is not on the curve"
        }

        return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(ECPoint(x, y), params)) as ECPublicKey
    }
}

private fun BigInteger.toFixedBytes(length: Int): ByteArray {
    val raw = toByteArray()
    return when {
        raw.size == length -> raw
        raw.size > length -> raw.copyOfRange(raw.size - length, raw.size) // drop sign byte
        else -> ByteArray(length - raw.size) + raw
    }
}
