package eu.kanade.tachiyomi.extension.en.igniscomic

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import okhttp3.OkHttpClient
import java.net.HttpURLConnection.HTTP_INTERNAL_ERROR
import java.net.HttpURLConnection.HTTP_OK

class IgnisComic : MangaThemesia(
    "Ignis Comic",
    "https://manhuaga.com",
    "en",
) {
    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            response.takeIf { it.code < HTTP_INTERNAL_ERROR }
                ?: response.newBuilder()
                    .code(HTTP_OK)
                    .build()
        }
        .build()
}
