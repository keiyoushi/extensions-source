package eu.kanade.tachiyomi.extension.all.lunaranime

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.Jsoup
import rx.Observable
import java.io.IOException
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class LunarAnime : HttpSource() {

    override val name = "Lunar Manga"

    override val baseUrl = "https://lunaranime.ru"

    override val lang = "all"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.url
            val hostCandidates = buildImageHostCandidates(url) ?: return@addInterceptor chain.proceed(request)

            val timeoutChain = chain
                .withConnectTimeout(IMAGE_CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
                .withReadTimeout(IMAGE_READ_TIMEOUT_SEC, TimeUnit.SECONDS)
                .withWriteTimeout(IMAGE_WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)

            var lastException: IOException? = null
            val lastIndex = hostCandidates.lastIndex

            for ((index, host) in hostCandidates.withIndex()) {
                val newUrl = url.newBuilder().host(host).build()
                val newRequest = request.newBuilder().url(newUrl).build()
                try {
                    val response = timeoutChain.proceed(newRequest)
                    if (response.isSuccessful) return@addInterceptor response
                    if (response.code !in RETRY_STATUS_CODES || index == lastIndex) {
                        return@addInterceptor response
                    }
                    response.close()
                } catch (e: IOException) {
                    lastException = e
                }
            }

            throw lastException ?: IOException("Image host fallback failed.")
        }
        .build()

    private val baseHttpUrl = baseUrl.toHttpUrl()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override fun popularMangaRequest(page: Int): Request = buildSearchRequest(
        page = page,
        query = "",
        filters = SearchFilters(sort = SORT_POPULAR, format = FORMAT_MANGA),
    )

    override fun popularMangaParse(response: Response): MangasPage = parseAniSearchPage(response)

    override fun latestUpdatesRequest(page: Int): Request = buildSearchRequest(
        page = page,
        query = "",
        filters = SearchFilters(sort = SORT_LATEST, format = FORMAT_MANGA),
    )

    override fun latestUpdatesParse(response: Response): MangasPage = parseAniSearchPage(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = buildSearchRequest(page, query, filters.toSearchFilters(format = FORMAT_MANGA))

    override fun searchMangaParse(response: Response): MangasPage = parseAniSearchPage(response)

    override fun mangaDetailsRequest(manga: SManga): Request {
        val id = mangaIdFromUrl(manga.url)
        val url = baseHttpUrl.newBuilder()
            .addPathSegments("api")
            .addPathSegments("manga")
            .addPathSegments("info")
            .addQueryParameter("id", id)
            .build()
        return GET(url.toString(), headers)
    }

    override fun mangaDetailsParse(response: Response): SManga = parseAniDetails(response)

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.fromCallable {
        val mangaId = mangaIdFromUrl(manga.url)
        fetchVermillionChapters(mangaId)
            .sortedWith(compareByDescending<SChapter> { it.chapter_number }.thenBy { it.name })
    }

    override fun chapterListRequest(manga: SManga): Request = throw UnsupportedOperationException("Not used.")

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException("Not used.")

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = Observable.fromCallable {
        val info = parseChapterUrl(chapter.url)
        fetchVermillionPages(info)
    }

    override fun pageListRequest(chapter: SChapter): Request = throw UnsupportedOperationException("Not used.")

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException("Not used.")

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used.")

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("Sort"),
        SortFilter(),
        Filter.Separator(),
        Filter.Header("Status"),
        StatusFilter(),
        Filter.Separator(),
        Filter.Header("Year"),
        YearFilter(),
        Filter.Separator(),
        Filter.Header("Genres"),
        GenreFilter(),
    )

    private fun parseAniSearch(response: Response): AniSearchResult {
        val result = response.parseAs<AniSearchResponse>()
        return AniSearchResult(
            mangas = result.data.page.media.map { it.toSManga(buildMangaRelativeUrl(it.id)) },
            pageInfo = result.data.page.pageInfo,
        )
    }

    private fun parseAniSearchPage(response: Response): MangasPage {
        val result = parseAniSearch(response)
        return MangasPage(
            mangas = result.mangas,
            hasNextPage = result.pageInfo.hasNextPage,
        )
    }

    private data class AniSearchResult(val mangas: List<SManga>, val pageInfo: PageInfo)

    private fun buildSearchRequest(page: Int, query: String, filters: SearchFilters): Request {
        val payload = SearchPayload(
            query = query,
            page = page,
            filters = filters,
        )
        val body = json.encodeToString(payload).toRequestBody(JSON_MEDIA_TYPE)
        val url = baseHttpUrl.newBuilder()
            .addPathSegments("api")
            .addPathSegments("manga")
            .addPathSegments("search")
            .addQueryParameter("page", page.toString())
            .build()
        return POST(url.toString(), headers, body)
    }

    private fun parseAniDetails(response: Response): SManga {
        val result = response.parseAs<AniInfoResponse>()
        val data = result.data
        return SManga.create().apply {
            title = data.title.displayTitle()
            author = data.author
            artist = data.artist
            description = data.description?.let { Jsoup.parse(it).text() }
            genre = data.genres.joinToString()
            status = data.status.toMangaStatus()
            thumbnail_url = data.coverImage.extraLarge
                ?: data.coverImage.large
                ?: data.coverImage.medium
            initialized = true
        }
    }

    private fun fetchVermillionChapters(mangaId: String): List<SChapter> {
        val body = json.encodeToString(VermillionChapterRequest(mangaId, refresh = false))
            .toRequestBody(JSON_MEDIA_TYPE)
        val requestUrl = baseHttpUrl.newBuilder()
            .addPathSegments("api")
            .addPathSegments("manga")
            .addPathSegments("vermillion")
            .addPathSegments("chapters")
            .build()
        val request = POST(requestUrl.toString(), headers, body)

        val result = client.newCall(request).execute().use { response ->
            response.parseAs<List<VermillionSource>>()
        }

        return result.flatMap { source ->
            source.chapters.map { chapter ->
                val chapterNumber = formatChapterNumber(chapter.number)
                SChapter.create().apply {
                    url = buildChapterUrl(mangaId, chapterNumber, DEFAULT_LANGUAGE, ChapterSource.VERMILLION)
                    name = buildChapterName(chapterNumber, chapter.title, DEFAULT_LANGUAGE)
                    chapter_number = chapter.number.toFloat()
                    scanlator = source.providerId
                }
            }
        }
    }

    private fun fetchVermillionPages(info: ChapterInfo): List<Page> {
        val body = json.encodeToString(VermillionImagesRequest(info.mangaId, info.chapterNumber))
            .toRequestBody(JSON_MEDIA_TYPE)
        val url = baseHttpUrl.newBuilder()
            .addPathSegments("api")
            .addPathSegments("manga")
            .addPathSegments("vermillion")
            .addPathSegments("images")
            .build()
        val request = POST(url.toString(), headers, body)
        val result = client.newCall(request).execute().use { response ->
            response.parseAs<VermillionImagesResponse>()
        }

        return result.images.sortedBy { it.index }.map { image ->
            val imageUrl = unwrapProxyUrl(image.url)
            Page(image.index, imageUrl = imageUrl)
        }
    }

    override fun getMangaUrl(manga: SManga): String {
        val id = mangaIdFromUrl(manga.url)
        return baseHttpUrl.newBuilder()
            .addPathSegment("manga")
            .addPathSegment(id)
            .build()
            .toString()
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val info = parseChapterUrl(chapter.url)
        val url = baseHttpUrl.newBuilder()
            .addPathSegment("manga")
            .addPathSegment(info.mangaId)
            .addPathSegment(info.chapterNumber)
            .apply {
                if (info.language.isNotBlank()) {
                    addQueryParameter("lang", info.language)
                }
            }
            .build()
        return url.toString()
    }

    private fun mangaIdFromUrl(url: String): String {
        val httpUrl = resolveUrl(url)
        val segments = httpUrl.pathSegments
        val mangaIndex = segments.indexOf("manga")
        return if (mangaIndex != -1 && mangaIndex + 1 < segments.size) {
            segments[mangaIndex + 1]
        } else {
            ""
        }
    }

    private fun buildChapterUrl(
        mangaId: String,
        chapterNumber: String,
        language: String,
        source: ChapterSource,
    ): String {
        val lang = language.ifBlank { DEFAULT_LANGUAGE }
        val url = baseHttpUrl.newBuilder()
            .addPathSegment("manga")
            .addPathSegment(mangaId)
            .addPathSegment(chapterNumber)
            .addQueryParameter("lang", lang)
            .addQueryParameter("source", source.id)
            .build()
        return url.toString()
    }

    private fun parseChapterUrl(url: String): ChapterInfo {
        val httpUrl = resolveUrl(url)
        val mangaId = httpUrl.pathSegments.getOrNull(1).orEmpty()
        val chapterNumber = httpUrl.pathSegments.getOrNull(2).orEmpty()
        val language = httpUrl.queryParameter("lang").orEmpty()
        val source = ChapterSource.fromId(httpUrl.queryParameter("source"))
        return ChapterInfo(mangaId, chapterNumber, language, source)
    }

    private fun formatChapterNumber(number: Float): String {
        val asInt = number.toInt()
        return if (asInt.toFloat() == number) {
            asInt.toString()
        } else {
            number.toString()
        }
    }

    private fun buildChapterName(number: String, title: String?, language: String): String {
        val baseTitle = title?.takeIf { it.isNotBlank() } ?: "Chapter $number"
        val lang = language.ifBlank { DEFAULT_LANGUAGE }
        return "$baseTitle [${lang.uppercase(Locale.ROOT)}]"
    }

    private fun resolveUrl(url: String) = url.toHttpUrlOrNull()
        ?: baseHttpUrl.resolve(url)
        ?: baseHttpUrl.resolve(url.removePrefix("/"))
        ?: throw IllegalArgumentException("Invalid url: $url")

    private fun buildMangaRelativeUrl(id: Int): String {
        val url = baseHttpUrl.newBuilder()
            .addPathSegment("manga")
            .addPathSegment(id.toString())
            .build()
        return url.encodedPath
    }

    private fun unwrapProxyUrl(url: String): String {
        val httpUrl = url.toHttpUrlOrNull() ?: return url
        if (httpUrl.host != "cluster.lunaranime.ru") return url

        val isProxy = httpUrl.encodedPath.startsWith("/api/proxy/")
        if (!isProxy) return url

        val inner = httpUrl.queryParameter("url") ?: return url
        return if (inner.isNotBlank()) inner else url
    }

    private fun buildImageHostCandidates(url: okhttp3.HttpUrl): List<String>? {
        if (!url.encodedPath.startsWith("/media/")) return null

        val host = url.host
        if (!HOST_PREFIX_REGEX.matches(host)) return null

        val dotIndex = host.indexOf('.')
        if (dotIndex <= 0 || dotIndex == host.lastIndex) return null

        val suffix = host.substring(dotIndex + 1)
        val preferredHost = "s%02d.%s".format(PREFERRED_IMAGE_HOST_INDEX, suffix)
        val candidates = ArrayList<String>(IMAGE_HOST_RANGE.count() + 2)
        candidates.add(preferredHost)
        if (host != preferredHost) {
            candidates.add(host)
        }

        for (i in IMAGE_HOST_RANGE) {
            val candidate = "s%02d.%s".format(i, suffix)
            if (candidate != preferredHost && candidate != host) candidates.add(candidate)
        }

        return candidates
    }

    private fun String?.toMangaStatus(): Int = when (this?.uppercase(Locale.ROOT)) {
        "RELEASING", "ONGOING" -> SManga.ONGOING
        "FINISHED", "COMPLETED" -> SManga.COMPLETED
        "NOT_YET_RELEASED" -> SManga.UNKNOWN
        "CANCELLED" -> SManga.CANCELLED
        "HIATUS" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    private fun AniTitle.displayTitle(): String = english ?: romaji ?: native ?: "Unknown"

    private fun AniMedia.toSManga(url: String): SManga = SManga.create().apply {
        title = this@toSManga.title.english
            ?: this@toSManga.title.romaji
            ?: this@toSManga.title.native
            ?: "Unknown"
        thumbnail_url = coverImage.large ?: coverImage.medium
        this.url = url
    }

    private fun FilterList.toSearchFilters(format: String): SearchFilters {
        val sort = filterIsInstance<SortFilter>().firstOrNull()?.toValue()
        val status = filterIsInstance<StatusFilter>().firstOrNull()?.toValue()
        val year = filterIsInstance<YearFilter>().firstOrNull()?.toValue()
        val genres = filterIsInstance<GenreFilter>().firstOrNull()?.toGenres()

        return SearchFilters(
            genres = genres?.ifEmpty { null },
            year = year,
            sort = sort,
            status = status,
            format = format,
        )
    }

    private class SortFilter : Filter.Select<String>("Sort", SORT_OPTIONS.map { it.first }.toTypedArray()) {
        fun toValue(): String? = SORT_OPTIONS.getOrNull(state)?.second
    }

    private class StatusFilter : Filter.Select<String>("Status", STATUS_OPTIONS.map { it.first }.toTypedArray()) {
        fun toValue(): String? = STATUS_OPTIONS.getOrNull(state)?.second
    }

    private class YearFilter : Filter.Select<String>("Year", YEAR_LABELS) {
        fun toValue(): Int? = YEAR_VALUES.getOrNull(state)?.toIntOrNull()
    }

    private class GenreFilter :
        Filter.Group<GenreOption>(
            "Genres",
            GENRE_OPTIONS.map { GenreOption(it) },
        ) {
        fun toGenres(): List<String> = state.filter { it.state }.map { it.name }
    }

    private class GenreOption(name: String) : Filter.CheckBox(name)

    @Serializable
    private data class SearchPayload(
        val query: String,
        val page: Int,
        val filters: SearchFilters = SearchFilters(),
    )

    @Serializable
    private data class SearchFilters(
        val genres: List<String>? = null,
        val year: Int? = null,
        val sort: String? = null,
        val status: String? = null,
        val format: String? = null,
    )

    @Serializable
    private data class AniSearchResponse(val data: AniSearchData)

    @Serializable
    private data class AniSearchData(@SerialName("Page") val page: AniPage)

    @Serializable
    private data class AniPage(
        val pageInfo: PageInfo,
        val media: List<AniMedia>,
    )

    @Serializable
    private data class PageInfo(
        val total: Int,
        val currentPage: Int,
        val lastPage: Int,
        val hasNextPage: Boolean,
    )

    @Serializable
    private data class AniMedia(
        val id: Int,
        val title: AniTitle,
        val description: String? = null,
        val coverImage: AniCoverImage,
        val genres: List<String> = emptyList(),
        val status: String? = null,
    )

    @Serializable
    private data class AniInfoResponse(val data: AniInfo)

    @Serializable
    private data class AniInfo(
        val title: AniTitle,
        val description: String? = null,
        val genres: List<String> = emptyList(),
        val status: String? = null,
        val coverImage: AniCoverImage,
        val author: String? = null,
        val artist: String? = null,
    )

    @Serializable
    private data class AniTitle(
        val romaji: String? = null,
        val english: String? = null,
        val native: String? = null,
    )

    @Serializable
    private data class AniCoverImage(
        val extraLarge: String? = null,
        val large: String? = null,
        val medium: String? = null,
    )

    @Serializable
    private data class VermillionChapterRequest(val id: String, val refresh: Boolean)

    @Serializable
    private data class VermillionSource(
        val providerId: String,
        val chapters: List<VermillionChapter> = emptyList(),
    )

    @Serializable
    private data class VermillionChapter(
        val id: String? = null,
        val title: String? = null,
        val number: Float,
    )

    @Serializable
    private data class VermillionImagesRequest(val id: String, val chapter: String)

    @Serializable
    private data class VermillionImagesResponse(val images: List<VermillionImage> = emptyList())

    @Serializable
    private data class VermillionImage(val index: Int, val url: String)

    private data class ChapterInfo(
        val mangaId: String,
        val chapterNumber: String,
        val language: String,
        val source: ChapterSource,
    )

    private enum class ChapterSource(val id: String) {
        VERMILLION("vermillion"),
        ;

        companion object {
            fun fromId(@Suppress("UNUSED_PARAMETER") id: String?): ChapterSource = VERMILLION
        }
    }

    companion object {
        private const val FORMAT_MANGA = "MANGA"
        private const val DEFAULT_LANGUAGE = "en"
        private val HOST_PREFIX_REGEX = Regex("^s\\d{2}\\..+")
        private val IMAGE_HOST_RANGE = 0..10
        private val RETRY_STATUS_CODES = setOf(404, 503)
        private const val PREFERRED_IMAGE_HOST_INDEX = 3
        private const val IMAGE_CONNECT_TIMEOUT_SEC = 3
        private const val IMAGE_READ_TIMEOUT_SEC = 10
        private const val IMAGE_WRITE_TIMEOUT_SEC = 10

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private const val SORT_POPULAR = "POPULARITY_DESC"
        private const val SORT_LATEST = "START_DATE_DESC"

        private val SORT_OPTIONS = arrayOf(
            "Popular" to SORT_POPULAR,
            "Top Rated" to "SCORE_DESC",
            "Latest" to SORT_LATEST,
            "A-Z" to "TITLE_ROMAJI",
        )

        private val STATUS_OPTIONS = arrayOf(
            "Any" to null,
            "Ongoing" to "RELEASING",
            "Completed" to "FINISHED",
            "Upcoming" to "NOT_YET_RELEASED",
            "Cancelled" to "CANCELLED",
            "On Hiatus" to "HIATUS",
        )

        private val GENRE_OPTIONS = listOf(
            "Action",
            "Adventure",
            "Comedy",
            "Drama",
            "Ecchi",
            "Fantasy",
            "Horror",
            "Romance",
            "Sci-Fi",
            "Slice of Life",
            "Supernatural",
            "Thriller",
            "Mystery",
            "Psychological",
            "Sports",
            "Music",
            "School",
            "Military",
        )

        private val YEAR_VALUES: Array<String> = run {
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            val years = (0..24).map { (currentYear - it).toString() }
            arrayOf("") + years
        }

        private val YEAR_LABELS: Array<String> = run {
            val currentYear = Calendar.getInstance().get(Calendar.YEAR)
            val years = (0..24).map { (currentYear - it).toString() }
            arrayOf("Any") + years
        }
    }
}
