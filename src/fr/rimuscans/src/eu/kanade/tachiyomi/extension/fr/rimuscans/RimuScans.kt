package eu.kanade.tachiyomi.extension.fr.rimuscans

import android.content.SharedPreferences
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
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

class RimuScans :
    HttpSource(),
    ConfigurableSource {

    override val name = "Rimu Scans"

    override val baseUrl = "https://rimuscan.fr"

    override val lang = "fr"

    override val supportsLatest = true

    private val preferences: SharedPreferences by getPreferencesLazy()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Accept", "application/json, text/plain, */*")

    // =============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/api/series?sort=rating&page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage = seriesParse(response)

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/api/series?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = seriesParse(response)

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/api/series".toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addQueryParameter("search", query)
            } else {
                filters.forEach { filter ->
                    when (filter) {
                        is SortFilter -> filter.toUriPart().takeIf { it.isNotEmpty() && it != "updated" }
                            ?.let { addQueryParameter("sort", it) }
                        is TypeFilter -> filter.toUriPart().takeIf { it.isNotEmpty() }
                            ?.let { addQueryParameter("types", it) }
                        is StatusFilter ->
                            filter.state
                                .filterIsInstance<StatusCheckBox>()
                                .filter { it.state }
                                .joinToString(",") { it.value }
                                .takeIf { it.isNotEmpty() }
                                ?.let { addQueryParameter("status", it) }
                        is MinChaptersFilter -> filter.toUriPart().takeIf { it.isNotEmpty() }
                            ?.let { addQueryParameter("min_chapters", it) }
                        is PremiumOnlyFilter -> if (filter.state) addQueryParameter("premium", "1")
                        is GenreFilter ->
                            filter.state
                                .filter { it.state }
                                .joinToString(",") { it.name }
                                .takeIf { it.isNotEmpty() }
                                ?.let { addQueryParameter("genres", it) }
                        else -> {}
                    }
                }
            }
            addQueryParameter("page", page.toString())
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = seriesParse(response)

    override fun getFilterList(): FilterList = getRimuFilterList(baseUrl, client, headers)

    private fun seriesParse(response: Response): MangasPage {
        val dto = response.parseAs<SeriesListDto>()
        val mangas = dto.series.map { it.toSManga(baseUrl) }
        return MangasPage(mangas, dto.hasMore)
    }

    // =========================== Manga Details ============================

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url.substringAfter("/manga/")
        return GET("$baseUrl/api/manga?slug=$slug", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<MangaDetailsWrapperDto>().manga.toSManga(baseUrl)

    // ============================== Chapters ==============================

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<MangaDetailsWrapperDto>()
        val showPremium = preferences.getBoolean(SHOW_PREMIUM_KEY, SHOW_PREMIUM_DEFAULT)

        return result.manga.chapters
            .filter { showPremium || !it.type.equals("PREMIUM", ignoreCase = true) }
            .map { it.toSChapter(result.manga.slug) }
            .reversed()
    }

    // =============================== Pages ================================

    override fun pageListRequest(chapter: SChapter): Request {
        // chapter.url = "/read/{slug}/{number}"
        val parts = chapter.url.removePrefix("/read/").split('/', limit = 2)
        val slug = parts[0]
        val number = parts.getOrNull(1).orEmpty()
        val url = "$baseUrl/api/manga".toHttpUrl().newBuilder()
            .addQueryParameter("slug", slug)
            .fragment(number)
            .build()
        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<MangaDetailsWrapperDto>()
        val chapterNumber = response.request.url.fragment?.toDoubleOrNull()
            ?: throw Exception("Numéro de chapitre absent de la requête")

        val chapter = result.manga.chapters.find { it.number == chapterNumber }
            ?: throw Exception("Chapitre introuvable")

        if (chapter.type.equals("PREMIUM", ignoreCase = true) && chapter.images.isEmpty()) {
            throw Exception("Ce chapitre est premium. Lisez-le sur le site.")
        }

        return chapter.images.sortedBy { it.order }.mapIndexed { i, img ->
            Page(i, imageUrl = img.url.toAbsoluteUrl(baseUrl))
        }
    }

    override fun imageUrlParse(response: Response): String = ""

    // ============================ Preferences =============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        CheckBoxPreference(screen.context).apply {
            key = SHOW_PREMIUM_KEY
            title = "Afficher les chapitres premium"
            summary = "Afficher les chapitres payants (identifiés par 🔒) dans la liste."
            setDefaultValue(SHOW_PREMIUM_DEFAULT)
        }.also(screen::addPreference)
    }

    companion object {
        private const val SHOW_PREMIUM_KEY = "show_premium_chapters"
        private const val SHOW_PREMIUM_DEFAULT = false
    }
}
