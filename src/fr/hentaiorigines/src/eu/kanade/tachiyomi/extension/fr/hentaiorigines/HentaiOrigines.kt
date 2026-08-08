package eu.kanade.tachiyomi.extension.fr.hentaiorigines

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.Serializable
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.Locale

@Source
abstract class HentaiOrigines : Madara() {
    override val useNewChapterEndpoint = true
    override val useLoadMoreRequest = LoadMoreStrategy.Never

    // The child theme no longer exposes the genres the Madara way, they are listed below instead.
    override val fetchGenres = false

    override fun popularMangaRequest(page: Int): Request = catalogueRequest(page, "", FilterList(), defaultSort = "populaire")

    override fun popularMangaParse(response: Response): MangasPage = catalogueParse(response)

    override fun latestUpdatesRequest(page: Int): Request = catalogueRequest(page, "", FilterList(), defaultSort = "recents")

    override fun latestUpdatesParse(response: Response): MangasPage = catalogueParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = catalogueRequest(page, query, filters, defaultSort = "recents")

    override fun searchMangaParse(response: Response): MangasPage = catalogueParse(response)

    /**
     * The listing pages only render their first batch server-side: paging, sorting and filtering
     * all go through this admin-ajax action, which returns the entries as a HTML fragment.
     */
    private fun catalogueRequest(
        page: Int,
        query: String,
        filters: FilterList,
        defaultSort: String,
    ): Request {
        val genres = filters.firstInstanceOrNull<GenreFilter>()
            ?.state.orEmpty()
            .filter(GenreCheckBox::state)
            .joinToString(",", transform = GenreCheckBox::slug)

        val form = FormBody.Builder()
            .add("action", "madara_child_catalogue")
            .add("s", query)
            .add("genres", genres)
            .add("statut", filters.firstInstanceOrNull<StatusFilter>()?.selected ?: "tous")
            .add("note", filters.firstInstanceOrNull<RatingFilter>()?.selected ?: "0")
            .add("origine", "")
            .add("tri", filters.firstInstanceOrNull<SortFilter>()?.selected ?: defaultSort)
            .add("chmin", filters.firstInstanceOrNull<ChapterMinFilter>()?.value ?: "0")
            .add("chmax", filters.firstInstanceOrNull<ChapterMaxFilter>()?.value ?: "0")
            .add("page", page.toString())
            .add("auteur", "")
            .add("artiste", "")
            .add("annee", "")
            .build()

        return POST("$baseUrl/wp-admin/admin-ajax.php", xhrHeaders, form)
    }

    private fun catalogueParse(response: Response): MangasPage {
        val data = response.parseAs<CatalogueResponse>().data
            ?: return MangasPage(emptyList(), false)

        val entries = Jsoup.parseBodyFragment(data.html, baseUrl)
            .select("a.ori-card:has(span.ori-card-title)")
            .map { element ->
                SManga.create().apply {
                    setUrlWithoutDomain(element.attr("abs:href"))
                    title = element.selectFirst("span.ori-card-title")!!.text()
                    thumbnail_url = element.selectFirst("img")
                        ?.let { processThumbnail(imageFromElement(it), true) }
                }
            }

        return MangasPage(entries, data.more)
    }

    @Serializable
    private class CatalogueResponse(
        val data: CatalogueData? = null,
    )

    @Serializable
    private class CatalogueData(
        val html: String = "",
        val more: Boolean = false,
    )

    override val mangaDetailsSelectorAuthor = "dt:contains(Scénario) + dd, dt:contains(Auteur) + dd"
    override val mangaDetailsSelectorArtist = "dt:contains(Dessin) + dd, dt:contains(Artiste) + dd"
    override val mangaDetailsSelectorStatus = "dt:contains(Statut) + dd"
    override val mangaDetailsSelectorDescription = "div.ori-sr-syn-texte"
    override val mangaDetailsSelectorThumbnail = "div.ori-sr-cover img"
    override val mangaDetailsSelectorGenre = "div.ori-sr-genres a.ori-sr-genre"
    override val seriesTypeSelector = "dt:contains(Type) + dd"
    override val altNameSelector = "dt:contains(Nom alternatif) + dd"

    override fun chapterListSelector() = "div.ori-chl-row"

    override val chapterUrlSelector = "a.ori-chl-corps"

    override fun chapterDateSelector() = "span.ori-chl-date"

    override fun chapterFromElement(element: Element): SChapter = super.chapterFromElement(element).apply {
        element.selectFirst("span.ori-chl-nom")?.let { name = it.text() }
    }

    override fun parseChapterDate(date: String?): Long = DATE_FORMATTER.tryParseDate(date, TIME_ZONE)

