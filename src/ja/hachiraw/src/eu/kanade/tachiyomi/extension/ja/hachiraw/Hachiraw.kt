package eu.kanade.tachiyomi.extension.ja.hachiraw

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class Hachiraw : HttpSource() {

    override val name = "Hachiraw"

    override val baseUrl = "https://hachiraw.net"

    override val lang = "ja"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val dateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.ROOT)

    override fun popularMangaRequest(page: Int) = searchMangaRequest(
        page,
        "",
        FilterList(SortFilter(2)),
    )

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int) = searchMangaRequest(
        page,
        "",
        FilterList(SortFilter(0)),
    )

    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> = if (query.startsWith(PREFIX_SLUG_SEARCH)) {
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

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = filters.ifEmpty { getFilterList() }
        val sortFilter = filterList.firstInstanceOrNull<SortFilter>()
        val genreFilter = filterList.firstInstanceOrNull<GenreFilter>()

        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("list-manga")

            if (query.isNotEmpty()) {
                addQueryParameter("search", query)
            } else if (genreFilter != null && genreFilter.state != 0) {
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

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.ng-scope > div.top-15").map { element ->
            SManga.create().apply {
                element.selectFirst("a.ng-binding.SeriesName")!!.let {
                    setUrlWithoutDomain(it.attr("href"))
                    title = it.text()
                }
                thumbnail_url = element.selectFirst("img.img-fluid")?.absUrl("src")
            }
        }
        val hasNextPage = document.selectFirst("ul.pagination li:contains(→)") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val row = response.asJsoup().selectFirst("div.BoxBody > div.row")!!

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

    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup().select("a.ChapterLink").map { element ->
        SChapter.create().apply {
            setUrlWithoutDomain(element.attr("href"))
            name = element.selectFirst("span")!!.text()
            date_upload = dateFormat.tryParse(
                element.selectFirst("span.float-right")?.text(),
            )
        }
    }

    override fun pageListParse(response: Response): List<Page> = response.asJsoup().select("#TopPage img").mapIndexed { i, img ->
        Page(i, imageUrl = img.absUrl("src"))
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        Filter.Header("タイトルで検索する場合、ジャンルフィルターは無視されます"),
        Filter.Separator(),
        SortFilter(),
        GenreFilter(),
    )

    companion object {
        internal const val PREFIX_SLUG_SEARCH = "slug:"
    }
}
