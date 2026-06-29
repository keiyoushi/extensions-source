package eu.kanade.tachiyomi.extension.en.manhuatop

import eu.kanade.tachiyomi.multisrc.madara.Madara
import java.text.SimpleDateFormat
import java.util.Locale

class ManhuaTop :
    Madara(
        "ManhuaTop",
        "https://manhuatop.org",
        "en",
        dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.ROOT),
    ) {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override fun popularMangaSelector() = ".comic_post__item"
    override val popularMangaUrlSelector = ".comic_post__title a"
    override val useNewChapterEndpoint = true

    override val mangaSubString = "manhua"
    override val filterNonMangaItems = false
}
