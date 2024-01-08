package eu.kanade.tachiyomi.extension.en.hentaihere

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy

class HentaiHere : ParsedHttpSource() {

    override val name = "HentaiHere"

    override val baseUrl = "https://hentaihere.com"

    override val lang = "en"

    override val supportsLatest = true

    private val json: Json by injectLazy()

    // Popular
    override fun popularMangaRequest(page: Int) =
        searchMangaRequest(page, "", getFilterList())

    override fun popularMangaSelector() =
        searchMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga =
        searchMangaFromElement(element)

    override fun popularMangaNextPageSelector() =
        searchMangaNextPageSelector()

    // Search
    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_ID_SEARCH)) {
            val id = query.removePrefix(PREFIX_ID_SEARCH)
            client.newCall(searchMangaByIdRequest(id))
                .asObservableSuccess()
                .map { response -> searchMangaByIdParse(response, id) }
        } else {
            super.fetchSearchManga(page, query, filters)
        }
    }

    private fun searchMangaByIdRequest(id: String) = GET("$baseUrl/m/$id")

    private fun searchMangaByIdParse(response: Response, id: String): MangasPage {
        val details = mangaDetailsParse(response)
        details.url = "/m/$id"
        return MangasPage(listOf(details), false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = if (filters.isEmpty()) getFilterList() else filters
        val sortFilter = filterList.find { it is SortFilter } as SortFilter
        val alphabetFilter = filterList.find { it is AlphabetFilter } as AlphabetFilter
        val statusFilter = filterList.find { it is StatusFilter } as StatusFilter
        val categoryFilter = filterList.find { it is CategoryFilter } as CategoryFilter

        val sortIndex = sortFilter.state
        val sortItem = sortFilterList[sortIndex]
        val sortMin = if (sortIndex >= 5) "newest" else sortItem.first

        val alphabetIndex = alphabetFilter.state
        val alphabetItem = alphabetFilterList[alphabetIndex]
        val alphabet = if (alphabetIndex != 0) "/${alphabetItem.first}" else ""

        val url = when {
            // query + sort_min ~ /search?s=ore&sort=most-popular
            query.isNotBlank() -> {
                "$baseUrl/search".toHttpUrl().newBuilder().apply {
                    addQueryParameter("s", query)
                    addQueryParameter("sort", sortMin)
                    addQueryParameter("page", page.toString())
                }.toString()
            }
            // category + sort_min + alphabet (optional) ~ /search/t34/newest/a
            categoryFilter.state != 0 -> {
                val category = categoryFilterList[categoryFilter.state].first
                "$baseUrl/search/$category/$sortMin$alphabet?page=$page"
            }
            // status + alphabet  (optional) ~ /directory/ongoing/a
            statusFilter.state != 0 -> {
                val status = statusFilterList[statusFilter.state].first
                "$baseUrl/directory/$status$alphabet?page=$page"
            }
            // sort + alphabet (optional) ~ /directory/staff-pick/a
            else -> {
                val sort = sortItem.first
                "$baseUrl/directory/$sort$alphabet?page=$page"
            }
        }
        return GET(url, headers)
    }

    override fun searchMangaSelector() = ".item"

    override fun searchMangaFromElement(element: Element): SManga {
        val a = element.select(".pos-rlt a")
        val img = element.select(".pos-rlt img")
        val mutedText = element.select("div:not(.pos-rtl) > .text-muted").text()
        val artistName = mutedText
            .substringAfter("by ")
            .substringBefore(".")

        return SManga.create().apply {
            setUrlWithoutDomain(a.attr("href"))
            title = img.attr("alt")
            author = when (artistName) {
                "-", "Unknown" -> null
                else -> artistName
            }
            thumbnail_url = img.attr("src")
        }
    }

    override fun searchMangaNextPageSelector() =
        ".pagination > li:last-child:not(.disabled)"

    // Latest
    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/directory/newest?page=$page")

    override fun latestUpdatesSelector() = searchMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga =
        searchMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() =
        searchMangaNextPageSelector()

    // Details
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val categories = document.select("#info .text-info:contains(Cat) ~ a")
        val contents = document.select("#info .text-info:contains(Content:) ~ a")
        val licensed = categories.find { it.text() == "Licensed" }

        title = document.select("h4 > a").first()!!.ownText()
        author = document.select("#info .text-info:contains(Artist:) ~ a")
            .joinToString { it.text() }

        description = document.select("#info > div:has(> .text-info:contains(Brief Summary:))")
            .first()
            ?.ownText()
        if (description == "Nothing yet!") { description = "" }

        genre = (categories + contents).joinToString { it.text() }
        status = when (licensed) {
            null -> document.select("#info .text-info:contains(Status:) ~ a")
                .first()
                ?.text()
                ?.toStatus()
                ?: SManga.UNKNOWN
            else -> SManga.LICENSED
        }
        thumbnail_url = document.select("#cover img")
            .first()!!
            .attr("src")
    }

    // Chapters
    override fun chapterListSelector() = "li.sub-chp > a"

    override fun chapterFromElement(element: Element): SChapter {
        val chapterName = element
            .text()
            .substringBefore("(")
            .trim()
        val chapterNumber = chapterName
            .substringBefore(" ")
            .toFloatOrNull() ?: -1f

        return SChapter.create().apply {
            setUrlWithoutDomain(element.attr("href"))
            name = chapterName
            chapter_number = chapterNumber
        }
    }

    // Pages
    override fun pageListParse(response: Response): List<Page> =
        json.decodeFromString<List<String>>(
            response.body.string()
                .substringAfter("var rff_imageList = ")
                .substringBefore(";"),
        ).mapIndexed { i, imagePath ->
            Page(i, "", "$IMAGE_SERVER_URL/hentai$imagePath")
        }

    override fun pageListParse(document: Document): List<Page> =
        throw UnsupportedOperationException("Not used.")

    override fun imageUrlParse(document: Document) =
        throw UnsupportedOperationException("Not used.")

    // Filters
    override fun getFilterList(): FilterList {
        return FilterList(
            Filter.Header("Note: Some ignored for text search, category!"),
            Filter.Header("Note: Ignored when used with status!"),
            SortFilter(sortFilterList.map { it.second }.toTypedArray()),
            Filter.Separator(),
            Filter.Header("Note: Ignored for text search!"),
            AlphabetFilter(alphabetFilterList.map { it.second }.toTypedArray()),
            Filter.Separator(),
            Filter.Header("Note: Ignored for text search, category!"),
            StatusFilter(statusFilterList.map { it.second }.toTypedArray()),
            Filter.Separator(),
            Filter.Header("Note: Ignored for text search!"),
            CategoryFilter(categoryFilterList.map { it.second }.toTypedArray()),
        )
    }

    val sortFilterList = listOf(
        Pair("newest", "Newest"),
        Pair("most-popular", "Most Popular"),
        Pair("last-updated", "Last Updated"),
        Pair("most-viewed", "Most Viewed"),
        Pair("alphabetical", "Alphabetical"),
        Pair("", "----"),
        Pair("staff-pick", "Staff Pick"),
        Pair("last-month", "Popular (Monthly)"),
        Pair("last-week", "Popular (Weekly)"),
        Pair("yesterday", "Popular (Daily)"),
        Pair("trending", "Trending"),
    )

    val alphabetFilterList = listOf(
        Pair("", "All"),
        Pair("a", "A"),
        Pair("b", "B"),
        Pair("c", "C"),
        Pair("d", "D"),
        Pair("e", "E"),
        Pair("f", "F"),
        Pair("g", "G"),
        Pair("h", "H"),
        Pair("i", "I"),
        Pair("j", "J"),
        Pair("k", "K"),
        Pair("l", "L"),
        Pair("m", "M"),
        Pair("n", "N"),
        Pair("o", "O"),
        Pair("p", "P"),
        Pair("q", "Q"),
        Pair("r", "R"),
        Pair("s", "S"),
        Pair("t", "T"),
        Pair("u", "U"),
        Pair("v", "V"),
        Pair("w", "W"),
        Pair("x", "X"),
        Pair("y", "Y"),
        Pair("z", "Z"),
    )

    val statusFilterList = listOf(
        Pair("", "All"),
        Pair("ongoing", "Ongoing"),
        Pair("completed", "Completed"),
    )

    // /tags/category
    // copy($$('.item > a').map(el => `Pair("t${/[^T]+$/.exec(el.href)[0]}", "${el.querySelector("span.clear > span").textContent}"),`).join("\r\n"))
    val categoryFilterList = listOf(
        Pair("", "All"),
        Pair("t34", "Adult"),
        Pair("t7", "Anal"),
        Pair("t372", "Beastiality"),
        Pair("t20", "Big Breasts"),
        Pair("t43", "Comedy"),
        Pair("t46", "Compilation"),
        Pair("t42", "Doujinshi"),
        Pair("t40", "Ecchi"),
        Pair("t6", "Fantasy"),
        Pair("t14", "Futanari"),
        Pair("t302", "Guro"),
        Pair("t31", "Harem"),
        Pair("t15", "Incest"),
        Pair("t2650", "Isekai (Otherworld)"),
        Pair("t2158", "Korean Comic"),
        Pair("t50", "Licensed"),
        Pair("t17", "Lolicon"),
        Pair("t30", "Mecha"),
        Pair("t2503", "No Penetration"),
        Pair("t33", "Oneshot"),
        Pair("t23", "Rape"),
        Pair("t567", "Reverse Harem"),
        Pair("t41", "Romance"),
        Pair("t432", "Scat"),
        Pair("t48", "School Life"),
        Pair("t5", "Sci-fi"),
        Pair("t32", "Serialized"),
        Pair("t44", "Shotacon"),
        Pair("t49", "Tragedy"),
        Pair("t47", "Uncensored"),
        Pair("t27", "Yaoi"),
        Pair("t28", "Yuri"),
    )

    class SortFilter(sortables: Array<String>, state: Int = 1) :
        Filter.Select<String>("Sort", sortables, state)

    class AlphabetFilter(alphabet: Array<String>) :
        Filter.Select<String>("Starts With", alphabet, 0)

    class StatusFilter(statuses: Array<String>) :
        Filter.Select<String>("Status", statuses, 0)

    class CategoryFilter(categories: Array<String>) :
        Filter.Select<String>("Category", categories, 0)

    private fun String.toStatus(): Int = when (this) {
        "Completed" -> SManga.COMPLETED
        "Ongoing" -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    companion object {
        private const val IMAGE_SERVER_URL = "https://hentaicdn.com"
        const val PREFIX_ID_SEARCH = "id:"
    }
}
