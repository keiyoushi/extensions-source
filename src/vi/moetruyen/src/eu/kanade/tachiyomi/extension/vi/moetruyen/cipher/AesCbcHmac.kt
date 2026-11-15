package eu.kanade.tachiyomi.extension.vi.moetruyen.cipher

import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * IMGX v4 profile p06: AES-256-CBC + HMAC-SHA512.
 * Derived key HKDF(contentKey, "IMGX-v4.p06") = macKey(32) || encKey(32).
 * Body: iv(16) || ciphertext || mac(32).
 * MAC = HMAC-SHA512(macKey, aad||iv||ct||be64(aadBits*8)).take(32).
 */
internal object AesCbcHmac {

    fun decrypt(key: ByteArray, body: ByteArray, aad: ByteArray): ByteArray {
        require(key.size == 64) { "IMGX p06 key must be 64 bytes" }
        require(body.size > 48) { "IMGX p06 payload too short" }
        val iv = body.copyOfRange(0, 16)
        val ct = body.copyOfRange(16, body.size - 32)
        val mac = body.copyOfRange(body.size - 32, body.size)
        val macKey = key.copyOfRange(0, 32)
        val encKey = key.copyOfRange(32, 64)
        try {
            val lenBlock = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
                .putLong(0, aad.size * 8L)
                .array()
            val macInput = aad + iv + ct + lenBlock
            val macInstance = Mac.getInstance("HmacSHA512")
            macInstance.init(SecretKeySpec(macKey, "HmacSHA512"))
            val expected = macInstance.doFinal(macInput).copyOfRange(0, 32)
            var diff = 0
            for (i in 0 until 32) diff = diff or (mac[i].toInt() xor expected[i].toInt())
            require(diff == 0) { "IMGX p06 authentication failed" }
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(encKey, "AES"), IvParameterSpec(iv))
            return cipher.doFinal(ct)
        } finally {
            macKey.fill(0)
            encKey.fill(0)
        }
    }
}
