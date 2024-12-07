package eu.kanade.tachiyomi.extension.tr.mangawt

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MangaWT : Madara(
    "MangaWT",
    "https://mangawt.com",
    "tr",
    dateFormat = SimpleDateFormat("MMMM dd, yyyy", Locale("tr")),
) {
    override val useNewChapterEndpoint = true

    override val useLoadMoreRequest = LoadMoreStrategy.Always
}
