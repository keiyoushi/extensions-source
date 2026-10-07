package eu.kanade.tachiyomi.extension.ar.dilar

import android.util.Base64
import keiyoushi.lib.ece.Ece
import keiyoushi.lib.secretstream.ChaCha20
import keiyoushi.lib.secretstream.Poly1305
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

// ECIES response decryption, protocol versions 1-14.
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

            else -> error("Unsupported encryption protocol version: ${data.v}")
        }

        val keyMaterial = Ece.hkdf(
            ikm = sharedSecret,
            salt = spec.salt,
            info = spec.info,
            length = if (spec.derivedNonce) 44 else 32,
            algorithm = spec.hash,
        )
        val key = keyMaterial.copyOf(32)
        val nonce = if (spec.derivedNonce) keyMaterial.copyOfRange(32, 44) else iv

        if (spec.chacha) {
            return chacha20Poly1305Decrypt(key, nonce, ct, tag, spec.aad ?: ByteArray(0)).toString(Charsets.UTF_8)
        }

        if (spec.gcmsiv) {
            return AesGcmSiv.decrypt(key, nonce, ct + tag, spec.aad ?: ByteArray(0)).toString(Charsets.UTF_8)
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

    private class CipherSpec(
        val salt: ByteArray,
        val info: ByteArray,
        val hash: String = "HmacSHA256",
        val derivedNonce: Boolean = false,
        val aad: ByteArray? = null,
        val chacha: Boolean = false,
        val gcmsiv: Boolean = false,
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
