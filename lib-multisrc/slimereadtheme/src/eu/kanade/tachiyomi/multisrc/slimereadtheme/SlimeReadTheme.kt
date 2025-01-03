package eu.kanade.tachiyomi.multisrc.slimereadtheme

import app.cash.quickjs.QuickJs
import eu.kanade.tachiyomi.extension.pt.slimeread.dto.ChapterDto
import eu.kanade.tachiyomi.extension.pt.slimeread.dto.LatestResponseDto
import eu.kanade.tachiyomi.extension.pt.slimeread.dto.MangaInfoDto
import eu.kanade.tachiyomi.extension.pt.slimeread.dto.PageListDto
import eu.kanade.tachiyomi.extension.pt.slimeread.dto.PopularMangaDto
import eu.kanade.tachiyomi.extension.pt.slimeread.dto.toSMangaList
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.min

abstract class SlimeReadTheme(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : HttpSource() {

    protected open val apiUrl: String by lazy { getApiUrlFromPage() }

    override val supportsLatest = true

    override val client = network.cloudflareClient

    private val json: Json by injectLazy()

    protected open val urlInfix: String = "slimeread.com"

    protected open fun getApiUrlFromPage(): String {
        val initClient = network.cloudflareClient
        val response = initClient.newCall(GET(baseUrl, headers)).execute()
        if (!response.isSuccessful) throw Exception("HTTP error ${response.code}")
        val document = response.asJsoup()
        val scriptUrl = document.selectFirst("script[src*=pages/_app]")?.attr("abs:src")
            ?: throw Exception("Could not find script URL")
        val scriptResponse = initClient.newCall(GET(scriptUrl, headers)).execute()
        if (!scriptResponse.isSuccessful) throw Exception("HTTP error ${scriptResponse.code}")
        val script = scriptResponse.body.string()
        val apiUrl = FUNCTION_REGEX.find(script)?.let { result ->
            val varBlock = result.groups["script"]?.value ?: return@let null
            val varUrlInfix = result.groups["infix"]?.value ?: return@let null

            val block = """${varBlock.replace(varUrlInfix, "\"$urlInfix\"")}.toString()"""

            try {
                QuickJs.create().use { it.evaluate(block) as String }
            } catch (e: Exception) {
                null
            }
        }

        return apiUrl?.let { "https://$it" } ?: throw Exception("Could not find API URL")
    }

    // ============================== Popular ===============================
    private var currentSlice = 0
    private var popularMangeCache: MangasPage? = null

    override fun popularMangaRequest(page: Int) =
        GET("$apiUrl/book_search?order=1&status=0", headers)

    // Returns a large JSON, so the app can't handle the list without pagination
    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        if (page == 1 || popularMangeCache == null) {
            popularMangeCache = super.fetchPopularManga(page)
                .toBlocking()
                .last()
        }

        // Handling a large manga list
        return Observable.just(popularMangeCache!!)
            .map { mangaPage ->
                val mangas = mangaPage.mangas
                val pageSize = 15

                currentSlice = (page - 1) * pageSize

                val startIndex = min(mangas.size - 1, currentSlice)
                val endIndex = min(mangas.size, currentSlice + pageSize)

                val slice = mangas.subList(startIndex, endIndex)

                MangasPage(slice, slice.isNotEmpty())
            }
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val items = response.parseAs<List<PopularMangaDto>>()
        val mangaList = items.toSMangaList()
        return MangasPage(mangaList, mangaList.isNotEmpty())
    }

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int) = GET("$apiUrl/books?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val dto = response.parseAs<LatestResponseDto>()
        val mangaList = dto.data.toSMangaList()
        val hasNextPage = dto.page < dto.pages
        return MangasPage(mangaList, hasNextPage)
    }

    // =============================== Search ===============================
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_SEARCH)) { // URL intent handler
            val id = query.removePrefix(PREFIX_SEARCH)
            client.newCall(GET("$apiUrl/book/$id", headers))
                .asObservableSuccess()
                .map(::searchMangaByIdParse)
        } else {
            super.fetchSearchManga(page, query, filters)
        }
    }

    private fun searchMangaByIdParse(response: Response): MangasPage {
        val details = mangaDetailsParse(response)
        return MangasPage(listOf(details), false)
    }

    override fun getFilterList() = SlimeReadThemeFilters.FILTER_LIST

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val params = SlimeReadThemeFilters.getSearchParameters(filters)

        val url = "$apiUrl/book_search".toHttpUrl().newBuilder()
            .addIfNotBlank("query", query)
            .addIfNotBlank("genre[]", params.genre)
            .addIfNotBlank("status", params.status)
            .addIfNotBlank("searchMethod", params.searchMethod)
            .apply {
                params.categories.forEach {
                    addQueryParameter("categories[]", it)
                }
            }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // =========================== Manga Details ============================
    override fun getMangaUrl(manga: SManga) = baseUrl + manga.url.replace("/book/", "/manga/")

    override fun mangaDetailsRequest(manga: SManga) = GET(apiUrl + manga.url, headers)

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val info = response.parseAs<MangaInfoDto>()
        thumbnail_url = info.thumbnail_url
        title = info.name
        description = info.description
        genre = info.categories.joinToString()
        url = "/book/${info.id}"
        status = when (info.status) {
            1 -> SManga.ONGOING
            2 -> SManga.COMPLETED
            3, 4 -> SManga.CANCELLED
            5 -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    // ============================== Chapters ==============================
    override fun chapterListRequest(manga: SManga) =
        GET("$apiUrl/book_cap_units_all?manga_id=${manga.url.substringAfterLast("/")}", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val items = response.parseAs<List<ChapterDto>>()
        val mangaId = response.request.url.queryParameter("manga_id")!!
        return items.map {
            SChapter.create().apply {
                name = "Cap " + parseChapterNumber(it.number)
                date_upload = parseChapterDate(it.updated_at)
                chapter_number = it.number
                scanlator = it.scan?.scan_name
                url = "/book_cap_units?manga_id=$mangaId&cap=${it.number}"
            }
        }.reversed()
    }

    private fun parseChapterNumber(number: Float): String {
        val cap = number + 1F
        return "%.2f".format(cap)
            .let { if (cap < 10F) "0$it" else it }
            .replace(",00", "")
            .replace(",", ".")
    }

    private fun parseChapterDate(date: String): Long {
        return try { dateFormat.parse(date)!!.time } catch (_: Exception) { 0L }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val url = "$baseUrl${chapter.url}".toHttpUrl()
        val id = url.queryParameter("manga_id")!!
        val cap = url.queryParameter("cap")!!.toFloat()
        val num = parseChapterNumber(cap)
        return "$baseUrl/ler/$id/cap-$num"
    }

    // =============================== Pages ================================
    override fun pageListRequest(chapter: SChapter) = GET(apiUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val body = response.body.string()
        val pages = if (body.startsWith("{")) {
            json.decodeFromString<Map<String, PageListDto>>(body).values.flatMap { it.pages }
        } else {
            json.decodeFromString<List<PageListDto>>(body).flatMap { it.pages }
        }

        return pages.mapIndexed { index, item ->
            Page(index, "", item.url)
        }
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

    // ============================= Utilities ==============================
    private inline fun <reified T> Response.parseAs(): T = use {
        json.decodeFromStream(it.body.byteStream())
    }

    private fun HttpUrl.Builder.addIfNotBlank(query: String, value: String): HttpUrl.Builder {
        if (value.isNotBlank()) addQueryParameter(query, value)
        return this
    }

    companion object {
        const val PREFIX_SEARCH = "id:"
        val FUNCTION_REGEX = """(?<script>\[""\.concat\("[^,]+,"\."\)\.concat\((?<infix>[^,]+),":\d+"\)\])""".toRegex(RegexOption.DOT_MATCHES_ALL)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
    }
}
