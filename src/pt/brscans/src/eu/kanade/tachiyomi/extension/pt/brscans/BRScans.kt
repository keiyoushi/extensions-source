package eu.kanade.tachiyomi.extension.pt.brscans

import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class BRScans :
    HttpSource(),
    ConfigurableSource {

    override val name = "BRScans"

    override val baseUrl = "https://brscans.vercel.app"

    private val apiUrl = "https://e5oer7ngt8.execute-api.sa-east-1.amazonaws.com/dev"

    override val lang = "pt-BR"

    override val supportsLatest = true

    private val dateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }

    private val preferences by getPreferencesLazy()

    private val showNsfw: Boolean get() = preferences.getBoolean(SHOW_NSFW_PREF, true)

    // Genre caching system
    private var genreMap: Map<Int, String> = emptyMap()
    private var genreMapFetched = false

    @Synchronized
    private fun fetchGenreMap() {
        if (genreMapFetched) return
        try {
            client.newCall(GET("$apiUrl/manhwas/genres/", headers)).execute().use { res ->
                val genres = res.parseAs<List<GenreDto>>()
                genreMap = genres.associate { it.id to it.name }
                genreMapFetched = true
            }
        } catch (_: Exception) {}
    }

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = if (showNsfw) {
        // Fetch the search endpoint with no query to get everything including NSFW
        GET("$apiUrl/manhwas/search/?query=", headers)
    } else {
        // Default paginated list which filters out NSFW
        GET("$apiUrl/manhwas/?page=$page", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        fetchGenreMap()
        return if (showNsfw) {
            val results = response.parseAs<List<ManhwaDto>>()
            val mangas = results.map { it.toSManga(genreMap) }
            MangasPage(mangas, false)
        } else {
            val paginated = response.parseAs<PaginatedManhwaDto>()
            val mangas = paginated.results.map { it.toSManga(genreMap) }
            val hasNext = paginated.next != null
            MangasPage(mangas, hasNext)
        }
    }

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$apiUrl/manhwas/search/?query=${query.trim()}", headers)

    override fun searchMangaParse(response: Response): MangasPage {
        fetchGenreMap()
        val results = response.parseAs<List<ManhwaDto>>()
        val filteredResults = if (showNsfw) {
            results
        } else {
            results.filter { !it.isNsfw }
        }
        val mangas = filteredResults.map { it.toSManga(genreMap) }
        return MangasPage(mangas, false)
    }

    // =========================== Manga Details ============================

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$apiUrl/manhwas/${manga.url}/", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        fetchGenreMap()
        val dto = response.parseAs<ManhwaDto>()
        return dto.toSManga(genreMap).apply {
            initialized = true
        }
    }

    // ============================== Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val dto = response.parseAs<ManhwaDto>()
        return dto.chapters.reversed().map { it.toSChapter(dateFormat) }
    }

    // =============================== Pages ================================

    override fun pageListRequest(chapter: SChapter): Request = GET("$apiUrl/chapters/${chapter.url}/", headers)

    override fun pageListParse(response: Response): List<Page> {
        val dto = response.parseAs<ChapterDetailDto>()
        return dto.pages.mapIndexedNotNull { index, pageDto ->
            pageDto.toPage(index)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used.")

    // ============================= Settings ===============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_NSFW_PREF
            title = "Mostrar conteúdo adulto (+18)"
            summary = "Habilita a listagem e visualização de manhwas adultos (+18) nos resultados."
            setDefaultValue(true)
        }.also(screen::addPreference)
    }

    companion object {
        private const val SHOW_NSFW_PREF = "showNsfwPref"
    }
}
