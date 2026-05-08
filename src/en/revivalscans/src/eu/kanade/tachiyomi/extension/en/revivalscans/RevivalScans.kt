package eu.kanade.tachiyomi.extension.en.revivalscans

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.extractNextJsRsc
import keiyoushi.utils.getPreferencesLazy
import okhttp3.Request
import okhttp3.Response
import rx.Observable

class RevivalScans :
    HttpSource(),
    ConfigurableSource {

    override val name = "Revival Scans"
    override val baseUrl = "https://www.revivalscans.com"
    override val lang = "en"
    override val supportsLatest = false

    private val preferences by getPreferencesLazy()

    private val apiHeaders = headersBuilder().add("RSC", "1").build()

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/series", apiHeaders)

    override fun popularMangaParse(response: Response): MangasPage {
        val dto = response.body.string().extractNextJsRsc<SeriesResponseDto>()
            ?: throw Exception("Failed to extract popular manga")

        val mangas = dto.series.map { it.toSManga(baseUrl) }
        return MangasPage(mangas, false)
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException("Not used")

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException("Not used")

    // ============================== Search ===============================

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = client.newCall(popularMangaRequest(page))
        .asObservableSuccess()
        .map { response ->
            val pageData = popularMangaParse(response)
            val filtered = pageData.mangas.filter {
                it.title.contains(query, ignoreCase = true)
            }
            MangasPage(filtered, false)
        }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException("Not used")

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException("Not used")

    // ============================== Details ==============================

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl/series/${manga.url}", apiHeaders)

    override fun mangaDetailsParse(response: Response): SManga {
        val dto = response.body.string().extractNextJsRsc<ManhwaResponseDto>()
            ?: throw Exception("Failed to extract manga details")

        return dto.manhwa.toSManga(baseUrl)
    }

    // ============================= Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request = GET("$baseUrl/series/${manga.url}", apiHeaders)

    override fun chapterListParse(response: Response): List<SChapter> {
        val dto = response.body.string().extractNextJsRsc<ManhwaResponseDto>()
            ?: throw Exception("Failed to extract chapter list")

        val showPremium = preferences.getBoolean(PREF_SHOW_PREMIUM, PREF_SHOW_PREMIUM_DEFAULT)
        return dto.manhwa.toSChapterList(showPremium)
    }

    // =============================== Pages ===============================

    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, apiHeaders)

    override fun pageListParse(response: Response): List<Page> {
        val dto = response.body.string().extractNextJsRsc<PagesResponseDto>()
            ?: throw Exception("Failed to extract pages")

        return dto.pages.mapIndexed { index, pageDto ->
            val imageUrl = if (pageDto.url.startsWith("http")) {
                pageDto.url
            } else {
                baseUrl + pageDto.url
            }
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")

    // ============================= Utilities =============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_SHOW_PREMIUM
            title = "Show premium chapters"
            summary = "Show chapters that require a paid subscription to read. (Note: These chapters cannot be read through the extension without an active subscription.)"
            setDefaultValue(PREF_SHOW_PREMIUM_DEFAULT)
        }.also(screen::addPreference)
    }

    companion object {
        private const val PREF_SHOW_PREMIUM = "pref_show_premium"
        private const val PREF_SHOW_PREMIUM_DEFAULT = false
    }
}
