package eu.kanade.tachiyomi.extension.fr.mangasoriginesfr

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class MangasOriginesFr :
    Madara(
        "Mangas-Origines.fr",
        "https://mangas-origines.fr",
        "fr",
        dateFormat,
    ) {
    override val mangaSubString = "catalogues"
    override val useNewChapterEndpoint = true
    override val useLoadMoreRequest = LoadMoreStrategy.Never

    override val mangaDetailsSelectorAuthor = "div.author-content > a"
    override val mangaDetailsSelectorDescription = "div.summary__content > p"
}

private val dateFormat = SimpleDateFormat("d MMMM yyyy", Locale("fr")).apply {
    timeZone = TimeZone.getTimeZone("Europe/Paris")
}
