package eu.kanade.tachiyomi.multisrc.mangaworld

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

private val chapterNumberRegex = Regex("""(?i)capitolo\s([0-9]+)""")
private val dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale.ITALY)
private val dateFormat2 = SimpleDateFormat("H", Locale.ITALY)

abstract class MangaWorld : HttpSource() {

    override val supportsLatest = true

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor(CookieRedirectInterceptor(network.client))
        .build()

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/archive?sort=most_read&page=$page", headers)
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/?page=$page", headers)

    private fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        thumbnail_url = element.selectFirst("a.thumb img")?.attr("abs:src")
        element.selectFirst("a")?.let {
            setUrlWithoutDomain(it.attr("abs:href").removeSuffix("/"))
            title = it.attr("title")
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.comics-grid .entry").map { searchMangaFromElement(it) }
        val hasNextPage = mangas.size == 16
        return MangasPage(mangas, hasNextPage)
    }

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)
    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/archive".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())

        if (query.isNotEmpty()) {
            url.addQueryParameter("keyword", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is GenreList -> {
                    filter.state.filter { it.state }.forEach {
                        url.addQueryParameter("genre", it.id)
                    }
                }
                is StatusList -> {
                    filter.state.filter { it.state }.forEach {
                        url.addQueryParameter("status", it.id)
                    }
                }
                is MTypeList -> {
                    filter.state.filter { it.state }.forEach {
                        url.addQueryParameter("type", it.id)
                    }
                }
                is SortBy -> url.addQueryParameter("sort", filter.toUriPart())
                is TextField -> {
                    if (filter.state.isNotEmpty()) {
                        url.addQueryParameter(filter.key, filter.state)
                    }
                }
                else -> {}
            }
        }
        return GET(url.build(), headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val infoElement = document.selectFirst("div.comic-info")
            ?: throw Exception("Page not found")

        return SManga.create().apply {
            author = infoElement.selectFirst("a[href*=/archive?author=]")?.text()
            artist = infoElement.selectFirst("a[href*=/archive?artist=]")?.text() ?: ""
            thumbnail_url = infoElement.selectFirst(".thumb > img")?.attr("abs:src")

            description = buildString {
                append(document.select("div#noidungm").text())
                val otherTitle = document.selectFirst("div.meta-data > div")?.text()
                if (!otherTitle.isNullOrEmpty() && otherTitle.contains("Titoli alternativi")) {
                    append("\n\n").append(otherTitle)
                }
            }

            genre = infoElement.select("div.meta-data a.badge").joinToString { it.text() }

            val statusText = infoElement.selectFirst("a[href*=/archive?status=]")?.text()
            status = parseStatus(statusText)
        }
    }

    protected fun parseStatus(status: String?) = when (status?.lowercase()) {
        "in corso" -> SManga.ONGOING
        "finito" -> SManga.COMPLETED
        "in pausa" -> SManga.ON_HIATUS
        "cancellato" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(".chapters-wrapper .chapter").map { element ->
            SChapter.create().apply {
                val urlElement = element.selectFirst("a.chap") ?: throw Exception("Url not found")
                setUrlWithoutDomain(fixChapterUrl(urlElement.attr("abs:href")))
                name = element.selectFirst("span.d-inline-block")?.text() ?: ""
                date_upload = parseChapterDate(element.select(".chap-date").last()?.text())
                parseChapterNumber(name)?.let { chapter_number = it }
            }
        }
    }

    protected fun fixChapterUrl(url: String?): String {
        if (url.isNullOrEmpty()) return ""
        val params = url.substringAfter("?", "")
        return when {
            params.contains("style=list") -> url
            params.contains("style=pages") -> url.replace("style=pages", "style=list")
            params.isEmpty() -> "$url?style=list"
            else -> "$url&style=list"
        }
    }

    protected fun parseChapterDate(string: String?): Long {
        if (string.isNullOrEmpty()) return 0L
        val date = dateFormat.tryParse(string)
        if (date != 0L) return date
        return dateFormat2.tryParse(string)
    }

    protected fun parseChapterNumber(name: String): Float? = chapterNumberRegex.find(name)?.let { it.groups[1]?.value?.toFloatOrNull() }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("div#page img.page-image").mapIndexed { index, it ->
            Page(index, imageUrl = it.attr("abs:src"))
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException("Not used.")

    override fun imageRequest(page: Page): Request {
        val imgHeaders = headersBuilder()
            .set("User-Agent", "Mozilla/5.0 (Linux; U; Android 4.1.1; en-gb; Build/KLP) AppleWebKit/534.30 (KHTML, like Gecko) Version/4.0 Safari/534.30")
            .set("Referer", "$baseUrl/")
            .build()
        return GET(page.imageUrl!!, imgHeaders)
    }

    override fun getFilterList() = FilterList(
        TextField("Anno di uscita", "year"),
        SortBy(),
        StatusList(STATUSES),
        GenreList(GENRES),
        MTypeList(MTYPES),
    )
}
