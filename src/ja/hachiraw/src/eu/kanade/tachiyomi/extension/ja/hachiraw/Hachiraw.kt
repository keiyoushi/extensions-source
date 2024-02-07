package eu.kanade.tachiyomi.extension.ja.hachiraw

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class Hachiraw : ParsedHttpSource() {

    override val name = "Hachiraw"

    override val baseUrl = "https://hachiraw.net"

    override val lang = "ja"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val dateFormat by lazy {
        SimpleDateFormat("dd-MM-yyyy", Locale.ROOT)
    }

    override fun popularMangaRequest(page: Int) =
        searchMangaRequest(
            page,
            "",
            FilterList(SortFilter(2)),
        )

    override fun popularMangaSelector() = searchMangaSelector()

    override fun popularMangaFromElement(element: Element) = searchMangaFromElement(element)

    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()

    override fun latestUpdatesRequest(page: Int) =
        searchMangaRequest(
            page,
            "",
            FilterList(SortFilter(0)),
        )

    override fun latestUpdatesSelector() = searchMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = searchMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = searchMangaNextPageSelector()

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_SLUG_SEARCH)) {
            val slug = query.removePrefix(PREFIX_SLUG_SEARCH)
            val manga = SManga.create().apply { url = "/manga/$slug" }

            fetchMangaDetails(manga)
                .map {
                    it.url = "/manga/$slug"
                    MangasPage(listOf(it), false)
                }
        } else {
            super.fetchSearchManga(page, query, filters)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = filters.ifEmpty { getFilterList() }
        val sortFilter = filterList.filterIsInstance<SortFilter>().firstOrNull()
        val genreFilter = filterList.filterIsInstance<GenreFilter>().firstOrNull()

        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("list-manga")

            if (query.isNotEmpty()) {
                addQueryParameter("search", query)
            } else if (genreFilter != null && genreFilter.state != 0) {
                // Searching by genre is under /manga-list
                setPathSegment(0, "manga-list")
                addPathSegment(genreFilter.items[genreFilter.state].id)
            }

            if (page > 1) {
                addPathSegment(page.toString())
            }

            if (sortFilter != null) {
                addQueryParameter("order_by", sortFilter.items[sortFilter.state].id)
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaSelector() = "div.ng-scope > div.top-15"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        element.selectFirst("a.ng-binding.SeriesName")!!.let {
            setUrlWithoutDomain(it.attr("href"))
            title = it.text()
        }

        thumbnail_url = element.selectFirst("img.img-fluid")?.absUrl("src")
    }

    override fun searchMangaNextPageSelector() = "ul.pagination li:contains(→)"

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val row = document.selectFirst("div.BoxBody > div.row")!!

        title = row.selectFirst("h1")!!.text()
        author = row.selectFirst("li.list-group-item:contains(著者)")?.ownText()
        genre = row.select("li.list-group-item:contains(ジャンル) a").joinToString { it.text() }
        thumbnail_url = row.selectFirst("img.img-fluid")?.absUrl("src")
        description = buildString {
            row.select("li.list-group-item:has(span.mlabel)").forEach {
                val key = it.selectFirst("span")!!.text().removeSuffix(":")
                val value = it.ownText()

                if (key == "著者" || key == "ジャンル" || value.isEmpty() || value == "-") {
                    return@forEach
                }

                append(key)
                append(": ")
                appendLine(value)
            }

            val desc = row.select("div.Content").text()

            if (desc.isNotEmpty()) {
                appendLine()
                append(desc)
            }
        }.trim()
    }

    override fun chapterListSelector() = "a.ChapterLink"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        name = element.selectFirst("span")!!.text()
        date_upload = try {
            val date = element.selectFirst("span.float-right")!!.text()
            dateFormat.parse(date)!!.time
        } catch (_: Exception) {
            0L
        }
    }

    override fun pageListParse(document: Document) =
        document.select("#TopPage img").mapIndexed { i, it ->
            Page(i, imageUrl = it.absUrl("src"))
        }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        // TODO: Not Google translate this
        // "Genre filter is ignored when searching by title"
        Filter.Header("タイトルで検索する場合、ジャンルフィルターは無視されます"),
        Filter.Separator(),
        SortFilter(),
        GenreFilter(),
    )

    companion object {
        internal const val PREFIX_SLUG_SEARCH = "slug:"
    }
}
