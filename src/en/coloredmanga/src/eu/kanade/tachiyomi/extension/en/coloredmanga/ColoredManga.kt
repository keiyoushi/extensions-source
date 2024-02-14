package eu.kanade.tachiyomi.extension.en.coloredmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class ColoredManga : Madara(
    "Colored Manga",
    "https://coloredmanga.com",
    "en",
    dateFormat = SimpleDateFormat("dd-MMM", Locale.ENGLISH),
) {
    override val useNewChapterEndpoint = true
}
