package eu.kanade.tachiyomi.multisrc.origines

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * Sites running the `child-origines` WordPress theme, a Madara child theme whose listing,
 * details and chapter templates were rewritten. Only the reader markup is still Madara's.
 */
abstract class Origines : KeiSource() {

    /**
     * Path prefix the entries live under, `<baseUrl>/<mangaPath>/<slug>/`.
     */
    protected abstract val mangaPath: String

    /**
     * Prefixes entries used to be served under. Stored URLs using them are still resolved.
     */
    protected open val legacyMangaPaths: Set<String> = emptySet()

    /**
     * Genres of the catalogue panel, as `label to slug`.
     */
    protected abstract val genres: List<Pair<String, String>>

    /**
     * Origins of the catalogue panel, as `label to slug`. Empty hides the filter.
     */
    protected open val origins: List<Pair<String, String>> = emptyList()

    private val knownPaths: Set<String> by lazy { legacyMangaPaths + mangaPath }

    // ============================= Utilities ==============================

    /**
     * Only the identifying slugs are stored, so entries survive the series path changing.
     */
    private fun String.pathSegments(): List<String> {
        val path = if (startsWith("http")) toHttpUrl().encodedPath else this

        return path.substringBefore('?')
            .substringBefore('#')
            .split('/')
            .filter { it.isNotBlank() && it !in knownPaths }
    }

    private fun String.toMangaSlug(): String = pathSegments().firstOrNull() ?: this

    private fun String.toChapterSlug(): String = pathSegments().take(2).joinToString("/")

    override fun getMangaUrl(manga: SManga) = "$baseUrl/$mangaPath/${manga.url.toMangaSlug()}/"

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/$mangaPath/${chapter.url.toChapterSlug()}/"

    // ============================== Catalogue =============================

    /**
     * Listings only render their first batch server-side: paging, sorting, filtering and
     * searching all go through this theme action, which answers with a HTML fragment.
     */
    private suspend fun getCatalogue(
        page: Int,
        query: String = "",
        genres: String = "",
        status: String = "tous",
        rating: String = "0",
        origin: String = "",
        sort: String = "recents",
        chapterMin: String = "0",
        chapterMax: String = "0",
    ): MangasPage {
        val form = FormBody.Builder()
            .add("action", "madara_child_catalogue")
            .add("s", query)
            .add("genres", genres)
            .add("statut", status)
            .add("note", rating)
            .add("origine", origin)
            .add("tri", sort)
            .add("chmin", chapterMin)
            .add("chmax", chapterMax)
            .add("page", page.toString())
            .add("auteur", "")
            .add("artiste", "")
            .add("annee", "")
            .build()

        val data = client.post("$baseUrl/wp-admin/admin-ajax.php", form)
            .parseAs<CatalogueResponse>()
            .data

        val entries = Jsoup.parseBodyFragment(data.html, baseUrl)
            .select("a.ori-card:has(span.ori-card-title)")
            .map(::mangaFromElement)

        return MangasPage(entries, data.more)
    }

    private fun mangaFromElement(element: Element) = SManga.create().apply {
        url = element.attr("href").toMangaSlug()
        title = element.selectFirst("span.ori-card-title")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    override suspend fun getPopularManga(page: Int) = getCatalogue(page, sort = "populaire")

    override suspend fun getLatestUpdates(page: Int) = getCatalogue(page, sort = "recents")

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList) = getCatalogue(
        page = page,
        query = query,
        genres = filters.firstInstance<GenreFilter>().checked.joinToString(","),
        status = filters.firstInstance<StatusFilter>().selected,
        rating = filters.firstInstance<RatingFilter>().selected,
        origin = filters.firstInstanceOrNull<OriginFilter>()?.checked.orEmpty().joinToString(","),
        sort = filters.firstInstance<SortFilter>().selected,
        chapterMin = filters.firstInstance<ChapterMinFilter>().value,
        chapterMax = filters.firstInstance<ChapterMaxFilter>().value,
    )

    override fun getFilterList(data: JsonElement?) = FilterList(
        buildList {
            if (origins.isNotEmpty()) {
                add(OriginFilter(origins))
            }
            add(GenreFilter(genres))
            add(StatusFilter())
            add(RatingFilter())
            add(SortFilter())
            add(ChapterMinFilter())
            add(ChapterMaxFilter())
        },
    )

