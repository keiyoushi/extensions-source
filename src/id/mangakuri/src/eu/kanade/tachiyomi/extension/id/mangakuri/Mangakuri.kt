package eu.kanade.tachiyomi.extension.id.mangakuri

import eu.kanade.tachiyomi.multisrc.mangathemesia.MangaThemesia
import java.text.SimpleDateFormat
import java.util.Locale

class Mangakuri : MangaThemesia("Mangakuri", "https://mangakuri.org", "id", dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("id"))) {

    override val hasProjectPage = true

    override val typeFilterOptions = arrayOf(
        Pair("Default", ""),
        Pair("Manga", "Manga"),
        Pair("Manhwa", "Manhwa"),
        Pair("Manhua", "Manhua"),
        Pair("Comic", "Comic"),
        Pair("Novel", "Novel"),
    )
}
