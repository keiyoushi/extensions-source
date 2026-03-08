package eu.kanade.tachiyomi.multisrc.iken

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
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
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
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

    protected val intl = Intl(
        language = lang,
        baseLanguage = "en",
        availableLanguages = setOf("en", "ar"),
        classLoader = this::class.java.classLoader!!,
    )

    protected val titleCache by lazy {
        val response = client.newCall(GET("$apiUrl/api/query?perPage=9999", headers)).execute()
        val data = response.parseAs<SearchResponse>()
        data.posts.filterNot { it.isNovel }.associateBy { it.slug }
    }

    /**
     * Enables the API request for fetching popular manga.
     */
    protected open val usePopularMangaApi: Boolean = false

    /**
     * The path segment used in the API URL for the popular request.
     * This can be changed if the site uses a different endpoint path
     */
    protected open val popularSubString: String = "query"

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

    // popular

    protected open fun popularMangaUrl(page: Int): HttpUrl.Builder = "$apiUrl/api/$popularSubString".toHttpUrl().newBuilder().apply {
        addQueryParameter("page", page.toString())
        addQueryParameter("perPage", PER_PAGE.toString())
        addQueryParameter("orderBy", "totalViews")
    }

    override fun popularMangaRequest(page: Int): Request = if (usePopularMangaApi) {
        GET(popularMangaUrl(page).build(), headers)
    } else {
        GET("$baseUrl/home", headers)
    }

    protected open val popularMangaSelector = "aside a:has(img), .splide:has(.card) li a:has(img)"

    override fun popularMangaParse(response: Response): MangasPage = if (usePopularMangaApi) {
        searchMangaParse(response)
    } else {
        val document = response.asJsoup()

        val entries = document.select(popularMangaSelector).mapNotNull {
            titleCache[it.absUrl("href").substringAfter("series/")]?.toSManga()
        }

        MangasPage(entries, false)
    }

    // latest

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/api/posts".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("perPage", PER_PAGE.toString())
            if (apiUrl.startsWith("https://api.", true)) {
                addQueryParameter("tag", "latestUpdate")
                addQueryParameter("isNovel", "false")
            }
        }.build()

        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

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

    // search

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
            intl["sort_by_latest_chapters"] to "latest_chapters",
            intl["sort_by_popular"] to "popular",
            intl["sort_by_newest"] to "newest",
            intl["sort_by_oldest"] to "oldest",
            intl["sort_by_most_chapters"] to "most_chapters",
            intl["sort_by_alphabetical"] to "alphabetical",
        )

    protected open val sortFilterKey: String = "sortBy"

    override fun getFilterList(): FilterList {
        CoroutineScope(Dispatchers.IO).launch {
            fetchGenres()
        }

        val filters = mutableListOf<Filter<*>>().apply {
            addIfNotEmpty(statusFilterOptions) { StatusFilter(intl["status_filter_title"], statusFilterKey, statusFilterOptions) }
            addIfNotEmpty(typeFilterOptions) { TypeFilter(intl["type_filter_title"], typeFilterKey, typeFilterOptions) }
            addIfNotEmpty(sortOptions) { SortFilter(intl["sort_by_title"], sortFilterKey, sortOptions) }
        }

        if (genresList.isNotEmpty()) {
            filters +=
                listOf(
                    Filter.Separator(),
                    Filter.Header(intl["genre_filter_header"]),
                    GenreFilter(
                        title = intl["genre_filter_title"],
                        "genreIds",
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
    protected fun fetchGenres() {
        if (fetchGenres && fetchGenresAttempts < 3 && !genresFetched) {
            try {
                client.newCall(genresRequest()).execute()
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

    override fun fetchMangaDetails(manga: SManga): Observable<SManga> {
        val slug = manga.url.substringBeforeLast("#")
        val update = titleCache[slug]?.toSManga() ?: manga

        return Observable.just(update)
    }

    override fun mangaDetailsParse(response: Response) = throw UnsupportedOperationException()

    // chapters

    override fun chapterListRequest(manga: SManga): Request = GET("$baseUrl/series/${manga.url}", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val userId = userIdRegex.find(response.body.string())?.groupValues?.get(1) ?: ""

        val id = response.request.url.fragment!!
        val chapterUrl = "$apiUrl/api/chapters?postId=$id&skip=0&take=900&order=desc&userid=$userId"
        val chapterResponse = client.newCall(GET(chapterUrl, headers)).execute()

        val data = chapterResponse.parseAs<Post<ChapterListResponse>>()

        assert(!data.post.isNovel) { "Novels are unsupported" }

        return data.post.chapters
            .filter { it.isPublic() && (it.isAccessible() || (preferences.getBoolean(SHOW_LOCKED_CHAPTER_PREF_KEY, false) && it.isLocked())) }
            .map { it.toSChapter(data.post.slug) }
    }

    // pages

    // some extensions need to sort image urls by filename, override this to true if so
    protected open val sortPagesByFilename = false

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        if (document.selectFirst("svg.lucide-lock") != null) {
            throw Exception("Unlock chapter in webview")
        }

        val pages = document.getNextJson("images").parseAs<List<PageParseDto>>()

        val sortedPages = if (sortPagesByFilename) {
            pages.sortedWith(
                compareBy { page ->
                    val filename = page.url.substringAfterLast('/')
                    val number = Regex("\\d+").find(filename)?.value?.toIntOrNull() ?: Int.MAX_VALUE
                    number
                },
            )
        } else {
            pages
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
            title = "Show inaccessible chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    protected fun Document.getNextJson(key: String): String {
        val data = selectFirst("script:containsData($key)")
            ?.data()
            ?: throw Exception("Unable to retrieve NEXT data")

        val keyIndex = data.indexOf(key)
        val start = data.indexOf('[', keyIndex)

        var depth = 1
        var i = start + 1

        while (i < data.length && depth > 0) {
            when (data[i]) {
                '[' -> depth++
                ']' -> depth--
            }
            i++
        }

        return "\"${data.substring(start, i)}\"".parseAs<String>()
    }

    companion object {
        const val PER_PAGE = 18
        const val SHOW_LOCKED_CHAPTER_PREF_KEY = "pref_show_locked_chapters"
        val userIdRegex = Regex(""""user\\":\{\\"id\\":\\"([^"']+)\\"""")
    }
}
