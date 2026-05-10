package eu.kanade.tachiyomi.extension.fr.pornhwafr

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SManga
import org.jsoup.nodes.Document
import java.text.SimpleDateFormat
import java.util.Locale

class PornhwaFR : MangaThemesia("Pornwha.fr", "https://pornhwa.fr", "fr", mangaUrlDirectory = "/catalogue", dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.FRENCH)) {
    override val altNamePrefix = "Nom alternatif : "
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
