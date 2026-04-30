package eu.kanade.tachiyomi.extension.en.manhwabuddy

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class ManhwaBuddy : HttpSource() {
    override val baseUrl = "https://manhwabuddy.com"
    override val lang = "en"
    override val name = "ManhwaBuddy"
    override val supportsLatest = true

    private val dateFormat by lazy { SimpleDateFormat("dd MMMM yyyy", Locale.ENGLISH) }

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".item-move").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a")!!.attr("abs:href"))
                title = element.selectFirst("h3")!!.text()
                thumbnail_url = element.selectFirst("img")?.attr("src")
            }
        }
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/page/$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val hasNextPage = document.selectFirst(".next") != null
        val mangas = document.select(".latest-list .latest-item").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a")!!.attr("abs:href"))
                title = element.selectFirst("h4")!!.text()
                thumbnail_url = element.selectFirst("img")?.attr("src")
            }
        }
        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            return GET(
                baseUrl.toHttpUrl().newBuilder().apply {
                    addPathSegment("search")
                    addQueryParameter("s", query)
                    addQueryParameter("page", page.toString())
                }.build(),
                headers,
            )
        }

        val genreFilter = filters.firstInstanceOrNull<GenreFilter>()
        val genre = genreFilter?.toUriPart() ?: ""

        return GET(
            baseUrl.toHttpUrl().newBuilder().apply {
                addPathSegment("genre")
                addPathSegment(genre)
                addPathSegment("page")
                addPathSegment(page.toString())
            }.build(),
            headers,
        )
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val hasNextPage = document.selectFirst(".next") != null
        val mangas = document.select(".latest-list .latest-item").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a")!!.attr("abs:href"))
                title = element.selectFirst("a")!!.attr("title")
                thumbnail_url = element.selectFirst("img")?.attr("src")
            }
        }
        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val document = response.asJsoup()
        val info = document.selectFirst(".main-info-right")!!
        author = info.selectFirst("li:contains(Author) a")?.text()
        status = when (info.selectFirst("li:contains(Status) span")?.text()) {
            "Ongoing" -> SManga.ONGOING
            "Complete" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        artist = info.selectFirst("li:contains(Artist) a")?.text()
        genre = info.select("li:contains(Genres) a").joinToString { it.text() }
        description = document.select(".short-desc-content p").joinToString("\n") { it.text() }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(".chapter-list a").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.attr("abs:href"))
                name = element.selectFirst(".chapter-name")!!.text()
                date_upload = dateFormat.tryParse(element.selectFirst(".ct-update")?.text())
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select(".loading").mapIndexed { i, element ->
            Page(i, imageUrl = element.attr("abs:src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        Filter.Header("Filter does not work with text search, reset it before filter"),
        Filter.Separator(),
        GenreFilter(),
    )
}
