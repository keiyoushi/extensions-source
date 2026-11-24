package eu.kanade.tachiyomi.extension.zh.bilibilimanga

import android.util.Base64
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.PrivateKey

// Decrypts the images ImageToken hands out with a `cpx` parameter.
class ImageInterceptor(private val privateKey: PrivateKey) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val url = response.request.url
        val cpx = url.queryParameter("cpx") ?: return response
        if (!response.isSuccessful) return response
        val ts = url.queryParameter("ts")?.toLong(16) ?: throw IOException("Image url without ts")
        val encrypted = Buffer()
        response.body.source().use { it.readAll(encrypted) }
        val image = try {
            ImageCipher.decrypt(encrypted, Base64.decode(cpx, Base64.DEFAULT), ts, privateKey)
        } catch (e: GeneralSecurityException) {
            throw IOException("Image decryption failed", e)
        } catch (e: IllegalArgumentException) {
            throw IOException("Image decryption failed", e)
        }
        return response.newBuilder().body(image.asResponseBody(contentLength = image.size)).build()
    }
}
