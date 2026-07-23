package eu.kanade.tachiyomi.multisrc.iken

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.lib.i18n.Intl
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.int
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import okhttp3.internal.closeQuietly
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

abstract class Iken :
    KeiSource(),
    ConfigurableSource {

    protected open val apiUrl: String
        get() = baseUrl.replace("https://", "https://api.")

    private val preferences: SharedPreferences by getPreferencesLazy()

    protected val intl = Intl(
        language = lang,
        baseLanguage = "en",
        availableLanguages = setOf("en", "ar"),
        classLoader = this::class.java.classLoader!!,
    )

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

    /**
     * Whether image URLs should be sorted by their filenames
     */
    protected open val sortPagesByFilename = false

    /**
     * Whether to use standalone chapters endpoint than manga object
     */
    protected open val useChaptersApi = false

    // ============================== Popular ==============================

    // Popular (Search with popular order and nothing else)
    protected open val popularFilter by lazy {
        FilterList(SortFilter("", sortFilterKey, sortOptions, sortOptions[1].second))
    }

    override suspend fun getPopularManga(page: Int) = getSearchMangaList(page, "", popularFilter)

    // ============================== Latest ===============================

    // Latest (Search with update order and nothing else)
    protected open val latestFilter by lazy {
        FilterList(SortFilter("", sortFilterKey, sortOptions))
    }

    override suspend fun getLatestUpdates(page: Int) = getSearchMangaList(page, "", latestFilter)

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$apiUrl/api/query".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("perPage", perPage.toString())
            addQueryParameter("searchTerm", query.trim())
            filters.filterIsInstance<UrlPartFilter>().forEach {
                it.addUrlParameter(this)
            }
        }.build()

        return parseSearchMangaList(client.get(url))
    }

    // Tracks the current page
    private val pageNumber = ConcurrentHashMap<String, Int>()

    private fun keyFromUrl(url: HttpUrl): String = url.queryParameterNames
        .sorted()
        .mapNotNull { paramName ->
            val value = url.queryParameter(paramName)
            if (value.isNullOrBlank()) null else "$paramName=$value"
        }.joinToString("&")

    protected open suspend fun parseSearchMangaList(response: Response): MangasPage {
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

            return parseSearchMangaList(client.get(newUrl))
        }

        if (!hasNextPage) pageNumber.remove(key)

        return MangasPage(entries, hasNextPage)
    }

    // ============================== Filters ==============================

    override val supportsFilterFetching get() = true

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

    override suspend fun fetchFilterData(): JsonElement = client.get("$apiUrl/api/genres").parseAs<JsonElement>()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genresList = data?.let {
            it.parseAs<List<Genre>>().map { it.name to it.id.toString() }
        }

        val filters = mutableListOf<Filter<*>>(
            StatusFilter(intl["status_filter_title"], statusFilterKey, statusFilterOptions),
            TypeFilter(intl["type_filter_title"], typeFilterKey, typeFilterOptions),
            SortFilter(intl["sort_by_title"], sortFilterKey, sortOptions),
            SortFilter(intl["sort_direction_title"], sortDirectionFilterKey, sortDirectionOptions),
        ).filterNot { filter ->
            filter is Filter.Select<*> && filter.values.isEmpty()
        }.toMutableList()

        if (!genresList.isNullOrEmpty()) {
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
        }

        return FilterList(filters)
    }

    // ============================== Details ==============================

    private suspend fun getMangaDetails(slug: String): Manga {
        val mangaUrl = "$apiUrl/api/post?postSlug=$slug"
        val response = client.get(mangaUrl)
        val data = response.parseAs<MangaDto>().post
        if (data.isNovel) throw IOException("Novels are unsupported")
        updateViews(data.id)
        return data
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.pathSegments.size >= 2) {
            val slug = url.pathSegments[1]
            val details = getMangaDetails(slug)
            return details.toSManga().apply {
                initialized = true
            }
        }

        throw Exception("Unsupported URL")
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url.substringBeforeLast("#")}"

    protected fun Chapter.isVisible(): Boolean = isAccessible() || (
        preferences.getBoolean(SHOW_LOCKED_CHAPTER_PREF_KEY, false) && isLocked()
        )

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val slug = manga.url.substringBeforeLast("#")
        val id = manga.url.substringAfterLast("#")

        if (useChaptersApi) {
            val mangaDeferred = async { if (fetchDetails) getMangaDetails(slug).toSManga() else manga }
            val chaptersDeferred = async {
                if (fetchChapters) {
                    val response = client.get("$apiUrl/api/chapters?postId=$id")
                    val chapterData = response.parseAs<ChapterDto>().post
                    chapterData.chapters.filter { it.isVisible() }.map { it.toSChapter(slug) }
                } else {
                    chapters
                }
            }
            SMangaUpdate(mangaDeferred.await(), chaptersDeferred.await())
        } else {
            val details = getMangaDetails(slug)
            val updatedManga = details.toSManga()

            val updatedChapters = details.chapters.filter { it.isVisible() }.map { it.toSChapter(details.slug) }
            SMangaUpdate(updatedManga, updatedChapters)
        }
    }

    // ========================= Related Manga =========================

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val id = manga.url.substringAfterLast("#")
        val response = client.get("$apiUrl/api/recommendations?postId=$id&limit=25")

        return response.parseAs<RelatedMangaDto>().recommendations.filterNot {
            it.isNovel
        }.map { it.toSManga() }
    }

    // ============================== Pages ==============================

    override fun getChapterUrl(chapter: SChapter): String {
        val seriesSlug = chapter.memo["seriesSlug"]!!.string
        val slug = chapter.memo["slug"]!!.string
        return "$baseUrl/series/$seriesSlug/$slug"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val id = chapter.memo["id"]?.int ?: throw Exception("Refresh Chapter List")
        val response = client.get("$apiUrl/api/chapter?chapterId=$id")

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

        updateViews(null, id)

        val sortedPages = if (sortPagesByFilename) {
            data.images.sortedWith(
                compareBy { page ->
                    val filename = page.url.substringAfterLast('/')
                    NUMBER_REGEX.find(filename)?.value?.toIntOrNull() ?: Int.MAX_VALUE
                },
            )
        } else {
            data.images.sortedBy { it.order ?: Int.MAX_VALUE }
        }

        return sortedPages.mapIndexed { idx, p ->
            Page(idx, imageUrl = p.url.replace(" ", "%20"))
        }
    }

    // ============================== View =============================

    /**
     * Sends a request to increment view counter
     */
    protected open fun updateViews(postId: Int? = null, chapterId: Int? = null) {
        if (!sendUpdateViews || (postId ?: chapterId) == null) return

        client.newCall(
            POST(
                "$apiUrl/api/analytics/updateViews",
                headers,
                ViewQuery(postId, chapterId).toJsonRequestBody(),
            ),
        ).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) = Unit
            override fun onResponse(call: Call, response: Response) {
                response.closeQuietly()
            }
        })
    }

    // ============================== Preferences ==============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_LOCKED_CHAPTER_PREF_KEY
            title = intl["show_inaccessible_title"]
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        const val SHOW_LOCKED_CHAPTER_PREF_KEY = "pref_show_locked_chapters"
        val NUMBER_REGEX = Regex("\\d+")
    }
}
