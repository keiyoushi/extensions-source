package eu.kanade.tachiyomi.extension.en.xlecx

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class XlecX : HttpSource() {
    override val name = "XlecX"
    override val baseUrl = "https://xlecx.one"
    override val lang = "en"
    override val supportsLatest = true

    // TODO: filters
    // TODO: description - text, other `subInfoLinks`s, JSON-LD,
    // TODO: deep link via UrlActivity

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor { chain ->
            val response = chain.proceed(chain.request())
            if (response.code == 403 && response.header("Content-Type")?.contains("text/html") == true) {
                val document = Jsoup.parse(response.peekBody(Long.MAX_VALUE).string())
                val message = document.selectFirst(".message-info__content")
                if (message != null) {
                    val host = chain.request().url.host
                    val messageCleaned = message.text().replace(whitespaceRegex, " ")
                    if (messageCleaned.length > 200) {
                        throw Exception("Open WebView to see message from $host")
                    }
                    throw Exception("$host: $messageCleaned")
                }
            }
            response
        }
        .build()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ROOT)

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        val pageStr = if (page > 1) "page/$page/" else ""
        return GET("$baseUrl/f/sort=news_read/order=desc/$pageStr", headers)
    }

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    // Latest
    override fun latestUpdatesRequest(page: Int): Request {
        val pageStr = if (page > 1) "page/$page/" else ""
        return GET("$baseUrl/f/sort=date/order=desc/$pageStr", headers)
    }

    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/index.php".toHttpUrl().newBuilder()
            .addQueryParameter("do", "search")
            .addQueryParameter("subaction", "search")
            .addQueryParameter("search_start", page.toString())
            .addQueryParameter("full_search", "0")
            .addQueryParameter("story", query)
            .build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document
            .select("#dle-content > a.thumb")
            .map(::searchMangaFromElement)

        val hasNextPage = document.selectFirst("#pagination > .pagination__pages > a:contains(Next)") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun searchMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))

        val img = element.selectFirst("img")!!
        title = img.attr("alt")
        thumbnail_url = img.absUrl("src")
    }

    // Details
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            setUrlWithoutDomain(response.request.url.toString())
            title = document.selectFirst("h1")!!.text()
            artist = document.subInfoLinks("Artist:")
            author = document.subInfoLinks("Group:")
            genre = document.subInfoLinks("Tags:")
            // TODO: document.subInfoLinks("Parody:")
            thumbnail_url = document.selectFirst("meta[property=og:image]")?.absUrl("content")
        }
    }

    private fun Element.subInfoLinks(label: String): String? = select(".page__subinfo-item > div:not([class]):contains($label) ~ a")
        ?.joinToString { it.text() }

    // Chapters
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val script = document.selectFirst("script[type=application/ld+json]")?.data() ?: throw Exception("JSON-LD data not found")
        val dto = script.parseAs<JsonLdDto>()
        val book = dto.graph.firstOrNull() ?: return emptyList()

        return listOf(
            SChapter.create().apply {
                setUrlWithoutDomain(response.request.url.toString())
                name = "Chapter"
                date_upload = dateFormat.tryParse(book.dateModified ?: book.datePublished)
                chapter_number = 1f
            },
        )
    }

    // Pages
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        // 'Full size' tab
        document.select("#content-2 > .imagegall23 > img").mapIndexed { index, element ->
            val imageUrl = element.absUrl("data-src").ifEmpty { element.absUrl("src") }
            Page(index, imageUrl = imageUrl)
        }.takeIf { it.isNotEmpty() }?.let { return it }

        // Plain
        document.select(".page__text a:has(img)").mapIndexed { index, element ->
            val imageUrl = element.absUrl("href")
            Page(index, imageUrl = imageUrl)
        }.takeIf { it.isNotEmpty() }?.let { return it }

        // 'Thumb' tab
        document.select("#content-1 > .imagegall23 > img").mapIndexed { index, element ->
            val imageUrl = element.absUrl("data-src").ifEmpty { element.absUrl("src") }
            Page(index, imageUrl = imageUrl)
        }.takeIf { it.isNotEmpty() }?.let { return it }

        // JSON-LD, may contain junk pages
        val script = document.selectFirst("script[type=application/ld+json]")?.data() ?: throw Exception("JSON-LD data not found")
        val dto = script.parseAs<JsonLdDto>()
        val book = dto.graph.firstOrNull() ?: return emptyList()

        return book.image.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private val whitespaceRegex = """\s+""".toRegex()
    }
}
