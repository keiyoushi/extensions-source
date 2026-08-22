package eu.kanade.tachiyomi.extension.en.comick

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.util.Locale

@Source
abstract class Comick :
    KeiSource(),
    ConfigurableSource {

    // baseUrl, name, lang, id are injected by KSP from build.gradle.kts

    override val supportsLatest = true

    private val preferences: SharedPreferences by getPreferencesLazy()

    companion object {
        private const val LIMIT = 20
        private const val WEB_BASE_URL = "https://comick.dev"
        const val SLUG_SEARCH_PREFIX = "id:"
        private val EXTRA_KEYWORDS = listOf(
            "oneshot",
            "extra",
            "special",
            "side story",
            "sidestory",
            "announcement",
        )
    }

    override fun Headers.Builder.configureHeaders() = apply {
        set(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Mobile Safari/537.36",
        )
        set("Referer", "$WEB_BASE_URL/")
        set("Accept", "application/json")
    }

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(3)
    }

    private fun applyContentRating(builder: HttpUrl.Builder): HttpUrl.Builder {
        // Inclusive list: e.g. suggestive → safe + suggestive. null = all (pornographic).
        Preferences.contentRatings(preferences)?.forEach { rating ->
            builder.addQueryParameter("content_rating", rating)
        }
        return builder
    }

    private fun detailsPrefs(): DetailsPrefs = DetailsPrefs(
        translatedTitle = Preferences.translatedTitle(preferences),
        showAltTitles = Preferences.showAltTitles(preferences),
        tagMode = Preferences.tagMode(preferences),
        showScore = Preferences.showScore(preferences),
    )

    // ============================== Popular / Latest =========================

    private suspend fun fetchBySort(sort: String, page: Int): MangasPage {
        val url = "$baseUrl/v1.0/search".toHttpUrl().newBuilder()
            .addQueryParameter("sort", sort)
            .addQueryParameter("lang", "en")
            .addQueryParameter("limit", LIMIT.toString())
            .addQueryParameter("page", page.coerceAtLeast(1).toString())
            .addQueryParameter("tachiyomi", "true")
            .let { applyContentRating(it) }
            .build()

        val response = client.get(url, headers)
        val result = response.parseAs<List<SearchMangaDto>>()
        val prefs = detailsPrefs()
        val mangas = result.map { it.toSManga(prefs) }
        return MangasPage(mangas, hasNextPage = result.size >= LIMIT)
    }

    override suspend fun getPopularManga(page: Int): MangasPage = fetchBySort("follow", page)

    /**
     * Latest updates via search sorted by last upload.
     * Uses the same endpoint/pagination as Search + "Last updated"
     * (the /chapter feed does not paginate reliably for infinite scroll).
     */
    override suspend fun getLatestUpdates(page: Int): MangasPage = fetchBySort("uploaded", page)

    // ============================== Search ===============================

    override fun getFilterList(data: JsonElement?): FilterList = getFilters()

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        // Deep link / share: id:<hid-or-slug>
        if (query.startsWith(SLUG_SEARCH_PREFIX)) {
            val id = query.removePrefix(SLUG_SEARCH_PREFIX).trim()
            if (id.isEmpty()) return MangasPage(emptyList(), false)
            val manga = SManga.create().apply { url = "/comic/$id" }
            val details = fetchDetails(manga)
            return MangasPage(listOf(details), false)
        }

        val url = "$baseUrl/v1.0/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("limit", LIMIT.toString())
            addQueryParameter("page", page.coerceAtLeast(1).toString())
            addQueryParameter("lang", "en")
            addQueryParameter("tachiyomi", "true")

            if (query.isNotBlank()) {
                addQueryParameter("q", query)
            }

            filters.firstInstanceOrNull<SortFilter>()?.let {
                addQueryParameter("sort", it.apiValue)
            } ?: addQueryParameter("sort", "uploaded")

            filters.firstInstanceOrNull<CountryFilter>()?.apiValue?.let {
                addQueryParameter("country", it)
            }

            filters.firstInstanceOrNull<StatusFilter>()?.apiValue?.let {
                addQueryParameter("status", it.toString())
            }

            filters.firstInstanceOrNull<DemographicFilter>()?.apiValue?.let {
                addQueryParameter("demographic", it.toString())
            }

            Preferences.contentRatings(preferences)?.forEach { rating ->
                addQueryParameter("content_rating", rating)
            }

            filters.firstInstanceOrNull<GenreFilter>()?.state?.forEach { genre ->
                when (genre.state) {
                    Filter.TriState.STATE_INCLUDE -> addQueryParameter("genres", genre.slug)
                    Filter.TriState.STATE_EXCLUDE -> addQueryParameter("excludes", genre.slug)
                }
            }
        }.build()

        val response = client.get(url, headers)
        val result = response.parseAs<List<SearchMangaDto>>()
        val prefs = detailsPrefs()
        val mangas = result.map { it.toSManga(prefs) }
        return MangasPage(mangas, hasNextPage = result.size >= LIMIT)
    }

    // ============================== Details + Chapters ===================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val detailsDeferred = if (fetchDetails) {
            async { fetchDetails(manga) }
        } else {
            null
        }
        val chaptersDeferred = if (fetchChapters) {
            async { fetchChapters(manga) }
        } else {
            null
        }

        val updatedManga = detailsDeferred?.await() ?: manga
        val updatedChapters = chaptersDeferred?.await() ?: chapters
        SMangaUpdate(updatedManga, updatedChapters)
    }

    private suspend fun fetchDetails(manga: SManga): SManga {
        val path = manga.url.trimStart('/')
        val url = "$baseUrl/$path".toHttpUrl().newBuilder()
            .addQueryParameter("tachiyomi", "true")
            .build()

        val response = client.get(url, headers)
        val details = response.parseAs<ComicDetailsResponse>()
        val prefs = detailsPrefs()
        return details.toSManga(manga, prefs)
    }

    private suspend fun fetchChapters(manga: SManga): List<SChapter> {
        val id = manga.url.substringAfterLast('/').takeIf { it.isNotBlank() }
            ?: return emptyList()

        val limit = 100
        val allChapters = mutableListOf<ChapterDto>()
        var page = 1
        var total: Int? = null

        while (true) {
            val url = "$baseUrl/comic/$id/chapters".toHttpUrl().newBuilder()
                .addQueryParameter("lang", "en")
                .addQueryParameter("limit", limit.toString())
                .addQueryParameter("page", page.toString())
                .addQueryParameter("tachiyomi", "true")
                .build()

            val response = client.get(url, headers)
            val data = response.parseAs<ChaptersResponse>()
            val batch = data.chapters.orEmpty()
            if (batch.isEmpty()) break

            allChapters += batch
            total = data.total

            if (batch.size < limit) break
            if (total != null && allChapters.size >= total) break
            page++
        }

        var result = allChapters
            .map { it.toSChapter() }
            .distinctBy { it.url }

        val ignored = Preferences.ignoredGroups(preferences)
        if (ignored.isNotEmpty()) {
            result = result.filterNot { ch ->
                val scan = ch.scanlator.orEmpty()
                ignored.any { g -> scan.contains(g, ignoreCase = true) }
            }
        }

        if (Preferences.hideExtras(preferences)) {
            result = result.filterNot { isHiddenExtra(it) }
        }

        val preferred = Preferences.preferredGroups(preferences)
        val sortChapters = Preferences.sortChapters(preferences)
        if (preferred.isNotEmpty() || sortChapters) {
            val comparator = compareBy<SChapter> { ch ->
                val scan = ch.scanlator.orEmpty()
                if (preferred.isNotEmpty() && preferred.any { g -> scan.contains(g, ignoreCase = true) }) 0 else 1
            }.let { base ->
                if (sortChapters) {
                    base.thenBy { it.chapter_number <= 0f }
                        .thenBy { it.chapter_number.coerceAtLeast(0f) }
                } else {
                    base.thenByDescending { it.chapter_number }
                }
            }.thenBy { it.name }
            result = result.sortedWith(comparator)
        }

        return result
    }

    /** Ch. 0 / unnumbered specials that "Hide extras / announcements" should remove. */
    private fun isHiddenExtra(ch: SChapter): Boolean {
        if (ch.chapter_number == 0f) return true
        if (ch.chapter_number != -1f) return false
        val name = ch.name.lowercase(Locale.ROOT)
        return EXTRA_KEYWORDS.any { name.contains(it) }
    }

    // ============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val hid = chapter.url.substringAfterLast('/').takeIf { it.isNotBlank() }
            ?: return emptyList()

        // Tracker: api.comick.dev serves no page images — return empty list
        // without an extra fallback request. News / announcement chapters
        // may carry a single md_image; handle both shapes in one request.
        val url = "$baseUrl/chapter/$hid".toHttpUrl().newBuilder().build()
        val response = client.get(url, headers)
        val data = response.parseAs<ChapterPagesResponse>()
        val images = data.chapter?.images
            ?: data.chapter?.mdImages
            ?: emptyList()

        return images.mapIndexed { index, img ->
            val imageUrl = when {
                !img.url.isNullOrBlank() -> img.url
                else -> ImageResolver.resolve(img.b2key)
            }
            Page(index, imageUrl = imageUrl)
        }.filter { !it.imageUrl.isNullOrBlank() }
    }

    // ============================== WebView URLs =========================

    override fun getHomeUrl(): String = WEB_BASE_URL

    override fun getMangaUrl(manga: SManga): String {
        val id = manga.url.substringAfterLast('/').ifBlank { return getHomeUrl() }
        return "$WEB_BASE_URL/comic/$id"
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val hid = chapter.url.substringAfterLast('/').ifBlank { return getHomeUrl() }
        return "$WEB_BASE_URL/chapter/$hid"
    }

    // ============================== Preferences ==========================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        Preferences.setupPreferenceScreen(screen)
    }
}

