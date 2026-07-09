package eu.kanade.tachiyomi.extension.en.xscans

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

@Source
abstract class Xscans :
    HttpSource(),
    ConfigurableSource {

    override val supportsLatest = true

    private val preferences: SharedPreferences by getPreferencesLazy()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/api/manga?limit=24&sort=popular&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<MangaResponseDto>()
        val mangas = dto.mangas.map { it.toSManga(baseUrl) }
        return MangasPage(mangas, dto.hasNextPage)
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/api/manga?limit=24&sort=newest&page=$page", headers)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/api/manga".toHttpUrl().newBuilder().apply {
            addQueryParameter("limit", "24")
            addQueryParameter("page", page.toString())

            if (query.isNotBlank()) {
                addQueryParameter("q", query)
            }

            filters.firstInstanceOrNull<SortFilter>()?.let {
                addQueryParameter("sort", it.selected)
            }
            filters.firstInstanceOrNull<GenreFilter>()?.takeIf { it.state != 0 }?.let {
                addQueryParameter("genres", it.selected)
            }
            filters.firstInstanceOrNull<DemographicFilter>()?.takeIf { it.state != 0 }?.let {
                addQueryParameter("demographic", it.selected)
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // ============================== Details ==============================

    override fun mangaDetailsRequest(manga: SManga): Request = mangaPageRequest(manga)

    override fun mangaDetailsParse(response: Response): SManga = response.extractMangaData().props.pageProps.initialManga.toSManga(baseUrl)

    // ============================= Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request = mangaPageRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val showLocked = preferences.getBoolean(PREF_SHOW_LOCKED, PREF_SHOW_LOCKED_DEFAULT)
        return response.extractMangaData().props.pageProps.initialManga.getChapters(showLocked)
    }

    // =============================== Pages ===============================

    override fun pageListParse(response: Response): List<Page> {
        val dto = response.parseAs<PagesResponseDto>()
        return dto.images.mapIndexed { index, imageUrl ->
            val url = if (imageUrl.startsWith("http")) imageUrl else baseUrl + imageUrl
            Page(index, imageUrl = url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ==============================

    override fun getFilterList() = FilterList(
        SortFilter(),
        Filter.Separator(),
        GenreFilter(),
        DemographicFilter(),
    )

    // ============================== Settings =============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SHOW_LOCKED
            title = "Show locked chapters"
            summary = "Show chapters that require unlocking on the website. They will be marked with a 🔒."
            setDefaultValue(PREF_SHOW_LOCKED_DEFAULT)
        }.also(screen::addPreference)
    }

    // ============================= Utilities =============================

    private fun mangaPageRequest(manga: SManga): Request = GET("$baseUrl/manga/${manga.url}", headersBuilder().add("rsc", "1").build())

    private fun Response.extractMangaData(): NextJsDataDto = requireNotNull(extractNextJs<NextJsDataDto>()) { "Failed to extract Next.js data" }

    companion object {
        private const val PREF_SHOW_LOCKED = "pref_show_locked"
        private const val PREF_SHOW_LOCKED_DEFAULT = false
    }
}