    override fun getFilterList() = FilterList(
        SortFilter(),
        StatusFilter(),
        RatingFilter(),
        ChapterMinFilter(),
        ChapterMaxFilter(),
        Filter.Separator(),
        GenreFilter(),
    )

    private open class SelectFilter(
        name: String,
        private val options: List<Pair<String, String>>,
    ) : Filter.Select<String>(name, options.map { it.first }.toTypedArray()) {
        val selected get() = options[state].second
    }

    private class SortFilter :
        SelectFilter(
            "Tri",
            listOf(
                "Récents" to "recents",
                "Populaire" to "populaire",
                "Mieux notés" to "notes",
                "A → Z" to "az",
            ),
        )

    private class StatusFilter :
        SelectFilter(
            "Statut",
            listOf(
                "Tous" to "tous",
                "En cours" to "en-cours",
                "Terminé" to "termine",
            ),
        )

    private class RatingFilter :
        SelectFilter(
            "Note minimum",
            listOf(
                "Toutes" to "0",
                "1 étoile et plus" to "1",
                "2 étoiles et plus" to "2",
                "3 étoiles et plus" to "3",
                "4 étoiles et plus" to "4",
                "5 étoiles" to "5",
            ),
        )

    private open class NumberFilter(name: String) : Filter.Text(name) {
        val value get() = state.takeIf { it.isNotEmpty() && it.all(Char::isDigit) } ?: "0"
    }

    private class ChapterMinFilter : NumberFilter("Chapitres (minimum)")

    private class ChapterMaxFilter : NumberFilter("Chapitres (maximum)")

    private class GenreCheckBox(name: String, val slug: String) : Filter.CheckBox(name)

    private class GenreFilter :
        Filter.Group<GenreCheckBox>(
            "Genres",
            GENRES.map { GenreCheckBox(it.first, it.second) },
        )

    companion object {
        private val TIME_ZONE = ZoneId.of("Europe/Paris")

        // Chapter dates are written with shortened, capitalized month names (`Juil`, `Sep`, `Déc`)
        // that no localized pattern matches.
        private val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("d ")
            .appendText(
                ChronoField.MONTH_OF_YEAR,
                mapOf(
                    1L to "Jan",
                    2L to "Fév",
                    3L to "Mar",
                    4L to "Avr",
                    5L to "Mai",
                    6L to "Juin",
                    7L to "Juil",
                    8L to "Août",
                    9L to "Sep",
                    10L to "Oct",
                    11L to "Nov",
                    12L to "Déc",
                ),
            )
            .appendPattern(" yyyy")
            .toFormatter(Locale.FRENCH)

        private val GENRES = listOf(
            "Action" to "action",
            "Alien" to "alien",
            "Alpha" to "alpha",
            "Amitié" to "amitie",
            "Art martiaux" to "art-martiaux",
            "Aventure" to "aventure",
            "Belle-mère" to "belle-mere",
            "Boy's Love" to "yaoi",
            "Campus" to "campus",
            "Comédie" to "comedie",
            "Domination" to "domination",
            "Drame" to "drame",
            "Démon" to "demon",
            "Ecchi" to "ecchi",
            "Fantasy" to "fantasy",
            "Furry" to "furry",
            "Futanari" to "futanari",
            "Fétichisme" to "fetichisme",
            "Gallerie" to "gallerie",
            "Gangster" to "gangster",
            "Gofast" to "gofast",
            "Gore" to "gore",
            "Guideverse" to "guideverse",
            "Hardcore" to "hardcore",
            "Harem" to "harem",
            "Historique" to "historique",
            "Horreur" to "horreur",
            "Humiliation" to "humiliation",
            "Inceste" to "inceste",
            "Isekai" to "isekai",
            "Josei" to "josei",
            "Loli" to "loli",
            "Love" to "love",
            "Magie" to "magie",
            "Mature" to "mature",
            "Milf" to "milf",
            "Mini-série" to "mini-serie",
            "Monsters girls" to "monsters-girls",
            "Ntr" to "ntr",
            "Office" to "office",
            "Omégaverse" to "omegaverse",
            "Oneshot" to "oneshot",
            "Parodie" to "parodie",
            "Professeur" to "professeur",
            "Psychologie" to "psychologie",
            "Rape" to "rape",
            "Romance" to "romance",
            "Réincarnation" to "reincarnation",
            "School life" to "school-life",
            "Sci-fi" to "sci-fi",
            "Shonen-ai" to "shonen-ai",
            "Slice of life" to "slice-of-life",
            "Smut" to "smut",
            "Soft" to "soft",
            "Sport" to "sport",
            "Surnaturel" to "surnaturel",
            "Tomgirl" to "tomgirl",
            "Tragédie" to "tragedie",
            "Triangle amoureux" to "triangle-amoureux",
            "Uncensored" to "uncensored",
            "Yuri" to "yuri",
        )
    }
}
