package eu.kanade.tachiyomi.extension.fr.sushiscanfr

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class SushiScanFR : MangaThemesia() {
    override val mangaUrlDirectory = "/catalogue"
    override val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.FRENCH)
    override val altNamePrefix = "Nom alternatif : "
    override val seriesAuthorSelector = ".imptdt:contains(Auteur) i, .fmed b:contains(Auteur)+span"
    override val seriesStatusSelector = ".imptdt:contains(Statut) i"
    override fun String?.parseStatus(): Int = when {
        this == null -> SManga.UNKNOWN
        this.contains("En Cours", ignoreCase = true) -> SManga.ONGOING
        this.contains("Terminé", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun mangaDetailsParse(document: Document): SManga = super.mangaDetailsParse(document).apply {
        status = document.select(seriesStatusSelector).text().parseStatus()
    }
}
