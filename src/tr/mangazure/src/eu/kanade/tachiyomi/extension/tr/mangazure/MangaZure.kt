package eu.kanade.tachiyomi.extension.tr.mangazure

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MangaZure :
    Madara(
        "MangaZure",
        "https://mangazure.net",
        "tr",
        dateFormat = SimpleDateFormat("dd MMMM yyyy", Locale("tr")),
    ) {
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = false
}
