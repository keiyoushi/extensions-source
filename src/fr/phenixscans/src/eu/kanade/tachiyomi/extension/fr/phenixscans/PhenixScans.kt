package eu.kanade.tachiyomi.extension.fr.phenixscans

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class PhenixScans : MangaThemesia("PhenixScans", "https://phenixscans.fr", "fr", dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale.FRENCH)) {
    override val seriesAuthorSelector = ".imptdt:contains(Auteur) i, .fmed b:contains(Auteur)+span"
    override val seriesStatusSelector = ".imptdt:contains(Statut) i"

    override fun String?.parseStatus(): Int = when {
        this == null -> SManga.UNKNOWN
        this.contains("En Cours", ignoreCase = true) -> SManga.ONGOING
        this.contains("Terminé", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun mangaDetailsParse(document: Document): SManga =
        super.mangaDetailsParse(document).apply {
            status = document.select(seriesStatusSelector).text().parseStatus()
        }
}
