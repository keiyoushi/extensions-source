package eu.kanade.tachiyomi.extension.es.mangaromance

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MangaRomance : Madara(
    "Manga Romance",
    "https://mangaromance19.com",
    "es",
    dateFormat = SimpleDateFormat("dd MMMM, yyyy", Locale("es")),
) {
    override val useNewChapterEndpoint: Boolean = true

    override val useLoadMoreRequest = LoadMoreStrategy.Never
}
