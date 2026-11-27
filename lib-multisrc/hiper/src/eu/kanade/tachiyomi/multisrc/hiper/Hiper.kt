package eu.kanade.tachiyomi.multisrc.hiper

import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.lib.i18n.Intl
import keiyoushi.network.Fetch
import keiyoushi.network.fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.get
import keiyoushi.utils.getLongOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.toJsonElement
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

abstract class Hiper :
    KeiSource(),
    ConfigurableSource {

    protected val preferences by getPreferencesLazy()

    protected open val mangaPath: String = "manga"

    private var wvHeaders: Headers? = null

    private val wvMutex = Mutex()

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor { chain ->
        var request = chain.request()

        wvHeaders?.let {
            request = request.newBuilder()
                .headers(it)
                .build()
        }

        val response = chain.proceed(request)

        // Fetch baseUrl with accept headers which then populates a cookie
        if (response.code == 401) {
            response.close()
            val acceptHeaders = headers.newBuilder()
                .set("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build()
            network.client.newCall(GET(baseUrl, acceptHeaders).newBuilder().fetch(Fetch.DOCUMENT).build()).execute().close()
            chain.proceed(request)
        } else {
            response
        }
    }.rateLimit(3)

    protected val intl = Intl(
        language = lang,
        baseLanguage = "en",
        availableLanguages = setOf("en", "pt-BR"),
        classLoader = this::class.java.classLoader!!,
    )

    private suspend fun trpc(procedure: String, input: JsonObject? = null, ensureSuccess: Boolean = true): Response {
        val url = "$baseUrl/api/trpc/$procedure".toHttpUrl().newBuilder().apply {
            if (input != null) addQueryParameter("input", buildJsonObject { put("json", input) }.toString())
        }.build()
        return client.get(url, Fetch.XHR, ensureSuccess = ensureSuccess)
    }

    private inline fun <reified T> Response.parseTrpc(): T? = parseAs<JsonElement>()["result"]["data"]["json"]?.parseAs<T>()

    // ============================ Popular ====================================

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(OrderByFilter("", arrayOf("" to "popular"))))

    // ============================ Latest ====================================

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", FilterList(OrderByFilter("", arrayOf("" to "recent"))))

    // ============================ Search ====================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val input = buildJsonObject {
            put("q", query)
            filters.firstInstanceOrNull<OrderByFilter>()?.let { put("sort", it.selected()) }
            putJsonObject("filters") {
                filters.firstInstanceOrNull<GenresFilter>()?.checked?.takeIf { it.isNotEmpty() }
                    ?.let { genres -> putJsonArray("genres") { genres.forEach { add(it) } } }
                filters.filterIsInstance<SelectFilter>().forEach { filter -> filter.selected()?.let { put(filter.key, it) } }
            }
            put("limit", LIMIT)
            put("offset", (page - 1) * LIMIT)
            put("maxRating", preferences.getString(MAX_RATING_PREF, MAX_RATING_DEFAULT))
        }
        return parseSearchMangaList(trpc("search.query", input))
    }

    protected open fun parseSearchMangaList(response: Response): MangasPage {
        val dto = response.parseTrpc<WrapperContent>() ?: return MangasPage(emptyList(), false)
        return MangasPage(dto.hits.map { it.toSManga(mangaPath) }, dto.hits.isNotEmpty())
    }

    // ============================ Details ===================================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    protected open suspend fun getMangaDetails(manga: SManga): SManga? {
        val slug = manga.url.getSlug()
        return getMangaBySlug(slug)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val slug = url.getSlug()
        return getMangaBySlug(slug)
    }

    protected open suspend fun getMangaBySlug(slug: String): SManga? = parseMangaDetails(trpc("series.bySlugWithGenres", buildJsonObject { put("slug", slug) }))

    protected open fun parseMangaDetails(response: Response): SManga = response.parseTrpc<MangaDto>()!!.toSManga(mangaPath)

    // ============================ Chapters ==================================

    override fun getChapterUrl(chapter: SChapter): String {
        val slug = chapter.url.getSlug()
        return "$baseUrl/$mangaPath/$slug/${chapter.getNumber()}".removeSuffix(".0")
    }

    protected open suspend fun getChapterList(manga: SManga): List<SChapter>? {
        val mangaId = manga.memo.getLongOrNull("mangaId")
            ?: manga.url.substringAfterLast("#").toLongOrNull() // Fallback for old manga URLs
            ?: return null

        val chaptersDTO = trpc("series.chapters", buildJsonObject { put("seriesId", mangaId) }).parseTrpc<List<ChapterDto>>()!!

        // Make chapter url unique only when needed
        val seen = mutableSetOf<Float>()
        return chaptersDTO.map {
            it.toSChapter(manga.url, !seen.add(it.number))
        }
    }

    // ============================ Manga updates =============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        var updatedManga = if (fetchDetails) getMangaDetails(manga)!! else manga
        val updatedChapters = if (fetchChapters) {
            getChapterList(updatedManga) ?: run {
                // Auto migrate
                updatedManga = getMangaDetails(manga)!!
                getChapterList(updatedManga)
            }!!
        } else {
            chapters
        }

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    // ============================ Related manga ==============================

    override val supportRelatedMangasBySearch = true

    // ============================ Pages =====================================

    private suspend fun fetchPages(chapter: SChapter): List<Page>? = trpc(
        "reader.chapterPages",
        buildJsonObject {
            put("seriesSlug", chapter.url.getSlug())
            put("chapterNumber", chapter.getNumber())
            chapter.memo["chapterId"]?.let { put("chapterId", it) }
        },
        ensureSuccess = false,
    ).parseTrpc<List<PageDto>>()?.map(PageDto::toPage)

    override suspend fun getPageList(chapter: SChapter): List<Page> = fetchPages(chapter)?.takeIf { it.isNotEmpty() } ?: run {
        fetchHeadersWv(getChapterUrl(chapter))

        wvHeaders?.let {
            fetchPages(chapter) ?: emptyList()
        } ?: error("Failed to get headers")
    }

    protected open suspend fun fetchHeadersWv(url: String) {
        wvMutex.withLock {
            val doc = client.get(url, Fetch.DOCUMENT).asJsoup()

            runWebView(timeout = 15.seconds) {
                userAgent = headers["User-Agent"] ?: ""
                blockImages = true

                interceptRequest { request ->
                    val requestUrl = request.url.toString()
                    if (requestUrl.startsWith("$baseUrl/api")) {
                        val allHeaders = request.requestHeaders
                        if (allHeaders.isNotEmpty()) {
                            wvHeaders = headers.newBuilder().apply {
                                allHeaders.forEach { (key, value) ->
                                    headers[key] ?: add(key, value)
                                }
                            }.build()
                        }
                        resolve(Unit)
                    }
                    null
                }

                loadData(doc.location(), doc.outerHtml())
            }
        }
    }

    // ============================ Preferences ================================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = MAX_RATING_PREF
            title = intl["pref_rating_title"]
            summary = "${intl["pref_rating_summary"]} %s"
            entries = MAX_RATING_ENTRIES
            entryValues = MAX_RATING_VALUES
            setDefaultValue(MAX_RATING_DEFAULT)
        }.let(screen::addPreference)
    }

    // ============================ Filters ====================================

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = trpc("search.genres").parseTrpc<List<GenreDto>>()!!.map { it.name }.toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        listOfNotNull<Filter<*>>(
            SortFilter(intl),
            RatingFilter(intl),
            TypeFilter(intl),
            StatusFilter(intl),
            data?.parseAs<List<String>>()?.takeIf { it.isNotEmpty() }?.let { GenresFilter(it, intl) },
        ),
    )

    open class OrderByFilter(displayName: String, private val vals: Array<Pair<String, String>>, state: Int = 0) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), state) {
        fun selected() = vals[state].second
    }

    class SortFilter(intl: Intl) :
        OrderByFilter(
            intl["sort_by_filter_title"],
            arrayOf(
                intl["sort_by_relevance"] to "relevance",
                intl["sort_by_popular"] to "popular",
                intl["sort_by_score"] to "score",
                intl["sort_by_recent"] to "recent",
                intl["sort_by_newest"] to "newest",
                intl["sort_by_oldest"] to "oldest",
                "A-Z" to "alphabetical",
            ),
        )

    open class SelectFilter(displayName: String, val key: String, private val vals: Array<Pair<String, String?>>, state: Int = 0) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), state) {
        fun selected(): String? = vals[state].second
    }

    class RatingFilter(intl: Intl) :
        SelectFilter(
            intl["rating_filter_title"],
            "contentRating",
            (
                listOf(
                    intl["status_all"] to null,
                ) + MAX_RATING_ENTRIES.zip(MAX_RATING_VALUES).map { it.first to it.second }
                )
                .toTypedArray(),
        )

    class TypeFilter(intl: Intl) :
        SelectFilter(
            intl["type_filter_title"],
            "type",
            arrayOf(
                intl["status_all"] to null,
                "Manga" to "manga",
                "Manhwa" to "manhwa",
                "Manhua" to "manhua",
                "Novel" to "novel",
                "Webtoon" to "webtoon",
                "One Shot" to "one_shot",
            ),
        )

    class StatusFilter(intl: Intl) :
        SelectFilter(
            intl["status_filter_title"],
            "status",
            arrayOf(
                intl["status_all"] to null,
                intl["status_ongoing"] to "ongoing",
                intl["status_completed"] to "completed",
                intl["status_onhiatus"] to "hiatus",
                intl["status_canceled"] to "cancelled",
            ),
        )

    class GenreCheckBox(val value: String) : Filter.CheckBox(value)

    class GenresFilter(entries: List<String>, intl: Intl) :
        Filter.Group<GenreCheckBox>(
            intl["genre_filter_title"],
            entries.map { GenreCheckBox(it) },
        ) {
        val checked get() = state.filter { it.state }.map { it.value }
    }

    companion object {
        private const val LIMIT = 30
        private const val MAX_RATING_PREF = "MAX_RATING"
        private const val MAX_RATING_DEFAULT = "pornographic"
        private val MAX_RATING_ENTRIES = arrayOf("Pornographic", "Erotica", "Suggestive", "Safe")
        private val MAX_RATING_VALUES = arrayOf("pornographic", "erotica", "suggestive", "safe")
    }

    // Old (New)
    // manga: /manga/<slug|(#ID)>
    // chapter: /manga/slug(#ID)/<chapter-XX|(XX.X)>

    fun String.getSlug(): String = baseUrl.toHttpUrl().resolve(this)?.getSlug()
        ?: throw IOException("Migrate from $name to $name")

    fun HttpUrl.getSlug(): String = pathSegments
        .takeIf { it.firstOrNull() == mangaPath }
        ?.getOrNull(1)
        ?.takeIf(String::isNotBlank)
        ?: throw IOException("Migrate from $name to $name")

    fun SChapter.getNumber() = if (chapter_number > 0) {
        chapter_number
    } else {
        url.trim('/').substringAfterLast("/").substringAfter("-").toFloatOrNull() ?: 1f
    }
}
