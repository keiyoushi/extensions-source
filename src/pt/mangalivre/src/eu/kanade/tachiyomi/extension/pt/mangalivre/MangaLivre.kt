package eu.kanade.tachiyomi.extension.pt.mangalivre

import eu.kanade.tachiyomi.multisrc.madara.Madara
import okhttp3.Interceptor
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class MangaLivre : Madara(
    "Manga Livre",
    "https://mangalivre.tv",
    "pt-BR",
    SimpleDateFormat("MMMM dd, yyyy", Locale("pt")),
) {

    private class MangaLivreInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            var request = chain.request()
            val url = request.url.toString()

            // APENAS REMOVE ?style=list - MANTÉM .tv
            if (url.contains("?style=list")) {
                val newUrl = url.replace("?style=list", "")
                request = request.newBuilder()
                    .url(newUrl)
                    .build()
            }

            return chain.proceed(request)
        }
    }

    override val client = super.client.newBuilder()
        .addInterceptor(MangaLivreInterceptor())
        .build()

    override val useNewChapterEndpoint = true
    override val id: Long = 2834885536325274328
}
