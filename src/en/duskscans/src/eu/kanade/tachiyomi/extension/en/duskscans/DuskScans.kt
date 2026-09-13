package eu.kanade.tachiyomi.extension.en.duskscans

import androidx.preference.CheckBoxPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
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
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getString
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Source
abstract class DuskScans :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    private val apiUrl get() = "$baseUrl/api"

    private val baseUrlHost get() = baseUrl.toHttpUrl().host

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(3) { it.host == baseUrlHost }
    }

    private suspend fun getCatalog(): List<MangaDto> = client.get("$apiUrl/manga").parseAs<List<MangaDto>>()

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val mangas = getCatalog().sortedByDescending { it.views }
        return MangasPage(mangas.map { it.toSManga() }, false)
    }

    // ============================== Latest ================================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val mangas = getCatalog() // already ordered by newest chapter release date
        return MangasPage(mangas.map { it.toSManga() }, false)
    }

    // ============================== Search ================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val mangas = getCatalog()

        val status = filters.firstInstanceOrNull<StatusFilter>()?.checked.orEmpty()
        val type = filters.firstInstanceOrNull<TypeFilter>()?.checked.orEmpty()
        val genreFilter = filters.firstInstanceOrNull<GenreFilter>()
        val included = genreFilter?.included.orEmpty()
        val excluded = genreFilter?.excluded.orEmpty()

        val filtered = mangas.filter {
            (query.isBlank() || it.matchesQuery(query)) &&
                (status.isEmpty() || it.status in status) &&
                (type.isEmpty() || it.type in type) &&
                it.genres.containsAll(included) &&
                it.genres.none { genre -> genre in excluded }
        }

        val sorted = when (filters.firstInstanceOrNull<SortFilter>()?.state ?: 0) {
            1 -> filtered.sortedByDescending { it.createdAt }
            2 -> filtered.sortedByDescending { it.views }
            3 -> filtered.sortedByDescending { it.rating }
            4 -> filtered.sortedBy { it.title }
            else -> filtered
        }

        return MangasPage(sorted.map { it.toSManga() }, false)
    }

    // ============================== Details ================================

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}"

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrlHost || url.pathSegments[0] != "series") return null
        val slug = url.pathSegments.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return null
        return getSeriesPage(slug)?.manga?.toSManga()
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val series = getSeriesPage(manga.url)
            ?: throw Exception("Series not found")

        val showPremium = preferences.getBoolean(PREF_SHOW_PREMIUM, PREF_SHOW_PREMIUM_DEFAULT)
        return SMangaUpdate(
            series.manga.toSManga(),
            series.chapters
                .filter { showPremium || !it.isLocked }
                .map { it.toSChapter(manga.url) },
        )
    }

    // get both manga details and chapters (already ordered newest first)
    private suspend fun getSeriesPage(slug: String): SeriesPageDto? {
        val rscHeaders = headersBuilder().add("RSC", "1").build()
        return client.get("$baseUrl/series/$slug", rscHeaders).extractNextJs()
    }

    // ============================== Chapters ==============================

    override fun getChapterUrl(chapter: SChapter): String {
        val slug = chapter.memo.getString("slug")
        val number = chapter.memo.getString("number")
        return "$baseUrl/series/$slug/chapter-$number"
    }

    // ============================== Pages ==================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get("$apiUrl/chapter/${chapter.url}")
        return response.parseAs<ChapterDetailDto>().pageUrls.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    // ============================== Filters ================================

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement {
        val catalog = getCatalog()
        return FilterDataDto(
            genres = catalog.flatMap { it.genres }.distinct().sorted(),
            statuses = catalog.mapNotNull { it.status }.distinct().sorted(),
            types = catalog.mapNotNull { it.type }.distinct().sorted(),
        ).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filterData = data?.parseAs<FilterDataDto>()
        return FilterList(
            buildList {
                add(SortFilter())
                filterData?.statuses?.takeIf { it.isNotEmpty() }?.let { add(StatusFilter(it)) }
                filterData?.types?.takeIf { it.isNotEmpty() }?.let { add(TypeFilter(it)) }
                filterData?.genres?.takeIf { it.isNotEmpty() }?.let { add(GenreFilter(it)) }
            },
        )
    }

    // ============================ Preferences ============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        CheckBoxPreference(screen.context).apply {
            key = PREF_SHOW_PREMIUM
            title = "Show premium chapters"
            summary = "Include chapters that require payment or login to read"
            setDefaultValue(PREF_SHOW_PREMIUM_DEFAULT)
        }.also(screen::addPreference)
    }

    companion object {
        private const val PREF_SHOW_PREMIUM = "pref_show_premium"
        private const val PREF_SHOW_PREMIUM_DEFAULT = false
    }
}
