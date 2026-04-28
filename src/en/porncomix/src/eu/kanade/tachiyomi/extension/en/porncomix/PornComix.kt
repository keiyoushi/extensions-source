package eu.kanade.tachiyomi.extension.en.porncomix

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable

class PornComix : HttpSource() {

    override val name = "PornComix"

    override val baseUrl = "https://bestporncomix.com"

    override val lang = "en"

    override val versionId = 2

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

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("#loops-wrapper article").map { element ->
            SManga.create().apply {
                val anchor = element.selectFirst("h2.post-title a")!!
                setUrlWithoutDomain(anchor.attr("href"))
                title = anchor.text()

                thumbnail_url = element.selectFirst("img")?.let { img ->
                    img.absUrl("data-pagespeed-lazy-src")
                        .ifEmpty { img.absUrl("data-src") }
                        .ifEmpty { img.absUrl("src") }
                }
            }
        }
        val hasNextPage = document.selectFirst("a.nextp") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ======================== Latest (disabled) ========================

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

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

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // ======================== Details ========================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val manga = SManga.create()

        manga.title = document.selectFirst("h1.post-title, h1.entry-title")!!.text()

        manga.thumbnail_url = document.selectFirst(
            "div.post-inner img, div.entry-content img, article img",
        )?.let { img ->
            img.absUrl("data-pagespeed-lazy-src")
                .ifEmpty { img.absUrl("data-src") }
                .ifEmpty { img.absUrl("src") }
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

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException("Not used")

    // ======================== Pages ========================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val pswp = document.select(".pswp-gallery__item")

        if (pswp.isNotEmpty()) {
            return pswp.mapIndexed { index, element ->
                val url = element.absUrl("data-pswp-src")
                Page(index, imageUrl = url)
            }
        }

        val images = document.select("div.entry-content img")

        return images.mapIndexed { index, img ->
            val url = img.absUrl("data-pagespeed-lazy-src")
                .ifEmpty { img.absUrl("data-src") }
                .ifEmpty { img.absUrl("src") }

            Page(index, imageUrl = url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")

    // ======================== Filters ========================

    override fun getFilterList() = FilterList()
}
