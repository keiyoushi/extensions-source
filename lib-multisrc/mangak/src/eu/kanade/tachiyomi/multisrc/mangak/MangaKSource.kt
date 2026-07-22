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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient

abstract class MangaKSource :
    KeiSource(),
    ConfigurableSource {

    override val supportsLatest = true

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

        val response = client.get(url, headers)
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

        val response = client.get(url, headers)
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
            val excludedGenres = mutableListOf<String>()

            filters.forEach { filter ->
                when (filter) {
                    is GenreList -> {
                        filter.state.forEach { genre ->
                            when (genre.state) {
                                Filter.TriState.STATE_INCLUDE -> includedGenres.add(genre.value)
                                Filter.TriState.STATE_EXCLUDE -> excludedGenres.add(genre.value)
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

        val response = client.get(url, headers)
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
            val response = client.get(detailsUrl, headers)
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
                val idFromUrl = manga.url.substringAfterLast("#", "")
                val idFromDesc = manga.description?.let { desc ->
                    MANGA_ID_REGEX.find(desc)?.groupValues?.get(1)?.trim()
                }

                val id = when {
                    idFromUrl.isNotBlank() -> idFromUrl
                    !idFromDesc.isNullOrBlank() -> idFromDesc
                    else -> {
                        val dto = nextJsDataDeferred.await()
                        dto.pageProps?.initialManga?.id
                            ?: throw IllegalStateException("Could not find manga ID for migration for: $detailsUrl")
                    }
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
        val response = client.get(url, headers)
        val chapterList = response.parseAs<ChapterListResponseDto>().chapters

        return chapterList.sortedByDescending { it.chapterNumber ?: 0f }
            .map { it.toSChapter() }
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val response = client.get(chapterUrl, headers)
        val dto = response.extractNextJs<NextJsDto> {
            it is JsonObject && "pageProps" in it
        }

        val images = dto?.pageProps?.initialChapter?.images
            ?: throw IllegalStateException("Could not find chapter images for: $chapterUrl")

        return images.mapIndexed { index, url ->
            Page(index, imageUrl = url)
        }
    }

    // ============================== Filters ==============================

    override fun getFilterList(data: JsonElement?): FilterList {
        val blacklist = getBlacklist()

        return FilterList(
            SortFilter(),
            ContentRatingFilter(),
            StatusFilter(),
            TypeFilter(),
            DemographicFilter(),
            Filter.Separator(),
            AuthorFilter(),
            MinChapterFilter(),
            Filter.Separator(),
            Filter.Header("Genres"),
            GenreList(getGenreList(blacklist)),
        )
    }

    // ============================= Utilities =============================

    private fun getBlacklist(): Set<String> = preferences.getStringSet(PREF_BLACKLIST_KEY, emptySet()) ?: emptySet()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val genres = getGenreList()
        val blacklistPref = MultiSelectListPreference(screen.context).apply {
            key = PREF_BLACKLIST_KEY
            title = "Global Genre Blacklist"
            summary = "Select genres to always exclude from search and browse results."
            entries = genres.map { it.name }.toTypedArray()
            entryValues = genres.map { it.value }.toTypedArray()
            setDefaultValue(emptySet<String>())
        }
        screen.addPreference(blacklistPref)
    }

    companion object {
        private const val PREF_BLACKLIST_KEY = "pref_blacklist"
        private const val FALLBACK_IMAGE_HOST = "rx.rzyn.net"
        private const val DEFAULT_PAGE_LIMIT = "24"
        private const val QUERY_LENGTH_LIMIT = 50
        private const val SORT_POPULAR = "popular"
        private const val SORT_LATEST = "latest"
        private const val WINDOW_WEEK = "week"

        private val IMAGE_FALLBACK_REGEX = "rx\\.qvzr[a-z]\\.org".toRegex()
        private val MANGA_ID_REGEX = """Manga ID:\s*(.+)""".toRegex(RegexOption.IGNORE_CASE)
    }
}
