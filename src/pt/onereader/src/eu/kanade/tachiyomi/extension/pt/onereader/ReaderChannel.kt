package eu.kanade.tachiyomi.extension.pt.onereader

import android.util.Base64
import keiyoushi.lib.ece.Ece
import keiyoushi.utils.aesDecrypt
import keiyoushi.utils.aesGcm
import keiyoushi.utils.hmacSha256
import keiyoushi.utils.sha256
import okhttp3.Headers
import okhttp3.HttpUrl
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import javax.crypto.KeyAgreement
import kotlin.time.Clock

/** The site reader's ECDH P-256 channel: page request proofs and image key unwrapping. */
internal class ReaderChannel {

    private val keyPair = KeyPairGenerator.getInstance("EC").run {
        initialize(ECGenParameterSpec("secp256r1"))
        generateKeyPair()
    }

    private val clientKey = Ece.publicKeyToRaw(keyPair.public as ECPublicKey).toBase64Url()

    fun addTransport(builder: Headers.Builder): Headers.Builder = builder
        .set("X-OneReader-Client-Key", clientKey)
        .set("X-OneReader-Key-Transport", TRANSPORT)

    fun addProof(builder: Headers.Builder, pageUrl: HttpUrl, serverKey: String): Headers.Builder {
        val timestamp = Clock.System.now().toEpochMilliseconds().toString()
        val nonce = ByteArray(16).also(SecureRandom()::nextBytes).toBase64Url()
        val canonical = listOf(PROOF_LABEL, "GET", pageUrl.encodedPath, pageUrl.queryParameter("g")!!, timestamp, nonce)
            .joinToString("\n")
        val proofKey = ("$PROOF_KEY_LABEL\n".encodeToByteArray() + sharedSecret(serverKey)).sha256()

        return addTransport(builder)
            .set("X-OneReader-Proof-Ts", timestamp)
            .set("X-OneReader-Proof-Nonce", nonce)
            .set("X-OneReader-Proof", canonical.hmacSha256(proofKey).toBase64Url())
    }

    fun unwrapImageKey(keyWrap: KeyWrapDto): ByteArray {
        val label = "$WRAP_LABEL\n"
        val context = "\n${keyWrap.context}".encodeToByteArray()
        val wrappingKey = (label.encodeToByteArray() + sharedSecret(keyWrap.serverKey) + context).sha256()
        val cipher = aesGcm(wrappingKey, keyWrap.iv.decodeBase64Url()).apply {
            updateAAD((label + keyWrap.context).encodeToByteArray())
        }

        return keyWrap.payload.decodeBase64Url().aesDecrypt(cipher)
    }

    private fun sharedSecret(serverKey: String): ByteArray = KeyAgreement.getInstance("ECDH").run {
        init(keyPair.private)
        doPhase(Ece.rawToPublicKey(serverKey.decodeBase64Url()), true)
        generateSecret()
    }

    companion object {
        private const val TRANSPORT = "ecdh-p256-aesgcm-v1"
        private const val PROOF_LABEL = "oneReader-scan-proof-v1"
        private const val PROOF_KEY_LABEL = "oneReader-scan-proof-hmac-v1"
        private const val WRAP_LABEL = "oneReader-keywrap-ecdh-p256-v1"
    }
}

internal fun String.decodeBase64Url(): ByteArray = Base64.decode(this, BASE64_URL_FLAGS)

private fun ByteArray.toBase64Url(): String = Base64.encodeToString(this, BASE64_URL_FLAGS)

private const val BASE64_URL_FLAGS = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
