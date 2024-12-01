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
import java.util.concurrent.TimeUnit

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

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
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
        element.select("div.manga-title a").let {
            title = it.text()
            setUrlWithoutDomain(it.attr("href"))
        }
        thumbnail_url = imageOrNull(element.select("div.manga-image img").first()!!)
    }

    override fun popularMangaNextPageSelector() = "div.list-pagination a:last-child:not(.active)"

    override val queryParam = "name"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/tim-truyen".toHttpUrl().newBuilder()

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
        status = document.selectFirst("ul.manga-desc > li:nth-child(2) div.md-content")?.text().toStatus()
        genre = document.select("div.tags.mt-15 span a")?.joinToString { it.text() }
        thumbnail_url = imageOrNull(document.selectFirst("div.manga-img img")!!)
    }

    override fun chapterListSelector() = "div.manga-chapters ul.clearfix li:not(.thead)"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            element.select("div.chapter-name a").let {
                name = it.text()
                setUrlWithoutDomain(it.attr("href"))
            }
            date_upload = parseChapterDate(element.select("div.col-30.alr").text())
        }
    }

    override val pageListSelector = "div.chapter-img.shine > img.img-chap-item"

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
            val genreName = it.text().trim()
            val genreValue = it.select("input").attr("value")
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
