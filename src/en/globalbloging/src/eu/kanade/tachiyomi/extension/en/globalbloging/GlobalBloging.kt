package eu.kanade.tachiyomi.extension.en.globalbloging

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class GlobalBloging : Madara(
    "Global Bloging",
    "https://globalbloging.com",
    "en",
    SimpleDateFormat("dd MMMM yyyy", Locale.US),
) {
    override val useNewChapterEndpoint = true

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"

    // =========================== Manga Details ============================

    override val mangaDetailsSelectorThumbnail = "${super.mangaDetailsSelectorThumbnail}[src~=.]"
    override val mangaDetailsSelectorAuthor = "div.manga-authors > a"
    override val mangaDetailsSelectorDescription = "div.manga-summary > p"
}
