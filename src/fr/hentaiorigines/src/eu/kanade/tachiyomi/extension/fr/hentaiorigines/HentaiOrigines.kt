package eu.kanade.tachiyomi.extension.fr.hentaiorigines

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.FilterList
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

private val dateFormat = SimpleDateFormat("d MMMM yyyy", Locale.FRENCH).apply {
    timeZone = TimeZone.getTimeZone("Europe/Paris")
}

class HentaiOrigines :
    Madara(
        "Hentai Origines",
        "https://hentai-origines.com",
        "fr",
        dateFormat,
    ) {

    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true

    override val mangaDetailsSelectorAuthor = "div.author-content > a"
    override val mangaDetailsSelectorDescription = "div.summary__content > p"
    override val seriesTypeSelector = "span.manga-title-badges > span"

    override fun getFilterList(): FilterList {
        val filters = super.getFilterList().list.filterNot {
            it.name.lowercase().contains("adult content")
        }

        return FilterList(filters)
    }
}
