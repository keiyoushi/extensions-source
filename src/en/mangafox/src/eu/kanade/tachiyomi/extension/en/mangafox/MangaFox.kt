package eu.kanade.tachiyomi.extension.en.mangafox

import android.webkit.CookieManager
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MangaFox : HttpSource() {

    override val name: String = "MangaFox"

    override val baseUrl: String = "https://fanfox.net"

    private val mobileUrl: String = "https://m.fanfox.net"

    override val lang: String = "en"

    override val supportsLatest: Boolean = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(1, 1)
        // Force readway=2 cookie to get all page URLs at once
        .cookieJar(
            object : CookieJar {
                private val cookieManager by lazy { CookieManager.getInstance() }

                init {
                    cookieManager.setCookie(mobileUrl.toHttpUrl().host, "readway=2")
                    cookieManager.setCookie(baseUrl.toHttpUrl().host, "isAdult=1")
                }

                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    val urlString = url.toString()
                    cookies.forEach { cookieManager.setCookie(urlString, it.toString()) }
                }

                override fun loadForRequest(url: HttpUrl): List<Cookie> {
                    val cookies = cookieManager.getCookie(url.toString())

                    return if (cookies != null && cookies.isNotEmpty()) {
                        cookies.split(";").mapNotNull {
                            Cookie.parse(url, it)
                        }
                    } else {
                        emptyList()
                    }
                }
            },
        )
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder().add("Referer", "$baseUrl/")

    private val dateFormat = SimpleDateFormat("MMM d,yyyy", Locale.ENGLISH)

    override fun popularMangaRequest(page: Int): Request {
        val pageStr = if (page != 1) "$page.html" else ""
        return GET("$baseUrl/directory/$pageStr", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(popularMangaSelector()).map { popularMangaFromElement(it) }
        val hasNextPage = document.select(popularMangaNextPageSelector()).isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    private fun popularMangaSelector() = "ul.manga-list-1-list li"

    private fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        element.select("a").first()!!.let {
            setUrlWithoutDomain(it.absUrl("href"))
            title = it.attr("title")
            thumbnail_url = it.select("img").attr("abs:src")
        }
    }

    private fun popularMangaNextPageSelector() = ".pager-list-left a.active + a + a"

    override fun latestUpdatesRequest(page: Int): Request {
        val pageStr = if (page != 1) "$page.html" else ""
        return GET("$baseUrl/directory/$pageStr?latest", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(popularMangaSelector()).map { popularMangaFromElement(it) }
        val hasNextPage = document.select(popularMangaNextPageSelector()).isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val genres = mutableListOf<Int>()
        val genresEx = mutableListOf<Int>()
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("search")
            addQueryParameter("title", query)
            (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
                when (filter) {
                    is UriPartFilter -> addQueryParameter(filter.query, filter.toUriPart())

                    is GenreFilter -> filter.state.forEach {
                        when (it.state) {
                            Filter.TriState.STATE_INCLUDE -> genres.add(it.id)
                            Filter.TriState.STATE_EXCLUDE -> genresEx.add(it.id)
                            else -> {}
                        }
                    }

                    is FilterWithMethodAndText -> {
                        val method = filter.state[0] as UriPartFilter
                        val text = filter.state[1] as TextSearchFilter
                        addQueryParameter(method.query, method.toUriPart())
                        addQueryParameter(text.query, text.state)
                    }

                    is RatingFilter -> filter.state.forEach {
                        addQueryParameter(it.query, it.toUriPart())
                    }

                    is TextSearchFilter -> addQueryParameter(filter.query, filter.state)

                    else -> {}
                }
            }
            addQueryParameter("genres", genres.joinToString(","))
            addQueryParameter("nogenres", genresEx.joinToString(","))
            addQueryParameter("sort", "")
            addQueryParameter("stype", "1")
        }.build().toString()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(searchMangaSelector()).map { searchMangaFromElement(it) }
        val hasNextPage = document.select(popularMangaNextPageSelector()).isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    private fun searchMangaSelector() = "ul.manga-list-4-list li"

    private fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val document = response.asJsoup()
        document.select(".detail-info-right").first()!!.let { it ->
            author = it.select(".detail-info-right-say a").joinToString(", ") { it.text() }
            genre = it.select(".detail-info-right-tag-list a").joinToString(", ") { it.text() }
            description = it.select("p.fullcontent").first()?.text()
            status = it.select(".detail-info-right-title-tip").first()?.text().orEmpty().let { parseStatus(it) }
            thumbnail_url = document.select(".detail-info-cover-img").first()?.attr("abs:src")
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(chapterListSelector()).map { chapterFromElement(it) }
    }

    private fun chapterListSelector() = "ul.detail-main-list li a"

    private fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        name = element.select(".detail-main-list-main p").first()?.text().orEmpty()
        date_upload = element.select(".detail-main-list-main p").last()?.text()?.let { parseChapterDate(it) } ?: 0
    }

    private fun parseChapterDate(date: String): Long = if ("Today" in date || " ago" in date) {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    } else if ("Yesterday" in date) {
        Calendar.getInstance().apply {
            add(Calendar.DATE, -1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    } else {
        dateFormat.tryParse(date)
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val mobilePath = chapter.url.replace("/manga/", "/roll_manga/")

        val headers = headersBuilder().set("Referer", "$mobileUrl/").build()

        return GET("$mobileUrl$mobilePath", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("#viewer img").mapIndexed { idx, it ->
            Page(idx, imageUrl = it.attr("abs:data-original"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun parseStatus(status: String) = when {
        status.contains("Ongoing") -> SManga.ONGOING
        status.contains("Completed") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun getFilterList(): FilterList = FilterList(
        NameFilter(),
        EntryTypeFilter(),
        CompletedFilter(),
        AuthorFilter(),
        ArtistFilter(),
        RatingFilter(),
        YearFilter(),
        GenreFilter(getGenreList()),
    )
}
