package eu.kanade.tachiyomi.extension.all.izneo

import android.util.Base64
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.buffer
import okio.cipherSource
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object ImageInterceptor : Interceptor {
    private inline val AES: Cipher
        get() = Cipher.getInstance("AES/CBC/PKCS7Padding")

    override fun intercept(chain: Interceptor.Chain): Response {
        val url = chain.request().url
        val key = url.queryParameter("key")
            ?: return chain.proceed(chain.request())
        return chain.proceed(
            chain.request().newBuilder().url(
                url.newBuilder()
                    .removeAllQueryParameters("key")
                    .removeAllQueryParameters("iv")
                    .build(),
            ).build(),
        ).decode(key.atob(), url.queryParameter("iv")!!.atob())
    }

    private fun Response.decode(key: ByteArray, iv: ByteArray) = AES.let {
        it.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        newBuilder().body(body.source().cipherSource(it).buffer().asResponseBody("image/jpeg".toMediaType())).build()
    }

    private fun String.atob() = Base64.decode(this, Base64.URL_SAFE)
}
