package eu.kanade.tachiyomi.extension.en.manhwaxxl

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class ManhwaXXL : ParsedHttpSource() {

    override val name = "Manhwa XXL"

    override val lang = "en"

    override val baseUrl = "https://manhwaxxl.com"

    override val supportsLatest = true

    // Site changed from BakaManga
    override val versionId = 2

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) =
        GET("$baseUrl/popular" + (if (page > 1) "/page/$page" else ""))

    override fun popularMangaSelector() = "section#page ul.row li"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("span.manga-name a")!!.attr("href"))
        title = element.selectFirst("span.manga-name h2")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    override fun popularMangaNextPageSelector() = "ul.pagination li.active:not(:last-child)"

    override fun latestUpdatesRequest(page: Int) =
        GET("$baseUrl/latest" + (if (page > 1) "/page/$page" else ""))

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotEmpty()) {
                addQueryParameter("s", query)
            } else {
                val filterList = if (filters.isEmpty()) getFilterList() else filters
                val genreFilter = filterList.find { it is GenreFilter } as GenreFilter
                val genreId = genreFilter.genres[genreFilter.state].id

                if (genreId.isEmpty()) {
                    addPathSegment("popular")
                } else {
                    addPathSegment("category")
                    addPathSegment(genreId)
                }
            }

            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val statusBadge = document.selectFirst("span.card-title i")?.classNames() ?: emptySet()

        title = document.selectFirst("span.card-title h1")!!.text()
        author = document.selectFirst("div:has(> i.fa-user)")?.ownText()
        description = document.selectFirst("div.manga-info")?.text()
        genre = document.select("ul.post-categories li").joinToString { it.text() }
        status = when {
            statusBadge.contains("fa-circle-check") -> SManga.COMPLETED
            statusBadge.contains("fa-rotate") -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
        thumbnail_url = document.selectFirst("div.card div.manga-avatar img")?.absUrl("src")
    }

    // Manga details page have paginated chapter list. We sacrifice `date_upload`
    // but we save a bunch of calls, since each page is like 12 chapters.
    override fun chapterListParse(response: Response): List<SChapter> {
        val detailsDocument = response.asJsoup()
        val firstChapter = detailsDocument.selectFirst("ul.chapters-list li.item-chapter a")?.absUrl("href")
            ?: return emptyList()
        val document = client.newCall(GET(firstChapter, headers)).execute().asJsoup()

        return document.select(chapterListSelector()).map { chapterFromElement(it) }.reversed()
    }

    override fun chapterListSelector() = "ul#slide-out a.chapter-link"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        name = element.text()
    }

    override fun pageListParse(document: Document) =
        document.select("div#viewer img").mapIndexed { i, it ->
            Page(i, imageUrl = it.absUrl("src"))
        }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        Filter.Header("Ignored if using text search"),
        GenreFilter(getGenreList()),
    )

    private data class Genre(val name: String, val id: String) {
        override fun toString() = name
    }

    private class GenreFilter(val genres: Array<Genre>) : Filter.Select<String>("Genre", genres.map { it.id }.toTypedArray())

    // https://manhwaxxl.com/genres
    // copy([...document.querySelectorAll("section#page ul li a:not([class])")].map((e) => `Genre("${e.textContent.trim()}", "${e.href.split("/").slice(-1)[0].replace(/#page$/u, "")}"),`).join("\n"))
    private fun getGenreList() = arrayOf(
        Genre("All", ""),
        Genre("Action", "action"),
        Genre("Adult", "adult"),
        Genre("BL", "bl"),
        Genre("Comedy", "comedy"),
        Genre("Doujinshi", "doujinshi"),
        Genre("Harem", "harem"),
        Genre("Horror", "horror"),
        Genre("Manga", "manga"),
        Genre("Manhwa", "manhwa"),
        Genre("Mature", "mature"),
        Genre("NTR", "ntr"),
        Genre("Romance", "romance"),
        Genre("Uncensore", "uncensore"),
        Genre("Webtoon", "webtoon"),
    )
}
