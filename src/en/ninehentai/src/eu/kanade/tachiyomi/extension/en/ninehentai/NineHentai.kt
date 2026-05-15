package eu.kanade.tachiyomi.extension.en.ninehentai

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
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
import keiyoushi.utils.toJsonRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import java.util.Calendar

class NineHentai : HttpSource() {

    override val baseUrl = "https://9hentai.so"

    override val name = "NineHentai"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    // ============================== Popular ==============================
    override fun popularMangaRequest(page: Int): Request = buildSearchRequest(page = page, sort = 1)

    override fun popularMangaParse(response: Response): MangasPage = parseSearchResponse(response)

    // ============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request = buildSearchRequest(page = page)

    override fun latestUpdatesParse(response: Response): MangasPage = parseSearchResponse(response)

    // ============================== Search ===============================
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith("https://")) {
            val url = query.toHttpUrlOrNull()
            if (url != null && url.host == baseUrl.toHttpUrlOrNull()?.host) {
                val id = url.pathSegments.getOrNull(1)
                if (id != null) {
                    return fetchSearchManga(page, "id:$id", filters)
                }
            }
            return Observable.just(MangasPage(emptyList(), false))
        }

        if (query.startsWith("id:")) {
            val id = query.substringAfter("id:").toIntOrNull() ?: return Observable.just(MangasPage(emptyList(), false))
            return client.newCall(buildDetailRequest(id))
                .asObservableSuccess()
                .map { response -> fetchSingleManga(response) }
        }

        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = if (filters.isEmpty()) getFilterList() else filters

        val sort = filterList.firstInstanceOrNull<SortFilter>()?.state ?: 0
        val minPages = filterList.firstInstanceOrNull<MinPagesFilter>()?.state?.toIntOrNull() ?: 0
        val maxPages = filterList.firstInstanceOrNull<MaxPagesFilter>()?.state?.toIntOrNull() ?: 2000

        val includedTags = mutableListOf<Tag>()
        val excludedTags = mutableListOf<Tag>()

        filterList.firstInstanceOrNull<IncludedFilter>()?.state?.takeIf { it.isNotBlank() }?.let { includedTags += getTags(it, 1) }
        filterList.firstInstanceOrNull<ExcludedFilter>()?.state?.takeIf { it.isNotBlank() }?.let { excludedTags += getTags(it, 1) }
        filterList.firstInstanceOrNull<GroupFilter>()?.state?.takeIf { it.isNotBlank() }?.let { includedTags += getTags(it, 2) }
        filterList.firstInstanceOrNull<ParodyFilter>()?.state?.takeIf { it.isNotBlank() }?.let { includedTags += getTags(it, 3) }
        filterList.firstInstanceOrNull<ArtistFilter>()?.state?.takeIf { it.isNotBlank() }?.let { includedTags += getTags(it, 4) }
        filterList.firstInstanceOrNull<CharacterFilter>()?.state?.takeIf { it.isNotBlank() }?.let { includedTags += getTags(it, 5) }
        filterList.firstInstanceOrNull<CategoryFilter>()?.state?.takeIf { it.isNotBlank() }?.let { includedTags += getTags(it, 6) }

        return buildSearchRequest(
            searchText = query,
            page = page,
            sort = sort,
            range = listOf(minPages, maxPages),
            includedTags = includedTags,
            excludedTags = excludedTags,
        )
    }

    override fun searchMangaParse(response: Response): MangasPage = parseSearchResponse(response)

    // ============================== Details ==============================
    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val document = response.asJsoup()
        val info = document.selectFirst("div#bigcontainer") ?: return this

        title = info.select("h1").text()
        thumbnail_url = info.selectFirst("div#cover v-lazy-image")?.attr("abs:src")
        status = SManga.COMPLETED
        artist = info.selectTextOrNull("div.field-name:contains(Artist:) a.tag")
        author = info.selectTextOrNull("div.field-name:contains(Group:) a.tag") ?: "Unknown circle"
        genre = info.selectTextOrNull("div.field-name:contains(Tag:) a.tag")

        description = buildString {
            info.selectTextOrNull("h2")?.let { append("Alternative Title: ", it, "\n\n") }
            info.selectTextOrNull("div#info > div:contains(pages)")?.let { append("Pages: ", it, "\n\n") }
            info.selectTextOrNull("div.field-name:contains(Parody:) a.tag")?.let { append("Parody: ", it, "\n\n") }
            info.selectTextOrNull("div.field-name:contains(Category:) a.tag")?.let { append("Category: ", it, "\n\n") }
            info.selectTextOrNull("div.field-name:contains(Language:) a.tag")?.let { append("Language: ", it) }
        }.trim()
    }

    // ============================= Chapters ==============================
    override fun chapterListParse(response: Response): List<SChapter> {
        val time = response.asJsoup().select("div#info div time").text()
        return listOf(
            SChapter.create().apply {
                name = "Chapter"
                date_upload = parseChapterDate(time)
                url = response.request.url.encodedPath
            },
        )
    }

    // =============================== Pages ===============================
    override fun pageListRequest(chapter: SChapter): Request {
        val mangaId = chapter.url.substringAfter("/g/").substringBefore("/").toInt()
        return buildDetailRequest(mangaId)
    }

    override fun pageListParse(response: Response): List<Page> {
        val manga = response.parseAs<SingleMangaResponse>().results
        val imageUrl = manga.getImageUrl()
        var totalPages = manga.totalPage

        client.newCall(GET("$imageUrl/preview/${totalPages}t.jpg", headers)).execute().code.let { code ->
            if (code == 404) totalPages--
        }

        return (1..totalPages).map {
            Page(it - 1, imageUrl = "$imageUrl/$it.jpg")
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ==============================
    private class SortFilter :
        Filter.Select<String>(
            "Sort by",
            arrayOf("Newest", "Popular Right now", "Most Fapped", "Most Viewed", "By Title"),
        )
    private class MinPagesFilter : Filter.Text("Minimum Pages")
    private class MaxPagesFilter : Filter.Text("Maximum Pages")
    private class IncludedFilter : Filter.Text("Included Tags")
    private class ExcludedFilter : Filter.Text("Excluded Tags")
    private class ArtistFilter : Filter.Text("Artist")
    private class GroupFilter : Filter.Text("Group")
    private class ParodyFilter : Filter.Text("Parody")
    private class CharacterFilter : Filter.Text("Character")
    private class CategoryFilter : Filter.Text("Category")

    override fun getFilterList() = FilterList(
        Filter.Header("Search by id with \"id:\" in front of query"),
        Filter.Separator(),
        SortFilter(),
        MinPagesFilter(),
        MaxPagesFilter(),
        IncludedFilter(),
        ExcludedFilter(),
        ArtistFilter(),
        GroupFilter(),
        ParodyFilter(),
        CharacterFilter(),
        CategoryFilter(),
    )

    // ============================= Utilities =============================
    private fun buildSearchRequest(
        searchText: String = "",
        page: Int,
        sort: Int = 0,
        range: List<Int> = listOf(0, 2000),
        includedTags: List<Tag> = emptyList(),
        excludedTags: List<Tag> = emptyList(),
    ): Request {
        val searchRequest = SearchRequest(
            text = searchText,
            page = page - 1, // Source starts counting from 0, not 1
            sort = sort,
            pages = Range(range),
            tag = Items(
                items = TagArrays(
                    included = includedTags,
                    excluded = excludedTags,
                ),
            ),
        )
        val payload = SearchRequestPayload(search = searchRequest)
        // Store the queried page into a transient URL param to be safely pulled within `parseSearchResponse`
        return POST("$baseUrl$SEARCH_URL?req_page=$page", headers, payload.toJsonRequestBody())
    }

    private fun parseSearchResponse(response: Response): MangasPage {
        val reqPage = response.request.url.queryParameter("req_page")?.toIntOrNull() ?: 1
        val searchResponse = response.parseAs<SearchResponse>()
        val mangas = searchResponse.results.map { it.toSManga() }

        return MangasPage(mangas, searchResponse.totalCount > reqPage)
    }

    private fun buildDetailRequest(id: Int): Request = POST("$baseUrl$MANGA_URL", headers, IdRequest(id).toJsonRequestBody())

    private fun fetchSingleManga(response: Response): MangasPage {
        val manga = response.parseAs<SingleMangaResponse>().results
        return MangasPage(listOf(manga.toSManga()), false)
    }

    private fun Element.selectTextOrNull(selector: String): String? {
        val list = this.select(selector)
        return if (list.isEmpty()) {
            null
        } else {
            list.joinToString { it.text() }
        }
    }

    private fun parseChapterDate(date: String): Long {
        val dateStringSplit = date.split(" ")
        val value = dateStringSplit.getOrNull(0)?.toIntOrNull() ?: return 0L

        return when (dateStringSplit.getOrNull(1)?.removeSuffix("s")) {
            "sec" -> Calendar.getInstance().apply { add(Calendar.SECOND, -value) }.timeInMillis
            "min" -> Calendar.getInstance().apply { add(Calendar.MINUTE, -value) }.timeInMillis
            "hour" -> Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, -value) }.timeInMillis
            "day" -> Calendar.getInstance().apply { add(Calendar.DATE, -value) }.timeInMillis
            "week" -> Calendar.getInstance().apply { add(Calendar.DATE, -value * 7) }.timeInMillis
            "month" -> Calendar.getInstance().apply { add(Calendar.MONTH, -value) }.timeInMillis
            "year" -> Calendar.getInstance().apply { add(Calendar.YEAR, -value) }.timeInMillis
            else -> 0L
        }
    }

    private fun getTags(queries: String, type: Int): List<Tag> = queries.split(",")
        .map(String::trim)
        .filter(String::isNotEmpty)
        .mapNotNull { query ->
            val request = TagRequest(query, type)
            val response = client.newCall(POST("$baseUrl$TAG_URL", headers, request.toJsonRequestBody())).execute()
            response.parseAs<TagResponse>().results.firstOrNull()
        }

    companion object {
        private const val SEARCH_URL = "/api/getBook"
        private const val MANGA_URL = "/api/getBookByID"
        private const val TAG_URL = "/api/getTag"
    }
}
