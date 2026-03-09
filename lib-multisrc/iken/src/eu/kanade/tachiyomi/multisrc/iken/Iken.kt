package eu.kanade.tachiyomi.multisrc.iken

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.lib.i18n.Intl
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.extractNextJsRsc
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
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
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/api/query".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("perPage", PER_PAGE.toString())
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

        val hasNextPage = data.totalCount > (page * PER_PAGE)

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
        CoroutineScope(Dispatchers.IO).launch {
            fetchGenres()
        }

        val filters = mutableListOf<Filter<*>>().apply {
            addIfNotEmpty(statusFilterOptions) {
                StatusFilter(intl["status_filter_title"], statusFilterKey, statusFilterOptions)
            }
            addIfNotEmpty(typeFilterOptions) {
                TypeFilter(intl["type_filter_title"], typeFilterKey, typeFilterOptions)
            }
            addIfNotEmpty(sortOptions) {
                SortFilter(intl["sort_by_title"], sortFilterKey, sortOptions)
            }
            addIfNotEmpty(sortDirectionOptions) {
                SortFilter(intl["sort_direction_title"], sortDirectionFilterKey, sortDirectionOptions)
            }
        }

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

    fun <T> MutableList<T>.addIfNotEmpty(options: List<*>, filter: () -> T) {
        if (options.isNotEmpty()) add(filter())
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

    // details

    override fun getMangaUrl(manga: SManga): String {
        val slug = manga.url.substringBeforeLast("#")

        return "$baseUrl/series/$slug"
    }

    override fun mangaDetailsRequest(manga: SManga): Request = GET(getMangaUrl(manga), rscHeaders)

    override fun mangaDetailsParse(response: Response): SManga = response.extractNextJs<Manga>()!!.toSManga()

    // chapters

    override fun chapterListRequest(manga: SManga): Request = GET("$baseUrl/series/${manga.url}", rscHeaders)

    override fun chapterListParse(response: Response): List<SChapter> {
        val id = response.request.url.fragment!!
        val slug = response.request.url.pathSegments.last()
        val body = response.body.string()

        // Detect vShield / BalooPow challenge pagege
        if (vShieldRegex.containsMatchIn(body)) throw Exception("vShield challenge detected. Open in WebView to solve it.")

        val data = runCatching {
            body.extractNextJsRsc<Post<ChapterListResponse>>()
        }.getOrNull() ?: run {
            val userId = userIdRegex.find(body)?.groupValues?.get(1).orEmpty()
            val chapterUrl = "$apiUrl/api/chapters?postId=$id&skip=0&take=900&order=desc&userid=$userId"

            client.newCall(GET(chapterUrl, headers))
                .execute()
                .parseAs<Post<ChapterListResponse>>()
        }

        assert(!data.post.isNovel) { "Novels are unsupported" }

        return data.post.chapters
            .filter { it.isPublic() && (it.isAccessible() || (preferences.getBoolean(SHOW_LOCKED_CHAPTER_PREF_KEY, false) && it.isLocked())) }
            .map { it.toSChapter(data.post.slug ?: slug) }
    }

    // pages

    // some extensions need to sort image urls by filename, override this to true if so
    protected open val sortPagesByFilename = false

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        if (document.select("#publicSalt, #challenge").isNotEmpty()) {
            throw Exception("vShield challenge detected. Open in WebView to solve it.")
        }

        if (document.selectFirst("svg.lucide-lock") != null) {
            throw Exception("Unlock chapter in webview")
        }

        val pages = document.extractNextJs<Images>()!!

        val sortedPages = if (sortPagesByFilename) {
            pages.images.sortedWith(
                compareBy { page ->
                    val filename = page.url.substringAfterLast('/')
                    val number = Regex("\\d+").find(filename)?.value?.toIntOrNull() ?: Int.MAX_VALUE
                    number
                },
            )
        } else {
            pages.images
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

    companion object {
        const val PER_PAGE = 18
        const val SHOW_LOCKED_CHAPTER_PREF_KEY = "pref_show_locked_chapters"
        val vShieldRegex = Regex("""balooPow\.min\.js|Completing challenge|publicSalt|_2__vShield_v""")
        val userIdRegex = Regex(""""user\\":\{\\"id\\":\\"([^"']+)\\"""")
    }
}
