package eu.kanade.tachiyomi.extension.es.senpaiediciones

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import eu.kanade.tachiyomi.source.model.SManga
import java.text.SimpleDateFormat
import java.util.Locale

class SenpaiEdiciones : MangaThemesia(
    "Senpai Ediciones",
    "http://senpaiediciones.com",
    "es",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("es")),
) {
    override val seriesAuthorSelector = ".imptdt:contains(Autor) i"
    override val seriesStatusSelector = ".imptdt:contains(Estado) i"

    override val pageSelector = "div#readerarea img:not(noscript img)"

    override fun String?.parseStatus(): Int = when {
        this == null -> SManga.UNKNOWN
        listOf("curso").any { this.contains(it, ignoreCase = true) } -> SManga.ONGOING
        this.contains("hiatus", ignoreCase = true) -> SManga.ON_HIATUS
        this.contains("finalizado", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }
}
