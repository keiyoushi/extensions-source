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
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document

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
    // The site dropped its JSON detail API; details, chapters and pages are now
    // read from the Next.js (App Router) server payload embedded in the pages.

    override fun mangaDetailsRequest(manga: SManga): Request = GET(baseUrl + manga.url, headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val ld = document.select("script[type=application/ld+json]")
            .map { it.data() }
            .firstOrNull { "\"ComicSeries\"" in it }
            ?.parseAs<ComicSeriesLd>()
            ?: throw Exception("Détails introuvables")

        // First two badges before the title are the type and the status.
        val badges = document.selectFirst("h1")
            ?.previousElementSibling()
            ?.select("span")
            ?.map { it.text().trim() }
            .orEmpty()

        return ld.toSManga(baseUrl, badges.getOrNull(0), badges.getOrNull(1))
    }

    // ============================== Chapters ==============================

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val slug = response.request.url.pathSegments.last()
        val showPremium = preferences.getBoolean(SHOW_PREMIUM_KEY, SHOW_PREMIUM_DEFAULT)

        return collectChapters(document)
            .distinctBy { it.number }
            .filter { showPremium || !it.type.equals("PREMIUM", ignoreCase = true) }
            .sortedByDescending { it.number }
            .map { it.toSChapter(slug) }
    }

    // =============================== Pages ================================

    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val chapterNumber = response.request.url.pathSegments.last().toDoubleOrNull()
            ?: throw Exception("Numéro de chapitre absent de la requête")

        val chapters = collectChapters(response.asJsoup())
        val chapter = chapters.firstOrNull { it.number == chapterNumber && it.images.isNotEmpty() }
            ?: chapters.firstOrNull { it.number == chapterNumber }
            ?: throw Exception("Chapitre introuvable")

        if (chapter.images.isEmpty()) {
            if (chapter.type.equals("PREMIUM", ignoreCase = true)) {
                throw Exception("Ce chapitre est premium. Lisez-le sur le site.")
            }
            throw Exception("Aucune image trouvée pour ce chapitre")
        }

        return chapter.images.sortedBy { it.order }.mapIndexed { i, img ->
            Page(i, imageUrl = img.url.toAbsoluteUrl(baseUrl))
        }
    }

    override fun imageUrlParse(response: Response): String = ""

    /**
     * Collects every chapter object found in the page's Next.js flight data. Walks the whole
     * payload tree (the predicate always returns `false`) collecting each matching object.
     *
     * The flight payload deduplicates chapters across components: the full list array holds some
     * chapters only as string references (e.g. `$25:props:children:1:props:chapters:0`) that point
     * back into a smaller "recent" array. Collecting at the object level instead of requiring whole
     * arrays of objects recovers every chapter regardless of which array materialises it.
     */
    private fun collectChapters(document: Document): List<NextChapterDto> {
        val chapters = mutableListOf<NextChapterDto>()
        document.extractNextJs<JsonElement>(
            predicate = { element ->
                if (element is JsonObject && "number" in element && "type" in element) {
                    runCatching { element.parseAs<NextChapterDto>() }
                        .getOrNull()
                        ?.let(chapters::add)
                }
                false
            },
        )
        return chapters
    }

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
