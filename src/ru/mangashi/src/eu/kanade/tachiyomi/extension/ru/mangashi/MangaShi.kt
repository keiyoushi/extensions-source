package eu.kanade.tachiyomi.extension.ru.mangashi

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class MangaShi :
    Madara(
        "Manga-shi",
        "https://manga-shi.org",
        "ru",
        dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.ROOT),
    ) {
    override val useNewChapterEndpoint = true
    override val useLoadMoreRequest = LoadMoreStrategy.Always
}
