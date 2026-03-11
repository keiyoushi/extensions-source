package eu.kanade.tachiyomi.extension.tr.sunsetmanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class SunsetManga :
    Madara(
        "Sunset Manga",
        "https://sunsetmanga.com",
        "tr",
        dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.ROOT),
    ) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false
}
