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
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
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

    private val supabaseHeaders by lazy {
        headersBuilder()
            .add("apikey", supabaseApiKey)
            .add("authorization", "Bearer $supabaseApiKey")
            .add("Accept", "application/json")
            .build()
    }

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val offset = (page - 1) * 24
        val url = "$supabaseUrl/series".toHttpUrl().newBuilder()
            .addQueryParameter("select", "title,slug,cover_url")
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
            .addQueryParameter("select", "title,slug,cover_url")
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
            url.addQueryParameter("select", "title,slug,cover_url,genres!inner(slug)")
            url.addQueryParameter("genres.slug", "eq.$genreSlug")
        } else {
            url.addQueryParameter("select", "title,slug,cover_url")
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
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments[0] != "series") {
            return null
        }

        val manga = SManga.create().apply {
            this.url = "/series/${url.pathSegments[1]}"
        }

        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false)
            .manga
            .apply {
                initialized = true
            }
    }

    // ============================= Utilities =============================

    private fun Response.extractSeriesPayload(): SeriesPayload = extractNextJs<SeriesPayload> {
        it is JsonObject && "initialSeries" in it && "initialChapters" in it
    } ?: throw Exception("Failed to extract series payload")

    // ============================== Updates ==============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val payload = client.get(baseUrl + manga.url).extractSeriesPayload()

        val newManga = payload.initialSeries.toSManga(baseUrl, payload.initialGenres)

        val seriesSlug = payload.initialSeries.slug
        val showPremium = preferences.getBoolean(PREF_SHOW_PREMIUM, false)

        val chapters = payload.initialChapters ?: emptyList()
        val filteredChapters = if (showPremium) {
            chapters
        } else {
            chapters.filterNot { it.isPremium() }
        }

        return SMangaUpdate(
            manga = newManga,
            chapters = filteredChapters.map { it.toSChapter(seriesSlug) },
        )
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val payload = client.get(baseUrl + chapter.url).extractNextJs<PagesPayload> {
            it is JsonObject && "initialPages" in it
        } ?: throw Exception("Failed to extract pages payload")

        return payload.initialPages.mapIndexed { i, page ->
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
