package eu.kanade.tachiyomi.extension.en.webdexscans

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
import keiyoushi.source.KeiSource
import keiyoushi.utils.boolean
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response

@Source
abstract class WebdexScans :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    private val supabaseUrl = "https://nrqghtbdrdnoywxjkgkf.supabase.co/rest/v1"
    private val supabaseApiKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Im5ycWdodGJkcmRub3l3eGprZ2tmIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzY4Njg4NDEsImV4cCI6MjA5MjQ0NDg0MX0.Gnrn33_LMxFA9m_OdCpybBZ-Cjcc5rdsJlD8Y9eOICg"

    private val supabaseHeaders: Headers
        get() = headersBuilder()
            .add("apikey", supabaseApiKey)
            .add("authorization", "Bearer $supabaseApiKey")
            .add("Accept", "application/json")
            .build()

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val offset = (page - 1) * 24
        val url = "$supabaseUrl/series".toHttpUrl().newBuilder()
            .addQueryParameter("select", "id,title,slug,cover_url")
            .addQueryParameter("order", "view_count.desc")
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("limit", "24")
            .build()

        return mangaListParse(client.get(url, supabaseHeaders))
    }

    private fun mangaListParse(response: Response): MangasPage {
        val mangaList = response.parseAs<List<SearchSeriesDto>>().map { it.toSManga(baseUrl) }
        return MangasPage(mangaList, mangaList.size == 24)
    }

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val offset = (page - 1) * 24
        val url = "$supabaseUrl/series".toHttpUrl().newBuilder()
            .addQueryParameter("select", "id,title,slug,cover_url")
            .addQueryParameter("order", "updated_at.desc")
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("limit", "24")
            .build()

        return mangaListParse(client.get(url, supabaseHeaders))
    }

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val offset = (page - 1) * 24
        val url = "$supabaseUrl/series".toHttpUrl().newBuilder()

        val genreSlug = filters.firstInstanceOrNull<GenreFilter>()?.selected
        if (genreSlug != null) {
            url.addQueryParameter("select", "id,title,slug,cover_url,genres!inner(slug)")
            url.addQueryParameter("genres.slug", "eq.$genreSlug")
        } else {
            url.addQueryParameter("select", "id,title,slug,cover_url")
        }

        if (query.isNotEmpty()) {
            url.addQueryParameter("title", "ilike.%$query%")
        }

        filters.firstInstanceOrNull<TypeFilter>()?.selected?.let {
            url.addQueryParameter("type", "eq.$it")
        }

        filters.firstInstanceOrNull<StatusFilter>()?.selected?.let {
            url.addQueryParameter("status", "eq.$it")
        }

        when (filters.firstInstanceOrNull<SortFilter>()?.selected) {
            "popular" -> url.addQueryParameter("order", "view_count.desc")
            "rating" -> url.addQueryParameter("order", "rating.desc")
            "a-z" -> url.addQueryParameter("order", "title.asc")
            "latest", null -> url.addQueryParameter("order", "updated_at.desc")
        }

        url.addQueryParameter("offset", offset.toString())
        url.addQueryParameter("limit", "24")

        return mangaListParse(client.get(url.build(), supabaseHeaders))
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.getOrNull(0) != "series") {
            return null
        }

        val slug = url.pathSegments.getOrNull(1) ?: return null
        val apiUrl = "$supabaseUrl/series".toHttpUrl().newBuilder()
            .addQueryParameter("slug", "eq.$slug")
            .addQueryParameter("select", "*,genres(name)")
            .build()

        val series = client.get(apiUrl, supabaseHeaders).parseAs<List<SeriesInfo>>().firstOrNull() ?: return null
        return series.toSManga(baseUrl)
    }

    // ============================= Utilities =============================

    override fun getMangaUrl(manga: SManga): String {
        val slug = manga.memo["slug"]?.string
            ?: throw Exception("Series slug missing (try refreshing)")
        return "$baseUrl/series/$slug"
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val seriesSlug = chapter.memo["seriesSlug"]?.string
            ?: throw Exception("Chapter missing series slug (try refreshing)")
        val chapterSlug = chapter.memo["slug"]?.string
            ?: throw Exception("Chapter slug missing (try refreshing)")
        return "$baseUrl/series/$seriesSlug/$chapterSlug"
    }

    // ============================== Updates ==============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val seriesDeferred = if (fetchDetails) {
            async {
                val url = "$supabaseUrl/series".toHttpUrl().newBuilder()
                    .addQueryParameter("id", "eq.${manga.url}")
                    .addQueryParameter("select", "*,genres(name)")
                    .build()
                client.get(url, supabaseHeaders).parseAs<List<SeriesInfo>>().firstOrNull()
            }
        } else {
            null
        }

        val chaptersDeferred = if (fetchChapters) {
            async {
                val url = "$supabaseUrl/chapters".toHttpUrl().newBuilder()
                    .addQueryParameter("series_id", "eq.${manga.url}")
                    .addQueryParameter("select", "id,chapter_number,title,slug,created_at,is_premium,free_at,series(slug)")
                    .addQueryParameter("order", "chapter_number.desc")
                    .build()
                client.get(url, supabaseHeaders).parseAs<List<ChapterInfo>>()
            }
        } else {
            null
        }

        val series = seriesDeferred?.await()
        val chapterInfos = chaptersDeferred?.await()

        val seriesSlug = series?.slug
            ?: chapterInfos?.firstOrNull()?.seriesSlug
            ?: manga.memo["slug"]?.string
            ?: throw Exception("Failed to resolve series slug (try refreshing)")

        val newManga = (series?.toSManga(baseUrl) ?: manga).apply {
            updateSeriesSlug(seriesSlug)
        }

        val updatedChapters = if (fetchChapters && chapterInfos != null) {
            val showPremium = preferences.getBoolean(PREF_SHOW_PREMIUM, false)
            val filtered = if (showPremium) {
                chapterInfos
            } else {
                chapterInfos.filterNot { it.isPremium() }
            }
            filtered.map { it.toSChapter(seriesSlug) }
        } else {
            chapters
        }

        SMangaUpdate(
            manga = newManga,
            chapters = updatedChapters,
        )
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val isLocked = chapter.memo["isLocked"]?.boolean == true
        if (isLocked) {
            return emptyList()
        }

        val url = "$supabaseUrl/pages".toHttpUrl().newBuilder()
            .addQueryParameter("chapter_id", "eq.${chapter.url}")
            .addQueryParameter("select", "image_url,page_number")
            .addQueryParameter("order", "page_number.asc")
            .build()

        val pages = client.get(url, supabaseHeaders).parseAs<List<PageInfo>>()
        return pages.mapIndexed { i, page ->
            Page(i, imageUrl = page.imageUrl.toAbsoluteUrl(baseUrl))
        }
    }

    // ============================== Filters ==============================

    override fun getFilterList(data: JsonElement?) = FilterList(
        GenreFilter(),
        TypeFilter(),
        StatusFilter(),
        SortFilter(),
    )

    // ============================ Preferences ============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        CheckBoxPreference(screen.context).apply {
            key = PREF_SHOW_PREMIUM
            title = "Show premium chapters"
            summary = "Include chapters that require coins to read"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val PREF_SHOW_PREMIUM = "pref_show_premium"
    }
}
