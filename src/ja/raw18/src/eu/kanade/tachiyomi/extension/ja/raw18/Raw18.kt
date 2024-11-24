package eu.kanade.tachiyomi.extension.ja.raw18

import eu.kanade.tachiyomi.multisrc.wpcomics.WPComics
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class Raw18 : WPComics(
    "Raw18",
    "https://raw18.net/",
    "ja",
    dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()),
    gmtOffset = null,
) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override fun popularMangaSelector() = "div.items div.item"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        element.select("h3 a").let {
            title = it.text()
            setUrlWithoutDomain(it.attr("abs:href"))
        }
        thumbnail_url = imageOrNull(element.selectFirst("img")!!)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/$searchPath".toHttpUrl().newBuilder()

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> filter.toUriPart()?.let { url.addQueryParameter("genre", it) }
                is StatusFilter -> filter.toUriPart()?.let { url.addQueryParameter("status", it) }
                else -> {}
            }
        }

        when {
            query.isNotBlank() -> url.addQueryParameter(queryParam, query)
            else -> url.addQueryParameter("page", page.toString())
        }

        return GET(url.toString(), headers)
    }

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1.title-detail")!!.text()
        description = document.selectFirst("p#description")?.text()
        status = document.selectFirst("li.status:last-child")?.text().toStatus()
        genre = document.select("li.kind p a")?.joinToString { it.text() }
        thumbnail_url = imageOrNull(document.selectFirst("div.col-image img")!!)
    }

    override fun chapterListSelector() = "div.list-chapter li.row:not(.heading):not([style])"

    override fun chapterFromElement(element: Element): SChapter {
        return super.chapterFromElement(element).apply {
            date_upload = element.select(".chapters + div").text().toDate()
        }
    }

    override val genresSelector = ".categories-detail ul.nav li:not(.active) a"
}
