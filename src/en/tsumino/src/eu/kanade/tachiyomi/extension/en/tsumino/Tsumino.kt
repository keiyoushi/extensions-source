package eu.kanade.tachiyomi.extension.en.tsumino

import eu.kanade.tachiyomi.extension.en.tsumino.TsuminoUtils.Companion.cfDecodeEmails
import eu.kanade.tachiyomi.extension.en.tsumino.TsuminoUtils.Companion.getArtists
import eu.kanade.tachiyomi.extension.en.tsumino.TsuminoUtils.Companion.getChapter
import eu.kanade.tachiyomi.extension.en.tsumino.TsuminoUtils.Companion.getCollection
import eu.kanade.tachiyomi.extension.en.tsumino.TsuminoUtils.Companion.getDesc
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import rx.Observable
import uy.kohesive.injekt.injectLazy

class Tsumino : HttpSource() {

    override val name = "Tsumino"

    override val baseUrl = "https://www.tsumino.com"

    override val lang = "en"

    override val supportsLatest = true

    // Based on Pufei ext
    private val rewriteOctetStream: Interceptor = Interceptor { chain ->
        val originalResponse: Response = chain.proceed(chain.request())
        if (originalResponse.headers("Content-Type").contains("application/octet-stream") &&
            originalResponse.request.url.pathSegments.any { it == "parts" }
        ) {
            val orgBody = originalResponse.body.bytes()
            val newBody = orgBody.toResponseBody("image/jpeg".toMediaTypeOrNull())
            originalResponse.newBuilder()
                .body(newBody)
                .build()
        } else {
            originalResponse
        }
    }

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addNetworkInterceptor(rewriteOctetStream)
        .build()

    private val json: Json by injectLazy()

    @Serializable
    data class Manga(
        val id: Int,
        val title: String,
        val thumbnailUrl: String,
    )

    // Latest

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/Search/Operate/?PageNumber=$page&Sort=Newest")

    override fun latestUpdatesParse(response: Response): MangasPage {
        val mangaList = mutableListOf<SManga>()
        val jsonResponse = json.parseToJsonElement(response.body.string()).jsonObject

        for (element in jsonResponse["data"]!!.jsonArray) {
            val manga = json.decodeFromJsonElement<Manga>(element.jsonObject["entry"]!!)
            mangaList.add(
                SManga.create().apply {
                    setUrlWithoutDomain("/entry/${manga.id}")
                    title = manga.title
                    thumbnail_url = manga.thumbnailUrl
                },
            )
        }

        val currentPage = jsonResponse["pageNumber"]!!.jsonPrimitive.int
        val totalPage = jsonResponse["pageCount"]!!.jsonPrimitive.int

        return MangasPage(mangaList, currentPage < totalPage)
    }

    // Popular

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/Search/Operate/?PageNumber=$page&Sort=Popularity")

    override fun popularMangaParse(response: Response): MangasPage = latestUpdatesParse(response)

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        // Taken from github.com/NerdNumber9/TachiyomiEH
        val f = filters + getFilterList()
        val advSearch = f.filterIsInstance<AdvSearchEntryFilter>().flatMap { filter ->
            val splitState = filter.state.split(",").map(String::trim).filterNot(String::isBlank)
            splitState.map {
                AdvSearchEntry(filter.type, it.removePrefix("-"), it.startsWith("-"))
            }
        }
        val body = FormBody.Builder()
            .add("PageNumber", page.toString())
            .add("Text", query)
            .add("Sort", SortType.values()[f.filterIsInstance<SortFilter>().first().state].name)
            .add("List", "0")
            .add("Length", LengthType.values()[f.filterIsInstance<LengthFilter>().first().state].id.toString())
            .add("MinimumRating", f.filterIsInstance<MinimumRatingFilter>().first().state.toString())
            .apply {
                advSearch.forEachIndexed { index, entry ->
                    add("Tags[$index][Type]", entry.type.toString())
                    add("Tags[$index][Text]", entry.text)
                    add("Tags[$index][Exclude]", entry.exclude.toString())
                }

                if (f.filterIsInstance<ExcludeParodiesFilter>().first().state) {
                    add("Exclude[]", "6")
                }
            }
            .build()

