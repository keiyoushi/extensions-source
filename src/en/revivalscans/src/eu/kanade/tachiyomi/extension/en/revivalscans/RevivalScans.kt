package eu.kanade.tachiyomi.extension.en.revivalscans

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
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
import keiyoushi.utils.getPreferencesLazy
import okhttp3.Headers

@Source
abstract class RevivalScans :
    KeiSource(),
    ConfigurableSource {

    override val supportsLatest = false

    private val preferences by getPreferencesLazy()

    private val apiHeaders: Headers get() = headers.newBuilder().add("RSC", "1").build()

    // ============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val dto = client.get("$baseUrl/series", apiHeaders).extractNextJs<SeriesResponseDto>()
            ?: throw Exception("Failed to extract popular manga")

        val mangas = dto.series.map { it.toSManga(baseUrl) }
        return MangasPage(mangas, false)
    }

    // ============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = throw UnsupportedOperationException()

    // ============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val filtered = getPopularManga(page).mangas.filter {
            it.title.contains(query, ignoreCase = true)
        }
        return MangasPage(filtered, false)
    }

    // ============================== Details ==============================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val dto = client.get("$baseUrl/series/${manga.url}", apiHeaders).extractNextJs<ManhwaResponseDto>()
            ?: throw Exception("Failed to extract manga details")

        val showPremium = preferences.getBoolean(PREF_SHOW_PREMIUM, PREF_SHOW_PREMIUM_DEFAULT)

        return SMangaUpdate(
            manga = dto.manhwa.toSManga(baseUrl),
            chapters = dto.manhwa.toSChapterList(showPremium),
        )
    }

    // =============================== Pages ===============================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val dto = client.get(baseUrl + chapter.url, apiHeaders).extractNextJs<PagesResponseDto>()
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
