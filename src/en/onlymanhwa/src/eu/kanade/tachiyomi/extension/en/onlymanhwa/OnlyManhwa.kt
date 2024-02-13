package eu.kanade.tachiyomi.extension.en.onlymanhwa

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class OnlyManhwa : Madara(
    "OnlyManhwa",
    "https://onlymanhwa.org",
    "en",
    dateFormat = SimpleDateFormat("d 'de' MMMM 'de' yyyy", Locale.ENGLISH),
) {
    override val useNewChapterEndpoint = true
    override val mangaSubString = "manhwa"
    override val mangaDetailsSelectorStatus = "div.summary-heading:contains(Status) + div.summary-content"

    override fun searchPage(page: Int): String = if (page == 1) "" else "page/$page/"
}
