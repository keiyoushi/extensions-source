package eu.kanade.tachiyomi.extension.en.mangafreakonline

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MangaFreakOnline : Madara(
    "MangaFreak.online",
    "https://mangafreak.online",
    "en",
    dateFormat = SimpleDateFormat("d MMM، yyy", Locale.US),
) {
    override val useNewChapterEndpoint = false
}
