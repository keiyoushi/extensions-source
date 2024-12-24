package eu.kanade.tachiyomi.extension.id.siimanga

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import okhttp3.internal.http.HTTP_INTERNAL_SERVER_ERROR
import okhttp3.internal.http.HTTP_OK

class Siikomik : MangaThemesia(
    "Siikomik",
    "https://siikomik.lat",
    "id",
) {
    override val versionId = 2

    override val client = super.client.newBuilder()
        .rateLimit(3)
        .addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            if (response.code == HTTP_INTERNAL_SERVER_ERROR) {
                return@addInterceptor response.newBuilder()
                    .code(HTTP_OK)
                    .build()
            }
            response
        }
        .build()

    override val hasProjectPage = true
}
