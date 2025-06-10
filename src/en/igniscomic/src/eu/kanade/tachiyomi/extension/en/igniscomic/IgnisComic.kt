package eu.kanade.tachiyomi.extension.en.igniscomic

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import okhttp3.OkHttpClient

class IgnisComic :
    MangaThemesia(
        "Ignis Comic",
        "https://manhuaga.com",
        "en",
    ) {
    override val client: OkHttpClient = super.client.newBuilder()
        .addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            response.takeIf { it.code < 500 }
                ?: response.newBuilder()
                    .code(200)
                    .build()
        }
        .build()
}