        return POST("$baseUrl/Search/Operate/", headers, body)
    }

    private fun searchMangaByIdRequest(id: String) = GET("$baseUrl/entry/$id", headers)

    private fun searchMangaByIdParse(response: Response, id: String): MangasPage {
        val details = mangaDetailsParse(response)
        details.url = "/entry/$id"
        return MangasPage(listOf(details), false)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_ID_SEARCH)) {
            val id = query.removePrefix(PREFIX_ID_SEARCH)
            client.newCall(searchMangaByIdRequest(id))
                .asObservableSuccess()
                .map { response -> searchMangaByIdParse(response, id) }
        } else {
            super.fetchSearchManga(page, query, filters)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage = latestUpdatesParse(response)

    // Details

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val infoElement = document.select("div.book-page-container")
        return SManga.create().apply {
            title = document.select("meta[property=og:title]").first()!!.attr("content")
            artist = getArtists(document)
            author = artist
            status = SManga.COMPLETED
            thumbnail_url = infoElement.select("img").attr("src")
            description = getDesc(document)
            genre = document.select("#Tag a").joinToString { it.attr("data-define") }
        }
    }

    // Chapters

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        cfDecodeEmails(document)

        val collection = document.select(".book-collection-table a")
        return if (collection.isNotEmpty()) {
            getCollection(document, ".book-collection-table a")
        } else {
            getChapter(document, response)
        }
    }

    // Page List

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val pages = mutableListOf<Page>()
        val numPages = document.select("h1").text().split(" ").last()

        if (numPages.isNotEmpty()) {
            for (i in 1 until numPages.toInt() + 1) {
                val data = document.select("#image-container").attr("data-cdn")
                    .replace("[PAGE]", i.toString())
                pages.add(Page(i, "", data))
            }
        } else {
            throw UnsupportedOperationException("Error: Open in WebView and solve the Captcha!")
        }
        return pages
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")

    data class AdvSearchEntry(val type: Int, val text: String, val exclude: Boolean)

    override fun getFilterList() = FilterList(
        Filter.Header("Separate tags with commas (,)"),
        Filter.Header("Prepend with dash (-) to exclude"),
        TagFilter(),
        CategoryFilter(),
        CollectionFilter(),
        GroupFilter(),
        ArtistFilter(),
        ParodyFilter(),
        CharactersFilter(),
        UploaderFilter(),

        Filter.Separator(),

        SortFilter(),
        LengthFilter(),
        MinimumRatingFilter(),
        ExcludeParodiesFilter(),
    )

    class TagFilter : AdvSearchEntryFilter("Tags", 1)
    class CategoryFilter : AdvSearchEntryFilter("Categories", 2)
    class CollectionFilter : AdvSearchEntryFilter("Collections", 3)
    class GroupFilter : AdvSearchEntryFilter("Groups", 4)
    class ArtistFilter : AdvSearchEntryFilter("Artists", 5)
    class ParodyFilter : AdvSearchEntryFilter("Parodies", 6)
    class CharactersFilter : AdvSearchEntryFilter("Characters", 7)
    class UploaderFilter : AdvSearchEntryFilter("Uploaders", 8)
    open class AdvSearchEntryFilter(name: String, val type: Int) : Filter.Text(name)

    class SortFilter : Filter.Select<SortType>("Sort by", SortType.values())
    class LengthFilter : Filter.Select<LengthType>("Length", LengthType.values())
    class MinimumRatingFilter : Filter.Select<String>("Minimum rating", (0..5).map { "$it stars" }.toTypedArray())
    class ExcludeParodiesFilter : Filter.CheckBox("Exclude parodies")

    enum class SortType {
        Popularity,
        Newest,
        Oldest,
        Alphabetical,
        Rating,
        Pages,
        Views,
        Random,
        Comments,
    }

    enum class LengthType(val id: Int) {
        Any(0),
        Short(1),
        Medium(2),
        Long(3),
    }

    companion object {
        const val PREFIX_ID_SEARCH = "id:"
    }
}
