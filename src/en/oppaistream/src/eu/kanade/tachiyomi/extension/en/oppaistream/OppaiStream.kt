package eu.kanade.tachiyomi.extension.en.oppaistream

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.net.URLDecoder
import java.util.Calendar

class OppaiStream : HttpSource() {

    override val name = "Oppai Stream"

    override val baseUrl = "https://read.oppai.stream"

    private val cdnUrl = "https://myspacecat.pictures"

    override val lang = "en"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // popular
    override fun popularMangaRequest(page: Int): Request = searchMangaRequest(page, "", FilterList(OrderByFilter("views")))

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    // latest
    override fun latestUpdatesRequest(page: Int): Request = searchMangaRequest(page, "", FilterList(OrderByFilter("uploaded")))

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    // search
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (!query.startsWith(SLUG_SEARCH_PREFIX)) {
            return super.fetchSearchManga(page, query, filters)
        }

        val url = "/manhwa?m=${query.substringAfter(SLUG_SEARCH_PREFIX)}"
        return fetchMangaDetails(SManga.create().apply { this.url = url }).map {
            it.url = url
            MangasPage(listOf(it), false)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/api-search.php".toHttpUrl().newBuilder().apply {
            addQueryParameter("text", query)
            filters.firstInstanceOrNull<OrderByFilter>()?.let {
                addQueryParameter("order", it.selectedValue())
            }
            filters.firstInstanceOrNull<GenreListFilter>()?.let { filter ->
                addQueryParameter("genres", filter.state.filter { it.isIncluded() }.joinToString(",") { it.value })
                addQueryParameter("blacklist", filter.state.filter { it.isExcluded() }.joinToString(",") { it.value })
            }
            addQueryParameter("page", "$page")
            addQueryParameter("limit", "$SEARCH_LIMIT")
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val elements = document.select("div.in-grid > a")

        val mangas = elements.map { element ->
            SManga.create().apply {
                thumbnail_url = element.select("img.read-cover").attr("src")
                title = element.select("h3.man-title").text()
                val rawUrl = element.absUrl("href")
                val url = if (rawUrl.contains("/fw?to=")) {
                    URLDecoder.decode(rawUrl.substringAfter("/fw?to="), "UTF-8")
                } else {
                    rawUrl
                }
                setUrlWithoutDomain(url)
            }
        }

        return MangasPage(mangas, elements.size >= SEARCH_LIMIT)
    }

    // manga details
    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val document = response.asJsoup()
        thumbnail_url = document.select(".cover-img").attr("src")
        document.select(".manhwa-info-in").let { info ->
            info.select("h1").run {
                title = text().substringBeforeLast("By").trim()
                author = select("a.red").text()
                artist = author
            }
            genre = info.select(".genres h5").joinToString { it.text() }
            description = info.select(".description").text()
        }
    }

    // chapter list
    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup().select(".sort-chapters > a").map { element ->
        SChapter.create().apply {
            setUrlWithoutDomain(element.attr("href"))
            name = element.select("div > h4").text()
            date_upload = element.select("div > h6").text().parseRelativeDate()
        }
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    // page list
    override fun pageListRequest(chapter: SChapter): Request {
        val chapterUrl = "$baseUrl${chapter.url}".toHttpUrl()
        val slug = chapterUrl.queryParameter("m")
        val chapNo = chapterUrl.queryParameter("c")

        return GET("$cdnUrl/manhwa/im.php?f-m=$slug&c=$chapNo", headers)
    }

    override fun pageListParse(response: Response): List<Page> = response.asJsoup().select("img").mapIndexed { index, img ->
        Page(index, imageUrl = img.attr("src"))
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // filters
    override fun getFilterList() = FilterList(
        OrderByFilter(),
        GenreListFilter(getGenreList()),
    )

    // helpers
    private fun String.parseRelativeDate(): Long {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val relativeDate = try {
            this.split(" ")[0].trim().toInt()
        } catch (e: NumberFormatException) {
            return 0L
        }

        return when {
            "second" in this -> now.apply { add(Calendar.SECOND, -relativeDate) }.timeInMillis
            "minute" in this -> now.apply { add(Calendar.MINUTE, -relativeDate) }.timeInMillis
            "hour" in this -> now.apply { add(Calendar.HOUR, -relativeDate) }.timeInMillis
            "day" in this -> now.apply { add(Calendar.DAY_OF_YEAR, -relativeDate) }.timeInMillis
            "week" in this -> now.apply { add(Calendar.WEEK_OF_YEAR, -relativeDate) }.timeInMillis
            "month" in this -> now.apply { add(Calendar.MONTH, -relativeDate) }.timeInMillis
            "year" in this -> now.apply { add(Calendar.YEAR, -relativeDate) }.timeInMillis
            else -> 0L
        }
    }

    companion object {
        const val SEARCH_LIMIT = 36
        const val SLUG_SEARCH_PREFIX = "slug:"
    }
}