// ============================== Filters ================================

internal class SortFilter :
    Filter.Select<String>(
        "Sort",
        arrayOf(
            "Last updated",
            "Most followed",
            "Most viewed",
            "Highest rating",
            "Newest",
        ),
        0,
    ) {
    val apiValue: String
        get() = when (state) {
            0 -> "uploaded"
            1 -> "follow"
            2 -> "view"
            3 -> "rating"
            4 -> "created_at"
            else -> "uploaded"
        }
}

internal class CountryFilter :
    Filter.Select<String>(
        "Origin",
        arrayOf("All", "Japan (Manga)", "Korea (Manhwa)", "China (Manhua)"),
        0,
    ) {
    val apiValue: String?
        get() = when (state) {
            1 -> "jp"
            2 -> "kr"
            3 -> "cn"
            else -> null
        }
}

internal class StatusFilter :
    Filter.Select<String>(
        "Status",
        arrayOf("All", "Ongoing", "Completed", "Cancelled", "Hiatus"),
        0,
    ) {
    val apiValue: Int?
        get() = when (state) {
            1 -> 1
            2 -> 2
            3 -> 3
            4 -> 4
            else -> null
        }
}

internal class DemographicFilter :
    Filter.Select<String>(
        "Demographic",
        arrayOf("All", "Shounen", "Shoujo", "Seinen", "Josei", "None"),
        0,
    ) {
    val apiValue: Int?
        get() = when (state) {
            1 -> 1
            2 -> 2
            3 -> 3
            4 -> 4
            5 -> 5
            else -> null
        }
}

