package eu.kanade.tachiyomi.extension.en.coffeemangaunoriginal

import eu.kanade.tachiyomi.multisrc.madara.MadaraNoAjax
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document

@Source
abstract class CoffeeManga : MadaraNoAjax() {
    override val chapterMode = ChapterMode.MangaAjax

    override fun archiveSelector() = "a.acard"
    override val archiveUrlSelector = "a.acard"
    override val archiveTitleSelector = ".ac-t"
    override val filterGenresSelector = "#gchips"

    override val mangaDetailsSelectorTitle = "h1.htitle"
    override val mangaDetailsSelectorStatus = ".htag--status"
    override val mangaDetailsSelectorDescription = "#syn"
    override val mangaDetailsSelectorThumbnail = ".hposter__card img"
    override val mangaDetailsSelectorGenre = ".hchips--genres a.chip"
    override val mangaDetailsSelectorTag = ".hchips--tags a.chip"
    override val altNameSelector = ".sir--wide .v"

    // Cards carry no post ID; it's picked up from the details page shortlink instead
    override fun parseArchive(document: Document) = document.select(archiveSelector()).mapNotNull { archiveManga(it, "") }
}
