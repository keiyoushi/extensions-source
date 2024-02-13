package eu.kanade.tachiyomi.extension.en.hentaimanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class HentaiManga : Madara(
    "Hentai Manga",
    "https://hentaimanga.me",
    "en",
    dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US),
) {

    // The website does not flag the content.
    override val filterNonMangaItems = false
}
