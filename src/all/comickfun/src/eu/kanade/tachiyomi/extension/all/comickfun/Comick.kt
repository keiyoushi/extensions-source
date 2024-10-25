package eu.kanade.tachiyomi.extension.all.comickfun

import android.app.Application
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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
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

    override val baseUrl = "https://comick.io"

    private val apiUrl = "https://api.comick.fun"

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

    private val preferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
            .newLineIgnoredGroups()
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
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

    private val SharedPreferences.includeMuTags: Boolean
        get() = getBoolean(INCLUDE_MU_TAGS_PREF, INCLUDE_MU_TAGS_DEFAULT)

    private val SharedPreferences.updateCover: Boolean
        get() = getBoolean(FIRST_COVER_PREF, FIRST_COVER_DEFAULT)

    private val SharedPreferences.scorePosition: String
        get() = getString(SCORE_POSITION_PREF, SCORE_POSITION_DEFAULT) ?: SCORE_POSITION_DEFAULT

    override fun headersBuilder() = Headers.Builder().apply {
        add("Referer", "$baseUrl/")
        add("User-Agent", "Tachiyomi ${System.getProperty("http.agent")}")
    }

    override val client = network.client.newBuilder()
        .addNetworkInterceptor(::errorInterceptor)
        .rateLimit(3, 1, TimeUnit.SECONDS)
        .build()

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
            throw Exception("$name error $statusCode: $message")
        } ?: throw Exception("HTTP error ${response.code}")
    }

    /** Popular Manga **/
    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/v1.0/search?sort=follow&limit=$LIMIT&page=$page&tachiyomi=true"
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<List<SearchManga>>()
        return MangasPage(
            result.map(SearchManga::toSManga),
            hasNextPage = result.size >= LIMIT,
        )
    }

    /** Latest Manga **/
    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/v1.0/search?sort=uploaded&limit=$LIMIT&page=$page&tachiyomi=true"
        return GET(url, headers)
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
        val url = "$apiUrl/v1.0/search?limit=300&page=1&tachiyomi=true"
            .toHttpUrl().newBuilder()
            .addQueryParameter("q", query.trim())
            .build()

        return GET(url, headers)
    }

    private fun querySearchParse(response: Response): MangasPage {
        searchResponse = response.parseAs()

        return paginatedSearchPage(1)
    }

    private fun paginatedSearchPage(page: Int): MangasPage {
        val end = min(page * LIMIT, searchResponse.size)
        val entries = searchResponse.subList((page - 1) * LIMIT, end)
            .map(SearchManga::toSManga)
        return MangasPage(entries, end < searchResponse.size)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/v1.0/search".toHttpUrl().newBuilder().apply {
            filters.forEach { it ->
                when (it) {
                    is CompletedFilter -> {
                        if (it.state) {
                            addQueryParameter("completed", "true")
                        }
                    }

                    is GenreFilter -> {
                        it.state.filter { it.isIncluded() }.forEach {
                            addQueryParameter("genres", it.value)
                        }

                        it.state.filter { it.isExcluded() }.forEach {
                            addQueryParameter("excludes", it.value)
                        }
                    }

                    is DemographicFilter -> {
                        it.state.filter { it.isIncluded() }.forEach {
                            addQueryParameter("demographic", it.value)
                        }
                    }

                    is TypeFilter -> {
                        it.state.filter { it.state }.forEach {
                            addQueryParameter("country", it.value)
                        }
                    }

                    is SortFilter -> {
                        addQueryParameter("sort", it.getValue())
                    }

                    is StatusFilter -> {
                        if (it.state > 0) {
                            addQueryParameter("status", it.getValue())
                        }
                    }

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
                            it.state.split(",").forEach {
                                addQueryParameter(
                                    "tags",
                                    it.trim().lowercase().replace(SPACE_AND_SLASH_REGEX, "-")
                                        .replace("'-", "-and-039-").replace("'", "-and-039-"),
                                )
                            }
                        }
                    }

                    else -> {}
                }
            }
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
        return GET("$apiUrl$mangaUrl?tachiyomi=true", headers)
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
        val mangaData = response.parseAs<Manga>()
        if (!preferences.updateCover && manga.thumbnail_url != mangaData.comic.cover) {
            val coversUrl =
                "$apiUrl/comic/${mangaData.comic.slug ?: mangaData.comic.hid}/covers?tachiyomi=true"
            val covers = client.newCall(GET(coversUrl)).execute()
                .parseAs<Covers>().mdCovers.reversed().toMutableList()
            if (covers.any { it.vol == "1" }) covers.retainAll { it.vol == "1" }
            if (
                covers.any { it.locale == comickLang.split('-').first() }
            ) {
                covers.retainAll { it.locale == comickLang.split('-').first() }
            }
            return mangaData.toSManga(
                includeMuTags = preferences.includeMuTags,
                scorePosition = preferences.scorePosition,
                covers = covers,
            )
        }
        return mangaData.toSManga(
            includeMuTags = preferences.includeMuTags,
            scorePosition = preferences.scorePosition,
        )
    }

    override fun getMangaUrl(manga: SManga): String {
        return "$baseUrl${manga.url.removeSuffix("#")}"
    }

    /** Manga Chapter List **/
    override fun chapterListRequest(manga: SManga): Request {
        // Migration from slug based urls to hid based ones
        if (!manga.url.endsWith("#")) {
            throw Exception("Migrate from Comick to Comick")
        }

        val mangaUrl = manga.url.removeSuffix("#")
        val url = "$apiUrl$mangaUrl".toHttpUrl().newBuilder().apply {
            addPathSegment("chapters")
            if (comickLang != "all") addQueryParameter("lang", comickLang)
            addQueryParameter("tachiyomi", "true")
            addQueryParameter("limit", "$CHAPTERS_LIMIT")
        }.build()

        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapterListResponse = response.parseAs<ChapterList>()

        val mangaUrl = response.request.url.toString()
            .substringBefore("/chapters")
            .substringAfter(apiUrl)

        val currentTimestamp = System.currentTimeMillis()

        return chapterListResponse.chapters
            .filter {
                val publishTime = try {
                    publishedDateFormat.parse(it.publishedAt)!!.time
                } catch (_: ParseException) {
                    0L
                }

                val publishedChapter = publishTime <= currentTimestamp

                val noGroupBlock = it.groups.map { g -> g.lowercase() }
                    .intersect(preferences.ignoredGroups)
                    .isEmpty()

                publishedChapter && noGroupBlock
            }
            .map { it.toSChapter(mangaUrl) }
    }

    private val publishedDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override fun getChapterUrl(chapter: SChapter): String {
        return "$baseUrl${chapter.url}"
    }

    /** Chapter Pages **/
    override fun pageListRequest(chapter: SChapter): Request {
        val chapterHid = chapter.url.substringAfterLast("/").substringBefore("-")
        return GET("$apiUrl/chapter/$chapterHid?tachiyomi=true", headers)
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

    private fun SharedPreferences.newLineIgnoredGroups(): SharedPreferences {
        if (getBoolean(MIGRATED_IGNORED_GROUPS, false)) return this
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

        return this
    }

    companion object {
        const val SLUG_SEARCH_PREFIX = "id:"
        private val SPACE_AND_SLASH_REGEX = Regex("[ /]")
        private const val IGNORED_GROUPS_PREF = "IgnoredGroups"
        private const val INCLUDE_MU_TAGS_PREF = "IncludeMangaUpdatesTags"
        const val INCLUDE_MU_TAGS_DEFAULT = false
        private const val MIGRATED_IGNORED_GROUPS = "MigratedIgnoredGroups"
        private const val FIRST_COVER_PREF = "DefaultCover"
        private const val FIRST_COVER_DEFAULT = true
        private const val SCORE_POSITION_PREF = "ScorePosition"
        const val SCORE_POSITION_DEFAULT = "top"
        private const val LIMIT = 20
        private const val CHAPTERS_LIMIT = 99999
    }
}
