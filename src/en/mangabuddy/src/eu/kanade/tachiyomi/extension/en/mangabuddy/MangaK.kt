package eu.kanade.tachiyomi.extension.en.mangabuddy

import android.content.SharedPreferences
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservable
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Source
abstract class MangaK :
    HttpSource(),
    ConfigurableSource {

    override val supportsLatest = true

    private val apiUrl = "https://api.mangak.io"

    private val preferences: SharedPreferences by getPreferencesLazy()

    override fun headersBuilder() = super.headersBuilder()
        .add("Origin", baseUrl)
        .add("Referer", "$baseUrl/")

    // Catches 5xx errors from the primary CDN and falls back to the working domain
    private val imageFallbackInterceptor = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)

        if (!response.isSuccessful && request.url.host.matches(IMAGE_FALLBACK_REGEX)) {
            response.close()
            val newUrl = request.url.newBuilder().host("rx.rzyn.net").build()
            return@Interceptor chain.proceed(request.newBuilder().url(newUrl).build())
        }

        response
    }

    override val client = network.client.newBuilder()
        .addInterceptor(imageFallbackInterceptor)
        .build()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/titles/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("sort", "popular")
            addQueryParameter("window", "week")
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", "24")

            val blacklist = getBlacklist()
            if (blacklist.isNotEmpty()) {
                addQueryParameter("exclude", blacklist.joinToString(","))
            }
        }.build()

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<SearchResponseDto>()
        return MangasPage(dto.items.map { it.toSManga() }, dto.hasNext)
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/titles/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("sort", "latest")
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", "24")

            val blacklist = getBlacklist()
            if (blacklist.isNotEmpty()) {
                addQueryParameter("exclude", blacklist.joinToString(","))
            }
        }.build()

        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/titles/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", "24")

            if (query.isNotBlank()) {
                val filteredQuery = query.filter { it.isLetterOrDigit() || it == ' ' }.trim().take(50)
                addQueryParameter("q", filteredQuery)
            }

            val includedGenres = mutableListOf<String>()
            val excludedGenres = mutableListOf<String>() // Populated directly by the filter object

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
                    is SortFilter -> {
                        if (filter.selected.isNotBlank()) {
                            addQueryParameter("sort", filter.selected)
                        }
                    }
                    is ContentRatingFilter -> {
                        if (filter.selected.isNotBlank()) {
                            addQueryParameter("content_rating", filter.selected)
                        }
                    }
                    is StatusFilter -> {
                        if (filter.selected.isNotBlank()) {
                            addQueryParameter("status", filter.selected)
                        }
                    }
                    is TypeFilter -> {
                        if (filter.selected.isNotBlank()) {
                            addQueryParameter("type", filter.selected)
                        }
                    }
                    is DemographicFilter -> {
                        if (filter.selected.isNotBlank()) {
                            addQueryParameter("demographic", filter.selected)
                        }
                    }
                    else -> {}
                }
            }

            filters.firstInstanceOrNull<AuthorFilter>()?.state?.takeIf { it.isNotBlank() }?.let {
                addQueryParameter("author", it)
            }
            filters.firstInstanceOrNull<MinChapterFilter>()?.state?.takeIf { it.isNotBlank() }?.let {
                addQueryParameter("min_ch", it)
            }

            if (includedGenres.isNotEmpty()) {
                addQueryParameter("genres", includedGenres.joinToString(","))
            }
            if (excludedGenres.isNotEmpty()) {
                addQueryParameter("exclude", excludedGenres.joinToString(","))
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // ============================== Details ==============================

    override fun getMangaUrl(manga: SManga): String {
        val path = manga.url.substringBefore("#")
        // `path` may be relative or already absolute depending on the API response,
        // so resolve it against baseUrl instead of blindly concatenating.
        return baseUrl.toHttpUrl().resolve(path)?.toString() ?: (baseUrl + path)
    }

    override fun mangaDetailsRequest(manga: SManga): Request = GET(getMangaUrl(manga), headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val dto = response.extractNextJs<NextJsDto> {
            it is JsonObject && "pageProps" in it
        }

        return dto?.pageProps?.initialManga?.toSManga()
            ?: throw Exception("Could not find manga details")
    }

    // ============================= Chapters ==============================

    // `chapter.url` may be relative or already absolute depending on the API response,
    // so resolve it against baseUrl instead of blindly concatenating.
    override fun getChapterUrl(chapter: SChapter): String = baseUrl.toHttpUrl().resolve(chapter.url)?.toString() ?: (baseUrl + chapter.url)

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        if (!manga.url.contains("#")) {
            val idFromDesc = manga.description
                ?.substringAfterLast("Manga ID: ", "")
                ?.substringBefore("\n")
                ?.trim()

            // If the ID was previously saved into the description, bypass the details request
            if (!idFromDesc.isNullOrEmpty()) {
                val request = GET("$apiUrl/titles/$idFromDesc/chapters?cv=${System.currentTimeMillis()}", headers)
                return client.newCall(request).asObservable().map { chapterListParse(it) }
            }

            // Fallback for uninitialized manga
            return client.newCall(mangaDetailsRequest(manga)).asObservable().map { response ->
                val dto = response.extractNextJs<NextJsDto> {
                    it is JsonObject && "pageProps" in it
                }

                val id = dto?.pageProps?.initialManga?.id
                    ?: throw Exception("Could not find manga ID for migration")

                val request = GET("$apiUrl/titles/$id/chapters?cv=${System.currentTimeMillis()}", headers)
                client.newCall(request).execute().let { chapterListParse(it) }
            }
        }

        return super.fetchChapterList(manga)
    }

    override fun chapterListRequest(manga: SManga): Request {
        val id = manga.url.substringAfterLast("#")
        return GET("$apiUrl/titles/$id/chapters?cv=${System.currentTimeMillis()}", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = response.parseAs<ChapterListResponseDto>().chapters

        // The website uses the API's `chapter_number` field as an internal sorting index,
        // not the actual chapter number. This dictates the site's canonical reading order,
        // allowing them to correctly interleave volume extras and side stories between main chapters.
        // Because we rely on this index for sorting, it triggers Mihon's "Missing Chapters"
        // warning whenever the site's custom order jumps around numerically.
        //
        // TODO: Investigate a better way to handle chapter sorting and numbering to maintain
        // the site's chronological reading order without causing sequence warnings in the app.
        return chapters.sortedByDescending { it.chapterNumber ?: 0f }
            .map { it.toSChapter(dateFormat) }
    }

    // =============================== Pages ===============================

    override fun pageListRequest(chapter: SChapter): Request = GET(getChapterUrl(chapter), headers)

    override fun pageListParse(response: Response): List<Page> {
        val dto = response.extractNextJs<NextJsDto> {
            it is JsonObject && "pageProps" in it
        }

        val images = dto?.pageProps?.initialChapter?.images
            ?: throw Exception("Could not find chapter images")

        return images.mapIndexed { index, url ->
            Page(index, imageUrl = url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ==============================

    override fun getFilterList(): FilterList {
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
        private val IMAGE_FALLBACK_REGEX = "rx\\.qvzr[a-z]\\.org".toRegex()
    }
}
