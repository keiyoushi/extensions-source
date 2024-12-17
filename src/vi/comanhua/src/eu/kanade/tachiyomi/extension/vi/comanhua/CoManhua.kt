package eu.kanade.tachiyomi.extension.vi.comanhua

import eu.kanade.tachiyomi.multisrc.wpcomics.WPComics
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class CoManhua : WPComics(
    "CoManhua",
    "https://comanhuaz.com",
    "vi",
    gmtOffset = null,
) {

    private val coManhuaDateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
    }

    private fun parseChapterDate(dateString: String): Long {
        return try {
            coManhuaDateFormat.parse(dateString)?.time ?: 0L
        } catch (e: ParseException) {
            0L
        }
    }

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(3)
        .build()

    override val popularPath = "truyen-de-cu"

    override fun popularMangaRequest(page: Int): Request {
        val offset = (page - 1) * 40
        val url = "$baseUrl/$popularPath?offset=$offset"
        return GET(url, headers)
    }

    override fun popularMangaSelector() = "div.pda.manga-list div.manga-item"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        element.selectFirst("div.manga-title a")?.let {
            title = it.text()
            setUrlWithoutDomain(it.attr("abs:href"))
        }
        element.selectFirst("div.manga-image img")?.let { imageOrNull(it)?.let { url -> thumbnail_url = url } }
    }

    override fun popularMangaNextPageSelector() = "div.list-pagination a:last-child:not(.active)"

    override val searchPath = "tim-truyen"
    override val queryParam = "name"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/$searchPath".toHttpUrl().newBuilder()

        url.addQueryParameter("chapter", "")
        url.addQueryParameter("year", "")
        url.addQueryParameter("name", query)

        var statusFilter = "Tất cả"
        var genreFilter = ""

        filters.forEach { filter ->
            when (filter) {
                is StatusFilter -> {
                    statusFilter = filter.toUriPart() ?: "Tất cả"
                }
                is GenreFilter -> {
                    genreFilter = filter.toUriPart()
                }
                else -> {}
            }
        }

        url.addQueryParameter("status", statusFilter)
        if (genreFilter.isNotEmpty()) {
            url.addQueryParameter("tags", genreFilter)
        }

        val offset = (page - 1) * 40
        url.addQueryParameter("offset", offset.toString())

        return GET(url.toString(), headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1.manga-title")!!.text()
        description = document.selectFirst("div.manga-des")?.text()
        status = document.selectFirst(".md-title:has(.fa-rss) ~ .md-content")?.text().toStatus()
        genre = document.select("div.tags span a")?.joinToString { it.text() }
        document.selectFirst("div.manga-img img")?.let { imageOrNull(it)?.let { url -> thumbnail_url = url } }
    }

    override fun chapterListSelector() = ".manga .manga-chapters ul li:has(.chapter-name)"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            element.selectFirst("div.chapter-name a")?.let {
                name = it.text()
                setUrlWithoutDomain(it.attr("abs:href"))
            }
            date_upload = element.selectFirst("> :nth-child(3):last-child")?.text()?.let { parseChapterDate(it) } ?: 0L
        }
    }

    override val pageListSelector = ".chapters img.img-chap-item"

    private class GenreFilter(genres: List<Pair<String?, String>>) : Filter.Group<GenreFilter.CheckBox>(
        "Thể loại",
        genres.map { CheckBox(it.second, false, it.first ?: "") },
    ) {
        class CheckBox(name: String, state: Boolean, val value: String) : Filter.CheckBox(name, state)

        fun toUriPart(): String = state.filter { it.state }.joinToString(",") { it.value }
    }

    override fun getStatusList(): List<Pair<String?, String>> =
        listOf(
            Pair("Tất cả", "Tất cả"),
            Pair("continue", "Đang tiến hành"),
            Pair("completed", "Đã hoàn thành"),
        )

    override val genresSelector = "div.filter-content div.filter-tags div.tags-checkbox div.tags label"

    override fun genresRequest() = GET("$baseUrl/$searchPath", headers)

    override fun parseGenres(document: Document): List<Pair<String?, String>> {
        val items = document.select(genresSelector)
        return items.map {
            val genreName = it.text()
            val genreValue = it.selectFirst("input")?.attr("value")
            Pair(genreValue, genreName)
        }
    }

    override fun getFilterList(): FilterList {
        launchIO { fetchGenres() }
        return FilterList(
            StatusFilter(intl["STATUS"], getStatusList()),
            if (genreList.isEmpty()) {
                Filter.Header(intl["GENRES_RESET"])
            } else {
                GenreFilter(genreList)
            },
        )
    }
}
