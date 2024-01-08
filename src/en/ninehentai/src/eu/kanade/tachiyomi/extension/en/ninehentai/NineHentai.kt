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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import org.jsoup.nodes.Element
import rx.Observable
import rx.schedulers.Schedulers
import uy.kohesive.injekt.injectLazy
import java.util.Calendar

class NineHentai : HttpSource() {

    override val baseUrl = "https://9hentai.to"

    override val name = "NineHentai"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient

    private val json: Json by injectLazy()

    // Builds request for /api/getBooks endpoint
    private fun buildSearchRequest(
        searchText: String = "",
        page: Int,
        sort: Int = 0,
        range: List<Int> = listOf(0, 2000),
        includedTags: List<Tag> = listOf(),
        excludedTags: List<Tag> = listOf(),
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
        val jsonString = json.encodeToString(SearchRequestPayload(search = searchRequest))
        return POST("$baseUrl$SEARCH_URL", headers, jsonString.toRequestBody(MEDIA_TYPE))
    }

    private fun parseSearchResponse(response: Response): MangasPage {
        return response.use {
            val page = json.decodeFromString<SearchRequestPayload>(it.request.bodyString).search.page
            json.decodeFromString<SearchResponse>(it.body.string()).let { searchResponse ->
                MangasPage(
                    searchResponse.results.map {
                        SManga.create().apply {
                            url = "/g/${it.id}"
                            title = it.title
                            // Cover is the compressed first page (cover might change if page count changes)
                            thumbnail_url = "${it.image_server}${it.id}/1.jpg?${it.total_page}"
                        }
                    },
                    searchResponse.totalCount - 1 > page,
                )
            }
        }
    }

    // Builds request for /api/getBookById endpoint
    private fun buildDetailRequest(id: Int): Request {
        val jsonString = buildJsonObject { put("id", id) }.toString()
        return POST("$baseUrl$MANGA_URL", headers, jsonString.toRequestBody(MEDIA_TYPE))
    }

    // Popular

    override fun popularMangaRequest(page: Int): Request = buildSearchRequest(page = page, sort = 1)

    override fun popularMangaParse(response: Response): MangasPage = parseSearchResponse(response)

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = buildSearchRequest(page = page)

    override fun latestUpdatesParse(response: Response): MangasPage = parseSearchResponse(response)

