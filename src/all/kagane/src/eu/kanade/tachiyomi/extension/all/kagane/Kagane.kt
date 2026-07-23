package eu.kanade.tachiyomi.extension.all.kagane

import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okio.IOException

@Source
abstract class Kagane :
    KeiSource(),
    ConfigurableSource {

    private val kaganeLangs: List<String>
        get() = if (lang == "zh") listOf("zh-Hans", "zh-Hant") else listOf(lang)

    private val domain get() = baseUrl.removePrefix("https://")
    private val apiUrl get() = "https://$domain/api/v2"

    private val prefs = getPreferences()

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(::refreshTokenInterceptor)
    }

    private fun refreshTokenInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        if (!url.queryParameterNames.contains("token")) {
            return chain.proceed(request)
        }

        val chapterId = if (url.pathSegments[4] == "datasaver") {
            url.pathSegments[5]
        } else {
            url.pathSegments[4]
        }

        var response = chain.proceed(
            request.newBuilder()
                .url(url.newBuilder().setQueryParameter("token", accessToken).build())
                .build(),
        )

        if (response.code == 401 || response.code == 403 || response.code == 507) {
            response.close()
            val challenge = runBlocking {
                runCatching { getChallengeResponse(chapterId) }
            }.getOrElse { throw IOException("Failed to retrieve token") }

            accessToken = challenge.accessToken
            response = chain.proceed(
                request.newBuilder()
                    .url(url.newBuilder().setQueryParameter("token", accessToken).build())
                    .build(),
            )
        }

        return response
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(
        page,
        "",
        FilterList(
            SortFilter(Filter.Sort.Selection(1, false)),
            ContentRatingFilter(contentRating.toSet()),
            GenresFilter(emptyList(), excludedGenreIds),

        ),
    )

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(
        page,
        "",
        FilterList(
            SortFilter(Filter.Sort.Selection(6, false)),
            ContentRatingFilter(contentRating.toSet()),
            GenresFilter(emptyList(), excludedGenreIds),
        ),
    )

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        var sortParam = ""

        val body = buildJsonObject {
            if (query.isNotBlank()) {
                put("title", query)
            }

            val displayMode = sourceDisplayMode
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
                    is SortFilter -> {
                        sortParam = filter.toUriPart()
                    }
                    else -> {}
                }
            }

            filters.forEach { filter ->
                when (filter) {
                    is GenresFilter -> {
                        filter.addToJsonObject(this, "genres", genresMatchAll)
                    }

                    is TagsSearchFilter -> {
                        val rawInput = filter.state.trim()
                        if (rawInput.isNotBlank()) {
                            val tagEntries = rawInput.split(",").map { it.trim() }.filter { it.isNotEmpty() }

                            val includeIds = mutableListOf<String>()
                            val excludeIds = mutableListOf<String>()

                            tagEntries.forEach { entry ->
                                val isExclude = entry.startsWith("-")
                                val tagName = if (isExclude) entry.removePrefix("-").trim() else entry

                                val tagId = filter.tagData[tagName.lowercase()]
                                if (tagId != null) {
                                    if (isExclude) excludeIds.add(tagId) else includeIds.add(tagId)
                                }
                            }

                            if (includeIds.isNotEmpty() || excludeIds.isNotEmpty()) {
                                putJsonObject("tags") {
                                    if (tagsMatchAll == true) {
                                        put("match_all", true)
                                    }

                                    putJsonArray("values") {
                                        includeIds.forEach { add(it) }
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
        }.toJsonRequestBody()

        val url = "$apiUrl/search/series".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", (page - 1).toString())
            addQueryParameter("size", 35.toString()) // Default items per request
            if (sortParam.isNotEmpty()) addQueryParameter("sort", sortParam)
        }.build()

        val response = client.post(url, body)
        val dto = response.parseAs<SearchDto>()
        val mangas = dto.content.map { it.toSManga(apiUrl, showSource, sources, cleanTitle) }
        return MangasPage(mangas, hasNextPage = dto.hasNextPage())
    }

    // ====================== Manga Details + Chapters ======================

    private suspend fun getMangaById(seriesId: String): DetailsDto {
        val url = "$apiUrl/series/$seriesId"
        return client.get(url).parseAs<DetailsDto>()
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments
        if (segments.size < 2) return null
        val seriesId = segments[1]
        return parseMangaDetails(getMangaById(seriesId)).apply {
            this.url = seriesId
            initialized = true
        }
    }

    private suspend fun parseMangaDetails(details: DetailsDto): SManga {
        val sourceName = details.sourceId?.let { sourceId ->
            sources[sourceId]
        }
        return details.toSManga(apiUrl, sourceName, baseUrl, showEdition, showSource, cleanTitle)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val seriesId = manga.url
        val dto = getMangaById(seriesId)

        val updatedManga = parseMangaDetails(dto).apply {
            if (!dto.trackerId.isNullOrEmpty()) {
                memo = buildJsonObject {
                    put("trackerId", dto.trackerId)
                }
            }
        }
        val updatedChapters = parseChapterList(dto, seriesId)

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    private fun parseChapterList(details: DetailsDto, seriesId: String): List<SChapter> {
        val useSourceChapterNumber = details.format in setOf(
            "Dark Horse Comics",
            "Flame Comics",
            "MangaDex",
            "Square Enix Manga",
        )

        return details.seriesBooks.map { book ->
            book.toSChapter(seriesId, useSourceChapterNumber, chapterTitleMode)
        }.reversed()
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    // =========================== Related Manga ============================
    override val supportsRelatedMangas = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val trackerId = manga.memo["trackerId"]?.string ?: return emptyList()

        val series = client.get("$apiUrl/trackers/$trackerId/series")
            .parseAs<TrackerDto>()
            .bookSeries
        return series
            .map { it.toSManga(apiUrl, showSource, sources, cleanTitle) }
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        if (chapter.url.contains(";")) {
            error("Outdated chapter URL. Please refresh the chapter list")
        }

        val chapterId = "$baseUrl${chapter.url}".toHttpUrl().pathSegments.last()
        val challengeResp = getChallengeResponse(chapterId)

        accessToken = challengeResp.accessToken
        val cacheUrl = challengeResp.cacheUrl

        val pageList = challengeResp.manifest?.pages ?: emptyList()

        return pageList.map { page ->
            val pageUrl = "$cacheUrl/api/v2/books/page".toHttpUrl().newBuilder().apply {
                if (dataSaver) {
                    addPathSegment("datasaver")
                }
                addPathSegment(chapterId)
                addPathSegment("${page.pageUuid}.${page.ext ?: "jxl"}")
                addQueryParameter("token", accessToken)
            }.build().toString()

            Page(page.pageNumber, url = pageUrl, imageUrl = pageUrl)
        }
    }

    private var accessToken: String = ""
    private var integrityToken: String = ""
    private var integrityExp = System.currentTimeMillis()

    private suspend fun getIntegrityToken(): String {
        if (integrityExp < System.currentTimeMillis()) {
            client.get("$baseUrl/").close()

            val res = client.post(
                "$baseUrl/api/integrity",
                "".toJsonRequestBody(),
            ).parseAs<IntegrityDto>()
            integrityToken = res.token
            integrityExp = res.exp * 1000
        }

        return integrityToken
    }

    private suspend fun getChallengeResponse(chapterId: String): ChallengeDto {
        val integrityToken = getIntegrityToken()

        val challengeUrl = "$apiUrl/books/$chapterId".toHttpUrl().newBuilder()
            .addQueryParameter("is_datasaver", dataSaver.toString())
            .build()

        val challengeBody = "{}".toJsonRequestBody()

        val headers = headers.newBuilder().add("x-integrity-token", integrityToken).build()

        val response = client.post(challengeUrl.toString(), headers, challengeBody)

        return response.parseAs<ChallengeDto>()
    }

    // ============================ Preferences =============================

    private val contentRating: List<String>
        get() {
            val maxRating = prefs.getString(CONTENT_RATING, CONTENT_RATING_DEFAULT)
            val index = CONTENT_RATINGS.indexOfFirst { it == maxRating }
            return CONTENT_RATINGS.slice(0..index.coerceAtLeast(0))
        }

    private val excludedGenreIds: Set<String>
        get() = prefs.getStringSet(GENRES_ID_PREF, emptySet()) ?: emptySet()

    private val sourceDisplayMode: String
        get() = prefs.getString(SOURCE_DISPLAY_MODE, SOURCE_DISPLAY_MODE_DEFAULT) ?: SOURCE_DISPLAY_MODE_DEFAULT

    private val cleanTitle: Boolean
        get() = prefs.getBoolean(CLEAN_TITLE, CLEAN_TITLE_DEFAULT)

    private val showEdition: Boolean
        get() = !cleanTitle && prefs.getBoolean(SHOW_EDITION, SHOW_EDITION_DEFAULT)

    private val showSource: Boolean
        get() = !cleanTitle && prefs.getBoolean(SHOW_SOURCE, SHOW_SOURCE_DEFAULT)

    private val dataSaver
        get() = prefs.getBoolean(DATA_SAVER, false)

    private val chapterTitleMode
        get() = prefs.getString(CHAPTER_TITLE_MODE, CHAPTER_TITLE_MODE_DEFAULT)!!

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

        val genreFilter = getFilterList().firstInstanceOrNull<GenresFilter>()
        val genreMap = genreFilter?.state?.associate { it.id to it.name.replaceFirstChar { c -> c.uppercase() } } ?: emptyMap()

        MultiSelectListPreference(screen.context).apply {
            key = GENRES_ID_PREF
            title = "Exclude Genres"
            entries = genreMap.values.toTypedArray()
            entryValues = genreMap.keys.toTypedArray()
            summary = genreMap.keys
                .filter { excludedGenreIds.contains(it) }
                .map { id -> genreMap[id] }
                .joinToString()
            setDefaultValue(emptySet<String>())
            setEnabled(genreMap.isNotEmpty())
            setOnPreferenceChangeListener { _, values ->
                @Suppress("UNCHECKED_CAST")
                val selected = values as Set<String>
                this.summary = selected.mapNotNull { genreMap[it] }.joinToString()
                true
            }
        }.let(screen::addPreference)

        ListPreference(screen.context).apply {
            key = SOURCE_DISPLAY_MODE
            title = "Source Display Selection"
            summary = "%s"
            entries = arrayOf("Show All (Official + Scanlations)", "Official Sources Only")
            entryValues = arrayOf("all", "official")
            setDefaultValue(SOURCE_DISPLAY_MODE_DEFAULT)
        }.let(screen::addPreference)

        val showEdition = SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_EDITION
            title = "Show edition name in title"
            setDefaultValue(SHOW_EDITION_DEFAULT)
            setEnabled(!cleanTitle)
        }.also(screen::addPreference)

        val showSource = SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_SOURCE
            title = "Show source name in title"
            setDefaultValue(SHOW_SOURCE_DEFAULT)
            setEnabled(!cleanTitle)
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = CLEAN_TITLE
            title = "Clean title"
            summary = "Removes extra brackets or parentheses in title (Disables others)"
            setDefaultValue(CLEAN_TITLE_DEFAULT)
            setOnPreferenceChangeListener { _, newValue ->
                val enabled = !(newValue as Boolean)
                showEdition.setEnabled(enabled)
                showSource.setEnabled(enabled)
                true
            }
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

        private const val GENRES_ID_PREF = "pref_genre_id_exclude"

        private const val SOURCE_DISPLAY_MODE = "pref_source_display_mode"
        private const val SOURCE_DISPLAY_MODE_DEFAULT = "all"

        private const val CLEAN_TITLE = "pref_clean_title"
        private const val CLEAN_TITLE_DEFAULT = false

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
            "vol_local",
            "vol_chapter",
        )
        internal val CHAPTER_TITLE_MODE_NAMES = arrayOf(
            "Title only (e.g. 'Manga Title' / 'Ch.5')",
            "Ch.X + title (e.g. 'Ch.5 Manga Title')",
            "Vol.X Ch.Y (e.g. 'Vol.1 Ch.5')",
            "Vol.X Ch.Y + title (e.g. 'Vol.1 Ch.5 Manga Title')",
        )
    }

    // ============================= Filters ==============================

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = coroutineScope {
        val genresDeferred = async {
            client.get("$apiUrl/genres/list")
                .parseAs<List<GenreDto>>()
                .associate { it.id to it.genreName }
        }

        val tagsDeferred = async {
            client.get("$apiUrl/tags/list")
                .parseAs<List<TagDto>>()
                .associate { it.tagName.lowercase() to it.id }
        }
        val sourcesDeferred = async {
            client.post(
                "$apiUrl/sources/list",
                buildJsonObject { put("source_types", null) }.toJsonRequestBody(),
            )
                .parseAs<SourcesDto>().sources
        }

        MetadataDto(genresDeferred.await(), tagsDeferred.await(), sourcesDeferred.await())
            .toJsonElement()
    }

    var sourceCache: Map<String, String>? = null

    val sources: Map<String, String>
        get() {
            if (sourceCache == null) {
                getFilterList()
            }
            return (sourceCache ?: emptyMap()).also { sourceCache = it }
        }

    override fun getFilterList(data: JsonElement?): FilterList {
        val meta = data?.parseAs<MetadataDto>()

        val filters = mutableListOf(
            SortFilter(),
            ContentRatingFilter(contentRating.toSet()),
            FormatFilter(),
            PublicationStatusFilter(),
            Filter.Separator(),
        )

        if (meta != null) {
            sourceCache = meta.sources.associate { it.sourceId to it.title }

            val displayMode = sourceDisplayMode
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
                    GenresFilter(
                        meta.getGenresList(),
                        excludedGenreIds,
                    ),
                    MatchAllTagsFilter(),
                    TagsSearchFilter(meta.tags),
                    SourcesFilter(sourceFilters),
                ),
            )
        }

        return FilterList(filters)
    }
}
