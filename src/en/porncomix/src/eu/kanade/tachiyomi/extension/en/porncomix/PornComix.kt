package eu.kanade.tachiyomi.extension.en.porncomix

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class PornComix : ParsedHttpSource() {

    override val name = "PornComix"

    override val baseUrl = "https://bestporncomix.com"

    override val lang = "en"

    override val supportsLatest = false

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
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

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isBlank()) return popularMangaRequest(page)

        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
            }
            addQueryParameter("s", query.trim())
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // ======================== Details ========================

    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()

        manga.title = document.selectFirst("h1.post-title, h1.entry-title")!!.text()

        manga.thumbnail_url = document.selectFirst(
            "div.post-inner img, div.entry-content img, article img",
        )?.let { img ->
            img.absUrl("data-pagespeed-lazy-src")
                .ifBlank { img.absUrl("data-src") }
                .ifBlank { img.absUrl("src") }
        }

        manga.description = document.selectFirst(
            "div.entry-content p, div.post-content p",
        )?.text()

        // Tags / genres
        val tags = document.select("a[rel=tag], .post-tags a, .tags-links a")
            .map { it.text() }
            .filter { it.isNotEmpty() }
        if (tags.isNotEmpty()) {
            manga.genre = tags.joinToString()
        }

        manga.status = SManga.COMPLETED
        manga.update_strategy = UpdateStrategy.ONLY_FETCH_ONCE

        return manga
    }

    // ======================== Chapters ========================

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val chapter = SChapter.create().apply {
            name = "CHAPTER"
            setUrlWithoutDomain(manga.url)
        }
        return Observable.just(listOf(chapter))
    }

    override fun chapterListSelector(): String = "unused"

    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException("Not used")

    // ======================== Pages ========================

    override fun pageListParse(document: Document): List<Page> {
        val pswp = document.select(".pswp-gallery__item")

        if (pswp.isNotEmpty()) {
            return pswp.mapIndexed { index, element ->
                val url = element.absUrl("data-pswp-src")
                Page(index, "", url)
            }
        }

        val images = document.select("div.entry-content img")

        return images.mapIndexed { index, img ->
            val url = img.absUrl("data-pagespeed-lazy-src")
                .ifBlank { img.absUrl("data-src") }
                .ifBlank { img.absUrl("src") }

            Page(index, imageUrl = url)
        }
    }

    override fun imageUrlParse(document: Document) = ""

    // ======================== Filters ========================

    override fun getFilterList() = FilterList()
}
