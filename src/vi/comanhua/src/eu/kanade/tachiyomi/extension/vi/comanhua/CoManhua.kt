package eu.kanade.tachiyomi.extension.vi.comanhua

import eu.kanade.tachiyomi.multisrc.wpcomics.WPComics
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class CoManhua : WPComics(
    "CoManhua",
    "https://comanhuaz.com",
    "vi",
    dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()),
    gmtOffset = null,
) {

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
                    genreFilter = filter.toUriPart() ?: ""
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
            date_upload = element.select("div.col-30.alr").text().toDate()
        }
    }

    override val pageListSelector = "div.chapter-img.shine > img.img-chap-item"

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
            Pair(genreName, genreName)
        }
    }

    override fun getFilterList(): FilterList {
        launchIO { fetchGenres() }
        return FilterList(
            StatusFilter("Trạng thái", getStatusList()),
            if (genreList.isEmpty()) {
                Filter.Header("Tap to load genres")
            } else {
                GenreFilter("Thể loại", genreList)
            },
        )
    }
}
