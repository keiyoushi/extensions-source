package eu.kanade.tachiyomi.multisrc.mangak

import android.content.SharedPreferences
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.stringOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import kotlin.time.Clock

abstract class MangaKSource :
    KeiSource(),
    ConfigurableSource {

    override val supportsFilterFetching = true
    protected open val apiUrl: String
        get() = "https://api." + baseUrl.toHttpUrl().host

    protected open val preferences: SharedPreferences by getPreferencesLazy()

    // Failed qvzr images are retried on the fallback host, like the site's reader
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

        val response = client.get(url, Fetch.XHR)
        val dto = response.parseAs<SearchResponseDto>()
        return MangasPage(dto.data.items.map { it.toSManga() }, dto.data.pagination.hasNext)
    }

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

        val response = client.get(url, Fetch.XHR)
        val dto = response.parseAs<SearchResponseDto>()
        return MangasPage(dto.data.items.map { it.toSManga() }, dto.data.pagination.hasNext)
    }

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
                    is FormatFilter -> if (filter.selected.isNotBlank()) addQueryParameter("format", filter.selected)
                    is TagFilter -> if (filter.state.isNotBlank()) addQueryParameter("tag", filter.state.toTagSlug())
                    is AuthorFilter -> if (filter.state.isNotBlank()) addQueryParameter("author", filter.state)
                    is MinChapterFilter -> if (filter.state.isNotBlank()) addQueryParameter("min_ch", filter.state)
                    is MaxChapterFilter -> if (filter.state.isNotBlank()) addQueryParameter("max_ch", filter.state)
                    else -> {}
                }
            }

            if (includedGenres.isNotEmpty()) addQueryParameter("genres", includedGenres.joinToString(","))
            if (excludedGenres.isNotEmpty()) addQueryParameter("exclude", excludedGenres.joinToString(","))
        }.build()

        val response = client.get(url, Fetch.XHR)
        val dto = response.parseAs<SearchResponseDto>()
        return MangasPage(dto.data.items.map { it.toSManga() }, dto.data.pagination.hasNext)
    }

    // Entries migrated from MangaBuddy keep their old "#id" suffix
    override fun getMangaUrl(manga: SManga): String {
        val path = manga.url.substringBefore("#")
        // `path` may be relative or already absolute depending on the API response,
        // so resolve it against baseUrl instead of blindly concatenating.
        return baseUrl.toHttpUrl().resolve(path)?.toString() ?: (baseUrl + path)
    }

    // `chapter.url` may be relative or already absolute depending on the API response,
    // so resolve it against baseUrl instead of blindly concatenating.
    override fun getChapterUrl(chapter: SChapter): String = baseUrl.toHttpUrl().resolve(chapter.url)?.toString() ?: (baseUrl + chapter.url)

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val slug = url.pathSegments.first().ifEmpty { return null }
        val titleUrl = baseUrl.toHttpUrl().newBuilder().addPathSegment(slug).build()
        return fetchTitle(titleUrl.toString())?.toSManga(includeTags = includeTags)?.apply { initialized = true }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val memoId = manga.memo["id"]?.stringOrNull
        val title = if (fetchDetails || memoId == null) {
            async {
                fetchTitle(getMangaUrl(manga)) ?: throw Exception("Could not find manga details")
            }
        } else {
            null
        }
        val chapterList = if (fetchChapters) {
            async { fetchChaptersByApiId(memoId ?: title!!.await().id) }
        } else {
            null
        }

        SMangaUpdate(title?.await()?.toSManga(manga.url, includeTags) ?: manga, chapterList?.await() ?: chapters)
    }

    private suspend fun fetchTitle(url: String): InitialMangaDto? = client.get(url, Fetch.DOCUMENT)
        .extractNextJs<MangaPageDto> { it is JsonObject && "pageProps" in it }
        ?.pageProps?.initialManga

    private suspend fun fetchChaptersByApiId(id: String): List<SChapter> {
        val url = apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("titles/$id/chapters")
            // The chapter list is cached at the edge for a day
            addQueryParameter("cv", Clock.System.now().toEpochMilliseconds().toString())
        }.build()
        return client.get(url, Fetch.XHR).parseAs<ChapterListResponseDto>().data.chapters.map { it.toSChapter() }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val response = client.get(chapterUrl, Fetch.DOCUMENT)
        val dto = response.extractNextJs<ChapterPageDto> {
            it is JsonObject && "pageProps" in it
        }

        val images = dto?.pageProps?.initialChapter?.images ?: throw Exception("Could not find pages")

        return images.mapIndexed { index, url ->
            Page(index, imageUrl = url)
        }
    }

    override suspend fun fetchFilterData(): JsonElement = client.get("$apiUrl/genres", Fetch.XHR).parseAs<JsonElement>()

    override fun getFilterList(data: JsonElement?): FilterList {
        val genres = getGenreList(data, getBlacklist())

        val filters = mutableListOf<Filter<*>>(
            SortFilter(),
            ContentRatingFilter(),
            StatusFilter(),
            TypeFilter(),
            DemographicFilter(),
            FormatFilter(),
            Filter.Separator(),
            AuthorFilter(),
            MinChapterFilter(),
            MaxChapterFilter(),
            TagFilter(),
        )
        if (genres.isNotEmpty()) {
            filters.add(Filter.Separator())
            filters.add(GenreList(genres))
        }
        return FilterList(filters)
    }

    private fun String.toTagSlug() = lowercase().replace(TAG_SLUG_DROP_REGEX, "").trim().replace(WHITESPACE_REGEX, "-")

    private fun getBlacklist(): Set<String> = preferences.getStringSet(PREF_BLACKLIST_KEY, emptySet()) ?: emptySet()

    protected open val includeTags: Boolean
        get() = preferences.getBoolean(PREF_INCLUDE_TAGS_KEY, PREF_INCLUDE_TAGS_DEFAULT)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_INCLUDE_TAGS_KEY
            title = "Include tags in genre list"
            summary = "Include tags alongside genres in manga details"
            setDefaultValue(PREF_INCLUDE_TAGS_DEFAULT)
        }.let(screen::addPreference)

        val genres = getFilterList().firstInstanceOrNull<GenreList>()?.state.orEmpty()
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
        private const val PREF_INCLUDE_TAGS_KEY = "pref_include_tags"
        private const val PREF_INCLUDE_TAGS_DEFAULT = true
        private const val FALLBACK_IMAGE_HOST = "rx.rzyn.net"
        private const val DEFAULT_PAGE_LIMIT = "24"
        private const val QUERY_LENGTH_LIMIT = 50
        private const val SORT_POPULAR = "popular"
        private const val SORT_LATEST = "latest"
        private const val WINDOW_WEEK = "week"

        private val IMAGE_FALLBACK_REGEX = """rx\.qvzr[a-z]\.org""".toRegex()
        private val TAG_SLUG_DROP_REGEX = """[^a-z0-9\s-]""".toRegex()
        private val WHITESPACE_REGEX = """\s+""".toRegex()
    }
}
