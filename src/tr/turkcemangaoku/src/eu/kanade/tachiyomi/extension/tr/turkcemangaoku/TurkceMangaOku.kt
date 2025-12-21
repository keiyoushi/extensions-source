package eu.kanade.tachiyomi.extension.tr.turkcemangaoku

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class TurkceMangaOku : Madara(
    "Turkce Manga Oku",
    "https://turkcemangaoku.com.tr",
    "tr",
    dateFormat = SimpleDateFormat("dd MMM yyyy", Locale("tr")),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}
