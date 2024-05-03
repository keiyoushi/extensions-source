package eu.kanade.tachiyomi.extension.ar.mangatime

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MangaTime : Madara(
    "Manga Time",
    "https://mangatime.us",
    "ar",
    dateFormat = SimpleDateFormat("dd MMMM، yyyy", Locale("ar")),
) {
    override val useLoadMoreRequest = LoadMoreStrategy.Always
    override val useNewChapterEndpoint = true
}
