package eu.kanade.tachiyomi.extension.ar.waveteamy

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.concurrent.TimeUnit

class WaveTeamy : ParsedHttpSource() {

    override val name = "WaveTeamy"

    override val baseUrl = "https://waveteamy.com"

    override val lang = "ar"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .rateLimit(10, 1, TimeUnit.SECONDS)
        .build()

    // Popular - Main grid of series
    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/series?page=$page", headers)
    }

    override fun popularMangaSelector() = "div.grid a[href*='/series/']"

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            setUrlWithoutDomain(element.attr("href"))
            
            // Get title from h3 or img alt
            title = element.select("h3").text().ifEmpty {
                element.select("img").attr("alt")
            }
            
            // Get thumbnail
            thumbnail_url = element.select("img").attr("abs:src")
        }
    }

    override fun popularMangaNextPageSelector() = "a[rel=next], a:contains(Next)"

    // Latest - Same as popular for now
    override fun latestUpdatesRequest(page: Int) = popularMangaRequest(page)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/series".toHttpUrl().newBuilder()
            .addQueryParameter("search", query)
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // Manga Details
    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            // Title from h1 or meta
            title = document.select("h1").first()?.text() ?: 
                    document.select("meta[property=og:title]").attr("content")
            
            // Description
            description = document.select("p.text-sm, div.description").text()
            
            // Thumbnail
            thumbnail_url = document.select("img[alt*='cover'], meta[property=og:image]")
                .first()?.attr("abs:src") ?: 
                document.select("meta[property=og:image]").attr("content")
            
            // Status - look for Arabic status text
            val statusText = document.text()
            status = when {
                statusText.contains("مستمر") -> SManga.ONGOING
                statusText.contains("مكتمل") -> SManga.COMPLETED
                statusText.contains("متوقف") -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
            
            // Genre/tags
            genre = document.select("a[href*='/genre/'], span.genre").joinToString { it.text() }
            
            // Author
            author = document.select("span:contains(المؤلف), span:contains(Author)").text()
                .replace("المؤلف:", "").replace("Author:", "").trim()
        }
    }

    // Chapters
    override fun chapterListSelector() = "a[href*='/chapter/'], a[href*='/series/'][href*='/chapter']"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            setUrlWithoutDomain(element.attr("href"))
            
            // Chapter name
            name = element.select("span, p, h3, h4").text().ifEmpty {
                element.text()
            }
            
            // Date - try to parse if available
            date_upload = 0L // Will be 0 if not found
        }
    }

    // Pages
    override fun pageListParse(document: Document): List<Page> {
        return document.select("img[src*='wcloud'], img[src*='cdn'], div.reader img").mapIndexed { index, element ->
            Page(
                index,
                "",
                element.attr("abs:src").ifEmpty { element.attr("abs:data-src") }
            )
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList()
}
