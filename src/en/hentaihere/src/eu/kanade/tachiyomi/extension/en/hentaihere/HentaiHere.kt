package eu.kanade.tachiyomi.extension.en.hentaihere

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable

class HentaiHere : HttpSource() {

    override val name = "HentaiHere"

    override val baseUrl = "https://hentaihere.com"

    override val lang = "en"

    override val supportsLatest = true

    // Popular
    override fun popularMangaRequest(page: Int) = searchMangaRequest(page, "", getFilterList())

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    // Search
    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> = if (query.startsWith(PREFIX_ID_SEARCH)) {
        val id = query.removePrefix(PREFIX_ID_SEARCH)
        client.newCall(searchMangaByIdRequest(id))
            .asObservableSuccess()
            .map { response -> searchMangaByIdParse(response, id) }
    } else {
        super.fetchSearchManga(page, query, filters)
    }

    private fun searchMangaByIdRequest(id: String) = GET("$baseUrl/m/$id")

    private fun searchMangaByIdParse(response: Response, id: String): MangasPage {
        val details = mangaDetailsParse(response)
        details.url = "/m/$id"
        return MangasPage(listOf(details), false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = if (filters.isEmpty()) getFilterList() else filters
        val sortFilter = filterList.firstInstanceOrNull<SortFilter>()!!
        val alphabetFilter = filterList.firstInstanceOrNull<AlphabetFilter>()!!
        val statusFilter = filterList.firstInstanceOrNull<StatusFilter>()!!
        val categoryFilter = filterList.firstInstanceOrNull<CategoryFilter>()!!

        val sortIndex = sortFilter.state
        val sortItem = sortFilterList[sortIndex]
        val sortMin = if (sortIndex >= 5) "newest" else sortItem.first

        val alphabetIndex = alphabetFilter.state
        val alphabetItem = alphabetFilterList[alphabetIndex]
        val alphabet = if (alphabetIndex != 0) "/${alphabetItem.first}" else ""

        val url = when {
            query.isNotEmpty() -> {
                "$baseUrl/search".toHttpUrl().newBuilder().apply {
                    addQueryParameter("s", query)
                    addQueryParameter("sort", sortMin)
                    addQueryParameter("page", page.toString())
                }.toString()
            }
            categoryFilter.state != 0 -> {
                val category = categoryFilterList[categoryFilter.state].first
                "$baseUrl/search/$category/$sortMin$alphabet?page=$page"
            }
            statusFilter.state != 0 -> {
                val status = statusFilterList[statusFilter.state].first
                "$baseUrl/directory/$status$alphabet?page=$page"
            }
            else -> {
                val sort = sortItem.first
                "$baseUrl/directory/$sort$alphabet?page=$page"
            }
        }
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".item").map { element ->
            val a = element.select("a")
            val img = element.select(".pos-rlt img")
            val mutedText = element.select("div:not(.pos-rtl) > .text-muted").text()
            val artistName = mutedText
                .substringAfter("by ")
                .substringBefore(".")

            SManga.create().apply {
                setUrlWithoutDomain(a.attr("abs:href"))
                title = img.attr("alt")
                author = when (artistName) {
                    "-", "Unknown" -> null
                    else -> artistName
                }
                thumbnail_url = img.attr("src")
            }
        }
        val hasNextPage = document.selectFirst(".pagination > li:last-child:not(.disabled)") != null
        return MangasPage(mangas, hasNextPage)
    }

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/directory/newest?page=$page")

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    // Details
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val categories = document.select("#info .text-info:contains(Cat) ~ a")
        val contents = document.select("#info .text-info:contains(Content:) ~ a")
        val licensed = categories.find { it.text() == "Licensed" }

        return SManga.create().apply {
            title = document.select("h4 > a").first()!!.ownText()
            author = document.select("#info .text-info:contains(Artist:) ~ a")
                .joinToString { it.text() }
            description = document.select("#info > div:has(> .text-info:contains(Brief Summary:))")
                .first()
                ?.ownText()
                ?.takeUnless { it == "Nothing yet!" }
            genre = (categories + contents).joinToString { it.text() }
            status = when (licensed) {
                null -> document.select("#info .text-info:contains(Status:) ~ a")
                    .first()
                    ?.text()
                    ?.toStatus()
                    ?: SManga.UNKNOWN
                else -> SManga.LICENSED
            }
            thumbnail_url = document.select("#cover img").first()!!.attr("src")
        }
    }

    // Chapters
    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup().select("li.sub-chp > a").map { element ->
        val chapterName = element.text().substringBefore("(").trim()
        val chapterNumber = chapterName.substringBefore(" ").toFloatOrNull() ?: -1f

        SChapter.create().apply {
            setUrlWithoutDomain(element.attr("abs:href"))
            name = chapterName
            chapter_number = chapterNumber
        }
    }

    // Pages
    override fun pageListParse(response: Response): List<Page> = response.parseAs<List<String>> {
        it.substringAfter("var rff_imageList = ").substringBefore(";")
    }.mapIndexed { i, imagePath ->
        Page(i, imageUrl = "$IMAGE_SERVER_URL/hentai$imagePath")
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    // Filters
    override fun getFilterList(): FilterList = FilterList(
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
