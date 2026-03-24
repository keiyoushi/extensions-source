package eu.kanade.tachiyomi.extension.en.allporncomicio

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class AllPornComicIo :
    Madara(
        "AllPornComic.io",
        "https://allporncomic.io",
        "en",
        dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
    ) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = true
}
