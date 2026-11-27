package eu.kanade.tachiyomi.extension.id.inazumanga

import eu.kanade.tachiyomi.multisrc.zeistmanga.Genre
import eu.kanade.tachiyomi.multisrc.zeistmanga.Status
import eu.kanade.tachiyomi.multisrc.zeistmanga.Type
import eu.kanade.tachiyomi.multisrc.zeistmanga.ZeistManga
import eu.kanade.tachiyomi.source.model.Page
import keiyoushi.annotation.Source
import keiyoushi.utils.asJsoup
import org.jsoup.nodes.Document

@Source
abstract class ReYume : ZeistManga() {

    override val popularMangaSelector = ".pop-card"
    override val popularMangaSelectorTitle = "h4 a"
    override val popularMangaSelectorUrl = "h4 a"

    override val mangaDetailsSelector = "#Blog1"
    override val mangaDetailsSelectorDescription = "#synopsis p"
    override val mangaDetailsSelectorGenres = "#append-info .col-span-2 a[rel=tag]"
    override val mangaDetailsSelectorAuthor = "#extra-info dl:has(dt:contains(Author)) dd"
    override val mangaDetailsSelectorArtist = "#extra-info dl:has(dt:contains(Artist)) dd"
    override val mangaDetailsSelectorAltName = "#extra-info dl:has(dt:contains(Alternative)) dd"
    override val mangaDetailsSelectorStatus = "span[data-bg]"
    override val mangaDetailsSelectorInfo = "#append-info > div"
    override val mangaDetailsSelectorInfoTitle = "dt"
    override val mangaDetailsSelectorInfoDescription = "dd"

    override val pageListSelector = ".separator"
    override fun pageListParse(document: Document): List<Page> {
        val textArea = document.selectFirst("textarea#zeist-raw-data")?.text().orEmpty()
        return super.pageListParse(textArea.asJsoup(baseUrl))
    }

    override val hasFilters = true
    override val hasLanguageFilter = false

    override fun getStatusList() = listOf(
        Status("Semua", ""),
        Status("Ongoing", "Ongoing"),
        Status("Completed", "Completed"),
        Status("Dropped", "Dropped"),
    )

    override fun getTypeList() = listOf(
        Type("Semua", ""),
        Type("Manga", "Manga"),
        Type("Manhua", "Manhua"),
    )

    override fun getGenreList() = listOf(
        "Action", "Adventure", "Aliens", "Animals", "Comedy", "Cooking",
        "Crime", "Demons", "Drama", "Ecchi", "Fantasy", "Game",
        "Gender Bender", "Ghosts", "Gore", "Harem", "Historical", "Horror",
        "Isekai", "Josei", "Magic", "Martial Arts", "Mature", "Military",
        "Monsters", "Music", "Mystery", "Psychological", "Reincarnation", "Romance",
        "School Life", "Sci-Fi", "Seinen", "Shoujo", "Shounen", "Slice of Life",
        "Sports", "Supernatural", "Survival", "Thriller", "Tragedy", "Vampire",
        "Yuri",
    ).map { Genre(it, it) }
}
