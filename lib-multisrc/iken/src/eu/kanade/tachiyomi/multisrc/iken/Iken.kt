package eu.kanade.tachiyomi.multisrc.iken

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.lib.i18n.Intl
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import rx.Observable
import java.util.concurrent.ConcurrentHashMap

abstract class Iken(
    override val name: String,
    override val lang: String,
    override val baseUrl: String,
    val apiUrl: String = baseUrl,
) : HttpSource(),
    ConfigurableSource {

    override val supportsLatest = true

    override val client = network.cloudflareClient

    private val preferences: SharedPreferences by getPreferencesLazy()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    protected val rscHeaders = headersBuilder()
        .set("rsc", "1")
        .build()

    protected val intl = Intl(
        language = lang,
        baseLanguage = "en",
        availableLanguages = setOf("en", "ar"),
        classLoader = this::class.java.classLoader!!,
    )

    /**
     * Automatically fetched genres from the source to be used in the filters.
     */
    protected open var genresList: Options = emptyList()

    /**
     * Whether genres have been fetched
     */
    private var genresFetched: Boolean = false

    /**
     * Inner variable to control how much tries the genres request was called.
     */
    private var fetchGenresAttempts: Int = 0

    /**
     * Disable it if you don't want the genres to be fetched.
     */
    protected open val fetchGenres: Boolean = true

    /**
     * Whether the extension should report view updates to the source website
     * through `analytics/updateViews`.
     *
     * Set to false to disable sending the request.
     */
    protected open val sendUpdateViews: Boolean = true

    /**
     * The number of items to fetch per page in search/popular/latest requests.
     */
    protected open val perPage: Int = 18

    // Popular (Search with popular order and nothing else)
    protected open val popularFilter by lazy {
        FilterList(SortFilter("", sortFilterKey, sortOptions, sortOptions[1].second))
    }

    override fun popularMangaRequest(page: Int) = searchMangaRequest(page, "", popularFilter)
    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    // Latest (Search with update order and nothing else)
    protected open val latestFilter by lazy {
        FilterList(SortFilter("", sortFilterKey, sortOptions))
    }

    override fun latestUpdatesRequest(page: Int) = searchMangaRequest(page, "", latestFilter)
    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    // search

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (!query.startsWith("http")) {
            return super.fetchSearchManga(page, query, filters)
        }

        val url = query.toHttpUrl()
        val baseHost = baseUrl.toHttpUrl().host

        if (url.host != baseHost) throw Exception("Unsupported URL")

        val pathSegments = url.pathSegments
        val slug = pathSegments.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
            ?: throw Exception("Invalid URL format")

        val manga = SManga.create().apply {
            this@apply.url = slug
        }

        return fetchMangaDetails(manga)
            .map { MangasPage(listOf(it), false) }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/api/query".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("perPage", perPage.toString())
            addQueryParameter("searchTerm", query.trim())
            filters.filterIsInstance<UrlPartFilter>().forEach {
                it.addUrlParameter(this)
            }
        }.build()

        return GET(url, headers)
    }

    // Tracks the current page
    private val pageNumber = ConcurrentHashMap<String, Int>()

    private fun keyFromUrl(url: HttpUrl): String = url.queryParameterNames
        .sorted()
        .mapNotNull { paramName ->
            val value = url.queryParameter(paramName)
            if (value.isNullOrBlank()) null else "$paramName=$value"
        }.joinToString("&")

    override fun searchMangaParse(response: Response): MangasPage {
        val data = response.parseAs<SearchResponse>()
        var page = response.request.url.queryParameter("page")!!.toInt()

        val entries = data.posts
            .filterNot { it.isNovel }
            .map { it.toSManga() }

        val hasNextPage = data.totalCount > (page * perPage)

        val key = keyFromUrl(response.request.url)
        if (page == 1) pageNumber[key] = 1

        if (entries.isEmpty() && hasNextPage) {
            pageNumber[key] = pageNumber[key]!! + 1
            val newUrl = response.request.url.newBuilder()
                .setQueryParameter("page", pageNumber[key]!!.toString())
                .build()
            val newResponse = client.newCall(GET(newUrl)).execute()
            return searchMangaParse(newResponse)
        }

        if (!hasNextPage) pageNumber.remove(key)

        return MangasPage(entries, hasNextPage)
    }

    // filters
    protected open val statusFilterKey: String = "seriesStatus"

    protected open val statusFilterOptions: Options =
        listOf(
            intl["status_filter_all"] to "",
            intl["status_filter_ongoing"] to "ONGOING",
            intl["status_filter_completed"] to "COMPLETED",
            intl["status_filter_canceled"] to "CANCELLED",
            intl["status_filter_dropped"] to "DROPPED",
            intl["status_filter_coming_soon"] to "COMING_SOON",
            intl["status_filter_mass_released"] to "MASS_RELEASED",
        )

    protected open val typeFilterKey: String = "seriesType"

    protected open val typeFilterOptions: Options =
        listOf(
            intl["type_filter_all"] to "",
            intl["type_filter_manga"] to "MANGA",
            intl["type_filter_manhua"] to "MANHUA",
            intl["type_filter_manhwa"] to "MANHWA",
            intl["type_filter_russian"] to "RUSSIAN",
            intl["type_filter_spanish"] to "SPANISH",
        )

    protected open val sortOptions: Options =
        listOf(
            intl["sort_by_last_chapter"] to "lastChapterAddedAt",
            intl["sort_by_views"] to "totalViews",
            intl["sort_by_added_date"] to "createdAt",
            intl["sort_by_chapters_count"] to "chaptersCount",
            intl["sort_by_alphabetical"] to "postTitle",
        )

    protected open val sortFilterKey: String = "orderBy"

    protected open val sortDirectionOptions: Options =
        listOf(
            intl["sort_direction_descending"] to "desc",
            intl["sort_direction_ascending"] to "asc",
        )

    protected open val sortDirectionFilterKey: String = "orderDirection"

    protected open val genreFilterKey: String = "genreIds"

    override fun getFilterList(): FilterList {
        launchIO { fetchGenres() }

        val filters = mutableListOf<Filter<*>>(
            StatusFilter(intl["status_filter_title"], statusFilterKey, statusFilterOptions),
            TypeFilter(intl["type_filter_title"], typeFilterKey, typeFilterOptions),
            SortFilter(intl["sort_by_title"], sortFilterKey, sortOptions),
            SortFilter(intl["sort_direction_title"], sortDirectionFilterKey, sortDirectionOptions),
        ).filterNot { filter ->
            filter is Filter.Select<*> && filter.values.isEmpty()
        }.toMutableList()

        if (genresList.isNotEmpty()) {
            filters +=
                listOf(
                    Filter.Separator(),
                    Filter.Header(intl["genre_filter_header"]),
                    GenreFilter(
                        title = intl["genre_filter_title"],
                        genreFilterKey,
                        genres = genresList,
                    ),
                )
        } else if (fetchGenres) {
            filters +=
                listOf(
                    Filter.Separator(),
                    Filter.Header(intl["genre_missing_warning"]),
                )
        }

        return FilterList(filters)
    }

    /**
     * Fetch the genres from the source to be used in the filters.
     */
    protected suspend fun fetchGenres() {
        if (fetchGenres && fetchGenresAttempts < 3 && !genresFetched) {
            try {
                client.newCall(genresRequest()).await()
                    .use { parseGenres(it) }
                    .also {
                        genresFetched = true
                    }
                    .takeIf { it.isNotEmpty() }
                    ?.also {
                        genresList = it
                    }
            } catch (_: Exception) {
            } finally {
                fetchGenresAttempts++
            }
        }
    }

    /**
     * The request to the search page (or another one) that have the genres list.
     */
    protected open fun genresRequest(): Request = GET("$apiUrl/api/genres", headers)

    /**
     * Get the genres from the search page document.
     *
     * @param document The search page document
     */
    protected open fun parseGenres(response: Response): List<Pair<String, String>> = response
        .parseAs<List<Genre>>()
        .map { Pair(it.name, it.id.toString()) }

    /**
     * Sends a request to update the view count.
     *
     * @param postId ID of the post.
     * @param chapterId ID of the chapter.
     */
    private suspend fun updateViews(postId: Int? = null, chapterId: Int? = null) {
        if (!sendUpdateViews || (postId ?: chapterId) == null) return

        try {
            client.newCall(
                POST(
                    "$apiUrl/api/analytics/updateViews",
                    headers,
                    ViewQuery(postId, chapterId)
                        .toJsonString()
                        .toRequestBody(JSON_MEDIA_TYPE),
                ),
            ).await().close()
        } catch (_: Exception) {
        }
    }

    // details

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url.substringBeforeLast("#")}"

    override fun mangaDetailsRequest(manga: SManga): Request {
        val id = manga.url.substringAfterLast("#")
        return GET("$apiUrl/api/post?postId=$id", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<Post<Manga>>().post.toSManga()

    // chapters

    protected fun Chapter.isVisible(): Boolean = isPublic() && (
        isAccessible() || (
            preferences.getBoolean(SHOW_LOCKED_CHAPTER_PREF_KEY, false) && isLocked()
            )
        )

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val id = response.request.url.queryParameter("postId")

        val data = response.parseAs<Post<ChapterListResponse>>()

        launchIO { updateViews(id?.toInt()) }

        assert(!data.post.isNovel) { "Novels are unsupported" }

        return data.post.chapters
            .filter { it.isVisible() }
            .map { it.toSChapter(data.post.slug) }
    }

    // Related Manga

    override fun relatedMangaListRequest(manga: SManga): Request {
        val id = manga.url.substringAfterLast("#")
        return GET("$apiUrl/api/recommendations?postId=$id&limit=25", headers)
    }

    override fun relatedMangaListParse(response: Response): List<SManga> = response.parseAs<RelatedMangaDto>().recommendations.filterNot { it.isNovel }
        .map { it.toSManga() }

    // pages

    // some extensions need to sort image urls by filename, override this to true if so
    protected open val sortPagesByFilename = false

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/${chapter.url.substringBeforeLast("#")}"

    override fun pageListRequest(chapter: SChapter): Request {
        val id = chapter.url.substringAfterLast("#")
        return GET("$apiUrl/api/chapter?chapterId=$id", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAs<PageResponse>().chapter

        if (data.isShortLinkLocked) {
            throw Exception("Chapter locked (short link)")
        }

        if (data.isLockedByCoins) {
            throw Exception("Chapter locked (coins required)")
        }

        if (data.isPermanentlyLocked) {
            throw Exception("Chapter permanently locked")
        }

        launchIO { updateViews(null, data.id) }

        val sortedPages = if (sortPagesByFilename) {
            data.images.sortedWith(
                compareBy { page ->
                    val filename = page.url.substringAfterLast('/')
                    numberRegex.find(filename)?.value?.toIntOrNull() ?: Int.MAX_VALUE
                },
            )
        } else {
            data.images.sortedBy { it.order ?: Int.MAX_VALUE }
        }

        return sortedPages.mapIndexed { idx, p ->
            Page(idx, imageUrl = p.url.replace(" ", "%20"))
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    // preferences

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_LOCKED_CHAPTER_PREF_KEY
            title = intl["show_inaccessible_title"]
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    protected fun launchIO(block: suspend () -> Unit) = scope.launch { block() }

    companion object {
        const val SHOW_LOCKED_CHAPTER_PREF_KEY = "pref_show_locked_chapters"
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        val numberRegex = Regex("\\d+")
    }
}
