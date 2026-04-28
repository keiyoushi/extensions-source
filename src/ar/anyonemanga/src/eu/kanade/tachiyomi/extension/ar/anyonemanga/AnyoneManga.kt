package eu.kanade.tachiyomi.extension.ar.anyonemanga

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class AnyoneManga :
    Madara(
        "Anyone Manga",
        "https://anyonemanga.com",
        "ar",
        dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.ROOT),
    ) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}
