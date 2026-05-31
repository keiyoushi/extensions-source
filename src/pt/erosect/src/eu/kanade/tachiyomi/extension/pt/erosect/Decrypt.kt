package eu.kanade.tachiyomi.extension.pt.erosect

import android.util.Base64
import android.util.Base64InputStream
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okio.BufferedSource
import okio.buffer
import okio.cipherSource
import okio.source
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

internal object Decrypt {
    private const val JWT_CRYPTO_PEPPER = "chapter_jwt_default_pepper"
    private const val CHAPTER_LAYER_KEY = "9b1c5f6c0e4b7f2d8d3a41e5c9a7b2f0"

    fun isEncryptedPayload(payload: JsonObject): Boolean = payload["iv"]?.jsonPrimitive?.contentOrNull != null &&
        payload["data"]?.jsonPrimitive?.contentOrNull != null

    fun chapterPayload(payload: JsonObject, token: String): JsonObject {
        val innerPayload = jsonPayload(payload, CHAPTER_LAYER_KEY)
        return jsonPayload(innerPayload, token.sessionKey())
    }

    fun imageSource(payload: JsonObject, token: String): BufferedSource {
        val source = Base64InputStream(
            payload.requiredString("data").byteInputStream(),
            Base64.DEFAULT,
        ).source()

        return source.cipherSource(payload.cipher(token.sessionKey())).buffer()
    }

    private fun jsonPayload(payload: JsonObject, key: String): JsonObject = bytes(payload, key).toString(Charsets.UTF_8).parseAs<JsonObject>()

    private fun bytes(payload: JsonObject, key: String): ByteArray {
        val cipherText = Base64.decode(payload.requiredString("data"), Base64.DEFAULT)
        return payload.cipher(key).doFinal(cipherText)
    }

    private fun JsonObject.cipher(key: String): Cipher {
        val iv = Base64.decode(requiredString("iv"), Base64.DEFAULT)
        val secretKey = SecretKeySpec(
            MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8)),
            "AES",
        )

        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        }
    }

    private fun JsonObject.requiredString(name: String): String = this[name]?.jsonPrimitive?.contentOrNull ?: error("Campo criptografado ausente: $name")

    private fun String.sessionKey(): String = "$this:$JWT_CRYPTO_PEPPER"
}
