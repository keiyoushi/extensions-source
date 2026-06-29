package eu.kanade.tachiyomi.extension.all.allporncomicsco

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class AllPornComicsCo :
    Madara(
        "AllPornComics.co",
        "https://allporncomics.co",
        "all",
        dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
    ) {
    override val mangaSubString = "comic"
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false

    override val popularMangaUrlSelector = "h3 > a:not([target=_self]):last-of-type"
}
