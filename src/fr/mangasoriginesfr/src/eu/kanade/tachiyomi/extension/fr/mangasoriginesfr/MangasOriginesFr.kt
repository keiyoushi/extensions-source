package eu.kanade.tachiyomi.extension.fr.mangasoriginesfr

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
abstract class MangasOriginesFr : Madara() {
    override val mangaSubString = "oeuvre"
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
            .add("origine", filters.firstInstanceOrNull<TypeFilter>()?.selected.orEmpty())
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
        TypeFilter(),
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

    private class TypeFilter :
        SelectFilter(
            "Type",
            listOf(
                "Tous" to "",
                "Manhwa" to "manhwa",
                "Manhua" to "manhua",
                "Manga" to "manga",
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
            "Adventure" to "adventure",
            "Amitié" to "amitie",
            "Amour" to "amour",
            "Art Martiaux" to "art-martiaux",
            "Aventure" to "aventure",
            "BL" to "bl",
            "Boys" to "boys",
            "Combat" to "combat",
            "Comedy" to "comedy",
            "Comédie" to "comedie",
            "Dark Fantasy" to "dark-fantasy",
            "Drama" to "drama",
            "Drame" to "drame",
            "Dystopie" to "dystopie",
            "Démon" to "demon",
            "Ecchi" to "ecchi",
            "Erotique" to "erotique",
            "Fantastique" to "fantastique",
            "Fantasy" to "fantasy",
            "Guerre" to "guerre",
            "Harem" to "harem",
            "Historique" to "historique",
            "Horreur" to "horreur",
            "Isekai" to "isekai",
            "Jeu" to "jeu",
            "Josei" to "josei",
            "Magie" to "magie",
            "Malédiction" to "malediction",
            "Mature" to "mature",
            "Moderne" to "moderne",
            "Mort" to "mort",
            "Murim" to "murim",
            "Musique" to "musique",
            "Mystère" to "mystere",
            "Novel" to "novel",
            "Post-Apo" to "post-apo",
            "Prison" to "prison",
            "Psychologique" to "psychologique",
            "Religion" to "religion",
            "Returner" to "returner",
            "Romance" to "romance",
            "Réincarnation" to "reincarnation",
            "Régression" to "regression",
            "School life" to "school-life",
            "Sci-fi" to "sci-fi",
            "Seinen" to "seinen",
            "Shojo" to "shojo",
            "Shonen" to "shonen",
            "Slice of Life" to "slice-of-life",
            "Société" to "societe",
            "Sorcellerie" to "sorcellerie",
            "Sport" to "sport",
            "Steampunk" to "steampunk",
            "Supernaturel" to "supernaturel",
            "Surnaturel" to "surnaturel",
            "Tragédie" to "tragedie",
            "Vengeance" to "vengeance",
            "Webcomic" to "webcomic",
            "Yuri" to "yuri",
            "École" to "ecole",
        )
    }
}
