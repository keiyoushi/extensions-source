package eu.kanade.tachiyomi.extension.en.mangaplanet

import eu.kanade.tachiyomi.lib.cookieinterceptor.CookieInterceptor
import eu.kanade.tachiyomi.lib.speedbinb.SpeedBinbInterceptor
import eu.kanade.tachiyomi.lib.speedbinb.SpeedBinbReader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale

class MangaPlanet : ParsedHttpSource() {

    override val name = "Manga Planet"

    override val baseUrl = "https://mangaplanet.com"

    override val lang = "en"

    override val supportsLatest = false

    // No need to be lazy if you're going to use it immediately below.
    private val json = Injekt.get<Json>()

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(SpeedBinbInterceptor(json))
        .addNetworkInterceptor(CookieInterceptor(baseUrl.toHttpUrl().host, "mpaconf" to "18"))
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/browse/title?ttlpage=$page", headers)

    override fun popularMangaSelector() = ".book-list"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
        title = element.selectFirst("h3")!!.text()
        author = element.selectFirst("p:has(.fa-pen-nib)")?.text()
        description = element.selectFirst("h3 + p")?.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("data-src")
        status = when {
            element.selectFirst(".fa-flag-alt") != null -> SManga.COMPLETED
            element.selectFirst(".fa-arrow-right") != null -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
    }

    override fun popularMangaNextPageSelector() = "ul.pagination a.page-link[rel=next]"

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()

    override fun latestUpdatesSelector() = throw UnsupportedOperationException()

    override fun latestUpdatesFromElement(element: Element) = throw UnsupportedOperationException()

    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotEmpty()) {
                addPathSegment("search")
                addQueryParameter("keyword", query)
            } else {
                addPathSegments("browse/title")
            }

            filters.ifEmpty { getFilterList() }
                .filterIsInstance<UrlFilter>()
                .forEach { it.addToUrl(this) }

            if (page > 1) {
                addQueryParameter("ttlpage", page.toString())
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document): SManga {
        val alternativeTitles = document.selectFirst("h3#manga_title + p")!!
            .textNodes()
            .filterNot { it.text().isBlank() }
            .joinToString { it.text() }

        return SManga.create().apply {
            title = document.selectFirst("h3#manga_title")!!.text()
            author = document.select("h3:has(.fa-pen-nib) a").joinToString { it.text() }
            description = buildString {
                append("Alternative Titles: ")
                appendLine(alternativeTitles)
                appendLine()
                appendLine(document.selectFirst("h3#manga_title ~ p:eq(2)")!!.text())
            }
            genre = buildList {
                document.select("h3:has(.fa-layer-group) a")
                    .map { it.text() }
                    .let { addAll(it) }
                document.select(".fa-pepper-hot").size
                    .takeIf { it > 0 }
                    ?.let { add("🌶️".repeat(it)) }
                document.select(".tags-btn button")
                    .map { it.text() }
                    .let { addAll(it) }
                document.selectFirst("span:has(.fa-book-spells, .fa-book)")?.let { add(it.text()) }
                document.selectFirst("span:has(.fa-user-friends)")?.let { add(it.text()) }
            }
                .joinToString()
            status = when {
                document.selectFirst(".fa-flag-alt") != null -> SManga.COMPLETED
                document.selectFirst(".fa-arrow-right") != null -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }
            thumbnail_url = document.selectFirst("img.img-thumbnail")?.absUrl("data-src")
        }
    }

    override fun chapterListSelector() = "ul.ep_ul li.list-group-item"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        element.selectFirst("h3 p")!!.let {
            val id = it.id().substringAfter("epi_title_")

            url = "/reader?cid=$id"
            name = it.text()
        }

        date_upload = try {
            val date = element.selectFirst("p")!!.ownText()

            dateFormat.parse(date)!!.time
        } catch (_: Exception) {
            0L
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select(chapterListSelector())
            .filter { e ->
                e.selectFirst("p")?.ownText()?.contains("Arrives on") != true
            }
            .map { chapterFromElement(it) }
            .reversed()
    }

    private val reader by lazy { SpeedBinbReader(client, headers, json) }

    override fun pageListParse(document: Document): List<Page> {
        if (document.selectFirst("a[href\$=account/sign-up]") != null) {
            throw Exception("Sign up in WebView to read this chapter")
        }

        if (document.selectFirst("a:contains(UNLOCK NOW)") != null) {
            throw Exception("Purchase this chapter in WebView")
        }

        return reader.pageListParse(document)
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        SortFilter(),
        AccessTypeFilter(),
        ReleaseStatusFilter(),
        LetterFilter(),
        CategoryFilter(),
        SpicyLevelFilter(),
        FormatFilter(),
        RatingFilter(),
    )
}

private val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH)
