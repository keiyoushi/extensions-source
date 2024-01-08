package eu.kanade.tachiyomi.multisrc.mccms

import android.util.Base64
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object DecryptInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val host = request.url.host
        val type = when {
            host.endsWith("bcebos.com") -> 1
            host.endsWith("mhrsrc.com") -> 2
            else -> return response
        }
        val data = decrypt(response.body.bytes(), type)
        val body = data.toResponseBody("image/jpeg".toMediaType())
        return response.newBuilder().body(body).build()
    }

    @Synchronized
    private fun decrypt(input: ByteArray, type: Int): ByteArray {
        val cipher = cipher
        val decodedInput: ByteArray
        when (type) {
            1 -> {
                decodedInput = input
                cipher.init(Cipher.DECRYPT_MODE, key1, iv)
            }
            2 -> {
                decodedInput = Base64.decode(input, Base64.DEFAULT)
                cipher.init(Cipher.DECRYPT_MODE, key2, iv2)
            }
            else -> return input
        }
        return cipher.doFinal(decodedInput)
    }

    private val cipher by lazy(LazyThreadSafetyMode.NONE) { Cipher.getInstance("DESede/CBC/PKCS5Padding") }
    private val key1 by lazy(LazyThreadSafetyMode.NONE) { SecretKeySpec("OW84U8Eerdb99rtsTXWSILDO".toByteArray(), "DESede") }
    private val key2 by lazy(LazyThreadSafetyMode.NONE) { SecretKeySpec("ys6n2GvmgEyB3rELDX1gaTBf".toByteArray(), "DESede") }
    private val iv by lazy(LazyThreadSafetyMode.NONE) { IvParameterSpec("SK8bncVu".toByteArray()) }
    private val iv2 by lazy(LazyThreadSafetyMode.NONE) { IvParameterSpec("2pnB3NI2".toByteArray()) }
}
