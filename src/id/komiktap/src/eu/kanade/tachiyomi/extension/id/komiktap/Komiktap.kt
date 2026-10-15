package eu.kanade.tachiyomi.extension.id.komiktap

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import keiyoushi.annotation.Source
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.asResponseBody

@Source
abstract class Komiktap : MangaThemesia() {

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            val mime = response.headers["Content-Type"]
            if (response.isSuccessful) {
                if (mime != "application/octet-stream") {
                    return@addInterceptor response
                }
                // Fix image content type
                val type = IMG_CONTENT_TYPE.toMediaType()
                val body = response.body.source().asResponseBody(type)
                return@addInterceptor response.newBuilder().body(body).build()
            }
            response
        }
    }
}

private const val IMG_CONTENT_TYPE = "image/jpeg"
