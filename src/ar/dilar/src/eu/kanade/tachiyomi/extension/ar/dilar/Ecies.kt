package eu.kanade.tachiyomi.extension.ar.dilar

import android.util.Base64
import keiyoushi.lib.aessiv.AesGcmSiv
import keiyoushi.lib.aessiv.AesSiv
import keiyoushi.lib.ece.Ece
import keiyoushi.lib.secretstream.ChaCha20
import keiyoushi.lib.secretstream.Poly1305
import keiyoushi.utils.readIntLittleEndian
import keiyoushi.utils.writeIntLittleEndian
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

// ECIES response decryption, protocol versions 1-16.
internal class Ecies {
    private val ecKeyPair: KeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec(CURVE_NAME))
    }.generateKeyPair()

    private val clientPubRaw: ByteArray = Ece.publicKeyToRaw(ecKeyPair.public as ECPublicKey)

    val clientPubB64: String =
        Base64.encodeToString(clientPubRaw, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    fun decrypt(data: EncryptedResponseDto): String {
        val serverPubRaw = Base64.decode(data.epk, Base64.URL_SAFE)
        val serverPubKey = Ece.rawToPublicKey(serverPubRaw)
        val iv = Base64.decode(data.iv, Base64.URL_SAFE)
        val ct = Base64.decode(data.ct, Base64.URL_SAFE)
        val tag = Base64.decode(data.tag, Base64.URL_SAFE)

        val sharedSecret = KeyAgreement.getInstance("ECDH").apply {
            init(ecKeyPair.private)
            doPhase(serverPubKey, true)
        }.generateSecret()

        val spec = when (data.v) {
            1 -> CipherSpec(
                clientPubRaw + serverPubRaw,
                "dilar.response.ecies.v1|${data.e}".toByteArray(),
            )

            2 -> CipherSpec(
                serverPubRaw + clientPubRaw,
                "dilar.response.ecies.v2|${data.e}".toByteArray(),
            )

            3 -> CipherSpec(
                sha256(serverPubRaw + clientPubRaw),
                "dilar.response.ecies.v3|${data.e}".toByteArray(),
            )

            4 -> CipherSpec(
                sha256(clientPubRaw + serverPubRaw + iv),
                "dilar.response.ecies.v4|${data.e}|${data.iv}"
                    .toByteArray(),
            )

            5 -> CipherSpec(
                hmac(
                    key = iv,
                    data = serverPubRaw + clientPubRaw,
                ),
                "dilar.response.ecies.v5|${data.e}".toByteArray(),
            )

            6 -> CipherSpec(
                sha256(sha256(clientPubRaw) + sha256(serverPubRaw) + iv),
                "dilar.response.ecies.v6|${data.e}|${data.iv}".toByteArray(),
            )

            7 -> CipherSpec(
                Ece.hkdf(
                    ikm = iv,
                    salt = serverPubRaw,
                    info = "dilar.response.ecies.v7.salt".toByteArray(),
                    length = 32,
                ),
                "dilar.response.ecies.v7|${data.e}".toByteArray(),
            )

            8 -> CipherSpec(
                sha256(joinBytes(lengthPrefixed(clientPubRaw), lengthPrefixed(serverPubRaw), lengthPrefixed(iv))),
                "dilar.response.ecies.v8|${data.e}|${iv.toHex()}".toByteArray(),
            )

            9 -> CipherSpec(
                hmac(
                    key = iv,
                    data = joinBytes(lengthPrefixed(serverPubRaw), lengthPrefixed(clientPubRaw)),
                    algorithm = "HmacSHA512",
                ).copyOfRange(0, 32),
                "dilar.response.ecies.v9|${data.e}|${sha256(iv).toHex().take(16)}".toByteArray(),
            )

            10 -> CipherSpec(
                sha512(joinBytes(lengthPrefixed(clientPubRaw), lengthPrefixed(serverPubRaw), lengthPrefixed(iv))),
                "dilar.response.ecies.v10|${data.e}|${sha512(iv).toHex().take(24)}".toByteArray(),
                hash = "HmacSHA512",
            )

            11 -> CipherSpec(
                hmac(
                    key = serverPubRaw,
                    data = joinBytes(lengthPrefixed(iv), lengthPrefixed(clientPubRaw)),
                    algorithm = "HmacSHA512",
                ),
                "dilar.response.ecies.v11|${data.e}|${sha384(iv).toBase64Url().take(22)}".toByteArray(),
                hash = "HmacSHA512",
                derivedNonce = true,
            )

            12 -> CipherSpec(
                clientKeyedSalt(serverPubRaw, iv).copyOf(32),
                "dilar.response.ecies.v12|${data.e}|${sha256(lengthPrefixed(iv)).toBase64Url().take(22)}".toByteArray(),
                hash = "HmacSHA384",
                derivedNonce = true,
                aad = aad("dilar.response.ecies.v12", data, serverPubRaw, iv, ct.size),
            )

            13 -> CipherSpec(
                clientKeyedSalt(serverPubRaw, iv),
                "dilar.response.ecies.v13|${data.e}|${sha512(lengthPrefixed(iv)).toBase64Url().take(22)}".toByteArray(),
                hash = "HmacSHA512",
                derivedNonce = true,
                aad = aad("dilar.response.ecies.v13", data, serverPubRaw, iv, ct.size),
                chacha = true,
            )

            14 -> CipherSpec(
                serverKeyedSalt(serverPubRaw, iv),
                "dilar.response.ecies.v14|${data.e}|${sha512(lengthPrefixed(iv)).toBase64Url().take(22)}".toByteArray(),
                hash = "HmacSHA512",
                derivedNonce = true,
                aad = aad("dilar.response.ecies.v14", data, serverPubRaw, iv, ct.size),
                gcmsiv = true,
            )

            15 -> CipherSpec(
                ivKeyedSalt(serverPubRaw, iv),
                "dilar.response.ecies.v15|${data.e}|${sha512(joinBytes(lengthPrefixed(iv), lengthPrefixed(serverPubRaw))).toBase64Url().take(22)}".toByteArray(),
                hash = "HmacSHA512",
                derivedNonce = true,
                aad = aad("dilar.response.ecies.v15", data, serverPubRaw, iv, ct.size),
                aessiv = true,
                keyBytes = 64,
            )

            16 -> CipherSpec(
                hmac(
                    key = joinBytes(clientPubRaw, iv),
                    data = lengthPrefixed(serverPubRaw),
                    algorithm = "HmacSHA384",
                ),
                "dilar.response.ecies.v16|${data.e}|${sha384(joinBytes(lengthPrefixed(iv), lengthPrefixed(serverPubRaw))).toBase64Url().take(22)}".toByteArray(),
                hash = "HmacSHA384",
                derivedNonce = true,
                aad = aad("dilar.response.ecies.v16", data, serverPubRaw, iv, ct.size),
                xchacha = true,
                nonceBytes = 24,
            )

            else -> error("Unsupported encryption protocol version: ${data.v}")
        }

        val keyMaterial = Ece.hkdf(
            ikm = sharedSecret,
            salt = spec.salt,
            info = spec.info,
            length = if (spec.derivedNonce) spec.keyBytes + spec.nonceBytes else 32,
            algorithm = spec.hash,
        )
        val key = keyMaterial.copyOf(spec.keyBytes)
        val nonce = if (spec.derivedNonce) keyMaterial.copyOfRange(spec.keyBytes, spec.keyBytes + spec.nonceBytes) else iv

        if (spec.chacha) {
            return chacha20Poly1305Decrypt(key, nonce, ct, tag, spec.aad ?: ByteArray(0)).toString(Charsets.UTF_8)
        }

        if (spec.gcmsiv) {
            return AesGcmSiv.decrypt(key, nonce, ct + tag, spec.aad ?: ByteArray(0)).toString(Charsets.UTF_8)
        }

        if (spec.aessiv) {
            return AesSiv.decrypt(key, tag + ct, spec.aad ?: ByteArray(0), nonce).toString(Charsets.UTF_8)
        }

        if (spec.xchacha) {
            return xchacha20Poly1305Decrypt(key, nonce, ct, tag, spec.aad ?: ByteArray(0)).toString(Charsets.UTF_8)
        }

        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            spec.aad?.let { updateAAD(it) }
        }

        return cipher.doFinal(ct + tag).toString(Charsets.UTF_8)
    }

    // RFC 8439 AEAD, built on the ChaCha20/Poly1305 primitives from lib:secretstream
    // (the platform Cipher only exposes ChaCha20-Poly1305 from API 28).
    private fun chacha20Poly1305Decrypt(
        key: ByteArray,
        nonce: ByteArray,
        ct: ByteArray,
        tag: ByteArray,
        aad: ByteArray,
    ): ByteArray {
        val otk = ByteArray(32)
        ChaCha20.streamIETF(otk, otk.size, nonce, key)

        val plain = ByteArray(ct.size)
        ChaCha20.streamIETFXorIC(plain, ct, ct.size, nonce, 1, key)

        val aadPad = (16 - aad.size % 16) % 16
        val ctPad = (16 - ct.size % 16) % 16
        val macData = joinBytes(
            aad,
            ByteArray(aadPad),
            ct,
            ByteArray(ctPad),
            le64(aad.size.toLong()),
            le64(ct.size.toLong()),
        )

        val state = Poly1305.State()
        Poly1305.init(state, otk)
        Poly1305.update(state, macData, macData.size)
        val mac = ByteArray(16)
        Poly1305.finalizeMAC(state, mac)

        require(mac.contentEquals(tag)) { "ChaCha20-Poly1305 authentication failed" }
        return plain
    }

    // XChaCha20-Poly1305 AEAD: HChaCha20 derives a subkey from the first 16 bytes
    // of the 24-byte nonce, then delegates to ChaCha20-Poly1305 with the subkey
    // and a 12-byte nonce built from 4 zero bytes + the last 8 nonce bytes.
    private fun xchacha20Poly1305Decrypt(
        key: ByteArray,
        nonce: ByteArray,
        ct: ByteArray,
        tag: ByteArray,
        aad: ByteArray,
    ): ByteArray {
        val subkey = hchacha20(key, nonce)
        val subNonce = ByteArray(12)
        System.arraycopy(nonce, 16, subNonce, 4, 8)
        return chacha20Poly1305Decrypt(subkey, subNonce, ct, tag, aad)
    }

    // HChaCha20: 20-round ChaCha core on (key, nonce[0..15]) returning 32 bytes
    // of internal state (words 0-3 and 12-15).
    private fun hchacha20(key: ByteArray, nonce: ByteArray): ByteArray {
        var x0 = 0x61707865
        var x1 = 0x3320646e
        var x2 = 0x79622d32
        var x3 = 0x6b206574
        var x4 = key.readIntLittleEndian(0)
        var x5 = key.readIntLittleEndian(4)
        var x6 = key.readIntLittleEndian(8)
        var x7 = key.readIntLittleEndian(12)
        var x8 = key.readIntLittleEndian(16)
        var x9 = key.readIntLittleEndian(20)
        var x10 = key.readIntLittleEndian(24)
        var x11 = key.readIntLittleEndian(28)
        var x12 = nonce.readIntLittleEndian(0)
        var x13 = nonce.readIntLittleEndian(4)
        var x14 = nonce.readIntLittleEndian(8)
        var x15 = nonce.readIntLittleEndian(12)

        repeat(10) {
            x0 += x4
            x12 = (x12 xor x0).rotl(16)
            x8 += x12
            x4 = (x4 xor x8).rotl(12)
            x0 += x4
            x12 = (x12 xor x0).rotl(8)
            x8 += x12
            x4 = (x4 xor x8).rotl(7)

            x1 += x5
            x13 = (x13 xor x1).rotl(16)
            x9 += x13
            x5 = (x5 xor x9).rotl(12)
            x1 += x5
            x13 = (x13 xor x1).rotl(8)
            x9 += x13
            x5 = (x5 xor x9).rotl(7)

            x2 += x6
            x14 = (x14 xor x2).rotl(16)
            x10 += x14
            x6 = (x6 xor x10).rotl(12)
            x2 += x6
            x14 = (x14 xor x2).rotl(8)
            x10 += x14
            x6 = (x6 xor x10).rotl(7)

            x3 += x7
            x15 = (x15 xor x3).rotl(16)
            x11 += x15
            x7 = (x7 xor x11).rotl(12)
            x3 += x7
            x15 = (x15 xor x3).rotl(8)
            x11 += x15
            x7 = (x7 xor x11).rotl(7)

            x0 += x5
            x15 = (x15 xor x0).rotl(16)
            x10 += x15
            x5 = (x5 xor x10).rotl(12)
            x0 += x5
            x15 = (x15 xor x0).rotl(8)
            x10 += x15
            x5 = (x5 xor x10).rotl(7)

            x1 += x6
            x12 = (x12 xor x1).rotl(16)
            x11 += x12
            x6 = (x6 xor x11).rotl(12)
            x1 += x6
            x12 = (x12 xor x1).rotl(8)
            x11 += x12
            x6 = (x6 xor x11).rotl(7)

            x2 += x7
            x13 = (x13 xor x2).rotl(16)
            x8 += x13
            x7 = (x7 xor x8).rotl(12)
            x2 += x7
            x13 = (x13 xor x2).rotl(8)
            x8 += x13
            x7 = (x7 xor x8).rotl(7)

            x3 += x4
            x14 = (x14 xor x3).rotl(16)
            x9 += x14
            x4 = (x4 xor x9).rotl(12)
            x3 += x4
            x14 = (x14 xor x3).rotl(8)
            x9 += x14
            x4 = (x4 xor x9).rotl(7)
        }

        val out = ByteArray(32)
        out.writeIntLittleEndian(0, x0)
        out.writeIntLittleEndian(4, x1)
        out.writeIntLittleEndian(8, x2)
        out.writeIntLittleEndian(12, x3)
        out.writeIntLittleEndian(16, x12)
        out.writeIntLittleEndian(20, x13)
        out.writeIntLittleEndian(24, x14)
        out.writeIntLittleEndian(28, x15)
        return out
    }

    private fun Int.rotl(n: Int): Int = (this shl n) or (this ushr (32 - n))

    private class CipherSpec(
        val salt: ByteArray,
        val info: ByteArray,
        val hash: String = "HmacSHA256",
        val derivedNonce: Boolean = false,
        val aad: ByteArray? = null,
        val chacha: Boolean = false,
        val gcmsiv: Boolean = false,
        val aessiv: Boolean = false,
        val xchacha: Boolean = false,
        val keyBytes: Int = 32,
        val nonceBytes: Int = 12,
    )

    // Shared by v12 and v13; v12 truncates the result to 32 bytes.
    private fun clientKeyedSalt(serverPubRaw: ByteArray, iv: ByteArray): ByteArray = hmac(
        key = clientPubRaw,
        data = joinBytes(lengthPrefixed(serverPubRaw), lengthPrefixed(iv)),
        algorithm = "HmacSHA512",
    )

    // v14 keys the salt with the server's public key instead of the client's.
    private fun serverKeyedSalt(serverPubRaw: ByteArray, iv: ByteArray): ByteArray = hmac(
        key = serverPubRaw,
        data = joinBytes(lengthPrefixed(clientPubRaw), lengthPrefixed(iv)),
        algorithm = "HmacSHA512",
    )

    // v15 keys the salt with the iv instead of public keys.
    private fun ivKeyedSalt(serverPubRaw: ByteArray, iv: ByteArray): ByteArray = hmac(
        key = iv,
        data = joinBytes(lengthPrefixed(clientPubRaw), lengthPrefixed(serverPubRaw)),
        algorithm = "HmacSHA512",
    )

    private fun aad(
        label: String,
        data: EncryptedResponseDto,
        serverPubRaw: ByteArray,
        iv: ByteArray,
        ctSize: Int,
    ): ByteArray {
        val size = u32(ctSize)

        return sha256(
            joinBytes(
                lengthPrefixed(label.toByteArray()),
                lengthPrefixed(data.v.toString().toByteArray()),
                lengthPrefixed(data.e.toString().toByteArray()),
                lengthPrefixed(serverPubRaw),
                lengthPrefixed(iv),
                lengthPrefixed(size),
            ),
        )
    }

    private fun lengthPrefixed(bytes: ByteArray): ByteArray = joinBytes(u16(bytes.size), bytes)

    private fun u16(n: Int): ByteArray = byteArrayOf(((n shr 8) and 0xFF).toByte(), (n and 0xFF).toByte())

    private fun u32(n: Int): ByteArray = byteArrayOf((n ushr 24).toByte(), (n ushr 16).toByte(), (n ushr 8).toByte(), n.toByte())

    private fun le64(n: Long): ByteArray = ByteArray(8) { ((n ushr (8 * it)) and 0xFF).toByte() }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun ByteArray.toBase64Url(): String = Base64.encodeToString(this, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    private fun joinBytes(vararg parts: ByteArray): ByteArray {
        val size = parts.sumOf { it.size }
        val result = ByteArray(size)
        var offset = 0
        for (p in parts) {
            System.arraycopy(p, 0, result, offset, p.size)
            offset += p.size
        }
        return result
    }

    private fun hmac(
        key: ByteArray,
        data: ByteArray,
        algorithm: String = "HmacSHA256",
    ): ByteArray = Mac.getInstance(algorithm).apply {
        init(SecretKeySpec(key, algorithm))
    }.doFinal(data)

    private fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    private fun sha384(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-384").digest(data)

    private fun sha512(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-512").digest(data)

    companion object {
        private const val CURVE_NAME = "secp256r1"
    }
}
