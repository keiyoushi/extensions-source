package eu.kanade.tachiyomi.extension.en.niadd

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.random.Random

class NiaddEn : ParsedHttpSource() {

    override val name: String = "Niadd"
    override val baseUrl: String = "https://www.niadd.com"
    override val lang: String = "en"
    override val supportsLatest: Boolean = true

    // Client com delay 2–5s para evitar bloqueios (vale para HTML e imagens)
    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor { chain ->
            val delayMillis = Random.nextLong(2000L, 5000L)
            Thread.sleep(delayMillis)
            chain.proceed(chain.request())
        }
        .build()

    // Headers personalizados
    private val customHeaders: Headers = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
        .build()

    // Popular
    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/category/?page=$page", headers = customHeaders)

    override fun popularMangaSelector(): String = "div.manga-item:has(a[href*='/manga/'])"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        val link = element.selectFirst("a[href*='/manga/']") ?: return manga
        manga.setUrlWithoutDomain(link.attr("href"))

        manga.title = element.selectFirst("div.manga-name")?.text()?.trim()
            ?: element.selectFirst("a[title]")?.attr("title")?.trim()
            ?: element.selectFirst("h3")?.text()?.trim()
            ?: element.selectFirst("img[alt]")?.attr("alt")?.trim()
            ?: link.text().trim()

        val img = element.selectFirst("img")
        manga.thumbnail_url = img?.absUrl("src")
            ?.takeIf { it.isNotBlank() }
            ?: img?.absUrl("data-cfsrc")
            ?: img?.absUrl("data-src")
            ?: img?.absUrl("data-original")

        manga.description = element.selectFirst("div.manga-intro")?.text()?.trim()
        return manga
    }

    override fun popularMangaNextPageSelector(): String? = "a.next"

    // Latest
    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/list/New-Update/?page=$page", headers = customHeaders)

    override fun latestUpdatesSelector(): String = popularMangaSelector()
    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)
    override fun latestUpdatesNextPageSelector(): String? = popularMangaNextPageSelector()

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val q = URLEncoder.encode(query, "UTF-8")
        return GET("$baseUrl/search/?name=$q&page=$page", headers = customHeaders)
    }

    override fun searchMangaSelector(): String = popularMangaSelector()
    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)
    override fun searchMangaNextPageSelector(): String? = popularMangaNextPageSelector()

    // Details
    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.title = document.selectFirst("h1")?.text()?.trim() ?: ""

        val img = document.selectFirst("div.detail-cover img, .bookside-cover img")
        manga.thumbnail_url = img?.absUrl("src")
            ?.takeIf { it.isNotBlank() }
            ?: img?.absUrl("data-cfsrc")
            ?: img?.absUrl("data-src")
            ?: img?.absUrl("data-original")

        val author = document.selectFirst(
            "div.bookside-bookinfo div[itemprop=author] span.bookside-bookinfo-value",
        )?.text()?.trim()
        manga.author = author
        manga.artist = author

        val synopsis = document.select("div.detail-section-box")
            .firstOrNull { it.selectFirst(".detail-cate-title")?.text()?.contains("Synopsis", true) == true }
            ?.selectFirst("section.detail-synopsis")
            ?.text()?.trim() ?: ""

        val alternatives = document.selectFirst("div.bookside-general-cell:contains(Alternative(s):)")
            ?.ownText()?.replace("Alternative(s):", "")?.trim()

        manga.description = buildString {
            append(synopsis)
            if (!alternatives.isNullOrBlank()) append("\n\nAlternative(s): $alternatives")
        }

        manga.genre = document.select("div.detail-section-box")
            .firstOrNull { it.selectFirst(".detail-cate-title")?.text()?.contains("Genres", true) == true }
            ?.select("section.detail-synopsis a span[itemprop=genre]")
            ?.joinToString(", ") { it.text().trim().trimStart(',') } ?: ""

        manga.status = SManga.UNKNOWN
        return manga
    }

    // Chapters
    override fun chapterListRequest(manga: SManga): Request {
        return GET(toAbsolute(manga.url), headers = customHeaders)
    }

    // selector amplo para variações do site
    override fun chapterListSelector(): String =
        "ul.chapter-list a.hover-underline, " +
            "ul#chapterlist a, ul.manga-chapter-list a, .chapter-list a, .detail-ch-list a, " +
            "ul li a[href*='/chapter/']"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        val href = element.attr("href")
        if (href.startsWith("http", ignoreCase = true)) {
            chapter.url = href
        } else {
            chapter.setUrlWithoutDomain(href)
        }
        chapter.name = element.selectFirst("span.chp-title")?.text()?.trim()
            ?: element.attr("title")?.trim()
            ?: element.text().trim()
        val dateText = element.selectFirst("span.chp-time")?.text()
        chapter.date_upload = parseDate(dateText)
        return chapter
    }

    private fun parseDate(dateString: String?): Long {
        if (dateString.isNullOrBlank()) return 0L
        return try {
            SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH).parse(dateString)?.time ?: 0L
        } catch (_: Exception) { 0L }
    }

    // Pages
    override fun pageListRequest(chapter: SChapter): Request {
        return GET(toAbsolute(chapter.url), headers = customHeaders)
    }

    override fun pageListParse(document: Document): List<Page> {
        val jsBlob = document.select("script:containsData(all_imgs_url)")
            .firstOrNull()
            ?.data()
            ?: document.outerHtml()

        val match = Regex(
            pattern = """all_imgs_url\s*:\s*\[(.*?)\]""",
            options = setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE),
        ).find(jsBlob) ?: throw Exception("all_imgs_url not found")

        val urls = Regex(""""(https?://[^"]+)"""")
            .findAll(match.groupValues[1])
            .map { it.groupValues[1] }
            .toList()

        val referer = document.location().ifBlank { toAbsolute("") }
        return urls.mapIndexed { index, imageUrl ->
            // guarda o referer no Page.url e já define a imageUrl
            Page(index, referer, imageUrl)
        }
    }

    // Já definimos imageUrl na Page; manter vazio para não ser chamado
    override fun imageUrlParse(document: Document): String = ""

    // Gera request de imagem com Referer correto
    override fun imageRequest(page: Page): Request {
        val referer = if (page.url.isNullOrBlank()) baseUrl else page.url
        val headers = Headers.Builder()
            .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .add("Referer", referer)
            .build()
        return GET(page.imageUrl!!, headers)
    }

    private fun toAbsolute(url: String): String {
        return if (url.startsWith("http", ignoreCase = true)) url else "$baseUrl$url"
    }
}
