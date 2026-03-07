package eu.kanade.tachiyomi.extension.en.porncomix

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.concurrent.TimeUnit

class PornComix : ParsedHttpSource() {

    override val name = "PornComix"

    override val baseUrl = "https://bestporncomix.com"

    override val lang = "en"

    override val supportsLatest = false

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ======================== Popular ========================

    override fun popularMangaRequest(page: Int): Request = if (page == 1) {
        GET("$baseUrl/multporn-net/", headers)
    } else {
        GET("$baseUrl/multporn-net/page/$page/", headers)
    }

    override fun popularMangaSelector() = "#loops-wrapper article"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()

        val anchor = element.selectFirst("h2.post-title a")!!

        manga.setUrlWithoutDomain(anchor.attr("href"))
        manga.title = anchor.text().trim()

        manga.thumbnail_url = element.selectFirst("img")?.let { img ->
            img.attr("data-pagespeed-lazy-src")
                .ifBlank { img.attr("data-src") }
                .ifBlank { img.attr("src") }
        }

        return manga
    }

    override fun popularMangaNextPageSelector() = "a.nextp"

    // ======================== Latest (disabled) ========================

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesSelector(): String = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String? = popularMangaNextPageSelector()

    // ======================== Search ========================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = if (query.isNotBlank()) {
        val encodedQuery = query.trim().replace(" ", "+")
        val url = if (page == 1) {
            "$baseUrl/?s=$encodedQuery"
        } else {
            "$baseUrl/page/$page/?s=$encodedQuery"
        }
        GET(url, headers)
    } else {
        popularMangaRequest(page)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaParse(response: okhttp3.Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(searchMangaSelector()).map { searchMangaFromElement(it) }
        val hasNextPage = document.selectFirst(searchMangaNextPageSelector()) != null
        return MangasPage(mangas, hasNextPage)
    }

    // ======================== Details ========================

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()

        manga.title = document.selectFirst("h1.post-title, h1.entry-title")?.text()?.trim() ?: ""

        manga.thumbnail_url = document.selectFirst(
            "div.post-inner img, div.entry-content img, article img",
        )?.let { img ->
            img.attr("data-pagespeed-lazy-src")
                .ifBlank { img.attr("data-src") }
                .ifBlank { img.attr("src") }
        }

        manga.description = document.selectFirst(
            "div.entry-content p, div.post-content p",
        )?.text()?.trim()

        // Tags / genres
        val tags = document.select("a[rel=tag], .post-tags a, .tags-links a")
            .map { it.text().trim() }
            .filter { it.isNotBlank() }
        if (tags.isNotEmpty()) {
            manga.genre = tags.joinToString(", ")
        }

        manga.status = SManga.COMPLETED

        return manga
    }

    // ======================== Chapters ========================

    override fun chapterListSelector() = "div.entry-content, article.post"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create()
        chapter.name = "Chapter 1"
        chapter.setUrlWithoutDomain(
            element.selectFirst("link[rel=canonical]")?.attr("href")
                ?: element.ownerDocument()?.location() ?: "",
        )
        return chapter
    }

    override fun chapterListParse(response: okhttp3.Response): List<SChapter> {
        val document = response.asJsoup()
        val chapter = SChapter.create()
        chapter.name = "Read"
        chapter.setUrlWithoutDomain(response.request.url.toString())
        return listOf(chapter)
    }

    // ======================== Pages ========================

    override fun pageListParse(document: Document): List<Page> {
        val pswp = document.select(".pswp-gallery__item")

        if (pswp.isNotEmpty()) {
            return pswp.mapIndexed { index, element ->
                val url = element.attr("data-pswp-src")
                Page(index, "", url)
            }
        }

        val images = document.select("div.entry-content img")

        return images.mapIndexed { index, img ->
            val url = img.attr("data-pagespeed-lazy-src")
                .ifBlank { img.attr("data-src") }
                .ifBlank { img.attr("src") }

            Page(index, "", url)
        }
    }

    override fun imageUrlParse(document: Document) = ""

    // ======================== Filters ========================

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Filters only work with Browse, not Search"),
    )
}
