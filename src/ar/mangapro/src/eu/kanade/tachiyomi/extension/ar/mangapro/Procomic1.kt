package eu.kanade.tachiyomi.extension.ar.procomic

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.*
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.Headers
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class ProChan : ParsedHttpSource() {

    override val name = "ProComic"
    override val baseUrl = "https://procomic.net"
    override val lang = "ar"
    override val supportsLatest = true

    // Fix HTTP 403
    override val headers: Headers = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0")
        .add("Referer", baseUrl)
        .add("Accept", "text/html")
        .add("Accept-Language", "en-US,en;q=0.9")
        .build()

    // ===============================
    // Popular Manga
    // ===============================

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/manga/?page=$page", headers)
    }

    override fun popularMangaSelector() = "div.bsx"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        val link = element.selectFirst("a")!!
        val img = element.selectFirst("img")

        manga.title = link.attr("title")
        manga.setUrlWithoutDomain(link.attr("href"))

        manga.thumbnail_url =
            img?.attr("data-src")
                ?: img?.attr("src")

        return manga
    }

    override fun popularMangaNextPageSelector() = "a.next"

    // ===============================
    // Latest Updates
    // ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/manga/?page=$page&order=update", headers)
    }

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) =
        popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = "a.next"

    // ===============================
    // Search
    // ===============================

    override fun searchMangaRequest(
        page: Int,
        query: String,
        filters: FilterList
    ): Request {

        val url = if (query.isNotEmpty()) {
            "$baseUrl/?s=$query&page=$page"
        } else {
            "$baseUrl/manga/?page=$page"
        }

        return GET(url, headers)
    }

    override fun searchMangaSelector() = "div.bsx"

    override fun searchMangaFromElement(element: Element) =
        popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = "a.next"

    // ===============================
    // Manga Details
    // ===============================

    override fun mangaDetailsParse(document: Document): SManga {

        val manga = SManga.create()

        manga.title = document.selectFirst("h1")?.text() ?: ""

        manga.thumbnail_url =
            document.selectFirst(".thumb img")?.attr("src")

        manga.description =
            document.selectFirst(".desc")?.text()

        manga.genre =
            document.select(".mgen a")
                .joinToString(", ") { it.text() }

        manga.status = SManga.UNKNOWN

        return manga
    }

    // ===============================
    // Chapters
    // ===============================

    override fun chapterListSelector() = "li.wp-manga-chapter"

    override fun chapterFromElement(element: Element): SChapter? {

        val text = element.text().lowercase()

        // Hide paid chapters
        if (
            text.contains("مدفوع") ||
            text.contains("paid") ||
            element.hasClass("premium") ||
            element.select(".lock").isNotEmpty()
        ) {
            return null
        }

        val chapter = SChapter.create()

        val link = element.selectFirst("a")!!

        chapter.name = link.text()
        chapter.setUrlWithoutDomain(link.attr("href"))

        return chapter
    }

    override fun chapterListParse(response: okhttp3.Response): List<SChapter> {

        val document = response.asJsoup()

        return document
            .select(chapterListSelector())
            .mapNotNull { chapterFromElement(it) }
    }

    // ===============================
    // Pages
    // ===============================

    override fun pageListParse(document: Document): List<Page> {

        val pages = mutableListOf<Page>()

        val images =
            document.select("div.page-break img, .reading-content img")

        images.forEachIndexed { index, element ->

            var imgUrl =
                element.attr("data-src")
                    .ifEmpty { element.attr("src") }

            // Fix CDN URLs
            if (imgUrl.startsWith("//")) {
                imgUrl = "https:$imgUrl"
            }

            pages.add(Page(index, "", imgUrl))
        }

        return pages
    }

    override fun imageUrlParse(document: Document) = ""
}
