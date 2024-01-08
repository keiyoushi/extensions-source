package eu.kanade.tachiyomi.extension.fr.lecercleduscan

import eu.kanade.tachiyomi.multisrc.foolslide.FoolSlide
import java.util.Locale

class LeCercleDuScan : FoolSlide("Le Cercle du Scan", "https://lel.lecercleduscan.com", "fr") {
    override fun parseChapterDate(date: String) = super.parseChapterDate(
        when (val lcDate = date.lowercase(Locale.FRENCH)) {
            "hier" -> "yesterday"
            "aujourd'hui" -> "today"
            "demain" -> "tomorrow"
            else -> lcDate
        },
    )
}
