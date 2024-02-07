package eu.kanade.tachiyomi.extension.en.earlymanga

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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

class EarlyManga : HttpSource() {

    override val name = "EarlyManga"

    override val baseUrl = "https://earlym.org"

    private val apiUrl = "$baseUrl/api"

    override val lang = "en"

    override val supportsLatest = true

    override val versionId = 2

    private val json: Json by injectLazy()

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", baseUrl)

    private val apiHeaders by lazy {
        headersBuilder()
            .set("Accept", ACCEPT_JSON)
            .build()
    }

    /* Popular */
    override fun popularMangaRequest(page: Int) =
        searchMangaRequest(page, "", OrderByFilter.POPULAR)
    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    /* latest */
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$apiUrl/home/show-more?page=$page")
    }

    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    /* search */
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val triFilters = filters.filterIsInstance<TriStateFilterGroup>()

        val payload = SearchPayload(
            excludedGenres_all = triFilters.flatMap { it.excluded },
            includedGenres_all = triFilters.flatMap { it.included },
            includedLanguages = filters.filterIsInstance<TypeFilter>().flatMap { it.checked },
            includedPubstatus = filters.filterIsInstance<StatusFilter>().flatMap { it.checked },
            list_order = filters.filterIsInstance<SortFilter>().firstOrNull()?.selected ?: "desc",
            list_type = filters.filterIsInstance<OrderByFilter>().firstOrNull()?.selected ?: "Views",
            term = query.trim(),
        )
            .let(json::encodeToString)
            .toRequestBody(JSON_MEDIA_TYPE)

        return POST("$apiUrl/search/advanced/post?page=$page", apiHeaders, payload)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        runCatching(::fetchGenres)

        val result = response.parseAs<SearchResponse>()

        return MangasPage(
            result.data.map {
                SManga.create().apply {
                    url = "/manga/${it.id}/${it.slug}"
                    title = it.title
                    thumbnail_url = it.cover?.let { cover ->
                        "$baseUrl/storage/uploads/covers_optimized_mangalist/manga_${it.id}/$cover"
                    }
                }
            },
            hasNextPage = result.meta.last_page > result.meta.current_page,
        )
    }

    /* Filters */
    private var genresMap: Map<String, List<String>> = emptyMap()
    private var fetchGenresAttempts = 0
    private var fetchGenresFailed = false

    private fun fetchGenres() {
        if (fetchGenresAttempts < 3 && (genresMap.isEmpty() || fetchGenresFailed)) {
            val genres = runCatching {
                client.newCall(GET("$apiUrl/search/filter", headers))
                    .execute()
                    .use { parseGenres(it) }
            }

            fetchGenresFailed = genres.isFailure
            genresMap = genres.getOrNull().orEmpty()
            fetchGenresAttempts++
        }
    }

    private fun parseGenres(response: Response): Map<String, List<String>> {
        val filterResponse = response.parseAs<FilterResponse>()

        val result = mutableMapOf<String, List<String>>()

        result["Genres"] = filterResponse.genres.map { it.name }
        result["Sub Genres"] = filterResponse.sub_genres.map { it.name }
        result["Content"] = filterResponse.contents.map { it.name }
        result["Demographic"] = filterResponse.demographics.map { it.name }
        result["Format"] = filterResponse.formats.map { it.name }
        result["Themes"] = filterResponse.themes.map { it.name }

        return result
    }

    override fun getFilterList(): FilterList {
        val filters = mutableListOf(
            OrderByFilter(),
            SortFilter(),
            Filter.Separator(),
            TypeFilter(),
            StatusFilter(),
            Filter.Separator(),
        )

        filters += if (genresMap.isNotEmpty()) {
            genresMap.map { it ->
                TriStateFilterGroup(it.key, it.value.map { Pair(it, it) })
            }
        } else {
            listOf(Filter.Header("Press 'Reset' to attempt to show genres"))
        }

        return FilterList(filters)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET("$apiUrl${manga.url}", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<MangaResponse>().main_manga
        return SManga.create().apply {
            url = "/manga/${result.id}/${result.slug}"
            title = result.title
            author = result.authors?.joinToString { it.trim() }
            artist = result.artists?.joinToString { it.trim() }
            description = buildString {
                result.desc?.trim()?.also { append(it, "\n\n") }
                result.alt_titles?.joinToString("\n") { "• ${it.name.trim()}" }
                    ?.takeUnless { it.isEmpty() }
                    ?.also { append("Alternative Names:\n", it) }
            }
            genre = result.all_genres?.joinToString { it.name.trim() }
            status = result.pubstatus[0].name.parseStatus()
            thumbnail_url = result.cover?.let { cover ->
                "$baseUrl/storage/uploads/covers/manga_${result.id}/$cover"
            }
        }
    }

    override fun getMangaUrl(manga: SManga): String {
        return "$baseUrl${manga.url}"
    }

    override fun chapterListRequest(manga: SManga): Request {
        return GET("$apiUrl${manga.url}/chapterlist", headers)
    }
    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<List<ChapterList>>()

        val mangaUrl = response.request.url.toString()
            .substringBefore("/chapterlist")
            .substringAfter(apiUrl)

        return result.map { chapter ->
            SChapter.create().apply {
                url = "$mangaUrl/${chapter.id}/chapter-${chapter.slug}"
                name = "Chapter ${chapter.chapter_number}" + if (chapter.title.isNullOrEmpty()) "" else ": ${chapter.title}"
                date_upload = runCatching {
                    dateFormat.parse(chapter.created_at!!)!!.time
                }.getOrDefault(0L)
            }
        }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        return "$baseUrl${chapter.url}"
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return GET("$apiUrl${chapter.url}", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<PageListResponse>().chapter
        val chapterUrl = response.request.url.toString()
            .replace("/api", "")
        val preSlug = if (result.on_disk != 0) {
            "storage/uploads/manga"
        } else {
            "e-storage/uploads/manga"
        }
        return result.images
            .filterNot { it.endsWith(".ico") }
            .mapIndexed { index, img ->
                Page(index = index, url = chapterUrl, imageUrl = "$baseUrl/$preSlug/manga_${result.manga_id}/chapter_${result.slug}/$img")
            }
    }

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headersBuilder()
            .set("Referer", page.url)
            .set("Accept", ACCEPT_IMAGE)
            .build()

        return GET(page.imageUrl!!, imageHeaders)
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException("not Used")
    }

    private fun String.parseStatus(): Int {
        return when (this) {
            "Ongoing" -> SManga.ONGOING
            "Completed" -> SManga.COMPLETED
            "Cancelled" -> SManga.CANCELLED
            "Hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    private inline fun <reified T> Response.parseAs(): T = use {
        json.decodeFromString(it.body.string())
    }

    companion object {
        private const val ACCEPT_JSON = "application/json, text/plain, */*"
        private const val ACCEPT_IMAGE = "image/avif, image/webp, */*"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()

        private val dateFormat by lazy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.ENGLISH)
        }
    }
}