    // Search
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith("id:")) {
            val id = query.substringAfter("id:").toInt()
            return client.newCall(buildDetailRequest(id))
                .asObservableSuccess()
                .map { response ->
                    fetchSingleManga(response)
                }
        }
        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = if (filters.isEmpty()) getFilterList() else filters
        var sort = 0
        val range = mutableListOf(0, 2000)
        val includedTags = mutableListOf<Tag>()
        val excludedTags = mutableListOf<Tag>()
        for (filter in filterList) {
            when (filter) {
                is SortFilter -> {
                    sort = filter.state
                }
                is MinPagesFilter -> {
                    try {
                        range[0] = filter.state.toInt()
                    } catch (_: NumberFormatException) {
                        // Suppress and retain default value
                    }
                }
                is MaxPagesFilter -> {
                    try {
                        range[1] = filter.state.toInt()
                    } catch (_: NumberFormatException) {
                        // Suppress and retain default value
                    }
                }
                is IncludedFilter -> {
                    includedTags += getTags(filter.state, 1)
                }
                is ExcludedFilter -> {
                    excludedTags += getTags(filter.state, 1)
                }
                is GroupFilter -> {
                    includedTags += getTags(filter.state, 2)
                }
                is ParodyFilter -> {
                    includedTags += getTags(filter.state, 3)
                }
                is ArtistFilter -> {
                    includedTags += getTags(filter.state, 4)
                }
                is CharacterFilter -> {
                    includedTags += getTags(filter.state, 5)
                }
                is CategoryFilter -> {
                    includedTags += getTags(filter.state, 6)
                }
                else -> { /* Do nothing */ }
            }
        }
        return buildSearchRequest(
            searchText = query,
            page = page,
            sort = sort,
            range = range,
            includedTags = includedTags,
            excludedTags = excludedTags,
        )
    }

    override fun searchMangaParse(response: Response): MangasPage = parseSearchResponse(response)

    // Manga Details

    override fun mangaDetailsParse(response: Response): SManga {
        return SManga.create().apply {
            response.asJsoup().selectFirst("div#bigcontainer")!!.let { info ->
                title = info.select("h1").text()
                thumbnail_url = info.selectFirst("div#cover v-lazy-image")!!.attr("abs:src")
                status = SManga.COMPLETED
                artist = info.selectTextOrNull("div.field-name:contains(Artist:) a.tag")
                author = info.selectTextOrNull("div.field-name:contains(Group:) a.tag") ?: "Unknown circle"
                genre = info.selectTextOrNull("div.field-name:contains(Tag:) a.tag")
                // Additional details
                description = listOf(
                    Pair("Alternative Title", info.selectTextOrNull("h2")),
                    Pair("Pages", info.selectTextOrNull("div#info > div:contains(pages)")),
                    Pair("Parody", info.selectTextOrNull("div.field-name:contains(Parody:) a.tag")),
                    Pair("Category", info.selectTextOrNull("div.field-name:contains(Category:) a.tag")),
                    Pair("Language", info.selectTextOrNull("div.field-name:contains(Language:) a.tag")),
                ).filterNot { it.second.isNullOrEmpty() }.joinToString("\n\n") { "${it.first}: ${it.second}" }
            }
        }
    }

    // Ensures no exceptions are thrown when scraping additional details
    private fun Element.selectTextOrNull(selector: String): String? {
        val list = this.select(selector)
        return if (list.isEmpty()) {
            null
        } else {
            list.joinToString(", ") { it.text() }
        }
    }

    // Chapter

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

    private fun parseChapterDate(date: String): Long {
        val dateStringSplit = date.split(" ")
        val value = dateStringSplit[0].toInt()

        return when (dateStringSplit[1].removeSuffix("s")) {
            "sec" -> Calendar.getInstance().apply {
                add(Calendar.SECOND, value * -1)
            }.timeInMillis
            "min" -> Calendar.getInstance().apply {
                add(Calendar.MINUTE, value * -1)
            }.timeInMillis
            "hour" -> Calendar.getInstance().apply {
                add(Calendar.HOUR_OF_DAY, value * -1)
            }.timeInMillis
            "day" -> Calendar.getInstance().apply {
                add(Calendar.DATE, value * -1)
            }.timeInMillis
            "week" -> Calendar.getInstance().apply {
                add(Calendar.DATE, value * 7 * -1)
            }.timeInMillis
            "month" -> Calendar.getInstance().apply {
                add(Calendar.MONTH, value * -1)
            }.timeInMillis
            "year" -> Calendar.getInstance().apply {
                add(Calendar.YEAR, value * -1)
            }.timeInMillis
            else -> {
                return 0
            }
        }
    }

    // Page List

    override fun pageListRequest(chapter: SChapter): Request {
        val mangaId = chapter.url.substringAfter("/g/").toInt()
        return buildDetailRequest(mangaId)
    }

    override fun pageListParse(response: Response): List<Page> {
        val resultsObj = json.parseToJsonElement(response.body.string()).jsonObject["results"]!!
        val manga = json.decodeFromJsonElement<Manga>(resultsObj)
        val imageUrl = manga.image_server + manga.id
        var totalPages = manga.total_page

        client.newCall(
            GET(
                "$imageUrl/preview/${totalPages}t.jpg",
                headersBuilder().build(),
            ),
        ).execute().code.let { code ->
            if (code == 404) totalPages--
        }

        return (1..totalPages).map {
            Page(it - 1, "", "$imageUrl/$it.jpg")
        }
    }

    private fun getTags(queries: String, type: Int): List<Tag> {
        return queries.split(",").map(String::trim)
            .filterNot(String::isBlank).mapNotNull { query ->
                val jsonString = buildJsonObject {
                    put("tag_name", query)
                    put("tag_type", type)
                }.toString()
                lookupTags(jsonString)
            }
    }

    // Based on HentaiHand ext
    private fun lookupTags(request: String): Tag? {
        return client.newCall(POST("$baseUrl$TAG_URL", headers, request.toRequestBody(MEDIA_TYPE)))
            .asObservableSuccess()
            .subscribeOn(Schedulers.io())
            .map { response ->
                // Returns the first matched tag, or null if there are no results
                val tagList = json.parseToJsonElement(response.body.string()).jsonObject["results"]!!.jsonArray.map {
                    json.decodeFromJsonElement<Tag>(it)
                }
                if (tagList.isEmpty()) {
                    return@map null
                } else {
                    tagList.first()
                }
            }.toBlocking().first()
    }

    private fun fetchSingleManga(response: Response): MangasPage {
        val resultsObj = json.parseToJsonElement(response.body.string()).jsonObject["results"]!!
        val manga = json.decodeFromJsonElement<Manga>(resultsObj)
        val list = listOf(
            SManga.create().apply {
                setUrlWithoutDomain("/g/${manga.id}")
                title = manga.title
                thumbnail_url = "${manga.image_server + manga.id}/cover.jpg"
            },
        )
        return MangasPage(list, false)
    }

    // Filters

    private class SortFilter : Filter.Select<String>(
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

    override fun imageUrlParse(response: Response): String = throw Exception("Not Used")

    private val Request.bodyString: String
        get() {
            val requestCopy = newBuilder().build()
            val buffer = Buffer()

            return runCatching { buffer.apply { requestCopy.body!!.writeTo(this) }.readUtf8() }
                .getOrNull() ?: ""
        }

    companion object {
        private val MEDIA_TYPE = "application/json; charset=utf-8".toMediaTypeOrNull()
        private const val SEARCH_URL = "/api/getBook"
        private const val MANGA_URL = "/api/getBookByID"
        private const val TAG_URL = "/api/getTag"
    }
}
