package eu.kanade.tachiyomi.multisrc.mangak

import android.content.SharedPreferences
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient

abstract class MangaKSource :
    KeiSource(),
    ConfigurableSource {

    override val supportsFilterFetching = true
    protected open val apiUrl: String
        get() = "https://api." + baseUrl.toHttpUrl().host

    protected open val preferences: SharedPreferences by getPreferencesLazy()

    // Catches 5xx errors from the primary CDN and falls back to the working domain
    private val imageFallbackInterceptor = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)

        if (!response.isSuccessful && request.url.host.matches(IMAGE_FALLBACK_REGEX)) {
            response.close()
            val newUrl = request.url.newBuilder().host(FALLBACK_IMAGE_HOST).build()
            return@Interceptor chain.proceed(request.newBuilder().url(newUrl).build())
        }

        response
    }

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = this.addInterceptor(imageFallbackInterceptor)

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$apiUrl/titles/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("sort", SORT_POPULAR)
            addQueryParameter("window", WINDOW_WEEK)
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", DEFAULT_PAGE_LIMIT)

            val blacklist = getBlacklist()
            if (blacklist.isNotEmpty()) {
                addQueryParameter("exclude", blacklist.joinToString(","))
            }
        }.build()

        val response = client.get(url)
        val dto = response.parseAs<SearchResponseDto>()
        return MangasPage(dto.items.map { it.toSManga() }, dto.hasNext)
    }

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$apiUrl/titles/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("sort", SORT_LATEST)
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", DEFAULT_PAGE_LIMIT)

            val blacklist = getBlacklist()
            if (blacklist.isNotEmpty()) {
                addQueryParameter("exclude", blacklist.joinToString(","))
            }
        }.build()

        val response = client.get(url)
        val dto = response.parseAs<SearchResponseDto>()
        return MangasPage(dto.items.map { it.toSManga() }, dto.hasNext)
    }

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$apiUrl/titles/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", DEFAULT_PAGE_LIMIT)

            if (query.isNotBlank()) {
                val filteredQuery = query.filter { it.isLetterOrDigit() || it == ' ' }
                    .trim()
                    .take(QUERY_LENGTH_LIMIT)
                addQueryParameter("q", filteredQuery)
            }

            val includedGenres = mutableListOf<String>()
            val excludedGenres = getBlacklist().toMutableSet()

            filters.forEach { filter ->
                when (filter) {
                    is GenreList -> {
                        filter.state.forEach { genre ->
                            when (genre.state) {
                                Filter.TriState.STATE_INCLUDE -> {
                                    includedGenres.add(genre.value)
                                    excludedGenres.remove(genre.value)
                                }
                                Filter.TriState.STATE_EXCLUDE -> excludedGenres.add(genre.value)
                                Filter.TriState.STATE_IGNORE -> {}
                            }
                        }
                    }
                    is SortFilter -> if (filter.selected.isNotBlank()) addQueryParameter("sort", filter.selected)
                    is ContentRatingFilter -> if (filter.selected.isNotBlank()) addQueryParameter("content_rating", filter.selected)
                    is StatusFilter -> if (filter.selected.isNotBlank()) addQueryParameter("status", filter.selected)
                    is TypeFilter -> if (filter.selected.isNotBlank()) addQueryParameter("type", filter.selected)
                    is DemographicFilter -> if (filter.selected.isNotBlank()) addQueryParameter("demographic", filter.selected)
                    else -> {}
                }
            }

            filters.firstInstanceOrNull<AuthorFilter>()?.state?.takeIf { it.isNotBlank() }?.let {
                addQueryParameter("author", it)
            }
            filters.firstInstanceOrNull<MinChapterFilter>()?.state?.takeIf { it.isNotBlank() }?.let {
                addQueryParameter("min_ch", it)
            }

            if (includedGenres.isNotEmpty()) addQueryParameter("genres", includedGenres.joinToString(","))
            if (excludedGenres.isNotEmpty()) addQueryParameter("exclude", excludedGenres.joinToString(","))
        }.build()

        val response = client.get(url)
        val dto = response.parseAs<SearchResponseDto>()
        return MangasPage(dto.items.map { it.toSManga() }, dto.hasNext)
    }

    // ======================= Details & Chapters ==========================

    override fun getMangaUrl(manga: SManga): String {
        val path = manga.url.substringBefore("#")
        return baseUrl.toHttpUrl().resolve(path)?.toString() ?: (baseUrl + path)
    }

    override fun getChapterUrl(chapter: SChapter): String = baseUrl.toHttpUrl().resolve(chapter.url)?.toString() ?: (baseUrl + chapter.url)

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val detailsUrl = getMangaUrl(manga)

        // Lazy deferred ensures NextJS data is fetched at most ONCE across both jobs
        val nextJsDataDeferred = async(start = CoroutineStart.LAZY) {
            val response = client.get(detailsUrl)
            response.extractNextJs<NextJsDto> {
                it is JsonObject && "pageProps" in it
            } ?: throw IllegalStateException("Could not extract Next.js data for: $detailsUrl")
        }

        val detailsDeferred = async {
            if (fetchDetails) {
                val dto = nextJsDataDeferred.await()
                dto.pageProps?.initialManga?.toSManga(manga.url)
                    ?: throw IllegalStateException("Could not find manga details for: $detailsUrl")
            } else {
                manga
            }
        }

        val chaptersDeferred = async {
            if (fetchChapters) {
                val id = manga.memo["id"]?.jsonPrimitive?.contentOrNull ?: run {
                    val dto = nextJsDataDeferred.await()
                    dto.pageProps?.initialManga?.id
                        ?: throw IllegalStateException("Could not find manga ID for migration for: $detailsUrl")
                }

                fetchChaptersByApiId(id)
            } else {
                chapters
            }
        }

        SMangaUpdate(detailsDeferred.await(), chaptersDeferred.await())
    }

    private suspend fun fetchChaptersByApiId(id: String): List<SChapter> {
        val url = "$apiUrl/titles/$id/chapters?cv=${System.currentTimeMillis()}"
        val response = client.get(url)
        val chapterList = response.parseAs<ChapterListResponseDto>().chapters

        return chapterList.sortedByDescending { it.chapterNumber ?: 0f }
            .map { it.toSChapter() }
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val response = client.get(chapterUrl)
        val dto = response.extractNextJs<NextJsDto> {
            it is JsonObject && "pageProps" in it
        }

        val images = dto?.pageProps?.initialChapter?.images ?: return emptyList()

        return images.mapIndexed { index, url ->
            Page(index, imageUrl = url)
        }
    }

    // ============================== Filters ==============================

    override suspend fun fetchFilterData(): JsonElement {
        val json = client.get("$apiUrl/genres").parseAs<JsonElement>()
        preferences.edit().putString(PREF_GENRES_CACHE_KEY, json.toString()).apply()
        return json
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val genreData = data ?: getCachedGenreData()
        val blacklist = getBlacklist()
        val genres = getGenreList(genreData, blacklist)

        val filters = mutableListOf<Filter<*>>(
            SortFilter(),
            ContentRatingFilter(),
            StatusFilter(),
            TypeFilter(),
            DemographicFilter(),
            Filter.Separator(),
            AuthorFilter(),
            MinChapterFilter(),
        )
        if (genres.isNotEmpty()) {
            filters.add(Filter.Separator())
            filters.add(GenreList(genres))
        }
        return FilterList(filters)
    }

    // ============================= Utilities =============================

    private fun getCachedGenreData(): JsonElement? {
        val cachedString = preferences.getString(PREF_GENRES_CACHE_KEY, null)
        return cachedString?.let { str ->
            runCatching { str.parseAs<JsonElement>() }.getOrNull()
        }
    }

    private fun getBlacklist(): Set<String> = preferences.getStringSet(PREF_BLACKLIST_KEY, emptySet()) ?: emptySet()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val cachedData = getCachedGenreData()
        val genres = getGenreList(cachedData)
        val blacklistPref = MultiSelectListPreference(screen.context).apply {
            key = PREF_BLACKLIST_KEY
            title = "Global Genre Blacklist"
            summary = if (genres.isEmpty()) {
                "Open search filters in the browse screen once to load and sync genres."
            } else {
                "Select genres to always exclude from search and browse results."
            }
            entries = genres.map { it.name }.toTypedArray()
            entryValues = genres.map { it.value }.toTypedArray()
            setDefaultValue(emptySet<String>())
        }
        screen.addPreference(blacklistPref)
    }

    companion object {
        private const val PREF_BLACKLIST_KEY = "pref_blacklist"
        private const val PREF_GENRES_CACHE_KEY = "pref_genres_cache"
        private const val FALLBACK_IMAGE_HOST = "rx.rzyn.net"
        private const val DEFAULT_PAGE_LIMIT = "24"
        private const val QUERY_LENGTH_LIMIT = 50
        private const val SORT_POPULAR = "popular"
        private const val SORT_LATEST = "latest"
        private const val WINDOW_WEEK = "week"

        private val IMAGE_FALLBACK_REGEX = "rx\\.qvzr[a-z]\\.org".toRegex()
    }
}
