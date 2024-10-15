package eu.kanade.tachiyomi.extension.fr.inovascanmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class InovaScanManga : Madara(
    "Inova Scans Manga",
    "https://inovascanmanga.com",
    "fr",
    SimpleDateFormat("dd MMMM yyyy", Locale.FRENCH),
) {
    override val useNewChapterEndpoint = true

    override val useLoadMoreRequest = LoadMoreStrategy.Always

    override val mangaDetailsSelectorDescription = "div.manga-summary > p"
    override val mangaDetailsSelectorAuthor = "div.manga-authors > a"

    /*
    Not implemented by the website
     * mangaDetailsSelectorStatus
     * mangaDetailsSelectorArtist
     * seriesTypeSelector
     * altNameSelector
     * altName
     * updatingRegex
     */
}
