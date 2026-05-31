package eu.kanade.tachiyomi.extension.en.emaqi

import android.util.Base64
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.buffer
import okio.cipherSource
import java.security.KeyFactory
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

class ImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val fragment = request.url.fragment

        if (!response.isSuccessful || fragment.isNullOrEmpty() || !fragment.contains(":") || request.url.pathSegments.first() == "graphql") return response

        val (privateKeyStr, hash) = fragment.split(":")
        val privateKeyBytes = Base64.decode(privateKeyStr, Base64.NO_WRAP or Base64.URL_SAFE)
        val privateKey = KEY_FACTORY.generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))
        val hashBytes = Base64.decode(hash, Base64.DEFAULT)

        val oaepCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
        oaepCipher.init(Cipher.DECRYPT_MODE, privateKey, OAEP_PARAMS)
        val aesKey = oaepCipher.doFinal(hashBytes)

        val source = response.body.source()
        val magicByte = source.readByte().toInt()
        val cipher: Cipher

        if (magicByte == 2) {
            source.skip(1)
            val iv = source.readByteArray(16)
            cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), GCMParameterSpec(128, iv))
        } else {
            val iv = ByteArray(16)
            iv[0] = magicByte.toByte()
            val rest = source.readByteArray(15)
            System.arraycopy(rest, 0, iv, 1, 15)
            cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), IvParameterSpec(iv))
        }

        val body = source.cipherSource(cipher).buffer().asResponseBody(response.body.contentType())

        return response.newBuilder()
            .body(body)
            .build()
    }

    companion object {
        private val KEY_FACTORY: KeyFactory = KeyFactory.getInstance("RSA")
        private val OAEP_PARAMS = OAEPParameterSpec(
            "SHA-256",
            "MGF1",
            MGF1ParameterSpec.SHA256,
            PSource.PSpecified.DEFAULT,
        )
    }
}
