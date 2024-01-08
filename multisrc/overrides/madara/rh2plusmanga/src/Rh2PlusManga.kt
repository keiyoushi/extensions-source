package eu.kanade.tachiyomi.extension.th.rh2plusmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class Rh2PlusManga : Madara("Rh2PlusManga", "https://www.rh2plusmanga.com", "th", SimpleDateFormat("d MMMM yyyy", Locale("th"))) {
    override val filterNonMangaItems = false

    override val pageListParseSelector = ".reading-content img"
}
