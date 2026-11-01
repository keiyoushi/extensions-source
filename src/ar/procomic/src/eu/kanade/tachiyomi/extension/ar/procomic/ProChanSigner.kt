package eu.kanade.tachiyomi.extension.ar.procomic

import android.util.Base64
import okhttp3.HttpUrl
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.time.Clock

// Reproduces the ProChan app's /api/android/ request signing for a guest with no account.
// A fresh device registers an EC public key under an HMAC signature keyed by the app's static
// secret; every later read is signed with that device's own key.
object ProChanSigner {
    const val READ_UA = "ProChanAndroid/2.1"

    private const val REGISTER_UA = "ProChanAndroid/2.0"
    private const val CLIENT = "android-app"
    private const val VERSION = "91"
    private const val DEVICE_INFO = "Google Pixel 7; Android 14; sdk 34"
    private const val PACKAGE = "com.prochan.android"
    private const val OBFUSCATION = "prochan-hmac-obf-static-v2"
    private const val BLOB_HEX =
        "2bf4f8dfba5f3b541a9e59268c29e0c92b7544f320df653f6e683a0880147eb0" +
            "0be4e68682742b516c9e681ed124ffd60f4922e723d1426d72432c03e03b5989"

    private val random = SecureRandom()

    // K[i] = blob[i] XOR SHA-256(package + 0x00 + obfuscation)[i % 32]
    private val hmacKey by lazy {
        val blob = ByteArray(BLOB_HEX.length / 2) {
            BLOB_HEX.substring(it * 2, it * 2 + 2).toInt(16).toByte()
        }
        val digest = MessageDigest.getInstance("SHA-256").run {
            update(PACKAGE.toByteArray())
            update(0)
            update(OBFUSCATION.toByteArray())
            digest()
        }
        ByteArray(blob.size) { (blob[it].toInt() xor digest[it % digest.size].toInt()).toByte() }
    }

    fun newKeyPair() = KeyPairGenerator.getInstance("EC")
        .apply { initialize(ECGenParameterSpec("secp256r1")) }
        .generateKeyPair()

    fun encodePublicKey(key: PublicKey): String = Base64.encodeToString(key.encoded, Base64.NO_WRAP)

    fun encodePrivateKey(key: PrivateKey): String = Base64.encodeToString(key.encoded, Base64.NO_WRAP)

    fun decodePrivateKey(encoded: String): PrivateKey = KeyFactory.getInstance("EC")
        .generatePrivate(PKCS8EncodedKeySpec(Base64.decode(encoded, Base64.NO_WRAP)))

    // Registration (no device id yet): HMAC the public key into the server.
    fun registerHeaders(url: HttpUrl, body: ByteArray): Map<String, String> {
        val timestamp = now()
        val nonce = newNonce()
        val signature = Mac.getInstance("HmacSHA256")
            .apply { init(SecretKeySpec(hmacKey, "HmacSHA256")) }
            .doFinal(canonical("POST", url, body, timestamp, nonce, deviceId = "").toByteArray())
            .toHex()
        return signatureHeaders(REGISTER_UA, deviceId = null, signature, timestamp, nonce)
    }

    // Every read: ECDSA with the registered device key.
    fun readHeaders(method: String, url: HttpUrl, deviceId: String, key: PrivateKey): Map<String, String> {
        val timestamp = now()
        val nonce = newNonce()
        val signature = Signature.getInstance("SHA256withECDSA")
            .apply {
                initSign(key)
                update(canonical(method, url, body = null, timestamp, nonce, deviceId).toByteArray())
            }
            .sign()
            .toHex()
        return signatureHeaders(READ_UA, deviceId, signature, timestamp, nonce)
    }

    private fun signatureHeaders(userAgent: String, deviceId: String?, signature: String, timestamp: String, nonce: String) = buildMap {
        put("User-Agent", userAgent)
        put("Accept", "application/json")
        put("X-ProChan-Client", CLIENT)
        put("X-ProChan-Version", VERSION)
        put("X-ProChan-Device-Info", DEVICE_INFO)
        put("X-ProChan-Signature", signature)
        put("X-ProChan-Timestamp", timestamp)
        put("X-ProChan-Nonce", nonce)
        deviceId?.let { put("X-ProChan-Device-Id", it) }
    }

    // METHOD, path[?query], sha256(body) hex, timestamp, nonce, deviceId, host, "" (guest auth slot).
    private fun canonical(method: String, url: HttpUrl, body: ByteArray?, timestamp: String, nonce: String, deviceId: String): String {
        val query = url.encodedQuery
        val pathWithQuery = url.encodedPath + if (query.isNullOrEmpty()) "" else "?$query"
        val bodyHash = MessageDigest.getInstance("SHA-256").digest(body ?: ByteArray(0)).toHex()
        return listOf(method, pathWithQuery, bodyHash, timestamp, nonce, deviceId, url.host, "").joinToString("\n")
    }

    private fun now() = (Clock.System.now().toEpochMilliseconds() / 1000).toString()

    private fun newNonce(): String {
        val bytes = ByteArray(18).also(random::nextBytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }
}
