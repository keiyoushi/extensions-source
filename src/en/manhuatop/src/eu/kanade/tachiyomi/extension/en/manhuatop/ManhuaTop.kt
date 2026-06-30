package eu.kanade.tachiyomi.extension.en.manhuatop

import eu.kanade.tachiyomi.multisrc.madara.Madara
import keiyoushi.annotation.Source
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class ManhuaTop : Madara() {
    override val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.ROOT)
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override fun popularMangaSelector() = ".comic_post__item"
    override val popularMangaUrlSelector = ".comic_post__title a"
    override val useNewChapterEndpoint = true

    override val mangaSubString = "manhua"
    override val filterNonMangaItems = false
}
