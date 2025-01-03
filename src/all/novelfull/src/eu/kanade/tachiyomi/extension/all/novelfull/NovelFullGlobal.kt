package eu.kanade.tachiyomi.extension.all.novelfull

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Evaluator
import rx.Observable

abstract class NovelFullGlobal(
    override val lang: String,
    val supportsSearch: Boolean = true,
) : ParsedHttpSource() {

    override val baseUrl = "https://allwebnovel.com" // https://mangafre.com

    override val name = "NovelFull"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder().build()

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/comic/bookclass.html?type=hot_novel&page_num=$page&language=$lang", headers)
    }

    override fun popularMangaSelector(): String {
        return "#list-page > .col-truyen-main .row"
    }

    override fun popularMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            title = element.select(Evaluator.Tag("a")).attr("alt")
            thumbnail_url = element.selectFirst(Evaluator.Tag("img"))!!.imgAttr()
            setUrlWithoutDomain(element.select(Evaluator.Tag("a")).attr("href"))
        }
    }

    override fun popularMangaNextPageSelector(): String? {
        return ".pagination > .next"
    }

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/comic/bookclass.html?type=last_release&page_num=$page&language=$lang")
    }

    override fun latestUpdatesSelector(): String = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String? = popularMangaNextPageSelector()

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            return GET("$baseUrl/comic/search.html?keyword=$query&page_num=$page")
        }
        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> {
                    val filterId = filter.selected
                    return GET("$baseUrl/comic/bookclass.html?type=category_novel&id=$filterId&page_num=$page&language=$lang")
                }
                else -> { }
            }
        }
        TODO("Need to implement empty search somewhere")
    }

    override fun searchMangaSelector(): String = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector(): String? = popularMangaNextPageSelector()

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        if (!supportsSearch && query.isNotEmpty()) {
            return Observable.just(MangasPage(emptyList(), false))
        }
        return super.fetchSearchManga(page, query, filters)
    }

    // =============================== Filters ==============================

    override fun getFilterList() = FilterList(
        Note,
        Filter.Separator(),
        GenreFilter(getGenreFilter(lang), 0),
    )

    // =========================== Manga Details ============================

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            title = document.selectFirst("h3.title")!!.text()
            description = document.selectFirst(".desc-text")!!.text()
                .replace("\\n", "\n")
            thumbnail_url = document.selectFirst(".books > .book > img")!!.imgAttr()
            document.selectFirst(Evaluator.Class("info"))!!.children().forEach {
                when (it.children().first()!!.text()) {
                    "Author:" -> it.parseAuthorsTo(this)
                    "Genre:" -> it.parseGenresTo(this)
                    "Status:" -> it.parseStatusTo(this)
                }
            }
        }
    }

    private fun Element.parseAuthorsTo(manga: SManga) {
        val authors = this.select("a").map { it.text() }
        manga.author = authors.joinToString(", ")
    }

    private fun Element.parseGenresTo(manga: SManga) {
        val genres = this.select("a").map { it.text() }
        manga.genre = genres.joinToString(", ")
    }

    private fun Element.parseStatusTo(manga: SManga) {
        this.selectFirst("h3:last-child").let {
            it!!
            when {
                it.text().contains("ongoing", ignoreCase = true) -> manga.status = SManga.ONGOING
                it.text().contains("completed", ignoreCase = true) -> manga.status = SManga.COMPLETED
                else -> {
                    Log.e("MangafreGlobal", "Unknown status: ${it.text()}")
                    manga.status = SManga.UNKNOWN
                }
            }
        }
    }

    // =============================== Chapters ==============================

    private fun chapterListRequest(manga: SManga, page: Int): Request {
        val url = (baseUrl + manga.url).toHttpUrl().newBuilder()
        if (page != 1) { // Cache ?
            url.addQueryParameter("page_num", page.toString())
        }
        return GET(url.build(), headers)
    }

    override fun chapterListRequest(manga: SManga): Request {
        throw UnsupportedOperationException("Not used")
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        // We need to get multiple pages for the chapters.
        // The first page is already loaded in the manga details page.
        // The rest of the pages are loaded with ?page_num=$page
        // Chapter list will be empty when there are no more pages.
        return client.newCall(chapterListRequest(manga, 1))
            .asObservableSuccess()
            .flatMap { firstResponse ->
                val firstDocument = firstResponse.asJsoup()

                // Determine the last page number from the pagination section.
                val lastPageElement = firstDocument.selectFirst(".last a")
                    ?.attr("href")

                val lastPageNumber = baseUrl.toHttpUrl().newBuilder().build()
                    .resolve(lastPageElement!!)
                    ?.queryParameter("page_num")
                    ?.toIntOrNull() ?: 1

                // Create an Observable to fetch all pages, starting with the first page.
                Observable.range(1, lastPageNumber)
                    .concatMap { page ->
                        if (page == 1) {
                            // Use the already fetched first page's response.
                            // Can't use chapterListParse here because response buffer is already closed.
                            Observable.just(
                                firstDocument.select(chapterListSelector()).map { chapterFromElement(it) },
                            )
                        } else {
                            // Fetch subsequent pages.
                            client.newCall(chapterListRequest(manga, page))
                                .asObservableSuccess()
                                .map { response -> chapterListParse(response) }
                        }
                    }
            }
            // Stop fetching if an empty list is encountered.
            .takeUntil { it.isEmpty() }
            // Combine all chapter lists into a single list.
            .reduce { acc, list -> acc + list }
    }

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            name = element.attr("title").trim()
            setUrlWithoutDomain(element.attr("href"))
        }
    }

    override fun chapterListSelector(): String {
        return "a:has(> span.chapter-text)" // <a><span class="chapter-text"></span></a>
    }

    // =============================== Pages ================================

    override fun pageListParse(document: Document): List<Page> {
        return document.select(imageListSelector()).mapIndexed { i, element ->
            Page(i, "", element.imgAttr())
        }
    }

    override fun imageUrlParse(document: Document): String {
        throw UnsupportedOperationException("Not used")
    }

    private fun imageListSelector() = "#chapter-content .row.top-item img"
}

// ============================= Utilities ==============================

private fun Element.imgAttr(): String = when {
    hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
    hasAttr("data-src") -> attr("abs:data-src")
    else -> attr("abs:src")
}
