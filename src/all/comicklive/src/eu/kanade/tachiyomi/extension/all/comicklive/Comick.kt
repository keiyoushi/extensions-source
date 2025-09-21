package eu.kanade.tachiyomi.extension.all.comicklive

import android.content.SharedPreferences
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.lib.i18n.Intl
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferencesLazy
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl.Builder
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.min

abstract class Comick(
    override val lang: String,
    private val comickLang: String,
) : ConfigurableSource, HttpSource() {

    override val name = "Comick"

    override var baseUrl: String = "https://comick.live"
        get() {
            val current = field
            if (current.isNotEmpty()) {
                return current
            }
            field = getMirrorPref()
            return field
        }

    private val apiUrl = "$baseUrl/api"

    override val supportsLatest = true

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = true
    }

    private lateinit var searchResponse: List<SearchManga>

    private val intl by lazy {
        Intl(
            language = lang,
            baseLanguage = "en",
            availableLanguages = setOf("en", "pt-BR"),
            classLoader = this::class.java.classLoader!!,
        )
    }

    private val preferences by getPreferencesLazy { newLineIgnoredGroups() }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = MIRROR_PREF_KEY
            title = "Mirror"
            entries = MIRROR_PREF_ENTRIES
            entryValues = MIRROR_PREF_ENTRY_VALUES
            setDefaultValue(MIRROR_PREF_DEFAULT_VALUE)
            summary = "%s"
            setOnPreferenceChangeListener { _, newValue ->
                baseUrl = newValue as String
                true
            }
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = IGNORED_GROUPS_PREF
            title = intl["ignored_groups_title"]
            summary = intl["ignored_groups_summary"]

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit()
                    .putString(IGNORED_GROUPS_PREF, newValue.toString())
                    .commit()
            }
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = PREFERRED_GROUPS_PREF
            title = intl["preferred_groups_title"]
            summary = intl["preferred_groups_summary"]

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit()
                    .putString(PREFERRED_GROUPS_PREF, newValue.toString())
                    .commit()
            }
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = IGNORED_TAGS_PREF
            title = intl["ignored_tags_title"]
            summary = intl["ignored_tags_summary"]
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_ALTERNATIVE_TITLES_PREF
            title = intl["show_alternative_titles_title"]
            summaryOn = intl["show_alternative_titles_on"]
            summaryOff = intl["show_alternative_titles_off"]
            setDefaultValue(SHOW_ALTERNATIVE_TITLES_DEFAULT)

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit()
                    .putBoolean(SHOW_ALTERNATIVE_TITLES_PREF, newValue as Boolean)
                    .commit()
            }
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = INCLUDE_MU_TAGS_PREF
            title = intl["include_tags_title"]
            summaryOn = intl["include_tags_on"]
            summaryOff = intl["include_tags_off"]
            setDefaultValue(INCLUDE_MU_TAGS_DEFAULT)

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit()
                    .putBoolean(INCLUDE_MU_TAGS_PREF, newValue as Boolean)
                    .commit()
            }
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = GROUP_TAGS_PREF
            title = intl["group_tags_title"]
            summaryOn = intl["group_tags_on"]
            summaryOff = intl["group_tags_off"]
            setDefaultValue(GROUP_TAGS_DEFAULT)

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit()
                    .putBoolean(GROUP_TAGS_PREF, newValue as Boolean)
                    .commit()
            }
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = FIRST_COVER_PREF
            title = intl["update_cover_title"]
            summaryOff = intl["update_cover_off"]
            summaryOn = intl["update_cover_on"]
            setDefaultValue(FIRST_COVER_DEFAULT)

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit()
                    .putBoolean(FIRST_COVER_PREF, newValue as Boolean)
                    .commit()
            }
        }.also(screen::addPreference)

        ListPreference(screen.context).apply {
            key = COVER_QUALITY_PREF
            title = intl["cover_quality_title"]
            entries = arrayOf(
                intl["cover_quality_original"],
                intl["cover_quality_compressed"],
                intl["cover_quality_web_default"],
            )
            entryValues = arrayOf(
                "Original",
                "Compressed",
                COVER_QUALITY_DEFAULT,
            )
            setDefaultValue(COVER_QUALITY_DEFAULT)
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit()
                    .putString(COVER_QUALITY_PREF, newValue as String)
                    .commit()
            }
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = LOCAL_TITLE_PREF
            title = intl["local_title_title"]
            summaryOff = intl["local_title_off"]
            summaryOn = intl["local_title_on"]
            setDefaultValue(LOCAL_TITLE_DEFAULT)

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit()
                    .putBoolean(LOCAL_TITLE_PREF, newValue as Boolean)
                    .commit()
            }
        }.also(screen::addPreference)

        ListPreference(screen.context).apply {
            key = SCORE_POSITION_PREF
            title = intl["score_position_title"]
            summary = "%s"
            entries = arrayOf(
                intl["score_position_top"],
                intl["score_position_middle"],
                intl["score_position_bottom"],
                intl["score_position_none"],
            )
            entryValues = arrayOf(SCORE_POSITION_DEFAULT, "middle", "bottom", "none")
            setDefaultValue(SCORE_POSITION_DEFAULT)

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = findIndexOfValue(selected)
                val entry = entryValues[index] as String

                preferences.edit()
                    .putString(SCORE_POSITION_PREF, entry)
                    .commit()
            }
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = CHAPTER_SCORE_FILTERING_PREF
            title = intl["chapter_score_filtering_title"]
            summaryOff = intl["chapter_score_filtering_off"]
            summaryOn = intl["chapter_score_filtering_on"]
            setDefaultValue(CHAPTER_SCORE_FILTERING_DEFAULT)

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit()
                    .putBoolean(CHAPTER_SCORE_FILTERING_PREF, newValue as Boolean)
                    .commit()
            }
        }.also(screen::addPreference)
    }

    private val SharedPreferences.ignoredGroups: Set<String>
        get() = getString(IGNORED_GROUPS_PREF, "")
            ?.lowercase()
            ?.split("\n")
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.sorted()
            .orEmpty()
            .toSet()

    private val SharedPreferences.preferredGroups: Set<String>
        get() = getString(PREFERRED_GROUPS_PREF, "")
            ?.lowercase()
            ?.split("\n")
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            .orEmpty()
            .toSet()

    private val SharedPreferences.ignoredTags: String
        get() = getString(IGNORED_TAGS_PREF, "")
            ?.split("\n")
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            .orEmpty()
            .joinToString(",")

    private val SharedPreferences.showAlternativeTitles: Boolean
        get() = getBoolean(SHOW_ALTERNATIVE_TITLES_PREF, SHOW_ALTERNATIVE_TITLES_DEFAULT)

    private val SharedPreferences.includeMuTags: Boolean
        get() = getBoolean(INCLUDE_MU_TAGS_PREF, INCLUDE_MU_TAGS_DEFAULT)

    private val SharedPreferences.groupTags: Boolean
        get() = getBoolean(GROUP_TAGS_PREF, GROUP_TAGS_DEFAULT)

    private val SharedPreferences.updateCover: Boolean
        get() = getBoolean(FIRST_COVER_PREF, FIRST_COVER_DEFAULT)

    private val coverQuality: CoverQuality
        get() = CoverQuality.valueOf(
            preferences.getString(COVER_QUALITY_PREF, COVER_QUALITY_DEFAULT) ?: COVER_QUALITY_DEFAULT,
        )

    private val SharedPreferences.localTitle: String
        get() = if (getBoolean(
                LOCAL_TITLE_PREF,
                LOCAL_TITLE_DEFAULT,
            )
        ) {
            comickLang.lowercase()
        } else {
            "all"
        }

    private val SharedPreferences.scorePosition: String
        get() = getString(SCORE_POSITION_PREF, SCORE_POSITION_DEFAULT) ?: SCORE_POSITION_DEFAULT

    private val SharedPreferences.chapterScoreFiltering: Boolean
        get() = getBoolean(CHAPTER_SCORE_FILTERING_PREF, CHAPTER_SCORE_FILTERING_DEFAULT)

    override fun headersBuilder() = Headers.Builder().apply {
        add("Referer", "$baseUrl/")
        add("User-Agent", "Tachiyomi ${System.getProperty("http.agent")}")
    }

    override val client = network.cloudflareClient.newBuilder()
        .addNetworkInterceptor(::errorInterceptor)
        .addInterceptor(::imageInterceptor)
        .rateLimit(5, 6, TimeUnit.SECONDS) // == 50req each (60sec / 1min)
        .build()

    private val imageClient = network.cloudflareClient.newBuilder()
        .rateLimit(7, 4, TimeUnit.SECONDS) // == 1.75req/1sec == 14req/8sec == 105req/60sec
        .build()

    private val smallThumbnailClient = network.cloudflareClient.newBuilder()
        .rateLimit(14, 1, TimeUnit.SECONDS)
        .build()

    private fun imageInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url.toString()

        return if ("comick.pictures" in url && "-s." in url) {
            val newRequest = request.newBuilder()
                .addHeader("Referer", "$baseUrl/")
                .build()
            smallThumbnailClient.newCall(newRequest).execute()
        } else if ("comick.pictures" in url) {
            val newRequest = request.newBuilder()
                .addHeader("Referer", "$baseUrl/")
                .build()
            imageClient.newCall(newRequest).execute()
        } else {
            chain.proceed(request)
        }
    }

    private fun errorInterceptor(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        if (
            response.isSuccessful ||
            "application/json" !in response.header("Content-Type").orEmpty()
        ) {
            return response
        }

        val error = try {
            response.parseAs<Error>()
        } catch (_: Exception) {
            null
        }

        error?.run {
            throw Exception("Comick API error $statusCode: $message")
        } ?: throw Exception("HTTP error ${response.code}")
    }

    /** Popular Manga **/
    override fun popularMangaRequest(page: Int): Request {
        return searchMangaRequest(
            page = page,
            query = "",
            filters = FilterList(
                SortFilter("user_follow_count"),
            ),
        )
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<SearchResponse>()
        return MangasPage(
            result.data.map(SearchManga::toSManga),
            hasNextPage = result.currentPage < result.lastPage,
        )
    }

    /** Latest Manga **/
    override fun latestUpdatesRequest(page: Int): Request {
        return searchMangaRequest(
            page = page,
            query = "",
            filters = FilterList(
                SortFilter("uploaded"),
            ),
        )
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    /** Manga Search **/
    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        return if (query.startsWith(SLUG_SEARCH_PREFIX)) {
            // url deep link
            val slugOrHid = query.substringAfter(SLUG_SEARCH_PREFIX)
            val manga = SManga.create().apply { this.url = "/comic/$slugOrHid#" }
            fetchMangaDetails(manga).map {
                MangasPage(listOf(it), false)
            }
        } else if (query.isEmpty()) {
            // regular filtering without text search
            client.newCall(searchMangaRequest(page, query, filters))
                .asObservableSuccess()
                .map(::searchMangaParse)
        } else {
            // text search, no pagination in api
            if (page == 1) {
                client.newCall(querySearchRequest(query))
                    .asObservableSuccess()
                    .map(::querySearchParse)
            } else {
                Observable.just(paginatedSearchPage(page))
            }
        }
    }

    private fun querySearchRequest(query: String): Request {
        val url = "$apiUrl/search?limit=300&page=1&tachiyomi=true"
            .toHttpUrl().newBuilder()
            .addQueryParameter("q", query.trim())
            .build()

        return GET(url, headers)
    }

    private fun querySearchParse(response: Response): MangasPage {
        val result = response.parseAs<SearchResponse>()
        searchResponse = result.data

        return paginatedSearchPage(1)
    }

    private fun paginatedSearchPage(page: Int): MangasPage {
        val end = min(page * LIMIT, searchResponse.size)
        val entries = searchResponse.subList((page - 1) * LIMIT, end)
            .map(SearchManga::toSManga)
        return MangasPage(entries, end < searchResponse.size)
    }

    private fun addTagQueryParameters(builder: Builder, tags: String, parameterName: String) {
        tags.split(",").filter(String::isNotEmpty).forEach {
            builder.addQueryParameter(
                parameterName,
                it.trim().lowercase().replace(SPACE_AND_SLASH_REGEX, "-")
                    .replace("'-", "-and-039-").replace("'", "-and-039-"),
            )
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/search".toHttpUrl().newBuilder().apply {
            filters.forEach { it ->
                when (it) {
                    // is CompletedFilter -> {
                    //     if (it.state) {
                    //         addQueryParameter("completed", "true")
                    //     }
                    // }

                    is GenreFilter -> {
                        it.state.filter { it.isIncluded() }.forEach {
                            addQueryParameter("genres", it.value)
                        }

                        it.state.filter { it.isExcluded() }.forEach {
                            addQueryParameter("excludes", it.value)
                        }
                    }

                    is DemographicFilter -> {
                        it.state.filter { it.state }.forEach {
                            addQueryParameter("demographic", it.value)
                        }
                    }

                    is TypeFilter -> {
                        it.state.filter { it.state }.forEach {
                            addQueryParameter("country", it.value)
                        }
                    }

                    is SortFilter -> {
                        addQueryParameter("order_by", it.getValue())
                        addQueryParameter("order_direction", "desc")
                    }

                    is StatusFilter -> {
                        if (it.state > 0) {
                            addQueryParameter("status", it.getValue())
                        }
                    }

                    // is ContentRatingFilter -> {
                    //     if (it.state > 0) {
                    //         addQueryParameter("content_rating", it.getValue())
                    //     }
                    // }

                    is CreatedAtFilter -> {
                        if (it.state > 0) {
                            addQueryParameter("time", it.getValue())
                        }
                    }

                    is MinimumFilter -> {
                        if (it.state.isNotEmpty()) {
                            addQueryParameter("minimum", it.state)
                        }
                    }

                    is FromYearFilter -> {
                        if (it.state.isNotEmpty()) {
                            addQueryParameter("from", it.state)
                        }
                    }

                    is ToYearFilter -> {
                        if (it.state.isNotEmpty()) {
                            addQueryParameter("to", it.state)
                        }
                    }

                    is TagFilter -> {
                        if (it.state.isNotEmpty()) {
                            addTagQueryParameters(this, it.state, "tags")
                        }
                    }

                    is ExcludedTagFilter -> {
                        if (it.state.isNotEmpty()) {
                            addTagQueryParameters(this, it.state, "excluded-tags")
                        }
                    }

                    else -> {}
                }
            }
            addTagQueryParameters(this, preferences.ignoredTags, "excluded-tags")
            addQueryParameter("tachiyomi", "true")
            addQueryParameter("limit", "$LIMIT")
            addQueryParameter("page", "$page")
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    /** Manga Details **/
    override fun mangaDetailsRequest(manga: SManga): Request {
        // Migration from slug based urls to hid based ones
        if (!manga.url.endsWith("#")) {
            throw Exception("Migrate from Comick to Comick")
        }

        val mangaUrl = manga.url.removeSuffix("#")
        return GET("$baseUrl$mangaUrl", headers)
    }

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        return client.newCall(mangaDetailsRequest(manga))
            .asObservableSuccess()
            .map { response ->
                mangaDetailsParse(response, manga).apply { initialized = true }
            }
    }

    override fun mangaDetailsParse(response: Response): SManga =
        mangaDetailsParse(response, SManga.create())

    private fun mangaDetailsParse(response: Response, manga: SManga): SManga {
        val html = response.body.string()

        // Extract JSON data from comic-data script tag
        val comicDataRegex = """<script type="application/json" id="comic-data">\s*(.+?)\s*</script>""".toRegex(RegexOption.DOT_MATCHES_ALL)
        val comicMatch = comicDataRegex.find(html)

        if (comicMatch != null) {
            try {
                val jsonString = comicMatch.groupValues[1].trim()
                val comicData = json.parseToJsonElement(jsonString)
                return parseComicJsonData(comicData, manga)
            } catch (e: Exception) {
                // Fall back to basic parsing if JSON parsing fails
            }
        }

        // Fallback: Basic HTML parsing
        return parseHtmlMangaDetails(html, manga)
    }

    private fun parseComicJsonData(jsonData: JsonElement, manga: SManga): SManga {
        val jsonObject = jsonData.jsonObject

        return SManga.create().apply {
            url = manga.url

            // Extract title
            title = jsonObject["title"]?.jsonPrimitive?.content ?: "Unknown Title"

            // Extract description with alternative titles if preference is enabled
            description = buildString {
                // Main description
                val desc = jsonObject["desc"]?.jsonPrimitive?.content
                if (!desc.isNullOrEmpty()) {
                    append(desc)
                }

                // Add alternative titles if preference is enabled
                if (preferences.showAlternativeTitles) {
                    val altTitlesArray = jsonObject["md_titles"]?.jsonArray
                    val altTitles = altTitlesArray?.mapNotNull { element ->
                        element.jsonObject["title"]?.jsonPrimitive?.content
                    }?.filter { it != title && it.isNotBlank() }

                    if (!altTitles.isNullOrEmpty()) {
                        if (this.isNotEmpty()) append("\n\n")
                        append("Alternative Titles:\n")
                        append(altTitles.joinToString("\n") { "• $it" })
                    }
                }
            }

            // Extract thumbnail
            thumbnail_url = jsonObject["default_thumbnail"]?.jsonPrimitive?.content

            // Extract status
            val statusCode = try {
                jsonObject["status"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            } catch (e: Exception) {
                0
            }
            status = when (statusCode) {
                1 -> SManga.ONGOING
                2 -> SManga.COMPLETED
                3 -> SManga.CANCELLED
                4 -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }

            // Extract author and artist
            val authorsArray = jsonObject["authors"]?.jsonArray
            val artistsArray = jsonObject["artists"]?.jsonArray

            val authorNames = authorsArray?.mapNotNull { element ->
                element.jsonObject["name"]?.jsonPrimitive?.content
            } ?: emptyList()

            val artistNames = artistsArray?.mapNotNull { element ->
                element.jsonObject["name"]?.jsonPrimitive?.content
            } ?: emptyList()

            author = authorNames.joinToString(", ")
            artist = artistNames.joinToString(", ")

            // Extract genres
            val genresArray = jsonObject["md_comic_md_genres"]?.jsonArray
            val genreNames = genresArray?.mapNotNull { element ->
                element.jsonObject["md_genres"]?.jsonObject?.get("name")?.jsonPrimitive?.content
            } ?: emptyList()

            genre = genreNames.joinToString(", ")

            initialized = true
        }
    }

    private fun parseHtmlMangaDetails(html: String, manga: SManga): SManga {
        return SManga.create().apply {
            url = manga.url

            // Extract title from HTML
            val titleRegex = """<h1[^>]*>(.*?)</h1>""".toRegex(RegexOption.DOT_MATCHES_ALL)
            title = titleRegex.find(html)?.groupValues?.get(1)?.let {
                it.replace(Regex("<[^>]+>"), "").trim()
            } ?: manga.title.ifEmpty { "Unknown Title" }

            // Extract description from meta tags or content
            val descRegex = """<meta[^>]*name=["]?description["]?[^>]*content=["]([^"]*)["]""".toRegex()
            description = descRegex.find(html)?.groupValues?.get(1) ?: "Description not available"

            status = SManga.UNKNOWN
            initialized = true
        }
    }

    override fun getMangaUrl(manga: SManga): String {
        return "$baseUrl${manga.url.removeSuffix("#")}"
    }

    /** Manga Chapter List **/
    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        // Migration from slug based urls to hid based ones
        if (!manga.url.endsWith("#")) {
            throw Exception("Migrate from Comick to Comick")
        }

        val mangaUrl = manga.url.removeSuffix("#")
        val slug = mangaUrl.substringAfterLast("/")

        // Use the standard request-based approach to ensure proper loading state
        val initialRequest = buildChapterListRequest(manga, 1)

        return client.newCall(initialRequest)
            .asObservableSuccess()
            .flatMap { response ->
                Observable.fromCallable {
                    val allChapters = mutableListOf<Chapter>()
                    val firstPageResponse = response.parseAs<ChapterList>()
                    allChapters.addAll(firstPageResponse.data)

                    // Get pagination info to determine if there are more pages
                    val pagination = firstPageResponse.pagination
                    val totalPages = pagination?.lastPage ?: 1

                    // Fetch remaining pages if any
                    if (totalPages > 1) {
                        for (page in 2..totalPages) {
                            val pageRequest = buildChapterListRequest(manga, page)
                            val pageResponse = client.newCall(pageRequest).execute()
                            val pageChapterList = pageResponse.parseAs<ChapterList>()
                            allChapters.addAll(pageChapterList.data)
                        }
                    }

                    // Parse and return the complete chapter list
                    parseChapterList(allChapters, mangaUrl)
                }
            }
    }

    private fun buildChapterListRequest(manga: SManga, page: Int): Request {
        val mangaUrl = manga.url.removeSuffix("#")
        val slug = mangaUrl.substringAfterLast("/")

        val url = "$apiUrl/comics/$slug/chapter-list".toHttpUrl().newBuilder().apply {
            if (comickLang != "all") addQueryParameter("lang", comickLang)
            addQueryParameter("tachiyomi", "true")
            addQueryParameter("page", "$page")
        }.build()

        return GET(url, headers)
    }

    override fun chapterListRequest(manga: SManga): Request {
        // Use the first page request as the default
        return buildChapterListRequest(manga, 1)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapterListResponse = response.parseAs<ChapterList>()
        val mangaUrl = response.request.url.toString()
            .substringBefore("/chapter-list")
            .substringAfter(apiUrl)
            .replace("/comics/", "/comic/")
        return parseChapterList(chapterListResponse.data, mangaUrl)
    }

    private fun parseChapterList(chapters: List<Chapter>, mangaUrl: String): List<SChapter> {
        val preferredGroups = preferences.preferredGroups
        val ignoredGroupsLowercase = preferences.ignoredGroups.map { it.lowercase() }
        val currentTimestamp = System.currentTimeMillis()

        // First, apply the ignored groups filter to remove chapters from blocked groups
        val filteredChapters = chapters
            .filter {
                val publishTime = try {
                    publishedDateFormat.parse(it.publishedAt)!!.time
                } catch (_: ParseException) {
                    0L
                }

                val publishedChapter = publishTime <= currentTimestamp

                val noGroupBlock = it.groups.map { g -> g.lowercase() }
                    .intersect(ignoredGroupsLowercase)
                    .isEmpty()

                publishedChapter && noGroupBlock
            }

        // Now apply the primary filtering logic based on user preferences
        val finalChapters = if (preferredGroups.isEmpty()) {
            // If preferredGroups is empty, fall back to the existing score filter
            filteredChapters.filterOnScore(preferences.chapterScoreFiltering)
        } else {
            // If preferredGroups is not empty, use the list to grab chapters from those groups in order of preference
            val chaptersByNumber = filteredChapters.groupBy { it.chap }
            val preferredFilteredChapters = mutableListOf<Chapter>()

            // Iterate through each chapter number's group of chapters
            chaptersByNumber.forEach { (_, chaptersForNumber) ->
                // Find the chapter from the most preferred group
                val preferredChapter = preferredGroups.firstNotNullOfOrNull { preferredGroup ->
                    chaptersForNumber.find { chapter ->
                        chapter.groups.any { group ->
                            group.lowercase() == preferredGroup.lowercase()
                        }
                    }
                }

                if (preferredChapter != null) {
                    preferredFilteredChapters.add(preferredChapter)
                } else {
                    // If no preferred group chapter was found, fall back to the score filter
                    val fallbackChapter = chaptersForNumber.filterOnScore(preferences.chapterScoreFiltering)
                    preferredFilteredChapters.addAll(fallbackChapter)
                }
            }
            preferredFilteredChapters
        }

        // Finally, map the filtered chapters to the SChapter model
        return finalChapters.map { it.toSChapter(mangaUrl) }
    }

    private fun List<Chapter>.filterOnScore(shouldFilter: Boolean): Collection<Chapter> {
        if (shouldFilter) {
            return groupBy { it.chap }
                .map { (_, chapters) -> chapters.maxBy { it.score } }
        } else {
            return this
        }
    }

    private val publishedDateFormat =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

    override fun getChapterUrl(chapter: SChapter): String {
        return "$baseUrl${chapter.url}"
    }

    /** Chapter Pages **/
    override fun pageListRequest(chapter: SChapter): Request {
        val chapterUrl = chapter.url
        // Extract manga slug and full chapter identifier from URL
        // URL format: /comic/{slug}/{hid}-chapter-{chap}-{lang}
        val mangaSlug = chapterUrl.substringAfterLast("/comic/").substringBefore("/")
        val chapterPart = chapterUrl.substringAfterLast("/")

        // Use the correct API endpoint: /api/comics/{slug}/{full-chapter-id}
        return GET("$apiUrl/comics/$mangaSlug/$chapterPart?tachiyomi=true", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<PageList>()
        val images = result.chapter.images.ifEmpty {
            // cache busting
            val url = response.request.url.newBuilder()
                .addQueryParameter("_", System.currentTimeMillis().toString())
                .build()

            client.newCall(GET(url, headers)).execute()
                .parseAs<PageList>().chapter.images
        }
        return images.mapIndexedNotNull { index, data ->
            if (data.url == null) null else Page(index = index, imageUrl = data.url)
        }
    }

    private inline fun <reified T> Response.parseAs(): T {
        return json.decodeFromString(body.string())
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

    override fun getFilterList() = getFilters()

    private fun SharedPreferences.newLineIgnoredGroups() {
        if (getBoolean(MIGRATED_IGNORED_GROUPS, false)) return

        val ignoredGroups = getString(IGNORED_GROUPS_PREF, "").orEmpty()

        edit()
            .putString(
                IGNORED_GROUPS_PREF,
                ignoredGroups
                    .split(",")
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .joinToString("\n"),
            )
            .putBoolean(MIGRATED_IGNORED_GROUPS, true)
            .apply()
    }

    private fun getMirrorPref(): String {
        return preferences.getString(MIRROR_PREF_KEY, MIRROR_PREF_DEFAULT_VALUE)
            ?: MIRROR_PREF_DEFAULT_VALUE
    }

    companion object {
        const val SLUG_SEARCH_PREFIX = "id:"
        private val SPACE_AND_SLASH_REGEX = Regex("[ /]")
        private const val IGNORED_GROUPS_PREF = "IgnoredGroups"
        private const val PREFERRED_GROUPS_PREF = "PreferredGroups"
        private const val IGNORED_TAGS_PREF = "IgnoredTags"
        private const val SHOW_ALTERNATIVE_TITLES_PREF = "ShowAlternativeTitles"
        const val SHOW_ALTERNATIVE_TITLES_DEFAULT = false
        private const val INCLUDE_MU_TAGS_PREF = "IncludeMangaUpdatesTags"
        const val INCLUDE_MU_TAGS_DEFAULT = false
        private const val GROUP_TAGS_PREF = "GroupTags"
        const val GROUP_TAGS_DEFAULT = false
        private const val MIGRATED_IGNORED_GROUPS = "MigratedIgnoredGroups"
        private const val FIRST_COVER_PREF = "DefaultCover"
        private const val FIRST_COVER_DEFAULT = true
        private const val COVER_QUALITY_PREF = "CoverQuality"
        const val COVER_QUALITY_DEFAULT = "WebDefault"
        private const val SCORE_POSITION_PREF = "ScorePosition"
        const val SCORE_POSITION_DEFAULT = "top"
        private const val LOCAL_TITLE_PREF = "LocalTitle"
        private const val LOCAL_TITLE_DEFAULT = false
        private const val CHAPTER_SCORE_FILTERING_PREF = "ScoreAutoFiltering"
        private const val CHAPTER_SCORE_FILTERING_DEFAULT = false
        private const val LIMIT = 20
        private const val CHAPTERS_LIMIT = 99999
        private const val MIRROR_PREF_KEY = "MIRROR"
        private val MIRROR_PREF_ENTRIES = arrayOf(
            "comick.live",
            "comick.art",
            "comick.so",
        )
        private val MIRROR_PREF_ENTRY_VALUES = MIRROR_PREF_ENTRIES.map { "https://$it" }.toTypedArray()
        private val MIRROR_PREF_DEFAULT_VALUE = MIRROR_PREF_ENTRY_VALUES[0]
    }
}
