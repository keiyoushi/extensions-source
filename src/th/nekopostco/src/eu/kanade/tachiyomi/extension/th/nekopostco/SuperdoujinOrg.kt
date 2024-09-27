package eu.kanade.tachiyomi.extension.th.nekopostco

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.source.model.SChapter
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class SuperdoujinOrg : Madara(
    "Superdoujin.org",
    "https://www.superdoujin.org",
    "th",
    dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT),
) {
    override val mangaSubString = "doujin"

    override val useLoadMoreRequest = LoadMoreStrategy.Never
    override val useNewChapterEndpoint = false

    override fun chapterListParse(response: Response): List<SChapter> {
        return super.chapterListParse(response).also { chapters ->
            if (chapters.size == 1) chapters[0].name = "Chapter"
        }
    }
}