internal class Genre(name: String, val slug: String) : Filter.TriState(name)

internal class GenreFilter(genres: List<Genre>) : Filter.Group<Genre>("Genres", genres)

/** Common Comick genres (slug = API value). */
internal fun defaultGenres() = listOf(
    Genre("Action", "action"),
    Genre("Adventure", "adventure"),
    Genre("Animals", "animals"),
    Genre("Comedy", "comedy"),
    Genre("Cooking", "cooking"),
    Genre("Crime", "crime"),
    Genre("Demons", "demons"),
    Genre("Drama", "drama"),
    Genre("Ecchi", "ecchi"),
    Genre("Fantasy", "fantasy"),
    Genre("Gender Bender", "gender-bender"),
    Genre("Gore", "gore"),
    Genre("Harem", "harem"),
    Genre("Historical", "historical"),
    Genre("Horror", "horror"),
    Genre("Isekai", "isekai"),
    Genre("Magic", "magic"),
    Genre("Martial Arts", "martial-arts"),
    Genre("Mecha", "mecha"),
    Genre("Military", "military"),
    Genre("Monster Girls", "monster-girls"),
    Genre("Monsters", "monsters"),
    Genre("Music", "music"),
    Genre("Mystery", "mystery"),
    Genre("Police", "police"),
    Genre("Post-Apocalyptic", "post-apocalyptic"),
    Genre("Psychological", "psychological"),
    Genre("Reincarnation", "reincarnation"),
    Genre("Reverse Harem", "reverse-harem"),
    Genre("Romance", "romance"),
    Genre("School Life", "school-life"),
    Genre("Sci-Fi", "sci-fi"),
    Genre("Slice of Life", "slice-of-life"),
    Genre("Smut", "smut"),
    Genre("Sports", "sports"),
    Genre("Superhero", "superhero"),
    Genre("Supernatural", "supernatural"),
    Genre("Survival", "survival"),
    Genre("Thriller", "thriller"),
    Genre("Time Travel", "time-travel"),
    Genre("Tragedy", "tragedy"),
    Genre("Vampires", "vampires"),
    Genre("Video Games", "video-games"),
    Genre("Villainess", "villainess"),
    Genre("Virtual Reality", "virtual-reality"),
    Genre("Yaoi", "yaoi"),
    Genre("Yuri", "yuri"),
    Genre("Zombies", "zombies"),
)

internal fun getFilters() = FilterList(
    SortFilter(),
    CountryFilter(),
    StatusFilter(),
    DemographicFilter(),
    Filter.Separator(),
    GenreFilter(defaultGenres()),
)
