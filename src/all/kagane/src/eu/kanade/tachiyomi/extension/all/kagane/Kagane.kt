package eu.kanade.tachiyomi.extension.all.kagane

import android.content.SharedPreferences
import android.util.Log
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.brotli.BrotliInterceptor
import okio.IOException
import rx.Observable

@Source
abstract class Kagane :
    HttpSource(),
    ConfigurableSource {

    private val kaganeLangs: List<String>
        get() = if (lang == "zh") listOf("zh-Hans", "zh-Hant") else listOf(lang)

    private val domain get() = baseUrl.removePrefix("https://")
    private val apiUrl get() = "https://yuzuki.$domain"

    override val supportsLatest = true

    private val preferences by getPreferencesLazy()

    override val client = network.client.newBuilder()
        .addInterceptor(::refreshTokenInterceptor)
        // fix disk cache
        .apply {
            val index = networkInterceptors().indexOfFirst { it is BrotliInterceptor }
            if (index >= 0) interceptors().add(networkInterceptors().removeAt(index))
        }
        .build()

    private fun refreshTokenInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        if (!url.queryParameterNames.contains("token")) {
            return chain.proceed(request)
        }

        val chapterId = url.pathSegments[4]

        var response = chain.proceed(
            request.newBuilder()
                .url(url.newBuilder().setQueryParameter("token", accessToken).build())
                .build(),
        )

        if (response.code == 401 || response.code == 403 || response.code == 507) {
            response.close()
            val challenge = try {
                getChallengeResponse(chapterId)
            } catch (_: Exception) {
                throw IOException("Failed to retrieve token")
            }
            accessToken = challenge.accessToken
            cacheUrl = challenge.cacheUrl
            response = chain.proceed(
                request.newBuilder()
                    .url(url.newBuilder().setQueryParameter("token", accessToken).build())
                    .build(),
            )
        }

        return response
    }

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int) = searchMangaRequest(
        page,
        "",
        FilterList(
            SortFilter(Filter.Sort.Selection(1, false)),
            ContentRatingFilter(
                preferences.contentRating.toSet(),
            ),
            GenresFilter(emptyList()),
        ),
    )

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int) = searchMangaRequest(
        page,
        "",
        FilterList(
            SortFilter(Filter.Sort.Selection(6, false)),
            ContentRatingFilter(
                preferences.contentRating.toSet(),
            ),
            GenresFilter(emptyList()),
        ),
    )

    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val body = buildJsonObject {
            if (query.isNotBlank()) {
                put("title", query)
            }

            val displayMode = preferences.sourceDisplayMode
            val sourceTypes = if (displayMode == "official") {
                listOf("Official")
            } else {
                listOf("Official", "Unofficial", "Mixed")
            }
            putJsonArray("source_type") {
                sourceTypes.forEach { add(it) }
            }

            var genresMatchAll: Boolean? = null
            var tagsMatchAll: Boolean? = null

            filters.forEach { filter ->
                when (filter) {
                    is MatchAllGenresFilter -> {
                        genresMatchAll = if (filter.state) true else null
                    }

                    is MatchAllTagsFilter -> {
                        tagsMatchAll = if (filter.state) true else null
                    }

                    else -> {}
                }
            }

            filters.forEach { filter ->
                when (filter) {
                    is GenresFilter -> {
                        val excludedGenreIds = preferences.excludedGenres.mapNotNull { genreName ->
                            metadata?.genres?.entries?.firstOrNull {
                                it.value.equals(genreName, ignoreCase = true)
                            }?.key
                        }
                        filter.addToJsonObject(this, "genres", excludedGenreIds, genresMatchAll)
                    }

                    is TagsSearchFilter -> {
                        val rawInput = filter.state.trim()
                        if (rawInput.isNotBlank()) {
                            val metadata = metadata
                            if (metadata != null) {
                                val tagEntries = rawInput.split(",").map { it.trim() }.filter { it.isNotEmpty() }

                                val includeIds = mutableListOf<String>()
                                val excludeIds = mutableListOf<String>()

                                tagEntries.forEach { entry ->
                                    val isExclude = entry.startsWith("-")
                                    val tagName = if (isExclude) entry.removePrefix("-").trim() else entry
                                    val tagId = metadata.tags.entries.firstOrNull {
                                        it.value.equals(tagName, ignoreCase = true)
                                    }?.key
                                    if (tagId != null) {
                                        if (isExclude) excludeIds.add(tagId) else includeIds.add(tagId)
                                    }
                                }

                                if (includeIds.isNotEmpty() || excludeIds.isNotEmpty()) {
                                    putJsonObject("tags") {
                                        if (tagsMatchAll == true) {
                                            put("match_all", true)
                                        }
                                        if (includeIds.isNotEmpty()) {
                                            putJsonArray("values") {
                                                includeIds.forEach { add(it) }
                                            }
                                        }
                                        if (excludeIds.isNotEmpty()) {
                                            putJsonArray("exclude") {
                                                excludeIds.forEach { add(it) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    is SourcesFilter -> {
                        filter.addToJsonObject(this)
                    }

                    is JsonFilter -> {
                        filter.addToJsonObject(this)
                    }

                    else -> {}
                }
            }

            // Add languages specific to this source instance
            putJsonArray("content_lang") {
                kaganeLangs.forEach { add(it) }
            }
        }
            .toJsonString()
            .toRequestBody("application/json".toMediaType())

        val url = "$apiUrl/api/v2/search/series".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", (page - 1).toString())
            addQueryParameter("size", 35.toString()) // Default items per request
            filters.forEach { filter ->
                when (filter) {
                    is SortFilter -> {
                        val sortParam = filter.toUriPart()
                        when {
                            sortParam.isNotEmpty() -> addQueryParameter("sort", sortParam)
                        }
                    }

                    else -> {}
                }
            }
        }

        return POST(url.toString(), headers, body)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<SearchDto>()
        val sources = getSourcesMap()
        val mangas = dto.content.map { it.toSManga(apiUrl, preferences.showSource, sources) }
        return MangasPage(mangas, hasNextPage = dto.hasNextPage())
    }

    private fun getSourcesMap(): Map<String, String> = if (!preferences.showSource) {
        emptyMap()
    } else {
        metadata?.sources?.associate { it.sourceId to it.title }
            ?: try {
                getSourcesResponse().use { response ->
                    response.takeIf { it.isSuccessful }
                        ?.parseAs<SourcesDto>()?.sources
                }
                    ?.associate { it.sourceId to it.title }
                    ?: emptyMap()
            } catch (e: Exception) {
                Log.w(name, "Failed to load sources", e)
                emptyMap()
            }
    }

    private fun getSourcesResponse(): Response = metadataClient.newCall(
        POST(
            "$apiUrl/api/v2/sources/list",
            apiHeaders,
            buildJsonObject { put("source_types", null) }.toJsonString()
                .toRequestBody("application/json".toMediaType()),
        ),
    ).execute()

    // =========================== Manga Details ============================

    override fun relatedMangaListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun relatedMangaListParse(response: Response): List<SManga> {
        val dto = response.parseAs<DetailsDto>()
        val trackerId = dto.trackerId?.takeIf(String::isNotBlank) ?: return emptyList()
        val trackerRequest = GET("$apiUrl/api/v2/trackers/$trackerId/series", apiHeaders)
        val series = client.newCall(trackerRequest).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            resp.parseAs<TrackerDto>().series
        }
        val sources = getSourcesMap()
        return series
            .map { it.toSManga(apiUrl, preferences.showSource, sources) }
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val dto = response.parseAs<DetailsDto>()
        val sourceName = dto.sourceId?.let { sourceId ->
            metadata?.sources?.firstOrNull { it.sourceId == sourceId }?.title
                ?: try {
                    getSourcesResponse().use { response ->
                        response.takeIf { it.isSuccessful }
                            ?.parseAs<SourcesDto>()?.sources
                    }
                        ?.firstOrNull { it.sourceId == sourceId }?.title
                } catch (e: Exception) {
                    Log.w(name, "Failed to load sources", e)
                    null
                }
        }
        return dto.toSManga(apiUrl, sourceName, baseUrl, preferences.showEdition, preferences.showSource)
    }

    override fun mangaDetailsRequest(manga: SManga): Request = mangaDetailsRequest(manga.url)

    private fun mangaDetailsRequest(seriesId: String): Request = GET("$apiUrl/api/v2/series/$seriesId", apiHeaders)

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}"

    // ============================== Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val seriesId = response.request.url.toString()
            .substringAfterLast("/")

        val dto = response.parseAs<DetailsDto>()

        val useSourceChapterNumber = dto.format in setOf(
            "Dark Horse Comics",
            "Flame Comics",
            "MangaDex",
            "Square Enix Manga",
        )

        return dto.seriesBooks.map { book ->
            book.toSChapter(seriesId, useSourceChapterNumber, preferences.chapterTitleMode)
        }.reversed()
    }

    override fun chapterListRequest(manga: SManga): Request = GET("$apiUrl/api/v2/series/${manga.url}", apiHeaders)

    // =============================== Pages ================================

    private val apiHeaders = headers.newBuilder().apply {
        add("Origin", baseUrl)
        add("Referer", "$baseUrl/")
    }.build()

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        if (chapter.url.contains(";")) {
            throw Exception("Outdated chapter URL. Please refresh the chapter list")
        }

        val chapterId = "$baseUrl${chapter.url}".toHttpUrl().pathSegments.last()
        val challengeResp = getChallengeResponse(chapterId)

        accessToken = challengeResp.accessToken
        cacheUrl = challengeResp.cacheUrl

        val pageList = challengeResp.manifest?.pages ?: emptyList()

        val pages = pageList.map { page ->
            val pageUrl = "$cacheUrl/api/v2/books/page".toHttpUrl().newBuilder().apply {
                addPathSegment(chapterId)
                addPathSegment("${page.pageUuid}.${page.ext ?: "jxl"}")
                addQueryParameter("token", accessToken)
                addQueryParameter("is_datasaver", preferences.dataSaver.toString())
            }.build().toString()

            Page(page.pageNumber, url = pageUrl, imageUrl = pageUrl)
        }
        return Observable.just(pages)
    }

    override fun imageUrlRequest(page: Page): Request = GET(page.imageUrl!!, apiHeaders)

    private var cacheUrl = "https://akari.$domain"
    private var accessToken: String = ""
    private var integrityToken: String = ""
    private var integrityExp = System.currentTimeMillis()

    private fun getIntegrityToken(): String {
        if (integrityExp < System.currentTimeMillis()) {
            client.newCall(GET("$baseUrl/", headers)).execute().close()

            val res = client.newCall(
                POST(
                    "$baseUrl/api/integrity",
                    headers,
                    body = "".toRequestBody("application/json".toMediaType()),
                ),
            ).execute().parseAs<IntegrityDto>()
            integrityToken = res.token
            integrityExp = res.exp * 1000
        }

        return integrityToken
    }

    private fun getChallengeResponse(chapterId: String): ChallengeDto {
        val integrityToken = getIntegrityToken()

        val challengeUrl =
            "$apiUrl/api/v2/books/$chapterId".toHttpUrl().newBuilder()
                .addQueryParameter("is_datasaver", preferences.dataSaver.toString())
                .build()

        val challengeBody = "{}".toRequestBody("application/json".toMediaType())

        val headers = apiHeaders.newBuilder().add("x-integrity-token", integrityToken).build()

        val response = client.newCall(POST(challengeUrl.toString(), headers, challengeBody)).execute()
        if (!response.isSuccessful) {
            response.close()
            throw Exception("Failed to get challenge. HTTP error ${response.code}")
        }

        return response.parseAs<ChallengeDto>()
    }

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================ Preferences =============================

    private val SharedPreferences.contentRating: List<String>
        get() {
            val maxRating = this.getString(CONTENT_RATING, CONTENT_RATING_DEFAULT)
            val index = CONTENT_RATINGS.indexOfFirst { it == maxRating }
            return CONTENT_RATINGS.slice(0..index.coerceAtLeast(0))
        }

    private val SharedPreferences.excludedGenres: Set<String>
        get() = this.getStringSet(GENRES_PREF, emptySet()) ?: emptySet()

    private val SharedPreferences.sourceDisplayMode: String
        get() = this.getString(SOURCE_DISPLAY_MODE, SOURCE_DISPLAY_MODE_DEFAULT) ?: SOURCE_DISPLAY_MODE_DEFAULT

    private val SharedPreferences.showEdition: Boolean
        get() = this.getBoolean(SHOW_EDITION, SHOW_EDITION_DEFAULT)

    private val SharedPreferences.showSource: Boolean
        get() = this.getBoolean(SHOW_SOURCE, SHOW_SOURCE_DEFAULT)

    private val SharedPreferences.dataSaver
        get() = this.getBoolean(DATA_SAVER, false)

    private val SharedPreferences.chapterTitleMode
        get() = this.getString(CHAPTER_TITLE_MODE, CHAPTER_TITLE_MODE_DEFAULT)!!

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = CONTENT_RATING
            title = "Content Rating"
            entries =
                CONTENT_RATINGS.map { it.replaceFirstChar { c -> c.uppercase() } }.toTypedArray()
            entryValues = CONTENT_RATINGS
            summary = "%s"
            setDefaultValue(CONTENT_RATING_DEFAULT)
        }.let(screen::addPreference)

        MultiSelectListPreference(screen.context).apply {
            key = GENRES_PREF
            title = "Exclude Genres"
            entries = GenresList.map { it.replaceFirstChar { c -> c.uppercase() } }.toTypedArray()
            entryValues = GenresList
            summary =
                preferences.excludedGenres.joinToString { it.replaceFirstChar { c -> c.uppercase() } }
            setDefaultValue(emptySet<String>())

            setOnPreferenceChangeListener { _, values ->
                @Suppress("UNCHECKED_CAST")
                val selected = values as Set<String>
                this.summary = selected.joinToString { it.replaceFirstChar { c -> c.uppercase() } }
                true
            }
        }.let(screen::addPreference)

        ListPreference(screen.context).apply {
            key = SOURCE_DISPLAY_MODE
            title = "Source Display Selection"
            summary = "%s"
            entries = arrayOf("Official Sources Only", "Show All (Official + Scanlations)")
            entryValues = arrayOf("official", "all")
            setDefaultValue(SOURCE_DISPLAY_MODE_DEFAULT)
        }.let(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_EDITION
            title = "Show edition name in title"
            setDefaultValue(SHOW_EDITION_DEFAULT)
        }.let(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_SOURCE
            title = "Show source name in title"
            setDefaultValue(SHOW_SOURCE_DEFAULT)
        }.let(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = DATA_SAVER
            title = "Data saver"
            setDefaultValue(false)
        }.let(screen::addPreference)

        ListPreference(screen.context).apply {
            key = CHAPTER_TITLE_MODE
            title = "Chapter title format"
            entries = CHAPTER_TITLE_MODE_NAMES
            entryValues = CHAPTER_TITLE_MODES
            summary = "How the chapter title should be displayed"
            setDefaultValue(CHAPTER_TITLE_MODE_DEFAULT)
        }.let(screen::addPreference)
    }

    // ============================= Utilities ==============================

    companion object {
        private const val CONTENT_RATING = "pref_content_rating"
        private const val CONTENT_RATING_DEFAULT = "pornographic"
        internal val CONTENT_RATINGS = arrayOf(
            "safe",
            "suggestive",
            "erotica",
            "pornographic",
        )

        private const val GENRES_PREF = "pref_genres_exclude"

        private const val SOURCE_DISPLAY_MODE = "pref_source_display_mode"
        private const val SOURCE_DISPLAY_MODE_DEFAULT = "all"

        private const val SHOW_SOURCE = "pref_show_source"
        private const val SHOW_SOURCE_DEFAULT = false

        private const val SHOW_EDITION = "pref_show_edition"
        private const val SHOW_EDITION_DEFAULT = false

        private const val DATA_SAVER = "data_saver_default"

        private const val CHAPTER_TITLE_MODE = "chapter_title_mode"
        private const val CHAPTER_TITLE_MODE_DEFAULT = "optional"
        internal val CHAPTER_TITLE_MODES = arrayOf(
            "optional",
            "always",
            "vol_chapter",
        )
        internal val CHAPTER_TITLE_MODE_NAMES = arrayOf(
            "Title only (e.g. 'Manga Title' / 'Ch.5')",
            "Ch.X + title (e.g. 'Ch.5 Manga Title')",
            "Vol.X Ch.Y + title (e.g. 'Vol.1 Ch.5 Manga Title')",
        )
    }

    // ============================= Filters ==============================

    private var metadata: MetadataDto? = null
    private val metadataClient = client.newBuilder()
        .addNetworkInterceptor { chain ->
            chain.proceed(chain.request()).newBuilder()
                .header("Cache-Control", "max-age=${24 * 60 * 60}")
                .removeHeader("Pragma")
                .removeHeader("Expires")
                .build()
        }.build()

    override fun getFilterList(): FilterList {
        val filters: MutableList<Filter<*>> = mutableListOf(
            SortFilter(),
            ContentRatingFilter(
                preferences.contentRating.toSet(),
            ),
            FormatFilter(),
            PublicationStatusFilter(),
            Filter.Separator(),
        )

        fetchMetadata()

        val meta = metadata

        if (meta != null) {
            val displayMode = preferences.sourceDisplayMode

            val validSources = meta.sources.filter { source ->
                when (displayMode) {
                    "official" -> source.sourceType.equals("Official", ignoreCase = true)
                    else -> true
                }
            }
            val sourceFilters = validSources
                .map { FilterData(it.sourceId, it.title) }
                .sortedBy { it.name }

            filters.addAll(
                listOf(
                    MatchAllGenresFilter(),
                    GenresFilter(meta.getGenresList()),
                    MatchAllTagsFilter(),
                    TagsSearchFilter(),
                    SourcesFilter(sourceFilters),
                ),
            )
        } else {
            filters.add(0, Filter.Header("Press 'Reset' to load more filters"))
            filters.add(1, Filter.Separator())
        }

        return FilterList(filters)
    }

    private fun fetchMetadata() {
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                val genres = metadataClient.newCall(
                    GET("$apiUrl/api/v2/genres/list", apiHeaders),
                ).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    resp.parseAs<List<GenreDto>>().associate { it.id to it.genreName }
                }
                val tags = metadataClient.newCall(
                    GET("$apiUrl/api/v2/tags/list", apiHeaders),
                ).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    resp.parseAs<List<TagDto>>().associate { it.id to it.tagName }
                }
                val sources = getSourcesResponse().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    resp.parseAs<SourcesDto>().sources
                }

                if (genres != null && tags != null && sources != null) {
                    metadata = MetadataDto(genres, tags, sources)
                    Log.d(name, "Metadata fetched and updated")
                } else {
                    Log.e(name, "Failed to fetch metadata: One or more requests failed")
                }
            } catch (e: Exception) {
                Log.e(name, "Failed to fetch metadata", e)
            }
        }
    }
}