    // =========================== Manga Details ============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.pathSegments.firstOrNull() !in knownPaths) return null

        val manga = SManga.create().apply { this.url = url.encodedPath.toMangaSlug() }

        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val slug = manga.url.toMangaSlug()

        if (fetchDetails) {
            manga.parseDetails(client.get("$baseUrl/$mangaPath/$slug/").asJsoup())
        }

        val chapterList = if (fetchChapters) getChapterList(slug) else chapters

        return SMangaUpdate(manga, chapterList)
    }

    private fun SManga.parseDetails(document: Document) {
        val infos = document.select("div.ori-sr-infos dt").associate { dt ->
            dt.text().lowercase(Locale.FRENCH) to dt.nextElementSibling()?.text().orEmpty()
        }

        title = document.selectFirst("h1.ori-sr-title")!!.text()
        thumbnail_url = document.selectFirst("div.ori-sr-cover img")?.absUrl("src")
        author = infos["auteur"] ?: infos["scénario"]
        artist = infos["artiste"] ?: infos["dessin"]
        description = buildString {
            document.select("div.ori-sr-syn-texte p").eachText().forEach(::appendLine)

            infos["nom alternatif"]?.takeIf(String::isNotBlank)?.let {
                appendLine()
                append("Nom alternatif: ", it)
            }
        }.trim()
        genre = document.select("div.ori-sr-genres a.ori-sr-genre").eachText()
            .plus(infos["type"].orEmpty())
            .filter(String::isNotBlank)
            .joinToString()
        status = when (infos["statut"]?.lowercase(Locale.FRENCH)) {
            "en cours" -> SManga.ONGOING
            "terminé" -> SManga.COMPLETED
            "en pause" -> SManga.ON_HIATUS
            "abandonné", "annulé" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }

    // ============================== Chapters ==============================

    private suspend fun getChapterList(slug: String): List<SChapter> {
        val form = FormBody.Builder().build()
        val document = client.post("$baseUrl/$mangaPath/$slug/ajax/chapters/", form).asJsoup()

        return document.select("div.ori-chl-row").map { element ->
            val link = element.selectFirst("a.ori-chl-corps")!!

            SChapter.create().apply {
                url = link.attr("href").toChapterSlug()
                name = element.selectFirst("span.ori-chl-nom")?.text() ?: link.text()
                date_upload = parseChapterDate(element.selectFirst("span.ori-chl-date")?.text())
            }
        }
    }

    /**
     * Dates read `8 Août 2026`: capitalized, shortened month names no locale pattern parses.
     */
    private fun parseChapterDate(date: String?): Long {
        val (day, month, year) = DATE_REGEX.find(date.orEmpty())?.destructured ?: return 0L
        val monthNumber = monthNumber(month) ?: return 0L

        return runCatching {
            LocalDate.of(year.toInt(), monthNumber, day.toInt())
                .atStartOfDay(TIME_ZONE)
                .toInstant()
                .toEpochMilli()
        }.getOrDefault(0L)
    }

    private fun monthNumber(month: String): Int? {
        val name = month.lowercase(Locale.FRENCH)

        return when {
            name.startsWith("jan") -> 1
            name.startsWith("fev") || name.startsWith("fév") -> 2
            name.startsWith("mar") -> 3
            name.startsWith("avr") -> 4
            name.startsWith("mai") -> 5
            name.startsWith("juin") -> 6
            name.startsWith("juil") -> 7
            name.startsWith("ao") -> 8
            name.startsWith("sep") -> 9
            name.startsWith("oct") -> 10
            name.startsWith("nov") -> 11
            name.startsWith("dec") || name.startsWith("déc") -> 12
            else -> null
        }
    }

    // =============================== Pages ================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get("$baseUrl/$mangaPath/${chapter.url.toChapterSlug()}/").asJsoup()

        return document.select("div.reading-content img.wp-manga-chapter-img").mapIndexed { index, image ->
            Page(index, imageUrl = image.attr("src").trim())
        }
    }

    companion object {
        private val DATE_REGEX = Regex("""(\d{1,2})\s+(\p{L}+)\.?\s+(\d{4})""")
        private val TIME_ZONE: ZoneId = ZoneId.of("Europe/Paris")
    }
}
