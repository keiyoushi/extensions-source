package eu.kanade.tachiyomi.extension.all.mitaku

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class Mitaku : ParsedHttpSource() {
    override val name = "Mitaku"

    override val baseUrl = "https://mitaku.net"

    override val lang = "all"

    override val supportsLatest = false

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/category/ero-cosplay/page/$page", headers)

    override fun popularMangaSelector() = "div.article-container article"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))

        title = element.selectFirst("a")!!.attr("title")

        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    override fun popularMangaNextPageSelector() = "div.wp-pagenavi a.page.larger"

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request {
        throw UnsupportedOperationException()
    }

    override fun latestUpdatesSelector(): String {
        throw UnsupportedOperationException()
    }

    override fun latestUpdatesFromElement(element: Element): SManga {
        throw UnsupportedOperationException()
    }

    override fun latestUpdatesNextPageSelector(): String? {
        throw UnsupportedOperationException()
    }

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = if (filters.isEmpty()) getFilterList() else filters
        val tagFilter = filterList.findInstance<TagFilter>()!!
        val categoryFilter = filterList.findInstance<CategoryFilter>()!!

        return when {
            query.isEmpty() && categoryFilter.state != 0 -> {
                val url = "$baseUrl/category/${categoryFilter.toUriPart()}/page/$page/"
                GET(url, headers)
            }
            query.isEmpty() && tagFilter.state.isNotEmpty() -> {
                val url = baseUrl.toHttpUrl().newBuilder()
                    .addPathSegment("tag")
                    .addPathSegment(tagFilter.toUriPart())
                    .addPathSegment("page")
                    .addPathSegment(page.toString())
                    .build()
                GET(url, headers)
            }
            query.isNotEmpty() -> {
                val url = "$baseUrl/page/$page/".toHttpUrl().newBuilder()
                    .addQueryParameter("s", query)
                    .build()
                GET(url, headers)
            }
            else -> latestUpdatesRequest(page)
        }
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        with(document.selectFirst("article")!!) {
            title = selectFirst("h1")!!.text()
            val catGenres = select("span.cat-links a").joinToString { it.text() }
            val tagGenres = select("span.tag-links a").joinToString { it.text() }
            genre = listOf(catGenres, tagGenres).filter { it.isNotEmpty() }.joinToString()
        }
    }

    // ============================== Chapters ==============================
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val chapter = SChapter.create().apply {
            url = manga.url
            chapter_number = 1F
            name = "Chapter"
        }

        return Observable.just(listOf(chapter))
    }

    override fun chapterListSelector(): String {
        throw UnsupportedOperationException()
    }

    override fun chapterFromElement(element: Element): SChapter {
        throw UnsupportedOperationException()
    }

    // =============================== Pages ================================

    override fun pageListParse(document: Document): List<Page> {
        val imageElements = document.select("a.msacwl-img-link")

        return imageElements.mapIndexed { index, element ->
            val imageUrl = element.absUrl("data-mfp-src")
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(document: Document): String {
        throw UnsupportedOperationException()
    }

    open class UriPartFilter(
        displayName: String,
        private val valuePair: Array<Pair<String, String>>,
    ) : Filter.Select<String>(displayName, valuePair.map { it.first }.toTypedArray()) {
        fun toUriPart() = valuePair[state].second
    }

    class CategoryFilter : UriPartFilter(
        "Category",
        arrayOf(
            Pair("Any", ""),
            Pair("Ero Cosplay", "/ero-cosplay"),
            Pair("Nude", "/nude"),
            Pair("Sexy Set", "/sexy-set"),
            Pair("Online Video", "/online-video"),
        ),
    )
    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("NOTE: Only one tag search"),
        Filter.Separator(),
        CategoryFilter(),
        TagFilter(),
    )

    class TagFilter : Filter.Text("Tag") {
        fun toUriPart(): String {
            return state.trim().lowercase().replace(" ", "-")
        }
    }

    private inline fun <reified T> Iterable<*>.findInstance() = find { it is T } as? T
}
