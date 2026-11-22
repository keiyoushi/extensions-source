package eu.kanade.tachiyomi.multisrc.mangak

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.preference.ListPreference
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
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.source.SMangaUpdate
import keiyoushi.utils.applicationContext
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.stringOrNull
import kotlinx.coroutines.CancellationException
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
    override val supportsRelatedMangas = true
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
            addQueryParameter("sort", popularSort)
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", DEFAULT_PAGE_LIMIT)
            addExcludedGenres()
        }.build()

        val data = client.get(url, Fetch.XHR).parseAs<DataDto<SearchDataDto>>().data
        return MangasPage(data.items.map { it.toSManga() }, data.pagination.hasNext)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$apiUrl/titles/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("sort", SORT_LATEST)
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", DEFAULT_PAGE_LIMIT)
            addExcludedGenres()
        }.build()

        val data = client.get(url, Fetch.XHR).parseAs<DataDto<SearchDataDto>>().data
        return MangasPage(data.items.map { it.toSManga() }, data.pagination.hasNext)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val minChapters = chapterBound(
            filters.filterIsInstance<MinChapterFilter>().firstOrNull()?.state.orEmpty(),
            "Min",
        )
        val maxChapters = chapterBound(
            filters.filterIsInstance<MaxChapterFilter>().firstOrNull()?.state.orEmpty(),
            "Max",
        )
        if (minChapters != null && maxChapters != null && minChapters > maxChapters) {
            failWith(
                message = "Min Chapters ($minChapters) is higher than Max Chapters ($maxChapters), " +
                    "which would match nothing. Lower the minimum or raise the maximum.",
                toastText = "Min Chapters is higher than Max Chapters.",
            )
        }

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
            val excludedGenres = getExcludedGenres().toMutableSet()

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
                    is TagFilter -> if (filter.state.isNotBlank()) addQueryParameter("tag", filter.state.toSlug())
                    is AuthorFilter -> if (filter.state.isNotBlank()) addQueryParameter("author", filter.state.toSlug())
                    is MinChapterFilter, is MaxChapterFilter -> {}
                    else -> {}
                }
            }

            if (minChapters != null) addQueryParameter("min_ch", minChapters.toString())
            if (maxChapters != null) addQueryParameter("max_ch", maxChapters.toString())

            if (includedGenres.isNotEmpty()) addQueryParameter("genres", includedGenres.joinToString(","))
            if (excludedGenres.isNotEmpty()) addQueryParameter("exclude", excludedGenres.joinToString(","))
        }.build()

        val data = client.get(url, Fetch.XHR).parseAs<DataDto<SearchDataDto>>().data
        if (page == 1 && data.items.isEmpty()) {
            maybeSuggest(query, filters)
        }
        return MangasPage(data.items.map { it.toSManga() }, data.pagination.hasNext)
    }

    // Entries migrated from MangaBuddy keep their old "#id" suffix
    override fun getMangaUrl(manga: SManga): String {
        val path = manga.url.substringBefore("#")
        return baseUrl.toHttpUrl().resolve(path)?.toString() ?: (baseUrl + path)
    }

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
            async { fetchChaptersByApiId(memoId ?: title!!.await().id, manga) }
        } else {
            null
        }

        SMangaUpdate(title?.await()?.toSManga(manga.url, includeTags) ?: manga, chapterList?.await() ?: chapters)
    }

    private suspend fun fetchTitle(url: String): InitialMangaDto? = client.get(url, Fetch.DOCUMENT)
        .extractNextJs<MangaPageDto> { it is JsonObject && "pageProps" in it }
        ?.pageProps?.initialManga

    private suspend fun fetchChaptersByApiId(id: String, manga: SManga): List<SChapter> = try {
        val url = apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("titles/$id/chapters")
            // The chapter list is cached at the edge for a day
            addQueryParameter("cv", Clock.System.now().toEpochMilliseconds().toString())
        }.build()
        client.get(url, Fetch.XHR).parseAs<DataDto<ChapterDataDto>>().data.chapters.map { it.toSChapter() }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        val fallback = fetchTitle(getMangaUrl(manga))?.chapters.orEmpty().map { it.toSChapter() }
        if (fallback.isEmpty()) throw e
        fallback
    }

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val id = manga.memo["id"]?.stringOrNull
            ?: fetchTitle(getMangaUrl(manga))?.id
            ?: throw Exception("Could not find manga details")
        val url = "$apiUrl/recommendations".toHttpUrl().newBuilder().apply {
            addPathSegment(id)
            addExcludedGenres()
        }.build()

        return client.get(url, Fetch.XHR).parseAs<DataDto<List<MangaItemDto>>>().data.map { it.toSManga() }
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
        val genres = getGenreList(data, getExcludedGenres())

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

    /**
     * The site's author and tag params take a lowercase slug. A space or uppercase in
     * either makes the backend ignore the filter and return the whole catalogue.
     */
    private fun String.toSlug() = lowercase().replace(SLUG_DROP_REGEX, "").trim().replace(WHITESPACE_REGEX, "-")

    private fun getExcludedGenres(): Set<String> = preferences.getStringSet(PREF_EXCLUDED_GENRES_KEY, emptySet()) ?: emptySet()

    private fun HttpUrl.Builder.addExcludedGenres() {
        val excludedGenres = getExcludedGenres()
        if (excludedGenres.isNotEmpty()) {
            addQueryParameter("exclude", excludedGenres.joinToString(","))
        }
    }

    protected open val includeTags: Boolean
        get() = preferences.getBoolean(PREF_INCLUDE_TAGS_KEY, PREF_INCLUDE_TAGS_DEFAULT)

    private val popularSort: String
        get() = preferences.getString(PREF_POPULAR_SORT, POPULAR_SORT_DEFAULT) ?: POPULAR_SORT_DEFAULT

    private val suggestions by lazy {
        MangaKSuggestions(client, headers, baseUrl, apiUrl, id)
    }

    private suspend fun maybeSuggest(query: String, filters: FilterList) {
        val author = filters.filterIsInstance<AuthorFilter>().firstOrNull()?.state?.trim().orEmpty()
        val tag = filters.filterIsInstance<TagFilter>().firstOrNull()?.state?.trim().orEmpty()

        val (input, types, field) = when {
            query.isNotBlank() -> Triple(query, ALL_SUGGESTION_TYPES, null)
            author.isNotBlank() -> Triple(author, setOf(MangaKSuggestions.Type.AUTHOR), "author")
            tag.isNotBlank() -> Triple(tag, setOf(MangaKSuggestions.Type.TAG), "tag")
            else -> return
        }

        val matches = suggestions.suggest(input, types)
        if (matches.isEmpty()) return

        val labels = if (field != null) {
            matches.joinToString { it.display }
        } else {
            matches.joinToString { "${it.type.label} \"${it.display}\"" }
        }
        val prefix = if (field != null) {
            "No results for $field \"$input\"."
        } else {
            "No results for \"$input\"."
        }
        failWith("$prefix Did you mean: $labels?")
    }

    private fun failWith(message: String, toastText: String = message): Nothing {
        toast(toastText)
        throw Exception(message)
    }

    /**
     * Min/Max Chapters are the series' total chapter count. The API answers 400 for
     * anything but a non-negative integer, so reject it here with something readable.
     */
    private fun chapterBound(raw: String, label: String): Int? {
        val value = raw.trim()
        if (value.isEmpty()) return null

        val parsed = value.toIntOrNull()
        if (parsed == null || parsed < 0) {
            failWith(
                message = "\"$value\" is not a valid $label Chapters value. This filter takes the " +
                    "series' total chapter count as a whole, non-negative number (e.g. 100); " +
                    "leave the field empty for no limit.",
                toastText = "$label Chapters: \"$value\" is not a number.",
            )
        }
        return parsed
    }

    private fun toast(message: String) {
        runCatching {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = PREF_POPULAR_SORT
            title = "Popular ranking"
            summary = "%s"
            entries = RANKING_LABELS
            entryValues = RANKING_VALUES
            setDefaultValue(POPULAR_SORT_DEFAULT)
        }.let(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_INCLUDE_TAGS_KEY
            title = "Include tags in genre list"
            summary = "Include tags alongside genres in manga details"
            setDefaultValue(PREF_INCLUDE_TAGS_DEFAULT)
        }.let(screen::addPreference)

        val genres = getFilterList().firstInstanceOrNull<GenreList>()?.state.orEmpty()
        MultiSelectListPreference(screen.context).apply {
            key = PREF_EXCLUDED_GENRES_KEY
            title = "Excluded genres"
            summary = if (genres.isEmpty()) {
                "Open search filters in the browse screen once to load and sync genres."
            } else {
                "Select genres to always exclude from search and browse results."
            }
            entries = genres.map { it.name }.toTypedArray()
            entryValues = genres.map { it.value }.toTypedArray()
            setDefaultValue(emptySet<String>())
        }.let(screen::addPreference)
    }

    companion object {
        private const val PREF_EXCLUDED_GENRES_KEY = "pref_blacklist"
        private const val PREF_POPULAR_SORT = "pref_popular_sort"
        private const val POPULAR_SORT_DEFAULT = "views_today"
        private val RANKING_LABELS = arrayOf(
            "Top Day",
            "Top Week",
            "Top Month",
            "Most Views",
            "Most Bookmarks",
            "Most Discussed",
            "Most Reviews",
        )
        private val RANKING_VALUES = arrayOf(
            "views_today",
            "views_7days",
            "views_30days",
            "views",
            "bookmarks",
            "comments",
            "reviews",
        )

        // Tags are only searched from the Tag filter: the sites carry ~9.5k, so a free-text
        // query would either match the wrong kind of term or pay for a full listing.
        private val ALL_SUGGESTION_TYPES = setOf(
            MangaKSuggestions.Type.AUTHOR,
            MangaKSuggestions.Type.TITLE,
            MangaKSuggestions.Type.GENRE,
        )
        private const val PREF_INCLUDE_TAGS_KEY = "pref_include_tags"
        private const val PREF_INCLUDE_TAGS_DEFAULT = true
        private const val FALLBACK_IMAGE_HOST = "rx.rzyn.net"
        private const val DEFAULT_PAGE_LIMIT = "24"
        private const val QUERY_LENGTH_LIMIT = 50
        private const val SORT_LATEST = "latest"

        private val IMAGE_FALLBACK_REGEX = """rx\.qvzr[a-z]\.org""".toRegex()
        private val SLUG_DROP_REGEX = """[^a-z0-9\s-]""".toRegex()
        private val WHITESPACE_REGEX = """\s+""".toRegex()
    }
}
