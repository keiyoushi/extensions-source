package eu.kanade.tachiyomi.extension.id.mgkomik

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.network.rateLimit
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class MGKomik :
    Madara(
        "MG Komik",
        "https://id.mgkomik.cc",
        "id",
        dateFormat,
    ) {
    override val useNewChapterEndpoint = true

    override val mangaSubString = "komik"

    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override fun headersBuilder() = super.headersBuilder().apply {
        set("Referer", "$baseUrl/")
    }

    override val client = network.client.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val headers = request.headers.newBuilder().apply {
                if (!request.url.toString().contains("admin-ajax.php") &&
                    !request.url.toString().contains("ajax/chapters")
                ) {
                    removeAll("X-Requested-With")
                }
            }.build()

            chain.proceed(request.newBuilder().headers(headers).build())
        }
        .rateLimit(1)
        .build()

    // ================================== Popular ======================================

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        element.select("div.item-thumb a").let {
            setUrlWithoutDomain(it.attr("abs:href"))
            title = it.attr("title")
            thumbnail_url = it.select("img").attr("abs:src")
        }
    }

    // ================================ Chapters ================================

    override val chapterUrlSuffix = ""

    // ================================ Filters ================================

    override fun getFilterList(): FilterList {
        val filters = super.getFilterList().list.filterNot {
            it.name.contains("Adult Content", ignoreCase = true)
        }

        return FilterList(filters)
    }

    override fun genresRequest() = GET("$baseUrl/$mangaSubString", headers)

    override fun parseGenres(document: Document): List<Genre> = document.select(".row.genres li a").map { a ->
        Genre(
            a.ownText(),
            a.absUrl("href")
                .trimEnd('/')
                .substringAfterLast('/'),
        )
    }

    companion object {
        private val dateFormat = SimpleDateFormat("dd MMM yy", Locale.US)
    }
}
