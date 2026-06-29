package eu.kanade.tachiyomi.extension.en.webdexscans

import androidx.preference.CheckBoxPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
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
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Source
abstract class WebdexScans :
    HttpSource(),
    ConfigurableSource {

    override val supportsLatest = true

    // Hardcode versionId to force users to migrate their old Madara entries.

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

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request {
        val offset = (page - 1) * 24
        val url = "$supabaseUrl/series".toHttpUrl().newBuilder()
            .addQueryParameter("select", "title,slug,cover_url")
            .addQueryParameter("order", "view_count.desc")
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("limit", "24")
            .build()
        return GET(url, supabaseHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val mangas = response.parseAs<List<SearchSeriesDto>>().map { it.toSManga(baseUrl) }
        return MangasPage(mangas, mangas.size == 24)
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        val offset = (page - 1) * 24
        val url = "$supabaseUrl/series".toHttpUrl().newBuilder()
            .addQueryParameter("select", "title,slug,cover_url")
            .addQueryParameter("order", "updated_at.desc")
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("limit", "24")
            .build()
        return GET(url, supabaseHeaders)
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
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

        return GET(url.build(), supabaseHeaders)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // ============================= Utilities =============================

    private fun Response.extractSeriesPayload(): SeriesPayload = extractNextJs<SeriesPayload> {
        it is JsonObject && "initialSeries" in it && "initialChapters" in it
    } ?: throw Exception("Failed to extract series payload")

    // ============================== Details ==============================

    override fun mangaDetailsParse(response: Response): SManga {
        val payload = response.extractSeriesPayload()
        return payload.initialSeries.toSManga(baseUrl, payload.initialGenres)
    }

    // ============================= Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val payload = response.extractSeriesPayload()

        val seriesSlug = payload.initialSeries.slug
        val showPremium = preferences.getBoolean(PREF_SHOW_PREMIUM, false)

        val chapters = payload.initialChapters ?: emptyList()
        val filteredChapters = if (showPremium) {
            chapters
        } else {
            chapters.filterNot { it.isPremium }
        }

        return filteredChapters.map { it.toSChapter(seriesSlug, dateFormat) }
    }

    // =============================== Pages ===============================

    override fun pageListParse(response: Response): List<Page> {
        val payload = response.extractNextJs<PagesPayload> {
            it is JsonObject && "initialPages" in it
        } ?: throw Exception("Failed to extract pages payload")

        return payload.initialPages.mapIndexed { i, page ->
            Page(i, imageUrl = page.imageUrl.toAbsoluteUrl(baseUrl))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ==============================

    override fun getFilterList() = FilterList(
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
