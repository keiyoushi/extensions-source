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
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.min

abstract class ComickFun(
    override val lang: String,
    private val comickFunLang: String,
) : ConfigurableSource, HttpSource() {

    override val name = "Comick"

    override val baseUrl = "https://comick.cc"

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

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
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

    init {
        preferences.newLineIgnoredGroups()
    }

    override fun headersBuilder() = Headers.Builder().apply {
        add("Referer", "$baseUrl/")
        add("User-Agent", "Tachiyomi ${System.getProperty("http.agent")}")
    }

    override val client = network.client.newBuilder()
        .addInterceptor(::thumbnailIntercept)
        .rateLimit(3, 1)
        .build()

    /** Popular Manga **/
    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/v1.0/search?sort=follow&limit=$limit&page=$page&tachiyomi=true"
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<List<SearchManga>>()
        return MangasPage(
            result.map(SearchManga::toSManga),
            hasNextPage = result.size >= limit,
        )
    }

    /** Latest Manga **/
    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/v1.0/search?sort=uploaded&limit=$limit&page=$page&tachiyomi=true"
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
        val end = min(page * limit, searchResponse.size)
        val entries = searchResponse.subList((page - 1) * limit, end)
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
                                addQueryParameter("tags", it.trim())
                            }
                        }
                    }

                    else -> {}
                }
            }
            addQueryParameter("tachiyomi", "true")
            addQueryParameter("limit", "$limit")
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
            if (manga.thumbnail_url.toString().endsWith("#1")) {
                return mangaData.toSManga(
                    includeMuTags = preferences.includeMuTags,
                    scorePosition = preferences.scorePosition,
                    covers = listOf(
                        MDcovers(
                            b2key = manga.thumbnail_url?.substringBeforeLast("#")
                                ?.substringAfterLast("/"),
                            vol = "1",
                        ),
                    ),
                )
            }
            val coversUrl =
                "$apiUrl/comic/${mangaData.comic.slug ?: mangaData.comic.hid}/covers?tachiyomi=true"
            val covers = client.newCall(GET(coversUrl)).execute()
                .parseAs<Covers>().md_covers.reversed()
            return mangaData.toSManga(
                includeMuTags = preferences.includeMuTags,
                covers = if (covers.any { it.vol == "1" }) covers.filter { it.vol == "1" } else covers,
            )
        }
        return mangaData.toSManga(includeMuTags = preferences.includeMuTags)
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

        return paginatedChapterListRequest(manga.url.removeSuffix("#"), 1)
    }

    private fun paginatedChapterListRequest(mangaUrl: String, page: Int): Request {
        return GET(
            "$apiUrl$mangaUrl".toHttpUrl().newBuilder().apply {
                addPathSegment("chapters")
                if (comickFunLang != "all") addQueryParameter("lang", comickFunLang)
                addQueryParameter("tachiyomi", "true")
                addQueryParameter("page", "$page")
            }.build(),
            headers,
        )
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapterListResponse = response.parseAs<ChapterList>()

        val mangaUrl = response.request.url.toString()
            .substringBefore("/chapters")
            .substringAfter(apiUrl)

        var resultSize = chapterListResponse.chapters.size
        var page = 2

        while (chapterListResponse.total > resultSize) {
            val newRequest = paginatedChapterListRequest(mangaUrl, page)
            val newResponse = client.newCall(newRequest).execute()
            val newChapterListResponse = newResponse.parseAs<ChapterList>()

            chapterListResponse.chapters += newChapterListResponse.chapters

            resultSize += newChapterListResponse.chapters.size
            page += 1
        }

        return chapterListResponse.chapters
            .filter {
                it.groups.map { g -> g.lowercase() }.intersect(preferences.ignoredGroups).isEmpty()
            }
            .map { it.toSChapter(mangaUrl) }
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
        return result.chapter.images.mapIndexedNotNull { index, data ->
            if (data.url == null) null else Page(index = index, imageUrl = data.url)
        }
    }

    private inline fun <reified T> Response.parseAs(): T {
        return json.decodeFromString(body.string())
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException("Not used")
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

    companion object {
        const val SLUG_SEARCH_PREFIX = "id:"
        private const val IGNORED_GROUPS_PREF = "IgnoredGroups"
        private const val INCLUDE_MU_TAGS_PREF = "IncludeMangaUpdatesTags"
        private const val INCLUDE_MU_TAGS_DEFAULT = false
        private const val MIGRATED_IGNORED_GROUPS = "MigratedIgnoredGroups"
        private const val FIRST_COVER_PREF = "DefaultCover"
        private const val FIRST_COVER_DEFAULT = true
        private const val SCORE_POSITION_PREF = "ScorePosition"
        private const val SCORE_POSITION_DEFAULT = "top"
        private const val limit = 20
        val dateFormat by lazy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ENGLISH).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
        }
        val markdownLinksRegex = "\\[([^]]+)]\\(([^)]+)\\)".toRegex()
        val markdownItalicBoldRegex = "\\*+\\s*([^*]*)\\s*\\*+".toRegex()
        val markdownItalicRegex = "_+\\s*([^_]*)\\s*_+".toRegex()
    }
}
