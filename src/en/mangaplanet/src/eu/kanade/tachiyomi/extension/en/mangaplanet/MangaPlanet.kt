package eu.kanade.tachiyomi.extension.en.mangaplanet

import eu.kanade.tachiyomi.multisrc.speedbinb.SpeedBinbReader
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class MangaPlanet : SpeedBinbReader() {

    override val name = "Manga Planet"

    override val baseUrl = "https://mangaplanet.com"

    override val lang = "en"

    override val supportsLatest = false

    override val client = super.client.newBuilder()
        .addInterceptor(CookieInterceptor(baseUrl.toHttpUrl().host, "mpaconf", "18"))
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/browse/title?ttlpage=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val manga = document.select(".book-list").map {
            SManga.create().apply {
                setUrlWithoutDomain(it.selectFirst("a")!!.attr("href"))
                title = it.selectFirst("h3")!!.text()
                author = it.selectFirst("p:has(.fa-pen-nib)")?.text()
                description = it.selectFirst("h3 + p")?.text()
                thumbnail_url = it.selectFirst("img")?.absUrl("data-src")
                status = when {
                    it.selectFirst(".fa-flag-alt") != null -> SManga.COMPLETED
                    it.selectFirst(".fa-arrow-right") != null -> SManga.ONGOING
                    else -> SManga.UNKNOWN
                }
            }
        }
        val hasNextPage = document.selectFirst("ul.pagination a.page-link[rel=next]") != null

        return MangasPage(manga, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response) = throw UnsupportedOperationException()

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

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
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

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select("ul.ep_ul li.list-group-item")
            .filter { e ->
                e.selectFirst("p")?.ownText()?.contains("Arrives on") != true
            }
            .map {
                SChapter.create().apply {
                    it.selectFirst("h3 p")!!.let {
                        val id = it.id().substringAfter("epi_title_")

                        url = "/reader?cid=$id"
                        name = it.text()
                    }

                    date_upload = try {
                        val date = it.selectFirst("p")!!.ownText()

                        dateFormat.parse(date)!!.time
                    } catch (_: Exception) {
                        0L
                    }
                }
            }
            .reversed()
    }

    override fun pageListParse(response: Response, document: Document): List<Page> {
        if (document.selectFirst("a[href\$=account/sign-up]") != null) {
            throw Exception("Sign up in WebView to read this chapter")
        }

        if (document.selectFirst("a:contains(UNLOCK NOW)") != null) {
            throw Exception("Purchase this chapter in WebView")
        }

        return super.pageListParse(response, document)
    }

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
