package eu.kanade.tachiyomi.extension.all.manhwaclubnet

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import okhttp3.Response

@Source
abstract class ManhwaClubNet : Madara() {
    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapters = super.chapterListParse(response)

        return when (lang) {
            "en" -> chapters.filterNot { it.name.endsWith(" raw") }
            "ko" -> chapters.filter { it.name.endsWith(" raw") }
            else -> emptyList()
        }
    }
}
