package eu.kanade.tachiyomi.extension.pt.diskusscan

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class DiskusScan : MangaThemesia(
    "Diskus Scan",
    "https://diskusscan.com",
    "pt-BR",
    dateFormat = SimpleDateFormat("MMMMM dd, yyyy", Locale("pt", "BR")),
) {

    // Changed their theme from Madara to MangaThemesia.
    override val versionId = 2

    override val client = super.client.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val headers = request.headers.newBuilder()
                .set("Accept-Encoding", "")
                .build()
            chain.proceed(request.newBuilder().headers(headers).build())
        }
        .rateLimit(2, 1, TimeUnit.SECONDS)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Accept-Language", "pt-BR,en-US;q=0.7,en;q=0.3")
        .set("Alt-Used", baseUrl.substringAfterLast("/"))
        .set("Sec-Fetch-Dest", "document")
        .set("Sec-Fetch-Mode", "navigate")
        .set("Sec-Fetch-Site", "same-origin")
        .set("Sec-Fetch-User", "?1")

    // =========================== Manga Details ============================
    override fun mangaDetailsRequest(manga: SManga): Request {
        val newHeaders = headersBuilder()
            .set("Referer", baseUrl + mangaUrlDirectory)
            .build()

        return GET(baseUrl + manga.url, newHeaders)
    }

    override val seriesAuthorSelector = ".infotable tr:contains(Autor) td:last-child"
    override val seriesDescriptionSelector = ".entry-content[itemprop=description] > *:not([class^=disku])"

    override fun String?.parseStatus() = when (orEmpty().trim().lowercase()) {
        "ativa" -> SManga.ONGOING
        "finalizada" -> SManga.COMPLETED
        "hiato" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================
    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    // =============================== Pages ================================
    override fun imageUrlRequest(page: Page): Request {
        val newHeaders = super.imageUrlRequest(page).headers.newBuilder()
            .set("Sec-Fetch-Dest", "image")
            .set("Sec-Fetch-Mode", "no-cors")
            .set("Sec-Fetch-Site", "cross-site")
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }
}
