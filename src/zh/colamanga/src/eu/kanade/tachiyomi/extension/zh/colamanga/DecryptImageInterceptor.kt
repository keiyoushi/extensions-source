package eu.kanade.tachiyomi.extension.zh.colamanga

import android.util.Log
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object DecryptImageInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()

        var imgKey = ""
        Log.i("ColaManga", "request.url: ${request.url}")
        // 通过 queryParameter 获取 imgKey
        if (request.url.queryParameterNames.contains("imgKey")) {
            imgKey = request.url.queryParameter("imgKey")!!
            Log.i("ColaManga", "imgKey: $imgKey")
            request =
                request.newBuilder()
                    .url(
                        request.url
                            .newBuilder()
                            .removeAllQueryParameters("imgKey")
                            .build(),
                    )
                    .build()
        }

        val originalResponse: Response = chain.proceed(request)
        if (originalResponse.request.url.toString().endsWith("enc.webp")) {
            // Decrypt images in mangas
            Log.i("ColaManga", "Decrypting image")
            Log.i("ColaManga", "originalResponse.code: ${originalResponse.code}")

            if (originalResponse.code != 200) {
                return originalResponse
            }

            val orgBody = originalResponse.body.bytes()
            val aesKey = SecretKeySpec(imgKey.toByteArray(), "AES")
            val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                aesKey,
                IvParameterSpec("0000000000000000".toByteArray()),
            )
            val result = cipher.doFinal(orgBody)
            val newBody = result.toResponseBody("image/webp".toMediaTypeOrNull())
            return originalResponse.newBuilder().body(newBody).build()
        } else {
            return originalResponse
        }
    }
}
