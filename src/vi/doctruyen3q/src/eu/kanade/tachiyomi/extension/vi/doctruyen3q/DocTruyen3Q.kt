package eu.kanade.tachiyomi.extension.vi.doctruyen3q

import eu.kanade.tachiyomi.multisrc.wpcomics.WPComics
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class DocTruyen3Q : WPComics(
    "DocTruyen3Q",
    "https://doctruyen3qk.pro",
    "vi",
    dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
    },
    gmtOffset = null,
) {
    override val client = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override fun pageListParse(document: Document): List<Page> {
        return document.select(".page-chapter a img, .page-chapter img").mapIndexed { index, element ->
            val img = element.attr("abs:src").takeIf { it.isNotBlank() } ?: element.attr("abs:data-original")
            Page(index, imageUrl = img)
        }.distinctBy { it.imageUrl }
    }

    override fun popularMangaSelector() = "div.item-manga div.item"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        element.selectFirst("h3 a")?.let {
            title = it.text()
            setUrlWithoutDomain(it.attr("abs:href"))
        }
        thumbnail_url = element.selectFirst("img")?.attr("abs:src")
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/$searchPath".toHttpUrl().newBuilder()

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> filter.toUriPart()?.let { url.addPathSegment(it) }
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
        title = document.selectFirst("h1.title-manga")!!.text()
        description = document.selectFirst("p.detail-summary")?.text()
        status = document.selectFirst("li.status p.detail-info span")?.text().toStatus()
        genre = document.select("li.category p.detail-info a")?.joinToString { it.text() }
        thumbnail_url = document.selectFirst("img.image-comic")?.attr("abs:src")
    }

    override fun chapterListSelector() = "div.list-chapter li.row:not(.heading):not([style])"

    override fun chapterFromElement(element: Element): SChapter {
        return super.chapterFromElement(element).apply {
            date_upload = element.selectFirst(".chapters + div")?.text().toDate()
        }
    }

    override val genresSelector = ".categories-detail ul.nav li:not(.active) a"
}
