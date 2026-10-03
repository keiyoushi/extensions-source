package eu.kanade.tachiyomi.extension.fr.rimuscans

import android.content.SharedPreferences
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
import keiyoushi.utils.asJsoup
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document

@Source
abstract class RimuScans :
    KeiSource(),
    ConfigurableSource {

    private val preferences: SharedPreferences by getPreferencesLazy()

    // =============================== Popular ==============================

    override suspend fun getPopularManga(page: Int): MangasPage = seriesParse("$baseUrl/api/series?sort=rating&page=$page".toHttpUrl())

    // =============================== Latest ===============================

    override suspend fun getLatestUpdates(page: Int): MangasPage = seriesParse("$baseUrl/api/series?page=$page".toHttpUrl())

    // =============================== Search ===============================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
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
        return seriesParse(url)
    }

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl/api/admin/genres").parseAs<GenresDto>().toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = getRimuFilterList(data?.parseAs<GenresDto>()?.genres)

    private suspend fun seriesParse(url: HttpUrl): MangasPage {
        val dto = client.get(url).parseAs<SeriesListDto>()
        val mangas = dto.series.map { it.toSManga(baseUrl) }
        return MangasPage(mangas, dto.hasMore)
    }

    // =========================== Manga Details ============================
    // The site dropped its JSON detail API; details, chapters and pages are now
    // read from the Next.js (App Router) server payload embedded in the pages.

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val response = client.get(getMangaUrl(manga))
        val slug = response.request.url.pathSegments.last()
        val document = response.asJsoup()

        return SMangaUpdate(mangaDetailsParse(document), chapterListParse(document, slug))
    }

    private fun mangaDetailsParse(document: Document): SManga {
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

    private fun chapterListParse(document: Document, slug: String): List<SChapter> {
        val showPremium = preferences.getBoolean(SHOW_PREMIUM_KEY, SHOW_PREMIUM_DEFAULT)

        return collectChapters(document)
            .distinctBy { it.number }
            .filter { showPremium || !it.type.equals("PREMIUM", ignoreCase = true) }
            .sortedByDescending { it.number }
            .map { it.toSChapter(slug) }
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get(getChapterUrl(chapter))
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
