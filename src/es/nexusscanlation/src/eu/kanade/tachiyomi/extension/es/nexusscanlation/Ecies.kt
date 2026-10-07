package eu.kanade.tachiyomi.extension.es.nexusscanlation

import keiyoushi.lib.ece.Ece
import keiyoushi.utils.parseAs
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val CURVE_NAME = "secp256r1"
private const val HKDF_INFO = "nexus:lector:v1"

/**
 * ECDH P-256 handshake for protected chapters. The reader's public key goes out in the `X-Rs`
 * header and the server answers with `r`, a blob wrapped for that key. Unwrapping it yields the
 * per-page scramble seeds and the AES keys for RFC 8188-encrypted pages.
 */
internal class Ecies {
    private val keyPair: KeyPair = KeyPairGenerator.getInstance("EC")
        .apply { initialize(ECGenParameterSpec(CURVE_NAME)) }
        .generateKeyPair()

    val publicKey: String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(Ece.publicKeyToRaw(keyPair.public as ECPublicKey))

    fun unwrap(wrapped: String): UnwrappedKeysDto {
        val raw = Base64.getUrlDecoder().decode(wrapped)

        val sharedSecret = KeyAgreement.getInstance("ECDH").apply {
            init(keyPair.private)
            doPhase(Ece.rawToPublicKey(raw.copyOfRange(0, 65)), true)
        }.generateSecret()

        val key = Ece.hkdf(sharedSecret, ByteArray(0), HKDF_INFO.toByteArray(), 16)
        val plain = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                GCMParameterSpec(128, raw.copyOfRange(65, 77)),
            )
            doFinal(raw.copyOfRange(77, raw.size))
        }

        return String(plain, Charsets.UTF_8).parseAs()
    }
}
