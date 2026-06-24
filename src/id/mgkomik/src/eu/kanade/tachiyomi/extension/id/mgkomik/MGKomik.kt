package eu.kanade.tachiyomi.extension.id.mgkomik

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.network.rateLimit
import okhttp3.Request
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class MGKomik :
    Madara(
        "MG Komik",
        "https://id.mgkomik.cc",
        "id",
        SimpleDateFormat("dd MMM yy", Locale.US),
    ) {

    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override val mangaSubString = "komik"

    override val chapterUrlSuffix = ""

    // HEADERS
    override fun headersBuilder() = super.headersBuilder().apply {
        set("User-Agent", USER_AGENT)
        set("Sec-CH-UA-Model", "\"\"")
    }

    // CLIENT — interceptor injects XHR headers into AJAX requests
    override val client = network.client.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val path = request.url.encodedPath
            val isAjax = path.contains("admin-ajax.php") ||
                path.contains("wp-json") ||
                path.endsWith("/ajax/chapters")
            if (isAjax) {
                chain.proceed(
                    request.newBuilder()
                        .header("X-Requested-With", "XMLHttpRequest")
                        .header("Sec-Fetch-Dest", "empty")
                        .header("Sec-Fetch-Mode", "cors")
                        .header("Sec-Fetch-Site", "same-origin")
                        .header("Origin", baseUrl)
                        .header("Priority", "u=1, i")
                        .removeHeader("Sec-Fetch-User")
                        .removeHeader("Upgrade-Insecure-Requests")
                        .build(),
                )
            } else {
                chain.proceed(request)
            }
        }
        .rateLimit(3)
        .build()

    // POPULAR
    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/$mangaSubString${if (page > 1) "/page/$page/" else "/"}?m_orderby=trending"
        return GET(url, headers)
    }

    override val mangaDetailsSelectorDescription = "div.description-summary div.summary__content p"

    override fun parseGenres(document: Document): List<Genre> =
        document.select("div.checkbox-group div.checkbox")
            .mapNotNull { cb ->
                val label = cb.selectFirst("label")?.text() ?: return@mapNotNull null
                val value = cb.selectFirst("input[type=checkbox]")?.`val`() ?: return@mapNotNull null
                if (value.matches(Regex("""^\d+[kKmM]?$"""))) return@mapNotNull null
                Genre(label, value)
            }

    companion object {
        private const val CH_VERSION = "147"
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/$CH_VERSION.0.0.0 Mobile Safari/537.36"
    }
}
