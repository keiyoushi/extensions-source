package eu.kanade.tachiyomi.extension.en.mangafre

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
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Evaluator
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

abstract class MangafreGlobal(
    override val lang: String,
    val supportsSearch: Boolean = true,
) : ParsedHttpSource() {
    override val baseUrl = "https://mangafre.com/"

    override val name = "Mangafre"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder().build()

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/comic/bookclass.html?type=hot_novel&page_num=$page&language=$lang")
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(popularMangaSelector()).map { popularMangaFromElement(it) }
        // Mangafre always has a next page button. So we give MangasPage.mangas empty.
        return MangasPage(mangas, true)
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

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return client.newCall(popularMangaRequest(page))
            .asObservableSuccess()
            .map { response ->
                popularMangaParse(response)
            }
    }

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/comic/bookclass.html?type=last_release&page_num=$page&language=$lang")
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(latestUpdatesSelector()).map { latestUpdatesFromElement(it) }
        return MangasPage(mangas, true)
    }

    override fun latestUpdatesSelector(): String = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String? = popularMangaNextPageSelector()

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return client.newCall(latestUpdatesRequest(page))
            .asObservableSuccess()
            .map { response ->
                popularMangaParse(response)
            }
    }

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

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(searchMangaSelector()).map { searchMangaFromElement(it) }
        return MangasPage(mangas, false)
    }
    override fun searchMangaSelector(): String = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector(): String? = popularMangaNextPageSelector()

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        if (!supportsSearch) {
            return Observable.just(MangasPage(emptyList(), false))
        }
        return client.newCall(searchMangaRequest(page, query, filters))
            .asObservableSuccess()
            .map { response ->
                searchMangaParse(response)
            }
    }

    // =============================== Filters ==============================

    // Can accept only one Genre at the time
    override fun getFilterList() = FilterList(
        Note,
        Filter.Separator(),
        GenreFilter(getGenreFilter(lang), 0),
    )

    // =========================== Manga Details ============================

    override fun mangaDetailsParse(document: Document): SManga {
        TODO("Not yet implemented")
    }

    private fun Element.parseAuthorsTo(manga: SManga) {
        TODO("Not yet implemented")
    }

    // =============================== Chapters ==============================

    override fun chapterFromElement(element: Element): SChapter {
        TODO("Not yet implemented")
    }


    // =============================== Pages ================================

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return client.newCall(GET(baseUrl + chapter.url))
            .asObservableSuccess()
            .map { response ->
                pageListParse(response.asJsoup())
            }
    }

    override fun pageListParse(document: Document): List<Page> {
        TODO("Not yet implemented")
    }

    override fun imageUrlParse(document: Document): String {
        TODO("Not yet implemented")
    }
}

// ============================= Utilities ==============================

private fun Element.imgAttr(): String = when {
    hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
    hasAttr("data-src") -> attr("abs:data-src")
    else -> attr("abs:src")
}

private fun parseChapterDate(date: String): Long {
    // Uppercase the first letter of the string
    val formattedDate = date.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.FRANCE) else it.toString() }
    return SimpleDateFormat("MMMM d, yyyy", Locale.FRANCE).parse(formattedDate)?.time ?: 0
}
