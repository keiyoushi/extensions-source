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
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.lib.i18n.Intl
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.get
import keiyoushi.utils.getLongOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
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

    protected fun OkHttpClient.Builder.addHiperAuthInterceptor(): OkHttpClient.Builder = addInterceptor { chain ->
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
            network.client.newCall(GET(baseUrl, acceptHeaders)).execute().close()
            chain.proceed(chain.request())
        } else {
            response
        }
    }

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addHiperAuthInterceptor()

    protected val intl = Intl(
        language = lang,
        baseLanguage = "en",
        availableLanguages = setOf("en", "pt-BR"),
        classLoader = this::class.java.classLoader!!,
    )

    // ============================ Popular ====================================

    protected open val popularFilter = FilterList(OrderByFilter("", arrayOf("" to "popular")))

    override suspend fun getPopularManga(page: Int): MangasPage = getSearchMangaList(page, "", popularFilter)

    // ============================ Latest ====================================
    protected open val latestFilter = FilterList(OrderByFilter("", arrayOf("" to "recent")))

    override suspend fun getLatestUpdates(page: Int): MangasPage = getSearchMangaList(page, "", latestFilter)

    // ============================ Search ====================================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val limit = 30
        val typeValue = filters.filterIsInstance<TypeFilter>().firstOrNull()?.selected()
        val statusValue = filters.filterIsInstance<StatusFilter>().firstOrNull()?.selected()
        val ratingValue = filters.filterIsInstance<RatingFilter>().firstOrNull()?.selected()
        val genresValue = filters.filterIsInstance<GenresFilter>().firstOrNull()?.checked.orEmpty()

        val input = buildJsonObject {
            putJsonObject("0") {
                putJsonObject("json") {
                    put("q", query)
                    filters.filterIsInstance<OrderByFilter>().forEach { filter ->
                        put("sort", filter.selected())
                    }
                    putJsonObject("filters") {
                        if (genresValue.isEmpty()) {
                            put("genres", null)
                        } else {
                            putJsonArray("genres") { genresValue.forEach { add(it) } }
                        }
                        put("type", typeValue)
                        put("status", statusValue)
                        put("contentRating", ratingValue)
                        put("author", null)
                        put("artist", null)
                        put("year", null)
                    }

                    put("limit", limit)
                    put("offset", (page - 1) * limit)
                    put("maxRating", preferences.getString(MAX_RATING_PREF, MAX_RATING_DEFAULT))
                }
                putJsonObject("meta") {
                    putJsonObject("values") {
                        if (genresValue.isEmpty()) put("filters.genres", buildJsonArray { add("undefined") })
                        if (typeValue == null) put("filters.type", buildJsonArray { add("undefined") })
                        if (statusValue == null) put("filters.status", buildJsonArray { add("undefined") })
                        if (ratingValue == null) put("filters.contentRating", buildJsonArray { add("undefined") })
                        put("filters.author", buildJsonArray { add("undefined") })
                        put("filters.artist", buildJsonArray { add("undefined") })
                        put("filters.year", buildJsonArray { add("undefined") })
                    }
                }
            }
        }.toString()

        val url = "$baseUrl/api/trpc/search.query".toHttpUrl().newBuilder()
            .addQueryParameter("batch", "1")
            .addQueryParameter("input", input)
            .build()

        return parseSearchMangaList(client.get(url))
    }

    protected open fun parseSearchMangaList(response: Response): MangasPage {
        val element = response.parseAs<List<JsonElement>>().first()
        val dto = element["result"]["data"]["json"]?.parseAs<WrapperContent>() ?: return MangasPage(emptyList(), false)
        return MangasPage(dto.hits.map { it.toSManga(mangaPath) }, dto.hits.isNotEmpty())
    }

    // ============================ Details ===================================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    protected open suspend fun getMangaDetails(manga: SManga): SManga? {
        val slug = manga.url.getSlug()
        return getMangaBySlug(slug)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val slug = url.toString().getSlug()
        return getMangaBySlug(slug)
    }

    protected open suspend fun getMangaBySlug(slug: String): SManga? {
        if (slug.isBlank()) return null

        val input = buildJsonObject {
            putJsonObject("0") {
                put("json", null)
                putJsonObject("meta") {
                    putJsonArray("values") {
                        add("undefined")
                    }
                }
            }
            putJsonObject("1") {
                putJsonObject("json") {
                    put("slug", slug)
                }
            }
        }

        val url = "$baseUrl/api/trpc/auth.me,series.bySlugWithGenres".toHttpUrl().newBuilder()
            .addQueryParameter("batch", "1")
            .addQueryParameter("input", input.toString())
            .build()

        return parseMangaDetails(client.get(url))
    }

    protected open fun parseMangaDetails(response: Response): SManga {
        val element = response.parseAs<List<JsonElement>>().last()
        return element["result"]["data"]["json"]!!.parseAs<MangaDto>().toSManga(mangaPath)
    }

    // ============================ Chapters ==================================

    override fun getChapterUrl(chapter: SChapter): String {
        val slug = chapter.url.getSlug()
        return "$baseUrl/$mangaPath/$slug/${chapter.getNumber()}".removeSuffix(".0")
    }

    protected open suspend fun getChapterList(manga: SManga): List<SChapter> {
        val mangaId = manga.memo.getLongOrNull("mangaId")
            ?: manga.url.substringAfterLast("#").toLongOrNull() // Fallback for old manga URLs
            ?: throw IOException("Refresh chapter list")

        val input = buildJsonObject {
            putJsonObject("0") {
                putJsonObject("json") {
                    putJsonArray("values") {
                        add("undefined")
                    }
                }
            }

            putJsonObject("1") {
                putJsonObject("json") {
                    put("seriesId", mangaId)
                    put("chapterId", null)
                    put("sort", "best")
                    put("page", 1)
                    put("limit", 20)
                }
                putJsonObject("meta") {
                    putJsonObject("values") {
                        put("chapterId", buildJsonArray { add("undefined") })
                    }
                }
            }

            putJsonObject("2") {
                putJsonObject("json") {
                    put("seriesId", mangaId)
                }
            }
        }

        val url = "$baseUrl/api/trpc/auth.me,series.chapters".toHttpUrl().newBuilder()
            .addQueryParameter("batch", "1")
            .addQueryParameter("input", input.toString())
            .build()

        val response = client.get(url)
        val element = response.parseAs<List<JsonElement>>().last()
        val chaptersDTO = element["result"]["data"]["json"]!!.parseAs<List<ChapterDto>>()
        return chaptersDTO.map { it.toSChapter(manga.url) }
    }

    // ============================ Manga updates =============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val updatedManga = if (fetchDetails) getMangaDetails(manga) else null
        val updatedChapters = if (fetchChapters) getChapterList(manga) else null

        return SMangaUpdate(updatedManga ?: manga, updatedChapters ?: chapters)
    }

    // ============================ Related manga ==============================

    override val supportsRelatedMangas = false
    override val supportRelatedMangasBySearch = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = throw UnsupportedOperationException()

    // ============================ Pages =====================================

    protected open fun pageListUrl(chapter: SChapter): HttpUrl {
        val slug = chapter.url.getSlug()

        val input = buildJsonObject {
            putJsonObject("0") {
                put("json", null)
                putJsonObject("meta") {
                    putJsonArray("values") {
                        add("undefined")
                    }
                }
            }
            putJsonObject("1") {
                putJsonObject("json") {
                    put("slug", slug)
                }
            }
            putJsonObject("2") {
                putJsonObject("json") {
                    put("seriesSlug", slug)
                    put("chapterNumber", chapter.getNumber())
                }
            }
            putJsonObject("3") {
                putJsonObject("json") {
                    put("position", "footer_bottom")
                }
            }
        }

        return "$baseUrl/api/trpc/auth.me,series.bySlug,reader.chapterPages".toHttpUrl().newBuilder()
            .addQueryParameter("batch", "1")
            .addQueryParameter("input", input.toString())
            .build()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val url = pageListUrl(chapter)

        val pages = parsePages(client.get(url, ensureSuccess = false))

        return pages?.takeIf { it.isNotEmpty() } ?: run {
            fetchHeadersWv(chapterUrl)

            wvHeaders?.let {
                parsePages(client.get(url, ensureSuccess = false)) ?: emptyList()
            } ?: error("Failed to get headers")
        }
    }

    protected open fun parsePages(response: Response): List<Page>? {
        val elements = response.parseAs<List<JsonElement>>()

        for (element in elements) {
            if ("error" in element.jsonObject) {
                return null
            }
        }

        val lastElement = elements.last()
        val pages = lastElement["result"]["data"]["json"]?.parseAs<List<PageDto>>() ?: return emptyList()
        return pages.map(PageDto::toPage)
    }

    protected open suspend fun fetchHeadersWv(url: String) {
        wvMutex.withLock {
            val doc = client.get(url).asJsoup()

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

    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        SortFilter(intl),
        RatingFilter(intl),
        TypeFilter(intl),
        StatusFilter(intl),
        GenresFilter(genresList, intl),
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

    open class SelectFilter(displayName: String, private val vals: Array<Pair<String, String?>>, state: Int = 0) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), state) {
        fun selected(): String? = vals[state].second
    }

    class RatingFilter(intl: Intl) :
        SelectFilter(
            intl["rating_filter_title"],
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

    protected open val genresList: List<String> = emptyList()

    companion object {
        private const val MAX_RATING_PREF = "MAX_RATING"
        private const val MAX_RATING_DEFAULT = "pornographic"
        private val MAX_RATING_ENTRIES = arrayOf("Pornographic", "Erotica", "Suggestive", "Safe")
        private val MAX_RATING_VALUES = arrayOf("pornographic", "erotica", "suggestive", "safe")
    }

    // Old (New)
    // manga: /manga/<slug|(#ID)>
    // chapter: /manga/slug(#ID)/<chapter-XX|(XX.X)>

    fun String.getSlug() = this.substringAfterLast("$mangaPath/")
        .substringBefore("/")
        .substringBefore("#")
        .takeIf(String::isNotBlank)
        ?: throw IOException("Refresh chapter list")

    fun SChapter.getNumber() = if (chapter_number > 0) {
        chapter_number
    } else {
        url.trim('/').substringAfterLast("/").substringAfter("-").toFloatOrNull() ?: 1f
    }
}
