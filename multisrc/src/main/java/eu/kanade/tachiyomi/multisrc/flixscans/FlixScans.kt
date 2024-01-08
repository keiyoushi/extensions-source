package eu.kanade.tachiyomi.multisrc.flixscans

import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy

abstract class FlixScans(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
    protected val apiUrl: String = baseUrl.replace("://", "://api.").plus("/api/v1"),
    protected val cdnUrl: String = baseUrl.replace("://", "://api.").plus("/storage/"),
) : HttpSource() {

    override val supportsLatest = true

    protected open val json: Json by injectLazy()

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", baseUrl)

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        runCatching { fetchGenre() }

        return super.fetchPopularManga(page)
    }

    override fun popularMangaRequest(page: Int): Request {
        return GET("$apiUrl/webtoon/homepage/home", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<HomeDto>()

        val entries = (result.hot + result.topAll + result.topMonth + result.topWeek)
            .distinctBy { it.id }
            .map { it.toSManga(cdnUrl) }

        return MangasPage(entries, false)
    }

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        runCatching { fetchGenre() }

        return super.fetchLatestUpdates(page)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$apiUrl/search/advance?page=$page&serie_type=webtoon", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.parseAs<ApiResponse<BrowseSeries>>()
        val currentPage = response.request.url.queryParameter("page")
            ?.toIntOrNull() ?: 1

        val entries = result.data.map { it.toSManga(cdnUrl) }
        val hasNextPage = result.meta.lastPage > currentPage

        return MangasPage(entries, hasNextPage)
    }

    private var fetchGenreList: List<GenreHolder> = emptyList()
    private var fetchGenreCallOngoing = false
    private var fetchGenreFailed = false
    private var fetchGenreAttempt = 0

    private fun fetchGenre() {
        if (fetchGenreAttempt < 3 && (fetchGenreList.isEmpty() || fetchGenreFailed) && !fetchGenreCallOngoing) {
            fetchGenreCallOngoing = true

            // fetch genre asynchronously as it sometimes hangs
            client.newCall(fetchGenreRequest()).enqueue(fetchGenreCallback)
        }
    }

    private val fetchGenreCallback = object : Callback {
        override fun onFailure(call: Call, e: okio.IOException) {
            fetchGenreAttempt++
            fetchGenreFailed = true
            fetchGenreCallOngoing = false

            e.message?.let { Log.e("$name Filters", it) }
        }

        override fun onResponse(call: Call, response: Response) {
            fetchGenreCallOngoing = false
            fetchGenreAttempt++

            if (!response.isSuccessful) {
                fetchGenreFailed = true
                response.close()

                return
            }

            val parsed = runCatching {
                response.use(::fetchGenreParse)
            }

            fetchGenreFailed = parsed.isFailure
            fetchGenreList = parsed.getOrElse {
                Log.e("$name Filters", it.stackTraceToString())
                emptyList()
            }
        }
    }

    private fun fetchGenreRequest(): Request {
        return GET("$apiUrl/search/genres", headers)
    }

    private fun fetchGenreParse(response: Response): List<GenreHolder> {
        return response.parseAs<List<GenreHolder>>()
    }

    override fun getFilterList(): FilterList {
        val filters: MutableList<Filter<*>> = mutableListOf(
            Filter.Header("Ignored when using Text Search"),
            MainGenreFilter(),
            TypeFilter(),
            StatusFilter(),
        )

        filters += if (fetchGenreList.isNotEmpty()) {
            listOf(
                GenreFilter("Genre", fetchGenreList),
            )
        } else {
            listOf(
                Filter.Separator(),
                Filter.Header("Press 'reset' to attempt to show Genres"),
            )
        }

        return FilterList(filters)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        runCatching { fetchGenre() }

        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val requestBody = SearchInput(query.trim())
                .let(json::encodeToString)
                .toRequestBody(JSON_MEDIA_TYPE)

            val newHeaders = headersBuilder()
                .add("Content-Length", requestBody.contentLength().toString())
                .add("Content-Type", requestBody.contentType().toString())
                .build()

            return POST("$apiUrl/search/serie?page=$page", newHeaders, requestBody)
        }

        val advSearchUrl = apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("search/advance")
            addQueryParameter("page", page.toString())
            addQueryParameter("serie_type", "webtoon")

            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> {
                        filter.checked.let {
                            if (it.isNotEmpty()) {
                                addQueryParameter("genres", it.joinToString(","))
                            }
                        }
                    }
                    is MainGenreFilter -> {
                        if (filter.state > 0) {
                            addQueryParameter("main_genres", filter.selected)
                        }
                    }
                    is TypeFilter -> {
                        if (filter.state > 0) {
                            addQueryParameter("type", filter.selected)
                        }
                    }
                    is StatusFilter -> {
                        if (filter.state > 0) {
                            addQueryParameter("status", filter.selected)
                        }
                    }
                    else -> {}
                }
            }
        }.build()

        return GET(advSearchUrl, headers)
    }

    override fun searchMangaParse(response: Response) = latestUpdatesParse(response)

    override fun mangaDetailsRequest(manga: SManga): Request {
        val id = manga.url.split("-")[1]

        return GET("$apiUrl/webtoon/series/$id", headers)
    }

    override fun getMangaUrl(manga: SManga) = baseUrl + manga.url

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<SeriesResponse>()

        return result.serie.toSManga(cdnUrl)
    }

    override fun chapterListRequest(manga: SManga): Request {
        val id = manga.url.split("-")[1]

        return GET("$apiUrl/webtoon/chapters/$id-desc", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = response.parseAs<List<Chapter>>()

        return chapters.map(Chapter::toSChapter)
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val id = chapter.url
            .substringAfterLast("/")
            .substringBefore("-")

        return GET("$apiUrl/webtoon/chapters/chapter/$id", headers)
    }

    override fun getChapterUrl(chapter: SChapter) = baseUrl + chapter.url

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<PageListResponse>()

        return result.chapter.chapterData.webtoon.mapIndexed { i, img ->
            Page(i, "", cdnUrl + img)
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException("Not Used")

    protected inline fun <reified T> Response.parseAs(): T =
        use { body.string() }.let(json::decodeFromString)

    companion object {
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaTypeOrNull()
    }
}
